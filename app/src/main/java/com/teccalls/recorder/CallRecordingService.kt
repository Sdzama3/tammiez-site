package com.teccalls.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that records a phone call. It tries the device's
 * call-audio source first (captures BOTH sides on phones that still allow it),
 * and falls back to the microphone when Android blocks it. On stop it emails
 * the recording (if configured) and deletes it, like the rest of the app.
 */
class CallRecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private val channelId = "teccalls-call"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "Call recording", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("TecCalls")
            .setContentText("Recording the call…")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, n)
        }
    }

    private fun recordingsDir(): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun startRecording() {
        if (recorder != null) return
        try {
            goForeground()
        } catch (e: Exception) {
            // If we can't go foreground we can't legally record; give up quietly.
            stopSelf()
            return
        }

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(recordingsDir(), "call_$stamp.m4a")
        currentFile = file

        // Try the call-audio sources first (both sides), then plain mic.
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        for (src in sources) {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            try {
                rec.setAudioSource(src)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128000)
                rec.setAudioSamplingRate(44100)
                rec.setOutputFile(file.absolutePath)
                rec.prepare()
                rec.start()
                recorder = rec
                return
            } catch (e: Exception) {
                try { rec.release() } catch (_: Exception) {}
            }
        }
        // Nothing worked — nothing to keep.
        currentFile = null
    }

    private fun stopRecording() {
        val rec = recorder
        recorder = null
        if (rec != null) {
            try {
                rec.stop()
            } catch (e: Exception) {
                currentFile?.delete()
                currentFile = null
            }
            try { rec.release() } catch (_: Exception) {}
        }
        val f = currentFile
        currentFile = null
        if (f != null && f.exists() && f.length() > 0 && Prefs.emailConfigured(this)) {
            val ctx = applicationContext
            Thread {
                try {
                    Backup.sendRecording(ctx, f)
                    f.delete()
                } catch (e: Exception) {
                    // keep it; the background worker will retry
                }
            }.start()
            BackupWorker.nudge(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recorder != null) stopRecording()
    }

    companion object {
        const val ACTION_START = "com.teccalls.recorder.START_CALL_REC"
        const val ACTION_STOP = "com.teccalls.recorder.STOP_CALL_REC"
    }
}
