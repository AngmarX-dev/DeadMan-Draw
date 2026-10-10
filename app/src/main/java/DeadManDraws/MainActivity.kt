package DeadManDraws

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.math.sin

class MainActivity : Activity() {
    private val engine = GameEngine()
    private var state: GameState? = null
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var difficulty = 1
    private var lastPlayerCount = 4
    private var lastDifficulty = 1
    private var lastRules = GameRules()
    private var lastPassAndPlay = false
    private var captainName = "Captain"
    private var rivalNames: List<String> = emptyList()
    private var lastFeedbackMessage = ""
    private var themeIndex = 0
    private var reducedMotion = false
    private var soundEnabled = false
    private var hapticsEnabled = true
    private var textScale = 1f
    private var trackedFinishedGame = false
    private val selectedHookIds = mutableSetOf<Int>()
    private var toneGenerator: ToneGenerator? = null

    private var navy = Color.rgb(13, 27, 35)
    private var panelColor = Color.rgb(24, 43, 52)
    private var gold = Color.rgb(242, 190, 91)
    private var mutedGold = Color.rgb(112, 91, 56)

    private val themes = listOf(
        Theme("Deep Ocean", Color.rgb(13, 27, 35), Color.rgb(24, 43, 52), Color.rgb(242, 190, 91), Color.rgb(112, 91, 56)),
        Theme("Black Pearl", Color.rgb(17, 15, 25), Color.rgb(38, 31, 48), Color.rgb(225, 190, 255), Color.rgb(105, 79, 126)),
        Theme("Tropical Reef", Color.rgb(9, 34, 37), Color.rgb(19, 55, 57), Color.rgb(114, 229, 198), Color.rgb(54, 130, 116))
    )

