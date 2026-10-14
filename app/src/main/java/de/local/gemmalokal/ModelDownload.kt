package de.local.gemmalokal

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.security.MessageDigest

class ModelDownload(private val context: Context) {
    companion object {
        const val FILE_NAME = "gemma-4-E4B-it.litertlm"
        const val MODEL_BYTES = 3659530240L
        const val SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
        const val SOURCE = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it.litertlm"
    }

    private val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(DownloadManager::class.java)
    val file: File get() = File(context.getExternalFilesDir(null), FILE_NAME)
    val ready: Boolean get() = prefs.getBoolean("verified", false) && file.length() == MODEL_BYTES
    val downloadId: Long get() = prefs.getLong("download_id", -1L)

    fun start(): String? {
        if (ready) return "Das Modell ist bereits installiert."
        val previous = status()
        if (previous?.status == DownloadManager.STATUS_PENDING ||
            previous?.status == DownloadManager.STATUS_RUNNING ||
            previous?.status == DownloadManager.STATUS_PAUSED) {
            return "Der Download läuft bereits."
        }
        if (downloadId >= 0) manager.remove(downloadId)
        val folder = context.getExternalFilesDir(null) ?: return "Kein App-Speicher verfügbar."
        if (StatFs(folder.path).availableBytes < MODEL_BYTES + 200_000_000L) {
            return "Mindestens 3,9 GB freier Speicherplatz erforderlich."
        }
        file.delete()
        val request = DownloadManager.Request(Uri.parse(SOURCE))
            .setTitle("Gemma 4 E4B-it")
            .setDescription("Modell für Gemma Lokal")
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        return try {
            val id = manager.enqueue(request)
            prefs.edit().putLong("download_id", id).putBoolean("verified", false).apply()
            null
        } catch (e: Exception) { e.localizedMessage ?: "Download konnte nicht starten." }
    }

    data class Progress(val status: Int, val bytes: Long, val total: Long, val reason: Int)

    fun status(): Progress? {
        val id = downloadId
        if (id < 0) return null
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            fun value(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
            return Progress(
                value(DownloadManager.COLUMN_STATUS).toInt(),
                value(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                value(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                value(DownloadManager.COLUMN_REASON).toInt()
            )
        }
        return null
    }

    fun verify(): Boolean {
        if (file.length() != MODEL_BYTES) {
            prefs.edit().putBoolean("verified", false).putLong("download_id", -1L).apply()
            file.delete()
            return false
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val block = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(block)
                if (n < 0) break
                digest.update(block, 0, n)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val correct = hash == SHA256
        prefs.edit().putBoolean("verified", correct).putLong("download_id", -1L).apply()
        if (!correct) file.delete()
        return correct
    }

    fun delete() {
        if (downloadId >= 0) manager.remove(downloadId)
        prefs.edit().putLong("download_id", -1L).putBoolean("verified", false).apply()
        file.delete()
    }
}
