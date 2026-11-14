package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The coach's confirmation rules.
 *
 * These are the rules that earned their place from live failures on the
 * Windows build. The parity corpus checks the parser and the planner; this
 * checks the state machine that sits on top of them, which the corpus does
 * not cover.
 */
class CoachEngineTest {

    // -- nothing counts on the first pass ----------------------------------

    @Test
    fun `a score is read back and waits for a yes`() {
        val coach = CoachEngine()

        val first = coach.handle("125")
        assertNotNull(first)
        assertEquals("You said one hundred and twenty-five. Right?", first)
        assertEquals(125, coach.pendingScore)

        // The score has not counted yet.
        assertEquals(501, coach.state.remainingScore)
        assertEquals(0, coach.state.turnsPlayed)

        coach.handle("yes")
        assertNull(coach.pendingScore)
        assertEquals(376, coach.state.remainingScore)
        assertEquals(1, coach.state.turnsPlayed)
    }

    @Test
    fun `a score can be refused`() {
        val coach = CoachEngine()

        coach.handle("125")
        assertEquals(125, coach.pendingScore)

        coach.handle("no")
        assertNull(coach.pendingScore)
        assertEquals(501, coach.state.remainingScore)
        assertEquals(0, coach.state.turnsPlayed)
    }

    // -- the wake word ------------------------------------------------------

    @Test
    fun `a destructive command needs the coach's name`() {
        val coach = CoachEngine()

        // The coach asks.
        coach.handle("undo")
        assertEquals("undo", coach.pendingCommand)

        // A bare "yes" is not enough. A 440 Hz tone was once transcribed as
        // "I'm going to go back" and undid a real turn, so refusing to act on
        // a bare yes is the whole point.
        coach.handle("yes")
        assertEquals("undo", coach.pendingCommand)
        assertEquals(501, coach.state.remainingScore)
        assertEquals(0, coach.state.turnsPlayed)

        // "coach yes" is enough: the question is answered and the command
        // runs. There are no turns to undo, so the coach says so.
        coach.handle("coach yes")
        assertNull(coach.pendingCommand)
        assertTrue(
            coach.message.contains("Nothing to undo"),
            "expected 'nothing to undo', got: ${coach.message}",
        )
        assertEquals(0, coach.state.turnsPlayed)
    }

    @Test
    fun `the coach's name can be on either side of the answer`() {
        val coach = CoachEngine()
        coach.handle("new game")
        assertEquals("new_game", coach.pendingCommand)

        coach.handle("yes coach")
        assertNull(coach.pendingCommand)
    }

    @Test
    fun `a leading wake word is stripped from a score`() {
        val coach = CoachEngine()

        val said = coach.handle("coach, one eighty")
        assertNotNull(said)
        assertEquals("You said one hundred and eighty. Right?", said)
    }

    // -- superseded questions ----------------------------------------------

    @Test
    fun `a command supersedes a pending score`() {
        val coach = CoachEngine()

        coach.handle("125")
        assertEquals(125, coach.pendingScore)

        // Saying "undo" instead of answering is that score's refusal.
        coach.handle("undo")
        assertNull(coach.pendingScore)
        assertEquals("undo", coach.pendingCommand)
    }

    @Test
    fun `a later yes cannot undo a turn by accident`() {
        val coach = CoachEngine()

        coach.handle("125")
        coach.handle("yes")
        assertEquals(376, coach.state.remainingScore)

        // The question is gone, so a stray "yes" does nothing.
        val said = coach.handle("yes")
        assertNull(said)
        assertEquals(376, coach.state.remainingScore)
    }

    // -- refusals are spoken aloud -----------------------------------------

    @Test
    fun `a refused score is spoken about`() {
        val coach = CoachEngine()

        // 179 cannot be thrown with three darts.
        val said = coach.handle("179")
        assertNotNull(said)
        assertTrue(
            said.contains("doesn't seem right"),
            "expected a correction, got: $said",
        )
    }

    @Test
    fun `conversation is ignored`() {
        val coach = CoachEngine()

        // "it was one of those days" really was said, and 1 really is a legal
        // turn, but it was not a score report.
        assertNull(coach.handle("it was one of those days"))
        assertEquals(501, coach.state.remainingScore)
    }

    @Test
    fun `conversation addressed to the coach is answered`() {
        val coach = CoachEngine()

        // The player named the coach, so the coach owns the reply even when
        // it has nothing better than "I didn't catch that".
        val said = coach.handle("coach what is the weather like")
        assertNotNull(said)
    }

    // -- bust ---------------------------------------------------------------

    @Test
    fun `a score above the remaining score is a bust`() {
        val coach = CoachEngine()
        coach.state.recordTurn(451)

        val said = coach.handle("100")
        assertNotNull(said)
        assertTrue(said.startsWith("Bust!"), "expected a bust, got: $said")
        assertEquals(50, coach.state.remainingScore)
    }

    // -- the preferred double ----------------------------------------------

    @Test
    fun `the preferred double is held for the leg`() {
        val coach = CoachEngine()

        coach.handle("i prefer double 16")
        assertEquals(16, coach.state.preferredDouble)

        coach.handle("125")
        coach.handle("yes")

        // The double is still in play.
        assertEquals(16, coach.state.preferredDouble)
    }

    @Test
    fun `a double that cannot be reached is refused and forgotten`() {
        val coach = CoachEngine()
        coach.state.recordTurn(471)

        val said = coach.handle("i prefer double 20")
        assertNotNull(said)
        assertTrue(said.contains("No shot at"), "expected a refusal, got: $said")
        assertNull(coach.state.preferredDouble)
    }

    // -- the suggestions ----------------------------------------------------

