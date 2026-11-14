package com.jr12866.dartcoach

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jr12866.dartcoach.audio.AudioEngine
import com.jr12866.dartcoach.core.CoachEngine
import com.jr12866.dartcoach.speech.PiperTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The coach.
 *
 * The phone does everything: it hears the score, works out what to throw,
 * and says it. There is no server and no network.
 *
 * The screen is deliberately plain. At the oche the player wants to read the
 * score and the suggestion, not admire a design.
 */
class MainActivity : ComponentActivity() {

    private val coach = CoachEngine()
    private lateinit var audio: AudioEngine
    private lateinit var tts: PiperTts

    private var remaining by mutableStateOf(501)
    private var turns by mutableStateOf(0)
    private var suggestion by mutableStateOf("")
    private var message by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var double by mutableStateOf("")

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The phone is at the oche and the screen must not sleep mid-leg.
        //
        // FLAG_KEEP_SCREEN_ON is enough for a foreground activity and needs
        // no permission. A PowerManager wake lock would need
        // WAKE_LOCK permission, and it would be the wrong tool: this keeps
        // the screen on, not the CPU.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audio = AudioEngine(this)
        tts = PiperTts(this)

        audio.onPhrase = { phrase -> onHeard(phrase) }
        audio.onStateChanged = { running -> listening = running }
        audio.onBargeIn = { tts.stop() }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F14),
                ) {
                    CoachScreen(
                        remaining = remaining,
                        turns = turns,
                        suggestion = suggestion,
                        message = message,
                        double = double,
                        listening = listening,
                        onNewGame = { onNewGame() },
                        onUndo = { onUndo() },
                        onPrivacyPolicy = { openPrivacyPolicy() },
                    )
                }
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }

        handleMeasurementIntent(intent)

        // A hidden way to measure the wording on the device.
        if (intent?.getBooleanExtra("measure", false) == true) {
            measureLines(
                arrayOf(
                    "You're at 401. Throw treble twenty, treble twenty and treble twenty to leave 221.",
                    "You're at 401. Throw treble twenty, treble twenty, treble twenty.",
                    "401. Treble twenty, treble twenty, treble twenty.",
                    "You said one hundred. Is that right?",
                    "You said one hundred.",
                    "You said 100. Right?",
                    "Is that right?",
                    "You're at 401. Treble twenty, treble twenty, treble twenty to leave 221.",
                    "401. Treble twenty, treble twenty, treble twenty. Leaves 221.",
                    "401. T20, T20, T20.",
                    "401. Treble 20, treble 20, treble 20.",
                    "401. Treble twenty, treble twenty, treble twenty.",
                    "T20, T20, T20. Leaves 221.",
                    "You said one hundred. Right?",
                    "You said 140. Right?",
                    "Right? 140.",
                    "140. Right?",
                    // The new wording, to measure it against the old.
                    "401. Treble twenty, treble twenty and treble twenty.",
                    "401. Treble twenty, treble twenty, treble twenty.",
                    "You said 140. Right?",
                )
            )
        }
    }

    /**
     * Handles the hidden measurement intents.
     *
     * `am start` on an app that is already running delivers the new intent
     * to the existing instance rather than starting a new one, so the
     * check has to be in onNewIntent as well as onCreate. Without this the
     * measurement silently did nothing, because the app is normally left
     * running between measurements.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMeasurementIntent(intent)
    }

    private fun handleMeasurementIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("tail", false)) {
            Log.i("DartsCoach", "Measuring the echo tail")
            measureTail()
        }
        if (intent.getBooleanExtra("route", false)) {
            Log.i("DartsCoach", "Checking the audio route")
            measureRoute()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.stop()
    }

    private fun startListening() {
        Thread {
            if (!audio.loadModels()) return@Thread
            if (!tts.load()) {
                // The coach can still work without a voice, though it cannot
                // read the score back.
            }
            audio.start()
        }.start()
    }

    /**
     * The coach heard something.
     *
     * The answer is spoken away from the thread that captured it, because
     * speaking takes seconds and the capture loop has to keep running.
     */
    private fun onHeard(phrase: String) {
        Thread {
            val said = coach.handle(phrase)
            Log.i("DartsCoach", "Coach says: $said")
            refresh()
            if (said != null) {
                speak(said)
            }
        }.start()
    }

    /**
     * Says something, with the microphone gated while it is talking.
     *
     * The gate is the half-duplex rule: the coach must not hear itself.
     */
    /**
     * Where is the coach's voice going?
     *
     * It matters because barge-in is only safe when the voice is not going
     * through the phone's own speaker. With headphones the coach cannot hear
     * itself at all; through the phone's speaker the echo peaks at 0.21,
     * which is louder than the player, so the coach would cut itself off
     * mid-sentence.
     */
    private fun isOnThePhoneSpeaker(): Boolean {
        val device = outputDeviceForSpeech() ?: return true
        return device.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
    }

    /**
     * The output the coach's voice is going to.
     *
     * The decision is made *before* the audio is played, so there is no
     * playing configuration to ask yet. The device list is the only thing
     * to go on, and it lists the phone's speaker even when the headphones
     * are carrying the audio.
     *
     * Android's own rule settles it: media goes to Bluetooth when a
     * Bluetooth output is available, and only falls back to the speaker
     * when there is none. So a connected A2DP device means the headphones,
     * whatever else is on the list.
     *
     * The playback configurations are still consulted first, because when
     * something *is* playing they name the device exactly. That matters
     * for the case where the player has turned the phone's speaker on
     * deliberately.
     */
    private fun outputDeviceForSpeech(): AudioDeviceInfo? {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        for (config in manager.activePlaybackConfigurations) {
            val info = config.audioDeviceInfo ?: continue
            if (info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            ) {
                return info
            }
        }

        var bluetooth: AudioDeviceInfo? = null
        for (device in manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                bluetooth == null
            ) {
                bluetooth = device
            }
        }
        if (bluetooth != null) return bluetooth

        for (device in manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return device
        }
        return null
    }

    /**
     * The route the coach's voice is actually going to, for the log.
     *
     * This is deliberately separate from the decision above. The decision
     * only has to be right about the echo and barge-in. The log says what
     * Android reported, so a measurement can be tied to the route it was
     * taken on.
     *
     * `AudioManager.getDevices()` lists every output that *could* be used,
     * including the phone's speaker even when the headphones are carrying
     * the audio. It is therefore not enough on its own: the Bose is on the
     * list whether or not it is the active device.
     *
     * `activePlaybackConfigurations` is the honest answer when something is
     * playing. The device list is the fallback before the first utterance.
     */
    private fun describeAudioRoute(): String {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val playback = manager.activePlaybackConfigurations
            .mapNotNull { it.audioDeviceInfo }
            .map { it.type }
            .distinct()
        if (playback.isNotEmpty()) {
            return "playback=${playback.joinToString(",") { routeName(it) }}"
        }

        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.type }
            .distinct()
        return "outputs=${outputs.joinToString(",") { routeName(it) }}"
    }

    /**
     * The route the coach's voice is going to, for the log.
     *
     * The decision and the log are separate on purpose. The decision only
     * has to be right about the echo and barge-in. The log says what the
     * device was, so a measurement can be tied to the route it was taken
     * on.
     */
    private fun routeDescription(): String {
        val device = outputDeviceForSpeech()
        return if (device == null) "no output device" else routeName(device.type)
    }

    /**
     * A one-off spoken line, for testing a route.
     *
     * The normal path is the coach answering a score, which needs somebody
     * to say the score. This says a fixed line instead, so the route can be
     * checked without a second person.
     */
    private fun measureRoute() {
        Thread {
            if (!tts.load()) {
                Log.i("DartsCoach", "The voice would not load")
                return@Thread
            }
            speak("401. Treble twenty, treble twenty and treble twenty.")
        }.start()
    }

    private fun routeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
        else -> "type=$type"
    }

    private fun speak(text: String) {
        val started = System.currentTimeMillis()
        // Ask the route before speaking, because both the barge-in test
        // and the tail depend on where the voice is going.
        //
        // With headphones the coach's voice never reaches the phone's
        // microphone at all, so there is no echo to wait for and the gate
        // can open the instant the audio stops. Through the phone's own
        // speaker there is a little mechanical ringing, which needs a
        // tenth of a second.
        val onThePhone = isOnThePhoneSpeaker()
        audio.bargeInEnabled = !onThePhone
        audio.echoTailSeconds = if (onThePhone) PHONE_SPEAKER_TAIL else 0.0
        audio.beginSpeaking()
        try {
            Log.i(
                "DartsCoach",
                "Route: ${routeDescription()} " +
                    "(${if (onThePhone) "phone speaker" else "headphones"}); " +
                    "Speaking: $text",
            )
            val seconds = tts.speak(text)
            if (seconds > 0 && !tts.wasInterrupted()) {
                // The speaker is still ringing. Wait for it to stop before
                // listening again.
                Thread.sleep((audio.echoTailSeconds * 1000).toLong())
            }
            Log.i(
                "DartsCoach",
                "Gate open after ${System.currentTimeMillis() - started}ms " +
                    "(speech ${(seconds * 1000).toInt()}ms, " +
                    "tail ${(audio.echoTailSeconds * 1000).toInt()}ms); " +
                    "${audio.dropped} chunk(s) dropped while speaking; " +
                    "levels ${audio.speechLevelSummary()}",
            )
        } finally {
            audio.endSpeaking()
        }
    }

    private fun refresh() {
        remaining = coach.state.remainingScore
        turns = coach.state.turnsPlayed
        suggestion = coach.suggestion
        message = coach.message
        double = coach.state.preferredDouble?.let { "Playing for D$it" } ?: ""
    }

    /**
     * Opens the privacy policy.
     *
     * Google requires the policy to be linked from inside the app as well
     * as from the Play listing. A public URL is preferred so it is the same
     * page Play links to. If none is configured, the policy is shown in a
     * plain dialog, because an `android.resource://` URI cannot be opened
     * by an external browser.
     */
    private fun openPrivacyPolicy() {
        val online = getString(R.string.privacy_policy_url).trim()
        if (online.isNotBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(online))
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w("DartsCoach", "Could not open the privacy policy URL", e)
            }
        }

        val policy = resources.openRawResource(R.raw.privacy_policy)
            .bufferedReader()
            .use { it.readText() }
        android.app.AlertDialog.Builder(this)
            .setTitle("Privacy policy")
            .setMessage(stripHtml(policy))
            .setPositiveButton("Close", null)
            .show()
    }

    /** A plain-text version of the bundled policy, for the dialog. */
    private fun stripHtml(html: String): String =
        html
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<head.*?</head>"), "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|li|h1|h2|div)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
            .trim()

    /**
     * Measures the echo tail on the device.
     *
     * Reached by an intent extra, so the number can be measured where the
     * phone actually is rather than on a desk.
     */
    private fun measureTail() {
        Thread {
            if (!tts.load()) {
                Log.i("DartsCoach", "The voice would not load")
                return@Thread
            }

            val minBuffer = android.media.AudioRecord.getMinBufferSize(
                16000,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_FLOAT,
            )
            if (minBuffer <= 0) {
                Log.i("DartsCoach", "AudioRecord would not start")
                return@Thread
            }
            val recorder = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuffer, 4096 * 4),
            )
            if (recorder.state != android.media.AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                Log.i("DartsCoach", "AudioRecord did not initialise")
                return@Thread
            }
            recorder.startRecording()

            // Three phrases, so the answer is not one sample.
            val phrases = arrayOf(
                "You're at 401. Treble twenty, treble twenty, treble twenty.",
                "One hundred and forty. Is that right?",
                "That leaves one hundred and eighty one.",
            )
            val tails = mutableListOf<Double>()
            for (phrase in phrases) {
                val tail = tts.measureTail(phrase, recorder)
                tails.add(tail)
                Log.i("DartsCoach", "TAIL for '$phrase' was ${"%.3f".format(tail)}s")
            }

            val mean = if (tails.isEmpty()) 0.0 else tails.sum() / tails.size
            val longest = tails.maxOrNull() ?: 0.0
            Log.i(
                "DartsCoach",
                "TAIL SUMMARY: mean=${"%.3f".format(mean)}s longest=${"%.3f".format(longest)}s " +
                    "(the Windows build used 0.35s)",
            )
            recorder.stop()
            recorder.release()
        }.start()
    }

    /**
     * Measures how long each line takes to say.
     *
     * Reached by an intent extra, so the wording can be tuned on the device
     * rather than by eye. Nothing in the app's normal flow calls it.
     */
    private fun measureLines(lines: Array<String>) {
        Thread {
            if (!tts.load()) {
                Log.i("DartsCoach", "The voice would not load")
                return@Thread
            }
            for (line in lines) {
                val seconds = tts.measure(line)
                Log.i("DartsCoach", "MEASURE ${"%.2f".format(seconds ?: 0.0)}s  $line")
            }
        }.start()
    }

    private fun onNewGame() {
        Thread {
            coach.handle("new game")
            coach.handle("coach yes")
            refresh()
        }.start()
    }

    private fun onUndo() {
        Thread {
            coach.handle("undo")
            coach.handle("coach yes")
            refresh()
        }.start()
    }

    companion object {
        /**
         * The echo tail through the phone's own speaker.
         *
         * On a quiet desk the coach's voice is gone within about 50 to
         * 100 ms of the audio stopping, and 0.10 s was enough. Measured
         * again at the oche, where the room is not quiet and the phone's
         * speaker is 10 cm from the microphone with a surface between them,
         * the tail was 447 - 835 ms.
         *
         * So 0.35 s is used: the same value the Windows build used, and it
         * covers the shorter half of the measured range. The cost of
         * waiting a little too long is much smaller than the cost of the
         * coach hearing its own reply.
         *
         * The right number has to be measured standing at the board. See
         * `docs/TAIL.md`.
         */
        private const val PHONE_SPEAKER_TAIL = 0.35
    }
}

