package com.jr12866.dartcoach

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
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
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig

/**
 * Phase 3 spike: does the coach hearing itself break anything?
 *
 * On the laptop the speaker and the microphone are across the room, so the
 * coach's voice reaches the microphone faintly and the half-duplex gate has
 * an easy job. On a phone they are centimetres apart in the same enclosure,
 * so the echo is much stronger. This measures how much.
 *
 * The spike plays a phrase through the speaker while the microphone is
 * open, and reports:
 *
 *   - how loud the speech is at the microphone
 *   - how loud the room is
 *   - what the recogniser makes of the speech, if it hears anything
 *
 * It also tries two ways of keeping the microphone quiet during playback:
 * pausing the capture, and leaving it running to see how bad it gets.
 */
class MainActivity : ComponentActivity() {

    private var tts: OfflineTts? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Log.e(TAG, "Microphone permission was refused")
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

    /**
     * Synthesises speech, phonemising the text first.
     *
     * The Piper model was trained on phoneme codes, not on text, so the text
     * has to be converted before the model sees it. The phonemizer does that
     * and the codes go in through GenerationConfig.
     */
    private fun synthesize(text: String): FloatArray {
        val phonemes = piperPhonemizeText(text, "en-us")
            ?: throw RuntimeException("Could not phonemize the text")
        try {
            val codes = Array(phonemes.numSentences) { i -> phonemes.getPhonemes(i) }
            val audio = tts!!.generateWithConfig(
                text,
                GenerationConfig(phonemeCodepoints = codes),
            )
            return audio.samples
        } finally {
            phonemes.close()
        }
    }

    private fun loadTts(): Boolean {
        if (tts != null) return true
        return try {
            // The Piper voice needs a text frontend: something to turn
            // "You're at 247" into the phoneme codes the model was trained
            // on. piper-phonemize does that, and it bundles its own copy of
            // the espeak-ng data, so the espeak-ng directory that ships with
            // the voice is not used at all.
            //
            // sherpa-onnx has not used data_dir since v2.0.0, so passing the
            // espeak-ng directory there does nothing. The upstream app says
            // so in its own build script.
            piperPhonemizeInitialize(applicationContext)

            val config = getOfflineTtsConfig(
                modelDir = "vits-piper-en_US-lessac-medium-int8",
                modelName = "en_US-lessac-medium.onnx",
            )
            tts = OfflineTts(assets, config)
            Log.i(TAG, "TTS loaded, sample rate ${tts!!.sampleRate()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the TTS: ${e.message}", e)
            false
        }
    }

    /**
     * Measures the microphone level while the coach speaks.
     *
     * The microphone stays open the whole time - that is the point. A gate
     * that pauses the capture cannot measure what it is protecting against,
     * and the answer decides which gate to build.
     *
     * Returns the peak level seen during the speech.
     */
    fun measureEchoWhileSpeaking(phrase: String): Float {
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, 4096 * 4),
        )
        record.startRecording()

        // Let the room settle first, so the floor is measured separately.
        var floor = 0f
        var floorPeak = 0f
        val settle = FloatArray(1024)
        repeat(30) {
            val read = record.read(settle, 0, settle.size, AudioRecord.READ_BLOCKING)
            if (read > 0) {
                for (i in 0 until read) {
                    val a = kotlin.math.abs(settle[i])
                    floor += a
                    if (a > floorPeak) floorPeak = a
                }
            }
        }
        floor /= 30 * 1024
        Log.i(TAG, "room floor: mean=%.5f peak=%.5f".format(floor, floorPeak))

        // Now synthesise and play, while still listening.
        val samples = synthesize(phrase)
        val ttsRate = tts!!.sampleRate()
        Log.i(TAG, "generated ${samples.size} samples at $ttsRate Hz")

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
                    .setSampleRate(ttsRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()

        // Listen through the whole of the playback.
        var peak = 0f
        var sum = 0f
        var count = 0
        val buf = FloatArray(1024)
        val deadline = System.currentTimeMillis() + (samples.size * 1000L / ttsRate) + 500
        while (System.currentTimeMillis() < deadline) {
            val read = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
            if (read > 0) {
                for (i in 0 until read) {
                    val a = kotlin.math.abs(buf[i])
                    sum += a
                    count++
                    if (a > peak) peak = a
                }
            }
        }

        track.stop()
        track.release()
        record.stop()
        record.release()

        val mean = if (count > 0) sum / count else 0f
        Log.i(
            TAG,
            "echo at the mic: mean=%.5f peak=%.5f (floor was %.5f)".format(
                mean, peak, floor,
            ),
        )
        return peak
    }

    /** Plays a phrase out loud, so the user can hear the voice. */
    fun speak(phrase: String) {
        if (!loadTts()) return
        val samples = synthesize(phrase)
        val rate = tts!!.sampleRate()

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

        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        Thread.sleep((samples.size * 1000L / rate) + 100)
        track.stop()
        track.release()
        Log.i(TAG, "Spoke: '$phrase'")
    }

    companion object {
        const val TAG = "DartsCoach"
    }
}

@Composable
private fun SpikeScreen() {
    var status by remember { mutableStateOf("Ready") }
    var result by remember { mutableStateOf("") }

    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Echo gate", fontSize = 24.sp)
        Text(status, fontSize = 14.sp)

        Button(onClick = {
            status = "Speaking..."
            Thread {
                activity.speak("You're at 247. Try treble twenty, treble twenty and double five.")
                status = "Measuring the echo..."
                val peak = activity.measureEchoWhileSpeaking(
                    "You're at 247. Try treble twenty and double five.",
                )
                result = "peak at the mic: $peak"
                status = "Done"
            }.start()
        }) {
            Text("Speak and measure")
        }

        Button(onClick = {
            status = "Speaking..."
            Thread {
                activity.speak("One hundred and eighty. Is that right?")
                status = "Done"
            }.start()
        }) {
            Text("Speak only")
        }

        if (result.isNotEmpty()) Text(result, fontSize = 15.sp)
    }
}