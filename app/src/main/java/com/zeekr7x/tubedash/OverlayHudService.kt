package com.zeekr7x.tubedash

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.*
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayHudService : Service() {

    private lateinit var centerWm: WindowManager
    private var centerContainer: FrameLayout? = null
    private var centerWebView: WebView? = null

    private var hudWm: WindowManager? = null
    private var hudContainer: FrameLayout? = null
    private var hudWebView: WebView? = null
    private var hudPresentation: Presentation? = null

    private var testPatternPresentation: Presentation? = null
    private var isDocked = false
    private var isLarge = false
    private var savedX = 80

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null

    companion object {
        const val ACTION_TEST_PATTERN = "ACTION_TEST_PATTERN"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        setupCarAudioFocus()
        setupMediaSession()

        if (intent?.action == ACTION_TEST_PATTERN) {
            val displayId = intent.getIntExtra("DISPLAY_ID", 1)
            showTestPatternOnDisplay(displayId)
            return START_STICKY
        }

        val url = intent?.getStringExtra("VIDEO_URL") ?: Prefs.getLastUrl(this)
        val mode = intent?.getIntExtra("OUTPUT_MODE", Prefs.getLastUsedDrivingMode(this)) ?: 0
        cleanupWindows()

        if (mode == 0 || mode == 2) {
            showCenterFloatingPopUp(url)
        }
        if (mode == 1 || mode == 2) {
            showArHudProjection(url)
        }

        return START_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun showCenterFloatingPopUp(url: String) {
        centerWm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val startW = (520 * density).toInt()
        val startH = (320 * density).toInt()

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
            x = 90
            y = 90
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#141821"))
            setPadding(4, 4, 4, 4)
        }

        val wv = createConfiguredWebView(url)
        root.addView(wv)

        // Control strip: Dock, HUD, -10s, +10s, Full, Close
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

        topBar.addView(barButton("-10s") { skipVideoSeconds(-10) })
        topBar.addView(barButton("+10s") { skipVideoSeconds(10) })

        topBar.addView(barButton("HUD") {
            if (hudContainer == null && hudPresentation == null) {
                showArHudProjection(wv.url ?: url)
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
                params.width = (params.width * factor).toInt().coerceIn((280 * density).toInt(), (1100 * density).toInt())
                params.height = (params.height * factor).toInt().coerceIn((180 * density).toInt(), (700 * density).toInt())
                centerWm.updateViewLayout(root, params)
                return true
            }
        })

        // Double-tap toggle Small/Large
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                isLarge = !isLarge
                params.width = ((if (isLarge) 820 else 520) * density).toInt()
                params.height = ((if (isLarge) 480 else 320) * density).toInt()
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
                    // Push near right edge auto-docks into slim strip
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
        centerWebView = wv
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showArHudProjection(url: String) {
        val dm = getSystemService(DisplayManager::class.java)
        val allDisplays = dm.displays
        val preferredId = Prefs.getHudDisplayId(this)

        val hudDisplay = allDisplays.firstOrNull { it.displayId == preferredId }
            ?: allDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (hudDisplay == null) {
            Toast.makeText(
                this,
                "AR-HUD secondary display not exposed; keeping Center Pop-Up active",
                Toast.LENGTH_LONG
            ).show()
            if (centerContainer == null) showCenterFloatingPopUp(url)
            return
        }

        val hudContext = createDisplayContext(hudDisplay)
        val hudMetrics = hudContext.resources.displayMetrics
        val scalePct = Prefs.getHudScale(this) / 100f
        val hudW = (hudMetrics.widthPixels * scalePct).toInt().coerceAtLeast(320)
        val hudH = (hudW * 9) / 16

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

            val frame = FrameLayout(hudContext).apply {
                setBackgroundColor(Color.BLACK)
            }
            val wv = createConfiguredWebView(url)
            frame.addView(wv)
            wm.addView(frame, hudParams)
            hudWm = wm
            hudContainer = frame
            hudWebView = wv
        } catch (e: Exception) {
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
                    val wv = createConfiguredWebView(url)
                    card.addView(wv)
                    root.addView(card)
                    setContentView(root)
                    hudWebView = wv
                }
            }
            pres.show()
            hudPresentation = pres
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
                    text = "✦ ZEEKR 7X HUD PROBE ✦\n[ Display ID $displayId ]\n${targetDisplay.name}\nActive Calibration Target"
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

        // Auto dismiss after 6 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            testPatternPresentation?.dismiss()
            testPatternPresentation = null
        }, 6000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(url: String): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString =
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
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

    private fun setupCarAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        // User confirmed: DO NOT duck audio volume during navigation voice prompts!
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        // Permanent loss
                    }
                }
            }
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
                override fun onPlay() {
                    togglePlayPause(forcePlay = true)
                }

                override fun onPause() {
                    togglePlayPause(forcePause = true)
                }

                override fun onFastForward() {
                    skipVideoSeconds(10)
                }

                override fun onRewind() {
                    skipVideoSeconds(-10)
                }

                override fun onSkipToNext() {
                    skipVideoSeconds(10)
                }

                override fun onSkipToPrevious() {
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

    private fun barButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#E6ECF5"))
            textSize = 12f
            setPadding(18, 6, 18, 6)
            setOnClickListener { onClick() }
        }
    }

    private fun removeHudProjection() {
        hudWebView?.destroy()
        hudWebView = null
        hudContainer?.let { runCatching { hudWm?.removeView(it) } }
        hudContainer = null
        hudPresentation?.dismiss()
        hudPresentation = null
    }

    private fun cleanupWindows() {
        centerWebView?.destroy()
        centerWebView = null
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
            .setContentTitle("7xTubeDash Active")
            .setContentText("Driving Pop-Up / AR-HUD Media Running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(2002, notif)
    }

    override fun onDestroy() {
        cleanupWindows()
        mediaSession?.release()
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        super.onDestroy()
    }
}
