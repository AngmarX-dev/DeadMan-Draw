package DeadManDraws

import java.io.Serializable
import kotlin.random.Random

enum class CardType(val displayName: String, val symbol: String) {
    ANCHOR("Anchor", "⚓"), CANNON("Cannon", "💣"), CHEST("Chest", "🧰"),
    HOOK("Hook", "🪝"), KEY("Key", "🗝"), KRAKEN("Kraken", "🐙"),
    MAP("Map", "🗺"), MERMAID("Mermaid", "🧜"), ORACLE("Oracle", "🔮"), SWORD("Sword", "⚔")
}

data class CardDefinition(val id: Int, val type: CardType, val value: Int) : Serializable {
    override fun toString() = "${type.symbol} ${type.displayName} · ${value}"
}

enum class TraitType(val displayName: String, val description: String) {
    BEASTMASTER("Beastmaster", "Kraken effects make opponents draw four cards instead of two."),
    CAPTAINS_HOOK("Captain's Hook", "Hook lets you return up to two different banked card types to the board."),
    CASANOVA("Casanova", "Mermaids are banked immediately."),
    DAVY_JONES_LOCKER("Davy Jones' Locker", "Choose a rival; gain their board cards when they bust."),
    FISHERMAN("Fisherman", "Kraken is banked immediately and does not force extra draws."),
    GOLDEN_SCALES("Golden Scales", "Banked Mermaids are worth five extra points."),
    MISER("Miser", "Cards brought back with Hook are protected from bust."),
    NAVIGATOR("Navigator", "Choose any eligible card from the Burn Deck when using Map."),
    MASTER_GUNNER("Master Gunner", "Cannon removes every banked card of the selected type from a rival."),
    MISFIRE("Misfire", "When targeted by Cannon, lose a top banked card instead of the attacker's chosen card."),
    MYSTIC("Mystic", "Oracle reveals up to three upcoming cards."),
    PARRY("Parry", "When a rival draws Sword, they must play a banked Kraken if they have one."),
    PLUNDERER("Plunderer", "When you collect a Chest + Key bonus, take the bonus from one rival's bank."),
    SAFE_HARBOR("Safe Harbor", "Your Anchor and the next two cards placed on the board are protected from bust."),
    SCAVENGER("Scavenger", "Keep the card stolen by Cannon instead of burning it."),
    SWORDSMAN("Swordsman", "Sword can steal a card type you already own."),
    TREASURE_HUNTER("Treasure Hunter", "Chest + Key triples the Burn Deck bonus when collecting.")
}

data class GameRules(
    var chestKeyBonusEnabled: Boolean = true,
    var krakenPressureEnabled: Boolean = true
) : Serializable

data class PendingCardEffect(val cardType: CardType, val playerIndex: Int) : Serializable

data class PlayerData(
    val id: Int,
    var name: String,
    var isHuman: Boolean,
    var trait: TraitType? = null,
    val bank: MutableList<CardDefinition> = mutableListOf(),
    var score: Int = 0
) : Serializable

data class GameState(
    val players: MutableList<PlayerData>,
    val drawDeck: MutableList<CardDefinition>,
    val burnDeck: MutableList<CardDefinition>,
    val board: MutableList<CardDefinition> = mutableListOf(),
    var currentPlayer: Int = 0,
    var finished: Boolean = false,
    var message: String = "Your turn. Draw a card or collect your haul.",
    var pendingForcedDraws: Int = 0,
    val difficulty: Int = 1,
    val passAndPlay: Boolean = false,
    val rules: GameRules = GameRules(),
    var pendingEffect: PendingCardEffect? = null,
    val protectedCardIds: MutableSet<Int> = mutableSetOf(),
    var protectedDrawsRemaining: Int = 0,
    val davyJonesTargets: MutableMap<Int, Int> = mutableMapOf(),
    var turnNumber: Int = 1,
    val turnLog: MutableList<String> = mutableListOf()
) : Serializable

