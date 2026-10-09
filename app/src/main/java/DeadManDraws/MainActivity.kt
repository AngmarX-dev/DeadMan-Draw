package DeadManDraws

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.sin

class MainActivity : Activity() {
    private val engine = GameEngine()
    private var state: GameState? = null
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var bankText: TextView
    private lateinit var playersText: TextView
    private var difficulty = 1

    private val navy = Color.rgb(13, 27, 35)
    private val panelColor = Color.rgb(24, 43, 52)
    private val gold = Color.rgb(242, 190, 91)
    private val mutedGold = Color.rgb(112, 91, 56)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        showSetup()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedDrawable(fill: Int, stroke: Int = Color.TRANSPARENT, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }

    private fun base(title: String): LinearLayout {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(navy)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(26))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(13, 27, 35), Color.rgb(20, 39, 46), Color.rgb(13, 27, 35))
            )
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(scroll)
        val heading = TextView(this).apply {
            text = title
            textSize = 27f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(gold)
            setShadowLayer(dp(10).toFloat(), 0f, 0f, Color.rgb(133, 79, 25))
            setPadding(0, dp(8), 0, dp(18))
        }
        root.addView(heading)
        root.alpha = 0f
        root.translationY = dp(14).toFloat()
        root.post {
            root.animate().alpha(1f).translationY(0f).setDuration(300)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        return root
    }

    private fun label(text: String, size: Float = 16f): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(dp(4), dp(7), dp(4), dp(7))
    }

    private fun panel(text: String, size: Float = 16f): TextView = label(text, size).apply {
        background = roundedDrawable(panelColor, mutedGold, 1)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun button(text: String, action: () -> Unit) {
        val control = Button(this).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.rgb(25, 32, 34))
            background = roundedDrawable(gold)
            elevation = dp(3).toFloat()
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                isEnabled = false
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(65)
                    .withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(95)
                            .withEndAction { action() }.start()
                    }.start()
            }
        }
        root.addView(control, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(9)
            topMargin = dp(2)
        })
    }

    private fun showSetup() {
        base("☠  DEAD MAN'S DRAW")
        root.addView(panel("A PIRATE PUSH-YOUR-LUCK CARD GAME", 16f))
        root.addView(label("Build your haul. Read the table. Know when to walk away.", 15f))
        root.addView(label("CREW SIZE", 14f))
        val countLabel = panel("Players: 4", 17f)
        root.addView(countLabel)
        val players = SeekBar(this).apply { max = 6; progress = 2 }
        players.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                countLabel.text = "Players: ${progress + 2}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(players)
        root.addView(label("AI DIFFICULTY", 14f))
        val difficultyLabel = panel("Normal", 17f)
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
        button("⚓  NEW VOYAGE") {
            state = engine.newGame(players.progress + 2, difficulty)
            chooseTrait()
        }
        button("📜  HOW TO PLAY") {
            base("HOW TO PLAY")
            root.addView(panel("1. DRAW", 17f))
            root.addView(label("Draw cards to grow the board. Every card type can appear only once on the board."))
            root.addView(panel("2. WATCH FOR A BUST", 17f))
            root.addView(label("Drawing a duplicate type burns the current board. You lose the unbanked haul."))
            root.addView(panel("3. COLLECT", 17f))
            root.addView(label("Collect to bank the board and pass your turn. Kraken may force extra draws before you can collect."))
            root.addView(panel("4. SCORE THE TREASURE", 17f))
            root.addView(label("When the draw deck is exhausted, the highest card value of each type contributes to your score. Chest + Key can grant a Burn Deck bonus."))
            button("BACK TO PORT") { showSetup() }
        }
    }

    private fun chooseTrait() {
        val game = state ?: return
        base("CHOOSE YOUR TRAIT")
        root.addView(label("Choose a pirate ability for this voyage.", 16f))
        val choices = TraitType.entries.shuffled().take(2)
        choices.forEach { trait ->
            button("✦  ${trait.displayName}\n${trait.description}") {
                engine.setTrait(game, 0, trait)
                renderGame()
            }
        }
    }

    private fun renderGame() {
        val game = state ?: return
        base("☠  DEAD MAN'S DRAW")
        status = panel(game.message, 16f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (game.message.contains("Bust!", ignoreCase = true)) {
                Color.rgb(255, 142, 112)
            } else gold)
        }
        root.addView(status)
        if (game.message.contains("Bust!", ignoreCase = true)) {
            val shake = TranslateAnimation(-dp(7).toFloat(), dp(7).toFloat(), 0f, 0f).apply {
                duration = 48
                repeatCount = 5
                repeatMode = android.view.animation.Animation.REVERSE
            }
            status.startAnimation(shake)
        } else {
            status.startAnimation(TranslateAnimation(0f, 0f, dp(12).toFloat(), 0f).apply {
                duration = 280
                interpolator = DecelerateInterpolator()
            })
        }

        root.addView(label("DECK  ${game.drawDeck.size}     •     BURN PILE  ${game.burnDeck.size}", 13f))
        root.addView(panel("⚑  CURRENT TURN: ${game.players[game.currentPlayer].name}", 18f))
        root.addView(label("Your trait: ${game.players[0].trait?.displayName ?: "None"}", 14f))

        root.addView(label("TREASURE BOARD  •  ${game.board.size} CARDS", 17f))
        if (game.board.isEmpty()) {
            root.addView(panel("The sea is clear. Draw your first card!", 15f))
        } else {
            val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            game.board.forEachIndexed { index, card ->
                val tile = TextView(this).apply {
                    text = "${card.type.symbol}\n${card.type.displayName.uppercase()}\nVALUE  ${card.value}"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(dp(7), dp(10), dp(7), dp(10))
                    background = roundedDrawable(cardColor(card.type), gold, 1)
                    elevation = dp(5).toFloat()
                    alpha = 0f
                    scaleX = 0.75f
                    scaleY = 0.75f
                    translationY = dp(14).toFloat()
                    rotation = if (index % 2 == 0) -4f else 4f
                }
                strip.addView(tile, LinearLayout.LayoutParams(dp(112), dp(112)).apply {
                    marginEnd = dp(8)
                    bottomMargin = dp(8)
                    topMargin = dp(3)
                })
                tile.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).rotation(0f)
                    .setStartDelay(index * 55L).setDuration(330)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            root.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(strip)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        root.addView(label("PLAYER STANDINGS", 17f))
        playersText = panel(game.players.joinToString("\n") { p ->
            "${if (p.isHuman) "👑 " else "🏴‍☠️ "}${p.name} — ${p.bank.size} cards — ${p.score} pts" +
                (p.trait?.let { "  •  ${it.displayName}" } ?: "")
        }, 14f)
        root.addView(playersText)
        bankText = panel("YOUR BANK\n" + (game.players[0].bank.groupingBy { it.type }.eachCount()
            .entries.joinToString("     ") { "${it.key.symbol} ${it.value}" }.ifEmpty { "Nothing banked yet" }), 14f)
        root.addView(bankText)

        if (game.finished) {
            root.addView(label("🏆  VOYAGE COMPLETE  🏆", 23f).apply {
                gravity = Gravity.CENTER
                setTextColor(gold)
                typeface = Typeface.DEFAULT_BOLD
            })
            root.addView(ConfettiView(this), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)
            ))
            button("⚓  PLAY AGAIN") { showSetup() }
            return
        }

        button("🃏  DRAW A CARD") {
            engine.draw(game)
            if (game.currentPlayer != 0 && !game.finished) runAiTurns(game)
            renderGame()
        }
        button("💰  COLLECT / END TURN") {
            engine.collect(game)
            if (game.currentPlayer != 0 && !game.finished) runAiTurns(game)
            renderGame()
        }
        button("✦  SHOW TRAIT HELP") {
            base("TRAIT HELP")
            root.addView(panel(game.players[0].trait?.displayName ?: "No trait selected", 18f))
            root.addView(label(game.players[0].trait?.description ?: "Choose a trait before setting sail."))
            button("BACK TO THE TABLE") { renderGame() }
        }
        button("↻  NEW GAME") { showSetup() }
    }

    private fun cardColor(type: CardType): Int = when (type) {
        CardType.ANCHOR -> Color.rgb(40, 86, 112)
        CardType.CANNON -> Color.rgb(128, 55, 43)
        CardType.CHEST -> Color.rgb(139, 96, 31)
        CardType.HOOK -> Color.rgb(72, 92, 107)
        CardType.KEY -> Color.rgb(122, 102, 42)
        CardType.KRAKEN -> Color.rgb(91, 46, 91)
        CardType.MAP -> Color.rgb(57, 104, 72)
        CardType.MERMAID -> Color.rgb(37, 112, 119)
        CardType.ORACLE -> Color.rgb(84, 66, 130)
        CardType.SWORD -> Color.rgb(89, 74, 68)
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

    private inner class ConfettiView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var progress = 0f
        private val colors = intArrayOf(
            gold, Color.rgb(237, 92, 68), Color.rgb(89, 190, 166),
            Color.rgb(104, 154, 231), Color.rgb(232, 220, 172)
        )

        init {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1300L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0) return
            val w = width.toFloat()
            val h = height.toFloat()
            repeat(36) { i ->
                val x = (i * 47 % width).toFloat()
                val startY = -((i * 19) % 75).toFloat()
                val y = (startY + (h + 80f) * progress + (i % 4) * 8f) % (h + 10f)
                paint.color = colors[i % colors.size]
                val drift = (sin(progress * 7f + i) * 9f)
                if (i % 2 == 0) canvas.drawCircle((x + drift).coerceIn(0f, w), y, 2.5f + (i % 3), paint)
                else canvas.drawRect(x, y, x + 5f, y + 8f, paint)
            }
        }
    }
}
