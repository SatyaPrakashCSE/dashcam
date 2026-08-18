package com.mobosafe.dashcam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView

/**
 * Tier 1 : one camera + mic -> RTMP (primary RtmpCamera2 on OpenGlView).
 * Tier 2 : concurrent dual-cam. Checks CameraManager.concurrentCameraIds (API 30+).
 *   - Supported  → opens a second headless RtmpCamera2 on the opposite physical camera.
 *   - Unsupported → fast-switch fallback (stop/flip/restart on one encoder pipeline).
 * Tier 3 : auto-reconnect via getStreamClient().reTry(); screen-lock survival via
 *           StreamService foreground notification (keeps camera/mic pipeline alive).
 *
 * Endpoint naming follows the spec exactly:
 *   rtmp://15.207.177.194:1936/hackathon/{ROLLNO}_front  ← back  physical cam (road view)
 *   rtmp://15.207.177.194:1936/hackathon/{ROLLNO}_back   ← front physical cam (cabin view)
 */
class MainActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {

    // ── Primary encoder — shown in the OpenGlView preview ─────────────────────
    private lateinit var rtmpCamera2: RtmpCamera2

    // ── Secondary encoder — headless Context constructor (Tier 2) ─────────────
    private var rtmpCamera2Secondary: RtmpCamera2? = null
    private var isDualStreaming = false

    private lateinit var statusText: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnDualStream: Button

    /**
     * false → primary is on BACK  physical camera → push to _front endpoint (road view)
     * true  → primary is on FRONT physical camera → push to _back  endpoint (cabin view)
     */
    private var streamingBack = false

    private val rollNo   get() = getString(R.string.roll_no)
    private val rtmpHost get() = getString(R.string.rtmp_host)

    private val currentStreamUrl: String
        get() = rtmpHost + rollNo + (if (streamingBack) "_back" else "_front")

    private val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private val REQUEST_CODE = 101

    // ── Tier 3: elapsed-time ticker ───────────────────────────────────────────
    private val timerHandler = Handler(Looper.getMainLooper())
    private var streamStartMs = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (rtmpCamera2.isStreaming) {
                val elapsed = (System.currentTimeMillis() - streamStartMs) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                val camLabel = if (isDualStreaming) "DUAL" else if (streamingBack) "BACK" else "FRONT"
                statusText.text = "● LIVE $camLabel — %02d:%02d — $rollNo".format(min, sec)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    // ── ConnectChecker for the secondary (headless) camera ────────────────────
    private val secondaryChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {}
        override fun onConnectionSuccess() {
            runOnUiThread {
                if (isDualStreaming) statusText.text = "● DUAL LIVE — $rollNo"
            }
        }
        override fun onConnectionFailed(reason: String) {
            runOnUiThread {
                rtmpCamera2Secondary?.getStreamClient()?.reTry(5000, reason, null)
            }
        }
        override fun onNewBitrate(bitrate: Long) {}
        override fun onDisconnect() {
            runOnUiThread { if (isDualStreaming) statusText.text = "● BACK CAM DISCONNECTED" }
        }
        override fun onAuthError()   {}
        override fun onAuthSuccess() {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText    = findViewById(R.id.statusText)
        btnStartStop  = findViewById(R.id.btnStartStop)
        btnDualStream = findViewById(R.id.btnSwitchCamera)

        val openGlView = findViewById<OpenGlView>(R.id.surfaceView)
        rtmpCamera2 = RtmpCamera2(openGlView, this)
        openGlView.holder.addCallback(this)

        btnStartStop.setOnClickListener  { toggleSingleStream() }
        btnDualStream.setOnClickListener { toggleDualStream() }
        btnDualStream.text = "Dual Stream"

        // Tier 3: start foreground service so the camera+mic pipeline survives
        // screen lock and app backgrounding (Android kills Activity but not Service).
        startForegroundServiceCompat()

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
        }
    }

