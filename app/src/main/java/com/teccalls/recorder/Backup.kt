package com.teccalls.recorder

import android.content.Context
import java.io.File

/**
 * Sends one recording by email. If the file is too large for a single email, it
 * is split into numbered parts and each part is sent separately, then the parts
 * are cleaned up. The caller deletes the original after this returns normally.
 * Throws if any send fails (so the caller keeps the file and retries later).
 * Call this OFF the main thread.
 */
object Backup {

    // Gmail caps a whole message at 25 MB *after* base64 encoding (~+37%),
    // so keep each raw part well under that.
    private const val MAX_PART = 18L * 1024 * 1024

    fun sendRecording(c: Context, file: File) {
        val sender = Prefs.senderEmail(c)
        val pw = Prefs.appPassword(c)
        val to = Prefs.recipient(c)

        if (file.length() <= MAX_PART) {
            MailSender.send(
                sender, pw, to,
                "Recording: ${file.name}",
                "Recording attached from TecCalls.",
                file
            )
            return
        }

        val workDir = File(c.cacheDir, "parts-${System.nanoTime()}").apply { mkdirs() }
        try {
            val parts = splitFile(file, workDir)
            val total = parts.size
            parts.forEachIndexed { i, part ->
                MailSender.send(
                    sender, pw, to,
                    "Recording: ${file.name} (part ${i + 1} of $total)",
                    "Part ${i + 1} of $total of ${file.name}.\n" +
                        "Save all $total parts, then join them in order to rebuild the recording:\n" +
                        "  Windows:  copy /b \"${file.name}.part01\"+\"${file.name}.part02\"+... \"${file.name}\"\n" +
                        "  Mac/Linux:  cat \"${file.name}\".part* > \"${file.name}\"",
                    part
                )
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** Cuts the file into <= MAX_PART byte chunks named name.part01, name.part02, … */
    private fun splitFile(file: File, dir: File): List<File> {
        val parts = mutableListOf<File>()
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            var index = 1
            while (true) {
                val partFile = File(dir, "${file.name}.part%02d".format(index))
                var written = 0L
                partFile.outputStream().use { out ->
                    while (written < MAX_PART) {
                        val toRead = minOf(buffer.size.toLong(), MAX_PART - written).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                    }
                }
                if (written > 0) {
                    parts.add(partFile)
                    index++
                } else {
                    partFile.delete()
                }
                if (written < MAX_PART) break // reached end of file
            }
        }
        return parts
    }
}
