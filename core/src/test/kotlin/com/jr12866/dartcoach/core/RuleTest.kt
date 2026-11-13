package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules that earned their place from a live failure.
 *
 * The parity corpus proves the Kotlin matches the Python. It does not explain
 * *why* any of these rules exist, and a rule nobody understands is a rule
 * nobody dares to change. Each test here names the failure that produced the
 * rule.
 */
class RuleTest {

    // -- the score gate ----------------------------------------------------

    @Test
    fun `a score has to be nearly the whole phrase`() {
        val state = GameState()

        // The whole phrase is a score.
        assertEquals("turn_score", Parser.parse("125", state).command)

        // Qualified darts are reported one at a time, and the coach adds
        // them up. "triple 20 double 16" is two darts, not a turn score.
        val darts = Parser.parse("triple 20 double 16", state)
        assertEquals("qualified_darts", darts.command)
        assertEquals(listOf(60, 32), darts.darts)

        // A number inside a sentence is not. "it was one of those days" was
        // really said, and 1 really is a legal turn, so the microphone heard
        // it correctly - the problem is that it was not a score report.
        assertEquals("invalid", Parser.parse("it was one of those days", state).command)
        assertEquals("invalid", Parser.parse("i need a hundred and forty", state).command)
        assertEquals("invalid", Parser.parse("leave me twenty", state).command)
    }

    @Test
    fun `a score is refused rather than ignored`() {
        val state = GameState()

        // A refusal is flagged, so the coach says something. Silence would
        // leave the player at the oche believing the score went in.
        val refused = Parser.parse("it was one of those days", state)
        assertEquals("invalid", refused.command)
        assertFalse(refused.refused, "this was never a score report at all")

        val zero = Parser.parse("0", state)
        assertEquals("invalid", zero.command)
        assertTrue(zero.refused, "zero was a score report and was refused")
    }

    @Test
    fun `a score that cannot be thrown is refused`() {
        val state = GameState()

        // 179 is not a total three darts can make. Recording it would move
        // the game to a score the player could not be standing on.
        val result = Parser.parse("179", state)
        assertEquals("invalid", result.command)
        assertTrue(result.refused)
    }

    // -- the number merge --------------------------------------------------

    @Test
    fun `one eighty is one hundred and eighty`() {
        val state = GameState()

        assertEquals(180, Parser.parse("one eighty", state).score)
        assertEquals(120, Parser.parse("one twenty", state).score)
        assertEquals(140, Parser.parse("one forty", state).score)
        assertEquals(180, Parser.parse("one hundred eighty", state).score)
        assertEquals(180, Parser.parse("one hundred and eighty", state).score)
    }

    @Test
    fun `one eighteen is nineteen, not a hundred and eighteen`() {
        val state = GameState()

        // "one eighteen" means one eighteen - 19 - because the second number
        // has to be 20 or more for "one eighty" to mean 180. Without the
        // rule "one eighteen" would be 118, which is a different turn.
        assertEquals(19, Parser.parse("one eighteen", state).score)
        assertEquals(20, Parser.parse("one nineteen", state).score)
    }

    @Test
    fun `whisper writing a compound as a decimal still parses`() {
        val state = GameState()

        // "one eighty" reaches the recogniser as "1.80".
        assertEquals(180, Parser.parse("1.80", state).score)
    }

    // -- the answers -------------------------------------------------------

    @Test
    fun `a no inside a sentence is not an answer`() {
        // Whisper writes "no" inside "one hundred" and "I know". A "no"
        // that is only part of a sentence would throw away a score that is
        // waiting to be confirmed.
        assertFalse(Parser.isConfirmation("one hundred"))
        assertFalse(Parser.isConfirmation("i know"))
        assertFalse(Parser.isCancellation("one hundred"))
    }

    @Test
    fun `all right is not an answer`() {
        // A missed "yes" leaves the question open to be answered again. An
        // unwanted "yes" starts a new game or erases a turn. The two ways to
        // get this wrong are not equal, so "all right" is left out.
        assertFalse(Parser.isConfirmation("all right"))
        assertFalse(Parser.isConfirmation("alright"))
    }

    @Test
    fun `a player who hesitates is still answering`() {
        assertTrue(Parser.isConfirmation("oh yes"))
        assertTrue(Parser.isCancellation("well, no"))
    }

    // -- the destructive commands ------------------------------------------

