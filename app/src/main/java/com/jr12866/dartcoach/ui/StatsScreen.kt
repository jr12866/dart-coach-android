package com.jr12866.dartcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.jr12866.dartcoach.core.Leg
import com.jr12866.dartcoach.core.StatsView

/**
 * The statistics.
 *
 * What the player has actually thrown, and where it is going wrong. The
 * numbers a darts player cares about are the average turn and the checkout
 * percentage, because those are what decide whether a leg is won.
 */
@Composable
fun StatsScreen(
    view: StatsView,
    isPro: Boolean,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
) {
    if (!isPro) {
        StatsLocked(onBack = onBack, onUpgrade = onUpgrade)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your statistics",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                )
                HelpButton(HelpTopic.STATS)
            }
            OutlinedButton(onClick = onBack) {
                Text("Back", color = PrimaryText)
            }
        }

        if (view.stats.legCount == 0) {
            EmptyStats()
            return@Column
        }

        // The two numbers that matter most.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeadlineCard(
                label = "Average turn",
                value = view.averageTurn.toString(),
                modifier = Modifier.weight(1f),
            )
            HeadlineCard(
                label = "Legs won",
                value = "${view.checkoutPercent}%",
                modifier = Modifier.weight(1f),
            )
        }

        StatRow("Legs played", "${view.legCount}")
        StatRow("Turns played", "${view.stats.turnCount}")
        StatRow("Average leg length", "${view.averageLegLength} turns")
        StatRow("Best turn", "${view.bestTurn}")
        StatRow("Turns of 100 or more", "${view.tons}")
        StatRow(
            "Average checkout score",
            "${view.averageCheckoutScore}",
        )
        StatRow("Best checkout", "${view.bestCheckoutScore}")

        if (view.stats.perfectLegs > 0) {
            StatRow("Legs finished from 170", "${view.stats.perfectLegs}")
        }

        if (view.stats.bogeyLegs > 0) {
            StatRow(
                "Legs finished from a bogey",
                "${view.stats.bogeyLegs}",
                warning = true,
            )
        }

        if (view.weakSegments.isNotEmpty()) {
            WeakSegments(view)
        }

        LegList(view.stats.legs)
    }
}

/** A big number at the top. */
@Composable
private fun HeadlineCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(PanelBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = QuietText,
            )
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = AccentGreen,
            )
        }
    }
}

/** One line of statistics. */
@Composable
private fun StatRow(
    label: String,
    value: String,
    warning: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground.copy(alpha = 0.5f))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = PrimaryText,
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (warning) AccentAmber else PrimaryText,
        )
    }
}

/**
 * The segments the player misses most.
 *
 * These are the ones worth practising, and they are the reason the coach
 * has been watching.
 */
@Composable
private fun WeakSegments(view: StatsView) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "WHERE YOUR MISSES GO",
            fontSize = 12.sp,
            color = QuietText,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        // Only the worst few, so the list is a shortlist rather than every
        // segment the coach has an opinion about.
        for (weak in view.weakSegments.take(8)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBackground.copy(alpha = 0.5f))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weak.label,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                )
                Text(
                    text = "${weak.percent}% on target",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (weak.percent < 50) AccentAmber else QuietText,
                )
                Text(
                    text = "${weak.onTarget}/${weak.attempts}",
                    fontSize = 13.sp,
                    color = QuietText,
                )
            }
        }
    }
}

/** The legs that have been played, most recent first. */
@Composable
private fun LegList(legs: List<Leg>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "RECENT LEGS",
            fontSize = 12.sp,
            color = QuietText,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        for (leg in legs.reversed().take(20)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBackground.copy(alpha = 0.5f))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${leg.turns} turns",
                    fontSize = 14.sp,
                    color = QuietText,
                )
                Text(
                    text = "avg ${leg.averageTurn}",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = PrimaryText,
                )
                Text(
                    text = "from ${leg.checkoutScore ?: "-"}",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AccentGreen,
                )
            }
        }
    }
}

/** What a player who has not finished a leg sees. */
@Composable
private fun EmptyStats() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No legs finished yet.\n\nThe statistics appear once you " +
                "have won a leg.",
            fontSize = 15.sp,
            color = QuietText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * What a player who has not bought Pro sees.
 *
 * It says what the statistics are and why they are worth having, rather
 * than showing numbers they cannot use.
 */
@Composable
private fun StatsLocked(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your statistics",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            OutlinedButton(onClick = onBack) {
                Text("Back", color = PrimaryText)
            }
        }

        Text(
            text = "Your own numbers: the average turn, the share of legs " +
                "you have won, and where your misses are going.",
            fontSize = 16.sp,
            color = PrimaryText,
        )

        Text(
            text = "The coach has been watching your darts. This is where it " +
                "tells you which part of the board is costing you.",
            fontSize = 14.sp,
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
            Text(
                text = "Get Pro",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
