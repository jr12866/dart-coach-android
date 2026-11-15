package com.jr12866.dartcoach.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The setup chart.
 *
 * The chart has to agree with the coach, because the whole point of it is
 * that the plan in the chart is the plan the coach gives.
 */
class SetupChartTest {

    /** At 350 the classic 170 out is the leave. */
    @Test
    fun atThreeFiftyTheClassicOutIsTheLeave() {
        val row = SetupChart.rowFor(350)
        assertEquals(170, row.leaves)
        assertEquals("the classic 170 out", row.note)
    }

    /** At 349 the same 180 leaves a bogey, so it is not the good leave. */
    @Test
    fun atThreeFortyNineItIsNotTheGoodLeave() {
        val row = SetupChart.rowFor(349)
        assertTrue(row.leaves != 170)
        assertFalse(row.note == "the classic 170 out")
    }

    /** The bogey numbers are set up rather than finished. */
    @Test
    fun theBogeysAreSetUp() {
        // The bogey numbers are below 170, so they are not in the setup
        // chart at all - they are in the out chart, marked as bogeys. The
        // setup chart starts at 171.
        for (score in Darts.BOGEY_NUMBERS) {
            assertTrue(
                score < SetupChart.MIN_SCORE,
                "$score is below the setup chart, which starts at 171",
            )
        }
    }

    /** A score that leaves a clean score says so. */
    @Test
    fun aScoreThatLeavesACleanScoreSaysSo() {
        val row = SetupChart.rowFor(200)
        assertEquals("leaves 40 for the next turn", row.note)
    }

    /** The chart agrees with the coach's own plan. */
    @Test
    fun theChartAgreesWithTheCoach() {
        for (score in SetupChart.MIN_SCORE..SetupChart.MAX_SCORE) {
            val row = SetupChart.rowFor(score)
            val plan = Planner.shared.planFor(score)
            assertEquals(
                plan.labels,
                row.aims,
                "the chart and the coach differ at $score",
            )
            assertEquals(plan.leaves, row.leaves, "the leave differs at $score")
        }
    }

    /** Every row has a plan. */
    @Test
    fun everyRowHasAPlan() {
        val chart = SetupChart.all()
        assertEquals(331, chart.size)   // 171 to 501
        assertEquals(171, chart.first().score)
        assertEquals(501, chart.last().score)
        for (row in chart) {
            assertTrue(row.aims.isNotEmpty(), "no plan for ${row.score}")
            assertTrue(row.note.isNotEmpty(), "no note for ${row.score}")
        }
    }

    /** The good leaves are the scores that can be finished from. */
    @Test
    fun theGoodLeavesAreFinishable() {
        for (leave in SetupChart.goodLeaves()) {
            assertTrue(
                Darts.canFinish(leave),
                "$leave should be finishable",
            )
        }
    }

    /** A leave that cannot be finished from is not a good leave. */
    @Test
    fun aLeaveThatCannotBeFinishedIsNotGood() {
        val good = SetupChart.goodLeaves()
        for (bogey in Darts.BOGEY_NUMBERS) {
            assertFalse(
                good.contains(bogey),
                "$bogey cannot be finished, so it is not a good leave",
            )
        }
        // 170 is the best leave there is.
        assertTrue(
            good.contains(170),
            "170 should be a good leave",
        )
        // 40 divides down to a double, so it is a good one.
        assertTrue(
            good.contains(40),
            "40 should be a good leave",
        )
    }

    /** The fallback is the plan to switch to when the first dart drops. */
    @Test
    fun theFallbackIsThereWhenItMatters() {
        val row = SetupChart.rowFor(350)
        // A treble twenty that drops leaves the single twenty, so there is
        // a plan for that.
        assertTrue(
            row.fallback.isNotEmpty(),
            "there should be a fallback for 350",
        )
        assertTrue(row.fallbackLeaves > 0)
    }

    /** The doubles are the ones still on from the leave. */
    @Test
    fun theDoublesAreTheOnesStillAvailable() {
        val row = SetupChart.rowFor(350)
        assertTrue(
            row.doubles.contains("Bull"),
            "the 170 out is on the bull, got ${row.doubles}",
        )
    }

    /** A score outside the chart is empty rather than an error. */
    @Test
    fun aScoreOutsideTheChartIsEmpty() {
        assertTrue(SetupChart.rowFor(100).aims.isEmpty())
        assertTrue(SetupChart.rowFor(600).aims.isEmpty())
    }
}
