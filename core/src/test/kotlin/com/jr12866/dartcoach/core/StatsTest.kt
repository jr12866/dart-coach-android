package com.jr12866.dartcoach.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The statistics.
 *
 * These are the numbers a player comes back to look at, so they have to be
 * right.
 */
class StatsTest {

    /** A leg of three turns. */
    private fun leg(
        turns: Int = 3,
        scores: List<Int> = listOf(100, 120, 60),
        checkout: Int? = 60,
    ) = Leg(
        turns = turns,
        scores = scores,
        checkouts = 1,
        checkoutScore = checkout,
    )

    /** With no legs, there are no statistics rather than a crash. */
    @Test
    fun noLegsIsNoStatistics() {
        val stats = Stats.EMPTY
        assertEquals(0, stats.legCount)
        assertEquals(0, stats.turnCount)
        assertEquals(0, stats.averageTurn)
        assertEquals(0, stats.checkoutPercent)
        assertEquals(0, stats.averageCheckoutScore)
        assertEquals(0, stats.bestCheckoutScore)
    }

    /** The average turn is the mean of the turns. */
    @Test
    fun theAverageTurnIsTheMean() {
        val stats = Stats(listOf(leg(scores = listOf(100, 120, 60))))
        assertEquals(93, stats.averageTurn)
        assertEquals(3, stats.turnCount)
    }

    /** The best and worst turns are across every leg. */
    @Test
    fun theBestAndWorstAreAcrossEveryLeg() {
        val stats = Stats(
            listOf(
                leg(scores = listOf(100, 120, 60)),
                leg(scores = listOf(180, 40, 20)),
            )
        )
        assertEquals(180, stats.bestTurn)
        assertEquals(20, stats.worstTurn)
    }

    /** A ton is a turn of 100 or more. */
    @Test
    fun aTonIsAHundredOrMore() {
        val stats = Stats(
            listOf(
                leg(scores = listOf(100, 120, 60)),
                leg(scores = listOf(180, 40, 99)),
            )
        )
        // 100, 120 and 180 are tons. 99 is not.
        assertEquals(3, stats.tons)
    }

    /** The checkout percentage is the share of legs finished on a double. */
    @Test
    fun theCheckoutPercentageIsTheShareFinished() {
        val stats = Stats(
            listOf(
                leg(checkout = 40),
                leg(checkout = 32),
                leg(checkout = 170),
            )
        )
        assertEquals(3, stats.legCount)
        assertEquals(100, stats.checkoutPercent)
    }

    /** The average checkout score is the mean of what was left. */
    @Test
    fun theAverageCheckoutScoreIsTheMean() {
        val stats = Stats(
            listOf(
                leg(checkout = 40),
                leg(checkout = 32),
            )
        )
        assertEquals(36, stats.averageCheckoutScore)
        assertEquals(40, stats.bestCheckoutScore)
    }

    /** A leg finished from 170 is a perfect leg. */
    @Test
    fun aLegFromOneSeventyIsPerfect() {
        val stats = Stats(
            listOf(
                leg(checkout = 170),
                leg(checkout = 40),
            )
        )
        assertEquals(1, stats.perfectLegs)
        assertEquals(170, stats.bestCheckoutScore)
    }

    /** A leg finished from a bogey number is counted. */
    @Test
    fun aLegFromABogeyIsCounted() {
        val stats = Stats(
            listOf(
                leg(checkout = 169),
                leg(checkout = 40),
            )
        )
        assertEquals(1, stats.bogeyLegs)
    }

    /** The average leg length is the mean number of turns. */
    @Test
    fun theAverageLegLengthIsTheMean() {
        val stats = Stats(
            listOf(
                leg(turns = 10),
                leg(turns = 20),
            )
        )
        assertEquals(15, stats.averageLegLength)
    }

    // -- the view ------------------------------------------------------------

    /** The weak segments are the ones the player misses most. */
    @Test
    fun theWeakSegmentsAreTheWorstOnes() {
        val learner = HitLearner()
        // T20 is hit every time.
        repeat(10) {
            learner.setAimedSegments(listOf(Segment(20, 3)))
            learner.learn(listOf(Segment(20, 3)))
        }
        // D16 is missed every time.
        repeat(10) {
            learner.setAimedSegments(listOf(Segment(16, 2)))
            learner.learn(listOf(Segment(16, 1)))
        }

        val view = StatsView(Stats(listOf(leg())), learner)
        val weak = view.weakSegments
        assertEquals(2, weak.size)
        assertEquals("D16", weak.first().label)
        assertEquals(0, weak.first().percent)
        assertEquals("T20", weak.last().label)
        assertEquals(100, weak.last().percent)
    }

    /** A segment with no attempts is not weak, it is unknown. */
    @Test
    fun aSegmentWithNoAttemptsIsNotListed() {
        val learner = HitLearner()
        val view = StatsView(Stats(listOf(leg())), learner)
        assertTrue(view.weakSegments.isEmpty())
    }

    // -- the engine ----------------------------------------------------------

    /** A finished leg is recorded. */
    @Test
    fun aFinishedLegIsRecorded() {
        val engine = CoachEngine()
        engine.state.remainingScore = 40
        engine.handle("40")
        engine.handle("yes")
        assertEquals(1, engine.legs.size)
        assertEquals(40, engine.legs.first().checkoutScore)
    }

    /** A leg that is not finished is not recorded. */
    @Test
    fun anUnfinishedLegIsNotRecorded() {
        val engine = CoachEngine()
        engine.state.remainingScore = 100
        engine.handle("40")
        engine.handle("yes")
        assertTrue(engine.legs.isEmpty())
    }

    /** A new game does not throw away the legs that have been played. */
    @Test
    fun aNewGameKeepsTheLegs() {
        val engine = CoachEngine()
        engine.state.remainingScore = 40
        engine.handle("40")
        engine.handle("yes")
        engine.handle("new game")
        engine.handle("coach yes")
        assertEquals(1, engine.legs.size)
        assertEquals(501, engine.state.remainingScore)
    }

    // -- saving --------------------------------------------------------------

    /** The legs survive a save and a load. */
    @Test
    fun theLegsSurviveASaveAndALoad() {
        val legs = listOf(
            leg(turns = 9, scores = listOf(100, 120, 60), checkout = 40),
            leg(turns = 12, scores = listOf(180, 40, 20), checkout = 32),
        )
        val snapshot = LegsSnapshot.of(legs)
        val restored = snapshot.toLegs()

        assertEquals(2, restored.size)
        assertEquals(9, restored[0].turns)
        assertEquals(40, restored[0].checkoutScore)
        assertEquals(listOf(100, 120, 60), restored[0].scores)
        assertEquals(32, restored[1].checkoutScore)
    }

    /** An empty snapshot is not an error. */
    @Test
    fun anEmptySnapshotIsNotAnError() {
        val restored = LegsSnapshot.EMPTY.toLegs()
        assertTrue(restored.isEmpty())
    }
}
