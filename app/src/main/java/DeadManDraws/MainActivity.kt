package DeadManDraws

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private val engine = GameEngine()
    private var state: GameState? = null
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var boardText: TextView
    private lateinit var bankText: TextView
    private lateinit var playersText: TextView
    private var difficulty = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 27, 35)
        window.navigationBarColor = Color.rgb(13, 27, 35)
        showSetup()
    }

    private fun base(title: String): LinearLayout {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 24)
            setBackgroundColor(Color.rgb(13, 27, 35))
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = title; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(242, 190, 91)); setPadding(0, 8, 0, 20)
        })
        return root
    }

    private fun label(text: String, size: Float = 16f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.WHITE)
        setPadding(4, 8, 4, 8)
    }

    private fun button(text: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 8
        })
    }

    private fun showSetup() {
        base("☠  DEAD MAN'S DRAW")
        root.addView(label("A pirate push-your-luck card game", 17f))
        root.addView(label("Players: 2–8"))
        val countLabel = label("Players: 4")
        root.addView(countLabel)
        val players = SeekBar(this).apply { max = 6; progress = 2 }
        players.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { countLabel.text = "Players: ${progress + 2}" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(players)
        root.addView(label("AI difficulty"))
        val difficultyLabel = label("Normal")
        root.addView(difficultyLabel)
        val levels = SeekBar(this).apply { max = 2; progress = 1 }
        levels.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                difficulty = progress
                difficultyLabel.text = listOf("Easy", "Normal", "Hard")[progress]
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(levels)
        button("NEW GAME") { state = engine.newGame(players.progress + 2, difficulty); chooseTrait() }
        button("HOW TO PLAY") {
            base("HOW TO PLAY")
            root.addView(label("Draw at least one card each turn. A repeated card type burns the whole board. Collect before busting to bank the board. When the draw deck is empty, highest score wins; score the highest value of each card type."))
            button("BACK") { showSetup() }
        }
    }

    private fun chooseTrait() {
        val game = state ?: return
        base("CHOOSE YOUR TRAIT")
        val choices = TraitType.entries.shuffled().take(2)
        choices.forEach { trait ->
            button("${trait.displayName}\n${trait.description}") {
                engine.setTrait(game, 0, trait)
                renderGame()
            }
        }
    }

    private fun renderGame() {
        val game = state ?: return
        base("☠  DEAD MAN'S DRAW")
        status = label(game.message, 17f)
        root.addView(status)
        root.addView(label("Deck: ${game.drawDeck.size}    Burn deck: ${game.burnDeck.size}"))
        root.addView(label("CURRENT TURN: ${game.players[game.currentPlayer].name}", 20f))
        root.addView(label("Your trait: ${game.players[0].trait?.displayName ?: "None"}"))
        root.addView(label("BOARD"))
        boardText = label(if (game.board.isEmpty()) "No cards on board" else game.board.joinToString("\n") { it.toString() })
        root.addView(boardText)
        root.addView(label("PLAYER BANKS"))
        playersText = label(game.players.joinToString("\n") { p ->
            "${p.name}${if (p.isHuman) " (you)" else " (AI)"} — ${p.bank.size} cards — ${p.score} points" +
                (p.trait?.let { " — ${it.displayName}" } ?: "")
        })
        root.addView(playersText)
        bankText = label("Your bank: " + (game.players[0].bank.groupingBy { it.type }.eachCount().entries.joinToString("  ") { "${it.key.symbol} ${it.value}" }.ifEmpty { "empty" }))
        root.addView(bankText)
        if (game.finished) {
            button("NEW GAME") { showSetup() }
            return
        }
        button("DRAW CARD") {
            engine.draw(game)
            if (game.currentPlayer != 0 && !game.finished) runAiTurns(game)
            renderGame()
        }
        button("COLLECT / END TURN") {
            engine.collect(game)
            if (game.currentPlayer != 0 && !game.finished) runAiTurns(game)
            renderGame()
        }
        button("SHOW TRAIT HELP") {
            base("TRAIT HELP")
            root.addView(label(game.players[0].trait?.description ?: "No trait selected."))
            button("BACK TO GAME") { renderGame() }
        }
        button("NEW GAME") { showSetup() }
    }

    private fun runAiTurns(game: GameState) {
        var guard = 0
        while (!game.finished && game.currentPlayer != 0 && guard++ < 30) {
            val player = game.players[game.currentPlayer]
            if (player.trait == null) player.trait = TraitType.entries.random()
            var draws = 0
            do {
                engine.draw(game)
                draws++
            } while (!game.finished && game.currentPlayer == player.id &&
                game.pendingForcedDraws > 0 && draws < 6)
            if (!game.finished && game.currentPlayer == player.id) {
                if (game.board.isNotEmpty() && (draws >= 2 || game.drawDeck.size < 5 || (0..2).random() == 0)) engine.collect(game)
                else if (draws < 12) engine.draw(game)
            }
        }
    }
}
