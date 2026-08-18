package com.mobosafe.dashcam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * TIER 3 SCAFFOLD — not wired into MainActivity yet, do this last if time allows.
 *
 * The goal: move the RtmpCamera2 instance's lifecycle in here behind a foreground
 * notification so Android doesn't kill the camera/mic pipeline when the screen
 * locks or the app backgrounds. The cleanest way to do this in the time you have:
 *
 *   1. Keep MainActivity's RtmpCamera2 as-is for Tier 1/2 testing.
 *   2. Once that's solid, start this service from MainActivity's onCreate with
 *      startForegroundService(Intent(this, StreamService::class.java)) and have
 *      it call startForeground() immediately (Android requires this within
 *      ~5 seconds of service start on API 26+).
 *   3. Move the actual RtmpCamera2 object + surface handling into this service
 *      (a SurfaceView isn't required to encode — RootEncoder can run headless
 *      off a dummy/offscreen surface once streaming has started).
 *
 * Also drop in the "true Tier 2" attempt here if you have time left: check
 * CameraManager.getConcurrentCameraIds() (API 30+) — if front+back appear
 * together in the returned set, you can open two independent RtmpCamera2-style
 * pipelines pointed at two Camera2 sessions and push both to *_front and *_back
 * simultaneously. If the set is empty on your test device, the fast-switch
 * fallback in MainActivity is your Tier 2 answer — say so plainly in the
 * interview, don't over-claim it.
 */
class StreamService : Service() {

    private val channelId = "dashcam_stream_channel"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Dashcam Streaming", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pocket Dashcam")
            .setContentText("Streaming live — do not close")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1, notification)
        }

        // TODO: move RtmpCamera2 start/stop + reconnect logic here (see class doc above).

        return START_STICKY
    }

    /** Returns true if this device can genuinely run two camera sessions at once. */
    fun supportsConcurrentCameras(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        return manager.concurrentCameraIds.any { it.size >= 2 }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