class GameEngine(private val random: Random = Random.Default) {
    fun newGame(
        playerCount: Int,
        difficulty: Int = 1,
        rules: GameRules = GameRules(),
        passAndPlay: Boolean = false
    ): GameState {
        require(playerCount in 2..8) { "Player count must be between 2 and 8." }
        require(difficulty in 0..2) { "Difficulty must be 0 (easy), 1 (normal), or 2 (hard)." }
        val players = (0 until playerCount).map {
            PlayerData(
                id = it,
                name = if (it == 0) "Captain" else "Pirate ${it + 1}",
                isHuman = passAndPlay || it == 0
            )
        }.toMutableList()

        val all = mutableListOf<CardDefinition>()
        var id = 0
        CardType.entries.forEach { type ->
            repeat(6) { index ->
                val value = if (type == CardType.MERMAID) index + 4 else index + 2
                all += CardDefinition(id++, type, value)
            }
        }
        val burn = mutableListOf<CardDefinition>()
        CardType.entries.forEach { type ->
            val lowest = all.filter { it.type == type }.minBy { it.value }
            burn += lowest
            all.remove(lowest)
        }
        all.shuffle(random)
        burn.shuffle(random)
        return GameState(
            players = players,
            drawDeck = all,
            burnDeck = burn,
            difficulty = difficulty,
            passAndPlay = passAndPlay,
            rules = rules
        ).also { addLog(it, "A new voyage begins with ${playerCount} players.") }
    }

    fun draw(state: GameState): CardDefinition? {
        if (state.finished) return null
        if (state.pendingEffect != null) {
            state.message = "Resolve the ${state.pendingEffect!!.cardType.displayName} effect before drawing again."
            return null
        }
        if (state.drawDeck.isEmpty()) {
            if (state.pendingForcedDraws > 0) {
                val stranded = state.pendingForcedDraws
                state.pendingForcedDraws = 0
                state.message = "The Draw Deck is empty; ${stranded} forced draw(s) cannot be completed. Collect the board to finish."
                addLog(state, state.message)
            } else if (state.board.isEmpty()) {
                finish(state)
            } else {
                state.message = "The Draw Deck is empty. Collect the remaining board to finish."
            }
            return null
        }

        val player = state.players[state.currentPlayer]
        val card = state.drawDeck.removeAt(0)
        if (state.pendingForcedDraws > 0) state.pendingForcedDraws--

        if (player.trait == TraitType.CASANOVA && card.type == CardType.MERMAID) {
            player.bank += card
            state.message = "${player.name} draws a Mermaid and banks it immediately."
            addLog(state, state.message)
            finishIfNoCardsRemain(state)
            return card
        }
        if (player.trait == TraitType.FISHERMAN && card.type == CardType.KRAKEN) {
            player.bank += card
            state.message = "${player.name}'s Fisherman banks Kraken immediately."
            addLog(state, state.message)
            finishIfNoCardsRemain(state)
            return card
        }

        if (state.board.any { it.type == card.type }) {
            handleBust(state, card)
            return card
        }

        state.board += card
        protectIfNeeded(state, card)
        state.message = "${player.name} draws $card."
        when (card.type) {
            CardType.ANCHOR -> {
                if (player.trait == TraitType.SAFE_HARBOR) {
                    state.protectedCardIds += card.id
                    state.protectedDrawsRemaining = 2
                    state.message += " Safe Harbor protects this Anchor and the next two board cards."
                } else {
                    state.message += " The Anchor steadies the board."
                }
            }
            CardType.KRAKEN -> {
                val forced = if (!state.rules.krakenPressureEnabled || player.trait == TraitType.FISHERMAN) 0
                    else if (player.trait == TraitType.BEASTMASTER) 4 else 2
                state.pendingForcedDraws = maxOf(state.pendingForcedDraws, forced)
                if (forced > 0) state.message += " Kraken demands ${state.pendingForcedDraws} more draw(s)."
            }
            CardType.ORACLE -> {
                val previewCount = if (player.trait == TraitType.MYSTIC) 3 else 1
                val preview = state.drawDeck.take(previewCount)
                if (preview.isNotEmpty()) {
                    state.message += if (player.trait == TraitType.MYSTIC) {
                        " Mystic reveals: ${preview.joinToString { it.type.displayName }}."
                    } else {
                        " Next card: ${preview.first().type.displayName}."
                    }
                }
            }
            CardType.CANNON, CardType.HOOK, CardType.MAP, CardType.SWORD -> {
                state.pendingEffect = PendingCardEffect(card.type, player.id)
                state.message += " Choose how to resolve ${card.type.displayName}."
            }
            else -> Unit
        }
        addLog(state, state.message)

        if (card.type == CardType.SWORD) applyParryIfNeeded(state, player)
        if (state.drawDeck.isEmpty() && state.pendingEffect == null) {
            if (state.pendingForcedDraws > 0) {
                val stranded = state.pendingForcedDraws
                state.pendingForcedDraws = 0
                state.message += " Draw Deck exhausted; ${stranded} forced draw(s) cannot be made."
                addLog(state, state.message)
            }
            if (!state.finished && state.board.isEmpty()) finish(state)
            else if (!state.finished) state.message += " Collect the remaining board to finish."
        }
        return card
    }

