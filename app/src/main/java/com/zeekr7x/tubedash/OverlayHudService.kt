package com.zeekr7x.tubedash

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class OverlayHudService : Service(), LocationListener {

    private lateinit var centerWm: WindowManager
    private var centerContainer: FrameLayout? = null
    private var centerWebView: WebView? = null
    private var centerMapsWebView: WebView? = null

    private var hudWm: WindowManager? = null
    private var hudContainer: FrameLayout? = null
    private var hudWebView: WebView? = null
    private var hudPresentation: Presentation? = null

    // Top-Left HUD Telemetry Card views
    private var hudTelemetryCard: LinearLayout? = null
    private var hudManeuverIcon: TextView? = null
    private var hudManeuverDistance: TextView? = null
    private var hudStreetName: TextView? = null
    private var hudTripSummary: TextView? = null

    private var testPatternPresentation: Presentation? = null
    private var isDocked = false
    private var isLarge = false
    private var savedX = 80

    private var centerSource = "YOUTUBE"
    private var hudSource = "MAPS"
    private var mapLayer = "DEFAULT"

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null
    private var locationManager: LocationManager? = null

    private var currentNav = NavTelemetry()
    private var currentSpeedKmh = 0

    companion object {
        const val ACTION_TEST_PATTERN = "ACTION_TEST_PATTERN"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val MOBILE_MAPS_UA =
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        setupCarAudioFocus()
        setupMediaSession()
        startLocationUpdates()

        if (intent?.action == ACTION_TEST_PATTERN) {
            val displayId = intent.getIntExtra("DISPLAY_ID", 1)
            showTestPatternOnDisplay(displayId)
            return START_STICKY
        }

        val videoUrl = intent?.getStringExtra("VIDEO_URL") ?: Prefs.getLastUrl(this)
        val mapsUrl = intent?.getStringExtra("MAPS_URL") ?: Prefs.getLastMapUrl(this)
        val mode = intent?.getIntExtra("OUTPUT_MODE", Prefs.getLastUsedDrivingMode(this)) ?: 2
        centerSource = intent?.getStringExtra("CENTER_SOURCE") ?: Prefs.getCenterSource(this)
        hudSource = intent?.getStringExtra("HUD_SOURCE") ?: Prefs.getHudSource(this)
        mapLayer = intent?.getStringExtra("MAP_LAYER") ?: Prefs.getMapLayer(this)

        cleanupWindows()

        if (mode == 0 || mode == 2) {
            showCenterFloatingPopUp(videoUrl, mapsUrl)
        }
        if (mode == 1 || mode == 2) {
            showArHudProjection(videoUrl, mapsUrl)
        }

        return START_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun showCenterFloatingPopUp(videoUrl: String, mapsUrl: String) {
        centerWm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val startW = (540 * density).toInt()
        val startH = (330 * density).toInt()

        val params = WindowManager.LayoutParams(
            startW,
            startH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 80
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#141821"))
            setPadding(4, 4, 4, 4)
        }

        val wvVideo = createConfiguredVideoWebView(videoUrl)
        val wvMaps = createConfiguredMapsWebView(mapsUrl, isHud = false)
        centerWebView = wvVideo
        centerMapsWebView = wvMaps

        root.addView(wvVideo)
        root.addView(wvMaps)
        updateCenterSourceVisibility()

        // Top Control Strip: Dock, Source, Layer, -10s, +10s, HUD, Full, Close
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(220, 18, 22, 31))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (38 * density).toInt()
            )
        }

        topBar.addView(barButton("Dock") {
            if (!isDocked) {
                savedX = params.x
                params.x = screenWidth - (36 * density).toInt()
                isDocked = true
            } else {
                params.x = savedX
                isDocked = false
            }
            centerWm.updateViewLayout(root, params)
        })

        // 1-Tap Source Switcher on Floating Window (Tube ↔ Maps)
        topBar.addView(barButton(if (centerSource == "MAPS") "To Tube" else "To Maps") {
            centerSource = if (centerSource == "MAPS") "YOUTUBE" else "MAPS"
            Prefs.setCenterSource(this, centerSource)
            updateCenterSourceVisibility()
            (it as TextView).text = if (centerSource == "MAPS") "To Tube" else "To Maps"
        })

        topBar.addView(barButton("Layer") {
            mapLayer = if (mapLayer == "DEFAULT") "SATELLITE" else "DEFAULT"
            Prefs.setMapLayer(this, mapLayer)
            val script = if (mapLayer == "SATELLITE") MapsTelemetryBridge.JS_TOGGLE_SATELLITE else MapsTelemetryBridge.JS_TOGGLE_DEFAULT
            centerMapsWebView?.evaluateJavascript(script, null)
            hudWebView?.evaluateJavascript(script, null)
            Toast.makeText(this, "Layer: $mapLayer", Toast.LENGTH_SHORT).show()
        })

        topBar.addView(barButton("-10s") { skipVideoSeconds(-10) })
        topBar.addView(barButton("+10s") { skipVideoSeconds(10) })

        topBar.addView(barButton("HUD") {
            if (hudContainer == null && hudPresentation == null) {
                showArHudProjection(videoUrl, mapsUrl)
            } else {
                removeHudProjection()
            }
        })

        topBar.addView(barButton("Full") {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            stopSelf()
        })

        topBar.addView(barButton("✕") { stopSelf() })

        // Pinch-to-Resize detector
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                params.width = (params.width * factor).toInt().coerceIn((280 * density).toInt(), (1150 * density).toInt())
                params.height = (params.height * factor).toInt().coerceIn((180 * density).toInt(), (720 * density).toInt())
                centerWm.updateViewLayout(root, params)
                return true
            }
        })

        // Double-tap toggle Small/Large
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                isLarge = !isLarge
                params.width = ((if (isLarge) 840 else 540) * density).toInt()
                params.height = ((if (isLarge) 500 else 330) * density).toInt()
                centerWm.updateViewLayout(root, params)
                return true
            }
        })

        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f

        topBar.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isDocked) {
                        isDocked = false
                        params.x = savedX
                        centerWm.updateViewLayout(root, params)
                    }
                    initX = params.x
                    initY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        params.x = initX + (event.rawX - touchX).toInt()
                        params.y = initY + (event.rawY - touchY).toInt()
                        centerWm.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (params.x > screenWidth - (140 * density).toInt()) {
                        savedX = (screenWidth / 2)
                        params.x = screenWidth - (36 * density).toInt()
                        isDocked = true
                        centerWm.updateViewLayout(root, params)
                    }
                    true
                }
                else -> false
            }
        }

        root.addView(topBar)
        centerWm.addView(root, params)
        centerContainer = root
    }

    private fun updateCenterSourceVisibility() {
        if (centerSource == "MAPS") {
            centerMapsWebView?.visibility = View.VISIBLE
            centerWebView?.visibility = View.GONE
        } else {
            centerWebView?.visibility = View.VISIBLE
            centerMapsWebView?.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showArHudProjection(videoUrl: String, mapsUrl: String) {
        val dm = getSystemService(DisplayManager::class.java)
        val allDisplays = dm.displays
        val preferredId = Prefs.getHudDisplayId(this)

        val hudDisplay = allDisplays.firstOrNull { it.displayId == preferredId }
            ?: allDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (hudDisplay == null) {
            Toast.makeText(
                this,
                "AR-HUD secondary display not detected; keeping Center Pop-Up active",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val hudContext = createDisplayContext(hudDisplay)
        val hudMetrics = hudContext.resources.displayMetrics
        val scalePct = Prefs.getHudScale(this) / 100f
        val hudW = (hudMetrics.widthPixels * scalePct).toInt().coerceAtLeast(360)
        val hudH = (hudW * 9) / 16

        // Build active HUD WebView (Google Maps by default or YouTube)
        val activeWv = if (hudSource == "MAPS") {
            createConfiguredMapsWebView(mapsUrl, isHud = true)
        } else {
            createConfiguredVideoWebView(videoUrl)
        }

        val frame = FrameLayout(hudContext).apply {
            setBackgroundColor(Color.BLACK)
        }
        frame.addView(activeWv)

        // Add Top-Left HUD Telemetry Card if HUD source is Maps
        if (hudSource == "MAPS" && Prefs.isHudTelemetryCardEnabled(this)) {
            val card = buildHudTelemetryCard(hudContext)
            hudTelemetryCard = card
            frame.addView(card)
        }

        try {
            val wm = hudContext.getSystemService(WindowManager::class.java)
            val hudParams = WindowManager.LayoutParams(
                hudW,
                hudH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = Prefs.getHudOffsetX(this@OverlayHudService)
                y = Prefs.getHudOffsetY(this@OverlayHudService)
            }

            wm.addView(frame, hudParams)
            hudWm = wm
            hudContainer = frame
            hudWebView = activeWv
        } catch (e: Exception) {
            // Presentation Fallback
            val pres = object : Presentation(this, hudDisplay) {
                override fun onCreate(savedInstanceState: android.os.Bundle?) {
                    super.onCreate(savedInstanceState)
                    window?.addFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                    val root = FrameLayout(context).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                    val card = FrameLayout(context).apply {
                        layoutParams = FrameLayout.LayoutParams(hudW, hudH).apply {
                            leftMargin = Prefs.getHudOffsetX(this@OverlayHudService)
                            topMargin = Prefs.getHudOffsetY(this@OverlayHudService)
                        }
                    }
                    card.addView(frame)
                    root.addView(card)
                    setContentView(root)
                    hudWebView = activeWv
                }
            }
            pres.show()
            hudPresentation = pres
        }
    }

    private fun buildHudTelemetryCard(context: Context): LinearLayout {
        val density = context.resources.displayMetrics.density
        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#E6090D14")) // 90% black glass
            cornerRadius = 14 * density
            setStroke((1.5f * density).toInt(), Color.parseColor("#10B981")) // Emerald stroke
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding((12 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = (14 * density).toInt()
                topMargin = (14 * density).toInt()
            }
        }

        // Top Row: Big Turn Maneuver Icon + Distance
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(context).apply {
            text = currentNav.maneuver
            textSize = 24f
            setTextColor(Color.parseColor("#10B981"))
            setPadding(0, 0, (8 * density).toInt(), 0)
        }
        hudManeuverIcon = icon
        topRow.addView(icon)

        val dist = TextView(context).apply {
            text = currentNav.distance
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        hudManeuverDistance = dist
        topRow.addView(dist)
        card.addView(topRow)

        // Middle Row: Next Road / Street
        val street = TextView(context).apply {
            text = currentNav.street
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
        }
        hudStreetName = street
        card.addView(street)

        // Bottom Strip: Arrival Time / ETA / Remaining or GPS Speed
        val trip = TextView(context).apply {
            text = if (currentNav.isNavigating) {
                "${currentNav.eta} · ${currentNav.remainingDist}"
            } else {
                "${currentSpeedKmh} km/h · 8295 GNSS"
            }
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        hudTripSummary = trip
        card.addView(trip)

        return card
    }

    private fun updateHudCardUi(telemetry: NavTelemetry) {
        Handler(Looper.getMainLooper()).post {
            currentNav = telemetry
            hudManeuverIcon?.text = telemetry.maneuver
            hudManeuverDistance?.text = telemetry.distance
            hudStreetName?.text = telemetry.street
            if (telemetry.isNavigating) {
                hudTripSummary?.text = "${telemetry.eta} · ${telemetry.remainingDist} (ETA)"
            } else {
                hudTripSummary?.text = "${currentSpeedKmh} km/h · 8295 GNSS Active"
            }
        }
    }

    private fun showTestPatternOnDisplay(displayId: Int) {
        val dm = getSystemService(DisplayManager::class.java)
        val targetDisplay = dm.displays.firstOrNull { it.displayId == displayId }
        if (targetDisplay == null) {
            Toast.makeText(this, "Display ID $displayId not found!", Toast.LENGTH_SHORT).show()
            return
        }

        testPatternPresentation?.dismiss()
        val pres = object : Presentation(this, targetDisplay) {
            override fun onCreate(savedInstanceState: android.os.Bundle?) {
                super.onCreate(savedInstanceState)
                val root = FrameLayout(context).apply {
                    setBackgroundColor(Color.argb(230, 20, 20, 30))
                }
                val tv = TextView(context).apply {
                    text = "✦ ZEEKR 7X HUD PROBE ✦\n[ Display ID $displayId ]\n${targetDisplay.name}\nActive AR-HUD Calibration Plane"
                    setTextColor(Color.parseColor("#10B981"))
                    textSize = 28f
                    gravity = Gravity.CENTER
                }
                root.addView(tv, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                ))
                setContentView(root)
            }
        }
        pres.show()
        testPatternPresentation = pres

        Handler(Looper.getMainLooper()).postDelayed({
            testPatternPresentation?.dismiss()
            testPatternPresentation = null
        }, 6000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredVideoWebView(url: String): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = DESKTOP_UA
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    val js = """
                        (function() {
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
                    view?.evaluateJavascript(js, null)
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredMapsWebView(url: String, isHud: Boolean): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setGeolocationEnabled(true)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = MOBILE_MAPS_UA

            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }

            if (isHud) {
                val bridge = MapsTelemetryBridge { telemetry ->
                    updateHudCardUi(telemetry)
                }
                addJavascriptInterface(bridge, "ZeekrNavBridge")
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    view?.evaluateJavascript(MapsTelemetryBridge.JS_INJECT_MAPS, null)
                    if (mapLayer == "SATELLITE") {
                        view?.evaluateJavascript(MapsTelemetryBridge.JS_TOGGLE_SATELLITE, null)
                    }
                }
            }
            loadUrl(url)
        }
    }

    private fun setupCarAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ZeekrOverlayMediaSession").apply {
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
                override fun onPlay() { togglePlayPause(forcePlay = true) }
                override fun onPause() { togglePlayPause(forcePause = true) }
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

    private fun skipVideoSeconds(seconds: Int) {
        val js = """
            (function() {
                const v = document.querySelector('video');
                if (v) { v.currentTime = Math.max(0, v.currentTime + ($seconds)); }
            })();
        """.trimIndent()
        centerWebView?.evaluateJavascript(js, null)
        hudWebView?.evaluateJavascript(js, null)
    }

    private fun togglePlayPause(forcePlay: Boolean = false, forcePause: Boolean = false) {
        val js = when {
            forcePlay -> "document.querySelector('video')?.play();"
            forcePause -> "document.querySelector('video')?.pause();"
            else -> "const v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); }"
        }
        centerWebView?.evaluateJavascript(js, null)
        hudWebView?.evaluateJavascript(js, null)
    }

    private fun startLocationUpdates() {
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        currentSpeedKmh = (location.speed * 3.6f).toInt()
        if (!currentNav.isNavigating) {
            hudTripSummary?.text = "$currentSpeedKmh km/h · 8295 GNSS"
        }
    }

    private fun barButton(label: String, onClick: (View) -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#E6ECF5"))
            textSize = 12f
            setPadding(16, 6, 16, 6)
            setOnClickListener { onClick(it) }
        }
    }

    private fun removeHudProjection() {
        hudWebView?.destroy()
        hudWebView = null
        hudContainer?.let { runCatching { hudWm?.removeView(it) } }
        hudContainer = null
        hudPresentation?.dismiss()
        hudPresentation = null
        hudTelemetryCard = null
    }

    private fun cleanupWindows() {
        centerWebView?.destroy()
        centerWebView = null
        centerMapsWebView?.destroy()
        centerMapsWebView = null
        centerContainer?.let { runCatching { centerWm.removeView(it) } }
        centerContainer = null
        removeHudProjection()
    }

    private fun startForegroundNotification() {
        val channelId = "zeekr_7x_tubedash_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "7xTubeDash Driving Service", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("7xTubeDash 2.0 Active")
            .setContentText("Google Maps AR-HUD & Floating Media Running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(2002, notif)
    }

    override fun onDestroy() {
        locationManager?.removeUpdates(this)
        cleanupWindows()
        mediaSession?.release()
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        super.onDestroy()
    }
}
