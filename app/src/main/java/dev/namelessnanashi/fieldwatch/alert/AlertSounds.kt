package dev.namelessnanashi.fieldwatch.alert

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Alert sound choices: the phone's own sounds, or one audio file copied into the app. */
object AlertSounds {
    data class Copied(val uri: String, val name: String)

    /** Larger files are refused; an alert sound only plays for a few seconds. */
    const val MAX_CUSTOM_BYTES = 5L * 1024 * 1024

    private fun dir(context: Context) = File(context.filesDir, "alert-sound")

    /** Copies [source] into app storage, replacing any earlier copy. Null when unreadable or too big. */
    fun importCustom(context: Context, source: Uri): Copied? {
        val name = displayName(context, source) ?: "Custom sound"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
        val folder = dir(context)
        val tmp = File(folder, "incoming.tmp")
        return runCatching {
            folder.mkdirs()
            val input = context.contentResolver.openInputStream(source) ?: return null
            var total = 0L
            input.use { stream ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_CUSTOM_BYTES) {
                            tmp.delete()
                            return null
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            if (total == 0L) {
                tmp.delete()
                return null
            }
            deleteCustom(context)
            folder.mkdirs()
            val dest = File(folder, if (ext != null) "custom.$ext" else "custom")
            if (!tmp.renameTo(dest)) return null
            Copied(Uri.fromFile(dest).toString(), name)
        }.getOrElse {
            tmp.delete()
            null
        }
    }

    fun deleteCustom(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    /** Title the phone gives a sound from its own picker. */
    fun titleOf(context: Context, uri: Uri): String =
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
