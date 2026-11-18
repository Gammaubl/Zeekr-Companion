package com.zeekr7x.tubedash

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), LocationListener {

    private val TAG = "Zeekr7xTubeDash"

    private lateinit var leftRail: LinearLayout
    private lateinit var homePage: ScrollView
    private lateinit var settingsPage: ScrollView
    private lateinit var webViewContainer: FrameLayout
    private var webView: WebView? = null
    private lateinit var mapsContainer: FrameLayout
    private var mapsWebView: WebView? = null
    private lateinit var urlBarContainer: LinearLayout
    private lateinit var urlEditText: EditText
    private lateinit var dimVeil: View

    private lateinit var modeCardSub: TextView
    private lateinit var pipCardSub: TextView
    private lateinit var hudInfoSub: TextView
    private lateinit var mapsTargetSub: TextView
    private var isDimmed = false
    private var dimAlpha = 135

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null
    private var locationManager: LocationManager? = null

    private var currentNavTelemetry = NavTelemetry()
    private var isPlayerInitialized = false
    private var isMapsInitialized = false

    companion object {
        private const val PERMISSION_REQ_LOCATION = 1001
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val MOBILE_MAPS_UA =
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety Crash Shield: Prevent any unhandled crash from freezing Zeekr App Lab's loading screen
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
        }

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemUI()

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

            // 1. Persistent Left Rail
            leftRail = buildLeftRail()
            mainHorizontal.addView(leftRail)

            // 2. Content Stack
            val contentStack = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }

            homePage = buildHomePage()
            settingsPage = buildSettingsPage()
            webViewContainer = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            mapsContainer = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#1A1D24"))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            contentStack.addView(webViewContainer)
            contentStack.addView(mapsContainer)
            contentStack.addView(homePage)
            contentStack.addView(settingsPage)

            showTab("HOME")
            mainHorizontal.addView(contentStack)
            root.addView(mainHorizontal)

            // 3. Night Dim Overlay Veil
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

            // CRITICAL: Call setContentView immediately so first frame draws in <50ms
            // and Zeekr App Lab launcher dismisses its 'Loading...' screen right away!
            setContentView(root)

            // Lazy initialize heavier components after first frame is drawn
            Handler(Looper.getMainLooper()).postDelayed({
                initHeavyComponents()
            }, 100L)

        } catch (t: Throwable) {
            Log.e(TAG, "Error in onCreate", t)
            showEmergencyErrorView(t)
        }
    }

    private fun initHeavyComponents() {
        runCatching { setupCarAudioFocus() }
        runCatching { setupMediaSession() }
        runCatching { requestLocationPermissions() }
        runCatching { initPlayerWebView() }
        runCatching { initMapsWebView() }
    }

    private fun showEmergencyErrorView(t: Throwable) {
        val errView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#180A0A"))
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "7xTubeDash Safe Mode"
            textSize = 22f
            setTextColor(Color.parseColor("#EF4444"))
        }
        val msg = TextView(this).apply {
            text = "Startup Notice:\n${t.localizedMessage ?: t.javaClass.simpleName}"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
        }
        val retryBtn = Button(this).apply {
            text = "Enter Home Dashboard"
            setOnClickListener { recreate() }
        }
        errView.addView(title)
        errView.addView(msg)
        errView.addView(retryBtn)
        setContentView(errView)
    }

    private fun buildLeftRail(): LinearLayout {
        val widthPx = dp(96)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#12161F"))
            setPadding(0, dp(12), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT)

            addView(railItem("Home") { showTab("HOME") })
            addView(railItem("Maps") {
                showTab("MAPS")
                Prefs.setCenterSource(this@MainActivity, "MAPS")
                if (!isMapsInitialized) initMapsWebView()
            })
            addView(railItem("YouTube") {
                showTab("PLAYER")
                Prefs.setCenterSource(this@MainActivity, "YOUTUBE")
                if (!isPlayerInitialized) initPlayerWebView()
            })
            addView(railItem("Browse") {
                showTab("PLAYER")
                if (!isPlayerInitialized) initPlayerWebView()
                if (::urlBarContainer.isInitialized) {
                    urlBarContainer.visibility =
                        if (urlBarContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
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
            addView(railItem("Sat/Def") {
                toggleMapLayer()
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
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(12), dp(6), dp(12))
            setOnClickListener { onClick() }
        }
    }

    private fun buildHomePage(): ScrollView {
        return ScrollView(this).apply {
            setPadding(dp(26), dp(18), dp(26), dp(18))
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            val header = TextView(this@MainActivity).apply {
                text = "7xTubeDash 2.0 · Zeekr 7X Suite"
                setTextColor(Color.WHITE)
                textSize = 21f
                setPadding(0, 0, 0, dp(4))
            }
            val subHeader = TextView(this@MainActivity).apply {
                text = "Snapdragon 8295 · Google Maps Windshield AR-HUD & Center Floating Player"
                setTextColor(Color.parseColor("#8A94A6"))
                textSize = 13f
                setPadding(0, 0, 0, dp(16))
            }
            col.addView(header)
            col.addView(subHeader)

            // Row 1 Bento Cards
            val row1 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            // Card 1: Driving Output Target
            val modeCard = createBentoCard(
                title = "Driving Output Mode",
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
            row1.addView(modeCard.first, LinearLayout.LayoutParams(0, dp(145), 1f).apply { rightMargin = dp(14) })

            // Card 2: Independent Display Targets
            val targetCard = createBentoCard(
                title = "Display Content Routing",
                subtitle = "Center: ${Prefs.getCenterSource(this@MainActivity)} · AR-HUD: ${Prefs.getHudSource(this@MainActivity)}\n(Tap to switch: Maps on HUD + YouTube on Center)",
                accentColor = "#10B981",
                onTap = {
                    val currentHud = Prefs.getHudSource(this@MainActivity)
                    val newHud = if (currentHud == "MAPS") "YOUTUBE" else "MAPS"
                    val newCenter = if (newHud == "MAPS") "YOUTUBE" else "MAPS"
                    Prefs.setHudSource(this@MainActivity, newHud)
                    Prefs.setCenterSource(this@MainActivity, newCenter)
                    mapsTargetSub.text = "Center: $newCenter · AR-HUD: $newHud\n(Tap to switch: Maps on HUD + YouTube on Center)"
                    Toast.makeText(this@MainActivity, "Target: Center=$newCenter, AR-HUD=$newHud", Toast.LENGTH_SHORT).show()
                },
                onHold = { showTab("SETTINGS") }
            )
            mapsTargetSub = targetCard.second
            row1.addView(targetCard.first, LinearLayout.LayoutParams(0, dp(145), 1f))
            col.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })

            // Row 2 Bento Cards
            val row2 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            // Card 3: Launch Active Driving Mode Now
            val launchCard = createBentoCard(
                title = "Launch Driving Pop-Up / HUD",
                subtitle = "Starts floating player & windshield HUD projection over Zeekr Navigation",
                accentColor = "#2E8BFF",
                onTap = { launchDrivingOverlayOrHud(Prefs.getOutputMode(this@MainActivity)) },
                onHold = { showTab("SETTINGS") }
            )
            row2.addView(launchCard.first, LinearLayout.LayoutParams(0, dp(145), 1f).apply { rightMargin = dp(14) })

            // Card 4: AR-HUD Display Status & Telemetry
            val hudCard = createBentoCard(
                title = "AR-HUD Map Status",
                subtitle = "${detectDisplaysSummary()}\nLayer: ${Prefs.getMapLayer(this@MainActivity)} · HUD Card: Enabled",
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
            setPadding(dp(26), dp(18), dp(26), dp(18))
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            val title = TextView(this@MainActivity).apply {
                text = "HUD & Google Maps Calibration Suite"
                setTextColor(Color.WHITE)
                textSize = 20f
                setPadding(0, 0, 0, dp(12))
            }
            col.addView(title)

            val displayTitle = TextView(this@MainActivity).apply {
                text = "AR-HUD Target Display ID (Active: Display ${Prefs.getHudDisplayId(this@MainActivity)})"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 15f
                setPadding(0, 0, 0, dp(6))
            }
            col.addView(displayTitle)

            val displayRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(14))
            }

            // Safe display enumeration
            val displayList = runCatching {
                getSystemService(DisplayManager::class.java)?.displays?.toList()
            }.getOrNull() ?: emptyList()

            val idsToDisplay = if (displayList.isNotEmpty()) {
                displayList.map { it.displayId to it.name }
            } else {
                listOf(1 to "AR-HUD", 2 to "HUD/Cluster", 3 to "Aux")
            }

            idsToDisplay.forEach { (id, name) ->
                val container = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, dp(12), 0)
                }

                val selectBtn = Button(this@MainActivity).apply {
                    text = "Display $id ($name)"
                    setOnClickListener {
                        Prefs.setHudDisplayId(this@MainActivity, id)
                        displayTitle.text = "AR-HUD Target Display ID (Active: Display $id)"
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
                text = "AR-HUD Safe-Zone Presets (Avoid telemetry overlap)"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 14f
                setPadding(0, 0, 0, dp(6))
            }
            col.addView(presetLabel)

            val presetRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(14))
            }

            listOf("Corner", "Banner", "Wide").forEach { name ->
                val btn = Button(this@MainActivity).apply {
                    text = "$name Preset"
                    setOnClickListener {
                        Prefs.applyHudPreset(this@MainActivity, name)
                        Toast.makeText(this@MainActivity, "Applied $name Preset", Toast.LENGTH_SHORT).show()
                        showTab("SETTINGS")
                    }
                }
                presetRow.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
            }
            col.addView(presetRow)

            // Sliders
            col.addView(createSliderControl(
                label = "AR-HUD Picture Scale (%)",
                initial = Prefs.getHudScale(this@MainActivity),
                max = 85,
                onChanged = { Prefs.setHudScale(this@MainActivity, it.coerceAtLeast(20)) }
            ))

            col.addView(createSliderControl(
                label = "AR-HUD Left / Right Offset (px)",
                initial = Prefs.getHudOffsetX(this@MainActivity),
                max = 600,
                onChanged = { Prefs.setHudOffsetX(this@MainActivity, it) }
            ))

            col.addView(createSliderControl(
                label = "AR-HUD Up / Down Offset (px)",
                initial = Prefs.getHudOffsetY(this@MainActivity),
                max = 400,
                onChanged = { Prefs.setHudOffsetY(this@MainActivity, it) }
            ))

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
            setPadding(0, dp(4), 0, dp(12))
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
    private fun initPlayerWebView() {
        if (isPlayerInitialized) return
        isPlayerInitialized = true

        runCatching {
            val wv = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
            }

            runCatching {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            }

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = DESKTOP_UA
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) {
                        Prefs.setLastUrl(this@MainActivity, url)
                        if (::urlEditText.isInitialized) urlEditText.setText(url)
                    }
                    injectAdSkipScript(wv)
                }
            }
            wv.webChromeClient = WebChromeClient()
            wv.loadUrl(Prefs.getLastUrl(this))
            webView = wv
            webViewContainer.addView(wv)

            urlBarContainer = buildUrlBar()
            webViewContainer.addView(urlBarContainer)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initMapsWebView() {
        if (isMapsInitialized) return
        isMapsInitialized = true

        runCatching {
            val wv = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#1A1D24"))
            }

            runCatching {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            }

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setGeolocationEnabled(true)
                mediaPlaybackRequiresUserGesture = false
                userAgentString = MOBILE_MAPS_UA
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            wv.webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }

            val bridge = MapsTelemetryBridge { telemetry ->
                runOnUiThread { currentNavTelemetry = telemetry }
            }
            wv.addJavascriptInterface(bridge, "ZeekrNavBridge")

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) Prefs.setLastMapUrl(this@MainActivity, url)
                    wv.evaluateJavascript(MapsTelemetryBridge.JS_INJECT_MAPS, null)
                }
            }

            wv.loadUrl(Prefs.getLastMapUrl(this))
            mapsWebView = wv
            mapsContainer.addView(wv)

            // Maps Floating Controls
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.argb(200, 18, 22, 31))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    rightMargin = dp(16)
                    bottomMargin = dp(16)
                }
            }

            controls.addView(mapBtn("Satellite") { toggleMapLayer() })
            controls.addView(mapBtn("Zoom +") { mapsWebView?.evaluateJavascript(MapsTelemetryBridge.JS_ZOOM_IN, null) })
            controls.addView(mapBtn("Zoom -") { mapsWebView?.evaluateJavascript(MapsTelemetryBridge.JS_ZOOM_OUT, null) })
            controls.addView(mapBtn("Recenter") { mapsWebView?.evaluateJavascript(MapsTelemetryBridge.JS_RECENTER, null) })
            controls.addView(mapBtn("Push to HUD") {
                val mapUrl = mapsWebView?.url ?: Prefs.getLastMapUrl(this)
                Prefs.setLastMapUrl(this, mapUrl)
                Prefs.setHudSource(this, "MAPS")
                Toast.makeText(this, "Pushed current Google Map to AR-HUD", Toast.LENGTH_SHORT).show()
            })

            mapsContainer.addView(controls)
        }
    }

    private fun mapBtn(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
        }
    }

    private fun toggleMapLayer() {
        val current = Prefs.getMapLayer(this)
        val next = if (current == "DEFAULT") "SATELLITE" else "DEFAULT"
        Prefs.setMapLayer(this, next)
        val script = if (next == "SATELLITE") MapsTelemetryBridge.JS_TOGGLE_SATELLITE else MapsTelemetryBridge.JS_TOGGLE_DEFAULT
        mapsWebView?.evaluateJavascript(script, null)
        Toast.makeText(this, "Switched Map Layer to: $next", Toast.LENGTH_SHORT).show()
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

            val inputRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            urlEditText = EditText(this@MainActivity).apply {
                setText(Prefs.getLastUrl(this@MainActivity))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                hint = "Enter URL (e.g., https://...)"
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
                    webView?.loadUrl(target)
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

            val bookmarkRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, 0)
            }

            val presets = listOf(
                "YouTube" to "https://www.youtube.com",
                "Google Maps" to "https://www.google.com/maps",
                "Bilibili" to "https://www.bilibili.com",
                "Netflix" to "https://www.netflix.com",
                "Disney+" to "https://www.disneyplus.com"
            )

            presets.forEach { (name, link) ->
                val chip = TextView(this@MainActivity).apply {
                    text = name
                    setTextColor(Color.parseColor("#93C5FD"))
                    textSize = 12f
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        urlEditText.setText(link)
                        if (name == "Google Maps") {
                            showTab("MAPS")
                            if (!isMapsInitialized) initMapsWebView()
                            mapsWebView?.loadUrl(link)
                        } else {
                            showTab("PLAYER")
                            if (!isPlayerInitialized) initPlayerWebView()
                            webView?.loadUrl(link)
                        }
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
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
            return
        }

        val videoUrl = webView?.url ?: Prefs.getLastUrl(this)
        val mapsUrl = mapsWebView?.url ?: Prefs.getLastMapUrl(this)
        Prefs.setLastUrl(this, videoUrl)
        Prefs.setLastMapUrl(this, mapsUrl)
        Prefs.setLastUsedDrivingMode(this, targetMode)

        webView?.onPause()
        val svc = Intent(this, OverlayHudService::class.java).apply {
            putExtra("VIDEO_URL", videoUrl)
            putExtra("MAPS_URL", mapsUrl)
            putExtra("OUTPUT_MODE", targetMode)
            putExtra("CENTER_SOURCE", Prefs.getCenterSource(this@MainActivity))
            putExtra("HUD_SOURCE", Prefs.getHudSource(this@MainActivity))
            putExtra("MAP_LAYER", Prefs.getMapLayer(this@MainActivity))
        }
        startForegroundService(svc)
        moveTaskToBack(true)
    }

    private fun enterCarPipMode() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        val activeSource = Prefs.getCenterSource(this)
        if (activeSource == "MAPS") {
            showTab("MAPS")
        } else {
            showTab("PLAYER")
        }
        leftRail.visibility = View.GONE
        enterPictureInPictureMode(params)
    }

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
            if (::urlBarContainer.isInitialized) urlBarContainer.visibility = View.GONE
            val activeSource = Prefs.getCenterSource(this)
            webViewContainer.visibility = if (activeSource == "YOUTUBE") View.VISIBLE else View.GONE
            mapsContainer.visibility = if (activeSource == "MAPS") View.VISIBLE else View.GONE
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
        mapsContainer.visibility = if (tab == "MAPS") View.VISIBLE else View.GONE
    }

    private fun modeLabel(mode: Int): String = when (mode) {
        0 -> "Center Screen Floating Window Only"
        1 -> "Windshield AR-HUD Projection Only"
        else -> "Dual Mode (Center Pop-Up + AR-HUD)"
    }

    private fun detectDisplaysSummary(): String {
        return runCatching {
            val dm = getSystemService(DisplayManager::class.java)
            val list = dm?.displays?.joinToString(", ") { "ID ${it.displayId} (${it.name})" }
            "Cockpit Displays: $list"
        }.getOrDefault("Cockpit Displays: Default 0, AR-HUD 1/2")
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
        runCatching {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { /* Keep full volume; do not duck for nav announcements */ }
                .build()

            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        }
    }

    private fun setupMediaSession() {
        runCatching {
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
                        webView?.evaluateJavascript("document.querySelector('video')?.play()", null)
                    }

                    override fun onPause() {
                        webView?.evaluateJavascript("document.querySelector('video')?.pause()", null)
                    }

                    override fun onFastForward() { skipVideoSeconds(10) }
                    override fun onRewind() { skipVideoSeconds(-10) }
                    override fun onSkipToNext() { skipVideoSeconds(10) }
                    override fun onSkipToPrevious() { skipVideoSeconds(-10) }

                    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                        @Suppress("DEPRECATION")
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
    }

    private fun skipVideoSeconds(seconds: Int) {
        val js = """
            (function() {
                const v = document.querySelector('video');
                if (v) { v.currentTime = Math.max(0, v.currentTime + ($seconds)); }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    private fun togglePlayPause() {
        val js = """
            (function() {
                const v = document.querySelector('video');
                if (v) { if (v.paused) v.play(); else v.pause(); }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    private fun requestLocationPermissions() {
        runCatching {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine) {
                // Safeguard against missing permission controller activity on Zeekr OS
                runCatching {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        PERMISSION_REQ_LOCATION
                    )
                }.onFailure {
                    Log.w(TAG, "Runtime permission request failed on Zeekr OS: ${it.message}")
                }
            } else {
                startLocationUpdates()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        runCatching {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        // High-precision Snapdragon 8295 GNSS update
    }

    override fun onDestroy() {
        runCatching { locationManager?.removeUpdates(this) }
        runCatching { mediaSession?.release() }
        runCatching { audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) } }
        super.onDestroy()
    }

    private fun hideSystemUI() {
        runCatching {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
