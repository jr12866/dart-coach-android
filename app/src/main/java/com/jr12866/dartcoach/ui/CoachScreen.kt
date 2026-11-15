package com.jr12866.dartcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The live coach.
 *
 * The screen is deliberately plain. At the oche the player wants to read the
 * score and the suggestion, not admire a design.
 */
@Composable
fun CoachScreen(
    remaining: Int,
    turns: Int,
    suggestion: String,
    message: String,
    double: String,
    listening: Boolean,
    turnDarts: Int,
    turnScore: Int,
    planKind: String,
    correctionAvailable: Boolean,
    onNewGame: () -> Unit,
    onUndo: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onOpenOutChart: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRestore: () -> Unit,
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
                color = QuietText,
                letterSpacing = 1.sp,
            )
            Text(
                text = if (listening) "listening" else "paused",
                fontSize = 13.sp,
                color = if (listening) AccentGreen else QuietText,
            )
        }

        Text(
            text = remaining.toString(),
            fontSize = 88.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = PrimaryText,
        )

        if (double.isNotEmpty()) {
            Text(text = double, fontSize = 16.sp, color = AccentAmber)
        }

        if (message.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBackground, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = message,
                    fontSize = 18.sp,
                    color = AccentAmber,
                )
            }
        }

        if (turnDarts > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Turn: $turnDarts dart${if (turnDarts == 1) "" else "s"}",
                    fontSize = 14.sp,
                    color = QuietText,
                )
                Text(
                    text = "Scored $turnScore",
                    fontSize = 14.sp,
                    color = QuietText,
                )
            }
        }

        if (suggestion.isNotEmpty()) {
            Text(
                text = if (planKind.isNotEmpty()) planKind.uppercase() else "SUGGESTION",
                fontSize = 13.sp,
                color = QuietText,
                letterSpacing = 1.sp,
            )
            Text(
                text = if (planKind.isNotEmpty()) {
                    suggestion.lineSequence().drop(1).joinToString("\n")
                } else {
                    suggestion
                },
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                color = PrimaryText,
            )
        }

        Text(
            text = "Turns: $turns",
            fontSize = 14.sp,
            color = QuietText,
        )

        if (turnDarts > 0) {
            Text(
                text = if (correctionAvailable) {
                    "Missed the target? Say “I hit single 8 instead” and the " +
                        "coach will correct the dart and re-plan the rest of " +
                        "the turn."
                } else {
                    "Missed-target corrections are part of Pro. Say “I hit " +
                        "single 8 instead” to use it."
                },
                fontSize = 13.sp,
                color = if (correctionAvailable) QuietText else AccentAmber,
            )
        }

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
                    containerColor = AccentGreen,
                    contentColor = ScreenBackground,
                ),
            ) {
                Text("New game")
            }
        }

        // The out chart and the setup chart are references rather than
        // things needed mid-leg, and the profile is a setting. All three sit
        // below the buttons rather than above the score.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onOpenOutChart,
                modifier = Modifier.weight(1f),
            ) {
                Text("Out chart")
            }
            OutlinedButton(
                onClick = onOpenSetup,
                modifier = Modifier.weight(1f),
            ) {
                Text("Setup")
            }
        }

        OutlinedButton(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Your profile")
        }

        Text(
            text = "Speech and voice run on this phone. No account, no ads, " +
                "no analytics. The only network use is the Play Store, for " +
                "the optional Pro upgrade.",
            fontSize = 12.sp,
            color = QuietText,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Restoring is for a player who has paid and reinstalled. Play
            // holds the purchase, so there is nothing to restore from - the
            // button just asks Play what the account owns.
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            ) {
                Text("Restore purchase", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            ) {
                Text("Settings", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onPrivacyPolicy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Privacy", fontSize = 13.sp)
            }
        }
    }
}
