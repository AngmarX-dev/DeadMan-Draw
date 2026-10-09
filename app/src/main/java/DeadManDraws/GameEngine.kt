package DeadManDraws

import kotlin.random.Random

enum class CardType(val displayName: String, val symbol: String) {
    ANCHOR("Anchor", "⚓"), CANNON("Cannon", "💣"), CHEST("Chest", "🧰"),
    HOOK("Hook", "🪝"), KEY("Key", "🗝"), KRAKEN("Kraken", "🐙"),
    MAP("Map", "🗺"), MERMAID("Mermaid", "🧜"), ORACLE("Oracle", "🔮"), SWORD("Sword", "⚔")
}
data class CardDefinition(val id: Int, val type: CardType, val value: Int) {
    override fun toString() = "${type.symbol} ${type.displayName} · $value"
}
enum class TraitType(val displayName: String, val description: String) {
    BEASTMASTER("Beastmaster", "Kraken effects make opponents draw four cards instead of two."),
    CAPTAINS_HOOK("Captain's Hook", "Hook effects bring two banked cards into play."),
    CASANOVA("Casanova", "Mermaids are banked immediately."),
    DAVY_JONES_LOCKER("Davy Jones' Locker", "Choose a rival; gain their board cards if they bust."),
    FISHERMAN("Fisherman", "Kraken is banked immediately and does not force extra draws."),
    GOLDEN_SCALES("Golden Scales", "Banked Mermaids are worth five extra points."),
    MISER("Miser", "Cards brought back with Hook are protected from bust."),
    NAVIGATOR("Navigator", "Choose any card from the Burn Deck when using Map."),
    MASTER_GUNNER("Master Gunner", "Cannon removes the whole selected card type from a rival."),
    MISFIRE("Misfire", "Rivals cannot target you with Cannon; they burn a top bank card instead."),
    MYSTIC("Mystic", "Oracle reveals three upcoming cards."),
    PARRY("Parry", "Rivals drawing Sword must play a banked Kraken, if available."),
    PLUNDERER("Plunderer", "Chest + Key bonus comes from one rival's bank."),
    SAFE_HARBOR("Safe Harbor", "Anchor protects itself and the next two board cards."),
    SCAVENGER("Scavenger", "Keep the card stolen by Cannon instead of burning it."),
    SWORDSMAN("Swordsman", "Sword can steal a type you already own."),
    TREASURE_HUNTER("Treasure Hunter", "Banked Chest + Key triples the Burn Deck bonus.")
}
data class PlayerData(
    val id: Int,
    val name: String,
    val isHuman: Boolean,
    var trait: TraitType? = null,
    val bank: MutableList<CardDefinition> = mutableListOf(),
    var score: Int = 0
)
data class GameState(
    val players: MutableList<PlayerData>,
    val drawDeck: MutableList<CardDefinition>,
    val burnDeck: MutableList<CardDefinition>,
    val board: MutableList<CardDefinition> = mutableListOf(),
    var currentPlayer: Int = 0,
    var finished: Boolean = false,
    var message: String = "Your turn. Draw a card or collect your haul.",
    var pendingForcedDraws: Int = 0
)

class GameEngine(private val random: Random = Random.Default) {
    fun newGame(playerCount: Int, difficulty: Int = 1): GameState {
        require(playerCount in 2..8)
        require(difficulty in 0..2)
        val players = (0 until playerCount).map { PlayerData(it, if (it == 0) "You" else "Pirate ${it + 1}", it == 0) }.toMutableList()
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
        return GameState(players, all, burn)
    }

