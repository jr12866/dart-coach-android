package com.jr12866.dartcoach

import android.Manifest
import android.content.pm.PackageManager
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
    private fun speak(text: String) {
        audio.beginSpeaking()
        try {
            Log.i("DartsCoach", "Speaking: $text")
            val seconds = tts.speak(text)
            if (seconds > 0) {
                // The speaker is still ringing. Wait for it to stop before
                // listening again.
                Thread.sleep((audio.echoTailSeconds * 1000).toLong())
            }
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
    }
}