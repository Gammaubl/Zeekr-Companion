package com.zeekr7x.tubedash

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var leftRail: LinearLayout
    private lateinit var homePage: ScrollView
    private lateinit var settingsPage: ScrollView
    private lateinit var webViewContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var urlBarContainer: LinearLayout
    private lateinit var urlEditText: EditText
    private lateinit var dimVeil: View

    private lateinit var modeCardSub: TextView
    private lateinit var pipCardSub: TextView
    private lateinit var hudInfoSub: TextView
    private var isDimmed = false
    private var dimAlpha = 135

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()
        setupCarAudioFocus()
        setupMediaSession()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B0D12"))
        }

        val mainHorizontal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Persistent Left Rail (Zeekr OS 7 styling)
        leftRail = buildLeftRail()
        mainHorizontal.addView(leftRail)

        // 2. Content Stack
        val contentStack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        homePage = buildHomePage()
        settingsPage = buildSettingsPage()
        webViewContainer = buildWebViewPlayer()

        contentStack.addView(webViewContainer)
        contentStack.addView(homePage)
        contentStack.addView(settingsPage)

        showTab("HOME")
        mainHorizontal.addView(contentStack)
        root.addView(mainHorizontal)

        // 3. Night Dim Veil (passes touches through)
        dimVeil = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(dimAlpha, 12, 9, 5))
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        root.addView(dimVeil)

        setContentView(root)
    }

    private fun buildLeftRail(): LinearLayout {
        val widthPx = dp(92)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#12161F"))
            setPadding(0, dp(16), 0, dp(16))
            layoutParams = LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT)

            addView(railItem("Home") { showTab("HOME") })
            addView(railItem("YouTube") {
                showTab("PLAYER")
                val target = "https://www.youtube.com"
                if (webView.url != target) {
                    webView.loadUrl(target)
                }
            })
            addView(railItem("Browse") {
                showTab("PLAYER")
                urlBarContainer.visibility = if (urlBarContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            })
            addView(railItem("Pop-Up") {
                Prefs.setOutputMode(this@MainActivity, 0)
                Prefs.setLastUsedDrivingMode(this@MainActivity, 0)
                launchDrivingOverlayOrHud(0)
            })
            addView(railItem("HUD") {
                Prefs.setOutputMode(this@MainActivity, 1)
                Prefs.setLastUsedDrivingMode(this@MainActivity, 1)
                launchDrivingOverlayOrHud(1)
            })
            addView(railItem("Dual") {
                Prefs.setOutputMode(this@MainActivity, 2)
                Prefs.setLastUsedDrivingMode(this@MainActivity, 2)
                launchDrivingOverlayOrHud(2)
            })
            addView(railItem("PiP") { enterCarPipMode() })
            addView(railItem("Dim") {
                isDimmed = !isDimmed
                dimVeil.visibility = if (isDimmed) View.VISIBLE else View.GONE
            })
            addView(railItem("Settings") { showTab("SETTINGS") })
        }
    }

    private fun railItem(title: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#E6ECF5"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(16), dp(8), dp(16))
            setOnClickListener { onClick() }
        }
    }

    private fun buildHomePage(): ScrollView {
        return ScrollView(this).apply {
            setPadding(dp(28), dp(22), dp(28), dp(22))
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            val header = TextView(this@MainActivity).apply {
                text = "7xTubeDash · Zeekr 7X Media & HUD Suite"
                setTextColor(Color.WHITE)
                textSize = 22f
                setPadding(0, 0, 0, dp(4))
            }
            val subHeader = TextView(this@MainActivity).apply {
                text = "Snapdragon 8295 · D-Gear Bypass · AR-HUD & Center Floating Player"
                setTextColor(Color.parseColor("#8A94A6"))
                textSize = 13f
                setPadding(0, 0, 0, dp(18))
            }
            col.addView(header)
            col.addView(subHeader)

            // Row 1 Bento Cards
            val row1 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            // Card 1: Driving Output Target
            val modeCard = createBentoCard(
                title = "Driving Video Target",
                subtitle = modeLabel(Prefs.getOutputMode(this@MainActivity)),
                accentColor = "#FF6B2C",
                onTap = {
                    val next = (Prefs.getOutputMode(this@MainActivity) + 1) % 3
                    Prefs.setOutputMode(this@MainActivity, next)
                    Prefs.setLastUsedDrivingMode(this@MainActivity, next)
                    modeCardSub.text = modeLabel(next)
                },
                onHold = { showTab("SETTINGS") }
            )
            modeCardSub = modeCard.second
            row1.addView(modeCard.first, LinearLayout.LayoutParams(0, dp(145), 1f).apply { rightMargin = dp(16) })

            // Card 2: Launch Active Driving Mode Now
            val launchCard = createBentoCard(
                title = "Start Driving Window (D Bypass)",
                subtitle = "Launches current target over Zeekr Navigation with steering wheel media controls",
                accentColor = "#2E8BFF",
                onTap = { launchDrivingOverlayOrHud(Prefs.getOutputMode(this@MainActivity)) },
                onHold = { showTab("SETTINGS") }
            )
            row1.addView(launchCard.first, LinearLayout.LayoutParams(0, dp(145), 1f))
            col.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })

            // Row 2 Bento Cards
            val row2 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            // Card 3: D-Gear Resume State
            val pipCard = createBentoCard(
                title = "D-Gear Shift Behavior",
                subtitle = "Auto-Resumes: ${modeLabel(Prefs.getLastUsedDrivingMode(this@MainActivity))}\n(Remembers last used mode when shifting to D)",
                accentColor = "#10B981",
                onTap = {
                    val next = (Prefs.getLastUsedDrivingMode(this@MainActivity) + 1) % 3
                    Prefs.setLastUsedDrivingMode(this@MainActivity, next)
                    pipCardSub.text = "Auto-Resumes: ${modeLabel(next)}\n(Remembers last used mode when shifting to D)"
                },
                onHold = { enterCarPipMode() }
            )
            pipCardSub = pipCard.second
            row2.addView(pipCard.first, LinearLayout.LayoutParams(0, dp(145), 1f).apply { rightMargin = dp(16) })

            // Card 4: AR-HUD Display Status
            val hudCard = createBentoCard(
                title = "AR-HUD Status & Display Probe",
                subtitle = detectDisplaysSummary(),
                accentColor = "#A855F7",
                onTap = {
                    Prefs.setOutputMode(this@MainActivity, 1)
                    modeCardSub.text = modeLabel(1)
                    launchDrivingOverlayOrHud(1)
                },
                onHold = { showTab("SETTINGS") }
            )
            hudInfoSub = hudCard.second
            row2.addView(hudCard.first, LinearLayout.LayoutParams(0, dp(145), 1f))
            col.addView(row2)

            addView(col)
        }
    }

    private fun buildSettingsPage(): ScrollView {
        return ScrollView(this).apply {
            setPadding(dp(28), dp(22), dp(28), dp(22))
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            val title = TextView(this@MainActivity).apply {
                text = "HUD & Overlay Calibration (7xDash / CAMCORDER)"
                setTextColor(Color.WHITE)
                textSize = 20f
                setPadding(0, 0, 0, dp(14))
            }
            col.addView(title)

            // Detected Displays & Test Pattern section
            val dm = getSystemService(DisplayManager::class.java)
            val displays = dm.displays

            val displayTitle = TextView(this@MainActivity).apply {
                text = "AR-HUD Target Display (Active: Display ${Prefs.getHudDisplayId(this@MainActivity)})"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 15f
                setPadding(0, 0, 0, dp(8))
            }
            col.addView(displayTitle)

            val displayRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(16))
            }

            displays.forEach { disp ->
                val id = disp.displayId
                val container = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, dp(12), 0)
                }

                val selectBtn = Button(this@MainActivity).apply {
                    text = "Use Display $id (${disp.name})"
                    setOnClickListener {
                        Prefs.setHudDisplayId(this@MainActivity, id)
                        displayTitle.text = "AR-HUD Target Display (Active: Display $id)"
                        Toast.makeText(this@MainActivity, "AR-HUD bound to Display $id", Toast.LENGTH_SHORT).show()
                    }
                }
                val testBtn = Button(this@MainActivity).apply {
                    text = "Test Pattern $id"
                    setBackgroundColor(Color.parseColor("#334155"))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        sendTestPattern(id)
                    }
                }
                container.addView(selectBtn)
                container.addView(testBtn)
                displayRow.addView(container)
            }
            col.addView(displayRow)

            // Safe-Zone Presets
            val presetLabel = TextView(this@MainActivity).apply {
                text = "Quick AR-HUD Safe-Zone Presets (Avoid telemetry overlap)"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 14f
                setPadding(0, 0, 0, dp(6))
            }
            col.addView(presetLabel)

            val presetRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(16))
            }

            listOf("Corner", "Banner", "Wide").forEach { name ->
                val btn = Button(this@MainActivity).apply {
                    text = "$name Preset"
                    setOnClickListener {
                        Prefs.applyHudPreset(this@MainActivity, name)
                        Toast.makeText(this@MainActivity, "Applied $name Preset", Toast.LENGTH_SHORT).show()
                        showTab("SETTINGS") // refresh sliders
                    }
                }
                presetRow.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
            }
            col.addView(presetRow)

            // HUD Size Slider
            col.addView(createSliderControl(
                label = "AR-HUD Picture Scale (%)",
                initial = Prefs.getHudScale(this@MainActivity),
                max = 80,
                onChanged = { Prefs.setHudScale(this@MainActivity, it.coerceAtLeast(20)) }
            ))

            // HUD Horizontal Offset Slider
            col.addView(createSliderControl(
                label = "AR-HUD Left / Right Offset (px)",
                initial = Prefs.getHudOffsetX(this@MainActivity),
                max = 600,
                onChanged = { Prefs.setHudOffsetX(this@MainActivity, it) }
            ))

            // HUD Vertical Offset Slider
            col.addView(createSliderControl(
                label = "AR-HUD Up / Down Offset (px)",
                initial = Prefs.getHudOffsetY(this@MainActivity),
                max = 400,
                onChanged = { Prefs.setHudOffsetY(this@MainActivity, it) }
            ))

            // Night Dimmer Opacity Slider
            col.addView(createSliderControl(
                label = "Night Dim Veil Darkness",
                initial = dimAlpha,
                max = 220,
                onChanged = {
                    dimAlpha = it
                    dimVeil.setBackgroundColor(Color.argb(dimAlpha, 12, 9, 5))
                }
            ))

            addView(col)
        }
    }

    private fun createSliderControl(
        label: String,
        initial: Int,
        max: Int,
        onChanged: (Int) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(14))
            val txt = TextView(this@MainActivity).apply {
                text = "$label: $initial"
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            val seek = SeekBar(this@MainActivity).apply {
                this.max = max
                progress = initial
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                        txt.text = "$label: $value"
                        onChanged(value)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            addView(txt)
            addView(seek)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebViewPlayer(): FrameLayout {
        val rootContainer = FrameLayout(this)

        // Webview
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) {
                    Prefs.setLastUrl(this@MainActivity, url)
                    urlEditText.setText(url)
                }
                injectAdSkipScript(webView)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(Prefs.getLastUrl(this))
        rootContainer.addView(webView)

        // Top URL and Bookmarks Bar (collapsible)
        urlBarContainer = buildUrlBar()
        rootContainer.addView(urlBarContainer)

        return rootContainer
    }

    private fun buildUrlBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(220, 18, 22, 31))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }

            // Input Row
            val inputRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            urlEditText = EditText(this@MainActivity).apply {
                setText(Prefs.getLastUrl(this@MainActivity))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                hint = "Enter video URL (e.g., https://...)"
                textSize = 13f
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            inputRow.addView(urlEditText)

            val goBtn = Button(this@MainActivity).apply {
                text = "Go"
                setOnClickListener {
                    var target = urlEditText.text.toString().trim()
                    if (!target.startsWith("http://") && !target.startsWith("https://")) {
                        target = "https://$target"
                    }
                    webView.loadUrl(target)
                    urlBarContainer.visibility = View.GONE
                }
            }
            inputRow.addView(goBtn)

            val closeBtn = Button(this@MainActivity).apply {
                text = "✕"
                setOnClickListener { urlBarContainer.visibility = View.GONE }
            }
            inputRow.addView(closeBtn)
            addView(inputRow)

            // Bookmarks / Quick Switcher Row
            val bookmarkRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, 0)
            }

            val presets = listOf(
                "YouTube" to "https://www.youtube.com",
                "Bilibili" to "https://www.bilibili.com",
                "Netflix" to "https://www.netflix.com",
                "Disney+" to "https://www.disneyplus.com",
                "Twitch" to "https://www.twitch.tv"
            )

            presets.forEach { (name, link) ->
                val chip = TextView(this@MainActivity).apply {
                    text = name
                    setTextColor(Color.parseColor("#93C5FD"))
                    textSize = 12f
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        urlEditText.setText(link)
                        webView.loadUrl(link)
                        urlBarContainer.visibility = View.GONE
                    }
                }
                bookmarkRow.addView(chip)
            }
            addView(bookmarkRow)
        }
    }

    private fun sendTestPattern(displayId: Int) {
        val intent = Intent(this, OverlayHudService::class.java).apply {
            action = "ACTION_TEST_PATTERN"
            putExtra("DISPLAY_ID", displayId)
        }
        startForegroundService(intent)
        Toast.makeText(this, "Broadcasting 6-second Test Pattern to Display $displayId", Toast.LENGTH_LONG).show()
    }

    private fun launchDrivingOverlayOrHud(targetMode: Int) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Appear on top' permission for 7xTubeDash", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        val currentUrl = webView.url ?: Prefs.getLastUrl(this)
        Prefs.setLastUrl(this, currentUrl)
        Prefs.setLastUsedDrivingMode(this, targetMode)

        webView.onPause()
        val svc = Intent(this, OverlayHudService::class.java).apply {
            putExtra("VIDEO_URL", currentUrl)
            putExtra("OUTPUT_MODE", targetMode)
        }
        startForegroundService(svc)
        moveTaskToBack(true)
    }

    private fun enterCarPipMode() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        showTab("PLAYER")
        leftRail.visibility = View.GONE
        enterPictureInPictureMode(params)
    }

    // Triggered automatically when Zeekr OS backgrounds the app on shifting to D gear
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val rememberedMode = Prefs.getLastUsedDrivingMode(this)
        if (Settings.canDrawOverlays(this)) {
            launchDrivingOverlayOrHud(rememberedMode)
        } else if (Prefs.isAutoPip(this)) {
            enterCarPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            leftRail.visibility = View.GONE
            homePage.visibility = View.GONE
            settingsPage.visibility = View.GONE
            urlBarContainer.visibility = View.GONE
            webViewContainer.visibility = View.VISIBLE
        } else {
            leftRail.visibility = View.VISIBLE
        }
    }

    private fun createBentoCard(
        title: String,
        subtitle: String,
        accentColor: String,
        onTap: () -> Unit,
        onHold: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#161B26"))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.parseColor(accentColor))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setOnClickListener { onTap() }
            setOnLongClickListener {
                onHold()
                true
            }
        }

        val t = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        val s = TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(t)
        card.addView(s)
        return Pair(card, s)
    }

    private fun showTab(tab: String) {
        homePage.visibility = if (tab == "HOME") View.VISIBLE else View.GONE
        settingsPage.visibility = if (tab == "SETTINGS") View.VISIBLE else View.GONE
        webViewContainer.visibility = if (tab == "PLAYER") View.VISIBLE else View.GONE
    }

    private fun modeLabel(mode: Int): String = when (mode) {
        0 -> "Center Screen Floating Window (Drag, Pinch, Edge-Dock)"
        1 -> "Windshield AR-HUD Projection Only"
        else -> "Dual Mode (Center Pop-Up + Windshield AR-HUD)"
    }

    private fun detectDisplaysSummary(): String {
        val dm = getSystemService(DisplayManager::class.java)
        val list = dm.displays.joinToString(", ") { "ID ${it.displayId} (${it.name})" }
        return "Detected Cockpit Displays: $list"
    }

    private fun injectAdSkipScript(wv: WebView) {
        val js = """
            (function() {
                if (window.zeekrAdSkip) return;
                window.zeekrAdSkip = true;
                setInterval(() => {
                    const skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                    if (skip) skip.click();
                    const video = document.querySelector('video');
                    if (document.querySelector('.ad-showing') && video && !isNaN(video.duration)) {
                        video.currentTime = video.duration;
                    }
                }, 500);
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    private fun setupCarAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Respect user decision: NO DUCKING for navigation announcements; full constant volume!
        val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Intentionally ignore ducking requests from Zeekr navigation to keep full volume
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Transient loss (e.g., incoming phone call)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Resume normal volume
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // Permanent loss
                }
            }
        }

        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ZeekrMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

            val state = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
            setPlaybackState(state)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    webView.evaluateJavascript("document.querySelector('video')?.play()", null)
                }

                override fun onPause() {
                    webView.evaluateJavascript("document.querySelector('video')?.pause()", null)
                }

                override fun onFastForward() {
                    skipVideoSeconds(10)
                }

                override fun onRewind() {
                    skipVideoSeconds(-10)
                }

                override fun onSkipToNext() {
                    // Steering wheel scroll up / next button skips +10s
                    skipVideoSeconds(10)
                }

                override fun onSkipToPrevious() {
                    // Steering wheel scroll down / prev button skips -10s
                    skipVideoSeconds(-10)
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                skipVideoSeconds(10)
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                skipVideoSeconds(-10)
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                                togglePlayPause()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            isActive = true
        }
    }

    private fun skipVideoSeconds(seconds: Int) {
        val js = """
            (function() {
                const v = document.querySelector('video');
                if (v) {
                    v.currentTime = Math.max(0, v.currentTime + ($seconds));
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun togglePlayPause() {
        val js = """
            (function() {
                const v = document.querySelector('video');
                if (v) {
                    if (v.paused) v.play(); else v.pause();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onDestroy() {
        mediaSession?.release()
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        super.onDestroy()
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
