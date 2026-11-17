package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the player's misses land.
 *
 * The coach already knew how often the player misses a bed. What it had no
 * idea of was *where* the dart goes when they do, and that is the thing that
 * makes the board's geometry useful: a dart aimed at the treble twenty that
 * lands in the single five has gone sideways, and one that lands in the
 * single twenty has dropped short. They are different problems, and only
 * the player's own play can tell them apart.
 *
 * These tests are about the counters: that they are filled in from the
 * corrections the player makes, that they survive a restart, and that the
 * profile can show them.
 */
class MissLandingTest {

    @Test
    fun `a correction is remembered with where the dart landed`() {
        val coach = CoachEngine()
        coach.state.recordTurn(331)
        coach.handle("triple 20")
        coach.handle("I hit single 5 instead")

        assertEquals(
            mapOf(Segment(5, 1) to 1),
            coach.learner.stats.missLandingsFor(Segment(20, 3)),
        )
    }

    @Test
    fun `a dart that landed where it was aimed is not a miss landing`() {
        val stats = HitStats()
        stats.record(Segment(20, 3), Segment(20, 3))

        assertTrue(stats.missLandingsFor(Segment(20, 3)).isEmpty())
    }

    @Test
    fun `a miss on a single is not tracked`() {
        // A single is a big target and its misses say nothing about the
        // board, so the counters are for the trebles and the doubles.
        val stats = HitStats()
        stats.record(Segment(5, 1), Segment(3, 1))

        assertTrue(stats.missLandingsFor(Segment(5, 1)).isEmpty())
    }

    @Test
    fun `the counts survive a restart`() {
        val stats = HitStats()
        stats.record(Segment(20, 3), Segment(5, 1))
        stats.record(Segment(20, 3), Segment(5, 1))
        stats.record(Segment(20, 3), Segment(1, 1))
        stats.record(Segment(16, 2), Segment(8, 1))

        val restored = HitStatsSnapshot.of(stats).toStats()

        assertEquals(
            mapOf(Segment(5, 1) to 2, Segment(1, 1) to 1),
            restored.missLandingsFor(Segment(20, 3)),
        )
        assertEquals(
            mapOf(Segment(8, 1) to 1),
            restored.missLandingsFor(Segment(16, 2)),
        )
    }

    @Test
    fun `a snapshot from before the counts existed still loads`() {
        // The counts are newer than the others, so a phone that saved its
        // settings before this feature existed must still be able to load
        // them.
        val snapshot = HitStatsSnapshot(
            attempts = listOf("20,3,2"),
            hits = listOf("20,3,1"),
            totalAttempts = 2,
            totalHits = 1,
        )

        val stats = snapshot.toStats()
        assertEquals(2, stats.totalAttempts)
        assertTrue(stats.missLandingCounts().isEmpty())
    }

    @Test
    fun `the learner knows which way the darts go wrong`() {
        val learner = HitLearner()
        learner.learnMiss(Segment(20, 3), Segment(5, 1))
        learner.learnMiss(Segment(20, 3), Segment(5, 1))
        learner.learnMiss(Segment(20, 3), Segment(1, 1))
        // A dart that dropped into the bed below is not a wire miss.
        learner.learnMiss(Segment(20, 3), Segment(20, 1))

        // Only the beds either side of the target: the treble twenty's
        // neighbours are the single five and the single one.
        assertEquals(mapOf(5 to 2, 1 to 1), learner.wireMisses(Segment(20, 3)))
    }

    @Test
    fun `a miss that landed on another treble is remembered as it was`() {
        // The dart was aimed at the treble twenty and landed in the treble
        // nineteen. That is a wild one rather than a wire miss, but it still
        // says where the dart went.
        val learner = HitLearner()
        learner.learnMiss(Segment(20, 3), Segment(19, 3))

        assertEquals(
            mapOf(Segment(19, 3) to 1),
            learner.stats.missLandingsFor(Segment(20, 3)),
        )
        assertTrue(learner.wireMisses(Segment(20, 3)).isEmpty())
    }

    @Test
    fun `the profile shows where the misses go`() {
        val learner = HitLearner()
        learner.learnMiss(Segment(20, 3), Segment(5, 1))
        learner.learnMiss(Segment(16, 2), Segment(8, 1))
        learner.learnMiss(Segment(16, 2), Segment(8, 1))
        learner.learnMiss(Segment(16, 2), Segment(8, 1))

        val view = ProfileView.of(learner, PlayerProfile())

        // The worst first: the double sixteen has three misses behind it.
        assertEquals(listOf("D16", "T20"), view.missLandings.map { it.label })
        assertEquals("8 x3", view.missLandings.first().describe())
        assertEquals(3, view.missLandings.first().total)
    }

    @Test
    fun `a player who has never missed has nothing to show`() {
        val view = ProfileView.of(HitLearner(), PlayerProfile())
        assertTrue(view.missLandings.isEmpty())
    }

    @Test
    fun `the advice does not change`() {
        // The learning is a picture of the player's play, not a change to the
        // plan. The advice is what the parity corpus pins, so the wire miss
        // is not in the search yet.
        val coach = CoachEngine()
        coach.state.recordTurn(501 - 388)
        coach.handle("triple 20")
        coach.handle("I hit single 5 instead")

        val plan = Planner.shared.planFor(388, 2)
        assertEquals(listOf("T20", "T20"), plan.labels)
        assertEquals(268, plan.leaves)
    }
}