    private fun protectIfNeeded(state: GameState, card: CardDefinition) {
        if (state.protectedDrawsRemaining > 0) {
            state.protectedCardIds += card.id
            state.protectedDrawsRemaining--
            if (state.protectedDrawsRemaining == 0) {
                addLog(state, "Safe Harbor protection has covered its next two cards.")
            }
        }
    }

    private fun handleBust(state: GameState, incoming: CardDefinition) {
        val bustedPlayerIndex = state.currentPlayer
        val rescuer = state.players.firstOrNull { owner ->
            owner.trait == TraitType.DAVY_JONES_LOCKER &&
                state.davyJonesTargets[owner.id] == bustedPlayerIndex
        }
        if (rescuer != null && rescuer.id != bustedPlayerIndex) {
            rescuer.bank += state.board
            rescuer.bank += incoming
            state.board.clear()
            state.protectedCardIds.clear()
            state.protectedDrawsRemaining = 0
            state.message = "Davy Jones' Locker! ${rescuer.name} claims ${state.players[bustedPlayerIndex].name}'s bust."
            addLog(state, state.message)
            state.pendingForcedDraws = 0
            state.pendingEffect = null
            nextPlayer(state)
            return
        }

        val protectedMatch = state.board.firstOrNull {
            it.type == incoming.type && it.id in state.protectedCardIds
        }
        if (protectedMatch != null) {
            state.burnDeck += incoming
            state.message = "Safe Harbor holds! The duplicate ${incoming.type.displayName} is burned, but the protected board survives."
            addLog(state, state.message)
            state.pendingForcedDraws = 0
            state.pendingEffect = null
            return
        }

        val vulnerable = state.board.filterNot { it.id in state.protectedCardIds }
        state.burnDeck += vulnerable
        state.burnDeck += incoming
        state.board.removeAll(vulnerable.toSet())
        state.protectedCardIds.retainAll(state.board.map { it.id }.toSet())
        state.message = "Bust! Duplicate ${incoming.type.displayName}; the unprotected board is burned."
        addLog(state, state.message)
        state.pendingForcedDraws = 0
        state.pendingEffect = null
        nextPlayer(state)
    }

    private fun applyParryIfNeeded(state: GameState, drawer: PlayerData) {
        val parryOwner = state.players.firstOrNull { it.id != drawer.id && it.trait == TraitType.PARRY } ?: return
        val kraken = drawer.bank.firstOrNull { it.type == CardType.KRAKEN } ?: return
        drawer.bank.remove(kraken)
        state.message = "Parry from ${parryOwner.name} forces ${drawer.name} to play a banked Kraken."
        addLog(state, state.message)
        if (state.board.any { it.type == CardType.KRAKEN }) {
            handleBust(state, kraken)
        } else {
            state.board += kraken
            state.pendingForcedDraws = maxOf(state.pendingForcedDraws, if (state.rules.krakenPressureEnabled) 2 else 0)
        }
    }

