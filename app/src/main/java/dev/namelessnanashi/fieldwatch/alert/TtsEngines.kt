package dev.namelessnanashi.fieldwatch.alert

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech

/** Speech engines: the ones installed (via the manifest's TTS_SERVICE query) plus a known list. */
object TtsEngines {
    /**
     * An engine with explicit support: named, linked when missing, and given extra time when its
     * neural model has to load before the first word.
     */
    data class Known(
        val packageName: String,
        val name: String,
        val url: String,
        /** On-device neural model; the first phrase after start can take several seconds. */
        val slowStart: Boolean,
    )

    val KNOWN: List<Known> = listOf(
        Known(
            "com.google.android.tts",
            "Google Speech Services",
            "https://play.google.com/store/apps/details?id=com.google.android.tts",
            slowStart = false,
        ),
        Known(
            "com.github.olga_yakovleva.rhvoice.android",
            "RHVoice",
            "https://f-droid.org/packages/com.github.olga_yakovleva.rhvoice.android/",
            slowStart = false,
        ),
        Known(
            "org.woheller69.ttsengine",
            "SherpaTTS",
            "https://f-droid.org/packages/org.woheller69.ttsengine/",
            slowStart = true,
        ),
        Known(
            "com.brahmadeo.supertonic.tts",
            "Supertonic TTS",
            "https://f-droid.org/packages/com.brahmadeo.supertonic.tts/",
            slowStart = true,
        ),
        Known(
            "app.marmalade.tts",
            "Marmalade TTS",
            "https://f-droid.org/packages/app.marmalade.tts/",
            slowStart = true,
        ),
        Known(
            "dev.ahmedmohamed.hayaitts",
            "HayaiTTS",
            "https://github.com/HayaiApp/HayaiTTS",
            slowStart = true,
        ),
    )

    private val byPackage = KNOWN.associateBy { it.packageName }

    data class Engine(val packageName: String, val label: String, val installed: Boolean, val known: Known?)

    fun known(packageName: String): Known? = byPackage[packageName]

    fun slowStart(packageName: String): Boolean = byPackage[packageName]?.slowStart == true

    /** Installed engines first (known names win), then known engines that are not installed. */
    fun all(context: Context): List<Engine> {
        val pm = context.packageManager
        val installed = pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { it.serviceInfo }
            .distinctBy { it.packageName }
            .map { info ->
                val known = byPackage[info.packageName]
                Engine(
                    info.packageName,
                    known?.name ?: info.loadLabel(pm).toString().ifBlank { info.packageName },
                    installed = true,
                    known = known,
                )
            }
            .sortedBy { it.label.lowercase() }
        val present = installed.map { it.packageName }.toSet()
        val missing = KNOWN.filter { it.packageName !in present }
            .map { Engine(it.packageName, it.name, installed = false, known = it) }
        return installed + missing
    }

    /** The engine's own app, where its voices are downloaded and set up. */
    fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
