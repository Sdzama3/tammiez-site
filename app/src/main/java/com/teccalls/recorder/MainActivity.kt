package com.teccalls.recorder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var settingsButton: Button
    private lateinit var listView: ListView

    private val files = mutableListOf<File>()
    private lateinit var adapter: ArrayAdapter<String>

    private val requestMic = 101
    private val channelId = "teccalls"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)
        settingsButton = findViewById(R.id.settingsButton)
        listView = findViewById(R.id.listView)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        recordButton.setOnClickListener {
            if (isRecording) stopRecording() else startRecordingWithPermission()
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position < files.size) routeRecording(files[position], manual = true)
        }

        createNotificationChannel()
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        // Ask once, on first launch, where recordings should go.
        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        } else {
            statusText.text = Prefs.summary(this)
            retryLeftovers()
            // Keep clearing leftovers in the background whenever there's internet.
            if (Prefs.emailConfigured(this)) BackupWorker.schedule(this)
            // Auto-call-recording needs phone + mic permission up front.
            if (Prefs.autoRecordCalls(this)) ensureCallPermissions()
        }
    }

    private fun ensureCallPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.READ_PHONE_STATE)
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 103)
        }
    }

    /**
     * Automatically re-sends any recordings still on the phone (e.g. a send that
     * failed earlier) and deletes each one as soon as it goes through — so you
     * never have to clear them by hand.
     */
    private fun retryLeftovers() {
        if (!Prefs.emailConfigured(this) || isRecording) return
        val leftovers = recordingsDir().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".m4a") }
            ?.sortedBy { it.lastModified() } ?: return
        if (leftovers.isEmpty()) return

        val to = Prefs.recipient(this)
        Thread {
            var sent = 0
            for (f in leftovers) {
                try {
                    Backup.sendRecording(applicationContext, f) // splits if too big
                    f.delete()
                    sent++
                } catch (e: Exception) {
                    // Still couldn't send — leave it and try again next time.
                }
            }
            if (sent > 0) runOnUiThread {
                refreshList()
                statusText.text = "Sent $sent pending recording(s); phone cleared"
                notifyUser("Pending recordings sent", "$sent emailed to $to and removed")
            }
        }.start()
    }

    private fun recordingsDir(): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun refreshList() {
        files.clear()
        recordingsDir().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".m4a") }
            ?.sortedByDescending { it.lastModified() }
            ?.let { files.addAll(it) }
        adapter.clear()
        adapter.addAll(files.map { it.name })
        adapter.notifyDataSetChanged()
    }

    private fun startRecordingWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), requestMic
            )
        } else {
            startRecording()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestMic && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else if (requestCode == requestMic) {
            Toast.makeText(this, "Microphone permission is required to record.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startRecording() {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(recordingsDir(), "rec_$stamp.m4a")
        currentFile = file

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            isRecording = true
            recordButton.text = "Stop"
            statusText.text = "Recording…"
        } catch (e: Exception) {
            rec.release()
            Toast.makeText(this, "Could not start recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        var ok = true
        try {
            recorder?.stop()
        } catch (e: Exception) {
            ok = false
            Toast.makeText(this, "Recording was too short to save.", Toast.LENGTH_SHORT).show()
            currentFile?.delete()
            currentFile = null
        }
        recorder?.release()
        recorder = null
        isRecording = false
        recordButton.text = "Record"
        refreshList()

        if (ok) {
            currentFile?.let { if (it.exists()) routeRecording(it, manual = false) }
            // If email is on, also queue a background send in case we're offline now.
            if (Prefs.emailConfigured(this)) BackupWorker.nudge(this)
        }
    }

    /** Applies the one-time choice automatically; manual = user tapped a list item. */
    private fun routeRecording(file: File, manual: Boolean) {
        when {
            Prefs.emailConfigured(this) -> emailInBackground(file)
            manual -> shareChooser(file) // let them email ad hoc even if default is phone
            else -> statusText.text = "Saved on phone: ${file.name}"
        }
    }

    private fun emailInBackground(file: File) {
        val to = Prefs.recipient(this)
        statusText.text = "Emailing ${file.name}…"
        Thread {
            try {
                Backup.sendRecording(applicationContext, file) // splits if too big
                val removed = file.delete() // free up phone space once it's safely sent
                runOnUiThread {
                    statusText.text =
                        if (removed) "Emailed to $to — removed from phone" else "Emailed to $to"
                    notifyUser("Recording emailed", "${file.name} → $to")
                    refreshList()
                }
            } catch (e: Exception) {
                // Sending failed — keep the file on the phone so it is never lost.
                runOnUiThread {
                    statusText.text = "Email failed — kept on phone"
                    notifyUser("Email failed — kept on phone", e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    private fun shareChooser(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_SUBJECT, "Recording: ${file.name}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Send recording to…"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "TecCalls", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
        }
    }

    private fun notifyUser(title: String, text: String) {
        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), n)
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) stopRecording()
    }
}
