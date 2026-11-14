package com.jr12866.dartcoach.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioRecord
import android.util.Log
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
     * Measures the echo tail.
     *
     * The tail is how long the coach's own voice is still audible at the
     * microphone after the audio has stopped. On a laptop it was room
     * reverb and 0.35 seconds was right. On a phone the speaker is 10 cm
     * from the microphone, so the tail is the speaker's mechanical
     * ringing - much shorter and much sharper.
     *
     * The microphone is NOT gated during this, because the whole point is
     * to hear what the gate would be hiding.
     *
     * Returns the tail in seconds.
     */
    fun measureTail(
        phrase: String,
        recorder: AudioRecord,
        secondsAfter: Double = 1.5,
    ): Double {
        val made = synthesize(phrase) ?: return 0.0
        val (samples, rate) = made

        // The room, before the coach says anything.
        val room = mutableListOf<Float>()
        val buffer = FloatArray(160)   // 10 ms at 16 kHz
        repeat(50) {
            val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read > 0) room.add(meanLevel(buffer, read))
        }
        val roomLevel = if (room.isEmpty()) 0f else room.sorted()[room.size / 2]

        val minBuffer = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
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
            .setBufferSizeInBytes(maxOf(minBuffer, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val levels = mutableListOf<Float>()
        val started = System.currentTimeMillis()
        try {
            track.play()
            var offset = 0
            while (offset < samples.size) {
                val chunk = minOf(1024, samples.size - offset)
                track.write(samples, offset, chunk, AudioTrack.WRITE_BLOCKING)
                offset += chunk
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) levels.add(meanLevel(buffer, read))
            }
            // The tail: how long the voice is still there afterwards.
            val until = started + (samples.size * 1000L / rate) +
                (secondsAfter * 1000).toLong()
            while (System.currentTimeMillis() < until) {
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) levels.add(meanLevel(buffer, read))
            }
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }

        // The tail is the time from the end of the audio until the level
        // falls back to the room. A slice counts as "still ringing" if it
        // is above the room by a clear margin.
        val sliceMs = 1000.0 * buffer.size / 16000
        val speechEnd = samples.size * 1000.0 / rate
        var tailMs = 0.0
        var index = 0
        for (level in levels) {
            val at = index * sliceMs
            index++
            if (at < speechEnd) continue
            if (level > roomLevel * 2f + 0.001f) {
                tailMs = at - speechEnd + sliceMs
            }
        }

        Log.i(
            "DartsCoach",
            String.format(
                "TAIL: room=%.5f speech=%.0fms tail=%.0fms (%.3fs) over %d slices",
                roomLevel, speechEnd, tailMs, tailMs / 1000.0, levels.size,
            ),
        )
        return tailMs / 1000.0
    }

    /** The mean absolute level in the buffer. */
    private fun meanLevel(buffer: FloatArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += kotlin.math.abs(buffer[i])
        }
        return if (length > 0) (sum / length).toFloat() else 0f
    }

    /**
     * How long a line takes to say, without playing it.
     *
     * Used to measure the wording: the coach's lines are read at the oche
     * and a player waiting six seconds for a suggestion is waiting too long.
     * Returns the seconds of audio, or null if the voice could not do it.
     */
    fun measure(text: String): Double? {
        val made = synthesize(text) ?: return null
        val (samples, rate) = made
        return samples.size.toDouble() / rate
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
     * True while the voice is speaking.
     *
     * The activity watches this so that a player who starts talking can cut
     * the coach off.
     */
    @Volatile
    var speaking = false
        private set

    /** Stops the voice mid-sentence. */
    fun stop() {
        interrupted = true
    }

    /** True when the voice was cut off rather than finishing. */
    @Volatile
    private var interrupted = false

    /** True when the last speak() was cut off rather than finishing. */
    fun wasInterrupted(): Boolean = interrupted

    /**
     * Plays speech, blocking until it has finished.
     *
     * Returns the number of seconds it played for, which the caller uses to
     * decide how long to keep the microphone muted.
     */
    fun speak(text: String): Double {
        interrupted = false
        val made = synthesize(text) ?: return 0.0
        val (samples, rate) = made

        // MODE_STREAM, not MODE_STATIC.
        //
        // MODE_STATIC takes the whole blob in one write, and the tail gets
        // clipped: the buffer is sized from the sample count, the device
        // rounds it, and the last word - "right" in "Is that right?" - goes
        // with it. MODE_STREAM writes into a buffer the device owns, and
        // `play()` is called before the write so the audio starts flowing
        // while it is still being written.
        //
        // It is also what the Windows coach does: Piper's synthesize()
        // yields one chunk per sentence and each is written as it arrives,
        // so playback starts before the whole reply has been synthesised.
        val minBuffer = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) return 0.0

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
            .setBufferSizeInBytes(maxOf(minBuffer, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            val started = System.currentTimeMillis()
            track.play()

            // Write in slices. A single large write can block for a long
            // time and the whole thing is buffered before it plays; slices
            // let the device play the start while the rest is written.
            speaking = true
            var offset = 0
            while (offset < samples.size) {
                if (interrupted) {
                    Log.i("DartsCoach", "TTS: cut off after ${offset} samples")
                    break
                }
                val chunk = minOf(SLICE_SAMPLES, samples.size - offset)
                val written = track.write(
                    samples, offset, chunk, AudioTrack.WRITE_BLOCKING,
                )
                if (written <= 0) break
                offset += written
            }

            // Wait for the device to actually finish playing. Sleeping for
            // the length of the audio is close enough, but the device may be
            // behind, and the gate has to stay shut until the sound has
            // really stopped.
            val millis = samples.size * 1000L / rate
            val deadline = started + millis + DRAIN_GRACE_MS
            while (System.currentTimeMillis() < deadline) {
                if (interrupted) break
                if (track.playbackHeadPosition >= samples.size) break
                Thread.sleep(20)
            }
            // Let the tail through before the buffer is torn down.
            if (!interrupted) {
                Thread.sleep(DRAIN_GRACE_MS)
            }

            val total = System.currentTimeMillis() - started
            Log.i(
                "DartsCoach",
                "TTS: ${samples.size} samples at $rate Hz = ${millis}ms, " +
                    "played in ${total}ms",
            )
            return total / 1000.0
        } finally {
            speaking = false
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

        /** How much audio to write at a time, in samples. About 45 ms. */
        private const val SLICE_SAMPLES = 1024

        /**
         * How long to let the device drain after the audio has been played.
         *
         * The output buffer holds a little audio that has been played but
         * not yet heard. Tearing the track down without letting it drain
         * clips the last syllable, which is how "Is that right?" lost its
         * last word.
         */
        private const val DRAIN_GRACE_MS = 250L
    }
}