    fun resolveCannon(state: GameState, targetPlayerIndex: Int, selectedType: CardType? = null): Boolean {
        val effect = state.pendingEffect ?: return false
        if (effect.cardType != CardType.CANNON || effect.playerIndex != state.currentPlayer) return false
        if (targetPlayerIndex !in state.players.indices || targetPlayerIndex == state.currentPlayer) return false
        val attacker = state.players[state.currentPlayer]
        val target = state.players[targetPlayerIndex]

        if (target.trait == TraitType.MISFIRE) {
            val lost = attacker.bank.removeLastOrNull()
            if (lost == null) {
                state.message = "Misfire! ${target.name} cannot be targeted; ${attacker.name} has no banked card to burn, so Cannon fizzles."
            } else {
                state.burnDeck += lost
                state.message = "Misfire! ${target.name} is protected; ${attacker.name} burns their own ${lost.type.displayName} instead."
            }
        } else {
            val type = selectedType ?: return false
            val matching = target.bank.filter { it.type == type }
            if (matching.isEmpty()) return false
            val stolen = matching.maxBy { it.value }
            if (attacker.trait == TraitType.MASTER_GUNNER) {
                val removed = target.bank.filter { it.type == type }
                target.bank.removeAll(removed.toSet())
                removed.filter { it.id != stolen.id }.forEach { state.burnDeck += it }
                state.message = "Master Gunner removes ${removed.size} ${type.displayName} card(s) from ${target.name}."
            } else {
                target.bank.remove(stolen)
                state.message = "${attacker.name}'s Cannon targets ${target.name}'s ${type.displayName}."
            }
            if (attacker.trait == TraitType.SCAVENGER) {
                attacker.bank += stolen
                state.message += " Scavenger keeps the stolen card."
            } else {
                state.burnDeck += stolen
                state.message += " The stolen card is sent to the Burn Deck."
            }
        }
        state.pendingEffect = null
        addLog(state, state.message)
        finishIfNoCardsRemain(state)
        return true
    }

    fun resolveHook(state: GameState, selectedCardIds: List<Int>): Boolean {
        val effect = state.pendingEffect ?: return false
        if (effect.cardType != CardType.HOOK || effect.playerIndex != state.currentPlayer) return false
        val player = state.players[state.currentPlayer]
        val limit = if (player.trait == TraitType.CAPTAINS_HOOK) 2 else 1
        if (selectedCardIds.size > limit || selectedCardIds.distinct().size != selectedCardIds.size) return false
        val candidates = selectedCardIds.map { id -> player.bank.firstOrNull { it.id == id } ?: return false }
        if (candidates.map { it.type }.distinct().size != candidates.size) return false
        if (candidates.any { card -> state.board.any { it.type == card.type } }) return false
        candidates.forEach { card ->
            player.bank.remove(card)
            state.board += card
            if (player.trait == TraitType.MISER) state.protectedCardIds += card.id
        }
        state.pendingEffect = null
        state.message = if (candidates.isEmpty()) {
            "${player.name} leaves Hook unused."
        } else {
            "${player.name} returns ${candidates.size} banked card(s) with Hook" +
                if (player.trait == TraitType.MISER) "; Miser protects them from bust." else "."
        }
        addLog(state, state.message)
        finishIfNoCardsRemain(state)
        return true
    }

    fun resolveMap(state: GameState, burnCardId: Int? = null): Boolean {
        val effect = state.pendingEffect ?: return false
        if (effect.cardType != CardType.MAP || effect.playerIndex != state.currentPlayer) return false
        val player = state.players[state.currentPlayer]
        val card = if (player.trait == TraitType.NAVIGATOR) {
            val id = burnCardId ?: return false
            state.burnDeck.firstOrNull { it.id == id } ?: return false
        } else {
            state.burnDeck.firstOrNull() ?: return false
        }
        if (state.board.any { it.type == card.type }) {
            state.message = "Map cannot place another ${card.type.displayName} on the current board. Choose another eligible map or skip."
            return false
        }
        state.burnDeck.remove(card)
        state.board += card
        protectIfNeeded(state, card)
        state.pendingEffect = null
        state.message = "${player.name} uses Map to recover ${card.type.displayName} from the Burn Deck."
        addLog(state, state.message)
        finishIfNoCardsRemain(state)
        return true
    }

