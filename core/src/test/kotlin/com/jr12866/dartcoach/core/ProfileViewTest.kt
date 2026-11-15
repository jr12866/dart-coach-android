package com.jr12866.dartcoach.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The profile view: what the coach thinks, what the player set, and how
 * much of each.
 */
class ProfileViewTest {

    /** A learner that has missed D16 thirty times. */
    private fun learnerThatMissesD16(): HitLearner {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(listOf(Segment(16, 2)))
            learner.learn(listOf(Segment(16, 1)))
        }
        return learner
    }

    /** The view of one segment, or null if it is not there. */
    private fun viewFor(view: ProfileView, segment: Segment): SegmentView? =
        view.segments.firstOrNull { it.segment == segment }

    /** The doubles come first, because they are what a player cares about. */
    @Test
    fun theDoublesComeFirst() {
        val view = ProfileView.of(HitLearner(), PlayerProfile())
        assertEquals(Segment(1, 2), view.segments.first().segment)
        assertTrue(view.segments.any { it.segment == Segment(16, 2) })
        assertTrue(view.segments.any { it.segment == Segment(20, 3) })
        assertTrue(view.segments.any { it.segment == Segment(25, 2) })
    }

    /** With no history, everything is the population average. */
    @Test
    fun withNoHistoryEverythingIsTheAverage() {
        val view = ProfileView.of(HitLearner(), PlayerProfile())
        val d20 = viewFor(view, Segment(20, 2))!!
        assertEquals(PlayerProfile.defaultDoubleHit(20), d20.effective, 1e-12)
        assertEquals(0, d20.attempts)
        assertFalse(d20.hasLearnedSomething)
    }

    /** A steady miss is learned, and the rate drops. */
    @Test
    fun aSteadyMissIsLearned() {
        val view = ProfileView.of(learnerThatMissesD16(), PlayerProfile())
        val d16 = viewFor(view, Segment(16, 2))!!
        assertTrue(d16.attempts >= 30)
        assertTrue(d16.effective < d16.prior, "the rate should have dropped")
        assertTrue(d16.hasLearnedSomething)
    }

    /** The player's own number wins over what the coach learned. */
    @Test
    fun thePlayerOwnNumberWins() {
        val view = ProfileView.of(
            learnerThatMissesD16(),
            PlayerProfile(doubleHit = mapOf(16 to 0.80)),
        )
        val d16 = viewFor(view, Segment(16, 2))!!
        assertEquals(0.80, d16.effective, 1e-12)
        assertEquals(0.80, d16.manual!!, 1e-12)
        assertTrue(d16.isManual)
        // What the coach learned is still shown, so the player can see the
        // difference.
        assertTrue(d16.learned < 0.80)
    }

    /** Setting a segment by hand puts it in the manual profile. */
    @Test
    fun settingASegmentByHandWorks() {
        val view = ProfileView.of(HitLearner(), PlayerProfile())
        val updated = view.withManual(Segment(16, 2), 0.25)
        assertEquals(0.25, updated.manual.doubleHit[16]!!, 1e-12)

        val d16 = updated.segments.first { it.segment == Segment(16, 2) }
        assertEquals(0.25, d16.effective, 1e-12)
        assertTrue(d16.isManual)
    }

    /** Clearing a segment by hand puts it back to what was learned. */
    @Test
    fun clearingASegmentByHandWorks() {
        val view = ProfileView.of(
            learnerThatMissesD16(),
            PlayerProfile(doubleHit = mapOf(16 to 0.80)),
        )
        val updated = view.withManual(Segment(16, 2), null)
        assertNull(updated.manual.doubleHit[16])

        val d16 = updated.segments.first { it.segment == Segment(16, 2) }
        assertFalse(d16.isManual)
        assertEquals(d16.learned, d16.effective, 1e-12)
    }

    /** The trebles are set separately from the doubles. */
    @Test
    fun theTreblesAreSetSeparately() {
        val view = ProfileView.of(HitLearner(), PlayerProfile())
        val updated = view.withManual(Segment(20, 3), 0.70)
        assertEquals(0.70, updated.manual.trebleHit[20]!!, 1e-12)
        assertTrue(updated.manual.doubleHit.isEmpty())
    }

    /** The overall rate is the share of darts that landed where they were aimed. */
    @Test
    fun theOverallRateIsTheShareThatLanded() {
        val learner = HitLearner()
        repeat(3) {
            learner.setAimedSegments(listOf(Segment(20, 3)))
            learner.learn(listOf(Segment(20, 3)))
        }
        repeat(1) {
            // Aimed at D16, dropped into the single sixteen. That is a miss
            // on D16, even though the dart landed on something.
            learner.setAimedSegments(listOf(Segment(16, 2)))
            learner.learn(listOf(Segment(16, 1)))
        }
        val view = ProfileView.of(learner, PlayerProfile())
        assertEquals(4, view.totalAttempts)
        // Three of the four darts landed where they were aimed, so the
        // overall rate is 75%.
        assertEquals(75, view.overallRate)
    }

    /** With nothing thrown, the overall rate is zero rather than a crash. */
    @Test
    fun noHistoryIsZeroNotACrash() {
        val view = ProfileView.of(HitLearner(), PlayerProfile())
        assertEquals(0, view.overallRate)
        assertEquals(0, view.totalAttempts)
    }

    /** The bull's prior is the double bull's chance. */
    @Test
    fun theBullPriorIsRight() {
        assertEquals(
            PlayerProfile.DEFAULT_DB_HIT,
            SegmentView.priorFor(Segment(25, 2)),
            1e-12,
        )
        assertEquals(
            PlayerProfile.DEFAULT_SB_HIT,
            SegmentView.priorFor(Segment(25, 1)),
            1e-12,
        )
    }

    /** The groups split the segments up for the screen. */
    @Test
    fun theGroupsSplitTheSegments() {
        val view = ProfileView.of(HitLearner(), PlayerProfile())
        // The doubles are D1..D20 and the double bull.
        assertEquals(21, view.doubles.size)
        // The trebles are T2..T20.
        assertEquals(19, view.trebles.size)
        // The rest is S1..S20 and the outer bull.
        assertEquals(21, view.rest.size)
        assertEquals(view.segments.size, view.doubles.size + view.trebles.size + view.rest.size)
    }

    /** The percentage is shown as a whole number. */
    @Test
    fun thePercentageIsAWholeNumber() {
        val view = SegmentView(
            segment = Segment(20, 2),
            effective = 0.4567,
            learned = 0.4567,
            manual = null,
            attempts = 10,
            hits = 5,
        )
        assertEquals("45%", view.percentText(view.effective))
    }

    /** A learned rate that barely moved is not worth showing as a lesson. */
    @Test
    fun aSmallChangeIsNotWorthShowing() {
        val learner = HitLearner()
        // One attempt at a treble that is already low. The prior pulls
        // hard, so this should not count as a lesson.
        learner.setAimedSegments(listOf(Segment(2, 3)))
        learner.learn(listOf(Segment(2, 1)))

        val view = ProfileView.of(learner, PlayerProfile())
        val t2 = viewFor(view, Segment(2, 3))!!
        assertFalse(
            t2.hasLearnedSomething,
            "a single miss should not count as a lesson, learned=${t2.learned}",
        )
    }

    /** The manual profile survives being copied about. */
    @Test
    fun theManualProfileIsNotShared() {
        val manual = PlayerProfile(doubleHit = mapOf(16 to 0.5))
        val view = ProfileView.of(HitLearner(), manual)
        val updated = view.withManual(Segment(20, 2), 0.6)
        assertEquals(0.5, manual.doubleHit[16]!!, 1e-12)
        assertNull(manual.doubleHit[20])
        assertNotNull(updated.manual.doubleHit[20])
    }
}
