package DeadManDraws

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private fun card(id: Int, type: CardType, value: Int = 2) = CardDefinition(id, type, value)

    private fun fixture(
        draw: List<CardDefinition> = emptyList(),
        board: List<CardDefinition> = emptyList(),
        burn: List<CardDefinition> = emptyList(),
        currentPlayer: Int = 0,
        difficulty: Int = 1,
        rules: GameRules = GameRules()
    ): GameState = GameState(
        players = mutableListOf(
            PlayerData(0, "Captain", true),
            PlayerData(1, "Rival", false)
        ),
        drawDeck = draw.toMutableList(),
        burnDeck = burn.toMutableList(),
        board = board.toMutableList(),
        currentPlayer = currentPlayer,
        difficulty = difficulty,
        rules = rules
    )

    @Test fun newGameHasExpectedDeckSizesAndPlayerCount() {
        val state = GameEngine(Random(7)).newGame(4)
        assertEquals(50, state.drawDeck.size)
        assertEquals(10, state.burnDeck.size)
        assertEquals(4, state.players.size)
    }

    @Test fun newGameSupportsLocalPassAndPlayAndCustomRules() {
        val state = GameEngine(Random(17)).newGame(
            playerCount = 3,
            difficulty = 2,
            rules = GameRules(chestKeyBonusEnabled = false, krakenPressureEnabled = false),
            passAndPlay = true
        )
        assertTrue(state.players.all { it.isHuman })
        assertTrue(state.passAndPlay)
        assertEquals(2, state.difficulty)
        assertFalse(state.rules.chestKeyBonusEnabled)
        assertFalse(state.rules.krakenPressureEnabled)
    }

    @Test fun cardValuesAreWithinTheirRanges() {
        val state = GameEngine(Random(11)).newGame(2)
        (state.drawDeck + state.burnDeck).forEach { card ->
            if (card.type == CardType.MERMAID) assertTrue(card.value in 4..9)
            else assertTrue(card.value in 2..7)
        }
    }

    @Test fun playerCountAndDifficultyAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { GameEngine().newGame(1) }
        assertThrows(IllegalArgumentException::class.java) { GameEngine().newGame(2, difficulty = 3) }
    }

    @Test fun duplicateCardBustsAndPassesTheTurn() {
        val engine = GameEngine(Random(2))
        val state = fixture(draw = listOf(card(2, CardType.ANCHOR, 3)), board = listOf(card(1, CardType.ANCHOR, 2)))
        engine.draw(state)
        assertTrue(state.board.isEmpty())
        assertEquals(listOf(1, 2), state.burnDeck.map { it.id }.sorted())
        assertEquals(1, state.currentPlayer)
        assertTrue(state.message.contains("Bust!"))
    }

    @Test fun cannonStealsAndBurnsSelectedCard() {
        val engine = GameEngine(Random(3))
        val state = fixture(board = listOf(card(100, CardType.CANNON)))
        state.pendingEffect = PendingCardEffect(CardType.CANNON, 0)
        state.players[1].bank += card(1, CardType.ANCHOR, 7)
        assertTrue(engine.resolveCannon(state, targetPlayerIndex = 1, selectedType = CardType.ANCHOR))
        assertTrue(state.players[1].bank.isEmpty())
        assertEquals(listOf(CardType.ANCHOR), state.burnDeck.map { it.type })
        assertNull(state.pendingEffect)
    }

    @Test fun masterGunnerRemovesEveryCardOfTheChosenType() {
        val engine = GameEngine(Random(4))
        val state = fixture(board = listOf(card(100, CardType.CANNON)))
        state.players[0].trait = TraitType.MASTER_GUNNER
        state.players[1].bank += listOf(card(1, CardType.KEY, 3), card(2, CardType.KEY, 6), card(3, CardType.MERMAID, 8))
        state.pendingEffect = PendingCardEffect(CardType.CANNON, 0)
        assertTrue(engine.resolveCannon(state, 1, CardType.KEY))
        assertEquals(listOf(CardType.MERMAID), state.players[1].bank.map { it.type })
        assertEquals(2, state.burnDeck.size)
    }

    @Test fun misfireMakesTheAttackerBurnTheirOwnTopBankCard() {
        val engine = GameEngine(Random(5))
        val state = fixture(board = listOf(card(100, CardType.CANNON)))
        state.players[0].bank += listOf(card(8, CardType.ANCHOR, 3), card(9, CardType.MERMAID, 6))
        state.players[1].trait = TraitType.MISFIRE
        state.players[1].bank += listOf(card(1, CardType.KEY, 4), card(2, CardType.CHEST, 6))
        state.pendingEffect = PendingCardEffect(CardType.CANNON, 0)
        assertTrue(engine.resolveCannon(state, targetPlayerIndex = 1))
        assertEquals(listOf(CardType.ANCHOR), state.players[0].bank.map { it.type })
        assertEquals(2, state.players[1].bank.size)
        assertEquals(CardType.MERMAID, state.burnDeck.single().type)
    }

    @Test fun scavengerKeepsTheCannonCard() {
        val engine = GameEngine(Random(6))
        val state = fixture(board = listOf(card(100, CardType.CANNON)))
        state.players[0].trait = TraitType.SCAVENGER
        state.players[1].bank += card(1, CardType.KEY, 6)
        state.pendingEffect = PendingCardEffect(CardType.CANNON, 0)
        assertTrue(engine.resolveCannon(state, 1, CardType.KEY))
        assertEquals(CardType.KEY, state.players[0].bank.single().type)
        assertTrue(state.burnDeck.isEmpty())
    }

    @Test fun captainsHookCanReturnTwoDifferentTypes() {
        val engine = GameEngine(Random(7))
        val state = fixture(board = listOf(card(100, CardType.HOOK)))
        state.players[0].trait = TraitType.CAPTAINS_HOOK
        state.players[0].bank += listOf(card(1, CardType.ANCHOR, 5), card(2, CardType.KEY, 6))
        state.pendingEffect = PendingCardEffect(CardType.HOOK, 0)
        assertTrue(engine.resolveHook(state, listOf(1, 2)))
        assertEquals(setOf(CardType.HOOK, CardType.ANCHOR, CardType.KEY), state.board.map { it.type }.toSet())
        assertTrue(state.players[0].bank.isEmpty())
    }

    @Test fun hookRejectsTheSameTypeTwice() {
        val engine = GameEngine(Random(8))
        val state = fixture(board = listOf(card(100, CardType.HOOK)))
        state.players[0].trait = TraitType.CAPTAINS_HOOK
        state.players[0].bank += listOf(card(1, CardType.ANCHOR, 3), card(2, CardType.ANCHOR, 6))
        state.pendingEffect = PendingCardEffect(CardType.HOOK, 0)
        assertFalse(engine.resolveHook(state, listOf(1, 2)))
    }

    @Test fun miserProtectsCardsReturnedByHook() {
        val engine = GameEngine(Random(9))
        val state = fixture(board = listOf(card(100, CardType.HOOK)))
        state.players[0].trait = TraitType.MISER
        state.players[0].bank += card(1, CardType.KEY, 6)
        state.pendingEffect = PendingCardEffect(CardType.HOOK, 0)
        assertTrue(engine.resolveHook(state, listOf(1)))
        assertTrue(1 in state.protectedCardIds)
    }

    @Test fun navigatorCanChooseABurnCardThatIsNotOnTheBoard() {
        val engine = GameEngine(Random(10))
        val state = fixture(
            board = listOf(card(100, CardType.MAP), card(101, CardType.ANCHOR)),
            burn = listOf(card(1, CardType.KEY, 5), card(2, CardType.MERMAID, 8))
        )
        state.players[0].trait = TraitType.NAVIGATOR
        state.pendingEffect = PendingCardEffect(CardType.MAP, 0)
        assertTrue(engine.resolveMap(state, burnCardId = 2))
        assertTrue(state.board.any { it.id == 2 })
        assertFalse(state.burnDeck.any { it.id == 2 })
    }

    @Test fun mapCannotAddDuplicateBoardType() {
        val engine = GameEngine(Random(12))
        val state = fixture(
            board = listOf(card(100, CardType.MAP), card(101, CardType.KEY)),
            burn = listOf(card(1, CardType.KEY, 5))
        )
        state.pendingEffect = PendingCardEffect(CardType.MAP, 0)
        assertFalse(engine.resolveMap(state))
        assertNotNull(state.pendingEffect)
        assertTrue(engine.skipPendingEffect(state))
        assertNull(state.pendingEffect)
    }

    @Test fun swordsmanCanStealAnAlreadyOwnedType() {
        val engine = GameEngine(Random(13))
        val state = fixture(board = listOf(card(100, CardType.SWORD)))
        state.players[0].trait = TraitType.SWORDSMAN
        state.players[0].bank += card(1, CardType.ANCHOR, 3)
        state.players[1].bank += card(2, CardType.ANCHOR, 7)
        state.pendingEffect = PendingCardEffect(CardType.SWORD, 0)
        assertTrue(engine.resolveSword(state, 1, 2))
        assertEquals(2, state.players[0].bank.count { it.type == CardType.ANCHOR })
        assertTrue(state.players[1].bank.isEmpty())
    }

    @Test fun ordinarySwordCannotStealAnOwnedType() {
        val engine = GameEngine(Random(14))
        val state = fixture(board = listOf(card(100, CardType.SWORD)))
        state.players[0].bank += card(1, CardType.ANCHOR, 3)
        state.players[1].bank += card(2, CardType.ANCHOR, 7)
        state.pendingEffect = PendingCardEffect(CardType.SWORD, 0)
        assertFalse(engine.resolveSword(state, 1, 2))
    }

    @Test fun safeHarborProtectsAnchorAndTheNextTwoBoardCards() {
        val engine = GameEngine(Random(15))
        val state = fixture(draw = listOf(
            card(1, CardType.ANCHOR),
            card(2, CardType.CHEST),
            card(3, CardType.KEY),
            card(4, CardType.ANCHOR, 5)
        ))
        state.players[0].trait = TraitType.SAFE_HARBOR
        repeat(3) { engine.draw(state) }
        assertEquals(3, state.protectedCardIds.size)
        engine.draw(state)
        assertEquals(3, state.board.size)
        assertEquals(1, state.burnDeck.size)
        assertEquals(0, state.currentPlayer)
        assertTrue(state.message.contains("Safe Harbor holds"))
    }

    @Test fun davyJonesClaimsTheTargetRivalsBustBoard() {
        val engine = GameEngine(Random(16))
        val state = fixture(
            draw = listOf(card(2, CardType.ANCHOR, 5)),
            board = listOf(card(1, CardType.ANCHOR, 3)),
            currentPlayer = 1
        )
        engine.setTrait(state, 0, TraitType.DAVY_JONES_LOCKER)
        assertTrue(engine.setDavyJonesTarget(state, 0, 1))
        engine.draw(state)
        assertEquals(setOf(1, 2), state.players[0].bank.map { it.id }.toSet())
        assertTrue(state.board.isEmpty())
    }

    @Test fun chestKeyBonusComesFromBurnDeck() {
        val engine = GameEngine(Random(18))
        val state = fixture(
            draw = listOf(card(20, CardType.ANCHOR)),
            board = listOf(card(1, CardType.CHEST), card(2, CardType.KEY)),
            burn = listOf(card(3, CardType.MERMAID, 8), card(4, CardType.ORACLE, 5), card(5, CardType.MAP, 6))
        )
        assertTrue(engine.collect(state))
        assertEquals(4, state.players[0].bank.size)
        assertEquals(1, state.burnDeck.size)
        assertEquals(1, state.currentPlayer)
    }

    @Test fun plundererTakesChestKeyBonusFromRivalsBank() {
        val engine = GameEngine(Random(19))
        val state = fixture(
            draw = listOf(card(20, CardType.ANCHOR)),
            board = listOf(card(1, CardType.CHEST), card(2, CardType.KEY)),
            burn = emptyList()
        )
        state.players[0].trait = TraitType.PLUNDERER
        state.players[1].bank += listOf(card(3, CardType.MERMAID, 8), card(4, CardType.ORACLE, 5))
        assertTrue(engine.collect(state, rivalTargetIndex = 1))
        assertEquals(4, state.players[0].bank.size)
        assertTrue(state.players[1].bank.isEmpty())
    }

    @Test fun treasureHunterTriplesTheChestKeyBonus() {
        val engine = GameEngine(Random(20))
        val state = fixture(
            draw = listOf(card(20, CardType.ANCHOR)),
            board = listOf(card(1, CardType.CHEST), card(2, CardType.KEY)),
            burn = listOf(
                card(3, CardType.MERMAID, 8), card(4, CardType.ORACLE, 5), card(5, CardType.MAP, 6),
                card(6, CardType.SWORD, 4), card(7, CardType.ANCHOR, 3), card(8, CardType.KRAKEN, 4)
            )
        )
        state.players[0].trait = TraitType.TREASURE_HUNTER
        assertTrue(engine.collect(state))
        assertEquals(8, state.players[0].bank.size)
        assertTrue(state.burnDeck.isEmpty())
    }

    @Test fun disablingKrakenPressurePreventsForcedDraws() {
        val engine = GameEngine(Random(21))
        val state = fixture(
            draw = listOf(card(1, CardType.KRAKEN)),
            rules = GameRules(krakenPressureEnabled = false)
        )
        engine.draw(state)
        assertEquals(0, state.pendingForcedDraws)
        assertNull(state.pendingEffect)
    }

    @Test fun goldenScalesAddsFivePointsForAMermaid() {
        val engine = GameEngine(Random(22))
        val state = fixture()
        state.players[0].trait = TraitType.GOLDEN_SCALES
        state.players[0].bank += card(1, CardType.MERMAID, 8)
        engine.finish(state)
        assertEquals(13, state.players[0].score)
        assertTrue(engine.scoreBreakdown(state.players[0]).single().contains("Golden Scales 5"))
    }

    @Test fun easyAiCollectsAfterItsFirstDraw() {
        val engine = GameEngine(Random(23))
        val state = fixture(
            draw = listOf(card(1, CardType.ANCHOR, 5), card(2, CardType.CHEST, 6)),
            currentPlayer = 1,
            difficulty = 0
        )
        engine.playAiTurnsUntilHuman(state)
        assertEquals(0, state.currentPlayer)
        assertEquals(listOf(CardType.ANCHOR), state.players[1].bank.map { it.type })
        assertTrue(state.board.isEmpty())
    }

    @Test fun unfinishedGameCanBeSerializedAndRestored() {
        val original = GameEngine(Random(24)).newGame(3, difficulty = 2)
        original.board += card(200, CardType.ANCHOR, 6)
        original.turnLog += "Saved test voyage"
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(original) }
            output.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as GameState }
        assertEquals(original.players.size, restored.players.size)
        assertEquals(original.drawDeck.map { it.id }, restored.drawDeck.map { it.id })
        assertEquals(original.board, restored.board)
        assertEquals(original.turnLog, restored.turnLog)
        assertEquals(2, restored.difficulty)
    }

    @Test fun beastmasterKrakenRequiresFourForcedDraws() {
        val engine = GameEngine(Random(25))
        val state = fixture(draw = listOf(
            card(1, CardType.KRAKEN), card(2, CardType.ANCHOR), card(3, CardType.CHEST),
            card(4, CardType.KEY), card(5, CardType.MAP)
        ))
        state.players[0].trait = TraitType.BEASTMASTER
        engine.draw(state)
        assertEquals(4, state.pendingForcedDraws)
    }

    @Test fun casanovaBanksMermaidsImmediately() {
        val engine = GameEngine(Random(26))
        val state = fixture(draw = listOf(card(1, CardType.MERMAID, 9), card(2, CardType.ANCHOR)))
        state.players[0].trait = TraitType.CASANOVA
        engine.draw(state)
        assertEquals(listOf(CardType.MERMAID), state.players[0].bank.map { it.type })
        assertTrue(state.board.isEmpty())
    }

    @Test fun fishermanBanksKrakenWithoutForcedDraws() {
        val engine = GameEngine(Random(27))
        val state = fixture(draw = listOf(card(1, CardType.KRAKEN, 6), card(2, CardType.ANCHOR)))
        state.players[0].trait = TraitType.FISHERMAN
        engine.draw(state)
        assertEquals(listOf(CardType.KRAKEN), state.players[0].bank.map { it.type })
        assertEquals(0, state.pendingForcedDraws)
        assertTrue(state.board.isEmpty())
    }

    @Test fun mysticOracleRevealsThreeUpcomingCards() {
        val engine = GameEngine(Random(28))
        val state = fixture(draw = listOf(
            card(1, CardType.ORACLE), card(2, CardType.ANCHOR), card(3, CardType.KEY), card(4, CardType.MERMAID)
        ))
        state.players[0].trait = TraitType.MYSTIC
        engine.draw(state)
        assertTrue(state.message.contains("Anchor"))
        assertTrue(state.message.contains("Key"))
        assertTrue(state.message.contains("Mermaid"))
    }

    @Test fun parryForcesARivalToPlayTheirBankedKrakenWhenDrawingSword() {
        val engine = GameEngine(Random(29))
        val state = fixture(draw = listOf(card(1, CardType.SWORD), card(2, CardType.ANCHOR)), currentPlayer = 1)
        state.players[0].trait = TraitType.PARRY
        state.players[1].bank += card(3, CardType.KRAKEN, 7)
        engine.draw(state)
        assertTrue(state.board.any { it.type == CardType.SWORD })
        assertTrue(state.board.any { it.type == CardType.KRAKEN })
        assertTrue(state.players[1].bank.none { it.type == CardType.KRAKEN })
        assertEquals(2, state.pendingForcedDraws)
        assertNotNull(state.pendingEffect)
    }

    @Test fun easyModeAndPassAndPlaySettingsAreSerializable() {
        val original = GameEngine(Random(30)).newGame(4, difficulty = 0, passAndPlay = true)
        original.rules.krakenPressureEnabled = false
        original.pendingEffect = PendingCardEffect(CardType.HOOK, 0)
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(original) }
            output.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as GameState }
        assertTrue(restored.passAndPlay)
        assertFalse(restored.rules.krakenPressureEnabled)
        assertEquals(PendingCardEffect(CardType.HOOK, 0), restored.pendingEffect)
    }

    @Test fun lastKrakenCannotSoftLockTheGameWhenForcedDrawsRunOutOfDeck() {
        val engine = GameEngine(Random(31))
        val state = fixture(draw = listOf(card(1, CardType.KRAKEN)))
        engine.draw(state)
        assertEquals(0, state.pendingForcedDraws)
        assertTrue(state.message.contains("Draw Deck exhausted"))
        assertTrue(engine.collect(state))
        assertTrue(state.finished)
    }
}
