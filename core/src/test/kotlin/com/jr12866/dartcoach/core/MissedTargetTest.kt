package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Pro missed-target correction: correct the last dart and re-plan. */
class MissedTargetTest {

    private fun coachAtScore(score: Int): CoachEngine {
        val coach = CoachEngine()
        coach.state.recordTurn(501 - score)
        // A suggestion populates the learner's aim list. The exact plan is
        // irrelevant here; the correction needs an intended target.
        coach.handle("what's my score")
        return coach
    }

    @Test
    fun `the parser recognises a missed target`() {
        val result = Parser.parse(
            "I missed double 16 and hit single 8",
            GameState(),
        )
        assertEquals("report_miss", result.command)
        assertEquals(Segment(16, 2), result.intendedSegment)
        assertEquals(Segment(8, 1), result.actualSegment)
    }

    @Test
    fun `the parser recognises hit instead`() {
        val result = Parser.parse("I hit single 8 instead", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(8, 1), result.actualSegment)
    }

    @Test
    fun `the parser recognises a bare hit with a correction word`() {
        val result = Parser.parse("I hit 8 instead", GameState())
        assertEquals("report_miss", result.command)
        assertEquals(Segment(8, 1), result.actualSegment)
    }

    @Test
    fun `whisper writing treble as trouble is still a treble`() {
        val result = Parser.parse("trouble 20", GameState())
        assertEquals("qualified_darts", result.command)
        assertEquals(listOf(60), result.darts)
        assertEquals(listOf(Segment(20, 3)), result.dartSegments)
    }

    @Test
    fun `an ordinary dart report is still a dart report`() {
        val result = Parser.parse("single 8", GameState())
        assertEquals("qualified_darts", result.command)
    }

    @Test
    fun `an ordinary score report is still a score report`() {
        val result = Parser.parse("48", GameState())
        assertEquals("turn_score", result.command)
        assertEquals(48, result.score)
    }

    @Test
    fun `replacing the last dart leaves earlier darts alone`() {
        val state = GameState()
        state.recordDarts(listOf(60, 32, 20), listOf(Segment(20, 3), Segment(16, 2), Segment(20, 1)))

        assertTrue(state.replaceLastDart(8, Segment(8, 1)))
        assertEquals(listOf(60, 32, 8), state.currentDarts)
        assertEquals(listOf(Segment(20, 3), Segment(16, 2), Segment(8, 1)), state.currentSegments)
        assertEquals(100, state.currentTurnScore())
    }

    @Test
    fun `a correction updates the turn score without moving the leg`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("double 16")

        assertEquals(2, coach.state.currentDarts.size)
        assertEquals(170, coach.state.remainingScore)

        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertEquals(listOf(60, 8), coach.state.currentDarts)
        assertEquals(68, coach.state.currentTurnScore())
        assertEquals(170, coach.state.remainingScore)
    }

    @Test
    fun `a correction names checkout or setup`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")

        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(said.contains("Checkout") || said.contains("Setup"), "expected a plan kind, got: $said")
        assertTrue(coach.suggestion.startsWith("Checkout\n") || coach.suggestion.startsWith("Setup\n"))
    }

    @Test
    fun `a corrected plan contains only the darts left`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("double 16")

        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(said.contains("1 dart left"), "expected one dart left, got: $said")
    }

    @Test
    fun `a miss is learned against the intended target`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")

        coach.handle("I missed triple 20 and hit single 8")
        assertEquals(1, coach.learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(1, coach.learner.stats.hitsFor(Segment(8, 1)))
    }

    @Test
    fun `a correction can replace a dart while the score is awaiting confirmation`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("double 16")
        coach.handle("single 20")

        assertEquals(112, coach.pendingScore)

        val said = coach.handle("I hit single 7 instead")
        assertNotNull(said)
        assertEquals(99, coach.pendingScore)
        assertEquals(listOf(60, 32, 7), coach.state.currentDarts)
        assertTrue(said.contains("You said ninety-nine"), "expected a new read-back, got: $said")
    }

    @Test
    fun `a correction before the third dart does not create a false read-back`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("double 16")

        val said = coach.handle("I hit single 7 instead")
        assertNotNull(said)
        assertNull(coach.pendingScore)
        assertEquals(listOf(60, 7), coach.state.currentDarts)
        assertTrue(!said.contains("You said"), "expected no read-back yet, got: $said")
    }

    @Test
    fun `a correction is refused when it is not available`() {
        val coach = CoachEngine()
        coach.missedTargetCorrectionAvailable = false
        coach.state.recordTurn(331)
        coach.handle("triple 20")

        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(said.contains("Pro"), "expected the Pro gate, got: $said")
        assertEquals(listOf(60), coach.state.currentDarts)
    }

    @Test
    fun `a correction without a current dart is refused clearly`() {
        val coach = CoachEngine()
        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        assertTrue(said.contains("Tell me the dart"), "expected a request, got: $said")
        assertEquals(501, coach.state.remainingScore)
    }

    @Test
    fun `a bust after a correction resets the turn`() {
        val coach = CoachEngine()
        coach.state.recordTurn(461)
        coach.handle("triple 20")

        val said = coach.handle("I hit treble 20 instead")
        assertNotNull(said)
        assertTrue(said.startsWith("Bust!"), "expected a bust, got: $said")
        assertTrue(coach.state.currentDarts.isEmpty())
        assertEquals(40, coach.state.remainingScore)
    }
}
