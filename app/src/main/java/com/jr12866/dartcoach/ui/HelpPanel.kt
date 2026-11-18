package com.jr12866.dartcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The help panel: a short guide to the screen you are on.
 *
 * The app is spoken to, and a spoken app has one problem a screen does not:
 * the player cannot tell from looking at it what to say. So the panel is
 * opened by a question mark rather than a "Help" label, and it is available
 * on every screen rather than only on the first one - the player who needs it
 * is usually the one who is already stuck.
 *
 * The guide is specific to the screen it is opened from. A general manual
 * would have to describe the whole app to be worth reading, and nobody reads
 * that at the oche. "Out chart" is explained on the out chart, where the
 * player is looking at a chart, and nowhere else.
 *
 * It is deliberately short. A panel that has to be scrolled through is a
 * panel nobody reads, and the points here are the ones that are genuinely
 * not guessable: what the coach is waiting for, what a percentage means, and
 * which number to set first.
 */
enum class HelpTopic(
    /** The heading of the panel. */
    val title: String,
    /** The points, in the order they are read. */
    val points: List<HelpPoint>,
) {

    /** The live coach. */
    COACH(
        "The coach",
        listOf(
            HelpPoint(
                "Say the visit",
                "Say the total you have just thrown - \"one hundred and forty\", " +
                    "or \"one eighty\". The coach works out what that leaves you.",
            ),
            HelpPoint(
                "It reads it back",
                "Nothing counts until you say yes. If it heard you wrong, say " +
                    "\"no\" and the score is discarded.",
            ),
            HelpPoint(
                "Name the darts and it learns",
                "The coach learns from the darts you name: \"treble twenty, " +
                    "treble nineteen, double sixteen\". A bare total is counted " +
                    "as a turn, but it cannot be pinned to a bed, so nothing is " +
                    "learned from it.",
            ),
            HelpPoint(
                "Correct a dart you missed",
                "\"I hit single 8 instead\" takes the missed dart off the turn " +
                    "and re-plans the rest of it.",
            ),
            HelpPoint(
                "Undo and new game",
                "Undo takes back the last turn and a new game starts again at " +
                    "501. Both ask you to say \"coach yes\" before they do it.",
            ),
            HelpPoint(
                "Headphones",
                "With headphones the coach speaks to you alone, and it never " +
                    "hears its own voice.",
            ),
        ),
    ),

    /** The out chart. */
    OUT_CHART(
        "Out chart",
        listOf(
            HelpPoint(
                "Every score from 2 to 170",
                "Type a score to see the route to take from it and the chance " +
                    "of checking out.",
            ),
            HelpPoint(
                "The percentage",
                "The chance of checking out from that score in the coming turn. " +
                    "It is worked out from your own hit rates, not from a book.",
            ),
            HelpPoint(
                "Doubles",
                "Every double you can still aim at from that score. More is " +
                    "better.",
            ),
            HelpPoint(
                "Your routes",
                "The routes onto each of your doubles, best first, with the " +
                    "chance of winning the leg that way.",
            ),
            HelpPoint(
                "A score with no finish",
                "Some scores cannot be finished in three darts. They show " +
                    "\"--\", and the chart tells you what to aim at instead.",
            ),
        ),
    ),

    /** The setup chart. */
    SETUP(
        "Setup chart",
        listOf(
            HelpPoint(
                "The middle of the leg",
                "Above 170 the score cannot be finished in a visit, so the " +
                    "coach aims for a leave it can finish from.",
            ),
            HelpPoint(
                "Type a score",
                "Type a score to see the plan and what it leaves.",
            ),
            HelpPoint(
                "The bull decides the 170",
                "170 is finished with treble twenty, treble twenty and the " +
                    "bull. The coach only sets you up for it if you can hit the " +
                    "bull, so set your rate in your profile.",
            ),
            HelpPoint(
                "The leave is the point",
                "The number under the plan is the score you hand to the next " +
                    "turn. A leave that can be finished is worth more than a " +
                    "bigger one that cannot.",
            ),
        ),
    ),

    /** The player's own rates. */
    PROFILE(
        "Your profile",
        listOf(
            HelpPoint(
                "Set your own numbers",
                "Tap a row to set the rate the coach uses for that target. It " +
                    "is free - it just makes the coach better for you.",
            ),
            HelpPoint(
                "\"yours\" and \"average\"",
                "\"yours\" is a number you set. \"average\" is what the coach " +
                    "has worked out from your own play.",
            ),
            HelpPoint(
                "Yours wins",
                "Where you have set a rate the coach uses it. Where you have " +
                    "not, it uses what it has learned, and failing that the " +
                    "population average.",
            ),
            HelpPoint(
                "Set the bull",
                "The double bull is worth setting by hand: it decides whether " +
                    "the coach aims you at the 170 out.",
            ),
        ),
    ),

    /** What the coach has worked out. */
    STATS(
        "Your statistics",
        listOf(
            HelpPoint(
                "What is here",
                "Your average turn, the share of legs you have won, and where " +
                    "your misses are going.",
            ),
            HelpPoint(
                "Where your misses go",
                "The coach watches which bed a missed dart lands in. The treble " +
                    "twenty that keeps coming off as the single five is a " +
                    "player who slides.",
            ),
            HelpPoint(
                "Recent legs",
                "The legs you have finished, with the average turn for each.",
            ),
            HelpPoint(
                "It needs a finished leg",
                "The statistics appear once you have won a leg.",
            ),
        ),
    ),

    /** The audio and the memory. */
    SETTINGS(
        "Settings",
        listOf(
            HelpPoint(
                "Echo delay",
                "How long the coach waits after speaking before it listens " +
                    "again, so it does not hear its own voice. Measure it where " +
                    "you play.",
            ),
            HelpPoint(
                "With headphones",
                "On Bluetooth headphones the coach's voice never reaches the " +
                    "microphone, so the delay is zero and the advice is private.",
            ),
            HelpPoint(
                "The coach's voice",
                "Two voices to choose from. The coach speaks with the one you " +
                    "pick, and both of them are on the phone already.",
            ),
            HelpPoint(
                "Forget what it has learned",
                "Throws away the counts the coach has built up from your play " +
                    "and puts it back on the population averages. The leg in " +
                    "play is left alone.",
            ),
            HelpPoint(
                "Nothing leaves the phone",
                "The speech, the voice and the learning all run here. The only " +
                    "network use is the Play Store, for the Pro purchase.",
            ),
        ),
    ),
}

/** One point in the panel: a heading and a sentence or two. */
data class HelpPoint(
    /** What the point is about. */
    val heading: String,
    /** The explanation. */
    val body: String,
)

/**
 * The question mark that opens the panel.
 *
 * The button holds its own state, so a screen gets its help by having one of
 * these in it rather than by plumbing a callback through the navigation.
 */
@Composable
fun HelpButton(topic: HelpTopic) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { open = true },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Text(
            text = "?",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
        )
    }

    if (open) {
        HelpDialog(topic = topic, onDismiss = { open = false })
    }
}

/**
 * The panel itself.
 *
 * It is a dialog rather than a screen, because the point of it is to answer
 * a question and get out of the way again: the player is standing at the
 * oche, and they want to be playing, not reading.
 */
@Composable
fun HelpDialog(topic: HelpTopic, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBackground,
        title = {
            Text(
                text = topic.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                for (point in topic.points) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = point.heading,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentGreen,
                        )
                        Text(
                            text = point.body,
                            fontSize = 14.sp,
                            color = QuietText,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentGreen)
            }
        },
    )
}
