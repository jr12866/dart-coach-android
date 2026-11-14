package com.jr12866.dartcoach.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import android.util.Log
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getVadModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * The coach's ears: capture, find speech, transcribe.
 *
 * The microphone is open all the time. The silero VAD decides where the
 * phrases are, and Whisper transcribes each one.
 *
 * The half-duplex gate lives here too. While the coach is speaking the
 * microphone has to be ignored, because the coach would otherwise hear its
 * own voice and answer it.
 *
 * How much that matters depends on where the sound comes from. Measured on a
 * Pixel 7:
 *
 *   the phone's own speaker   echo peak 0.028, room floor 0.0037
 *   Bluetooth headphones      echo peak 0.0019, room floor 0.0037
 *
 * So on the phone's own speaker the coach's voice is loud enough to be
 * heard, and with headphones on it is not. The gate is there either way,
 * because the player might take the headphones off.
 */
class AudioEngine(context: Context) {

    private val appContext = context.applicationContext

    /** Called with each phrase the coach hears. */
    var onPhrase: ((String) -> Unit)? = null

    /** Called when the engine starts and stops. */
    var onStateChanged: ((Boolean) -> Unit)? = null

    /**
     * Called when the player starts speaking while the coach is talking.
     *
     * The coach should stop and let them finish.
     */
    var onBargeIn: (() -> Unit)? = null

    /**
     * The mean level above which a sound during the coach's speech is taken
     * to be the player rather than the coach.
     *
     * Measured on a Pixel 7, through the phone's own speaker, the coach's
     * own voice has a mean level around 0.0014 but a peak of 0.21 - a
     * plosive is louder than the player. So the test is on the mean, and it
     * sits above the coach's own voice.
     *
     * It is a guess until it is measured against the real thing, which is
     * what Phase 4 is for.
     */
    var bargeInThreshold = 0.012f

    /**
     * Whether barge-in is allowed at all.
     *
     * It is safe when the coach is talking through headphones, because the
     * voice never reaches the phone's microphone. It is **not** safe when
     * the coach is talking through the phone's own speaker: the echo peaks
     * at 0.21, which is louder than the player, so the coach would cut
     * itself off.
     *
     * The app sets this from the audio route.
     */
    @Volatile
    var bargeInEnabled = true

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    private val running = AtomicBoolean(false)

    /** Chunks thrown away because the coach was speaking. */
    private val droppedChunks = AtomicLong(0)

    /**
     * True while the coach is speaking.
     *
     * The microphone keeps recording - stopping and starting it costs a
     * noticeable gap - but the chunks are thrown away.
     */
    private val speaking = AtomicBoolean(false)

    /**
     * How long to keep ignoring the microphone after the coach stops
     * speaking.
     *
     * Measured on a Pixel 7, 13 November 2026. See `docs/TAIL.md`.
     *
     * The coach's own voice is gone within about 50 to 100 ms of the audio
     * stopping. On a phone the speaker is 10 cm from the microphone, so
     * there is no room reverb to speak of: what is left is the speaker's
     * mechanical ringing, and that is over in well under 100 ms.
     *
     * The Windows build used 0.35 s, which was room reverb on a laptop
     * whose speakers were across the room.
     *
     * The app sets this to zero when the coach is talking through
     * headphones, where the voice never reaches the microphone at all.
     */
    var echoTailSeconds = 0.10

