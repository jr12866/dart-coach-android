package com.jr12866.dartcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jr12866.dartcoach.core.TailSetting

/**
 * The settings.
 *
 * The echo tail is the one thing here that the player might need to change.
 * It is how long the coach waits after speaking before it listens again,
 * and it depends on the phone - the speaker's ringing and the acoustic path
 * to the microphone - so it is measured rather than assumed.
 *
 * A phone that is quieter, or louder, or in a different room, wants a
 * different answer, which is why the measurement can be run again.
 *
 * The second panel is about the coach's memory. The coach works out how
 * well the player throws from the darts that have been reported, and that
 * memory is only as good as what went into it - a misheard game or a test
 * can leave it believing something untrue. So it can be cleared.
 */
@Composable
fun SettingsScreen(
    measuredTail: Double?,
    measuring: Boolean,
    tailMessage: String,
    learnedAttempts: Int,
    learnedMessage: String,
    onBack: () -> Unit,
    onMeasureTail: () -> Unit,
    onResetTail: () -> Unit,
    onResetLearned: () -> Unit,
) {
    // Whether the confirmation is on the screen. The reset is worth a
    // question, because it throws away work the player cannot get back.
    var confirmingReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
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
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            OutlinedButton(onClick = onBack) {
                Text("Back", color = PrimaryText)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBackground, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Echo delay",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            Text(
                text = "How long the coach waits after speaking before it " +
                    "listens again. It waits so it does not hear its own " +
                    "voice.",
                fontSize = 14.sp,
                color = QuietText,
            )
            Text(
                text = "Currently: " + TailSetting.describe(
                    TailSetting.forRoute(measuredTail, onThePhoneSpeaker = true)
                ),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = AccentGreen,
            )
            if (measuredTail == null) {
                Text(
                    text = "Not measured yet, so the safe default is being " +
                        "used. Measuring takes about half a minute and the " +
                        "coach will talk three times.",
                    fontSize = 13.sp,
                    color = QuietText,
                )
            }
            if (tailMessage.isNotEmpty()) {
                Text(
                    text = tailMessage,
                    fontSize = 14.sp,
                    color = AccentAmber,
                )
            }

            Button(
                onClick = onMeasureTail,
                enabled = !measuring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = ScreenBackground,
                    disabledContainerColor = PanelBackground,
                    disabledContentColor = QuietText,
                ),
            ) {
                Text(
                    text = if (measuring) "Measuring..." else "Measure again",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (measuredTail != null) {
                OutlinedButton(
                    onClick = onResetTail,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use the default instead", color = PrimaryText)
                }
            }

            Text(
                text = "If the coach cuts itself off, measure again in the " +
                    "room you are playing in. If it waits too long before " +
                    "answering, measure again with the phone somewhere else.",
                fontSize = 13.sp,
                color = QuietText,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBackground, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "What the coach has learned",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            Text(
                text = "The coach works out how well you throw from the " +
                    "darts you have reported. It starts from the average " +
                    "player and gets better as it goes.",
                fontSize = 14.sp,
                color = QuietText,
            )
            Text(
                text = "Learned from " +
                    (if (learnedAttempts == 1) "1 dart" else "$learnedAttempts darts"),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = AccentGreen,
            )
            if (learnedAttempts == 0) {
                Text(
                    text = "There is nothing to forget. The coach is on the " +
                        "average player.",
                    fontSize = 13.sp,
                    color = QuietText,
                )
            } else {
                Text(
                    text = "If the numbers are wrong - a game was misheard, " +
                        "or they are of a test rather than a leg - you can " +
                        "make the coach forget them.",
                    fontSize = 13.sp,
                    color = QuietText,
                )
            }
            if (learnedMessage.isNotEmpty()) {
                Text(
                    text = learnedMessage,
                    fontSize = 14.sp,
                    color = AccentAmber,
                )
            }

            OutlinedButton(
                onClick = { confirmingReset = true },
                enabled = learnedAttempts > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Forget what I've learned",
                    color = if (learnedAttempts > 0) AccentAmber else QuietText,
                )
            }

            Text(
                text = "Your own rates and your leg history are not " +
                    "touched. The leg in play keeps its score.",
                fontSize = 13.sp,
                color = QuietText,
            )
        }
    }

    if (confirmingReset) {
        // The reset cannot be undone, so it is asked for first.
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            containerColor = PanelBackground,
            title = {
                Text(
                    text = "Forget what the coach has learned?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                )
            },
            text = {
                Text(
                    text = "The coach will go back to the average player. " +
                        "The $learnedAttempts dart(s) it learned from are " +
                        "thrown away. Your own rates and your leg history " +
                        "are not touched.",
                    fontSize = 15.sp,
                    color = QuietText,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmingReset = false
                        onResetLearned()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentAmber,
                        contentColor = ScreenBackground,
                    ),
                ) {
                    Text("Forget it", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmingReset = false }) {
                    Text("Keep it", color = PrimaryText)
                }
            },
        )
    }
}