    @Test
    fun `the suggestion names the darts to throw`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)

        val said = coach.handle("125")
        assertNotNull(said)
        coach.handle("yes")

        // 170 - 125 leaves 45, which is single five then double twenty.
        val suggestion = coach.suggestion
        assertTrue(
            suggestion.contains("D20"),
            "expected a double twenty suggestion, got: $suggestion",
        )
    }

    @Test
    fun `the suggestion replaces the read-back once the score is confirmed`() {
        val coach = CoachEngine()

        coach.handle("125")
        assertEquals(
            "You said one hundred and twenty-five. Right?",
            coach.message,
        )

        coach.handle("yes")

        // The coach must say the plan, not repeat the read-back.
        assertTrue(
            coach.message.contains("Treble"),
            "expected the suggestion, got: ${coach.message}",
        )
        assertFalse(
            coach.message.contains("Is that right"),
            "the read-back should not be repeated, got: ${coach.message}",
        )
        assertEquals(376, coach.state.remainingScore)
    }

    // -- what would that leave ----------------------------------------------

    @Test
    fun `the player can ask what the plan would leave`() {
        val coach = CoachEngine()
        coach.handle("125")
        coach.handle("yes")

        val said = coach.handle("what would that leave?")
        assertNotNull(said)
        assertTrue(
            said.contains("leaves"),
            "expected a leave, got: $said",
        )
    }

    @Test
    fun `the leave question has several ways of being asked`() {
        val coach = CoachEngine()
        coach.handle("125")
        coach.handle("yes")

        for (question in listOf(
            "what would that leave?",
            "what does that leave?",
            "what will that leave?",
            "what's the leave?",
            "what leave?",
        )) {
            val said = coach.handle(question)
            assertNotNull(said, "no answer for '$question'")
            assertTrue(
                said.contains("leaves"),
                "expected a leave for '$question', got: $said",
            )
        }
    }

    @Test
    fun `a player talking to themselves is not asking about a leave`() {
        val coach = CoachEngine()

        // "leave me twenty" is a player talking to themselves. The Windows
        // build already refuses it as a score report, and it must not be
        // read as the question either.
        assertNull(coach.handle("leave me twenty"))
        assertEquals(501, coach.state.remainingScore)
    }

    @Test
    fun `the leave is the one for the score standing`() {
        val coach = CoachEngine()
        coach.handle("125")
        coach.handle("yes")

        // 501 - 125 = 376. The plan for 376 leaves something, and the
        // answer has to be about 376 rather than about the score before it.
        val said = coach.handle("what would that leave?")
        assertNotNull(said)
        val leaves = said.filter { it.isDigit() }.toString()
        assertTrue(
            leaves.isNotEmpty(),
            "expected a number in the answer, got: $said",
        )
    }

    @Test
    fun `the leave question respects a chosen double`() {
        val coach = CoachEngine()
        coach.handle("i prefer double 16")
        coach.handle("125")
        coach.handle("yes")

        val said = coach.handle("what would that leave?")
        assertNotNull(said)
        assertTrue(
            said.contains("double sixteen") || said.contains("finishes"),
            "expected the chosen double in the answer, got: $said",
        )
    }

    @Test
    fun `the leave question on a finished leg says so`() {
        val coach = CoachEngine()
        coach.state.recordTurn(501)

        val said = coach.handle("what would that leave?")
        assertNotNull(said)
        assertTrue(
            said.contains("finished"),
            "expected the leg to be finished, got: $said",
        )
    }

    @Test
    fun `finishing the leg is announced`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)

        coach.handle("170")
        coach.handle("yes")

        assertEquals(0, coach.state.remainingScore)
        assertTrue(
            coach.message.contains("Congratulations"),
            "expected congratulations, got: ${coach.message}",
        )
    }

    @Test
    fun `new game resets the leg and forgets the double`() {
        val coach = CoachEngine()
        coach.handle("i prefer double 16")
        coach.handle("125")
        coach.handle("yes")

        coach.handle("new game")
        coach.handle("coach yes")

        assertEquals(501, coach.state.remainingScore)
        assertEquals(0, coach.state.turnsPlayed)
        assertNull(coach.state.preferredDouble)
    }

    @Test
    fun `undo takes the last turn back`() {
        val coach = CoachEngine()
        coach.handle("125")
        coach.handle("yes")
        assertEquals(376, coach.state.remainingScore)

        coach.handle("undo")
        coach.handle("coach yes")

        assertEquals(501, coach.state.remainingScore)
        assertEquals(0, coach.state.turnsPlayed)
    }

    @Test
    fun `undo with nothing to undo says so`() {
        val coach = CoachEngine()
        coach.handle("undo")
        coach.handle("coach yes")

        assertTrue(
            coach.message.contains("Nothing to undo"),
            "expected 'nothing to undo', got: ${coach.message}",
        )
    }

    @Test
    fun `the score can be asked for`() {
        val coach = CoachEngine()
        coach.handle("125")
        coach.handle("yes")

        val said = coach.handle("what's my score")
        assertNotNull(said)
        assertTrue(said.contains("376"), "expected the score, got: $said")
    }

    @Test
    fun `a qualified dart report waits for the turn to finish`() {
        val coach = CoachEngine()

        // One dart of three: nothing to say yet.
        assertNull(coach.handle("triple 20"))

        // Two darts: still waiting.
        assertNull(coach.handle("double 16"))

        // The third dart completes the turn, so the score is read back.
        val said = coach.handle("single 5")
        assertNotNull(said)
        assertEquals("You said ninety-seven. Right?", said)
    }

    @Test
    fun `a full score is read back too`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)

        val said = coach.handle("170")
        assertNotNull(said)
        assertEquals("You said one hundred and seventy. Right?", said)
    }
}