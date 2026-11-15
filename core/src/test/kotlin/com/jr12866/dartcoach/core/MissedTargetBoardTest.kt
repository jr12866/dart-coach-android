package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The missed-target correction over the whole board and at every kind of
 * score.
 *
 * The Pixel 7 test in `docs/MISSED-TARGET.md` covered a setup plan at 501
 * and a handful of spoken forms. This covers the rest of what the feature
 * can be asked to do: every number on the board, the bull, the checkout
 * branch, and the scores where the plan changes kind.
 *
 * These are core tests rather than device tests because the correction is
 * pure logic. The device still has to be checked for the spoken forms, but
 * the arithmetic does not need a phone.
 */
class MissedTargetBoardTest {

    /**
     * A coach standing at `remaining`, with one treble 20 already in the
     * turn.
     *
     * The aim list is populated so that the intended target can be inferred
     * when the player does not name it. The treble is there to be the dart
     * the correction replaces.
     */
    private fun coachAt(remaining: Int): CoachEngine {
        val coach = CoachEngine()
        coach.state.recordTurn(501 - remaining)
        coach.handle("what's my score")
        coach.handle("triple 20")
        return coach
    }

    /**
     * A coach standing at `remaining`, with `darts` already in the turn.
     *
     * Every dart is a treble 20, so this is only for tests where the turn
     * cannot bust: 60 three times is 180, which is above any checkout score
     * but below 501.
     */
    private fun coachWithTrebles(remaining: Int, darts: Int): CoachEngine {
        val coach = CoachEngine()
        coach.state.recordTurn(501 - remaining)
        coach.handle("what's my score")
        for (i in 0 until darts) {
            coach.handle("triple 20")
        }
        return coach
    }

    // -- the whole board ---------------------------------------------------

    @Test
    fun `every number on the board can be reported as a landing`() {
        for (n in 1..20) {
            val result = Parser.parse("I hit single $n instead", GameState())
            assertEquals("report_miss", result.command, "for $n")
            assertEquals(
                Segment(n, 1),
                result.actualSegment,
                "single $n should be the landing",
            )
        }
    }

    @Test
    fun `every number on the board can be reported as a bare landing`() {
        for (n in 1..20) {
            val result = Parser.parse("I hit $n instead", GameState())
            assertEquals("report_miss", result.command, "for $n")
            assertEquals(
                Segment(n, 1),
                result.actualSegment,
                "bare $n should be the landing",
            )
        }
    }

    @Test
    fun `every double on the board can be reported as a landing`() {
        for (n in 1..20) {
            val result = Parser.parse("I hit double $n instead", GameState())
            assertEquals("report_miss", result.command, "for D$n")
            assertEquals(
                Segment(n, 2),
                result.actualSegment,
                "double $n should be the landing",
            )
        }
    }

    @Test
    fun `every treble on the board can be reported as a landing`() {
        for (n in 2..20) {
            val result = Parser.parse("I hit treble $n instead", GameState())
            assertEquals("report_miss", result.command, "for T$n")
            assertEquals(
                Segment(n, 3),
                result.actualSegment,
                "treble $n should be the landing",
            )
        }
    }

    @Test
    fun `the bull can be named as a landing`() {
        val result = Parser.parse("I hit the bull instead", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(25, 2), result.actualSegment)
    }

    @Test
    fun `the outer bull can be named as a landing`() {
        val result = Parser.parse("I hit outer bull instead", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(25, 1), result.actualSegment)
    }

    @Test
    fun `the bull can be named as a landing by its value`() {
        assertEquals(
            Segment(25, 2),
            Parser.parse("I hit 50 instead", GameState()).actualSegment,
        )
        assertEquals(
            Segment(25, 1),
            Parser.parse("I hit 25 instead", GameState()).actualSegment,
        )
    }

    @Test
    fun `the bull is the dart nearest the verb`() {
        // "I missed the bull and hit single 8" is the bull missed and the
        // eight landed. Reading it the other way round would be nonsense:
        // the player would be told they hit the eight they said they
        // missed.
        val result = Parser.parse("I missed the bull and hit single 8", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(25, 2), result.intendedSegment)
        assertEquals(Segment(8, 1), result.actualSegment)
    }

    @Test
    fun `the bull is the landing when it comes first`() {
        // "I hit the bull and single 8 instead" names two darts, and the
        // one nearest the verb is the landing.
        val result = Parser.parse("I hit the bull and single 8 instead", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(25, 2), result.actualSegment)
    }

