package com.jr12866.dartcoach.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File

/**
 * The coach's voice.
 *
 * Piper through sherpa-onnx. The model was trained on phoneme codes rather
 * than on text, so the text is phonemised first by the piper-phonemize
 * library, which bundles its own copy of the espeak-ng data.
 *
 * The espeak-ng directory that ships with the voice is **not** used.
 * sherpa-onnx has ignored `data_dir` since v2.0.0, and the upstream build
 * script deletes it for the same reason.
 */
class PiperTts(private val appContext: Context) {

    private var tts: OfflineTts? = null

    /** The sample rate the voice runs at. 22050 for the Piper voices. */
    val sampleRate: Int
        get() = tts?.sampleRate() ?: 22050

    /**
     * Loads the voice.
     *
     * Slow the first time: the ONNX model has to be read. Returns false if
     * the voice is not available, so the coach can still work without it.
     */
    fun load(): Boolean {
        if (tts != null) return true
        return try {
            piperPhonemizeInitialize(appContext)
            val config = getOfflineTtsConfig(
                modelDir = MODEL_DIR,
                modelName = MODEL_NAME,
            )
            tts = OfflineTts(appContext.assets, config)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Synthesises speech.
     *
     * Returns the samples and the rate they are meant to be played at, or
     * null if the voice could not do it.
     */
    fun synthesize(text: String): Pair<FloatArray, Int>? {
        if (!load()) return null
        return try {
            val phonemes = piperPhonemizeText(text, "en-us") ?: return null
            try {
                val codes = Array(phonemes.numSentences) { i -> phonemes.getPhonemes(i) }
                val audio = tts!!.generateWithConfig(
                    text,
                    GenerationConfig(phonemeCodepoints = codes),
                )
                audio.samples to tts!!.sampleRate()
            } finally {
                phonemes.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Plays speech, blocking until it has finished.
     *
     * Returns the number of seconds it played for, which the caller uses to
     * decide how long to keep the microphone muted.
     */
    fun speak(text: String): Double {
        val made = synthesize(text) ?: return 0.0
        val (samples, rate) = made

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            // MODE_STATIC blocks on write, so the audio has already been
            // queued by the time play() returns. Wait for it to drain.
            val millis = samples.size * 1000L / rate
            Thread.sleep(millis + 100)
            return millis / 1000.0
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    companion object {
        /** The voice lives in its own directory, with its tokens file beside it. */
        const val MODEL_DIR = "vits-piper-en_US-lessac-medium-int8"
        const val MODEL_NAME = "en_US-lessac-medium.onnx"
    }
}