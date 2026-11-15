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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jr12866.dartcoach.core.ChartRoute
import com.jr12866.dartcoach.core.ChartRow
import com.jr12866.dartcoach.core.OutChart
import com.jr12866.dartcoach.core.Planner
import com.jr12866.dartcoach.entitlement.Entitlement

/** The background the app uses. */
internal val ScreenBackground = Color(0xFF0B0F14)

/** The colour of the main text. */
internal val PrimaryText = Color(0xFFE8EDF2)

/** The colour of the quiet text. */
internal val QuietText = Color(0xFF7D8894)

/** The green the coach uses. */
internal val AccentGreen = Color(0xFF4ADE80)

/** The amber the coach uses for the message. */
internal val AccentAmber = Color(0xFFFFD166)

/** The colour of a panel. */
internal val PanelBackground = Color(0xFF1A222C)

/**
 * The out chart.
 *
 * Every score from 2 to 170, with the route, the chance of checking out,
 * and the doubles that can still be aimed at.
 *
 * This is a reference, not something needed mid-leg, which is why it is the
 * screen behind the paywall: a player looking up a score is a player who
 * wants more than the live coach.
 */
@Composable
fun OutChartScreen(
    planner: Planner,
    entitlement: Entitlement,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Int?>(null) }

    val chart = remember(planner) { OutChart.all(planner) }

    // A score typed in, or the one that is open.
    val wanted = query.trim().toIntOrNull() ?: selected

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
                text = "Out chart",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            OutlinedButton(onClick = onBack) {
                Text("Back", color = PrimaryText)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                selected = null
            },
            placeholder = {
                Text("Type a score, like 167", color = QuietText)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // The setup chart is the other half of this reference: what to aim
        // at before the score is low enough to finish from.
        OutlinedButton(
            onClick = onOpenSetup,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Setup chart - the middle of the leg")
        }

        if (wanted != null && wanted in OutChart.MIN_SCORE..OutChart.MAX_SCORE) {
            val row = chart.firstOrNull { it.score == wanted }
            if (row != null) {
                ChartDetail(
                    row = row,
                    routes = remember(planner, wanted) {
                        OutChart.routesFor(wanted, planner)
                    },
                    showRoutes = entitlement.has(
                        Entitlement.Feature.PERSONALISED_ROUTES
                    ),
                    onUpgrade = onUpgrade,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(chart) { row ->
                ChartRowView(
                    row = row,
                    highlighted = row.score == wanted,
                    onClick = { selected = row.score },
                )
            }
        }
    }
}

/**
 * One line of the chart.
 *
 * The score, the route, and the percentage. The percentage is the thing
 * that is different in a personalised chart, so it is the thing that is
 * given its own column.
 */
@Composable
private fun ChartRowView(
    row: ChartRow,
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
            color = if (row.bogey) QuietText else PrimaryText,
            modifier = Modifier.weight(0.22f),
        )
        Text(
            text = row.routeText,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = if (row.bogey) QuietText else PrimaryText,
            modifier = Modifier.weight(0.58f),
        )
        Text(
            text = row.percentText,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = if (row.bogey) QuietText else AccentGreen,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.20f),
        )
    }
}

/**
 * The detail for one score.
 *
 * The routes onto each double are behind the paywall, because which route
 * is best depends entirely on which double the player can hit - and that is
 * the thing the personalisation is about.
 */
@Composable
private fun ChartDetail(
    row: ChartRow,
    routes: List<ChartRoute>,
    showRoutes: Boolean,
    onUpgrade: () -> Unit,
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
                text = if (row.bogey) {
                    "${row.score} cannot be finished in three darts"
                } else {
                    "${row.score}  -  ${row.routeText}  (${row.percentText})"
                },
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (row.bogey) AccentAmber else PrimaryText,
            )

            if (row.bogey && row.route.isNotEmpty()) {
                Text(
                    text = "Aim for ${row.routeText}, which leaves ${row.leaves}.",
                    fontSize = 15.sp,
                    color = PrimaryText,
                )
            }

            if (!row.bogey && row.leaves > 0) {
                Text(
                    text = "Leaves ${row.leaves}.",
                    fontSize = 15.sp,
                    color = QuietText,
                )
            }

            if (row.doubles.isNotEmpty()) {
                Text(
                    text = "Doubles: ${row.doubles.joinToString(", ")}",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = QuietText,
                )
            }

            if (showRoutes) {
                if (routes.isEmpty()) {
                    Text(
                        text = "No route onto a double from here.",
                        fontSize = 14.sp,
                        color = QuietText,
                    )
                } else {
                    Text(
                        text = "ROUTES ONTO YOUR DOUBLES",
                        fontSize = 12.sp,
                        color = QuietText,
                        letterSpacing = 1.sp,
                    )
                    for (route in routes) {
                        Text(
                            text = "${route.doubleLabel}  ${route.routeText}  " +
                                "${route.percentText}",
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PrimaryText,
                        )
                    }
                }
            } else {
                Text(
                    text = "The routes onto each of your doubles are in Pro.",
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
                    Text("Get Pro")
                }
            }
        }
    }
}
