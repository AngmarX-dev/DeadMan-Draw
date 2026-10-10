package DeadManDraws

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
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
import android.os.Handler
import android.os.Looper
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
import android.widget.ProgressBar
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
    private var captainName = "کاپیتان"
    private var rivalNames: List<String> = emptyList()
    private var captainAvatar = "🏴‍☠️"
    private var selectedCardBack = 1
    private val avatars = listOf("🏴‍☠️", "🦜", "⚓", "🦈", "🐙", "💀", "🧭", "🦑", "☠️")
    private val cardBackColors = listOf(
        Color.rgb(0, 220, 28), Color.BLUE, Color.RED, Color.rgb(242, 143, 191),
        Color.rgb(13, 57, 140), Color.rgb(151, 0, 203), Color.CYAN, Color.rgb(89, 143, 255),
        Color.rgb(143, 250, 250), Color.rgb(93, 46, 46), Color.rgb(255, 103, 0), Color.YELLOW
    )
    private var lastFeedbackMessage = ""
    private var themeIndex = 0
    private var reducedMotion = false
    private var soundEnabled = false
    private var hapticsEnabled = true
    private var textScale = 1f
    private var trackedFinishedGame = false
    private val selectedHookIds = mutableSetOf<Int>()
    private var toneGenerator: ToneGenerator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var aiSequenceRunning = false
    private var aiDrawsThisTurn = 0
    private var aiActionCount = 0
    private var restartAiWhenResumed = false
    private var aiStepRunnable: Runnable? = null
    private var selectedPlayerId = 0
    private var playerListColumn: LinearLayout? = null
    private var selectedPlayerCardsTitle: TextView? = null
    private var selectedPlayerCardsRow: LinearLayout? = null
    private var ownHandTitle: TextView? = null
    private var ownHandCardsRow: LinearLayout? = null
    private var drawDeckControl: LinearLayout? = null
    private var burnDeckControl: LinearLayout? = null
    private var drawDeckCountLabel: TextView? = null
    private var burnDeckCountLabel: TextView? = null
    private var drawAnimationRunning = false
    private var landscapeScreenRoot: LinearLayout? = null

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
        captainName = prefs.getString("captain_name", "کاپیتان").orEmpty().ifBlank { "کاپیتان" }
        captainAvatar = prefs.getString("captain_avatar", "🏴‍☠️").orEmpty().ifBlank { "🏴‍☠️" }
        selectedCardBack = prefs.getInt("card_back", 1).coerceIn(cardBackColors.indices)
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
                .setTitle("ادامهٔ بازی؟")
                .setMessage("یک بازی ناتمام ذخیره شده است. ادامه می‌دهید یا بازی تازه‌ای شروع می‌کنید؟")
                .setPositiveButton("ادامه") { _, _ ->
                    if (saved.players.firstOrNull()?.trait == null && saved.currentPlayer == 0) chooseTrait()
                    else if (!saved.passAndPlay && saved.currentPlayer != 0) beginAiSequence(saved)
                    else renderGame()
                }
                .setNegativeButton("بازی جدید") { _, _ ->
                    state = null
                    deleteSavedGame()
                    showMainMenu()
                }
                .setOnCancelListener { showMainMenu() }
                .show()
        } else {
            deleteSavedGame()
            showMainMenu()
            if (!prefs.getBoolean("tutorial_seen", false)) {
                AlertDialog.Builder(this)
                    .setTitle("خوش آمدید، ناخدا!")
                    .setMessage("کارت بکشید و گنج جمع کنید؛ اما تکراری شدن نوع کارت ممکن است گنج جمع‌نشده را بسوزاند. با «جمع کردن کارت‌ها» امتیاز را ذخیره کنید.")
                    .setPositiveButton("شروع ماجراجویی") { _, _ ->
                        prefs.edit().putBoolean("tutorial_seen", true).apply()
                    }
                    .show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (aiSequenceRunning) restartAiWhenResumed = true
        aiStepRunnable?.let { mainHandler.removeCallbacks(it) }
        aiSequenceRunning = false
        val game = state
        if (game != null && !game.finished) saveGame(game) else if (game?.finished == true) deleteSavedGame()
    }

    override fun onResume() {
        super.onResume()
        if (restartAiWhenResumed) {
            restartAiWhenResumed = false
            state?.let { game ->
                if (!game.finished && !game.passAndPlay && game.currentPlayer != 0) beginAiSequence(game)
            }
        }
    }

    override fun onDestroy() {
        aiStepRunnable?.let { mainHandler.removeCallbacks(it) }
        mainHandler.removeCallbacksAndMessages(null)
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(navy)
            contentDescription = "Scrollable Dead Man's Draw screen"
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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


    /**
     * A dedicated landscape table: play area on the left, selectable player/robot list on
     * the right, and the human player's cards in a dock that stays visible during play.
     */
    private fun baseLandscapeGame(title: String, game: GameState) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val screenRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(navy)
            setPadding(dp(6), dp(3), dp(6), dp(5))
        }
        setContentView(screenRoot)

        screenRoot.addView(TextView(this).apply {
            text = title
            textSize = 21f * textScale
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(gold)
            setShadowLayer(dp(6).toFloat(), 0f, 0f, mutedGold)
            contentDescription = title
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        screenRoot.addView(contentRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(navy, panelColor, navy)
            )
        }
        contentRow.addView(mainColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val boardScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            contentDescription = "میز بازی قابل پیمایش"
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        boardScroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        mainColumn.addView(boardScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val deckDock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = roundedDrawable(panelColor, mutedGold, 1)
            setPadding(dp(5), dp(3), dp(5), dp(2))
        }
        val deckRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        fun createDeckPile(drawableName: String, title: String, description: String, isDrawPile: Boolean): LinearLayout {
            val pile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER
                background = roundedDrawable(navy, mutedGold, 1)
                setPadding(dp(5), dp(2), dp(5), dp(2))
                isClickable = true
                isFocusable = true
                contentDescription = description
            }
            val imageId = resources.getIdentifier(drawableName, "drawable", packageName)
            if (imageId != 0) {
                pile.addView(ImageView(this).apply {
                    setImageResource(imageId)
                    setColorFilter(cardBackColors[selectedCardBack])
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = description
                }, LinearLayout.LayoutParams(dp(46), dp(62)).apply { marginEnd = dp(7) })
            }
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            info.addView(label(title, 13f).apply {
                setTextColor(gold)
                typeface = Typeface.DEFAULT_BOLD
            })
            val counter = label("۰", 17f).apply {
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            info.addView(counter)
            pile.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (isDrawPile) {
                drawDeckCountLabel = counter
                pile.setOnClickListener { onDrawDeckTapped(game) }
                pile.setOnLongClickListener { onDrawDeckLongPressed(game) }
            } else {
                burnDeckCountLabel = counter
                pile.setOnClickListener { onBurnDeckTapped(game) }
            }
            deckRow.addView(pile, LinearLayout.LayoutParams(0, dp(70), 1f).apply { marginEnd = dp(5) })
            return pile
        }
        drawDeckControl = createDeckPile("backcart", "دستهٔ کارت", "لمس: برداشتن کارت؛ نگه‌داشتن: جمع‌کردن گنج", true)
        burnDeckControl = createDeckPile("backcartburn", "کارت‌های سوخته", "برای بازیابی نقشه، دستهٔ سوخته را لمس کنید", false)
        deckDock.addView(deckRow)
        deckDock.addView(label("لمس دستهٔ کارت: برداشتن کارت  •  نگه‌داشتن: جمع‌کردن گنج", 10f).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            setTextColor(Color.LTGRAY)
        })
        mainColumn.addView(deckDock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(96)
        ).apply { topMargin = dp(3) })

        val handDock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = roundedDrawable(panelColor, gold, 1)
            setPadding(dp(6), dp(3), dp(6), dp(4))
        }
        ownHandTitle = label("کارت‌های ذخیره‌شدهٔ تو", 13f).apply {
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, dp(4), dp(2))
        }
        handDock.addView(ownHandTitle)
        val ownHandScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            contentDescription = "کارت‌های خود بازیکن که همیشه نمایش داده می‌شوند"
        }
        ownHandCardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
        }
        ownHandScroll.addView(ownHandCardsRow)
        handDock.addView(ownHandScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        mainColumn.addView(handDock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(125)
        ).apply { topMargin = dp(4) })

        val sidebarWidth = if (resources.configuration.screenWidthDp >= 850) dp(245) else dp(198)
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = roundedDrawable(panelColor, mutedGold, 1)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        contentRow.addView(sidebar, LinearLayout.LayoutParams(
            sidebarWidth, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { marginStart = dp(6) })

        sidebar.addView(label("بازیکنان و ربات‌ها", 15f).apply {
            gravity = Gravity.CENTER
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
        })
        val playerListScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            contentDescription = "فهرست بازیکنان؛ برای دیدن کارت‌ها لمس کنید"
        }
        playerListColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        playerListScroll.addView(playerListColumn, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        sidebar.addView(playerListScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        selectedPlayerCardsTitle = label("کارت‌های بازیکن", 12f).apply {
            gravity = Gravity.CENTER
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(3), dp(2), dp(2))
        }
        sidebar.addView(selectedPlayerCardsTitle)
        val selectedCardsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            contentDescription = "کارت‌های بازیکن انتخاب‌شده"
        }
        selectedPlayerCardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
        }
        selectedCardsScroll.addView(selectedPlayerCardsRow)
        sidebar.addView(selectedCardsScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(88)
        ))

        if (!reducedMotion) {
            screenRoot.alpha = 0f
            screenRoot.translationY = dp(5).toFloat()
            screenRoot.animate().alpha(1f).translationY(0f).setDuration(180)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        landscapeScreenRoot = screenRoot
        refreshDeckDock(game)
        refreshOwnHand(game)
        refreshPlayerSidebar(game)
    }

    private fun makeCardTile(card: CardDefinition, compact: Boolean): LinearLayout {
        val cardWidth = if (compact) dp(54) else dp(70)
        val artHeight = if (compact) dp(38) else dp(56)
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable(cardColor(card.type), gold, 1)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            contentDescription = "${cardNameFa(card.type)}، ارزش ${card.value}"
        }
        val imageId = resources.getIdentifier(cardDrawableName(card.type), "drawable", packageName)
        if (imageId != 0) {
            tile.addView(ImageView(this).apply {
                setImageResource(imageId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = cardNameFa(card.type)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, artHeight))
        } else {
            tile.addView(CardIllustrationView(this, card.type), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, artHeight
            ))
        }
        tile.addView(TextView(this).apply {
            text = cardNameFa(card.type)
            textSize = if (compact) 8f * textScale else 10f * textScale
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(15)))
        tile.addView(TextView(this).apply {
            text = card.value.toPersianDigits()
            textSize = if (compact) 8f * textScale else 9f * textScale
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(13)))
        tile.layoutParams = LinearLayout.LayoutParams(cardWidth,
            if (compact) dp(70) else dp(90)).apply { marginEnd = dp(4) }
        if (!reducedMotion) {
            tile.alpha = 0f
            tile.scaleX = 0.88f
            tile.scaleY = 0.88f
            tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(230)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        return tile
    }

    private fun refreshOwnHand(game: GameState) {
        val player = game.players.getOrNull(game.currentPlayer) ?: return
        val hookPending = game.pendingEffect?.cardType == CardType.HOOK &&
            game.pendingEffect?.playerIndex == game.currentPlayer
        ownHandTitle?.text = if (hookPending) {
            "قلاب: کارت انتخاب کن • عنوان را نگه‌دار (${selectedHookIds.size})"
        } else {
            "کارت‌های ${player.name} • ${player.bank.size.toPersianDigits()}"
        }
        ownHandTitle?.setOnLongClickListener {
            if (hookPending) applySelectedHookCards(game) else false
        }
        val row = ownHandCardsRow ?: return
        row.removeAllViews()
        if (player.bank.isEmpty()) {
            row.addView(label("هنوز کارتی ذخیره نکرده‌ای؛ پس از جمع‌کردن گنج، کارت‌ها اینجا می‌مانند.", 12f))
        } else {
            player.bank.forEach { card ->
                val tile = makeCardTile(card, compact = false)
                if (hookPending) {
                    val selected = card.id in selectedHookIds
                    tile.background = roundedDrawable(if (selected) mutedGold else cardColor(card.type),
                        if (selected) Color.rgb(114, 229, 198) else gold, if (selected) 3 else 1)
                    tile.isClickable = true
                    tile.isFocusable = true
                    tile.setOnClickListener { onBankCardTapped(game, player.id, card) }
                }
                row.addView(tile)
            }
        }
    }

    private fun refreshPlayerSidebar(game: GameState) {
        val list = playerListColumn ?: return
        if (game.players.none { it.id == selectedPlayerId }) selectedPlayerId = 0
        list.removeAllViews()
        game.players.forEach { player ->
            val selected = player.id == selectedPlayerId
            val active = player.id == game.currentPlayer
            val robot = !player.isHuman
            val prefix = when {
                robot && active && aiSequenceRunning -> "🤖 ▶"
                robot -> "🤖"
                player.id == 0 -> captainAvatar
                else -> "👤"
            }
            val entry = TextView(this).apply {
                text = "$prefix  ${player.name}\n${if (robot) "ربات" else "بازیکن"}  •  ${player.bank.size.toPersianDigits()} کارت  •  ${engine.scoreFor(player).toPersianDigits()} امتیاز"
                textSize = 11f * textScale
                setTextColor(if (active) gold else Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), dp(3), dp(7), dp(3))
                minHeight = dp(47)
                background = roundedDrawable(
                    when {
                        active -> Color.rgb(68, 63, 35)
                        selected -> mutedGold
                        else -> navy
                    },
                    if (active) gold else if (selected) gold else mutedGold,
                    if (active || selected) 2 else 1
                )
                isClickable = true
                isFocusable = true
                contentDescription = "نمایش کارت‌های ${player.name}"
                setOnClickListener {
                    onPlayerRowTapped(game, player)
                }
            }
            list.addView(entry, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) })
            if (active && robot && aiSequenceRunning && !reducedMotion) {
                entry.animate().scaleX(1.025f).scaleY(1.025f).setDuration(280)
                    .withEndAction {
                        if (entry.parent != null) entry.animate().scaleX(1f).scaleY(1f).setDuration(280).start()
                    }.start()
            }
        }
        refreshSelectedPlayerHand(game)
    }

    private fun refreshSelectedPlayerHand(game: GameState) {
        val player = game.players.firstOrNull { it.id == selectedPlayerId } ?: game.players.firstOrNull() ?: return
        selectedPlayerCardsTitle?.text = "کارت‌های ${player.name}  •  ${player.bank.size.toPersianDigits()}"
        val row = selectedPlayerCardsRow ?: return
        row.removeAllViews()
        if (player.bank.isEmpty()) {
            row.addView(label("بدون کارت ذخیره‌شده", 11f))
        } else {
            player.bank.forEach { card ->
                val tile = makeCardTile(card, compact = true)
                if (game.pendingEffect?.playerIndex == game.currentPlayer &&
                    (game.pendingEffect?.cardType == CardType.CANNON || game.pendingEffect?.cardType == CardType.SWORD)) {
                    tile.isClickable = true
                    tile.isFocusable = true
                    tile.setOnClickListener { onBankCardTapped(game, player.id, card) }
                }
                row.addView(tile)
            }
        }
    }

    private fun refreshDeckDock(game: GameState) {
        drawDeckCountLabel?.text = "${game.drawDeck.size.toPersianDigits()} کارت"
        burnDeckCountLabel?.text = "${game.burnDeck.size.toPersianDigits()} کارت"
        val canAct = canCurrentPlayerAct(game)
        drawDeckControl?.alpha = if (canAct) 1f else 0.72f
        burnDeckControl?.alpha = if (canAct) 1f else 0.72f
    }

    private fun canCurrentPlayerAct(game: GameState): Boolean {
        if (state !== game || game.finished) return false
        val active = game.players.getOrNull(game.currentPlayer) ?: return false
        return active.isHuman && (!aiSequenceRunning || game.passAndPlay)
    }

    private fun animateDeckControl(control: View?, onFinished: () -> Unit) {
        if (control == null || reducedMotion) {
            onFinished()
            return
        }
        control.animate().cancel()
        control.animate().rotationBy(3.5f).scaleX(0.92f).scaleY(0.92f).setDuration(110)
            .withEndAction {
                control.animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(130)
                    .withEndAction { onFinished() }.start()
            }.start()
    }

    private fun onDrawDeckTapped(game: GameState) {
        if (!canCurrentPlayerAct(game) || drawAnimationRunning) return
        if (game.pendingEffect != null) {
            engine.skipPendingEffect(game)
            selectedHookIds.clear()
            finishPlayerAction(game)
            return
        }
        if (game.drawDeck.isEmpty()) {
            if (game.board.isNotEmpty()) onDrawDeckLongPressed(game)
            else {
                game.message = "دستهٔ کارت تمام شده است."
                renderGame()
            }
            return
        }
        drawAnimationRunning = true
        animateDeckControl(drawDeckControl) {
            drawAnimationRunning = false
            if (!canCurrentPlayerAct(game) || game.pendingEffect != null) return@animateDeckControl
            engine.draw(game)
            finishPlayerAction(game)
        }
    }

    private fun onDrawDeckLongPressed(game: GameState): Boolean {
        if (!canCurrentPlayerAct(game)) return true
        if (game.pendingEffect != null) {
            if (::status.isInitialized) status.text = "برای رد اثر ویژه، دستهٔ اصلی را یک بار لمس کن."
            return true
        }
        if (game.board.isEmpty()) {
            if (::status.isInitialized) status.text = "هنوز گنجی روی میز نیست که جمع شود."
            return true
        }
        animateDeckControl(drawDeckControl) {
            if (canCurrentPlayerAct(game) && game.pendingEffect == null) collectAfterConfirmation(game)
        }
        return true
    }

    private fun onBurnDeckTapped(game: GameState) {
        if (!canCurrentPlayerAct(game)) return
        val effect = game.pendingEffect
        if (effect?.cardType != CardType.MAP || effect.playerIndex != game.currentPlayer) {
            if (::status.isInitialized) status.text = "دستهٔ سوخته فقط هنگام استفاده از نقشه قابل بازیابی است."
            return
        }
        animateDeckControl(burnDeckControl) {
            if (!canCurrentPlayerAct(game) || game.pendingEffect?.cardType != CardType.MAP) return@animateDeckControl
            val actor = game.players[game.currentPlayer]
            val success = if (actor.trait == TraitType.NAVIGATOR) {
                val chosen = game.burnDeck.filter { card -> game.board.none { it.type == card.type } }
                    .maxByOrNull { it.value }
                chosen != null && engine.resolveMap(game, chosen.id)
            } else engine.resolveMap(game)
            if (success) finishPlayerAction(game) else {
                game.message = "کارت قابل بازیابی نیست؛ برای رد اثر، دستهٔ اصلی را لمس کن."
                renderGame()
            }
        }
    }

    private fun onPlayerRowTapped(game: GameState, player: PlayerData) {
        if (!canCurrentPlayerAct(game)) return
        val effect = game.pendingEffect
        if (effect != null && effect.playerIndex == game.currentPlayer &&
            player.id != game.currentPlayer &&
            (effect.cardType == CardType.CANNON || effect.cardType == CardType.SWORD)) {
            selectedPlayerId = player.id
            if (effect.cardType == CardType.CANNON && player.trait == TraitType.MISFIRE) {
                if (engine.resolveCannon(game, player.id)) finishPlayerAction(game) else renderGame()
                return
            }
            if (player.bank.isEmpty()) {
                game.message = "این بازیکن کارت ذخیره‌شده‌ای برای هدف‌گیری ندارد."
                renderGame()
                return
            }
        }
        selectedPlayerId = player.id
        refreshPlayerSidebar(game)
    }

    private fun onBankCardTapped(game: GameState, ownerId: Int, card: CardDefinition) {
        if (!canCurrentPlayerAct(game)) return
        val effect = game.pendingEffect ?: return
        if (effect.playerIndex != game.currentPlayer) return
        when (effect.cardType) {
            CardType.CANNON -> {
                if (ownerId == game.currentPlayer) return
                val target = game.players.getOrNull(ownerId) ?: return
                val success = if (target.trait == TraitType.MISFIRE) engine.resolveCannon(game, target.id)
                    else engine.resolveCannon(game, target.id, card.type)
                if (success) finishPlayerAction(game) else {
                    game.message = "این کارت برای توپ قابل هدف‌گیری نیست."
                    renderGame()
                }
            }
            CardType.SWORD -> {
                if (ownerId == game.currentPlayer) return
                if (engine.resolveSword(game, ownerId, card.id)) finishPlayerAction(game) else {
                    game.message = "این کارت برای شمشیر مجاز نیست."
                    renderGame()
                }
            }
            CardType.HOOK -> {
                if (ownerId != game.currentPlayer) return
                val limit = if (game.players[game.currentPlayer].trait == TraitType.CAPTAINS_HOOK) 2 else 1
                if (card.id in selectedHookIds) {
                    selectedHookIds.remove(card.id)
                } else {
                    val selectedTypes = selectedHookIds.mapNotNull { id ->
                        game.players[game.currentPlayer].bank.firstOrNull { it.id == id }?.type
                    }.toSet()
                    if (selectedHookIds.size < limit && card.type !in selectedTypes &&
                        game.board.none { it.type == card.type }) selectedHookIds.add(card.id)
                    else game.message = "قلاب فقط کارت‌هایی با نوع متفاوت و بدون نمونه روی میز می‌پذیرد."
                }
                refreshOwnHand(game)
                refreshSelectedPlayerHand(game)
            }
            else -> Unit
        }
    }

    private fun applySelectedHookCards(game: GameState): Boolean {
        if (!canCurrentPlayerAct(game) || game.pendingEffect?.cardType != CardType.HOOK) return true
        if (selectedHookIds.isEmpty()) {
            if (::status.isInitialized) status.text = "اول دست‌کم یک کارت از نوار پایین انتخاب کن."
            return true
        }
        if (engine.resolveHook(game, selectedHookIds.toList())) {
            selectedHookIds.clear()
            finishPlayerAction(game)
        } else {
            game.message = "کارت‌های انتخابی برای قلاب مجاز نیستند."
            renderGame()
        }
        return true
    }

    private fun leaveGameScreen(game: GameState) {
        aiStepRunnable?.let { mainHandler.removeCallbacks(it) }
        aiSequenceRunning = false
        restartAiWhenResumed = false
        aiDrawsThisTurn = 0
        aiActionCount = 0
        drawAnimationRunning = false
        if (game.finished) {
            state = null
            deleteSavedGame()
        } else saveGame(game)
        showMainMenu()
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


    private fun showMainMenu() {
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        val coins = prefs.getInt("coins", 152).coerceAtLeast(0)
        base("دزدان دریایی")
        root.addView(panel("بازی کارتی ماجراجویانه در دریای آزاد", 17f))
        root.addView(label("گنج جمع کن، رقیب‌ها را زیر نظر بگیر و به‌موقع کنار بکش.", 14f))
        val profile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(panelColor, mutedGold, 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        profile.addView(TextView(this).apply {
            text = captainAvatar
            textSize = 34f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(navy)
                setStroke(dp(2), gold)
            }
            contentDescription = "آواتار بازیکن"
        }, LinearLayout.LayoutParams(dp(62), dp(62)).apply { marginEnd = dp(12) })
        val profileInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        profileInfo.addView(label(captainName, 18f).apply { typeface = Typeface.DEFAULT_BOLD })
        profileInfo.addView(label("تعداد سکه: ${coins.toPersianDigits()}", 14f))
        profile.addView(profileInfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(profile, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })
        button("بازی جدید") { state = null; deleteSavedGame(); showSetup() }
        button("ادامهٔ بازی") {
            val saved = state ?: loadSavedGame()
            if (saved != null && !saved.finished) {
                state = saved
                if (saved.players.firstOrNull()?.trait == null && saved.currentPlayer == 0) chooseTrait()
                else if (!saved.passAndPlay && saved.currentPlayer != 0) beginAiSequence(saved)
                else renderGame()
            } else showSetup()
        }
        button("بازی گروهی") { showSetup(groupMode = true) }
        button("راهنما") { showHowToPlay() }
        button("انتخاب پشت کارت") { showCardBackPicker() }
        button("تنظیمات") { showSettingsScreen() }
        button("خرید سکه") { showCoinShop() }
        button("بازگشت به فهرست بازی‌ها") { finish() }
    }

    private fun showSettingsScreen() {
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        base("تنظیمات")
        root.addView(label("نام بازیکن", 17f))
        val nameInput = EditText(this).apply {
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(captainName)
            hint = "نام بازیکن"
            textSize = 17f * textScale
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            background = roundedDrawable(panelColor, mutedGold, 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(nameInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })
        button("انتخاب آواتار  $captainAvatar") { showAvatarPicker(returnToSettings = true) }
        val sound = checkbox("صدای بازی", soundEnabled)
        val haptics = checkbox("بازخورد لمسی", hapticsEnabled)
        val reduced = checkbox("کاهش انیمیشن‌ها", reducedMotion)
        root.addView(sound); root.addView(haptics); root.addView(reduced)
        root.addView(label("ظاهر بازی", 17f))
        button("طرح زمینه: ${themes[themeIndex].name}") {
            themeIndex = (themeIndex + 1) % themes.size
            applyTheme(themeIndex)
            prefs.edit().putInt("theme", themeIndex).apply()
            showSettingsScreen()
        }
        button("انتخاب پشت کارت") { showCardBackPicker() }
        val textScaleStops = listOf(0.85f, 0.95f, 1.0f, 1.1f, 1.2f)
        val textScaleLabels = listOf("۸۵٪", "۹۵٪", "۱۰۰٪", "۱۱۰٪", "۱۲۰٪")
        val selectedScale = textScaleStops.indexOf(textScale).takeIf { it >= 0 } ?: 2
        val textScaleLabel = panel("اندازهٔ نوشته: ${textScaleLabels[selectedScale]}", 15f)
        root.addView(textScaleLabel)
        root.addView(SeekBar(this).apply {
            max = textScaleStops.lastIndex
            progress = selectedScale
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    textScaleLabel.text = "اندازهٔ نوشته: ${textScaleLabels[progress]}"
                    if (fromUser) {
                        textScale = textScaleStops[progress]
                        prefs.edit().putFloat("text_scale", textScale).apply()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        button("ذخیره") {
            captainName = nameInput.text.toString().trim().ifBlank { "کاپیتان" }.take(24)
            soundEnabled = sound.isChecked
            hapticsEnabled = haptics.isChecked
            reducedMotion = reduced.isChecked
            prefs.edit().putString("captain_name", captainName)
                .putBoolean("sound", soundEnabled).putBoolean("haptics", hapticsEnabled)
                .putBoolean("reduced_motion", reducedMotion).putFloat("text_scale", textScale).apply()
            showMainMenu()
        }
        button("بازگشت به منو") { showMainMenu() }
    }

    private fun showAvatarPicker(returnToSettings: Boolean = false) {
        val names = listOf("کاپیتان", "طوطی", "لنگر", "کوسه", "اختاپوس", "جمجمه", "قطب‌نما", "ماهی مرکب", "دزد دریایی")
        base("آواتار")
        root.addView(label("آواتار دلخواه خود را انتخاب کنید.", 15f))
        var index = 0
        while (index < avatars.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (column in 0 until 3) {
                val avatarIndex = index + column
                if (avatarIndex < avatars.size) {
                    val avatar = avatars[avatarIndex]
                    val selected = captainAvatar == avatar
                    val option = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        isClickable = true
                        isFocusable = true
                        background = roundedDrawable(if (selected) mutedGold else panelColor,
                            if (selected) gold else mutedGold, if (selected) 2 else 1)
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        contentDescription = names[avatarIndex]
                    }
                    option.addView(TextView(this).apply {
                        text = avatar; textSize = 30f; gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(navy)
                            setStroke(dp(2), if (selected) gold else mutedGold)
                        }
                    }, LinearLayout.LayoutParams(dp(62), dp(62)))
                    option.addView(label(names[avatarIndex], 12f).apply { gravity = Gravity.CENTER })
                    option.setOnClickListener {
                        captainAvatar = avatar
                        getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE).edit().putString("captain_avatar", avatar).apply()
                        if (returnToSettings) showSettingsScreen() else showMainMenu()
                    }
                    row.addView(option, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6); bottomMargin = dp(8)
                    })
                }
            }
            root.addView(row)
            index += 3
        }
        button("ذخیره") {
            getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE).edit().putString("captain_avatar", captainAvatar).apply()
            if (returnToSettings) showSettingsScreen() else showMainMenu()
        }
        button("بازگشت به منو") { if (returnToSettings) showSettingsScreen() else showMainMenu() }
    }

    private fun showCardBackPicker() {
        base("پشت کارت")
        root.addView(label("رنگ دلخواه کارت‌ها را انتخاب کنید.", 15f))
        var index = 0
        while (index < cardBackColors.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (column in 0 until 4) {
                val colorIndex = index + column
                if (colorIndex < cardBackColors.size) {
                    val chosen = colorIndex == selectedCardBack
                    val swatch = TextView(this).apply {
                        text = if (chosen) "✓" else ""
                        textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            setColor(cardBackColors[colorIndex]); cornerRadius = dp(22).toFloat()
                            setStroke(dp(if (chosen) 4 else 1), if (chosen) Color.WHITE else Color.LTGRAY)
                        }
                        isClickable = true; isFocusable = true
                        contentDescription = "رنگ کارت ${colorIndex + 1}"
                        setOnClickListener {
                            selectedCardBack = colorIndex
                            getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE).edit().putInt("card_back", selectedCardBack).apply()
                            showCardBackPicker()
                        }
                    }
                    row.addView(swatch, LinearLayout.LayoutParams(0, dp(110), 1f).apply {
                        marginEnd = dp(8); bottomMargin = dp(10)
                    })
                }
            }
            root.addView(row); index += 4
        }
        button("ذخیره") { showMainMenu() }
        button("بازگشت به منو") { showMainMenu() }
    }

    private fun showCoinShop() {
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        val coins = prefs.getInt("coins", 152).coerceAtLeast(0)
        base("خرید سکه")
        root.addView(panel("موجودی فعلی: ${coins.toPersianDigits()} سکه", 17f))
        root.addView(panel("خرید ۵۰۰ سکه  ــ  ۱۰٬۰۰۰ تومان", 16f))
        button("انتخاب بستهٔ ۵۰۰ سکه") { showPaymentUnavailable() }
        root.addView(panel("خرید ۱٬۰۰۰ سکه  ــ  ۲۰٬۰۰۰ تومان", 16f))
        button("انتخاب بستهٔ ۱٬۰۰۰ سکه") { showPaymentUnavailable() }
        root.addView(panel("خرید ۱۰٬۰۰۰ سکه  ــ  ۵۰٬۰۰۰ تومان", 16f))
        button("انتخاب بستهٔ ۱۰٬۰۰۰ سکه") { showPaymentUnavailable() }
        root.addView(label("خرید واقعی هنوز به درگاه پرداخت متصل نیست؛ هیچ مبلغی دریافت نمی‌شود.", 13f))
        button("بازگشت به منو") { showMainMenu() }
    }

    private fun showPaymentUnavailable() {
        AlertDialog.Builder(this).setTitle("پرداخت فعال نیست")
            .setMessage("درگاه پرداخت در این نسخه متصل نشده است؛ سکه‌ای به حساب اضافه نمی‌شود و هیچ مبلغی دریافت نمی‌شود.")
            .setPositiveButton("متوجه شدم", null).show()
    }

    private fun showSetup(groupMode: Boolean = false) {
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        base("بازی جدید")
        root.addView(label("نام بازیکن", 15f))
        val nameInput = EditText(this).apply {
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE; setText(captainName); hint = "نام بازیکن"
            textSize = 17f * textScale; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            background = roundedDrawable(panelColor, mutedGold, 1); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(nameInput)
        root.addView(label("نام بازیکنان دیگر (با ویرگول جدا کنید)", 14f))
        val rivalNamesInput = EditText(this).apply {
            setSingleLine(true); setText(prefs.getString("rival_names", rivalNames.joinToString(", ")).orEmpty())
            hint = "مثلاً: ریش‌سیاه، گرگ دریا"; textSize = 15f * textScale
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            background = roundedDrawable(panelColor, mutedGold, 1); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(rivalNamesInput)
        root.addView(label("تعداد نفرات", 16f))
        root.addView(label("تعداد نفرات بین ۲ تا ۸ نفر است.", 13f))
        val countLabel = panel("تعداد نفرات: ${lastPlayerCount.toPersianDigits()}", 17f)
        root.addView(countLabel)
        val players = SeekBar(this).apply { max = 6; progress = (lastPlayerCount - 2).coerceIn(0, 6) }
        players.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                countLabel.text = "تعداد نفرات: ${(progress + 2).toPersianDigits()}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(players)
        root.addView(label("درجهٔ سختی", 16f))
        val difficultyNames = listOf("آسان", "معمولی", "سخت")
        val difficultyLabel = panel(difficultyNames[lastDifficulty.coerceIn(0, 2)], 17f)
        root.addView(difficultyLabel)
        val levels = SeekBar(this).apply { max = 2; progress = lastDifficulty.coerceIn(0, 2) }
        levels.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                difficultyLabel.text = difficultyNames[progress]
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(levels)
        root.addView(label("قوانین بازی", 15f))
        val chestKey = checkbox("فعال بودن جایزهٔ صندوق و کلید", prefs.getBoolean("chest_key_bonus", true))
        val kraken = checkbox("فعال بودن فشار کراکن", prefs.getBoolean("kraken_pressure", true))
        val passPlay = checkbox("بازی گروهی روی یک دستگاه", groupMode || lastPassAndPlay)
        root.addView(chestKey); root.addView(kraken); root.addView(passPlay)
        button("شروع بازی") {
            captainName = nameInput.text.toString().trim().ifBlank { "کاپیتان" }.take(24)
            rivalNames = rivalNamesInput.text.toString().split(",").map { it.trim().take(24) }
                .filter { it.isNotBlank() }.distinct().take(7)
            lastPlayerCount = players.progress + 2; lastDifficulty = levels.progress; difficulty = lastDifficulty
            lastRules = GameRules(chestKey.isChecked, kraken.isChecked)
            lastPassAndPlay = passPlay.isChecked || groupMode
            prefs.edit().putString("captain_name", captainName).putString("rival_names", rivalNames.joinToString(", "))
                .putBoolean("chest_key_bonus", chestKey.isChecked).putBoolean("kraken_pressure", kraken.isChecked).apply()
            startNewGame(lastPlayerCount, lastDifficulty, lastRules, lastPassAndPlay)
        }
        button("تنظیمات") { showSettingsScreen() }
        button("راهنما") { showHowToPlay() }
        button("بازگشت به منو") { showMainMenu() }
    }

    private fun Int.toPersianDigits(): String = toString().toPersianDigits()

    private fun String.toPersianDigits(): String = replace('0', '۰').replace('1', '۱')
        .replace('2', '۲').replace('3', '۳').replace('4', '۴').replace('5', '۵')
        .replace('6', '۶').replace('7', '۷').replace('8', '۸').replace('9', '۹')
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
        base("انتخاب توانایی")
        root.addView(label("${game.players[0].name}، توانایی این سفر را انتخاب کنید.", 16f))
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
                if (hapticsEnabled) choice.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                engine.setTrait(game, 0, trait)
                if (trait == TraitType.DAVY_JONES_LOCKER) chooseDavyJonesTarget(game) else renderGame()
            }
            root.addView(choice, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
        button("مشاهدهٔ همهٔ توانایی‌ها") {
            base("راهنمای توانایی‌های دزدان دریایی")
            TraitType.entries.forEach { root.addView(panel("${it.displayName}\n${it.description}", 14f)) }
            button("بازگشت به انتخاب توانایی") { chooseTrait() }
        }
    }

    private fun chooseDavyJonesTarget(game: GameState) {
        val rivals = game.players.filter { it.id != 0 }
        AlertDialog.Builder(this)
            .setTitle("انتخاب حریف")
            .setItems(rivals.map { it.name }.toTypedArray()) { _, which ->
                engine.setDavyJonesTarget(game, 0, rivals[which].id)
                renderGame()
            }
            .setOnCancelListener { renderGame() }
            .show()
    }

    private fun renderGame() {
        val game = state ?: return
        baseLandscapeGame("دزدان دریایی", game)
        val active = game.players[game.currentPlayer]
        status = panel(game.message, 16f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (game.message.contains("Bust!", ignoreCase = true)) Color.rgb(255, 142, 112) else gold)
        }
        root.addView(status)
        if (aiSequenceRunning && !game.passAndPlay && !active.isHuman) {
            root.addView(panel("🤖  ${active.name} در حال بازی است؛ حرکت‌ها یکی‌یکی نمایش داده می‌شوند.", 15f))
            root.addView(ProgressBar(this).apply {
                isIndeterminate = true
                contentDescription = "ربات در حال فکر کردن"
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)))
        }
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

        root.addView(label("میز گنج  •  ${game.board.size.toPersianDigits()} کارت", 17f))
        if (game.board.isEmpty()) {
            root.addView(panel("دریا آرام است؛ برای برداشتن اولین کارت، دستهٔ پایین را لمس کن.", 15f))
        } else {
            val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            game.board.forEachIndexed { index, card ->
                val protected = card.id in game.protectedCardIds
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = roundedDrawable(cardColor(card.type),
                        if (protected) Color.rgb(111, 220, 188) else gold,
                        if (protected) 3 else 1)
                    elevation = dp(5).toFloat()
                    contentDescription = "${card.type.displayName}, value ${card.value}${if (protected) ", protected from bust" else ""}"
                    alpha = if (reducedMotion) 1f else 0f
                    scaleX = if (reducedMotion) 1f else 0.68f
                    scaleY = if (reducedMotion) 1f else 0.68f
                    translationY = if (reducedMotion) 0f else dp(28).toFloat()
                    rotation = if (reducedMotion) 0f else if (index % 2 == 0) -7f else 7f
                }
                val cardArtId = resources.getIdentifier(cardDrawableName(card.type), "drawable", packageName)
                if (cardArtId != 0) {
                    tile.addView(ImageView(this).apply {
                        setImageResource(cardArtId)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "${card.type.displayName} card artwork"
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)).apply {
                        topMargin = dp(4)
                    })
                } else {
                    tile.addView(CardIllustrationView(this, card.type), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(70)
                    ).apply { topMargin = dp(4) })
                }
                tile.addView(TextView(this).apply {
                    text = cardNameFa(card.type)
                    textSize = 11f * textScale
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    contentDescription = card.type.displayName
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
                tile.addView(TextView(this).apply {
                    text = "${card.value.toPersianDigits()} امتیاز${if (protected) " • محافظت‌شده" else ""}"
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
                        .setStartDelay(index * 45L).setDuration(330).setInterpolator(DecelerateInterpolator()).start()
                    tile.postDelayed({
                        when (card.type) {
                            CardType.CANNON -> tile.animate().rotationBy(12f).setDuration(90).withEndAction {
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
                contentDescription = "میز کارت‌های گنج"
                addView(strip)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (!game.finished && aiSequenceRunning && !game.passAndPlay && !active.isHuman) {
            root.addView(panel("ربات ${active.name} در حال انتخاب حرکت است.", 14f))
        } else if (!game.finished && game.pendingEffect != null) {
            showEffectControls(game)
        }

        if (game.finished) {
            trackFinishedGame(game)
            root.addView(label("🏆  پایان بازی  🏆", 23f).apply {
                gravity = Gravity.CENTER
                setTextColor(gold)
                typeface = Typeface.DEFAULT_BOLD
            })
            root.addView(ConfettiView(this), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)
            ))
            button("خروج") { leaveGameScreen(game) }
            deleteSavedGame()
            return
        }

        button("خروج") { leaveGameScreen(game) }
        saveGame(game)
    }

    private fun effectInstruction(game: GameState): String = when (game.pendingEffect?.cardType) {
        CardType.CANNON -> "توپ: بازیکن هدف را در سمت راست انتخاب کن، سپس یکی از کارت‌های او را لمس کن. هدف دارای خطای توپ را فقط لمس کن."
        CardType.HOOK -> "قلاب: کارت یا کارت‌های خودت را در نوار پایین انتخاب کن؛ برای اجرا، عنوان کارت‌های خودت را نگه دار."
        CardType.MAP -> "نقشه: دستهٔ سوخته در پایین را لمس کن تا کارت بازیابی شود."
        CardType.SWORD -> "شمشیر: بازیکن هدف را در سمت راست انتخاب کن، سپس کارت موردنظرش را لمس کن."
        else -> "برای رد اثر ویژه، دستهٔ اصلی کارت را لمس کن."
    }

    private fun showEffectControls(game: GameState) {
        root.addView(panel(effectInstruction(game), 14f))
    }

    private fun finishPlayerAction(game: GameState) {
        if (game.finished) trackFinishedGame(game)
        selectedHookIds.clear()
        if (game.finished) deleteSavedGame() else saveGame(game)
        if (!game.finished && !game.passAndPlay && game.currentPlayer != 0) {
            beginAiSequence(game)
        } else {
            aiStepRunnable?.let { mainHandler.removeCallbacks(it) }
            aiSequenceRunning = false
            aiDrawsThisTurn = 0
            aiActionCount = 0
            renderGame()
        }
    }

    private fun beginAiSequence(game: GameState) {
        if (game.finished || game.passAndPlay || game.currentPlayer == 0) {
            aiSequenceRunning = false
            renderGame()
            return
        }
        if (!aiSequenceRunning) {
            aiSequenceRunning = true
            aiDrawsThisTurn = 0
            aiActionCount = 0
        }
        renderGame()
        scheduleNextAiAction(game, 850L)
    }

    private fun scheduleNextAiAction(game: GameState, delayMs: Long) {
        aiStepRunnable?.let { mainHandler.removeCallbacks(it) }
        val next = Runnable {
            if (state !== game) {
                aiSequenceRunning = false
                return@Runnable
            }
            if (game.finished || game.passAndPlay || game.currentPlayer == 0) {
                aiSequenceRunning = false
                aiDrawsThisTurn = 0
                aiActionCount = 0
                if (game.finished) {
                    trackFinishedGame(game)
                    deleteSavedGame()
                } else saveGame(game)
                renderGame()
                return@Runnable
            }
            if (aiActionCount >= 240) {
                aiSequenceRunning = false
                game.message = "بازی ربات‌ها متوقف شد تا از حلقهٔ بی‌نهایت جلوگیری شود."
                saveGame(game)
                renderGame()
                return@Runnable
            }

            val playerBefore = game.currentPlayer
            aiDrawsThisTurn = engine.playAiStep(game, aiDrawsThisTurn)
            aiActionCount++
            if (game.currentPlayer != playerBefore || game.finished) aiDrawsThisTurn = 0

            if (game.finished) {
                trackFinishedGame(game)
                deleteSavedGame()
            } else saveGame(game)
            renderGame()

            if (!game.finished && !game.passAndPlay && game.currentPlayer != 0) {
                val delay = if (game.pendingEffect != null) 620L else 850L
                scheduleNextAiAction(game, delay)
            } else {
                aiSequenceRunning = false
                aiDrawsThisTurn = 0
                aiActionCount = 0
                renderGame()
            }
        }
        aiStepRunnable = next
        mainHandler.postDelayed(next, delayMs)
    }

    private fun collectAfterConfirmation(game: GameState) {
        val active = game.players[game.currentPlayer]
        val hasBonus = game.rules.chestKeyBonusEnabled &&
            game.board.any { it.type == CardType.CHEST } && game.board.any { it.type == CardType.KEY }
        if (active.trait == TraitType.PLUNDERER && hasBonus) {
            val target = game.players.filter { it.id != active.id && it.bank.isNotEmpty() }
                .maxByOrNull { it.bank.size }?.id
            if (engine.collect(game, target)) finishPlayerAction(game) else renderGame()
            return
        }
        if (engine.collect(game)) finishPlayerAction(game) else renderGame()
    }

    private fun showHowToPlay() {
        base("راهنما")
        root.addView(panel("۱. کارت بردارید", 17f))
        root.addView(label("کارت بردارید تا میز گنج بزرگ‌تر شود. تکرار یک نوع کارت معمولاً کارت‌های محافظت‌نشدهٔ روی میز را می‌سوزاند."))
        root.addView(panel("۲. زمان توقف را تشخیص دهید", 17f))
        root.addView(label("با جمع کردن کارت‌ها، گنج روی میز را ذخیره و نوبت را واگذار کنید. با تمام شدن دستهٔ کارت، میز باقی‌مانده جمع می‌شود و بازی پایان می‌یابد."))
        root.addView(panel("۳. کارت‌های ویژه", 17f))
        root.addView(label("توپ به کارت‌های ذخیره‌شدهٔ حریف حمله می‌کند؛ قلاب کارت‌ها را به میز برمی‌گرداند؛ نقشه کارت سوخته را بازیابی می‌کند و شمشیر کارت مجازی را از حریف می‌دزدد. هر اثر را می‌توان رد کرد."))
        root.addView(panel("۴. مراقب کراکن باشید", 17f))
        root.addView(label("کراکن ممکن است پیش از جمع کردن گنج، شما را مجبور به برداشتن کارت‌های بیشتری کند. پناهگاه امن و خسیس می‌توانند از بعضی کارت‌ها محافظت کنند."))
        root.addView(panel("۵. امتیاز گنج", 17f))
        root.addView(label("بالاترین ارزش ذخیره‌شده از هر نوع کارت در امتیاز حساب می‌شود. فلس طلایی برای پری دریایی ۵ امتیاز اضافه می‌کند. صندوق و کلید جایزه می‌دهند و گنج‌یاب آن را سه‌برابر می‌کند."))
        root.addView(panel("۶. بازی گروهی", 17f))
        root.addView(label("برای چند بازیکن روی یک دستگاه، بازی گروهی را در تنظیمات شروع فعال کنید؛ در غیر این صورت با دزدان دریایی هوش مصنوعی بازی می‌کنید."))
        button("بازگشت") { if (state != null && state?.finished == false) renderGame() else showSetup() }
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
        base("راهنمای کارت‌ها")
        CardType.entries.forEach { type ->
            root.addView(panel("${type.symbol}  ${cardNameFa(type)}", 17f))
            root.addView(label(cardDescriptionFa(type)))
        }
        button("بازگشت به منو") { showSetup() }
    }

    private fun showScoreBreakdown(game: GameState) {
        val report = game.players.joinToString("\n\n") { player ->
            "${player.name} — ${player.score} points\n" +
                engine.scoreBreakdown(player).joinToString("\n")
        }
        AlertDialog.Builder(this)
            .setTitle("جزئیات امتیاز نهایی")
            .setMessage(report)
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun showTurnLog(game: GameState) {
        AlertDialog.Builder(this)
            .setTitle("گزارش بازی")
            .setMessage(game.turnLog.joinToString("\n\n").ifBlank { "No turns recorded yet." })
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun showStatistics() {
        val prefs = getSharedPreferences("dead_man_draw_prefs", MODE_PRIVATE)
        val matches = prefs.getInt("matches_played", 0)
        val wins = prefs.getInt("matches_won", 0)
        val points = prefs.getInt("total_score", 0)
        val best = prefs.getInt("best_score", 0)
        AlertDialog.Builder(this)
            .setTitle("آمار بازی")
            .setMessage("Completed games: $matches\nVictories: $wins\nTotal final score: $points\nBest final score: $best")
            .setPositiveButton("بستن", null)
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

    private fun cardNameFa(type: CardType): String = when (type) {
        CardType.ANCHOR -> "لنگر"
        CardType.CANNON -> "توپ"
        CardType.CHEST -> "صندوق"
        CardType.HOOK -> "قلاب"
        CardType.KEY -> "کلید"
        CardType.KRAKEN -> "کراکن"
        CardType.MAP -> "نقشه"
        CardType.MERMAID -> "پری دریایی"
        CardType.ORACLE -> "پیشگو"
        CardType.SWORD -> "شمشیر"
    }

    private fun traitNameFa(trait: TraitType): String = when (trait) {
        TraitType.BEASTMASTER -> "ارباب هیولاها"
        TraitType.CAPTAINS_HOOK -> "قلاب ناخدا"
        TraitType.CASANOVA -> "کازانووا"
        TraitType.DAVY_JONES_LOCKER -> "گنجینهٔ دیوی جونز"
        TraitType.FISHERMAN -> "ماهیگیر"
        TraitType.GOLDEN_SCALES -> "فلس طلایی"
        TraitType.MISER -> "خسیس"
        TraitType.NAVIGATOR -> "ناوبر"
        TraitType.MASTER_GUNNER -> "توپچی ارشد"
        TraitType.MISFIRE -> "خطای توپ"
        TraitType.MYSTIC -> "عارف"
        TraitType.PARRY -> "دفع ضربه"
        TraitType.PLUNDERER -> "غارتگر"
        TraitType.SAFE_HARBOR -> "پناهگاه امن"
        TraitType.SCAVENGER -> "لاشه‌جمع‌کن"
        TraitType.SWORDSMAN -> "شمشیرزن"
        TraitType.TREASURE_HUNTER -> "گنج‌یاب"
    }

    private fun cardDescriptionFa(type: CardType): String = when (type) {
        CardType.ANCHOR -> "لنگر میز را ثابت نگه می‌دارد؛ پناهگاه امن از لنگر و دو کارت بعدی محافظت می‌کند."
        CardType.CANNON -> "توپ به کارت‌های ذخیره‌شدهٔ حریف حمله می‌کند؛ توپچی ارشد همهٔ کارت‌های همان نوع را حذف می‌کند."
        CardType.CHEST -> "همراه شدن صندوق با کلید روی میز، هنگام ذخیره‌سازی جایزه ایجاد می‌کند."
        CardType.HOOK -> "قلاب یک نوع کارت ذخیره‌شده را به میز برمی‌گرداند؛ قلاب ناخدا اجازهٔ دو نوع را می‌دهد."
        CardType.KEY -> "کلید همراه صندوق روی میز، هنگام جمع‌کردن جایزه می‌دهد."
        CardType.KRAKEN -> "کراکن معمولاً دو کارت اضافی را اجباری می‌کند؛ ارباب هیولاها این تعداد را به چهار می‌رساند."
        CardType.MAP -> "نقشه یک کارت را از دستهٔ سوخته بازیابی می‌کند؛ ناوبر می‌تواند کارت مجاز را انتخاب کند."
        CardType.MERMAID -> "بالاترین ارزش پری دریایی ذخیره‌شده در امتیاز حساب می‌شود؛ فلس طلایی ۵ امتیاز اضافه می‌کند."
        CardType.ORACLE -> "پیشگو کارت بعدی را آشکار می‌کند؛ عارف تا سه کارت آینده را می‌بیند."
        CardType.SWORD -> "شمشیر یک کارت مجاز را از ذخیرهٔ حریف می‌دزدد؛ شمشیرزن می‌تواند نوعی را بدزدد که خودش هم دارد."
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
