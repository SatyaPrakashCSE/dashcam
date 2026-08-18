package com.mobosafe.dashcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.encoder.input.video.CameraHelper
import net.ossrs.rtmp.ConnectCheckerRtmp

/**
 * Tier 1: one camera + mic -> RTMP.
 * Tier 2: camera-switch fallback (front/back not run concurrently on this path;
 *         see StreamService.tryConcurrentCameras() for the true dual-feed attempt).
 *
 * Endpoint naming follows the spec exactly:
 *   rtmp://15.207.177.194:1936/hackathon/{ROLLNO}_front
 *   rtmp://15.207.177.194:1936/hackathon/{ROLLNO}_back
 */
class MainActivity : AppCompatActivity(), ConnectCheckerRtmp, SurfaceHolder.Callback {

    private lateinit var rtmpCamera2: RtmpCamera2
    private lateinit var statusText: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnSwitchCamera: Button

    // Which physical camera we're currently bound to — toggling this is our
    // Tier 2 fallback when true concurrent dual-camera isn't supported on device.
    private var streamingBack = false

    private val rollNo get() = getString(R.string.roll_no)
    private val rtmpHost get() = getString(R.string.rtmp_host)

    private val currentStreamUrl: String
        get() = rtmpHost + rollNo + (if (streamingBack) "_back" else "_front")

    private val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private val REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)

        val surfaceView = findViewById<android.view.SurfaceView>(R.id.surfaceView)
        rtmpCamera2 = RtmpCamera2(surfaceView, this)
        surfaceView.holder.addCallback(this)

        btnStartStop.setOnClickListener { toggleStream() }
        btnSwitchCamera.setOnClickListener { switchCamera() }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
        }
    }

    private fun hasPermissions(): Boolean = permissions.all {
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

    // ---- Surface lifecycle: prepare video/audio once the preview surface exists ----

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!hasPermissions()) return
        // Encoder settings from the spec: 1280x720 @ 25fps, 1-2 Mbps, keyframe every 2s.
        val videoPrepared = rtmpCamera2.prepareVideo(1280, 720, 25, 1_500_000, 2, CameraHelper.getCameraOrientation(this))
        val audioPrepared = rtmpCamera2.prepareAudio(128 * 1024, 44100, true, false, false)
        if (!videoPrepared || !audioPrepared) {
            statusText.text = "Encoder init failed — check device codec support"
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (rtmpCamera2.isStreaming) rtmpCamera2.stopStream()
    }

    // ---- Start / stop ----

    private fun toggleStream() {
        if (rtmpCamera2.isStreaming) {
            rtmpCamera2.stopStream()
            btnStartStop.text = "Start Stream"
            statusText.text = "● STOPPED"
        } else {
            if (rtmpCamera2.isRecording.not()) {
                rtmpCamera2.startStream(currentStreamUrl)
                btnStartStop.text = "Stop Stream"
                statusText.text = "● CONNECTING… ${if (streamingBack) "BACK" else "FRONT"}"
            }
        }
    }

    private fun switchCamera() {
        // Tier 2 fallback: fast-switch between front/back, each publishing to its
        // own correctly-suffixed endpoint. True concurrent dual-stream needs two
        // independent encoder pipelines — see StreamService for that attempt.
        val wasStreaming = rtmpCamera2.isStreaming
        if (wasStreaming) rtmpCamera2.stopStream()
        rtmpCamera2.switchCamera()
        streamingBack = !streamingBack
        if (wasStreaming) {
            rtmpCamera2.startStream(currentStreamUrl)
            statusText.text = "● CONNECTING… ${if (streamingBack) "BACK" else "FRONT"}"
        }
    }

    // ---- ConnectCheckerRtmp: drives status text + Tier 3 auto-reconnect ----

    override fun onConnectionSuccessRtmp() {
        runOnUiThread { statusText.text = "● LIVE — ${if (streamingBack) "BACK" else "FRONT"} — $rollNo" }
    }

    override fun onConnectionFailedRtmp(reason: String) {
        runOnUiThread {
            statusText.text = "● RECONNECTING… ($reason)"
            // Tier 3: auto-reconnect on drop instead of just dying.
            if (rtmpCamera2.reTry(5000, reason, null)) {
                // retry scheduled by the library
            } else {
                rtmpCamera2.stopStream()
                btnStartStop.text = "Start Stream"
            }
        }
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
        runOnUiThread {
            if (rtmpCamera2.isStreaming) {
                statusText.text = "● LIVE — ${if (streamingBack) "BACK" else "FRONT"} — ${bitrate / 1000} kbps"
            }
        }
    }

    override fun onDisconnectRtmp() {
        runOnUiThread { statusText.text = "● DISCONNECTED" }
    }

    override fun onAuthErrorRtmp() {
        runOnUiThread { statusText.text = "● AUTH ERROR" }
    }

    override fun onAuthSuccessRtmp() {}

    override fun onDestroy() {
        super.onDestroy()
        if (rtmpCamera2.isStreaming) rtmpCamera2.stopStream()
    }
}
