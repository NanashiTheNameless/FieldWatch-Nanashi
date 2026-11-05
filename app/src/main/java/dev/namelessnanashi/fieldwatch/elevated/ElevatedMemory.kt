package dev.namelessnanashi.fieldwatch.elevated

import java.io.File

/**
 * What this phone last said about root, and which shell the user picked, kept across launches so
 * the Elevated page does not need Check root after every start. Check root again to refresh it.
 * Per phone: files in the app's private storage, never part of a settings pack.
 */
object ElevatedMemory {
    private const val ROOT = "elevated-root.txt"
    private const val BACKEND = "elevated-backend.txt"

    /** True or false from the last Check root; null when it has never run. */
    fun root(filesDir: File): Boolean? = when (read(filesDir, ROOT)) {
        "yes" -> true
        "no" -> false
        else -> null
    }

    fun setRoot(filesDir: File, available: Boolean) = write(filesDir, ROOT, if (available) "yes" else "no")

    /** The last shell picked on the Elevated page, by name; null when none was saved. */
    fun backend(filesDir: File): String? = read(filesDir, BACKEND)

    fun setBackend(filesDir: File, name: String) = write(filesDir, BACKEND, name)

    private fun read(filesDir: File, name: String): String? =
        runCatching { File(filesDir, name).takeIf { it.isFile }?.readText()?.trim() }.getOrNull()

    private fun write(filesDir: File, name: String, value: String) {
        runCatching { File(filesDir, name).writeText(value) }
    }
}