    /** Loads the models. Slow the first time. */
    fun loadModels(): Boolean {
        if (recognizer != null) return true
        return try {
            val vadConfig = getVadModelConfig(0)!!
            vad = Vad(appContext.assets, vadConfig)

            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "base.en-encoder.int8.onnx",
                        decoder = "base.en-decoder.int8.onnx",
                    ),
                    tokens = "base.en-tokens.txt",
                    numThreads = 4,
                ),
            )
            recognizer = OfflineRecognizer(appContext.assets, config)
            Log.i(TAG, "Models loaded")
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Starts listening. */
    fun start() {
        if (running.get()) return
        if (!loadModels()) return

        val sampleRate = SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) return

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, 4096 * 4),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        record = recorder
        running.set(true)
        recorder.startRecording()
        Log.i(TAG, "Listening")
        onStateChanged?.invoke(true)

        worker = thread(name = "darts-audio", isDaemon = true) {
            captureLoop(recorder)
        }
    }

    /** Stops listening. */
    fun stop() {
        if (!running.getAndSet(false)) return
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        worker?.join(1000)
        record?.release()
        record = null
        onStateChanged?.invoke(false)
    }

    /**
     * The gate.
     *
     * Call this before the coach speaks. The microphone keeps running but
     * the chunks are dropped, so the coach cannot hear itself.
     */
    fun beginSpeaking() {
        speaking.set(true)
    }

    /** Call this when the coach has finished speaking. */
    fun endSpeaking() {
        speaking.set(false)
    }

    /** True while the microphone is being ignored. */
    val isSpeaking: Boolean get() = speaking.get()

    /** How many chunks have been dropped because the coach was speaking. */
    val dropped: Long get() = droppedChunks.get()

    /** The peak level of every chunk seen while the coach was speaking. */
    private val speechLevels = mutableListOf<Float>()

    /** The mean level of every chunk seen while the coach was speaking. */
    private val speechMeans = mutableListOf<Float>()

    /**
     * A summary of the levels seen during the coach's speech.
     *
     * The coach's own voice is what these are, unless the player spoke over
     * it. They are what the barge-in threshold has to sit above.
     */
    fun speechLevelSummary(): String {
        if (speechLevels.isEmpty()) return "no levels yet"
        val sorted = speechLevels.sorted()
        val means = speechMeans.sorted()
        fun pct(list: List<Float>, p: Double): Float =
            list[(list.size * p).toInt().coerceIn(0, list.size - 1)]
        return String.format(
            "peak: n=%d p50=%.4f p90=%.4f max=%.4f | " +
                "mean: p50=%.4f p90=%.4f max=%.4f",
            sorted.size, pct(sorted, 0.5), pct(sorted, 0.9), sorted.last(),
            pct(means, 0.5), pct(means, 0.9), means.last(),
        )
    }

    private fun captureLoop(recorder: AudioRecord) {
        val buffer = FloatArray(1024)
        val vad = this.vad!!

        while (running.get()) {
            val read = try {
                recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            } catch (e: Exception) {
                break
            }
            if (read <= 0) continue

            if (speaking.get()) {
                // The coach is talking.
                //
                // The audio is still analysed, because the player might be
                // answering. Throwing it away silently means a player who
                // says "yes" while the coach is still talking is ignored,
                // and the answer is lost - which is the difference between a
                // coach you can talk to and one you have to wait for.
                //
                // The danger is the coach's own voice, which is far louder
                // than the player's. It is told apart by level: the echo is
                // measured at a peak of 0.028 on the phone's own speaker,
                // while a spoken score at arm's length peaks around 0.09. A
                // level above the echo threshold during playback is the
                // player, not the coach.
                droppedChunks.incrementAndGet()
                val level = peakLevel(buffer, read)
                speechLevels.add(level)

                // The barge-in test is on the sustained level, not the peak.
                //
                // The coach's own voice through the phone's speaker is
                // almost silent for most of the speech and has the
                // occasional loud transient - a plosive - which can be
                // louder than the player. Measured:
                //
                //   min=0.0010 p50=0.0014 p90=0.0045 p99=0.0535 max=0.0535
                //
                // The player's voice is sustained, so the mean over a
                // window is what tells them apart. A single loud chunk is
                // the coach.
                val mean = meanLevel(buffer, read)
                speechMeans.add(mean)
                if (bargeInEnabled && mean > bargeInThreshold) {
                    Log.i(
                        TAG,
                        "Barge-in: the player is speaking (mean $mean)",
                    )
                    onBargeIn?.invoke()
                    // The VAD is reset so the coach's own voice is not
                    // sitting in it when the gate opens.
                    vad.reset()
                    continue
                }
                // The coach's own voice. Reset the VAD so it is not sitting
                // in the buffer when the gate opens.
                vad.reset()
                continue
            }

            vad.acceptWaveform(buffer.copyOf(read))

            while (!vad.empty()) {
                val segment = vad.front()
                vad.pop()
                val samples = segment.samples
                if (samples.isEmpty()) continue

                val text = transcribe(samples)
                Log.i(TAG, "Heard: '$text'")
                if (text.isNotBlank()) {
                    onPhrase?.invoke(text)
                }
            }
        }
    }

    /** The loudest sample in the chunk. */
    private fun peakLevel(buffer: FloatArray, length: Int): Float {
        var peak = 0f
        for (i in 0 until length) {
            val a = kotlin.math.abs(buffer[i])
            if (a > peak) peak = a
        }
        return peak
    }

    /** The mean absolute level in the chunk. */
    private fun meanLevel(buffer: FloatArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += kotlin.math.abs(buffer[i])
        }
        return if (length > 0) (sum / length).toFloat() else 0f
    }

    /** Transcribes one phrase. */
    private fun transcribe(samples: FloatArray): String {
        val rec = recognizer ?: return ""
        return try {
            val stream: OfflineStream = rec.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            rec.decode(stream)
            rec.getResult(stream).text
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        const val TAG = "DartsCoach"

        /** The rate everything runs at. Whisper wants 16 kHz. */
        const val SAMPLE_RATE = 16000
    }
}