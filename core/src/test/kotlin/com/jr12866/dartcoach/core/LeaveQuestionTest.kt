package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The leave question.
 *
 * "What does that leave?" is asked by a player at the board who wants the
 * number the plan leaves for the finish. The darts they have already thrown
 * count: a player standing at 341 with two darts left is not standing at the
 * 401 they were at before the treble twenty.
 */
class LeaveQuestionTest {

    /**
     * A player who finds T19 more often than T20, so the coach aims there.
     *
     * Without this the plan for 401 is treble twenty three times, and the
     * numbers in these tests cannot be told apart from the stale ones.
     */
    private fun coachAt(remaining: Int): CoachEngine {
        val profile = PlayerProfile(trebleHit = mapOf(19 to 0.75, 20 to 0.30))
        val coach = CoachEngine(planner = Planner.build(profile))
        coach.state.recordTurn(501 - remaining)
        coach.handle("what's my score")
        return coach
    }

    @Test
    fun `the leave counts the dart the player has in hand`() {
        val coach = coachAt(401)

        // The plan from 401 is treble nineteen three times, leaving 230.
        assertEquals(
            230,
            coach.planner.planFor(401).leaves,
            "the plan from 401 should leave 230",
        )

        // The player hit a treble twenty instead.
        coach.handle("treble 20")
        assertEquals(listOf(60), coach.state.currentDarts)

        val said = coach.handle("what does that leave")
        assertNotNull(said)

        // 401 - 60 = 341, with two darts left. The plan from there leaves
        // 227, not the 230 of the plan the player has already heard.
        assertTrue(
            said.contains("227"),
            "expected the leave of the plan from 341, got: $said",
        )
        assertTrue(
            !said.contains("230"),
            "the leave of the old plan should not be given, got: $said",
        )
        assertTrue(
            said.contains("three hundred and forty-one"),
            "expected the score the player is on, got: $said",
        )
        assertTrue(
            said.contains("2 darts left"),
            "expected the darts left in the visit, got: $said",
        )
    }

    @Test
    fun `the leave is unchanged when there are no darts in hand`() {
        val coach = coachAt(401)

        val said = coach.handle("what does that leave")
        assertEquals("That leaves 230.", said)
    }

    @Test
    fun `the leave after a finished visit counts the turn`() {
        val coach = coachAt(401)

        // Three trebles twenty is 180, so the player would be on 221.
        coach.handle("treble 20")
        coach.handle("treble 20")
        coach.handle("treble 20")

        val said = coach.handle("what does that leave")
        assertNotNull(said)
        assertTrue(
            said.contains("two hundred and twenty-one"),
            "expected the score the turn leaves, got: $said",
        )
        assertTrue(
            said.contains(coach.planner.planFor(221).leaves.toString()),
            "expected the leave of the plan from 221, got: $said",
        )
    }

    @Test
    fun `asking the leave does not throw the reported turn away`() {
        val coach = coachAt(401)

        coach.handle("treble 20")
        coach.handle("treble 20")
        coach.handle("treble 20")

        coach.handle("what does that leave")

        // The turn is still there, and the read-back is still the question,
        // so a "yes" still confirms it.
        assertEquals(3, coach.state.currentDarts.size)
        val confirmed = coach.handle("yes")
        assertNotNull(confirmed)
        assertEquals(
            221,
            coach.state.remainingScore,
            "the turn should have counted once it was confirmed",
        )
    }

    @Test
    fun `a turn reported as a bare total counts too`() {
        val coach = coachAt(401)

        // "one eighty" is the whole turn as a total, so there are no darts
        // in it - the total is the pending score.
        coach.handle("one eighty")

        val said = coach.handle("what does that leave")
        assertNotNull(said)
        assertTrue(
            said.contains("two hundred and twenty-one"),
            "expected the score the turn leaves, got: $said",
        )
        assertTrue(
            said.contains(coach.planner.planFor(221).leaves.toString()),
            "expected the leave of the plan from 221, got: $said",
        )
    }

    @Test
    fun `a dart that busts the leg is a bust`() {
        val coach = coachAt(40)

        coach.handle("treble 20")

        val said = coach.handle("what does that leave")
        assertNotNull(said)
        assertTrue(
            said.contains("bust"),
            "a treble twenty from 40 is a bust, got: $said",
        )
    }

    @Test
    fun `the leave follows the player's own double with the darts in hand`() {
        val coach = coachAt(100)

        coach.handle("prefer double 20")
        assertEquals(20, coach.state.preferredDouble)

        coach.handle("single 20")
        assertEquals(listOf(20), coach.state.currentDarts)

        val said = coach.handle("what does that leave")
        assertNotNull(said)
        assertTrue(
            said.contains("eighty"),
            "expected the score the player is on, got: $said",
        )
        assertTrue(
            said.contains("double twenty"),
            "expected the route onto the player's double, got: $said",
        )
    }
}
