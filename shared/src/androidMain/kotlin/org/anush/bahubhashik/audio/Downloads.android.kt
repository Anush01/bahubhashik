package org.anush.bahubhashik.audio

import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private const val FOLDER = "downloads"

/**
 * Files live in the app's private files dir, exposed to other apps only
 * through a FileProvider when the user actually shares one — no storage
 * permission needed, and nothing left behind on uninstall.
 *
 * One directory per message id, holding a single readable file. The id has
 * to stay out of the filename itself: that name is what the person receiving
 * the message sees in WhatsApp, and a UUID in it looks like junk mail.
 */
actual object Downloads {

    private fun root(): File = File(AndroidContext.application.filesDir, FOLDER).apply { mkdirs() }

    private fun directory(messageId: String) = File(root(), messageId)

    private fun fileFor(messageId: String): File? =
        directory(messageId).listFiles()?.firstOrNull()

    actual fun save(messageId: String, filename: String, bytes: ByteArray): String {
        delete(messageId)
        val directory = directory(messageId).apply { mkdirs() }
        val file = File(directory, filename)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    actual fun isSaved(messageId: String): Boolean = fileFor(messageId) != null

    actual fun delete(messageId: String) {
        directory(messageId).deleteRecursively()
    }

    actual fun deleteAll() {
        root().deleteRecursively()
    }

    actual fun savedIds(): Set<String> =
        root().listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

    actual fun share(messageId: String) {
        val file = fileFor(messageId) ?: return
        val context = AndroidContext.application

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            // EXTRA_STREAM alone grants the *target* app access but not the
            // chooser, which then can't read the file to preview it. ClipData
            // is what Android asks for to cover both.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Started from the application context, so the chooser needs its own task.
        val chooser = Intent.createChooser(send, "Share this message").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
