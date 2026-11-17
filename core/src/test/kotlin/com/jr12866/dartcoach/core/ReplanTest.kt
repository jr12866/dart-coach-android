package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Planning again after the coach has been rebuilt.
 *
 * The coach is rebuilt when the player changes their own rates, and when
 * they clear what the coach had learned. Either way the advice the coach
 * gave was planned with the old numbers, so it has to be planned again -
 * otherwise the screen sits empty until the next turn, and the advice on it
 * is advice the coach no longer believes.
 */
class ReplanTest {

    @Test
    fun `the coach plans again for the score standing`() {
        val coach = CoachEngine(state = GameState(remainingScore = 361))

        val said = coach.replan()

        assertEquals(
            "361. Treble twenty, treble twenty and treble twenty.",
            said,
        )
        assertTrue(
            coach.suggestion.contains("T20 T20 T20"),
            "The suggestion was ${coach.suggestion}",
        )
    }

    @Test
    fun `a leg that is finished has nothing to plan for`() {
        val coach = CoachEngine(state = GameState(remainingScore = 0))

        // Nothing is said, because there is no score left to plan for.
        assertNull(coach.replan())
    }

    @Test
    fun `the aim of the darts already thrown is kept`() {
        val coach = CoachEngine(state = GameState(remainingScore = 361))

        // The aim as it was before the rebuild was planned.
        coach.learner.setAimedSegments(
            listOf(Segment(19, 3), Segment(19, 3), Segment(18, 3))
        )
        // One dart of the turn has been thrown at the treble nineteen.
        coach.state.recordDarts(listOf(57))

        coach.replan()

        // The dart that was thrown keeps the aim it was thrown at. If the
        // aim were replaced wholesale, the coach would learn the rest of
        // the visit against the wrong targets.
        assertEquals(Segment(19, 3), coach.learner.aimed[0])
        // The rest of the visit is planned again.
        assertEquals(3, coach.learner.aimed.size)
        assertEquals(Segment(20, 3), coach.learner.aimed[1])
    }

    @Test
    fun `the plan is remembered as what the coach aimed at`() {
        val coach = CoachEngine(state = GameState(remainingScore = 361))

        coach.replan()

        assertEquals(
            listOf(Segment(20, 3), Segment(20, 3), Segment(20, 3)),
            coach.learner.aimed,
        )
    }
}
