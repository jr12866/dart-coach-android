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

    /**
     * A correction with nothing on the books is the first dart of the turn.
     *
     * This is the case a player runs into when they report their turn as a
     * number and then correct the dart they missed. There is no dart to
     * replace, so the landing is recorded as the first dart of the turn and
     * the rest of the turn is planned around it.
     */
    @Test
    fun `a correction with no dart on the books records the landing`() {
        val coach = CoachEngine()
        coach.handle("180")
        coach.handle("yes")
        assertEquals(321, coach.state.remainingScore)

        val said = coach.handle("I miss treble 20, I hit a single 20.")
        assertNotNull(said)
        assertEquals(listOf(20), coach.state.currentDarts)
        assertEquals(20, coach.state.currentTurnScore())
        // The leg score does not move until the turn is confirmed.
        assertEquals(321, coach.state.remainingScore)
        assertTrue(said.contains("2 darts left"), "expected two darts left, got: $said")
    }

    /**
     * A correction of an out, with the turn reported as a number.
     *
     * The player reports the turn and then says what landed instead. The
     * total is corrected: the double that was missed comes off it and the
     * single that landed goes on. The new total is read back.
     */
    @Test
    fun `a correction of an out corrects the total that is pending`() {
        val coach = CoachEngine()
        coach.state.recordTurn(469)
        // The coach is playing for double sixteen from thirty-two.
        coach.learner.setAimedSegments(listOf(Segment(16, 2)))
        assertEquals(32, coach.state.remainingScore)

        // The player reports the turn as a number, and the coach reads it
        // back.
        coach.handle("32")
        assertEquals(32, coach.pendingScore)

        // And then says what actually landed.
        val said = coach.handle("I hit single 8 instead")
        assertNotNull(said)
        // Thirty-two, with the double sixteen taken off it and the single
        // eight put on.
        assertEquals(8, coach.pendingScore)
        assertTrue(said.contains("Right?"), "expected a read-back, got: $said")
        assertEquals(32, coach.state.remainingScore)
    }

    /**
     * A correction that does not add up is refused, and the total the player
     * gave stands.
     */
    @Test
    fun `a correction that does not add up leaves the total alone`() {
        val coach = CoachEngine()
        coach.handle("180")
        coach.handle("yes")
        coach.handle("20")

        // A treble twenty was not in a turn of twenty, so taking it off and
        // putting a single eight on does not make a turn.
        val said = coach.handle("I missed treble 20 and hit single 8")
        assertNotNull(said)
        assertTrue(said.contains("doesn't add up"), "expected a refusal, got: $said")
        assertEquals(20, coach.pendingScore)
    }

    /**
     * A corrected dart is learned once, not twice.
     *
     * The miss is learned when the correction is made, and the turn is
     * learned again when it is confirmed. The same dart must not be counted
     * in both, or the player would be told they miss a treble twice for the
     * one dart they missed it on.
     *
     * The count is checked on the miss landing rather than on the attempt,
     * because the attempt count also carries the darts that were hit. The
     * coach aims the treble twenty at the second dart of the turn and the
     * player hits it, so the treble twenty has two attempts against it: the
     * miss that was corrected and the hit.
     */
    @Test
    fun `a corrected dart is learned once`() {
        val coach = CoachEngine()
        coach.handle("180")
        coach.handle("yes")

        coach.handle("triple 20")
        coach.handle("I hit single 8 instead")
        assertEquals(1, coach.learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(1, coach.learner.stats.missLandingsFor(Segment(20, 3))[Segment(8, 1)])

        // The rest of the turn, reported and confirmed.
        coach.handle("triple 20")
        coach.handle("triple 20")
        coach.handle("yes")

        // The miss is still counted once, and the second dart was a hit.
        assertEquals(1, coach.learner.stats.missLandingsFor(Segment(20, 3))[Segment(8, 1)])
        assertEquals(2, coach.learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(1, coach.learner.stats.hitsFor(Segment(8, 1)))
    }

    /**
     * A dart recorded by a correction is learned once as well.
     *
     * The correction is made with nothing on the books, so the coach takes
     * the target from its own plan and records the miss against that. The
     * turn is learned again when it is confirmed, and the same dart must not
     * be counted in both.
     */
    @Test
    fun `a dart recorded by a correction is learned once`() {
        val coach = CoachEngine()
        coach.handle("180")
        coach.handle("yes")

        coach.handle("I hit single 8 instead")
        assertEquals(1, coach.learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(1, coach.learner.stats.missLandingsFor(Segment(20, 3))[Segment(8, 1)])

        coach.handle("triple 20")
        coach.handle("triple 20")
        coach.handle("yes")

        // The miss is still counted once. The second dart was aimed at the
        // treble twenty and hit, so the treble twenty has two attempts
        // against it: the miss that was corrected and the hit.
        assertEquals(1, coach.learner.stats.missLandingsFor(Segment(20, 3))[Segment(8, 1)])
        assertEquals(2, coach.learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(1, coach.learner.stats.hitsFor(Segment(8, 1)))
    }

    /**
     * A correction the parser cannot read a target from is learned against
     * the coach's own aim, and not against the dart that landed.
     *
     * This is what the Pixel 7 showed. Whisper wrote "missed treble 20" as
     * "mist-traveled 20" once, which is not a phrase the parser can read a
     * target out of, so the target has to come from the coach's plan. The
     * dart that landed was on the books by then, and taking that as the
     * target taught the coach that the single eight was a segment the player
     * hit every time - which is the opposite of what happened, and it moved
     * the coach's advice off the treble twenty.
     */
    @Test
    fun `a correction with an unreadable target is learned against the aim`() {
        val coach = CoachEngine()
        coach.handle("180")
        coach.handle("yes")

        // The first correction puts the landing on the books as the first
        // dart of the turn.
        coach.handle("I hit single 8 instead")
        assertEquals(1, coach.learner.stats.attemptsFor(Segment(20, 3)))

        // The second one names the target in words the parser cannot read.
        coach.handle("I mistraveled treble 20 and hit single 8 instead")

        // The treble twenty is the target: that is what the coach aimed at
        // and what the player says they missed.
        assertEquals(2, coach.learner.stats.attemptsFor(Segment(20, 3)))
        // The single eight is the landing, so it is a hit and not a target.
        assertEquals(0, coach.learner.stats.attemptsFor(Segment(8, 1)))
        assertEquals(2, coach.learner.stats.hitsFor(Segment(8, 1)))
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