    fun resolveSword(state: GameState, targetPlayerIndex: Int, bankCardId: Int): Boolean {
        val effect = state.pendingEffect ?: return false
        if (effect.cardType != CardType.SWORD || effect.playerIndex != state.currentPlayer) return false
        if (targetPlayerIndex !in state.players.indices || targetPlayerIndex == state.currentPlayer) return false
        val attacker = state.players[state.currentPlayer]
        val target = state.players[targetPlayerIndex]
        val stolen = target.bank.firstOrNull { it.id == bankCardId } ?: return false
        if (attacker.trait != TraitType.SWORDSMAN && attacker.bank.any { it.type == stolen.type }) {
            state.message = "${attacker.name} already owns ${stolen.type.displayName}; Swordsman is required to steal another."
            return false
        }
        target.bank.remove(stolen)
        attacker.bank += stolen
        state.pendingEffect = null
        state.message = "${attacker.name}'s Sword steals ${stolen.type.displayName} from ${target.name}."
        addLog(state, state.message)
        finishIfNoCardsRemain(state)
        return true
    }

    fun skipPendingEffect(state: GameState): Boolean {
        val effect = state.pendingEffect ?: return false
        state.pendingEffect = null
        state.message = "${state.players[state.currentPlayer].name} skips the ${effect.cardType.displayName} effect."
        addLog(state, state.message)
        finishIfNoCardsRemain(state)
        return true
    }

    fun collect(state: GameState, rivalTargetIndex: Int? = null): Boolean {
        if (state.finished) return false
        if (state.pendingEffect != null) {
            state.message = "Resolve or skip the pending ${state.pendingEffect!!.cardType.displayName} effect first."
            return false
        }
        if (state.pendingForcedDraws > 0) {
            state.message = "You must draw ${state.pendingForcedDraws} more card(s) for Kraken."
            return false
        }
        val player = state.players[state.currentPlayer]
        val boardCards = state.board.toList()
        val boardCount = boardCards.size
        val hasChestAndKey = boardCards.any { it.type == CardType.CHEST } &&
            boardCards.any { it.type == CardType.KEY }
        player.bank += boardCards
        state.board.clear()
        state.protectedCardIds.clear()
        state.protectedDrawsRemaining = 0

        if (hasChestAndKey && state.rules.chestKeyBonusEnabled &&
            (player.trait == TraitType.PLUNDERER || state.burnDeck.isNotEmpty())) {
            val multiplier = if (player.trait == TraitType.TREASURE_HUNTER) 3 else 1
            val requestedBonus = boardCount * multiplier
            val bonusCount = if (player.trait == TraitType.PLUNDERER) requestedBonus else minOf(state.burnDeck.size, requestedBonus)
            if (player.trait == TraitType.PLUNDERER) {
                val targetIndex = rivalTargetIndex?.takeIf { it in state.players.indices && it != player.id }
                    ?: state.players.filter { it.id != player.id }.maxByOrNull { it.bank.size }?.id
                val rival = targetIndex?.let { state.players[it] }
                if (rival == null || rival.bank.isEmpty()) {
                    state.message = "Chest + Key found, but no rival has banked treasure to plunder."
                } else {
                    val stolen = rival.bank.sortedByDescending { it.value }.take(bonusCount)
                    rival.bank.removeAll(stolen.toSet())
                    player.bank += stolen
                    state.message = "Plunderer steals ${stolen.size} bonus card(s) from ${rival.name}'s bank."
                }
            } else {
                repeat(bonusCount) { player.bank += state.burnDeck.removeAt(0) }
                state.message = if (multiplier == 3) {
                    "${player.name} collects the board and Treasure Hunter triples the Burn Deck bonus to ${bonusCount} card(s)."
                } else {
                    "${player.name} collects the board and claims ${bonusCount} Chest + Key bonus card(s)."
                }
            }
        } else {
            state.message = "${player.name} banks ${boardCount} board card(s)."
        }
        addLog(state, state.message)
        if (state.drawDeck.isEmpty()) finish(state) else nextPlayer(state)
        return true
    }

    fun setTrait(state: GameState, playerIndex: Int, trait: TraitType) {
        require(playerIndex in state.players.indices)
        state.players[playerIndex].trait = trait
        if (trait == TraitType.DAVY_JONES_LOCKER) {
            state.davyJonesTargets[playerIndex] = state.players.firstOrNull { it.id != playerIndex }?.id ?: playerIndex
        } else {
            state.davyJonesTargets.remove(playerIndex)
        }
        addLog(state, "${state.players[playerIndex].name} chooses ${trait.displayName}.")
    }

