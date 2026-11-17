package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The contingency: what to do if the first dart drops into its single bed.
 *
 * The planner has always worked this out - it is why a leave of 40 beats a
 * leave of 38 - but it kept it to itself. These tests are about the coach
 * now saying it out loud.
 */
class ContingencyTest {

    @Test
    fun `a plan knows what happens if the first dart drops`() {
        val plan = Planner.shared.planFor(388, 2)

        val contingency = assertNotNull(plan.contingency)
        // The treble twenty comes off as the single twenty: 388 - 20.
        assertEquals(368, contingency.score)
        // And then the coach's plan for the dart that is left.
        assertEquals(listOf("T20"), contingency.labels)
    }

    @Test
    fun `a turn with one dart left still has a contingency`() {
        // With one dart left there are no darts to fall back on, but there
        // is still a score to fall back on, and it is the one the player
        // needs: the dart either leaves the score the coach planned for or
        // it does not.
        val plan = Planner.shared.planFor(388, 1)

        val contingency = assertNotNull(plan.contingency)
        assertEquals(368, contingency.score)
        assertTrue(
            contingency.labels.isEmpty(),
            "there are no darts left to throw, was ${contingency.labels}",
        )
    }

    @Test
    fun `a dart that cannot drop into a bed has no contingency`() {
        // A single cannot come off as a single below it - it is already
        // the single. Neither can the outer bull.
        val plan = Planner.shared.planFor(100, 1)
        val first = plan.aims.first()

        if (first.wobbleValue == 0) {
            assertEquals(null, plan.contingency)
        }
    }

    @Test
    fun `the contingency says where you would be and what to throw`() {
        val plan = Planner.shared.planFor(388, 2)
        val contingency = assertNotNull(plan.contingency)

        // The first line is the drop into the bed, with the plan for the
        // dart that is left. The second is the sideways miss: the treble
        // twenty's neighbours on the board are the single five and the
        // single one.
        assertEquals(
            "If it drops to the single: 368, then T20\n" +
                "If it slides into 1 or 5: 387 or 383",
            contingency.line,
        )
        assertEquals(
            "If it drops to the single bed you're on three hundred and " +
                "sixty-eight, then treble twenty.",
            contingency.speech,
        )
    }

    @Test
    fun `the coach says the contingency when it re-plans the turn`() {
        val coach = CoachEngine()
        coach.handle("180")
        coach.handle("yes")
        coach.handle("triple 20")
        coach.handle("triple 20")

        // Two darts in the books, so the coach is planning the third.
        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(
            said.contains("If it drops to the single bed"),
            "expected the contingency, got: $said",
        )
        assertTrue(
            coach.contingency.isNotEmpty(),
            "expected a contingency on the screen, was: '${coach.contingency}'",
        )
    }

    @Test
    fun `the screen shows the contingency for the standing plan`() {
        val coach = CoachEngine()
        coach.handle("180")
        coach.handle("yes")

        // The coach has given a plan for the turn that is starting.
        assertTrue(
            coach.contingency.startsWith("If it drops to the single:"),
            "expected a contingency, was: '${coach.contingency}'",
        )
    }

    @Test
    fun `the advice itself does not change`() {
        // The contingency is added to what the coach says. It must not
        // change what the coach tells the player to throw, because the
        // advice is what the parity corpus pins.
        val plan = Planner.shared.planFor(388, 2)
        assertEquals(listOf("T20", "T20"), plan.labels)
        assertEquals(268, plan.leaves)
        assertEquals("treble twenty and treble twenty", plan.speak())
    }
}
