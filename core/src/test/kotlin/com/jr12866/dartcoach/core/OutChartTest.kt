package com.jr12866.dartcoach.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The out chart.
 *
 * The chart is the coach's own advice written down, so these tests check it
 * against what the coach would actually say.
 */
class OutChartTest {

    /** 170 is finished with treble twenty, treble twenty and the bull. */
    @Test
    fun theTopScoreIsTheClassicOut() {
        val row = OutChart.rowFor(170)
        assertEquals(listOf("T20", "T20", "Bull"), row.route)
        assertFalse(row.bogey)
        assertTrue(row.probability > 0.0)
    }

    /** 160 is finished on double twenty. */
    @Test
    fun oneSixtyIsADoubleTwenty() {
        val row = OutChart.rowFor(160)
        assertEquals(listOf("T20", "T20", "D20"), row.route)
    }

    /** 100 is a split: double ten, double thirteen or double twenty. */
    @Test
    fun oneHundredIsASplit() {
        val row = OutChart.rowFor(100)
        assertTrue(
            row.doubles.containsAll(listOf("D10", "D13", "D20")),
            "expected the three doubles, got ${row.doubles}",
        )
    }

    /** The bogey numbers are marked as bogeys rather than left out. */
    @Test
    fun theBogeysAreMarked() {
        for (score in Darts.BOGEY_NUMBERS) {
            val row = OutChart.rowFor(score)
            assertTrue(row.bogey, "$score should be a bogey")
            assertEquals("--", row.percentText)
        }
    }

    /** A bogey still says what to aim at. */
    @Test
    fun aBogeyStillHasAPlan() {
        val row = OutChart.rowFor(169)
        assertTrue(row.bogey)
        assertTrue(row.route.isNotEmpty(), "a bogey should still have a plan")
    }

    /** The chart covers every score from 2 to 170. */
    @Test
    fun theChartCoversEveryScore() {
        val chart = OutChart.all()
        assertEquals(169, chart.size)
        assertEquals(2, chart.first().score)
        assertEquals(170, chart.last().score)
        // Every row must have a route, including the bogeys.
        for (row in chart) {
            assertTrue(row.route.isNotEmpty(), "no route for ${row.score}")
        }
    }

    /** The chart agrees with the coach's own plan. */
    @Test
    fun theChartAgreesWithTheCoach() {
        for (score in 2..170) {
            val row = OutChart.rowFor(score)
            val plan = Planner.shared.planFor(score)
            assertEquals(
                plan.labels,
                row.route,
                "the chart and the coach differ at $score",
            )
        }
    }

    /** A route onto a chosen double is found. */
    @Test
    fun aRouteOntoAChosenDoubleIsFound() {
        val route = OutChart.routeOnto(40, 20)
        assertNotNull(route)
        assertEquals(listOf("D20"), route.route)
        assertTrue(route.finishes)
    }

    /** Every route onto every reachable double is found. */
    @Test
    fun everyRouteIsFound() {
        val routes = OutChart.routesFor(100)
        assertTrue(routes.size >= 2, "100 has more than one way out")
        // The best route comes first.
        for (i in 1 until routes.size) {
            assertTrue(
                routes[i - 1].probability >= routes[i].probability,
                "the routes are not in order",
            )
        }
    }

    /** A personalised chart uses the player's own numbers. */
    @Test
    fun aPersonalisedChartUsesThePlayerOwnNumbers() {
        val strong = Planner.build(PlayerProfile(doubleHit = mapOf(20 to 0.99)))
        val weak = Planner.build(PlayerProfile(doubleHit = mapOf(20 to 0.10)))

        val strongRow = OutChart.rowFor(160, strong)
        val weakRow = OutChart.rowFor(160, weak)

        assertTrue(
            strongRow.probability > weakRow.probability,
            "a good D20 should be worth more, " +
                "${strongRow.probability} against ${weakRow.probability}",
        )
    }

    /** The percentage is shown as a whole number. */
    @Test
    fun thePercentageIsAWholeNumber() {
        val row = OutChart.rowFor(100)
        assertTrue(row.percentText.endsWith("%"))
        assertFalse(row.percentText.contains("."))
    }
}