@Composable
private fun CoachScreen(
    remaining: Int,
    turns: Int,
    suggestion: String,
    message: String,
    double: String,
    listening: Boolean,
    onNewGame: () -> Unit,
    onUndo: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Remaining",
                fontSize = 14.sp,
                color = Color(0xFF7D8894),
                letterSpacing = 1.sp,
            )
            Text(
                text = if (listening) "listening" else "paused",
                fontSize = 13.sp,
                color = if (listening) Color(0xFF4ADE80) else Color(0xFF7D8894),
            )
        }

        Text(
            text = remaining.toString(),
            fontSize = 88.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE8EDF2),
        )

        if (double.isNotEmpty()) {
            Text(text = double, fontSize = 16.sp, color = Color(0xFFFFD166))
        }

        if (message.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A222C), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = message,
                    fontSize = 18.sp,
                    color = Color(0xFFFFD166),
                )
            }
        }

        if (suggestion.isNotEmpty()) {
            Text(
                text = "SUGGESTION",
                fontSize = 13.sp,
                color = Color(0xFF7D8894),
                letterSpacing = 1.sp,
            )
            Text(
                text = suggestion,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE8EDF2),
            )
        }

        Text(
            text = "Turns: $turns",
            fontSize = 14.sp,
            color = Color(0xFF7D8894),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.weight(1f),
            ) {
                Text("Undo")
            }
            Button(
                onClick = onNewGame,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4ADE80),
                    contentColor = Color(0xFF0B0F14),
                ),
            ) {
                Text("New game")
            }
        }

        Text(
            text = "Speech and voice run on this phone. No account, no ads, no analytics.",
            fontSize = 12.sp,
            color = Color(0xFF7D8894),
        )

        OutlinedButton(
            onClick = onPrivacyPolicy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Privacy policy")
        }
    }
}