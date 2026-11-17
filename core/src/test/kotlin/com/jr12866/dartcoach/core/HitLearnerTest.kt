package com.jr12866.dartcoach.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Learning how the player throws.
 *
 * The coach knows what it aimed at and the player reports what landed. The
 * difference between the two is the lesson.
 */
class HitLearnerTest {

    /** The segments of a plan, as the coach aimed at them. */
    private fun segmentsOf(vararg labels: String): List<Segment> =
        labels.map { label ->
            when {
                label.startsWith("T") -> Segment(label.removePrefix("T").toInt(), 3)
                label.startsWith("D") -> Segment(label.removePrefix("D").toInt(), 2)
                label == "Bull" -> Segment(25, 2)
                label == "25" -> Segment(25, 1)
                else -> Segment(label.toInt(), 1)
            }
        }

    /** A learner that was aimed at T20 and was told it landed on T20. */
    private fun learnerFor(aimed: List<Segment>, landed: List<Segment>): HitLearner {
        val learner = HitLearner()
        learner.setAimedSegments(aimed)
        learner.learn(landed)
        return learner
    }

    /** A dart that landed where it was aimed is a hit. */
    @Test
    fun aDartThatLandsIsAHit() {
        val learner = learnerFor(
            segmentsOf("T20"),
            segmentsOf("T20"),
        )
        assertEquals(1, learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(1, learner.stats.hitsFor(Segment(20, 3)))
    }

    /** A dart that landed somewhere else is a miss on the target. */
    @Test
    fun aDartThatMissesIsAMiss() {
        val learner = learnerFor(
            segmentsOf("T20"),
            segmentsOf("20"),
        )
        assertEquals(1, learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(0, learner.stats.hitsFor(Segment(20, 3)))
        // The dart landed on the single twenty, and that is worth knowing.
        assertEquals(1, learner.stats.hitsFor(Segment(20, 1)))
    }

    /** A turn reported as a bare total says nothing about any segment. */
    @Test
    fun aBareTotalIsNotAttributed() {
        val learner = learnerFor(
            segmentsOf("T20"),
            emptyList(),
        )
        assertEquals(0, learner.stats.totalAttempts)
        assertTrue(learner.stats.seenSegments().isEmpty())
    }

    /** Darts are matched in order, as far as the two lists overlap. */
    @Test
    fun dartsAreMatchedInOrder() {
        val learner = learnerFor(
            segmentsOf("T20", "T20", "D16"),
            segmentsOf("T20", "T20"),
        )
        assertEquals(2, learner.stats.attemptsFor(Segment(20, 3)))
        assertEquals(2, learner.stats.hitsFor(Segment(20, 3)))
    }

    /** With no history the rate is the population average. */
    @Test
    fun aSegmentWithNoHistoryKeepsThePrior() {
        val learner = HitLearner()
        val rate = learner.stats.trebleRate(20, PlayerProfile.defaultTrebleHit(20))
        assertEquals(PlayerProfile.defaultTrebleHit(20), rate, 1e-12)
    }

    /**
     * One miss does not swing the rate to zero.
     *
     * The population average is worth fifteen darts, so a single miss moves
     * the rate a little rather than a lot.
     */
    @Test
    fun oneMissDoesNotSwingTheRate() {
        val learner = learnerFor(segmentsOf("T20"), segmentsOf("20"))
        val prior = PlayerProfile.defaultTrebleHit(20)
        val rate = learner.stats.trebleRate(20, prior)
        assertTrue(rate < prior, "the rate should have dropped")
        assertTrue(rate > prior * 0.5, "but not by much, was $rate")
    }

    /** A player who always misses D16 learns a low rate for it. */
    @Test
    fun aSteadyMissLearnsALowRate() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(segmentsOf("D16"))
            learner.learn(segmentsOf("16"))
        }
        val rate = learner.stats.doubleRate(16, PlayerProfile.defaultDoubleHit(16))
        assertTrue(rate < 0.3, "the rate should be low, was $rate")
    }