    fun setDavyJonesTarget(state: GameState, ownerIndex: Int, targetIndex: Int): Boolean {
        if (ownerIndex !in state.players.indices || targetIndex !in state.players.indices || ownerIndex == targetIndex) return false
        if (state.players[ownerIndex].trait != TraitType.DAVY_JONES_LOCKER) return false
        state.davyJonesTargets[ownerIndex] = targetIndex
        addLog(state, "${state.players[ownerIndex].name} marks ${state.players[targetIndex].name} for Davy Jones' Locker.")
        return true
    }

    fun scoreFor(player: PlayerData): Int {
        return CardType.entries.sumOf { type ->
            val cards = player.bank.filter { it.type == type }
            (cards.maxOfOrNull { it.value } ?: 0) +
                if (type == CardType.MERMAID && player.trait == TraitType.GOLDEN_SCALES && cards.isNotEmpty()) 5 else 0
        }
    }

    fun scoreBreakdown(player: PlayerData): List<String> {
        return CardType.entries.mapNotNull { type ->
            val cards = player.bank.filter { it.type == type }
            if (cards.isEmpty()) null else {
                val high = cards.maxOf { it.value }
                val traitBonus = if (type == CardType.MERMAID && player.trait == TraitType.GOLDEN_SCALES) 5 else 0
                "${type.symbol} ${type.displayName}: highest ${high}" +
                    if (traitBonus > 0) " + Golden Scales 5 = ${high + traitBonus} points (${cards.size} card(s))"
                    else " = ${high} point(s) (${cards.size} card(s))"
            }
        }.ifEmpty { listOf("No banked cards.") }
    }

    fun finish(state: GameState) {
        if (state.finished) return
        state.finished = true
        state.players.forEach { player ->
            player.score = scoreFor(player)
        }
        val winner = state.players.sortedWith(compareByDescending<PlayerData> { it.score }.thenByDescending { it.bank.size }).firstOrNull()
        val gameOverMessage = "Game over! Winner: ${winner?.name ?: "nobody"}."
        val previousMessage = state.message
        val keepPreviousAction = previousMessage.contains("Bust!", ignoreCase = true) ||
            previousMessage.contains("Davy Jones", ignoreCase = true) ||
            previousMessage.contains("banks", ignoreCase = true) ||
            previousMessage.contains("collect", ignoreCase = true) ||
            previousMessage.contains("Draw Deck exhausted", ignoreCase = true)
        state.message = if (keepPreviousAction && !previousMessage.contains("Game over!", ignoreCase = true)) {
            "$previousMessage $gameOverMessage"
        } else gameOverMessage
        addLog(state, state.message)
    }

    fun playAiTurnsUntilHuman(state: GameState) {
        var safety = 0
        while (!state.finished && !state.passAndPlay && state.currentPlayer != 0 && safety++ < 200) {
            val player = state.players[state.currentPlayer]
            if (player.trait == null) {
                setTrait(state, player.id, TraitType.entries[random.nextInt(TraitType.entries.size)])
            }
            val turnOwner = player.id
            var drawsThisTurn = 0
            var shouldStop = false
            while (!state.finished && state.currentPlayer == turnOwner && !shouldStop) {
                if (state.pendingEffect != null) {
                    resolveAiEffect(state)
                    continue
                }
                if (state.pendingForcedDraws > 0 || drawsThisTurn == 0) {
                    draw(state)
                    drawsThisTurn++
                    if (state.currentPlayer != turnOwner || state.finished) break
                    if (state.pendingEffect != null) continue
                    if (state.pendingForcedDraws > 0) continue
                }
                if (state.drawDeck.isEmpty()) {
                    if (state.board.isNotEmpty()) collect(state)
                    shouldStop = true
                } else if (shouldAiCollect(state, drawsThisTurn)) {
                    collect(state)
                    shouldStop = true
                } else {
                    draw(state)
                    drawsThisTurn++
                    if (state.currentPlayer != turnOwner) shouldStop = true
                }
            }
        }
        if (!state.finished && state.drawDeck.isEmpty() && state.board.isNotEmpty() &&
            state.currentPlayer != 0 && !state.passAndPlay) {
            collect(state)
        }
    }

