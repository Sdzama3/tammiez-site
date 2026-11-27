package com.teccalls.recorder

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Runs in the background (even when the app is closed) whenever the phone has a
 * network connection. It emails any recordings still on the phone and deletes
 * each one as soon as it sends. Files that fail stay put and are tried again.
 */
class BackupWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val c = applicationContext
        if (!Prefs.emailConfigured(c)) return Result.success()

        val dir = c.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: c.filesDir
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".m4a") }
            ?.sortedBy { it.lastModified() }
            ?: return Result.success()
        if (files.isEmpty()) return Result.success()

        var allSent = true
        for (f in files) {
            try {
                Backup.sendRecording(c, f) // splits into parts if too big
                f.delete()
            } catch (e: Exception) {
                allSent = false // keep it; WorkManager will retry later
            }
        }
        // Retry the whole job later if anything is still waiting (e.g. flaky signal).
        return if (allSent) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "teccalls-backup-periodic"
        private const val NOW = "teccalls-backup-now"

        private fun netConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Keep a steady check (every ~15 min, Android's minimum) when online. */
        fun schedule(c: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                .setConstraints(netConstraints())
                .build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** Send as soon as there is internet — fires right after a new recording. */
        fun nudge(c: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(netConstraints())
                .build()
            WorkManager.getInstance(c).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
