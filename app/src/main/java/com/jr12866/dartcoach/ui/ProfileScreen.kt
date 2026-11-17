package com.jr12866.dartcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jr12866.dartcoach.core.MissLanding
import com.jr12866.dartcoach.core.ProfileView
import com.jr12866.dartcoach.core.SegmentView
import com.jr12866.dartcoach.entitlement.Entitlement
import kotlin.math.roundToInt

/**
 * How many segments the "where your misses go" panel shows.
 *
 * Eight is enough to see the pattern - a player who slides right has the
 * same handful of segments on it - without pushing the segment list off the
 * bottom of the screen.
 */
private const val MAX_MISS_LANDING_ROWS = 8

/**
 * The profile: how well the player throws, and what the coach does about
 * it.
 *
 * The coach learns from the player's own play, and this is where that
 * learning can be seen and corrected. A player who knows they cannot hit
 * D16 should be able to say so, rather than waiting for the coach to work
 * it out over several legs.
 *
 * The player's own number wins over the learned one, and the learned one is
 * still shown, so the difference is visible rather than hidden.
 *
 * Setting a hit rate by hand is free, because it makes the free coach
 * better for that player. What the coach has *learned* is the Pro part: the
 * overall on-target rate and the breakdown of where the learning came from.
 */
@Composable
fun ProfileScreen(
    view: ProfileView,
    entitlement: Entitlement,
    onBack: () -> Unit,
    onSave: (ProfileView) -> Unit,
    onUpgrade: () -> Unit,
    onOpenStats: () -> Unit,
) {
    var editing by remember { mutableStateOf<SegmentView?>(null) }

    val showAnalysis = entitlement.has(Entitlement.Feature.LEARNED_ANALYSIS)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                )
                HelpButton(HelpTopic.PROFILE)
            }
            OutlinedButton(onClick = onBack) {
                Text("Back", color = PrimaryText)
            }
        }

        OverallCard(view = view, showAnalysis = showAnalysis, onUpgrade = onUpgrade)

        // Where the misses go. This is what the coach has seen the player's
        // darts land on when they missed the bed they were aimed at: the
        // treble twenty that keeps coming off as the single five is a
        // player who slides, and the coach can only say so if it has seen
        // it.
        if (showAnalysis && view.missLandings.isNotEmpty()) {
            MissLandingCard(rows = view.missLandings)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // The statistics are the other half of the profile: what the coach
        // has worked out about the player's play.
        OutlinedButton(
            onClick = onOpenStats,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text("Your statistics")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SectionHeader("DOUBLES") }
            items(view.doubles) { segment ->
                SegmentRow(segment) { editing = segment }
            }

            item { SectionHeader("TREBLES") }
            items(view.trebles) { segment ->
                SegmentRow(segment) { editing = segment }
            }

            item { SectionHeader("SINGLES") }
            items(view.rest) { segment ->
                SegmentRow(segment) { editing = segment }
            }
        }
    }

    editing?.let { segment ->
        EditDialog(
            segment = segment,
            onDismiss = { editing = null },
            onSave = { rate ->
                editing = null
                onSave(view.withManual(segment.segment, rate))
            },
        )
    }
}

/**
 * The headline numbers.
 *
 * The overall rate is the share of darts that landed on the bed they were
 * aimed at, which is the number a player actually cares about. That is the
 * analysis the coach has done, so it is Pro.
 */
@Composable
private fun OverallCard(
    view: ProfileView,
    showAnalysis: Boolean,
    onUpgrade: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(PanelBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showAnalysis) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Darts landed on target",
                        fontSize = 15.sp,
                        color = QuietText,
                    )
                    Text(
                        text = "${view.overallRate}%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AccentGreen,
                    )
                }
                Text(
                    text = "${view.totalAttempts} dart(s) aimed, " +
                        "${view.totalAttempts - view.learner.stats.totalOnTarget} " +
                        "missed the bed they were aimed at.",
                    fontSize = 13.sp,
                    color = QuietText,
                )
            } else {
                Text(
                    text = "Set the segments you know you cannot hit, and the " +
                        "coach will work around them. Tap a segment to set " +
                        "your own rate.",
                    fontSize = 15.sp,
                    color = PrimaryText,
                )
                Text(
                    text = "Pro also works out your own hit rates from your " +
                        "play and shows where your misses are going.",
                    fontSize = 13.sp,
                    color = QuietText,
                )
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = ScreenBackground,
                    ),
                ) {
                    Text("Get Pro")
                }
            }
        }
    }
}

