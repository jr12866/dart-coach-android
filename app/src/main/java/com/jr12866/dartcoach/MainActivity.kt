package com.jr12866.dartcoach

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig

/**
 * Phase 1 spike: does the phone hear a darts score?
 *
 * This is the measurement that decides which speech recogniser to use. It
 * captures 16 kHz mono from the microphone, finds speech with the silero
 * VAD, and transcribes each phrase with Whisper.
 *
 * It prints the transcript and the real-time factor for every phrase, so
 * the numbers can be compared against the Raspberry Pi 4 figures in the
 * sherpa-onnx documentation.
 *
 * The UI is deliberately plain. This screen is a measuring instrument, not
 * the coach.
 */
class MainActivity : ComponentActivity() {

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.e(TAG, "Microphone permission was refused")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SpikeScreen()
                }
            }
        }
    }

    /** Loads the VAD and the recogniser. Slow the first time: the models load. */
    fun loadModels(): Boolean {
        if (recognizer != null) return true
        return try {
            val vadConfig: VadModelConfig = getVadModelConfig(0)!!
            vad = Vad(assets, vadConfig)
            Log.i(TAG, "VAD loaded")

            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "base.en-encoder.int8.onnx",
                        decoder = "base.en-decoder.int8.onnx",
                    ),
                    tokens = "base.en-tokens.txt",
                    numThreads = 4,
                    debug = false,
                ),
            )
            recognizer = OfflineRecognizer(assets, config)
            Log.i(TAG, "Recognizer loaded")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the models: ${e.message}", e)
            false
        }
    }

    /**
     * Records until the player stops, then transcribes each phrase.
     *
     * The VAD decides where the phrases are, so the audio handed to the
     * recogniser is speech and nothing else. That is what makes the
     * real-time factor a fair number.
     */
    fun recordAndTranscribe(onPhrase: (String, Double) -> Unit) {
        if (!loadModels()) return

        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize returned $minBuffer")
            return
        }

        val bufferSize = maxOf(minBuffer, 4096 * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord did not initialise")
            return
        }

        record.startRecording()
        Log.i(TAG, "Recording. Speak a score.")

        val buffer = FloatArray(1024)
        val vad = this.vad!!

        while (!Thread.currentThread().isInterrupted) {
            val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) continue

            vad.acceptWaveform(buffer.copyOf(read))

            while (!vad.empty()) {
                val segment = vad.front()
                vad.pop()

                val samples = segment.samples
                if (samples.isEmpty()) continue

                val rec = recognizer!!
                val stream: OfflineStream = rec.createStream()
                stream.acceptWaveform(samples, sampleRate)

                val started = System.nanoTime()
                rec.decode(stream)
                val elapsed = (System.nanoTime() - started) / 1_000_000_000.0

                val text = rec.getResult(stream).text
                val seconds = samples.size.toDouble() / sampleRate
                val rtf = if (seconds > 0) elapsed / seconds else 0.0
                Log.i(
                    TAG,
                    "Heard: '$text'  audio=${"%.2f".format(seconds)}s  " +
                        "elapsed=${"%.2f".format(elapsed)}s  rtf=$rtf",
                )
                onPhrase(text, rtf)
            }
        }

        record.stop()
        record.release()
    }

    companion object {
        const val TAG = "DartsCoach"
    }
}

@Composable
private fun SpikeScreen() {
    var status by remember { mutableStateOf("Ready") }
    var phrases by remember { mutableStateOf(listOf<String>()) }

    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Phase 1 spike", fontSize = 24.sp)
        Text(status, fontSize = 14.sp)

        Button(onClick = {
            status = "Listening..."
            Thread {
                activity.recordAndTranscribe { text, rtf ->
                    phrases = phrases + "$text  (rtf $rtf)"
                }
            }.start()
        }) {
            Text("Listen")
        }

        phrases.forEach { Text(it, fontSize = 15.sp) }
    }
}