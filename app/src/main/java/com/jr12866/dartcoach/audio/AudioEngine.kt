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
import java.util.concurrent.atomic.AtomicInteger
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

    /**
     * Called with each phrase the coach hears.
     *
     * The second argument says whether the phrase arrived during the tail -
     * the moment after the coach stopped speaking, when its own voice was
     * still ringing in the room. The caller uses it to be careful about what
     * it accepts from the tail.
     */
    var onPhrase: ((String, Boolean) -> Unit)? = null

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
     * On the Bluetooth headphone route the coach's voice never reaches the
     * microphone at all: the measured speech level was 0.00035, the same
     * as the room floor. So the threshold is only ever in play on the
     * phone's own speaker.
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
     * The app sets this from the audio route. Both routes have been
     * measured: see `docs/BLUETOOTH.md`.
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
     * On a quiet desk the coach's own voice is gone within about 50 to
     * 100 ms of the audio stopping. At the oche, where the room is not
     * quiet, the measured tail was 447 - 835 ms, so the app uses 0.35 s.
     *
     * The app sets this to zero when the coach is talking through
     * headphones, where the voice never reaches the microphone at all.
     * That was measured too: see `docs/BLUETOOTH.md`.
     */
    var echoTailSeconds = 0.35

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
        gateOpenings.incrementAndGet()
    }

    /** How many times the coach has finished speaking and let the mic back in. */
    private val gateOpenings = AtomicInteger(0)

    /**
     * How many times the microphone has been let back in.
     *
     * The self-hearing test waits for this number to go up, so that it
     * speaks the way a player does: after the coach has answered, and not
     * over the top of the answer. A phrase that arrives while the coach is
     * still talking is thrown away by the gate, which is right, but it
     * means the test is not testing anything.
     */
    val gateOpenCount: Int get() = gateOpenings.get()

    /**
     * The tail: the coach has stopped speaking, but its voice is still
     * ringing in the room.
     *
     * The microphone is not ignored in this window. The player answers the
     * coach's question while the echo is still there, and an answer that
     * starts in this window used to be thrown away with everything else,
     * because the gate was shut until the tail was over. The player has to
     * wait about half a second to be heard, and a question asks to be
     * answered at once.
     *
     * So the audio goes to the voice detector as usual, and the level
     * decides afterwards what it was: the player's voice, or the coach's
     * echo. The echo is quiet and fading, and the player is not.
     */
    fun beginTail() {
        tailActive.set(true)
        tailEnded.set(false)
        tailHandled.set(false)
    }

    /** The tail is over. The capture loop now decides what it heard. */
    fun endTail() {
        tailActive.set(false)
        tailEnded.set(true)
    }

    /**
     * The level a sound has to reach for the app to take it for the player
     * rather than for the coach's own echo.
     *
     * Measured on a Pixel 7, in the room the app was developed in, with the
     * echo tail measured in the same room:
     *
     *   the coach's own voice ringing in the room   chunk mean at most 0.022
     *   a player speaking over the coach             mean 0.052 to 0.055
     *
     * So 0.03 sits above the echo and below the player. It is a guess in
     * between two measured numbers, and it is only ever used to tell the
     * player's answer from the coach's echo in the tail - nothing else
     * depends on it.
     */
    private val PLAYER_LEVEL = 0.03f

    /**
     * How many chunks have to be over the player's level before the tail is
     * taken for the player.
     *
     * One is not enough: the coach's own voice has the occasional loud
     * transient in it - a plosive - and a single chunk cannot tell that
     * apart from a word. A spoken word is several chunks.
     */
    private val PLAYER_CHUNKS = 2

    /** True while the coach's voice is still ringing in the room. */
    private val tailActive = AtomicBoolean(false)

    /** True once the tail is over and the capture loop has not judged it yet. */
    private val tailEnded = AtomicBoolean(false)

    /** True once the capture loop has judged the tail. */
    private val tailHandled = AtomicBoolean(false)

    /** The phrases the voice detector found during the tail. */
    private val tailPhrases = mutableListOf<FloatArray>()

    /** The mean level of every chunk heard during the tail. */
    private val tailLevels = mutableListOf<Float>()

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
            // Has the tail just ended? Decide what it was before going on.
            if (tailEnded.get() && !tailHandled.get()) {
                tailHandled.set(true)
                val loud = tailLevels.count { it >= PLAYER_LEVEL }
                val player = loud >= PLAYER_CHUNKS
                Log.i(
                    TAG,
                    "Tail: ${tailLevels.size} chunk(s), $loud over the player's " +
                        "level, so it was ${if (player) "the player" else "the echo"}",
                )
                tailLevels.clear()
                if (player) {
                    // The player answered while the echo was still there.
                    for (samples in tailPhrases) {
                        val text = transcribe(samples)
                        Log.i(TAG, "Heard: '$text'")
                        if (text.isNotBlank()) onPhrase?.invoke(text, true)
                    }
                    tailPhrases.clear()
                } else {
                    // Only the coach's own voice. Throw it away rather than
                    // let it be heard as the player talking.
                    tailPhrases.clear()
                    vad.reset()
                }
            }

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

                if (tailActive.get() || tailEnded.get()) {
                    // The tail. The coach has stopped, but its voice is still
                    // in the room, and the player may already be answering.
                    // The audio goes to the voice detector so that the answer
                    // is not lost with the rest of the gate, and the level
                    // decides afterwards what it was.
                    tailLevels.add(mean)
                    vad.acceptWaveform(buffer.copyOf(read))
                    while (!vad.empty()) {
                        val segment = vad.front()
                        vad.pop()
                        if (segment.samples.isNotEmpty()) {
                            tailPhrases.add(segment.samples)
                        }
                    }
                    continue
                }

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
                    onPhrase?.invoke(text, false)
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