/**
 * Where the player's misses land.
 *
 * The coach only learns this when the player tells it where a dart actually
 * landed, so the panel is empty for a player who has never corrected a
 * dart. What it shows is worth more than a rate: a rate says how often the
 * player misses, and this says where the dart goes when they do.
 */
@Composable
private fun MissLandingCard(rows: List<MissLanding>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(PanelBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Where your misses go",
                fontSize = 15.sp,
                color = QuietText,
            )
            Text(
                text = "What your darts landed on when they missed the bed " +
                    "they were aimed at.",
                fontSize = 13.sp,
                color = QuietText,
            )
            for (row in rows.take(MAX_MISS_LANDING_ROWS)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = row.label,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        modifier = Modifier.weight(0.22f),
                    )
                    Text(
                        text = row.describe(),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentAmber,
                        modifier = Modifier.weight(0.78f),
                    )
                }
            }
            if (rows.size > MAX_MISS_LANDING_ROWS) {
                Text(
                    text = "and ${rows.size - MAX_MISS_LANDING_ROWS} more",
                    fontSize = 13.sp,
                    color = QuietText,
                )
            }
        }
    }
}

/** The heading for a group of segments. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = QuietText,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/**
 * One segment: the label, the rate the coach is using, and where that rate
 * came from.
 */
@Composable
private fun SegmentRow(segment: SegmentView, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = segment.label,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            modifier = Modifier.weight(0.22f),
        )
        Text(
            text = segment.percentText(segment.effective),
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = if (segment.isManual) AccentAmber else AccentGreen,
            modifier = Modifier.weight(0.20f),
        )
        Text(
            text = whereItCameFrom(segment),
            fontSize = 13.sp,
            color = QuietText,
            modifier = Modifier.weight(0.58f),
        )
    }
}

/**
 * Where the rate came from.
 *
 * The three cases: the player set it, the coach learned it, or it is the
 * population average.
 */
private fun whereItCameFrom(segment: SegmentView): String = when {
    segment.isManual -> "yours"
    segment.hasLearnedSomething -> "learned from ${segment.attempts} dart(s)"
    segment.attempts > 0 -> "average, ${segment.attempts} dart(s) seen"
    else -> "average"
}

/**
 * Setting a segment by hand.
 *
 * The slider is the number the coach will use, and the learned rate is
 * shown next to it so the player can see what they are changing it from.
 */
@Composable
private fun EditDialog(
    segment: SegmentView,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    // A rate the player has not set is shown as the rate the coach would
    // use, so the slider starts where the coach already is.
    var rate by remember { mutableStateOf((segment.effective * 100).roundToInt().toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBackground,
        title = {
            Text(
                text = segment.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "How often do you hit ${segment.label}?",
                    fontSize = 15.sp,
                    color = QuietText,
                )

                Text(
                    text = "${rate.roundToInt()}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentGreen,
                )

                Slider(
                    value = rate,
                    onValueChange = { rate = it },
                    valueRange = 0f..100f,
                    steps = 19,
                )

                Text(
                    text = "The coach is using " +
                        "${segment.percentText(segment.effective)} for this. " +
                        "Set it here and the coach will use your number instead.",
                    fontSize = 13.sp,
                    color = QuietText,
                )

                if (segment.learned != segment.effective) {
                    Text(
                        text = "The coach has learned " +
                            "${segment.percentText(segment.learned)} from " +
                            "${segment.attempts} dart(s) at this.",
                        fontSize = 13.sp,
                        color = QuietText,
                    )
                }

                if (segment.isManual) {
                    Text(
                        text = "Clear this to go back to what the coach has learned.",
                        fontSize = 13.sp,
                        color = QuietText,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(rate.roundToInt() / 100.0) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = ScreenBackground,
                ),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (segment.isManual) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("Clear", color = AccentAmber)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = QuietText)
                }
            }
        },
    )
}