    fun draw(state: GameState): CardDefinition? {
        if (state.finished || state.drawDeck.isEmpty()) { finish(state); return null }
        val player = state.players[state.currentPlayer]
        val card = state.drawDeck.removeAt(0)
        if (state.pendingForcedDraws > 0) state.pendingForcedDraws--
        if (player.trait == TraitType.CASANOVA && card.type == CardType.MERMAID) {
            player.bank += card
            state.message = "${player.name} draws a Mermaid and banks it immediately."
            return card
        }
        if (player.trait == TraitType.FISHERMAN && card.type == CardType.KRAKEN) {
            player.bank += card
            state.message = "${player.name}'s Fisherman banks Kraken immediately."
            return card
        }
        val duplicate = state.board.any { it.type == card.type }
        if (duplicate) {
            state.burnDeck += state.board
            state.board.clear()
            state.burnDeck += card
            state.message = "Bust! Duplicate ${card.type.displayName}; the board is burned."
            state.pendingForcedDraws = 0
            nextPlayer(state)
            return card
        }
        state.board += card
        state.message = "${player.name} draws $card."
        when (card.type) {
            CardType.ANCHOR -> state.message += " Anchor: previous board cards are protected."
            CardType.KRAKEN -> {
                val forced = if (player.trait == TraitType.BEASTMASTER) 4 else if (player.trait == TraitType.FISHERMAN) 0 else 2
                state.pendingForcedDraws = maxOf(state.pendingForcedDraws, forced)
                if (state.pendingForcedDraws > 0) state.message += " Must draw ${state.pendingForcedDraws} more."
            }
            CardType.ORACLE -> {
                val next = state.drawDeck.firstOrNull()
                if (next != null) state.message += " Next card: ${next.type.displayName}."
            }
            CardType.CANNON -> state.message += " Cannon effect selection is available in a future update."
            CardType.HOOK -> state.message += " Hook effect selection is available in a future update."
            CardType.MAP -> state.message += " Map effect selection is available in a future update."
            CardType.SWORD -> state.message += " Sword effect selection is available in a future update."
            else -> Unit
        }
        if (state.drawDeck.isEmpty()) finish(state)
        return card
    }

    fun collect(state: GameState) {
        if (state.finished) return
        if (state.pendingForcedDraws > 0) {
            state.message = "You must draw ${state.pendingForcedDraws} more card(s) for Kraken."
            return
        }
        val player = state.players[state.currentPlayer]
        val boardCount = state.board.size
        val boardHasChestAndKey = state.board.any { it.type == CardType.CHEST } &&
            state.board.any { it.type == CardType.KEY }
        player.bank += state.board
        state.board.clear()
        if (player.trait == TraitType.TREASURE_HUNTER &&
            player.bank.any { it.type == CardType.CHEST } && player.bank.any { it.type == CardType.KEY }) {
            repeat(minOf(state.burnDeck.size, boardCount * 3)) { player.bank += state.burnDeck.removeAt(0) }
        } else if (boardHasChestAndKey) {
            repeat(minOf(state.burnDeck.size, boardCount)) { player.bank += state.burnDeck.removeAt(0) }
        }
        state.message = "${player.name} banks their cards."
        nextPlayer(state)
    }

    fun setTrait(state: GameState, playerIndex: Int, trait: TraitType) {
        state.players[playerIndex].trait = trait
    }

    private fun nextPlayer(state: GameState) {
        if (state.drawDeck.isEmpty()) { finish(state); return }
        state.currentPlayer = (state.currentPlayer + 1) % state.players.size
        state.pendingForcedDraws = 0
    }

    fun finish(state: GameState) {
        state.finished = true
        state.players.forEach { player ->
            player.score = CardType.entries.sumOf { type ->
                val cards = player.bank.filter { it.type == type }
                (cards.maxOfOrNull { it.value } ?: 0) +
                    if (type == CardType.MERMAID && player.trait == TraitType.GOLDEN_SCALES && cards.isNotEmpty()) 5 else 0
            }
        }
        val winner = state.players.sortedWith(compareByDescending<PlayerData> { it.score }.thenByDescending { it.bank.size }).firstOrNull()
        state.message = "Game over! Winner: ${winner?.name ?: "nobody"}."
    }
}
