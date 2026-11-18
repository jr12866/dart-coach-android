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
     * reverb and 0.35 seconds was right. On a phone it depends where the
     * phone is: on a quiet desk the speaker's mechanical ringing is over
     * in 50 - 100 ms, but at the oche it measured 370 - 835 ms. See
     * `docs/TAIL.md`.
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

        // The tail is the time from the end of the audio until the coach's
        // voice falls back to the room.
        //
        // The measure is a running median of the recent slices, not the
        // peak of a single slice. Taking the last slice above the room
        // meant a single noise spike in a quiet room counted as "still
        // ringing" for as long as it happened to be late - which is how a
        // 100 ms tail came out as 1400 ms.
        //
        // A real acoustic tail decays smoothly, so the median follows it
        // down. Noise does not.
        val sliceMs = 1000.0 * buffer.size / 16000
        val speechEnd = samples.size * 1000.0 / rate

        // The reference is the speech, not the room.
        //
        // The room is noisy enough to produce spikes well above its own
        // floor: the measured curve shows the level back at the room
        // within 100 ms of the audio stopping, and then noise spiking to
        // 0.04 and 0.049 over the next second. Those spikes are the room,
        // and comparing against the room counted them as a tail.
        //
        // So the reference is how loud the speech was. The tail is the time
        // for the level to fall from there down to the room, and the way to
        // find it is the decay: the point where the level drops sharply and
        // stays down.
        val speechLevels = levels.filterIndexed { index, _ ->
            index * sliceMs < speechEnd
        }
        val speechLevel = if (speechLevels.isEmpty()) {
            roomLevel
        } else {
            speechLevels.sorted()[speechLevels.size / 2]
        }
        // A tenth of the speech is well above the room and well below the
        // speech, so it is the point where the voice has gone.
        val margin = (speechLevel * 0.1f).coerceAtLeast(roomLevel * 3f + 0.004f)

        // Find the decay: the last stretch where the level was still up.
        // A running median over 70 ms ignores the isolated spikes.
        var tailMs = 0.0
        var index = 0
        val recent = ArrayDeque<Float>()
        for (level in levels) {
            val at = index * sliceMs
            index++
            if (at < speechEnd) continue

            recent.addLast(level)
            while (recent.size > 7) {
                recent.removeFirst()
            }
            val sorted = recent.sorted()
            val median = sorted[sorted.size / 2]
            if (median > margin) {
                tailMs = at - speechEnd + sliceMs
            }
        }

        Log.i(
            "DartsCoach",
            String.format(
                "TAIL: room=%.5f speechLevel=%.5f margin=%.5f " +
                    "speech=%.0fms tail=%.0fms (%.3fs) over %d slices",
                roomLevel, speechLevel, margin,
                speechEnd, tailMs, tailMs / 1000.0, levels.size,
            ),
        )

        // The curve, so the shape can be seen rather than only the total.
        // A real acoustic tail falls away smoothly. A step up, or a level
        // that stays flat, is something else - noise, or the detection
        // picking up the room in a quiet room.
        val curve = StringBuilder()
        for (i in levels.indices) {
            if (i % 5 != 0) continue    // every 50 ms
            val at = i * sliceMs
            curve.append(String.format("%.0f:%.4f ", at, levels[i]))
        }
        Log.i("DartsCoach", "CURVE $curve")
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
     * Plays speech without the caller gating the microphone.
     *
     * This is only used by the self-hearing test, where the point is for the
     * app to hear itself through the speaker. In normal use the microphone
     * is gated while the coach speaks, so that it does not hear its own
     * voice.
     */
    fun speakUngated(text: String): Double {
        val made = synthesize(text) ?: return 0.0
        val (samples, rate) = made

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

        return try {
            val started = System.currentTimeMillis()
            track.play()
            var offset = 0
            while (offset < samples.size) {
                val chunk = minOf(1024, samples.size - offset)
                track.write(samples, offset, chunk, AudioTrack.WRITE_BLOCKING)
                offset += chunk
            }

            // Wait for the device to actually finish playing, the same way
            // speak() does.
            //
            // The buffer here is the whole utterance, so every write returns
            // at once and the audio is still sitting in the track when they
            // are done. Stopping it then cuts the phrase off: the phone
            // plays the first fraction of it and the rest never reaches the
            // speaker. That is why the self-hearing test heard "one" for
            // "one hundred and forty" - it was hearing the first third of
            // the phrase and nothing else.
            val millis = samples.size * 1000L / rate
            val deadline = started + millis + DRAIN_GRACE_MS
            while (System.currentTimeMillis() < deadline) {
                if (track.playbackHeadPosition >= samples.size) break
                Thread.sleep(20)
            }
            Thread.sleep(DRAIN_GRACE_MS)

            val total = System.currentTimeMillis() - started
            Log.i(
                "DartsCoach",
                "TTS (ungated): ${samples.size} samples at $rate Hz = " +
                    "${millis}ms, played in ${total}ms",
            )
            return total / 1000.0
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }

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