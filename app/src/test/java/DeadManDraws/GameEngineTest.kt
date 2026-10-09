package DeadManDraws

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    @Test fun newGameHasExpectedDeckSizesAndPlayerCount() {
        val state = GameEngine(Random(7)).newGame(4)
        assertEquals(50, state.drawDeck.size)
        assertEquals(10, state.burnDeck.size)
        assertEquals(4, state.players.size)
    }

    @Test fun cardValuesAreWithinTheirRanges() {
        val state = GameEngine(Random(11)).newGame(2)
        (state.drawDeck + state.burnDeck).forEach { card ->
            if (card.type == CardType.MERMAID) assertTrue(card.value in 4..9)
            else assertTrue(card.value in 2..7)
        }
    }

    @Test fun playerCountIsValidated() {
        try {
            GameEngine().newGame(1)
            fail("Expected invalid player count to be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