    @Test
    fun `new game and undo ask before they act`() {
        val state = GameState()

        // A 440 Hz tone was transcribed as "I'm going to go back" and undid
        // a real turn. The score gate cannot help here: it only judges
        // utterances that report a score, and commands are matched by name,
        // not by shape.
        val undo = Parser.parse("undo", state)
        assertEquals("undo", undo.command)
        assertTrue(undo.needsConfirmation)

        val newGame = Parser.parse("new game", state)
        assertEquals("new_game", newGame.command)
        assertTrue(newGame.needsConfirmation)

        // Everything else is safe to act on directly.
        assertFalse(Parser.parse("what's my score", state).needsConfirmation)
        assertFalse(Parser.parse("continue", state).needsConfirmation)
    }

    @Test
    fun `bust is a command in its own right`() {
        val state = GameState()

        // Until the parser implemented the word, the only way to get a bust
        // was to say a number above the remaining score.
        assertEquals("bust", Parser.parse("bust", state).command)
        assertEquals("bust", Parser.parse("busted", state).command)

        // Whisper also hears the coach say it as "Bus.".
        assertEquals("bust", Parser.parse("bus", state).command)

        // But a number alongside it is still a score.
        assertEquals("turn_score", Parser.parse("bus 40", state).command)
    }

    // -- the preferred double ----------------------------------------------

    @Test
    fun `a preference names a double, but a bare double is a dart`() {
        val state = GameState()

        assertEquals(20, Parser.parse("i prefer double 20", state).double)
        assertEquals(18, Parser.parse("i'd rather have double eighteen", state).double)

        // The bull is worth 50, and 25 is the outer bull, so it means the
        // double bull - the same way the planner reads it.
        assertEquals(50, Parser.parse("i prefer the bull", state).double)
        assertEquals(50, Parser.parse("i prefer double 25", state).double)

        // A bare "double 20" is a dart report, not a request for a
        // different finish.
        assertEquals("qualified_darts", Parser.parse("double 20", state).command)

        // A double that is not on the board names nothing.
        assertNull(Parser.parse("i prefer double 21", state).double)
    }

    // -- the planner -------------------------------------------------------

    @Test
    fun `a leave divides down to a double`() {
        // 40 gives D20, a drop to 20 gives D10, a drop to 10 gives D5. The
        // longer the chain, the more chances to hit a double.
        assertEquals(listOf("D20", "20", "D10", "10", "D5"), Darts.chain(40))
        assertEquals(listOf("D20"), Darts.chain(40).take(1))
        assertEquals(emptyList(), Darts.chain(41))
    }

    @Test
    fun `the classic checkouts are the ones everyone knows`() {
        val planner = Planner.shared

        assertEquals(listOf("T20", "T20", "Bull"), planner.planFor(170).labels)
        assertEquals(listOf("T20", "T19", "Bull"), planner.planFor(167).labels)
        assertEquals(listOf("T20", "T20", "D20"), planner.planFor(160).labels)
        assertEquals(listOf("T20", "D20"), planner.planFor(100).labels)
        assertEquals(listOf("D20"), planner.planFor(40).labels)
    }

    @Test
    fun `a bogey number cannot be finished on`() {
        // A bogey can be thrown. It just cannot be finished on.
        assertTrue(Darts.isBogey(169))
        assertTrue(Darts.isBogey(163))
        assertFalse(Darts.canFinish(169))
        assertFalse(Darts.canFinish(1))

        // 44 is a legal checkout: 4 then double twenty.
        assertTrue(Darts.canFinish(44))
        assertEquals(listOf("4", "D20"), Planner.shared.planFor(44).labels)
        assertEquals("single four and double twenty", Planner.shared.planFor(44).speak())
    }

    @Test
    fun `one point cannot be finished`() {
        // The smallest double is 2.
        val plan = Planner.shared.planFor(1)
        assertEquals(emptyList(), plan.labels)
        assertFalse(plan.finishes)
    }

    @Test
    fun `a route onto a chosen double leaves that double standing`() {
        val planner = Planner.shared

        // Above 170 the route is the setup that leaves twice the double's
        // number standing for the next visit.
        val route = planner.routeToDouble(200, 20)
        assertTrue(route != null)
        assertEquals(40, route!!.leaves)
        assertEquals(20, route.double)

        // There is no shot at a double worth more than the score standing.
        assertNull(planner.routeToDouble(30, 20))
    }

    @Test
    fun `the preferred double is part of the leg`() {
        val state = GameState()
        state.preferredDouble = 16
        state.recordTurn(45)

        assertEquals(456, state.remainingScore)
        assertEquals(16, state.preferredDouble)

        // A new game is a new leg, so the coach goes back to choosing.
        state.newGame()
        assertEquals(501, state.remainingScore)
        assertNull(state.preferredDouble)
    }
}