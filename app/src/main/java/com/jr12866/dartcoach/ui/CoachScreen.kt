package com.jr12866.dartcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The live coach.
 *
 * The screen is deliberately plain. At the oche the player wants to read the
 * score and the suggestion, not admire a design.
 *
 * The order down the screen is the order the player looks in: the score on
 * top, because it is the thing being watched; then what the coach said, in
 * a box big enough to read at arm's length; then the buttons, under the
 * message so the thumb does not have to travel past it.
 */
@Composable
fun CoachScreen(
    remaining: Int,
    turns: Int,
    suggestion: String,
    contingency: String,
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
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The score, centred at the top of the screen.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "REMAINING",
                fontSize = 13.sp,
                color = QuietText,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            LedScore(
                value = remaining,
                modifier = Modifier.fillMaxWidth(),
                height = 108.dp,
            )
            if (double.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = double,
                    fontSize = 15.sp,
                    color = AccentAmber,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (listening) "listening" else "paused",
                    fontSize = 13.sp,
                    color = if (listening) AccentGreen else QuietText,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Turn $turns",
                    fontSize = 13.sp,
                    color = QuietText,
                    letterSpacing = 1.sp,
                )
            }
        }

        // The message box: what the coach said, and what to throw.
        //
        // It has a floor as well as a height, so it reads as a box even
        // when the coach has only said something short, and it grows when
        // the coach has said something long.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBackground, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF243040), RoundedCornerShape(16.dp))
                .heightIn(min = 190.dp)
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (turnDarts > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Turn: $turnDarts dart${if (turnDarts == 1) "" else "s"}",
                            fontSize = 13.sp,
                            color = QuietText,
                        )
                        Text(
                            text = "Scored $turnScore",
                            fontSize = 13.sp,
                            color = QuietText,
                        )
                    }
                }

                Text(
                    text = if (message.isNotEmpty()) {
                        message
                    } else {
                        "Say your score and the coach will call the next darts."
                    },
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (message.isNotEmpty()) AccentAmber else QuietText,
                    textAlign = TextAlign.Start,
                )

                if (suggestion.isNotEmpty()) {
                    HorizontalDivider(color = Color(0xFF2A3644))
                    Text(
                        text = if (planKind.isNotEmpty()) planKind.uppercase() else "SUGGESTION",
                        fontSize = 12.sp,
                        color = QuietText,
                        letterSpacing = 2.sp,
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

                    // The contingency: what to do if the first dart of the
                    // plan drops into its single bed.
                    //
                    // It is quieter than the plan and smaller than it,
                    // because it is the second thing to read rather than
                    // the first. The plan is in monospace because it is a
                    // list of darts to throw; this is a sentence, so it is
                    // not.
                    if (contingency.isNotEmpty()) {
                        Text(
                            text = contingency,
                            fontSize = 14.sp,
                            color = AccentAmber,
                        )
                    }
                }

                // The hint is shown once the coach is playing a leg, which is
                // as soon as it has given a plan or a turn has been played.
                // It used to wait for a dart to be reported, but the
                // correction works whether the turn was reported dart by
                // dart or as a number, so the hint was missing exactly when
                // it was needed.
                if (turns > 0 || suggestion.isNotEmpty()) {
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
            }
        }

        // The buttons sit under the message.
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
            textAlign = TextAlign.Center,
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