    private fun shouldAiCollect(state: GameState, drawsThisTurn: Int): Boolean {
        if (state.board.isEmpty()) return false
        return when (state.difficulty) {
            0 -> true
            1 -> drawsThisTurn >= 2 || state.drawDeck.size <= 5 || random.nextInt(3) == 0
            else -> {
                val boardTypes = state.board.map { it.type }.toSet()
                val duplicatesRemaining = state.drawDeck.count { it.type in boardTypes }
                val bustProbability = if (state.drawDeck.isEmpty()) 1.0 else duplicatesRemaining.toDouble() / state.drawDeck.size
                val boardSize = state.board.size
                bustProbability >= 0.16 || boardSize >= 5 || (boardSize >= 3 && bustProbability >= 0.10) ||
                    (state.drawDeck.size <= 3 && boardSize >= 2)
            }
        }
    }

    private fun resolveAiEffect(state: GameState) {
        val effect = state.pendingEffect ?: return
        val active = state.players[state.currentPlayer]
        val resolved = when (effect.cardType) {
            CardType.CANNON -> {
                val target = state.players.filter { it.id != active.id && it.bank.isNotEmpty() }
                    .maxByOrNull { it.bank.size }
                if (target == null) {
                    skipPendingEffect(state)
                } else if (target.trait == TraitType.MISFIRE) {
                    resolveCannon(state, target.id)
                } else {
                    val type = target.bank.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key
                    if (type == null) skipPendingEffect(state) else resolveCannon(state, target.id, type)
                }
            }
            CardType.HOOK -> {
                val allowed = if (active.trait == TraitType.CAPTAINS_HOOK) 2 else 1
                val ids = active.bank
                    .filter { bankCard -> state.board.none { it.type == bankCard.type } }
                    .groupBy { it.type }
                    .values
                    .mapNotNull { sameType -> sameType.maxByOrNull { it.value } }
                    .sortedByDescending { it.value }
                    .take(allowed)
                    .map { it.id }
                resolveHook(state, ids)
            }
            CardType.MAP -> {
                val candidates = state.burnDeck.filter { card -> state.board.none { it.type == card.type } }
                val chosen = if (active.trait == TraitType.NAVIGATOR) candidates.maxByOrNull { it.value }
                    else state.burnDeck.firstOrNull()?.takeIf { first -> candidates.any { it.id == first.id } }
                if (chosen == null) skipPendingEffect(state) else resolveMap(state, chosen.id)
            }
            CardType.SWORD -> {
                val target = state.players.filter { it.id != active.id && it.bank.isNotEmpty() }
                    .maxByOrNull { it.bank.size }
                val stealable = target?.bank?.filter {
                    active.trait == TraitType.SWORDSMAN || active.bank.none { owned -> owned.type == it.type }
                }?.maxByOrNull { it.value }
                if (target == null || stealable == null) skipPendingEffect(state)
                else resolveSword(state, target.id, stealable.id)
            }
            else -> skipPendingEffect(state)
        }
        if (!resolved && state.pendingEffect != null) skipPendingEffect(state)
    }

    private fun finishIfNoCardsRemain(state: GameState) {
        if (!state.finished && state.drawDeck.isEmpty() && state.pendingForcedDraws > 0 && state.pendingEffect == null) {
            val stranded = state.pendingForcedDraws
            state.pendingForcedDraws = 0
            state.message += " Draw Deck exhausted; ${stranded} forced draw(s) cannot be made."
            addLog(state, state.message)
        }
        if (!state.finished && state.drawDeck.isEmpty() && state.board.isEmpty() && state.pendingEffect == null) finish(state)
    }

    private fun nextPlayer(state: GameState) {
        state.pendingForcedDraws = 0
        state.turnNumber++
        state.currentPlayer = (state.currentPlayer + 1) % state.players.size
        if (state.drawDeck.isEmpty() && state.board.isEmpty() && state.pendingEffect == null) {
            finish(state)
        } else if (!state.finished) {
            state.message = "${state.players[state.currentPlayer].name}'s turn."
            addLog(state, state.message)
        }
    }

    private fun addLog(state: GameState, entry: String) {
        state.turnLog += "Turn ${state.turnNumber}: $entry"
        while (state.turnLog.size > 80) state.turnLog.removeAt(0)
    }
}
