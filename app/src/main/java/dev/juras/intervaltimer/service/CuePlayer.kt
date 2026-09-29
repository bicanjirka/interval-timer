package dev.juras.intervaltimer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.juras.intervaltimer.data.Settings
import dev.juras.intervaltimer.engine.PhaseKind
import kotlin.math.PI
import kotlin.math.sin

/** A short sequence of tones. A frequency of 0 is silence. */
enum class Cue(vararg val notes: Pair<Int, Int>) {
    TICK(880 to 90),
    GET_READY(660 to 200),
    WORK(988 to 120, 0 to 40, 1319 to 260),
    REST(523 to 420),
    COOL_DOWN(587 to 300),
    DONE(784 to 150, 0 to 30, 988 to 150, 0 to 30, 1319 to 150, 0 to 30, 1568 to 500);

    val durationMs: Int get() = notes.sumOf { it.second }

    companion object {
        fun startOf(kind: PhaseKind) = when (kind) {
            PhaseKind.WARM_UP -> GET_READY
            PhaseKind.WORK -> WORK
            PhaseKind.REST -> REST
            PhaseKind.COOL_DOWN -> COOL_DOWN
        }
    }
}

/**
 * Signals cues by beep, speech and/or vibration, depending on [Settings.mode]. Sound plays at the
 * app's own volume; with ducking on, other audio dips (audio focus with GAIN_TRANSIENT_MAY_DUCK)
 * only while a cue is sounding. Vibration-only mode makes no sound and takes no audio focus.
 * Main thread only.
 */
class CuePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val vibrator = appContext.getSystemService(VibratorManager::class.java).defaultVibrator
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var settings = Settings()
    private var focus: AudioFocusRequest? = null
    private var holds = 0
    private val speaking = mutableSetOf<String>()
    private var utterance = 0
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts.setAudioAttributes(attributes)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finished(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
        })
    }

    fun apply(settings: Settings) {
        this.settings = settings
    }

    fun play(cue: Cue) {
        if (settings.mode.vibrate) vibrate(cue)
        if (settings.mode.sound) beep(cue)
    }

    /** Pulses follow the cue's notes; gaps are stretched so they can be felt. */
    private fun vibrate(cue: Cue) {
        val timings = mutableListOf(0L) // a waveform alternates off, on, off, on, ...
        var lastWasPulse = false
        for ((hz, ms) in cue.notes) {
            val pulse = hz > 0
            if (!pulse && timings.size == 1) continue
            if (pulse && lastWasPulse) timings += MIN_VIBRATION_GAP_MS.toLong()
            timings += if (pulse) ms.toLong() else maxOf(ms, MIN_VIBRATION_GAP_MS).toLong()
            lastWasPulse = pulse
        }
        vibrator.vibrate(
            VibrationEffect.createWaveform(timings.toLongArray(), -1),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun beep(cue: Cue) {
        val pcm = render(cue)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setVolume(settings.volume)
        hold()
        track.play()
        handler.postDelayed({
            track.release()
            release()
        }, cue.durationMs + TAIL_MS)
    }

    /** Reads [text] aloud after [delayMs] (so a start beep is not talked over). */
    fun speak(text: String, delayMs: Long = 0) {
        if (!settings.voice || !settings.mode.sound) return
        handler.postDelayed({
            if (!ttsReady) return@postDelayed
            val id = "u${utterance++}"
            speaking += id
            hold()
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume) }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) finished(id)
        }, delayMs)
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        tts.shutdown()
        holds = 0
        abandonFocus()
    }

    private fun finished(id: String) {
        handler.post { if (speaking.remove(id)) release() }
    }

    private fun hold() {
        if (holds++ == 0 && settings.ducking) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            focus = request
            audioManager.requestAudioFocus(request)
        }
    }

    private fun release() {
        if (holds > 0 && --holds == 0) abandonFocus()
    }

    private fun abandonFocus() {
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun render(cue: Cue): ShortArray {
        val out = ArrayList<Short>()
        for ((hz, ms) in cue.notes) {
            val n = SAMPLE_RATE * ms / 1000
            val ramp = (SAMPLE_RATE * RAMP_MS / 1000).coerceAtMost(n / 2).coerceAtLeast(1)
            for (i in 0 until n) {
                val envelope = minOf(1.0, i.toDouble() / ramp, (n - 1 - i).toDouble() / ramp)
                val sample = if (hz == 0) 0.0 else sin(2 * PI * hz * i / SAMPLE_RATE) * envelope * AMPLITUDE
                out += (sample * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val RAMP_MS = 8
        const val AMPLITUDE = 0.9
        const val TAIL_MS = 150L
        const val MIN_VIBRATION_GAP_MS = 90
    }
}