    private data class Theme(val name: String, val background: Int, val panel: Int, val accent: Int, val border: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        themeIndex = prefs.getInt("theme", 0).coerceIn(themes.indices)
        reducedMotion = prefs.getBoolean("reduced_motion", false)
        soundEnabled = prefs.getBoolean("sound", false)
        hapticsEnabled = prefs.getBoolean("haptics", true)
        textScale = prefs.getFloat("text_scale", 1f).coerceIn(0.85f, 1.2f)
        applyTheme(themeIndex)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        val saved = loadSavedGame()
        if (saved != null && !saved.finished) {
            state = saved
            lastPlayerCount = saved.players.size
            lastDifficulty = saved.difficulty
            lastRules = saved.rules.copy()
            lastPassAndPlay = saved.passAndPlay
            captainName = saved.players.firstOrNull()?.name ?: "Captain"
            rivalNames = saved.players.drop(1).map { it.name }
            AlertDialog.Builder(this)
                .setTitle("Resume your voyage?")
                .setMessage("An unfinished game was saved. Continue it or start a new voyage.")
                .setPositiveButton("RESUME") { _, _ -> if (saved.players.firstOrNull()?.trait == null) chooseTrait() else renderGame() }
                .setNegativeButton("NEW VOYAGE") { _, _ ->
                    state = null
                    deleteSavedGame()
                    showSetup()
                }
                .setOnCancelListener { renderGame() }
                .show()
        } else {
            deleteSavedGame()
            showSetup()
            if (!prefs.getBoolean("tutorial_seen", false)) {
                AlertDialog.Builder(this)
                    .setTitle("Welcome aboard")
                    .setMessage("Draw cards to grow your haul, but duplicate card types can burn your unbanked treasure. Use Collect to bank the board. Special cards have their own actions, and Kraken can force extra draws. Keep an eye on the turn log and card glossary.")
                    .setPositiveButton("LET'S SAIL") { _, _ ->
                        prefs.edit().putBoolean("tutorial_seen", true).apply()
                    }
                    .show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val game = state
        if (game != null && !game.finished) saveGame(game) else if (game?.finished == true) deleteSavedGame()
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    private fun applyTheme(index: Int) {
        val theme = themes[index.coerceIn(themes.indices)]
        navy = theme.background
        panelColor = theme.panel
        gold = theme.accent
        mutedGold = theme.border
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
            contentDescription = "Scrollable Dead Man's Draw screen"
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val sidePadding = if (resources.configuration.screenWidthDp >= 600) dp(56) else dp(20)
            setPadding(sidePadding, dp(18), sidePadding, dp(26))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(navy, panelColor, navy)
            )
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(scroll)
        val heading = TextView(this).apply {
            text = title
            textSize = 27f * textScale
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(gold)
            setShadowLayer(dp(10).toFloat(), 0f, 0f, mutedGold)
            setPadding(0, dp(8), 0, dp(18))
            contentDescription = title
        }
        root.addView(heading)
        if (!reducedMotion) {
            root.alpha = 0f
            root.translationY = dp(14).toFloat()
            root.post {
                root.animate().alpha(1f).translationY(0f).setDuration(280)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
        return root
    }

    private fun label(text: String, size: Float = 16f): TextView = TextView(this).apply {
        this.text = text
        textSize = size * textScale
        setTextColor(Color.WHITE)
        setPadding(dp(4), dp(7), dp(4), dp(7))
    }

    private fun panel(text: String, size: Float = 16f): TextView = label(text, size).apply {
        background = roundedDrawable(panelColor, mutedGold, 1)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun checkbox(text: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        this.text = text
        isChecked = checked
        textSize = 14f
        setTextColor(Color.WHITE)
        minHeight = dp(48)
        contentDescription = text
    }

    private fun button(text: String, action: () -> Unit) {
        val control = Button(this).apply {
            this.text = text
            contentDescription = text.replace(Regex("[^\\p{L}\\p{N} ]"), "").trim()
            textSize = 15f * textScale
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.rgb(25, 32, 34))
            background = roundedDrawable(gold)
            elevation = dp(3).toFloat()
            setPadding(dp(10), dp(6), dp(10), dp(6))
            minHeight = dp(48)
            setOnClickListener {
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                isEnabled = false
                if (!reducedMotion) {
                    animate().scaleX(0.96f).scaleY(0.96f).setDuration(65)
                        .withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(95)
                                .withEndAction { action() }.start()
                        }.start()
                } else action()
            }
        }
        root.addView(control, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
            topMargin = dp(2)
        })
    }

    private fun showSetup() {
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        base("☠  DEAD MAN'S DRAW")
        root.addView(panel("A PIRATE PUSH-YOUR-LUCK CARD GAME", 16f))
        root.addView(label("Build your haul. Read the table. Know when to walk away.", 15f))
        root.addView(label("CAPTAIN NAME", 14f))
        val nameInput = EditText(this).apply {
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(captainName)
            hint = "Enter your name"
            textSize = 16f * textScale
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            background = roundedDrawable(panelColor, mutedGold, 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            contentDescription = "Captain's display name"
        }
        root.addView(nameInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        root.addView(label("RIVAL NAMES (COMMA SEPARATED)", 14f))
        val rivalNamesInput = EditText(this).apply {
            setSingleLine(true)
            setText(prefs.getString("rival_names", rivalNames.joinToString(", ")).orEmpty())
            hint = "Blackbeard, Redbeard, Sea Wolf..."
            textSize = 15f * textScale
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            background = roundedDrawable(panelColor, mutedGold, 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            contentDescription = "Optional names for rival pirates, separated by commas"
        }
        root.addView(rivalNamesInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        root.addView(label("CREW SIZE", 14f))
        val countLabel = panel("Players: $lastPlayerCount", 17f)
        root.addView(countLabel)
        val players = SeekBar(this).apply { max = 6; progress = (lastPlayerCount - 2).coerceIn(0, 6) }
        players.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                countLabel.text = "Players: ${progress + 2}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(players)

        root.addView(label("AI DIFFICULTY", 14f))
        val difficultyLabel = panel(listOf("Easy", "Normal", "Hard")[lastDifficulty], 17f)
        root.addView(difficultyLabel)
        val levels = SeekBar(this).apply { max = 2; progress = lastDifficulty }
        levels.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                difficulty = progress
                difficultyLabel.text = listOf("Easy", "Normal", "Hard")[progress]
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(levels)

        root.addView(label("VOYAGE RULES", 14f))
        val chestKey = checkbox("Enable Chest + Key bonus", prefs.getBoolean("chest_key_bonus", true))
        val kraken = checkbox("Enable Kraken forced draws", prefs.getBoolean("kraken_pressure", true))
        val passPlay = checkbox("Local pass-and-play (all players human)", lastPassAndPlay)
        root.addView(chestKey)
        root.addView(kraken)
        root.addView(passPlay)

        button("🎨  TABLE THEME: ${themes[themeIndex].name.uppercase()}") {
            themeIndex = (themeIndex + 1) % themes.size
            prefs.edit().putInt("theme", themeIndex).apply()
            applyTheme(themeIndex)
            showSetup()
        }
        val reduced = checkbox("Reduce animations", reducedMotion)
        val sound = checkbox("Enable sound cues", soundEnabled)
        val haptics = checkbox("Enable haptic feedback", hapticsEnabled)
        root.addView(reduced)
        root.addView(sound)
        root.addView(haptics)

        val textScaleStops = listOf(0.85f, 0.95f, 1.0f, 1.1f, 1.2f)
        val textScaleLabels = listOf("85%", "95%", "100%", "110%", "120%")
        val selectedScale = textScaleStops.indexOf(textScale).takeIf { it >= 0 } ?: 2
        val textScaleLabel = panel("TEXT SIZE: ${textScaleLabels[selectedScale]}", 14f)
        root.addView(textScaleLabel)
        val textScaleSeek = SeekBar(this).apply {
            max = textScaleStops.lastIndex
            progress = selectedScale
            contentDescription = "Text size percentage"
        }
        textScaleSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textScaleLabel.text = "TEXT SIZE: ${textScaleLabels[progress]}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(textScaleSeek)

        button("⚓  START NEW VOYAGE") {
            captainName = nameInput.text.toString().trim().ifBlank { "Captain" }.take(24)
            rivalNames = rivalNamesInput.text.toString().split(",").map { it.trim().take(24) }
                .filter { it.isNotBlank() }.distinct().take(7)
            difficulty = levels.progress
            lastRules = GameRules(chestKey.isChecked, kraken.isChecked)
            lastPlayerCount = players.progress + 2
            lastDifficulty = difficulty
            lastPassAndPlay = passPlay.isChecked
            reducedMotion = reduced.isChecked
            soundEnabled = sound.isChecked
            hapticsEnabled = haptics.isChecked
            textScale = textScaleStops[textScaleSeek.progress]
            prefs.edit()
                .putBoolean("chest_key_bonus", chestKey.isChecked)
                .putBoolean("kraken_pressure", kraken.isChecked)
                .putBoolean("reduced_motion", reducedMotion)
                .putBoolean("sound", soundEnabled)
                .putBoolean("haptics", hapticsEnabled)
                .putFloat("text_scale", textScale)
                .putString("rival_names", rivalNames.joinToString(", "))
                .apply()
            startNewGame(lastPlayerCount, lastDifficulty, lastRules, lastPassAndPlay)
        }
        button("📜  HOW TO PLAY") { showHowToPlay() }
        button("📚  CARD GLOSSARY") { showCardGlossary() }
        button("📊  VOYAGE STATISTICS") { showStatistics() }
    }

    private fun startNewGame(playerCount: Int, skill: Int, rules: GameRules, passPlay: Boolean) {
        deleteSavedGame()
        trackedFinishedGame = false
        selectedHookIds.clear()
        state = engine.newGame(playerCount, skill, rules, passPlay)
        state!!.players[0].name = captainName.ifBlank { "Captain" }
        state!!.players.drop(1).forEachIndexed { index, player ->
            player.name = rivalNames.getOrNull(index).orEmpty().ifBlank { "Pirate ${player.id + 1}" }
            engine.setTrait(state!!, player.id, TraitType.entries.random())
        }
        chooseTrait()
    }

    private fun chooseTrait() {
        val game = state ?: return
        base("CHOOSE YOUR TRAIT")
        root.addView(label("${game.players[0].name}, choose your captain's ability for this voyage.", 16f))
        TraitType.entries.shuffled().take(2).forEach { trait ->
            val choice = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedDrawable(panelColor, gold, 1)
                setPadding(dp(10), dp(10), dp(12), dp(10))
                isClickable = true
                isFocusable = true
                contentDescription = "Choose ${trait.displayName}. ${trait.description}"
            }
            val artId = resources.getIdentifier(traitDrawableName(trait), "drawable", packageName)
            if (artId != 0) {
                choice.addView(ImageView(this).apply {
                    setImageResource(artId)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "${trait.displayName} trait illustration"
                }, LinearLayout.LayoutParams(dp(86), dp(104)).apply { marginEnd = dp(12) })
            }
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(label("✦  ${trait.displayName}", 17f))
            copy.addView(label(trait.description, 13f))
            choice.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            choice.setOnClickListener {
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                engine.setTrait(game, 0, trait)
                if (trait == TraitType.DAVY_JONES_LOCKER) chooseDavyJonesTarget(game) else renderGame()
            }
            root.addView(choice, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
        button("📚  READ ALL TRAITS") {
            base("PIRATE TRAIT GUIDE")
            TraitType.entries.forEach { root.addView(panel("${it.displayName}\n${it.description}", 14f)) }
            button("BACK TO TRAIT CHOICE") { chooseTrait() }
        }
    }

    private fun chooseDavyJonesTarget(game: GameState) {
        val rivals = game.players.filter { it.id != 0 }
        AlertDialog.Builder(this)
            .setTitle("Choose a rival")
            .setItems(rivals.map { it.name }.toTypedArray()) { _, which ->
                engine.setDavyJonesTarget(game, 0, rivals[which].id)
                renderGame()
            }
            .setOnCancelListener { renderGame() }
            .show()
    }

    private fun renderGame() {
        val game = state ?: return
        base("☠  DEAD MAN'S DRAW")
        status = panel(game.message, 16f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (game.message.contains("Bust!", ignoreCase = true)) Color.rgb(255, 142, 112) else gold)
        }
        root.addView(status)
        if (!reducedMotion) {
            if (game.message.contains("Bust!", ignoreCase = true)) {
                status.startAnimation(TranslateAnimation(-dp(7).toFloat(), dp(7).toFloat(), 0f, 0f).apply {
                    duration = 48
                    repeatCount = 5
                    repeatMode = android.view.animation.Animation.REVERSE
                })
            } else {
                status.startAnimation(TranslateAnimation(0f, 0f, dp(12).toFloat(), 0f).apply {
                    duration = 250
                    interpolator = DecelerateInterpolator()
                })
            }
        }
        feedbackForMessage(game.message)

        val deckRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addDeckIndicator(drawableName: String, title: String, count: Int, description: String) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedDrawable(panelColor, mutedGold, 1)
                setPadding(dp(8), dp(7), dp(8), dp(7))
                contentDescription = "$description: $count cards"
            }
            val imageId = resources.getIdentifier(drawableName, "drawable", packageName)
            if (imageId != 0) {
                card.addView(ImageView(this).apply {
                    setImageResource(imageId)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = description
                }, LinearLayout.LayoutParams(dp(62), dp(70)))
            }
            card.addView(label("$title  •  $count", 13f).apply { gravity = Gravity.CENTER })
            deckRow.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            })
        }
        addDeckIndicator("backcart", "DRAW DECK", game.drawDeck.size, "Draw Deck")
        addDeckIndicator("backcartburn", "BURN DECK", game.burnDeck.size, "Burn Deck")
        root.addView(deckRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        val active = game.players[game.currentPlayer]
        root.addView(panel("⚑  TURN ${game.turnNumber}: ${active.name}", 18f))
        root.addView(label("Current trait: ${active.trait?.displayName ?: "None"}", 14f))
        if (game.pendingForcedDraws > 0) {
            root.addView(panel("🐙  Kraken pressure: ${game.pendingForcedDraws} compulsory draw(s) remaining.", 14f))
        }

        root.addView(label("TREASURE BOARD  •  ${game.board.size} CARDS", 17f))
        if (game.board.isEmpty()) root.addView(panel("The sea is clear. Draw your first card!", 15f))
        else {
            val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            game.board.forEachIndexed { index, card ->
                val protected = card.id in game.protectedCardIds
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = roundedDrawable(cardColor(card.type), if (protected) Color.rgb(111, 220, 188) else gold, if (protected) 3 else 1)
                    elevation = dp(5).toFloat()
                    contentDescription = "${card.type.displayName}, value ${card.value}${if (protected) ", protected from bust" else ""}"
                    alpha = if (reducedMotion) 1f else 0f
                    scaleX = if (reducedMotion) 1f else 0.75f
                    scaleY = if (reducedMotion) 1f else 0.75f
                    translationY = if (reducedMotion) 0f else dp(14).toFloat()
                    rotation = if (reducedMotion) 0f else if (index % 2 == 0) -4f else 4f
                }
                val cardArtId = resources.getIdentifier(cardDrawableName(card.type), "drawable", packageName)
                if (cardArtId != 0) {
                    tile.addView(ImageView(this).apply {
                        setImageResource(cardArtId)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "${card.type.displayName} card artwork"
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(70)
                    ).apply { topMargin = dp(4) })
                } else {
                    tile.addView(CardIllustrationView(this, card.type), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(70)
                    ).apply { topMargin = dp(4) })
                }
                tile.addView(TextView(this).apply {
                    text = card.type.displayName.uppercase()
                    textSize = 11f * textScale
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    contentDescription = card.type.displayName
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
                tile.addView(TextView(this).apply {
                    text = "${card.value} pts${if (protected) "  •  SHIELDED" else ""}"
                    textSize = (if (protected) 9f else 11f) * textScale
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (protected) Color.rgb(167, 255, 222) else Color.WHITE)
                    contentDescription = "Value ${card.value}${if (protected) ", shielded" else ""}"
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
                strip.addView(tile, LinearLayout.LayoutParams(dp(112), dp(116)).apply {
                    marginEnd = dp(8)
                    bottomMargin = dp(8)
                    topMargin = dp(3)
                })
                if (!reducedMotion) {
                    tile.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).rotation(0f)
                        .setStartDelay(index * 45L).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
                    tile.postDelayed({
                        when (card.type) {
                            CardType.CANNON -> tile.animate().rotationBy(9f).setDuration(90).withEndAction {
                                tile.animate().rotation(0f).setDuration(140).start()
                            }.start()
                            CardType.KRAKEN -> tile.animate().scaleX(1.08f).scaleY(1.08f).setDuration(180).withEndAction {
                                tile.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                            }.start()
                            CardType.SWORD -> tile.animate().translationX(dp(5).toFloat()).setDuration(75).withEndAction {
                                tile.animate().translationX(0f).setDuration(90).start()
                            }.start()
                            CardType.MAP -> tile.animate().rotationBy(-7f).setDuration(140).withEndAction {
                                tile.animate().rotation(0f).setDuration(140).start()
                            }.start()
                            CardType.HOOK -> tile.animate().rotationBy(8f).setDuration(120).withEndAction {
                                tile.animate().rotation(0f).setDuration(150).start()
                            }.start()
                            CardType.ORACLE -> tile.animate().alpha(0.65f).setDuration(160).withEndAction {
                                tile.animate().alpha(1f).setDuration(190).start()
                            }.start()
                            else -> Unit
                        }
                    }, index * 45L + 330L)
                }
            }
            root.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                contentDescription = "Horizontal treasure card list"
                addView(strip)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        root.addView(label("PLAYER STANDINGS", 17f))
        root.addView(panel(game.players.joinToString("\n") { player ->
            "${listOf("👑", "🏴‍☠️", "🦜", "⚓", "🦈", "🐙", "💀", "🧭")[player.id % 8]} ${player.name} — ${player.bank.size} cards — ${engine.scoreFor(player)} pts" +
                (player.trait?.let { "  •  ${it.displayName}" } ?: "")
        }, 14f))

        val currentBank = active.bank.groupingBy { it.type }.eachCount().entries
            .joinToString("     ") { "${it.key.symbol} ${it.value}" }.ifEmpty { "Nothing banked yet" }
        root.addView(panel("${active.name.uppercase()}'S BANK\n$currentBank", 14f))
        root.addView(label("RECENT LOG", 14f))
        root.addView(panel(game.turnLog.takeLast(4).joinToString("\n").ifEmpty { "The voyage has just begun." }, 12f))

        if (game.finished) {
            trackFinishedGame(game)
            root.addView(label("🏆  VOYAGE COMPLETE  🏆", 23f).apply {
                gravity = Gravity.CENTER
                setTextColor(gold)
                typeface = Typeface.DEFAULT_BOLD
            })
            root.addView(ConfettiView(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))
            button("📊  SCORE BREAKDOWN") { showScoreBreakdown(game) }
            button("📜  FULL TURN LOG") { showTurnLog(game) }
            button("⚓  QUICK REMATCH") { startNewGame(lastPlayerCount, lastDifficulty, lastRules, lastPassAndPlay) }
            button("NEW GAME / SETTINGS") { showSetup() }
            deleteSavedGame()
            return
        }

        if (game.pendingEffect != null) {
            showEffectControls(game)
        } else {
            button("🃏  DRAW A CARD") {
                engine.draw(game)
                finishPlayerAction(game)
            }
            button("💰  COLLECT / END TURN") { confirmCollect(game) }
        }
        button("📜  FULL TURN LOG") { showTurnLog(game) }
        button("📚  CARD GLOSSARY") { showCardGlossary() }
        button("✦  SHOW TRAIT HELP") {
            base("TRAIT HELP")
            root.addView(panel(active.trait?.displayName ?: "No trait selected", 18f))
            root.addView(label(active.trait?.description ?: "No trait selected."))
            if (active.trait == TraitType.DAVY_JONES_LOCKER) {
                val target = game.davyJonesTargets[active.id]?.let { game.players[it].name } ?: "No target"
                root.addView(panel("Marked rival: $target", 14f))
            }
            button("BACK TO THE TABLE") { renderGame() }
        }
        button("↻  NEW GAME / SETTINGS") { showSetup() }
        saveGame(game)
    }

    private fun showEffectControls(game: GameState) {
        val effect = game.pendingEffect ?: return
        val player = game.players[game.currentPlayer]
        root.addView(panel("SPECIAL CARD: ${effect.cardType.displayName}", 18f))
        when (effect.cardType) {
            CardType.CANNON -> {
                root.addView(label("Choose a rival and a banked card type to target.", 14f))
                val targets = game.players.filter { it.id != player.id }
                var foundTarget = false
                targets.forEach { target ->
                    if (target.trait == TraitType.MISFIRE) {
                        foundTarget = true
                        button("💣  ${target.name} — MISFIRE (burn top bank card)") {
                            if (engine.resolveCannon(game, target.id)) finishPlayerAction(game)
                        }
                    } else {
                        target.bank.map { it.type }.distinct().forEach { type ->
                            foundTarget = true
                            button("💣  ${target.name}: ${type.symbol} ${type.displayName}") {
                                if (engine.resolveCannon(game, target.id, type)) finishPlayerAction(game)
                                else renderGame()
                            }
                        }
                    }
                }
                if (!foundTarget) root.addView(label("No rival has a card to target. Skip this effect."))
            }
            CardType.HOOK -> {
                val limit = if (player.trait == TraitType.CAPTAINS_HOOK) 2 else 1
                root.addView(label("Select up to $limit banked card(s) with different types to return to the board.", 14f))
                player.bank.filter { card -> game.board.none { it.type == card.type } }.forEach { card ->
                    val checked = card.id in selectedHookIds
                    val toggle = checkbox(
                        "${if (checked) "☑" else "☐"} ${card.type.symbol} ${card.type.displayName} — value ${card.value}",
                        checked
                    )
                    toggle.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            val currentTypes = selectedHookIds.mapNotNull { id -> player.bank.firstOrNull { it.id == id }?.type }.toSet()
                            if (selectedHookIds.size >= limit || card.type in currentTypes) {
                                toggle.isChecked = false
                                return@setOnCheckedChangeListener
                            }
                            selectedHookIds += card.id
                        } else selectedHookIds -= card.id
                    }
                    root.addView(toggle)
                }
                button("🪝  RETURN SELECTED CARDS (${selectedHookIds.size}/$limit)") {
                    if (engine.resolveHook(game, selectedHookIds.toList())) {
                        selectedHookIds.clear()
                        finishPlayerAction(game)
                    } else {
                        game.message = "Select no more than $limit different card type(s)."
                        renderGame()
                    }
                }
            }
            CardType.MAP -> {
                if (player.trait == TraitType.NAVIGATOR) {
                    root.addView(label("Navigator: choose any eligible card in the Burn Deck.", 14f))
                    game.burnDeck.filter { card -> game.board.none { it.type == card.type } }
                        .sortedByDescending { it.value }.forEach { card ->
                            button("🗺  ${card.type.symbol} ${card.type.displayName} — ${card.value}") {
                                if (engine.resolveMap(game, card.id)) finishPlayerAction(game)
                                else renderGame()
                            }
                        }
                } else {
                    val top = game.burnDeck.firstOrNull()
                    if (top == null) root.addView(label("The Burn Deck is empty."))
                    else root.addView(panel("Map's top Burn Deck card: ${top.type.symbol} ${top.type.displayName} — ${top.value}", 15f))
                    button("🗺  RECOVER TOP BURN CARD") {
                        if (engine.resolveMap(game)) finishPlayerAction(game) else renderGame()
                    }
                }
            }
            CardType.SWORD -> {
                root.addView(label("Choose an eligible banked card to steal from a rival.", 14f))
                var found = false
                game.players.filter { it.id != player.id }.forEach { target ->
                    target.bank.filter { card ->
                        player.trait == TraitType.SWORDSMAN || player.bank.none { it.type == card.type }
                    }.forEach { card ->
                        found = true
                        button("⚔  ${target.name}: ${card.type.symbol} ${card.type.displayName}") {
                            if (engine.resolveSword(game, target.id, card.id)) finishPlayerAction(game)
                            else renderGame()
                        }
                    }
                }
                if (!found) root.addView(label("No rival has an eligible card. Skip this effect."))
            }
            else -> Unit
        }
        button("SKIP SPECIAL EFFECT") {
            engine.skipPendingEffect(game)
            selectedHookIds.clear()
            finishPlayerAction(game)
        }
    }

    private fun finishPlayerAction(game: GameState) {
        if (!game.passAndPlay && game.currentPlayer != 0 && !game.finished) engine.playAiTurnsUntilHuman(game)
        if (game.finished) trackFinishedGame(game)
        if (game.finished) deleteSavedGame() else saveGame(game)
        renderGame()
    }

    private fun confirmCollect(game: GameState) {
        if (game.board.size >= 3) {
            AlertDialog.Builder(this)
                .setTitle("Collect this haul?")
                .setMessage("You are about to bank ${game.board.size} card(s). Collecting ends your turn.")
                .setNegativeButton("KEEP DRAWING", null)
                .setPositiveButton("COLLECT") { _, _ -> collectAfterConfirmation(game) }
                .show()
        } else collectAfterConfirmation(game)
    }

    private fun collectAfterConfirmation(game: GameState) {
        val active = game.players[game.currentPlayer]
        val hasBonus = game.rules.chestKeyBonusEnabled &&
            game.board.any { it.type == CardType.CHEST } && game.board.any { it.type == CardType.KEY }
        if (active.trait == TraitType.PLUNDERER && hasBonus) {
            val rivals = game.players.filter { it.id != active.id && it.bank.isNotEmpty() }
            if (rivals.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Plunder the bonus")
                    .setItems(rivals.map { "${it.name} — ${it.bank.size} banked card(s)" }.toTypedArray()) { _, which ->
                        if (engine.collect(game, rivals[which].id)) finishPlayerAction(game)
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
                return
            }
        }
        if (engine.collect(game)) finishPlayerAction(game) else renderGame()
    }

    private fun showHowToPlay() {
        base("HOW TO PLAY")
        root.addView(panel("1. DRAW", 17f))
        root.addView(label("Draw cards to grow the board. A duplicate type normally burns the unprotected board."))
        root.addView(panel("2. KNOW WHEN TO STOP", 17f))
        root.addView(label("Collect to bank the board and pass your turn. If the Draw Deck runs out, collect the remaining board to finish the game."))
        root.addView(panel("3. RESOLVE SPECIAL CARDS", 17f))
        root.addView(label("Cannon targets a rival's bank; Hook returns your banked cards; Map recovers a Burn Deck card; Sword steals an eligible card from a rival. Each effect can be skipped."))
        root.addView(panel("4. WATCH THE KRAKEN", 17f))
        root.addView(label("Kraken can force more draws before you can collect. Safe Harbor and Miser can protect selected cards from bust."))
        root.addView(panel("5. SCORE THE TREASURE", 17f))
        root.addView(label("The highest banked value of each type counts. Golden Scales add five points for owning a Mermaid. Chest + Key may grant a Burn Deck bonus, and Treasure Hunter triples it."))
        root.addView(panel("6. PLAY TOGETHER", 17f))
        root.addView(label("Choose local pass-and-play in the setup screen for multiple people on one device, or leave it off to face AI pirates."))
        button("BACK") { if (state != null && state?.finished == false) renderGame() else showSetup() }
    }

    private fun showCardGlossary() {
        val descriptions = mapOf(
            CardType.ANCHOR to "Steady the board. Safe Harbor protects the Anchor and the next two cards.",
            CardType.CANNON to "Choose a rival and target a card type in their bank. Master Gunner removes the whole type; Scavenger keeps the taken card; Misfire burns a top bank card instead.",
            CardType.CHEST to "Pairs with Key on the board to trigger a Burn Deck bonus when collecting.",
            CardType.HOOK to "Return one banked card type to the board. Captain's Hook permits two; Miser protects returned cards.",
            CardType.KEY to "Pairs with Chest on the board to trigger a bonus when collecting.",
            CardType.KRAKEN to "Normally forces two more draws. Beastmaster forces four; Fisherman banks Kraken immediately. Disable pressure in custom rules.",
            CardType.MAP to "Recover the top Burn Deck card. Navigator can choose any eligible Burn Deck card.",
            CardType.MERMAID to "Contributes its highest banked value to score. Casanova banks drawn Mermaids immediately; Golden Scales add five points.",
            CardType.ORACLE to "Reveals the next card. Mystic reveals up to three upcoming cards.",
            CardType.SWORD to "Steal an eligible banked card from a rival. Swordsman can steal a type already in your bank; Parry can force a rival to play a banked Kraken."
        )
        base("CARD GLOSSARY")
        CardType.entries.forEach { type ->
            root.addView(panel("${type.symbol}  ${type.displayName}", 17f))
            root.addView(label(descriptions[type] ?: "No additional rule."))
        }
        button("BACK TO PORT") { showSetup() }
    }

    private fun showScoreBreakdown(game: GameState) {
        val report = game.players.joinToString("\n\n") { player ->
            "${player.name} — ${player.score} points\n" +
                engine.scoreBreakdown(player).joinToString("\n")
        }
        AlertDialog.Builder(this)
            .setTitle("Final score breakdown")
            .setMessage(report)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showTurnLog(game: GameState) {
        AlertDialog.Builder(this)
            .setTitle("Voyage turn log")
            .setMessage(game.turnLog.joinToString("\n\n").ifBlank { "No turns recorded yet." })
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showStatistics() {
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        val matches = prefs.getInt("matches_played", 0)
        val wins = prefs.getInt("matches_won", 0)
        val points = prefs.getInt("total_score", 0)
        val best = prefs.getInt("best_score", 0)
        AlertDialog.Builder(this)
            .setTitle("Voyage statistics")
            .setMessage("Completed games: $matches\nVictories: $wins\nTotal final score: $points\nBest final score: $best")
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun trackFinishedGame(game: GameState) {
        if (trackedFinishedGame) return
        trackedFinishedGame = true
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        val matches = prefs.getInt("matches_played", 0) + 1
        val winner = game.players.sortedWith(compareByDescending<PlayerData> { it.score }.thenByDescending { it.bank.size }).firstOrNull()
        val userScore = game.players.firstOrNull()?.score ?: 0
        prefs.edit()
            .putInt("matches_played", matches)
            .putInt("matches_won", prefs.getInt("matches_won", 0) + if (winner?.id == 0) 1 else 0)
            .putInt("total_score", prefs.getInt("total_score", 0) + userScore)
            .putInt("best_score", maxOf(prefs.getInt("best_score", 0), userScore))
            .apply()
    }

    private fun feedbackForMessage(message: String) {
        if (message == lastFeedbackMessage) return
        lastFeedbackMessage = message
        if (soundEnabled) {
            val tone = when {
                message.contains("Bust!", true) -> ToneGenerator.TONE_PROP_NACK
                message.contains("Game over!", true) -> ToneGenerator.TONE_PROP_ACK
                message.contains("banks", true) || message.contains("collects", true) -> ToneGenerator.TONE_PROP_BEEP2
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            try {
                if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
                toneGenerator?.startTone(tone, 110)
            } catch (_: RuntimeException) {
                // Sound is an optional enhancement; unsupported audio should not interrupt play.
            }
        }
    }

    private fun cardDrawableName(type: CardType): String = when (type) {
        CardType.ANCHOR -> "anchor"
        CardType.CANNON -> "cannon"
        CardType.CHEST -> "chest"
        CardType.HOOK -> "hook"
        CardType.KEY -> "key"
        CardType.KRAKEN -> "kraken"
        CardType.MAP -> "map"
        CardType.MERMAID -> "mermaid"
        CardType.ORACLE -> "oracle"
        CardType.SWORD -> "sword"
    }

    private fun traitDrawableName(trait: TraitType): String = when (trait) {
        TraitType.CAPTAINS_HOOK -> "captain_s_hook"
        TraitType.DAVY_JONES_LOCKER -> "davy_jones_locker"
        TraitType.GOLDEN_SCALES -> "golden_scales"
        TraitType.MASTER_GUNNER -> "master_gunner"
        TraitType.SAFE_HARBOR -> "safe_harbor"
        TraitType.TREASURE_HUNTER -> "treasure_hunter"
        else -> trait.name.lowercase()
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

    private fun saveGame(game: GameState) {
        try {
            ObjectOutputStream(FileOutputStream(filesDir.resolve("saved-voyage.bin"))).use { it.writeObject(game) }
        } catch (_: Exception) {
            // Saving is best-effort; a filesystem error must not crash a game.
        }
    }

    private fun loadSavedGame(): GameState? {
        return try {
            ObjectInputStream(FileInputStream(filesDir.resolve("saved-voyage.bin"))).use {
                it.readObject() as? GameState
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteSavedGame() {
        runCatching { filesDir.resolve("saved-voyage.bin").delete() }
    }

    private fun startRematchFromSettings() {
        startNewGame(lastPlayerCount, lastDifficulty, lastRules, lastPassAndPlay)
    }

    private fun saveSetupDefaults() {
        getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE).edit()
            .putFloat("text_scale", textScale)
            .putInt("theme", themeIndex)
            .putBoolean("reduced_motion", reducedMotion)
            .putBoolean("sound", soundEnabled)
            .putBoolean("haptics", hapticsEnabled)
            .apply()
    }

    private inner class CardIllustrationView(context: Context, private val type: CardType) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun stroke(color: Int, width: Float) {
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        }

        private fun fill(color: Int) {
            paint.color = color
            paint.style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val u = minOf(width, height) * 0.34f
            val ink = Color.rgb(246, 226, 178)
            val shadow = Color.rgb(35, 32, 28)
            fill(Color.argb(35, 255, 239, 185))
            canvas.drawCircle(cx, cy, u * 1.08f, paint)
            stroke(ink, maxOf(dp(2), (u * 0.10f).toInt()).toFloat())

            when (type) {
                CardType.ANCHOR -> {
                    canvas.drawCircle(cx, cy - u * 0.68f, u * 0.24f, paint)
                    canvas.drawLine(cx, cy - u * 0.43f, cx, cy + u * 0.63f, paint)
                    canvas.drawLine(cx - u * 0.53f, cy - u * 0.22f, cx + u * 0.53f, cy - u * 0.22f, paint)
                    val path = Path().apply {
                        moveTo(cx - u * 0.72f, cy + u * 0.05f)
                        quadTo(cx - u * 0.62f, cy + u * 0.82f, cx, cy + u * 0.84f)
                        quadTo(cx + u * 0.62f, cy + u * 0.82f, cx + u * 0.72f, cy + u * 0.05f)
                    }
                    canvas.drawPath(path, paint)
                    canvas.drawLine(cx - u * 0.72f, cy + u * 0.05f, cx - u * 0.39f, cy + u * 0.25f, paint)
                    canvas.drawLine(cx + u * 0.72f, cy + u * 0.05f, cx + u * 0.39f, cy + u * 0.25f, paint)
                }
                CardType.CANNON -> {
                    fill(ink)
                    canvas.drawRoundRect(RectF(cx - u * 0.65f, cy - u * 0.25f, cx + u * 0.44f, cy + u * 0.20f), u * 0.12f, u * 0.12f, paint)
                    stroke(ink, u * 0.13f)
                    canvas.drawCircle(cx - u * 0.28f, cy + u * 0.40f, u * 0.20f, paint)
                    canvas.drawCircle(cx + u * 0.18f, cy + u * 0.40f, u * 0.20f, paint)
                    fill(shadow)
                    canvas.drawCircle(cx + u * 0.56f, cy - u * 0.44f, u * 0.14f, paint)
                }
                CardType.CHEST -> {
                    stroke(ink, u * 0.11f)
                    canvas.drawRoundRect(RectF(cx - u * 0.65f, cy - u * 0.15f, cx + u * 0.65f, cy + u * 0.63f), u * 0.10f, u * 0.10f, paint)
                    canvas.drawArc(RectF(cx - u * 0.65f, cy - u * 0.72f, cx + u * 0.65f, cy + u * 0.25f), 180f, 180f, false, paint)
                    canvas.drawLine(cx - u * 0.65f, cy - u * 0.1f, cx + u * 0.65f, cy - u * 0.1f, paint)
                    canvas.drawLine(cx, cy - u * 0.1f, cx, cy + u * 0.6f, paint)
                    canvas.drawCircle(cx, cy + u * 0.14f, u * 0.10f, paint)
                }
                CardType.HOOK -> {
                    val path = Path().apply {
                        moveTo(cx - u * 0.48f, cy - u * 0.62f)
                        lineTo(cx + u * 0.12f, cy - u * 0.62f)
                        lineTo(cx + u * 0.12f, cy + u * 0.08f)
                        cubicTo(cx + u * 0.12f, cy + u * 0.76f, cx - u * 0.84f, cy + u * 0.78f, cx - u * 0.65f, cy + u * 0.10f)
                        quadTo(cx - u * 0.56f, cy - u * 0.12f, cx - u * 0.34f, cy - u * 0.02f)
                    }
                    canvas.drawPath(path, paint)
                    canvas.drawLine(cx - u * 0.58f, cy - u * 0.74f, cx + u * 0.28f, cy - u * 0.74f, paint)
                }
                CardType.KEY -> {
                    canvas.drawCircle(cx - u * 0.36f, cy - u * 0.20f, u * 0.35f, paint)
                    canvas.drawCircle(cx - u * 0.36f, cy - u * 0.20f, u * 0.13f, paint)
                    canvas.drawLine(cx - u * 0.10f, cy + u * 0.02f, cx + u * 0.64f, cy + u * 0.62f, paint)
                    canvas.drawLine(cx + u * 0.30f, cy + u * 0.34f, cx + u * 0.11f, cy + u * 0.52f, paint)
                    canvas.drawLine(cx + u * 0.52f, cy + u * 0.52f, cx + u * 0.34f, cy + u * 0.70f, paint)
                }
                CardType.KRAKEN -> {
                    canvas.drawCircle(cx, cy - u * 0.08f, u * 0.40f, paint)
                    fill(ink)
                    canvas.drawCircle(cx - u * 0.14f, cy - u * 0.10f, u * 0.055f, paint)
                    canvas.drawCircle(cx + u * 0.14f, cy - u * 0.10f, u * 0.055f, paint)
                    stroke(ink, u * 0.13f)
                    repeat(4) { i ->
                        val side = if (i % 2 == 0) -1f else 1f
                        val startY = cy + u * (0.12f + i / 8f)
                        val path = Path().apply {
                            moveTo(cx + side * u * 0.20f, startY)
                            cubicTo(cx + side * u * 0.85f, startY + u * 0.10f, cx - side * u * 0.65f, cy + u * 0.70f, cx + side * u * (0.45f + i * 0.07f), cy + u * 0.78f)
                        }
                        canvas.drawPath(path, paint)
                    }
                }
                CardType.MAP -> {
                    val paper = Path().apply {
                        moveTo(cx - u * 0.75f, cy - u * 0.52f)
                        lineTo(cx - u * 0.20f, cy - u * 0.70f)
                        lineTo(cx + u * 0.22f, cy - u * 0.48f)
                        lineTo(cx + u * 0.72f, cy - u * 0.64f)
                        lineTo(cx + u * 0.72f, cy + u * 0.58f)
                        lineTo(cx + u * 0.20f, cy + u * 0.73f)
                        lineTo(cx - u * 0.20f, cy + u * 0.52f)
                        lineTo(cx - u * 0.75f, cy + u * 0.67f)
                        close()
                    }
                    canvas.drawPath(paper, paint)
                    canvas.drawLine(cx - u * 0.20f, cy - u * 0.70f, cx - u * 0.20f, cy + u * 0.52f, paint)
                    canvas.drawLine(cx + u * 0.22f, cy - u * 0.48f, cx + u * 0.22f, cy + u * 0.73f, paint)
                    canvas.drawLine(cx - u * 0.05f, cy - u * 0.12f, cx + u * 0.34f, cy + u * 0.22f, paint)
                    canvas.drawLine(cx + u * 0.34f, cy - u * 0.12f, cx - u * 0.05f, cy + u * 0.22f, paint)
                }
                CardType.MERMAID -> {
                    canvas.drawCircle(cx, cy - u * 0.55f, u * 0.20f, paint)
                    val body = Path().apply {
                        moveTo(cx - u * 0.08f, cy - u * 0.32f)
                        quadTo(cx + u * 0.50f, cy - u * 0.02f, cx + u * 0.06f, cy + u * 0.25f)
                        lineTo(cx - u * 0.12f, cy + u * 0.54f)
                    }
                    canvas.drawPath(body, paint)
                    val tail = Path().apply {
                        moveTo(cx - u * 0.12f, cy + u * 0.54f)
                        lineTo(cx - u * 0.55f, cy + u * 0.83f)
                        lineTo(cx - u * 0.02f, cy + u * 0.73f)
                        lineTo(cx + u * 0.38f, cy + u * 0.88f)
                        close()
                    }
                    canvas.drawPath(tail, paint)
                    canvas.drawArc(RectF(cx - u * 0.25f, cy - u * 0.84f, cx + u * 0.25f, cy - u * 0.25f), 180f, 180f, false, paint)
                }
                CardType.ORACLE -> {
                    val crystal = Path().apply {
                        moveTo(cx, cy - u * 0.82f)
                        lineTo(cx + u * 0.60f, cy - u * 0.06f)
                        lineTo(cx + u * 0.10f, cy + u * 0.73f)
                        lineTo(cx - u * 0.55f, cy + u * 0.20f)
                        close()
                    }
                    canvas.drawPath(crystal, paint)
                    canvas.drawLine(cx, cy - u * 0.82f, cx + u * 0.10f, cy + u * 0.73f, paint)
                    canvas.drawLine(cx + u * 0.60f, cy - u * 0.06f, cx - u * 0.55f, cy + u * 0.20f, paint)
                    canvas.drawCircle(cx - u * 0.73f, cy - u * 0.65f, u * 0.06f, paint)
                    canvas.drawCircle(cx + u * 0.78f, cy + u * 0.42f, u * 0.06f, paint)
                }
                CardType.SWORD -> {
                    val blade = Path().apply {
                        moveTo(cx - u * 0.50f, cy + u * 0.60f)
                        lineTo(cx + u * 0.50f, cy - u * 0.68f)
                        lineTo(cx + u * 0.28f, cy + u * 0.45f)
                        close()
                    }
                    canvas.drawPath(blade, paint)
                    canvas.drawLine(cx - u * 0.24f, cy + u * 0.12f, cx + u * 0.20f, cy + u * 0.45f, paint)
                    canvas.drawLine(cx - u * 0.62f, cy + u * 0.10f, cx - u * 0.02f, cy + u * 0.56f, paint)
                    canvas.drawLine(cx - u * 0.55f, cy + u * 0.72f, cx - u * 0.25f, cy + u * 0.43f, paint)
                }
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
            if (!reducedMotion) {
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1300L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        progress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else progress = 1f
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