    /** A player who always hits D16 learns a high rate for it. */
    @Test
    fun aSteadyHitLearnsAHighRate() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(segmentsOf("D16"))
            learner.learn(segmentsOf("D16"))
        }
        val rate = learner.stats.doubleRate(16, PlayerProfile.defaultDoubleHit(16))
        assertTrue(rate > 0.8, "the rate should be high, was $rate")
    }

    /**
     * A dart that lands on a segment it was not aimed at is not a hit on it.
     *
     * The two counts are different populations. The attempts are the darts
     * thrown at the segment; the hits that matter are the darts that went
     * where they were aimed. Mixing them tells the coach the player hits a
     * segment more often than they do, and the rate it works out is not a
     * rate at all - it is a ratio of two unrelated numbers.
     */
    @Test
    fun aDartFromElsewhereIsNotAHit() {
        val learner = HitLearner()
        // Aimed at T16, and the dart slid into T20.
        learner.setAimedSegments(segmentsOf("T16"))
        learner.learn(segmentsOf("T20"))

        val t16 = PlayerProfile.defaultTrebleHit(16)
        val t20 = PlayerProfile.defaultTrebleHit(20)

        // T20 was never aimed at, so its rate is the population average: one
        // dart landing there says nothing about how the player throws.
        assertEquals(t20, learner.stats.trebleRate(20, t20), 1e-12)

        // T16 was aimed at and missed, so its rate has to fall.
        assertTrue(
            learner.stats.trebleRate(16, t16) < t16,
            "the rate should have dropped",
        )
    }

    /**
     * The rate is the darts that went where they were aimed.
     *
     * Two darts at T20, both hit, and a third dart thrown at T16 that landed
     * on T20: the rate for T20 is the two that were aimed at it and hit it,
     * not the three that landed on it.
     */
    @Test
    fun theRateCountsTheDartsThatWentWhereTheyWereAimed() {
        val learner = HitLearner()
        learner.setAimedSegments(segmentsOf("T20", "T20", "T16"))
        learner.learn(segmentsOf("T20", "T20", "T20"))

        val prior = PlayerProfile.defaultTrebleHit(20)
        val k = HitStats.PRIOR_WEIGHT
        val expected = (prior * k + 2) / (k + 2)
        assertEquals(expected, learner.stats.trebleRate(20, prior), 1e-12)
    }

    /**
     * The outer bull is learned like the double bull is.
     *
     * The counts were kept for it all along, but the rate was not read out
     * of them, so it sat at the population average whatever the player's own
     * play said.
     */
    @Test
    fun theOuterBullIsLearned() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(segmentsOf("25"))
            learner.learn(segmentsOf("20"))
        }
        val rate = learner.profile().outerBullHit!!
        assertTrue(
            rate < PlayerProfile.DEFAULT_SB_HIT,
            "thirty missed outer bulls should pull the rate down, was $rate",
        )
    }

    /** The outer bull is a bull and not a bed, so it is not pooled with the singles. */
    @Test
    fun theOuterBullIsNotPooledWithTheSingles() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(segmentsOf("25"))
            learner.learn(segmentsOf("20"))
        }
        val profile = learner.profile()
        assertEquals(
            PlayerProfile.DEFAULT_SINGLE_HIT,
            profile.singleHit!!,
            1e-12,
        )
    }

    /** With no history the outer bull is the population average. */
    @Test
    fun anOuterBullWithNoHistoryKeepsThePrior() {
        val profile = HitLearner().profile()
        assertEquals(PlayerProfile.DEFAULT_SB_HIT, profile.outerBullHit!!, 1e-12)
    }

    /** The profile carries an override only for segments that were thrown at. */
    @Test
    fun theProfileOnlyOverridesSeenSegments() {        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(segmentsOf("D16"))
            learner.learn(segmentsOf("D16"))
        }
        val profile = learner.profile()
        // One segment was thrown at, so one segment is overridden. The
        // thirty is how many times it was thrown, not how many segments
        // there are.
        assertEquals(1, profile.doubleHit.size)
        assertTrue(profile.doubleHit.containsKey(16))
        assertTrue(profile.trebleHit.isEmpty(), "no treble was thrown at")
    }

    /** A learned profile builds a planner that thinks with it. */
    @Test
    fun aLearnedProfileBuildsAPlanner() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(segmentsOf("D16"))
            learner.learn(segmentsOf("D16"))
        }
        val planner = Planner.build(learner.profile())
        val route = planner.routeToDouble(40, 16)
        assertTrue(route != null)
        assertTrue(route.probability > 0.8, "a good D16 should be likely")
    }

    // -- the parser's side --------------------------------------------------

    /** A named dart carries its segment as well as its value. */
    @Test
    fun theParserReportsSegments() {
        val result = Parser.parse("treble twenty and double sixteen", GameState())
        assertEquals("qualified_darts", result.command)
        assertEquals(listOf(60, 32), result.darts)
        assertEquals(
            listOf(Segment(20, 3), Segment(16, 2)),
            result.dartSegments,
        )
    }

    /** A bare total carries no segments. */
    @Test
    fun aBareTotalCarriesNoSegments() {
        val result = Parser.parse("one hundred and twenty five", GameState())
        assertNull(result.dartSegments)
    }

    /** The segments match the values the parity corpus checks. */
    @Test
    fun theSegmentValuesMatchTheDartValues() {
        val result = Parser.parse("treble twenty treble twenty double eight", GameState())
        assertEquals(listOf(60, 60, 16), result.darts)
        assertEquals(
            listOf(60, 60, 16),
            result.dartSegments!!.map { it.value },
        )
    }

    // -- saving and restoring -----------------------------------------------

    /** The counts survive a save and a load unchanged. */
    @Test
    fun theCountsSurviveASaveAndALoad() {
        val learner = HitLearner()
        learner.setAimedSegments(segmentsOf("T20", "D16"))
        learner.learn(segmentsOf("T20", "16"))

        val snapshot = HitStatsSnapshot.of(learner.stats)
        val restored = snapshot.toStats()

        // Both darts were thrown, and both landed somewhere.
        assertEquals(2, restored.totalAttempts)
        assertEquals(2, restored.totalHits)
        assertEquals(1, restored.attemptsFor(Segment(20, 3)))
        assertEquals(1, restored.hitsFor(Segment(20, 3)))
        // D16 was aimed at but missed, so it was not a hit on D16.
        assertEquals(1, restored.attemptsFor(Segment(16, 2)))
        assertEquals(0, restored.hitsFor(Segment(16, 2)))
        // It landed on the single sixteen instead.
        assertEquals(1, restored.hitsFor(Segment(16, 1)))
    }

    /** A restored learner carries on learning rather than starting again. */
    @Test
    fun aRestoredLearnerCarriesOn() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(segmentsOf("D16"))
            learner.learn(segmentsOf("D16"))
        }

        val restored = HitLearner(HitStatsSnapshot.of(learner.stats).toStats())
        assertEquals(30, restored.stats.totalAttempts)
        assertEquals(30, restored.stats.hitsFor(Segment(16, 2)))
    }

    /** An empty snapshot is not an error. */
    @Test
    fun anEmptySnapshotIsNotAnError() {
        val restored = HitStatsSnapshot(emptyList(), emptyList(), 0, 0).toStats()
        assertEquals(0, restored.totalAttempts)
        assertTrue(restored.seenSegments().isEmpty())
    }
}