    private fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            statusText.text = "Permissions granted — ready to stream"
        } else if (requestCode == REQUEST_CODE) {
            statusText.text = "Camera/mic permission denied — cannot stream"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Surface lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!hasPermissions()) return
        // Skip re-prepare if already streaming (surface recreated after screen-unlock).
        if (rtmpCamera2.isStreaming) return
        val videoPrepared = rtmpCamera2.prepareVideo(
            1280, 720, 25, 1_500_000, 2,
            CameraHelper.getCameraOrientation(this)
        )
        val audioPrepared = rtmpCamera2.prepareAudio(128 * 1024, 44100, true, false, false)
        if (!videoPrepared || !audioPrepared) {
            statusText.text = "Encoder init failed — check device codec support"
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // DO NOT stop stream here: screen lock destroys the Surface but
        // RootEncoder keeps the encoder pipeline alive without a display.
        // The foreground StreamService keeps the process alive.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 1 — single stream start / stop
    // ─────────────────────────────────────────────────────────────────────────

    private fun toggleSingleStream() {
        if (isDualStreaming) { stopDualStream(); return }
        if (rtmpCamera2.isStreaming) {
            rtmpCamera2.stopStream()
            stopTimer()
            btnStartStop.text = "Start Stream"
            statusText.text   = "● STOPPED"
        } else {
            rtmpCamera2.startStream(currentStreamUrl)
            startTimer()
            btnStartStop.text = "Stop Stream"
            statusText.text   = "● CONNECTING… ${if (streamingBack) "BACK" else "FRONT"}"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 2 — dual stream
    // ─────────────────────────────────────────────────────────────────────────

    private fun toggleDualStream() {
        if (isDualStreaming) stopDualStream() else startDualStream()
    }

    private fun startDualStream() {
        if (!hasPermissions()) return

        // Check concurrent-camera support (Android 11 / API 30+)
        val supportsConcurrent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val mgr = getSystemService(CAMERA_SERVICE) as CameraManager
                mgr.concurrentCameraIds.any { it.size >= 2 }
            } catch (e: Exception) { false }
        } else false

        if (!supportsConcurrent) {
            statusText.text = "● No concurrent cam support — fast-switching"
            fastSwitchCamera()
            return
        }

        isDualStreaming = true

        // Ensure primary is on BACK physical camera → _front endpoint
        if (streamingBack) {
            rtmpCamera2.switchCamera()
            streamingBack = false
        }

        // Primary: BACK cam → _front
        if (!rtmpCamera2.isStreaming) {
            rtmpCamera2.startStream(rtmpHost + rollNo + "_back")
            startTimer()
        }

        // Secondary: headless, FRONT cam → _back
        rtmpCamera2Secondary?.stopStream()
        rtmpCamera2Secondary = RtmpCamera2(applicationContext, secondaryChecker).also { cam ->
            cam.prepareVideo(1280, 720, 25, 1_500_000, 2, CameraHelper.getCameraOrientation(this))
            cam.prepareAudio(128 * 1024, 44100, true, false, false)
            try { cam.startPreview(CameraHelper.Facing.FRONT) } catch (_: Exception) {}
            cam.startStream(rtmpHost + rollNo + "_front")
        }

        btnStartStop.text  = "Stop All"
        btnDualStream.text = "Stop Dual"
        statusText.text    = "● DUAL STREAM STARTING…"
    }

    private fun stopDualStream() {
        isDualStreaming = false
        rtmpCamera2Secondary?.stopStream()
        rtmpCamera2Secondary = null
        btnDualStream.text = "Dual Stream"
        btnStartStop.text  = "Stop Stream"
        statusText.text    = "● SINGLE STREAM (dual stopped)"
    }

    /** Tier 2 fallback for devices without concurrent camera support. */
    private fun fastSwitchCamera() {
        val wasStreaming = rtmpCamera2.isStreaming
        if (wasStreaming) rtmpCamera2.stopStream()
        rtmpCamera2.switchCamera()
        streamingBack = !streamingBack
        if (wasStreaming) {
            rtmpCamera2.startStream(currentStreamUrl)
            statusText.text = "● CONNECTING… ${if (streamingBack) "BACK" else "FRONT"}"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 3 — elapsed timer helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun startTimer() {
        streamStartMs = System.currentTimeMillis()
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    private fun stopTimer() {
        timerHandler.removeCallbacks(timerRunnable)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 3 — foreground service (keeps pipeline alive on screen lock)
    // ─────────────────────────────────────────────────────────────────────────

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, StreamService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ConnectChecker (primary camera)
    // ─────────────────────────────────────────────────────────────────────────

    override fun onConnectionStarted(url: String) {
        runOnUiThread { statusText.text = "● CONNECTING…" }
    }

    override fun onConnectionSuccess() {
        runOnUiThread {
            if (isDualStreaming) {
                statusText.text = "● FRONT LIVE — waiting for back cam…"
            }
            // Timer will update the status text every second once live
        }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            statusText.text = "● RECONNECTING… ($reason)"
            if (!rtmpCamera2.getStreamClient().reTry(5000, reason, null)) {
                rtmpCamera2.stopStream()
                stopTimer()
                btnStartStop.text = "Start Stream"
            }
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        // Timer ticker shows live elapsed time; we append bitrate when not dual streaming
        if (!isDualStreaming && rtmpCamera2.isStreaming) {
            val elapsed = (System.currentTimeMillis() - streamStartMs) / 1000
            val min = elapsed / 60; val sec = elapsed % 60
            val cam = if (streamingBack) "BACK" else "FRONT"
            runOnUiThread {
                statusText.text = "● LIVE $cam — %02d:%02d — ${bitrate / 1000} kbps".format(min, sec)
            }
        }
    }

    override fun onDisconnect() {
        runOnUiThread { stopTimer(); statusText.text = "● DISCONNECTED" }
    }

    override fun onAuthError()   { runOnUiThread { statusText.text = "● AUTH ERROR" } }
    override fun onAuthSuccess() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        rtmpCamera2Secondary?.stopStream()
        if (rtmpCamera2.isStreaming) rtmpCamera2.stopStream()
        // Note: StreamService keeps running — this is intentional for Tier 3.
        // Organisers can see you streaming even after you background the app.
    }
}
