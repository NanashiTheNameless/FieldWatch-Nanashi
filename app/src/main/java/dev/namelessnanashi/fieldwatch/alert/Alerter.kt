package dev.namelessnanashi.fieldwatch.alert

import dev.namelessnanashi.fieldwatch.domain.LanguageText

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sin
import androidx.core.app.NotificationCompat
import dev.namelessnanashi.fieldwatch.MainActivity
import dev.namelessnanashi.fieldwatch.R
import dev.namelessnanashi.fieldwatch.domain.AlertOtherAudio
import dev.namelessnanashi.fieldwatch.domain.AlertVoiceWhat
import dev.namelessnanashi.fieldwatch.domain.Fleet
import dev.namelessnanashi.fieldwatch.domain.MacUtil
import dev.namelessnanashi.fieldwatch.domain.RadioBookmarks
import dev.namelessnanashi.fieldwatch.domain.Sighting
import dev.namelessnanashi.fieldwatch.domain.WatchTarget
import dev.namelessnanashi.fieldwatch.domain.spokenWatchPhrase
import dev.namelessnanashi.fieldwatch.domain.testWatchPhrase
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class Alerter(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val announced = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var beepTrack: AudioTrack? = null
    private var beepPlayer: MediaPlayer? = null
    /** Chosen alert sound; null plays the built-in double pip. */
    @Volatile private var alertSound: Uri? = null
    private var huntTrack: AudioTrack? = null
    /** Held from the alert's first sound until its phrase ends, so other audio stays down throughout. */
    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var otherAudio = AlertOtherAudio.LOWER
    private val focusCap = Runnable { releaseFocus() }
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    /** Package of the chosen speech engine; blank uses the system default. */
    private var ttsEnginePackage = ""
    /** Voice name within that engine; blank uses the engine's voice for the phone language. */
    private var ttsVoiceName = ""
    private val voiceList = MutableStateFlow<List<VoiceOption>>(emptyList())
    /** Installed voices of the current engine, filled once the engine has started. */
    val voices: StateFlow<List<VoiceOption>> = voiceList.asStateFlow()

    /** Silence played before alert audio so a sleeping Bluetooth link is awake for the real sound. */
    @Volatile private var leadInMs = DEFAULT_LEAD_IN_MS
    private val audioWakeup = AudioWakeup(
        now = SystemClock::elapsedRealtime,
        later = { delay, action -> main.postDelayed({ action() }, delay) },
        open = ::openWakeAudio,
    )
    private var speaking = false
    private var utteranceStarted = false
    private var ttsSpeakTries = 0
    private var pendingSpeak: String? = null
    private var speakAfterBeep: String? = null
    private val speakWatchdog = Runnable {
        if (!speaking) return@Runnable
        val retry = pendingSpeak
        speaking = false
        utteranceStarted = false
        if (retry != null && ttsSpeakTries < 2) speakClassName(retry) else releaseFocusIfIdle()
    }
    private val _flashes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val flashes: SharedFlow<String> = _flashes.asSharedFlow()

    init {
        ensureChannel()
    }

    fun checkLive(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        alertsOn: Boolean,
        beepOn: Boolean,
        voiceOn: Boolean = false,
        voiceWhat: AlertVoiceWhat = AlertVoiceWhat.CLASS,
        shadeOn: Boolean = false,
        visibleOnLive: (Sighting) -> Boolean = { !it.gone },
        arrivalsOnly: Boolean = false,
        demoMode: Boolean = false,
    ) {
        if (!alertsOn || watchlist.isEmpty()) return
        fireNewAppearances(
            devices, fleets, watchlist, beepOn, voiceOn, voiceWhat, shadeOn,
            visibleOnLive, arrivalsOnly, demoMode,
        )
    }

    fun playTestBeep(
        beepOn: Boolean = true,
        speakClass: Boolean = false,
        voiceWhat: AlertVoiceWhat = AlertVoiceWhat.CLASS,
    ) {
        if (speakClass) prepareVoice()
        if (beepOn) {
            beep()
            vibrate()
        }
        if (speakClass) queueVoice(testWatchPhrase(voiceWhat), afterBeep = beepOn)
    }

    /**
     * Switches the speech engine for spoken alerts. The current engine is shut down and the
     * chosen one starts on the next prepare or alert. A missing engine falls back to the default.
     */
    fun setTtsEngine(enginePackage: String) {
        val next = enginePackage.trim()
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { setTtsEngine(next) }
            return
        }
        if (next == ttsEnginePackage) return
        ttsEnginePackage = next
        voiceList.value = emptyList()
        main.removeCallbacks(speakWatchdog)
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        speaking = false
        utteranceStarted = false
    }

    /** Picks a voice by name. Applies now if the engine is running, otherwise when it starts. */
    fun setTtsVoice(name: String) {
        val next = name.trim()
        main.post {
            ttsVoiceName = next
            val engine = tts ?: return@post
            if (ttsReady) applyVoice(engine)
        }
    }

    fun setLeadIn(ms: Int) {
        leadInMs = ms.coerceIn(0, MAX_LEAD_IN_MS)
    }

    fun setOtherAudio(mode: AlertOtherAudio) {
        otherAudio = mode
    }

    /** Warm the TTS engine on the main thread so the first alert is not silent. */
    fun prepareVoice() {
        if (Looper.myLooper() == Looper.getMainLooper()) ensureTts()
        else main.post { ensureTts() }
    }

    /**
     * GNSS interference or spoofing. Own notification channel so it can be muted
     * without muting the watchlist. Phrase is null when voice is off.
     * Text must not include coordinates.
     */
    fun gnssAlert(title: String, text: String, phrase: String?, beep: Boolean, shade: Boolean) {
        if (shade) {
            ensureGnssChannel()
            val pending = PendingIntent.getActivity(
                context,
                GNSS_NOTE_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, GNSS_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_fieldwatch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(GNSS_NOTE_ID, note)
        } else {
            manager.cancel(GNSS_NOTE_ID)
        }
        if (beep) beep()
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
    }

    fun clearGnssNote() {
        manager.cancel(GNSS_NOTE_ID)
    }

    /** Short Hunt geiger tick. Not the watchlist double-pip. */
    fun huntTick(beepOn: Boolean, vibrateOn: Boolean) {
        if (vibrateOn) main.post { huntPulse() }
        if (beepOn) main.post { playHuntClick() }
    }

    fun forgetAnnounced() {
        announced.clear()
    }

    private fun fireNewAppearances(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        beepOn: Boolean,
        voiceOn: Boolean,
        voiceWhat: AlertVoiceWhat,
        shadeOn: Boolean,
        visibleOnLive: (Sighting) -> Boolean,
        arrivalsOnly: Boolean,
        demoMode: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val onAir = devices.filter { !it.gone }
        val liveKeys = onAir.map { it.key }.toSet()
        announced.removeAll { token -> token.substringAfter('\t') !in liveKeys }
        val fleetNames = fleets.associateBy { it.id }
        val mineKeys = RadioBookmarks.mineKeys(watchlist)
        var beepFired = false
        var voicePhrase: String? = null
        if (voiceOn) prepareVoice()
        for (target in watchlist) {
            for (device in onAir) {
                if (!shouldRaiseWatch(target, device, mineKeys)) continue
                if (!visibleOnLive(device)) continue
                val token = "${target.id}\t${device.key}"
                if (token in announced) continue
                if (!arrivalsOnly && !isNewAppearance(device, now)) continue
                announced.add(token)
                val label = MacUtil.redactMacIn(
                    target.label.ifBlank {
                        fleetNames[target.fleetId]?.name ?: device.displayName
                    },
                    device.mac,
                    demoMode,
                )
                if (shadeOn && target.notify) notify(label, device, demoMode)
                if (target.vibrate) vibrate()
                if (beepOn && !beepFired) {
                    beep()
                    beepFired = true
                }
                if (beepOn || voiceOn) _flashes.tryEmit(device.key)
                if (voiceOn && voicePhrase == null) {
                    voicePhrase = spokenWatchPhrase(device, fleets, voiceWhat, target)
                }
            }
        }
        if (voicePhrase != null) queueVoice(voicePhrase, afterBeep = beepFired)
    }

    private fun queueVoice(text: String, afterBeep: Boolean) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        if (afterBeep) {
            speakAfterBeep = phrase
        } else {
            main.post {
                takeFocus()
                afterWake { speakClassName(phrase) }
            }
        }
    }

    private fun isNewAppearance(device: Sighting, now: Long): Boolean {
        if (now - device.firstSeen <= NEW_WINDOW_MS) return true
        val spanStart = device.presence.lastOrNull()?.start ?: device.firstSeen
        return now - spanStart <= NEW_WINDOW_MS
    }

    private fun notify(label: String, device: Sighting, demoMode: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEVICE_KEY, device.key)
        }
        val pending = PendingIntent.getActivity(
            context,
            device.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(LanguageText.text("ui.fieldwatch.watchlist"))
            .setContentText("$label  ${device.rssi} dBm  ${MacUtil.screenMac(device.mac, demoMode)}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$label appeared\n${device.kind}  ${MacUtil.screenMac(device.mac, demoMode)}\n${device.rssi} dBm  ${MacUtil.redactMacIn(device.displayName, device.mac, demoMode)}",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
        manager.notify(device.key.hashCode() and 0x7FFFFFFF, note)
    }

    /** Sound for alert beeps: a content or file URI, or blank for the built-in double pip. */
    fun setAlertSound(uri: String) {
        alertSound = uri.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    private fun beep() {
        main.post {
            val sound = alertSound
            if (sound == null) playSynthBeep() else playSoundBeep(sound)
        }
    }

    /** Plays the chosen sound, at most [CUSTOM_SOUND_MAX_MS]; falls back to the double pip on error. */
    private fun playSoundBeep(sound: Uri) {
        stopBeep()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val player = MediaPlayer()
        val done = Runnable {
            if (beepPlayer === player) {
                stopBeep()
                finishBeep()
            }
        }
        val started = runCatching {
            player.setAudioAttributes(attrs)
            player.setDataSource(context, sound)
            player.setOnCompletionListener {
                main.removeCallbacks(done)
                done.run()
            }
            player.setOnErrorListener { _, _, _ ->
                main.removeCallbacks(done)
                if (beepPlayer === player) {
                    stopBeep()
                    playSynthBeep()
                }
                true
            }
            player.setOnPreparedListener {
                if (beepPlayer !== player) return@setOnPreparedListener
                takeFocus()
                // Preparation and focus changes finish before warming the actual playback route.
                afterWake {
                    if (beepPlayer === player) {
                        runCatching {
                            player.start()
                            main.postDelayed(done, CUSTOM_SOUND_MAX_MS)
                        }.onFailure {
                            stopBeep()
                            playSynthBeep()
                        }
                    }
                }
            }
            beepPlayer = player
            player.prepareAsync()
        }.isSuccess
        if (!started) {
            runCatching { player.release() }
            if (beepPlayer === player) beepPlayer = null
            playSynthBeep()
        }
    }

    private fun playSynthBeep() {
        runCatching {
            stopBeep()
            val sampleRate = 22_050
            val pipSamples = sampleRate * PIP_MS / 1000
            val gapSamples = sampleRate * GAP_MS / 1000
            // Silence and both pips share one track, so the route cannot sleep between them.
            val leadSamples = sampleRate * leadInMs / 1000
            val total = leadSamples + pipSamples + gapSamples + pipSamples
            val pcm = ShortArray(total)
            writeSine(pcm, leadSamples, pipSamples, sampleRate, FREQ_HZ)
            writeSine(pcm, leadSamples + pipSamples + gapSamples, pipSamples, sampleRate, FREQ_HZ)

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(maxOf(total * 2, minBuf))
                .build()
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) {
                track.release()
                finishBeep()
                return@runCatching
            }
            takeFocus()
            beepTrack = track
            track.play()
            val playMs = total * 1000L / sampleRate
            val deadline = SystemClock.elapsedRealtime() + playMs + 2_000L
            fun finishWhenPlayed() {
                if (beepTrack !== track) return
                val frames = runCatching { track.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(0L)
                if (frames < total && SystemClock.elapsedRealtime() < deadline) {
                    main.postDelayed({ finishWhenPlayed() }, 25L)
                } else {
                    stopBeep()
                    finishBeep()
                }
            }
            main.postDelayed({ finishWhenPlayed() }, playMs + 40L)
        }.onFailure {
            finishBeep()
        }
    }

    private fun stopBeep() {
        val track = beepTrack
        beepTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        val player = beepPlayer
        beepPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    /**
     * Lowers or pauses other apps for the whole alert, pip through phrase. One request covers
     * overlapping alerts. Android ducks only the apps that lose focus, so the speech engine's
     * own process keeps full volume. Leave alone skips focus for engines that misbehave.
     */
    private fun takeFocus() {
        val gain = when (otherAudio) {
            AlertOtherAudio.LOWER -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            AlertOtherAudio.PAUSE -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            AlertOtherAudio.OFF -> return
        }
        main.removeCallbacks(focusCap)
        main.postDelayed(focusCap, FOCUS_CAP_MS)
        if (focusRequest != null) return
        val req = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .build()
        if (runCatching { audio.requestAudioFocus(req) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = req
        }
    }

    private fun releaseFocus() {
        main.removeCallbacks(focusCap)
        val req = focusRequest ?: return
        focusRequest = null
        runCatching { audio.abandonAudioFocusRequest(req) }
    }

    /** Gives focus back once no alert sound or phrase is playing or queued. */
    private fun releaseFocusIfIdle() {
        if (beepTrack != null || beepPlayer != null || speakAfterBeep != null) return
        if (speaking || pendingSpeak != null) return
        releaseFocus()
    }

    private fun finishBeep() {
        val phrase = speakAfterBeep
        if (phrase == null) {
            releaseFocusIfIdle()
            return
        }
        speakAfterBeep = null
        main.postDelayed({ speakClassName(phrase) }, VOICE_AFTER_TRACK_MS)
    }

    private fun writeSine(dest: ShortArray, offset: Int, n: Int, sampleRate: Int, freqHz: Double) {
        val step = 2.0 * Math.PI * freqHz / sampleRate
        val attack = (sampleRate * 0.004).toInt().coerceAtLeast(1)
        val release = (sampleRate * 0.008).toInt().coerceAtLeast(1)
        val peak = Short.MAX_VALUE * 0.62
        for (i in 0 until n) {
            val env = when {
                i < attack -> i.toFloat() / attack
                i > n - release -> (n - i).toFloat() / release
                else -> 1f
            }.coerceIn(0f, 1f)
            dest[offset + i] = (sin(step * i) * peak * env).toInt().toShort()
        }
    }

    private fun playHuntClick() {
        runCatching {
            stopHuntClick()
            val sampleRate = 22_050
            val n = sampleRate * HUNT_PIP_MS / 1000
            val pcm = ShortArray(n)
            writeSine(pcm, 0, n, sampleRate, HUNT_FREQ_HZ)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(maxOf(n * 2, minBuf))
                .build()
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) {
                track.release()
                return@runCatching
            }
            huntTrack = track
            track.play()
            val playMs = n * 1000L / sampleRate
            main.postDelayed({
                if (huntTrack === track) stopHuntClick()
            }, playMs + 20L)
        }
    }

    private fun stopHuntClick() {
        val track = huntTrack
        huntTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun huntPulse() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        val amp = if (vibrator.hasAmplitudeControl()) 255 else VibrationEffect.DEFAULT_AMPLITUDE
        val effect = VibrationEffect.createOneShot(HUNT_PULSE_MS, amp)
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun speakClassName(text: String) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        pendingSpeak = phrase
        ensureTts()
        if (!ttsReady) return
        startSpeak(phrase)
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(context, { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) {
                main.post {
                    pendingSpeak = null
                    releaseFocusIfIdle()
                }
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            applyVoice(engine)
            voiceList.value = installedVoices(engine)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceStarted = true
                    speaking = true
                    main.removeCallbacks(speakWatchdog)
                    main.postDelayed(speakWatchdog, speakTimeoutMs())
                }
                override fun onDone(utteranceId: String?) {
                    speaking = false
                    utteranceStarted = false
                    pendingSpeak = null
                    ttsSpeakTries = 0
                    main.removeCallbacks(speakWatchdog)
                    main.post { releaseFocusIfIdle() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speaking = false
                    utteranceStarted = false
                    pendingSpeak = null
                    main.removeCallbacks(speakWatchdog)
                    main.post { releaseFocusIfIdle() }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    speaking = false
                    utteranceStarted = false
                    pendingSpeak = null
                    main.removeCallbacks(speakWatchdog)
                    main.post { releaseFocusIfIdle() }
                }
            })
            // Samsung often drops the first speak() if it runs inside onInit.
            main.postDelayed({
                pendingSpeak?.let { startSpeak(it) }
            }, TTS_AFTER_INIT_MS)
        }, ttsEnginePackage.ifBlank { null })
    }

    /** The chosen voice when the engine has it; otherwise the engine's voice for the phone language. */
    private fun applyVoice(engine: TextToSpeech) {
        val chosen = ttsVoiceName.takeIf { it.isNotEmpty() }?.let { name ->
            runCatching { engine.voices?.firstOrNull { it.name == name } }.getOrNull()
        }
        if (chosen != null && runCatching { engine.voice = chosen }.isSuccess) return
        runCatching {
            val lang = engine.setLanguage(Locale.getDefault())
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.US
            }
        }
    }

    /** Voices that are installed, phone language first, then by language and name. */
    private fun installedVoices(engine: TextToSpeech): List<VoiceOption> {
        val phone = Locale.getDefault().language
        return runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty() }
            .map { v ->
                VoiceOption(
                    name = v.name,
                    language = v.locale.displayName,
                    phoneLanguage = v.locale.language == phone,
                    network = v.isNetworkConnectionRequired,
                )
            }
            .sortedWith(compareBy({ !it.phoneLanguage }, { it.language }, { it.name }))
    }

    /** Waits for rendered silence and overlaps the handoff to the prepared sound. */
    private fun afterWake(then: () -> Unit) = audioWakeup.run(leadInMs, then)

    private fun openWakeAudio(): AudioWakeup.Playback? {
        val sampleRate = 22_050
        val samples = sampleRate / 10
        var track: AudioTrack? = null
        return try {
            val output = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(maxOf(samples * 2, AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )))
                .build()
            track = output
            check(output.write(ShortArray(samples), 0, samples) == samples)
            check(output.setLoopPoints(0, samples, -1) == AudioTrack.SUCCESS)
            output.play()
            object : AudioWakeup.Playback {
                override val playedMs: Long
                    get() = (output.playbackHeadPosition.toLong() and 0xffffffffL) * 1000L / sampleRate
                override fun close() {
                    try { output.stop() } finally { output.release() }
                }
            }
        } catch (_: Exception) {
            runCatching { track?.release() }
            null
        }
    }

    private fun startSpeak(text: String) {
        val engine = tts ?: return
        if (!ttsReady) {
            pendingSpeak = text
            return
        }
        if (speaking && utteranceStarted) return
        pendingSpeak = text
        ttsSpeakTries++
        speaking = true
        utteranceStarted = false
        val ok = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
        if (ok != TextToSpeech.SUCCESS) {
            speaking = false
            if (ttsSpeakTries < 2) {
                main.postDelayed({ startSpeak(text) }, TTS_AFTER_INIT_MS)
            } else {
                pendingSpeak = null
                ttsSpeakTries = 0
                releaseFocusIfIdle()
            }
            return
        }
        main.removeCallbacks(speakWatchdog)
        main.postDelayed({
            if (speaking && !utteranceStarted && ttsSpeakTries < 2) {
                speaking = false
                startSpeak(text)
            } else if (speaking && !utteranceStarted) {
                speaking = false
                pendingSpeak = null
                ttsSpeakTries = 0
                releaseFocusIfIdle()
            }
        }, startWaitMs())
    }

    /** The engine actually in use: the chosen one, or the phone's default. */
    private fun activeEnginePackage(): String =
        ttsEnginePackage.ifBlank { runCatching { tts?.defaultEngine }.getOrNull().orEmpty() }

    /**
     * Neural engines (SherpaTTS, Supertonic, Marmalade, HayaiTTS) load a model before the first
     * word. Retrying too early flushes that load, so they get longer to start and to finish.
     */
    private fun startWaitMs(): Long =
        if (TtsEngines.slowStart(activeEnginePackage())) SLOW_START_WAIT_MS else TTS_START_WAIT_MS

    private fun speakTimeoutMs(): Long =
        if (TtsEngines.slowStart(activeEnginePackage())) SLOW_SPEAK_TIMEOUT_MS else SPEAK_TIMEOUT_MS

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching { manager.deleteNotificationChannel("fieldwatch_watch_v2") }
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Watchlist", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = LanguageText.text("ui.appearing.signatures.devices.watchlist")
                    enableVibration(true)
                    enableLights(true)
                    setSound(null, null)
                },
            )
        }
    }

    private fun ensureGnssChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(GNSS_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(GNSS_CHANNEL, LanguageText.text("settings.topic.gnss"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = LanguageText.text("ui.possible.gnss.interference.spoofing")
                setSound(null, null)
            },
        )
    }

    companion object {
        const val CHANNEL = "fieldwatch_watch_v3"
        const val GNSS_CHANNEL = "fieldwatch_gnss"
        private const val GNSS_NOTE_ID = 71_001
        const val EXTRA_DEVICE_KEY = "device_key"
        private const val FREQ_HZ = 1050.0
        private const val PIP_MS = 85
        private const val GAP_MS = 55
        const val DEFAULT_LEAD_IN_MS = 500
        const val MAX_LEAD_IN_MS = 1_000

        /** Longest a chosen alert sound plays before the spoken phrase may start. */
        private const val CUSTOM_SOUND_MAX_MS = 4_000L
        private const val NEW_WINDOW_MS = 20_000L
        private const val HUNT_FREQ_HZ = 1680.0
        private const val HUNT_PIP_MS = 22
        private const val HUNT_PULSE_MS = 60L
        private const val VOICE_AFTER_TRACK_MS = 220L
        private const val TTS_AFTER_INIT_MS = 250L
        private const val TTS_START_WAIT_MS = 1_500L
        private const val SPEAK_TIMEOUT_MS = 8_000L
        private const val SLOW_START_WAIT_MS = 15_000L
        private const val SLOW_SPEAK_TIMEOUT_MS = 30_000L
        private const val UTTERANCE = "fieldwatch-class"
        /** Last resort if an engine never reports the phrase ending; other audio must come back. */
        private const val FOCUS_CAP_MS = 45_000L
    }
}

/**
 * Mine skips beep, voice, flash, vibrate, and the shade card for that device key.
 * A fleet watch still matches every other radio. Alert on the row is left as set.
 */
internal fun shouldRaiseWatch(
    target: WatchTarget,
    device: Sighting,
    mineKeys: Set<String>,
): Boolean {
    if (device.key in mineKeys) return false
    if (target.deviceKey != null && !target.alert) return false
    return when {
        target.deviceKey != null -> device.key == target.deviceKey
        target.fleetId != null -> target.fleetId in device.fleetIds
        else -> false
    }
}

/** One installed speech voice, for the voice picker. */
data class VoiceOption(
    val name: String,
    val language: String,
    val phoneLanguage: Boolean,
    val network: Boolean,
)