    @Test
    fun `the bull can be named after the verb`() {
        val result = Parser.parse(
            "I missed double 16 and hit the bull instead",
            GameState(),
        )
        assertEquals("report_miss", result.command)
        assertEquals(Segment(16, 2), result.intendedSegment)
        assertEquals(Segment(25, 2), result.actualSegment)
    }

    @Test
    fun `the outer bull can be named after the verb`() {
        val result = Parser.parse(
            "I missed treble 20 and hit outer bull",
            GameState(),
        )
        assertEquals("report_miss", result.command)
        assertEquals(Segment(20, 3), result.intendedSegment)
        assertEquals(Segment(25, 1), result.actualSegment)
    }

    @Test
    fun `the bull can be named by its value after the verb`() {
        val result = Parser.parse("I missed double 16 and hit 25", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(16, 2), result.intendedSegment)
        assertEquals(Segment(25, 1), result.actualSegment)
    }

    @Test
    fun `the coach says what the bull was`() {
        val coach = coachAt(301)
        val said = coach.handle("I hit the bull instead")
        assertNotNull(said)
        assertTrue(
            said.contains("double bull"),
            "expected the double bull to be named, got: $said",
        )
        assertEquals(listOf(50), coach.state.currentDarts)
    }

    @Test
    fun `a landing on every single bed replaces the last dart`() {
        for (n in 1..20) {
            val coach = coachAt(301)
            coach.handle("I hit single $n instead")
            assertEquals(
                listOf(n),
                coach.state.currentDarts,
                "single $n should have replaced the dart",
            )
        }
    }

    // -- the target and the landing are not the same thing -----------------

    @Test
    fun `a missed target alone is not a landing`() {
        // The player said they missed D16 and did not say what landed. The
        // coach must not treat D16 as the landing: that would tell the
        // player they hit the very double they said they missed.
        val result = Parser.parse("I missed double 16", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(16, 2), result.intendedSegment)
        assertNull(result.actualSegment)
    }

    @Test
    fun `a missed target with instead is still not a landing`() {
        // "instead" does not make the dart a landing. The verb in front of
        // it does.
        val result = Parser.parse("I missed single 8 instead", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(8, 1), result.intendedSegment)
        assertNull(result.actualSegment)
    }

    @Test
    fun `a missed target alone leaves the turn alone`() {
        val coach = coachAt(301)
        val said = coach.handle("I missed double 16")
        assertNotNull(said)
        assertEquals(listOf(60), coach.state.currentDarts)
        assertTrue(
            said.contains("what you hit"),
            "expected a request for the landing, got: $said",
        )
    }

    @Test
    fun `a missed target with a landing has both`() {
        val result = Parser.parse("I missed double 16 and hit single 8", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(16, 2), result.intendedSegment)
        assertEquals(Segment(8, 1), result.actualSegment)
    }

    @Test
    fun `a landing named after hit is the landing`() {
        val result = Parser.parse("I hit single 8 instead", GameState())
        assertEquals("report_miss", result.command)
        assertNull(result.intendedSegment)
        assertEquals(Segment(8, 1), result.actualSegment)
    }

    @Test
    fun `a landing that cannot be read is asked for rather than guessed`() {
        // Whisper was heard to write the spoken "8" as "eat" on the device.
        // The coach has to ask, because saying nothing leaves the player at
        // the oche with no idea the coach had not understood.
        val coach = coachAt(301)
        val said = coach.handle("I hit single eat instead")
        assertNotNull(said)
        assertEquals(listOf(60), coach.state.currentDarts)
        assertTrue(
            said.contains("didn't catch"),
            "expected a request to repeat, got: $said",
        )
    }

    @Test
    fun `a refused correction during a read-back does not re-ask the score`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("double 16")
        coach.handle("single 20")
        assertEquals(112, coach.pendingScore)

