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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jr12866.dartcoach.core.Planner
import com.jr12866.dartcoach.core.SetupChart
import com.jr12866.dartcoach.core.SetupRow

/**
 * The setup chart: what to aim at when the leg is too high to finish from.
 *
 * This is the middle of the leg. The job here is not to score as much as
 * possible but to leave a score that can be finished from, and the chart
 * says what to aim at, what it leaves, and what to do if the first dart
 * drops into the single bed.
 *
 * The chart is the coach's own advice written down - it comes from the same
 * planner - so it cannot drift away from what the coach says at the oche.
 */
@Composable
fun SetupScreen(
    planner: Planner,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    isPro: Boolean,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Int?>(null) }

    val chart = remember(planner) { SetupChart.all(planner) }

    val wanted = query.trim().toIntOrNull() ?: selected

    // A player who has not bought Pro gets the explanation rather than a
    // table they cannot use.
    if (!isPro) {
        SetupLocked(onBack = onBack, onUpgrade = onUpgrade)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Setup",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            OutlinedButton(onClick = onBack) {
                Text("Back", color = PrimaryText)
            }
        }

        Text(
            text = "Aim for a leave you can finish from. A 180 from 350 " +
                "leaves the 170 out; from 349 it leaves 169, which nothing " +
                "can finish.",
            fontSize = 14.sp,
            color = QuietText,
        )

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                selected = null
            },
            placeholder = {
                Text("Type a score, like 250", color = QuietText)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        if (wanted != null && wanted in SetupChart.MIN_SCORE..SetupChart.MAX_SCORE) {
            val row = chart.firstOrNull { it.score == wanted }
            if (row != null) {
                SetupDetail(row = row, onUpgrade = onUpgrade, isPro = isPro)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(chart) { row ->
                SetupRowView(
                    row = row,
                    highlighted = row.score == wanted,
                    onClick = { selected = row.score },
                )
            }
        }
    }
}

/**
 * What a player who has not bought Pro sees.
 *
 * It says what the setup chart is and why it is worth having, rather than
 * showing a table they cannot use.
 */
@Composable
private fun SetupLocked(
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
                text = "Setup",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            OutlinedButton(onClick = onBack) {
                Text("Back", color = PrimaryText)
            }
        }

        Text(
            text = "The setup chart is the middle of the leg: what to aim at " +
                "from 171 to 350, so that the score you leave can be " +
                "finished from.",
            fontSize = 16.sp,
            color = PrimaryText,
        )

        Text(
            text = "It is the same advice the coach gives, written down. " +
                "Every score, what it leaves, and what to do if the first " +
                "dart drops into the single bed.",
            fontSize = 14.sp,
            color = QuietText,
        )

        Button(
            onClick = onUpgrade,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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

/**
 * One line of the setup chart.
 *
 * The score, the plan, and what it leaves. The leave is the point, so it
 * gets its own column.
 */
@Composable
private fun SetupRowView(
    row: SetupRow,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) PanelBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.score.toString(),
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            modifier = Modifier.weight(0.22f),
        )
        Text(
            text = row.aimsText,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = PrimaryText,
            modifier = Modifier.weight(0.48f),
        )
        Text(
            text = if (row.leaves > 0) row.leaves.toString() else "--",
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = if (row.leaves == 170) AccentGreen else QuietText,
            modifier = Modifier.weight(0.16f),
        )
        Text(
            text = if (row.leaves == 170) "170" else "",
            fontSize = 12.sp,
            color = AccentGreen,
            modifier = Modifier.weight(0.14f),
        )
    }
}

/**
 * The detail for one score.
 *
 * The plan, the leave, what to do if the first dart drops into the single
 * bed, and the doubles that are still on.
 */
@Composable
private fun SetupDetail(
    row: SetupRow,
    onUpgrade: () -> Unit,
    isPro: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(PanelBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "At ${row.score}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )

            Text(
                text = "Aim ${row.aimsText}",
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace,
                color = AccentGreen,
            )

            if (row.leaves > 0) {
                Text(
                    text = "That leaves ${row.leaves}.",
                    fontSize = 15.sp,
                    color = PrimaryText,
                )
            }

            Text(
                text = row.note,
                fontSize = 14.sp,
                color = if (row.leaves == 170) AccentAmber else QuietText,
            )

            if (row.fallback.isNotEmpty()) {
                Text(
                    text = "If the first dart drops to the single bed, aim " +
                        "${row.fallbackText} instead.",
                    fontSize = 14.sp,
                    color = QuietText,
                )
            }

            if (row.doubles.isNotEmpty()) {
                Text(
                    text = "Doubles from the leave: ${row.doubles.joinToString(", ")}",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = QuietText,
                )
            }
        }
    }
}
