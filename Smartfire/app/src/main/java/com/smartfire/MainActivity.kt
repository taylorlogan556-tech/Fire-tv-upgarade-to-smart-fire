package com.smartfire

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class MainActivity : Activity() {

    private val dark = Color.rgb(16, 22, 34)
    private val panel = Color.rgb(39, 52, 72)
    private val orange = Color.rgb(255, 129, 43)

    private val prefs by lazy {
        getSharedPreferences("smartfire_parents", Context.MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sessionStart = 0L
    private var browser: WebView? = null
    private var screen = "home"

    private val lockable = listOf(
        "YouTube", "Netflix", "Prime Video",
        "Spotify", "BBC iPlayer",
        "Pac-Man", "Tic-Tac-Toe", "Chess",
        "Apps", "Files"
    )

    private val tick = object : Runnable {
        override fun run() {
            recordUsage()

            if (
                timeExpired() &&
                screen != "parents" &&
                screen != "limit"
            ) {
                showTimeLimit()
            }

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetDailyUsage()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        resetDailyUsage()
        sessionStart = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
    }

    override fun onPause() {
        recordUsage()
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun resetDailyUsage() {
        if (prefs.getString("usage_date", "") != today()) {
            prefs.edit()
                .putString("usage_date", today())
                .putLong("used_ms", 0L)
                .apply()
        }
    }

    private fun recordUsage() {
        resetDailyUsage()
        if (sessionStart == 0L) return

        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - sessionStart).coerceAtLeast(0L)

        prefs.edit()
            .putLong(
                "used_ms",
                prefs.getLong("used_ms", 0L) + elapsed
            )
            .apply()

        sessionStart = now
    }

    private fun timeExpired(): Boolean {
        val minutes = prefs.getInt("daily_limit", 0)
        return minutes > 0 &&
            prefs.getLong("used_ms", 0L) >= minutes * 60_000L
    }

    private fun remainingTime(): String {
        val limit = prefs.getInt("daily_limit", 0)
        if (limit == 0) return "Unlimited"

        val remaining = (
            limit * 60_000L -
                prefs.getLong("used_ms", 0L)
        ).coerceAtLeast(0L)

        return "${(remaining + 59_999L) / 60_000L} minutes"
    }

    // COMMON UI

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(20, 15, 20, 20)
        setBackgroundColor(dark)
    }

    private fun title(
        value: String,
        size: Float = 23f
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(5, 10, 5, 10)
    }

    private fun note(value: String) =
        TextView(this).apply {
            text = value
            textSize = 17f
            setTextColor(Color.LTGRAY)
            setPadding(12, 10, 12, 18)
        }

    private fun button(
        label: String,
        action: () -> Unit
    ) = Button(this).apply {
        text = label
        isAllCaps = false
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun display(root: LinearLayout) {
        setContentView(
            ScrollView(this).apply {
                fillViewport = true
                addView(root)
            }
        )
    }

    private fun message(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }

    // HOME SCREEN

    private fun showHome() {
        closeBrowser()
        recordUsage()

        if (timeExpired()) {
            showTimeLimit()
            return
        }

        screen = "home"

        val root = page()

        root.addView(
            title("🔥 SMARTFIRE", 34f).apply {
                setTextColor(orange)
            }
        )

        root.addView(title("Welcome home", 20f))

        section(
            root,
            "Streaming",
            listOf(
                "YouTube",
                "Netflix",
                "Prime Video",
                "Spotify",
                "BBC iPlayer"
            )
        )

        section(
            root,
            "Games",
            listOf(
                "Pac-Man",
                "Tic-Tac-Toe",
                "Chess"
            )
        )

        section(
            root,
            "System",
            listOf(
                "Browser",
                "Apps",
                "Files",
                "Settings",
                "Parents"
            )
        )

        root.addView(
            note("Time remaining: ${remainingTime()}")
        )

        display(root)
    }

    private fun section(
        root: LinearLayout,
        heading: String,
        names: List<String>
    ) {
        root.addView(title(heading, 24f))

        names.chunked(3).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            group.forEach { name ->
                val shape = GradientDrawable().apply {
                    cornerRadius = 18f
                    setColor(panel)
                }

                val tile = TextView(this).apply {
                    text =
                        if (isLocked(name)) "🔒 $name"
                        else name

                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = shape
                    isFocusable = true
                    isClickable = true

                    setOnFocusChangeListener { _, focused ->
                        shape.setColor(
                            if (focused) orange else panel
                        )
                    }

                    setOnClickListener {
                        openTile(name)
                    }
                }

                row.addView(
                    tile,
                    LinearLayout.LayoutParams(
                        0, 105, 1f
                    ).apply {
                        setMargins(4, 4, 4, 4)
                    }
                )
            }

            root.addView(row)
        }
    }

    // OPEN APPS AND GAMES

    private fun isLocked(name: String): Boolean =
        prefs.getBoolean("lock_$name", false)

    private fun openTile(name: String) {
        when (name) {
            "Settings" -> {
                showSmartfireSettings()
                return
            }

            "Parents" -> {
                openParents()
                return
            }
        }

        if (timeExpired()) {
            showTimeLimit()
            return
        }

        val requiresPin =
            (name == "Browser" &&
                prefs.getBoolean("browser_locked", false)) ||
                isLocked(name)

        if (requiresPin) {
            verifyPin {
                launchTile(name)
            }
        } else {
            launchTile(name)
        }
    }

    private fun launchTile(name: String) {
        when (name) {
            "YouTube" -> launchApp(
                listOf(
                    "com.amazon.firetv.youtube",
                    "com.google.android.youtube.tv",
                    "com.google.android.youtube"
                ),
                "https://www.youtube.com"
            )

            "Netflix" -> launchApp(
                listOf(
                    "com.netflix.ninja",
                    "com.netflix.mediaclient"
                ),
                "https://www.netflix.com"
            )

            "Prime Video" -> launchApp(
                listOf(
                    "com.amazon.avod.thirdpartyclient"
                ),
                "https://www.primevideo.com"
            )

            "Spotify" -> launchApp(
                listOf(
                    "com.spotify.tv.android",
                    "com.spotify.music"
                ),
                "https://open.spotify.com"
            )

            "BBC iPlayer" -> launchApp(
                listOf(
                    "bbc.iplayer.android",
                    "bbc.iplayer.android.tv"
                ),
                "https://www.bbc.co.uk/iplayer"
            )

            "Browser" -> openWebsite(
                "https://www.google.com"
            )

            "Pac-Man" -> playPacman()
            "Tic-Tac-Toe" -> playTicTacToe()
            "Chess" -> playChess()
            "Apps" -> showApps()

            "Files" -> safeStart(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            )
        }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            message("No compatible app installed")
        }
    }

    private fun launchApp(
        packages: List<String>,
        fallback: String
    ) {
        for (pkg in packages) {
            val intent =
                packageManager
                    .getLeanbackLaunchIntentForPackage(pkg)
                    ?: packageManager
                        .getLaunchIntentForPackage(pkg)

            if (intent != null) {
                safeStart(intent)
                return
            }
        }

        openWebsite(fallback)
    }

    // SMARTFIRE SETTINGS

    private fun showSmartfireSettings() {
        closeBrowser()
        screen = "settings"

        val root = page()

        root.addView(
            title("⚙️ SMARTFIRE SETTINGS", 29f).apply {
                setTextColor(orange)
            }
        )

        root.addView(title("👨‍👩‍👧 Kids & Parents", 25f))

        root.addView(title("For kids", 20f))

        root.addView(
            note(
                "Some apps, games and websites may be " +
                    "locked by your parent or guardian. " +
                    "Ask them if you need access."
            )
        )

        root.addView(title("For parents", 20f))

        root.addView(
            note(
                "Set a private PIN, choose which apps " +
                    "and games are available, lock the " +
                    "browser and manage daily screen time."
            )
        )

        root.addView(
            button("🔒 Manage Parental Controls") {
                openParents()
            }
        )

        root.addView(
            title("Device settings", 22f)
        )

        root.addView(
            button("Android / Fire TV Settings") {
                safeStart(
                    Intent(Settings.ACTION_SETTINGS)
                )
            }
        )

        root.addView(
            button("Back to Smartfire") {
                showHome()
            }
        )

        display(root)
    }

    // PARENT PIN

    private fun hashPin(
        pin: String,
        salt: String
    ): String {
        val bytes =
            MessageDigest.getInstance("SHA-256")
                .digest(
                    "$salt:$pin".toByteArray(Charsets.UTF_8)
                )

        return bytes.joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
    }

    private fun hasPin(): Boolean =
        prefs.contains("pin_hash")

    private fun pinInput() = EditText(this).apply {
        hint = "Parent PIN"

        inputType =
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD

        filters = arrayOf(
            android.text.InputFilter.LengthFilter(8)
        )
    }

    private fun openParents() {
        if (hasPin()) {
            verifyPin {
                showParents()
            }
        } else {
            createPin()
        }
    }

    private fun createPin() {
        val input = pinInput()

        AlertDialog.Builder(this)
            .setTitle("Create parent PIN")
            .setMessage(
                "A parent should choose a private " +
                    "4–8 digit PIN."
            )
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val pin = input.text.toString()

                if (
                    pin.length !in 4..8 ||
                    !pin.all { it.isDigit() }
                ) {
                    message("PIN must contain 4–8 digits")
                    createPin()
                } else {
                    confirmPin(pin)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmPin(pin: String) {
        val input = pinInput()

        AlertDialog.Builder(this)
            .setTitle("Confirm parent PIN")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                if (input.text.toString() != pin) {
                    message("PINs do not match")
                    createPin()
                } else {
                    val salt =
                        UUID.randomUUID().toString()

                    prefs.edit()
                        .putString("pin_salt", salt)
                        .putString(
                            "pin_hash",
                            hashPin(pin, salt)
                        )
                        .apply()

                    showParents()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyPin(onSuccess: () -> Unit) {
        if (!hasPin()) {
            message("Create a parent PIN first")
            createPin()
            return
        }

        val input = pinInput()

        AlertDialog.Builder(this)
            .setTitle("Parent PIN required")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val salt =
                    prefs.getString("pin_salt", "") ?: ""

                val expected =
                    prefs.getString("pin_hash", "") ?: ""

                if (
                    hashPin(
                        input.text.toString(),
                        salt
                    ) == expected
                ) {
                    onSuccess()
                } else {
                    message("Incorrect PIN")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // KIDS & PARENTS CONTROLS

    private fun showParents() {
        closeBrowser()
        screen = "parents"

        val root = page()

        root.addView(
            title("🔒 KIDS & PARENTS", 29f).apply {
                setTextColor(orange)
            }
        )

        root.addView(
            note(
                "Parents can manage Smartfire's " +
                    "browser, apps, games and daily usage."
            )
        )

        val browserLocked =
            prefs.getBoolean("browser_locked", false)

        root.addView(
            button(
                if (browserLocked)
                    "Browser: Locked"
                else
                    "Browser: Unlocked"
            ) {
                prefs.edit()
                    .putBoolean(
                        "browser_locked",
                        !browserLocked
                    )
                    .apply()

                showParents()
            }
        )

        root.addView(
            button("Choose locked apps and games") {
                chooseLockedApps()
            }
        )

        root.addView(
            button(
                "Daily limit: ${limitDescription()}"
            ) {
                chooseTimeLimit()
            }
        )

        root.addView(
            note(
                "Used today: " +
                    "${prefs.getLong("used_ms", 0L) / 60_000L} " +
                    "minutes"
            )
        )

        root.addView(
            button("Change parent PIN") {
                changePin()
            }
        )

        root.addView(
            button("Back to Settings") {
                showSmartfireSettings()
            }
        )

        display(root)
    }

    private fun chooseLockedApps() {
        val checked = lockable.map {
            isLocked(it)
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Lock apps and games")
            .setMultiChoiceItems(
                lockable.toTypedArray(),
                checked
            ) { _, index, selected ->
                checked[index] = selected
            }
            .setPositiveButton("Save") { _, _ ->
                val editor = prefs.edit()

                lockable.forEachIndexed { index, name ->
                    editor.putBoolean(
                        "lock_$name",
                        checked[index]
                    )
                }

                editor.apply()
                showParents()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun limitDescription(): String {
        val limit = prefs.getInt("daily_limit", 0)

        return if (limit == 0) {
            "Unlimited"
        } else {
            "$limit minutes"
        }
    }

    private fun chooseTimeLimit() {
        val values = intArrayOf(
            0, 15, 30, 45, 60, 90, 120, 180
        )

        val labels = values.map {
            if (it == 0) "Unlimited"
            else "$it minutes"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Daily Smartfire time limit")
            .setItems(labels) { _, index ->
                recordUsage()

                prefs.edit()
                    .putInt(
                        "daily_limit",
                        values[index]
                    )
                    .apply()

                showParents()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun changePin() {
        val input = pinInput()

        AlertDialog.Builder(this)
            .setTitle("New parent PIN")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val pin = input.text.toString()

                if (
                    pin.length !in 4..8 ||
                    !pin.all { it.isDigit() }
                ) {
                    message("Use 4–8 digits")
                } else {
                    confirmPin(pin)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimeLimit() {
        closeBrowser()
        screen = "limit"

        val root = page()

        root.addView(
            title("⏰ TIME IS UP", 32f).apply {
                setTextColor(orange)
            }
        )

        root.addView(
            note(
                "Today's Smartfire time limit " +
                    "has been reached."
            )
        )

        root.addView(
            button("Parent PIN / Settings") {
                verifyPin {
                    showParents()
                }
            }
        )

        display(root)
    }

    // BUILT-IN BROWSER

    private fun openWebsite(url: String) {
        closeBrowser()
        screen = "browser"

        val root = page().apply {
            setPadding(4, 4, 4, 4)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val address = EditText(this).apply {
            setSingleLine(true)
            setText(url)
            setTextColor(Color.WHITE)
            textSize = 15f
        }

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    address.setText(url)
                }
            }
        }

        browser = web

        fun navigate() {
            val input =
                address.text.toString().trim()

            if (input.isEmpty()) return

            val destination = when {
                input.startsWith("https://") ||
                    input.startsWith("http://") -> input

                input.contains(".") &&
                    !input.contains(" ") ->
                    "https://$input"

                else ->
                    "https://www.google.com/search?q=" +
                        Uri.encode(input)
            }

            web.loadUrl(destination)
        }

        toolbar.addView(
            button("←") {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    showHome()
                }
            }
        )

        toolbar.addView(
            button("→") {
                if (web.canGoForward()) {
                    web.goForward()
                }
            }
        )

        toolbar.addView(
            address,
            LinearLayout.LayoutParams(
                0, 65, 1f
            )
        )

        toolbar.addView(
            button("GO") {
                navigate()
            }
        )

        toolbar.addView(
            button("⌂") {
                showHome()
            }
        )

        root.addView(toolbar)

        root.addView(
            web,
            LinearLayout.LayoutParams(
                -1, 0, 1f
            )
        )

        setContentView(root)
        web.loadUrl(url)
    }

    private fun closeBrowser() {
        browser?.let { web ->
            web.stopLoading()
            (web.parent as? ViewGroup)
                ?.removeView(web)
            web.destroy()
        }

        browser = null
    }

    private fun showApps() {
        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(
                Intent.CATEGORY_LEANBACK_LAUNCHER
            )
        }

        val apps =
            packageManager.queryIntentActivities(query, 0)

        AlertDialog.Builder(this)
            .setTitle("Installed TV apps")
            .setItems(
                apps.map {
                    it.loadLabel(packageManager).toString()
                }.toTypedArray()
            ) { _, index ->
                val info = apps[index].activityInfo

                safeStart(
                    Intent(Intent.ACTION_MAIN).apply {
                        setClassName(
                            info.packageName,
                            info.name
                        )
                    }
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // PAC-MAN

    private fun playPacman() {
        screen = "pacman"

        val maze = arrayOf(
            "#########",
            "#.......#",
            "#.##.##.#",
            "#.......#",
            "#.##.##.#",
            "#.......#",
            "#########"
        ).map {
            it.toCharArray()
        }.toTypedArray()

        var px = 1
        var py = 1
        var gx = 7
        var gy = 5
        var score = 0
        var ended = false

        val total = maze.sumOf { row ->
            row.count { it == '.' }
        }

        val root = page()
        root.addView(title("🟡 PAC-MAN", 29f))

        val status = title("Dots: 0 / $total", 19f)
        root.addView(status)

        val grid = GridLayout(this).apply {
            columnCount = 9
        }

        val cells = Array(7) {
            arrayOfNulls<TextView>(9)
        }

        for (y in 0..6) {
            for (x in 0..8) {
                val cell = title("", 22f)
                cells[y][x] = cell

                grid.addView(
                    cell,
                    GridLayout.LayoutParams().apply {
                        width = 43
                        height = 43
                    }
                )
            }
        }

        fun render() {
            for (y in 0..6) {
                for (x in 0..8) {
                    val cell = cells[y][x]!!

                    cell.setBackgroundColor(
                        if (maze[y][x] == '#')
                            Color.rgb(25, 55, 180)
                        else dark
                    )

                    cell.text = when {
                        x == px && y == py -> "🟡"
                        x == gx && y == gy -> "👻"
                        maze[y][x] == '.' -> "·"
                        else -> ""
                    }
                }
            }
        }

        fun move(dx: Int, dy: Int) {
            if (ended) return

            val nx = px + dx
            val ny = py + dy

            if (maze[ny][nx] == '#') return

            px = nx
            py = ny

            if (maze[py][px] == '.') {
                maze[py][px] = ' '
                score++
            }

            if (px == gx && py == gy) {
                ended = true
                status.text = "Ghost caught you!"
                render()
                return
            }

            if (score == total) {
                ended = true
                status.text = "You win!"
                render()
                return
            }

            val steps = listOf(
                Pair(1, 0),
                Pair(-1, 0),
                Pair(0, 1),
                Pair(0, -1)
            ).filter { (dx, dy) ->
                maze[gy + dy][gx + dx] != '#'
            }.sortedBy { (dx, dy) ->
                abs(gx + dx - px) +
                    abs(gy + dy - py)
            }

            if (steps.isNotEmpty()) {
                gx += steps[0].first
                gy += steps[0].second
            }

            if (px == gx && py == gy) {
                ended = true
                status.text = "Ghost caught you!"
            } else {
                status.text = "Dots: $score / $total"
            }

            render()
        }

        root.addView(grid)

        root.addView(
            button("▲") { move(0, -1) }
        )

        root.addView(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER

                addView(
                    button("◀") { move(-1, 0) }
                )

                addView(
                    button("▼") { move(0, 1) }
                )

                addView(
                    button("▶") { move(1, 0) }
                )
            }
        )

        root.addView(
            button("Play again") {
                playPacman()
            }
        )

        root.addView(
            button("Home") {
                showHome()
            }
        )

        root.isFocusableInTouchMode = true

        root.setOnKeyListener { _, code, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP ->
                        move(0, -1)

                    KeyEvent.KEYCODE_DPAD_DOWN ->
                        move(0, 1)

                    KeyEvent.KEYCODE_DPAD_LEFT ->
                        move(-1, 0)

                    KeyEvent.KEYCODE_DPAD_RIGHT ->
                        move(1, 0)

                    else ->
                        return@setOnKeyListener false
                }

                true
            }
        }

        display(root)
        render()
        root.requestFocus()
    }

    // TIC-TAC-TOE

    private fun playTicTacToe() {
        screen = "tictactoe"

        val board = Array(9) { "" }
        val cells = arrayOfNulls<Button>(9)
        var player = "X"
        var ended = false

        val winningLines = listOf(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            listOf(0, 3, 6),
            listOf(1, 4, 7),
            listOf(2, 5, 8),
            listOf(0, 4, 8),
            listOf(2, 4, 6)
        )

        val root = page()
        root.addView(title("TIC-TAC-TOE", 29f))

        val status = title("Player X's turn", 20f)
        root.addView(status)

        val grid = GridLayout(this).apply {
            columnCount = 3
        }

        for (i in 0..8) {
            val cell = button(" ") {
                if (!ended && board[i].isEmpty()) {
                    board[i] = player
                    cells[i]?.text = player

                    val win =
                        winningLines.firstOrNull { line ->
                            line.all {
                                board[it] == player
                            }
                        }

                    if (win != null) {
                        ended = true
                        status.text = "$player wins!"

                        win.forEach {
                            cells[it]?.setBackgroundColor(orange)
                        }
                    } else if (
                        board.all { it.isNotEmpty() }
                    ) {
                        ended = true
                        status.text = "Draw!"
                    } else {
                        player =
                            if (player == "X") "O" else "X"

                        status.text =
                            "Player $player's turn"
                    }
                }
            }.apply {
                textSize = 28f
            }

            cells[i] = cell

            grid.addView(
                cell,
                GridLayout.LayoutParams().apply {
                    width = 120
                    height = 85
                    setMargins(4, 4, 4, 4)
                }
            )
        }

        root.addView(grid)

        root.addView(
            button("Play again") {
                playTicTacToe()
            }
        )

        root.addView(
            button("Home") {
                showHome()
            }
        )

        display(root)
        cells[4]?.requestFocus()
    }

    // SIMPLIFIED TWO-PLAYER CHESS

    private fun playChess() {
        screen = "chess"

        val board = arrayOf(
            "rnbqkbnr".toCharArray(),
            "pppppppp".toCharArray(),
            "........".toCharArray(),
            "........".toCharArray(),
            "........".toCharArray(),
            "........".toCharArray(),
            "PPPPPPPP".toCharArray(),
            "RNBQKBNR".toCharArray()
        )

        val symbols = mapOf(
            'K' to "♔", 'Q' to "♕",
            'R' to "♖", 'B' to "♗",
            'N' to "♘", 'P' to "♙",
            'k' to "♚", 'q' to "♛",
            'r' to "♜", 'b' to "♝",
            'n' to "♞", 'p' to "♟"
        )

        val cells = Array(8) {
            arrayOfNulls<Button>(8)
        }

        var whiteTurn = true
        var selectedX = -1
        var selectedY = -1
        var ended = false

        fun isWhite(piece: Char) =
            piece in 'A'..'Z'

        fun own(piece: Char) =
            piece != '.' &&
                isWhite(piece) == whiteTurn

        fun clearPath(
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int
        ): Boolean {
            val dx = (x2 - x1).compareTo(0)
            val dy = (y2 - y1).compareTo(0)

            var x = x1 + dx
            var y = y1 + dy

            while (x != x2 || y != y2) {
                if (board[y][x] != '.') return false

                x += dx
                y += dy
            }

            return true
        }

        fun validMove(
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int
        ): Boolean {
            if (x1 == x2 && y1 == y2) return false

            val piece = board[y1][x1]
            val target = board[y2][x2]

            if (piece == '.') return false

            if (
                target != '.' &&
                isWhite(piece) == isWhite(target)
            ) {
                return false
            }

            val dx = abs(x2 - x1)
            val dy = abs(y2 - y1)

            return when (piece.lowercaseChar()) {
                'p' -> {
                    val step =
                        if (isWhite(piece)) -1 else 1

                    val start =
                        if (isWhite(piece)) 6 else 1

                    if (x1 == x2) {
                        target == '.' && (
                            y2 - y1 == step ||
                            (
                                y1 == start &&
                                y2 - y1 == step * 2 &&
                                board[y1 + step][x1] == '.'
                            )
                        )
                    } else {
                        dx == 1 &&
                            y2 - y1 == step &&
                            target != '.'
                    }
                }

                'r' ->
                    (dx == 0 || dy == 0) &&
                        clearPath(x1, y1, x2, y2)

                'b' ->
                    dx == dy &&
                        clearPath(x1, y1, x2, y2)

                'q' ->
                    (dx == dy || dx == 0 || dy == 0) &&
                        clearPath(x1, y1, x2, y2)

                'n' ->
                    (dx == 1 && dy == 2) ||
                        (dx == 2 && dy == 1)

                'k' ->
                    dx <= 1 && dy <= 1

                else -> false
            }
        }

        val root = page()
        root.addView(title("♟ CHESS", 29f))

        val status = title("White's turn", 20f)
        root.addView(status)

        val grid = GridLayout(this).apply {
            columnCount = 8
        }

        fun render() {
            for (y in 0..7) {
                for (x in 0..7) {
                    val cell = cells[y][x]!!
                    val piece = board[y][x]

                    cell.text = symbols[piece] ?: " "

                    cell.setTextColor(
                        if (isWhite(piece))
                            Color.WHITE
                        else
                            Color.BLACK
                    )

                    cell.setBackgroundColor(
                        if (
                            x == selectedX &&
                            y == selectedY
                        ) {
                            orange
                        } else if ((x + y) % 2 == 0) {
                            Color.rgb(170, 183, 195)
                        } else {
                            Color.rgb(75, 99, 120)
                        }
                    )
                }
            }
        }

        for (y in 0..7) {
            for (x in 0..7) {
                val cx = x
                val cy = y

                val cell = button(" ") {
                    if (!ended) {
                        val piece = board[cy][cx]

                        if (selectedX == -1) {
                            if (own(piece)) {
                                selectedX = cx
                                selectedY = cy
                                status.text =
                                    "Select destination"
                            }
                        } else if (own(piece)) {
                            selectedX = cx
                            selectedY = cy
                        } else if (
                            validMove(
                                selectedX,
                                selectedY,
                                cx,
                                cy
                            )
                        ) {
                            val moving =
                                board[selectedY][selectedX]

                            val captured =
                                board[cy][cx]

                            board[cy][cx] = moving
                            board[selectedY][selectedX] = '.'

                            if (
                                moving == 'P' &&
                                cy == 0
                            ) {
                                board[cy][cx] = 'Q'
                            }

                            if (
                                moving == 'p' &&
                                cy == 7
                            ) {
                                board[cy][cx] = 'q'
                            }

                            selectedX = -1
                            selectedY = -1

                            if (
                                captured.lowercaseChar() == 'k'
                            ) {
                                ended = true

                                status.text =
                                    if (whiteTurn)
                                        "White wins!"
                                    else
                                        "Black wins!"
                            } else {
                                whiteTurn = !whiteTurn

                                status.text =
                                    if (whiteTurn)
                                        "White's turn"
                                    else
                                        "Black's turn"
                            }
                        } else {
                            selectedX = -1
                            selectedY = -1
                            status.text = "Invalid move"
                        }

                        render()
                    }
                }.apply {
                    textSize = 27f
                    minWidth = 0
                    minHeight = 0
                    setPadding(0, 0, 0, 0)
                }

                cells[y][x] = cell

                grid.addView(
                    cell,
                    GridLayout.LayoutParams().apply {
                        width = 49
                        height = 49
                        setMargins(1, 1, 1, 1)
                    }
                )
            }
        }

        root.addView(grid)

        root.addView(
            button("Play again") {
                playChess()
            }
        )

        root.addView(
            button("Home") {
                showHome()
            }
        )

        display(root)
        render()
        cells[6][4]?.requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val web = browser

        if (web != null && web.canGoBack()) {
            web.goBack()
        } else if (screen == "parents") {
            showSmartfireSettings()
        } else if (screen != "home") {
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        closeBrowser()
        super.onDestroy()
    }
}
