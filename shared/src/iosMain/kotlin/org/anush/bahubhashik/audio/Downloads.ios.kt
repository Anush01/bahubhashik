package org.anush.bahubhashik.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

private const val FOLDER = "downloads"

/** Left behind in a message's directory when its saved copy is deleted by hand. */
private const val REMOVED_MARKER = ".removed"

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    }

/**
 * Files live in the app's Documents directory. Sharing goes through
 * UIActivityViewController, which is what puts WhatsApp and Mail in the list.
 */
@OptIn(ExperimentalForeignApi::class)
actual object Downloads {

    private fun folder(): NSURL {
        val documents = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .first() as NSURL
        val directory = documents.URLByAppendingPathComponent(FOLDER, isDirectory = true)!!
        NSFileManager.defaultManager.createDirectoryAtURL(directory, true, null, null)
        return directory
    }

    private fun entries(path: String): List<String> =
        (NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null) ?: emptyList<Any?>())
            .filterIsInstance<String>()

    private fun directory(messageId: String): NSURL =
        folder().URLByAppendingPathComponent(messageId, isDirectory = true)!!

    /**
     * One directory per message holding a single readable file. The id has to
     * stay out of the filename: that name is what the recipient sees in
     * WhatsApp, and a UUID in it looks like junk mail.
     */
    private fun urlFor(messageId: String): NSURL? {
        val directory = directory(messageId)
        // Skips the removed marker, and anything else hidden.
        val name = entries(directory.path.orEmpty()).firstOrNull { !it.startsWith(".") } ?: return null
        return directory.URLByAppendingPathComponent(name)
    }

    actual fun save(messageId: String, filename: String, bytes: ByteArray): String {
        NSFileManager.defaultManager.removeItemAtURL(directory(messageId), null)
        val directory = directory(messageId)
        NSFileManager.defaultManager.createDirectoryAtURL(directory, true, null, null)
        val url = directory.URLByAppendingPathComponent(filename)!!
        bytes.toNSData().writeToURL(url, true)
        return url.path.orEmpty()
    }

    actual fun isSaved(messageId: String): Boolean = urlFor(messageId) != null

    actual fun delete(messageId: String) {
        val directory = directory(messageId)
        NSFileManager.defaultManager.removeItemAtURL(directory, null)
        NSFileManager.defaultManager.createDirectoryAtURL(directory, true, null, null)
        NSFileManager.defaultManager.createFileAtPath(
            directory.URLByAppendingPathComponent(REMOVED_MARKER)!!.path.orEmpty(),
            null,
            null,
        )
    }

    actual fun wasRemoved(messageId: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(
            directory(messageId).URLByAppendingPathComponent(REMOVED_MARKER)!!.path.orEmpty(),
        )

    actual fun deleteAll() {
        NSFileManager.defaultManager.removeItemAtURL(folder(), null)
    }

    actual fun savedIds(): Set<String> =
        entries(folder().path.orEmpty())
            .filter { urlFor(it) != null }
            .toSet()

    actual fun share(messageId: String) {
        val url = urlFor(messageId) ?: return
        val application = UIApplication.sharedApplication
        val root = application.windows
            .filterIsInstance<platform.UIKit.UIWindow>()
            .firstOrNull { it.isKeyWindow() }
            ?.rootViewController
            ?: return

        val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        // An iPad would need a popover anchor here; this app is phone-only.
        root.presentViewController(sheet, animated = true, completion = null)
    }
}
