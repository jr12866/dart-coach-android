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

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    private val running = AtomicBoolean(false)

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
     * The Windows build uses 0.35 seconds, for a laptop whose speakers are
     * across the room. On a phone the speaker is 10 cm from the microphone,
     * so the tail is the mechanical ringing of the speaker rather than room
     * reverb. It is measured rather than guessed.
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
    }

    /** True while the microphone is being ignored. */
    val isSpeaking: Boolean get() = speaking.get()

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
                // The coach is talking. Throw the audio away, and reset the
                // VAD so the coach's own voice is not sitting in it when the
                // gate opens.
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