        val said = coach.handle("I hit single eat instead")
        assertNotNull(said)
        assertEquals(112, coach.pendingScore)
        assertEquals(listOf(60, 32, 20), coach.state.currentDarts)
        assertTrue(
            !said.contains("You said"),
            "the score was not changed, so it should not be read back again: $said",
        )
    }

    // -- the checkout branch -----------------------------------------------

    @Test
    fun `a correction at a checkout score is a checkout`() {
        val coach = coachAt(100)
        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(said.contains("Checkout"), "expected a checkout, got: $said")
        assertTrue(
            coach.suggestion.startsWith("Checkout\n"),
            "expected a checkout plan, got: ${coach.suggestion}",
        )
    }

    @Test
    fun `a correction at a setup score is a setup`() {
        val coach = coachAt(301)
        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(said.contains("Setup"), "expected a setup, got: $said")
    }

    @Test
    fun `a correction can turn a setup into a checkout`() {
        // Standing on 100 with two darts in the turn, a treble 20 and a
        // single 15 leave 25 with one dart to go. Twenty-five is not a
        // double, so it cannot be finished: a setup.
        val coach = CoachEngine()
        coach.state.recordTurn(501 - 100)
        coach.handle("what's my score")
        coach.handle("triple 20")
        coach.handle("single 15")
        val setup = coach.handle("I hit single 15 instead")
        assertNotNull(setup)
        assertTrue(setup.contains("Setup"), "expected a setup, got: $setup")

        // The same turn with the last dart corrected to a single 20 leaves
        // 20 with one dart to go, which is a double ten: a checkout.
        val coach2 = CoachEngine()
        coach2.state.recordTurn(501 - 100)
        coach2.handle("what's my score")
        coach2.handle("triple 20")
        coach2.handle("single 15")
        val checkout = coach2.handle("I hit single 20 instead")
        assertNotNull(checkout)
        assertTrue(
            checkout.contains("Checkout"),
            "expected a checkout, got: $checkout",
        )
    }

    @Test
    fun `a correction can turn a checkout into a setup`() {
        // Standing on 168 with two darts left, a treble 20 leaves 108 with
        // two darts to go, which is a checkout: treble 19 and treble 17.
        val coach = CoachEngine()
        coach.state.recordTurn(501 - 168)
        coach.handle("what's my score")
        coach.handle("triple 20")
        val checkout = coach.handle("I hit single 8 instead")
        assertNotNull(checkout)
        assertTrue(
            checkout.contains("Setup"),
            "160 cannot be finished with two darts, so this is a setup: $checkout",
        )
    }

    @Test
    fun `a correction at a bogey score is a setup`() {
        val coach = coachAt(162)
        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(said.contains("Setup"), "expected a setup, got: $said")
    }

    @Test
    fun `a correction that cannot be finished says so`() {
        // Standing on 2, a single 8 would bust, and a single 1 leaves 1,
        // which no double can take.
        val coach = coachAt(2)
        coach.handle("triple 20")
        val said = coach.handle("I hit single 1 instead")
        assertNotNull(said)
        assertTrue(
            said.contains("no finish") || said.contains("Bust"),
            "expected a bust or no finish, got: $said",
        )
    }

    @Test
    fun `the plan after a correction holds only the darts that are left`() {
        for (darts in 1..3) {
            val coach = coachWithTrebles(301, darts)
            val said = coach.handle("I hit single 8 instead")
            assertNotNull(said)
            val left = 3 - darts
            assertTrue(
                said.contains("$left dart${if (left == 1) "" else "s"} left"),
                "expected $left darts left, got: $said",
            )
        }
    }

    @Test
    fun `the leg score does not move until the turn is confirmed`() {
        val coach = coachAt(301)
        val turnsBefore = coach.state.turnsPlayed
        coach.handle("I hit single 8 instead")
        assertEquals(301, coach.state.remainingScore)
        assertEquals(8, coach.state.currentTurnScore())

        // The turn is not finished, so there is no read-back waiting and a
        // "yes" answers nothing. The leg score does not move.
        coach.handle("yes")
        assertEquals(301, coach.state.remainingScore)
        assertEquals(turnsBefore, coach.state.turnsPlayed)

        // The rest of the turn, and then the confirmation.
        coach.handle("single 20")
        coach.handle("single 20")
        assertEquals(48, coach.pendingScore)
        coach.handle("yes")
        assertEquals(253, coach.state.remainingScore)
        assertEquals(turnsBefore + 1, coach.state.turnsPlayed)
    }

    @Test
    fun `the miss is learned against the target the coach aimed at`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("I hit single 8 instead")

        assertEquals(1, coach.learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(1, coach.learner.stats.hitsFor(Segment(8, 1)))
    }

    @Test
    fun `a miss is learned against the target the player named`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("I missed double 16 and hit single 8")

        assertEquals(1, coach.learner.stats.attemptsFor(Segment(16, 2)))
        assertEquals(1, coach.learner.stats.hitsFor(Segment(8, 1)))
    }
}
