package com.jr12866.dartcoach.core

/**
 * One leg: a game from 501 to zero.
 *
 * This is what the statistics are worked out from. It is recorded when the
 * leg is finished, so the numbers are the ones the player actually played
 * rather than a running tally that can be thrown away by an undo.
 */
data class Leg(
    /** How many turns the leg took. */
    val turns: Int,
    /** The score of each turn, in order. */
    val scores: List<Int>,
    /** The darts of each turn, when the player named them. */
    val darts: List<List<Segment>> = emptyList(),
    /** How many turns were finished on a double. */
    val checkouts: Int = 0,
    /** The score that was left on the last turn. */
    val checkoutScore: Int? = null,
) {
    /** The average score a turn. */
    val averageTurn: Int
        get() = if (scores.isEmpty()) 0 else scores.sum() / scores.size

    /** The highest turn of the leg. */
    val bestTurn: Int
        get() = scores.maxOrNull() ?: 0

    /** The lowest turn of the leg. */
    val worstTurn: Int
        get() = scores.minOrNull() ?: 0

    /** How many turns were over 100. */
    val tons: Int
        get() = scores.count { it >= 100 }

    /** The total score of the leg. */
    val total: Int
        get() = scores.sum()
}

/**
 * The player's statistics.
 *
 * These are worked out from the legs they have played. The interesting ones
 * are the checkout percentage and the on-target percentage, because those
 * are the two things that decide whether a leg is won.
 */
class Stats(
    /** The legs that have been played, oldest first. */
    val legs: List<Leg> = emptyList(),
) {
    /** How many legs have been played. */
    val legCount: Int get() = legs.size

    /** Every turn of every leg, in order. */
    val turns: List<Int> get() = legs.flatMap { it.scores }

    /** How many turns have been played. */
    val turnCount: Int get() = turns.size

    /** The average score a turn. */
    val averageTurn: Int
        get() = if (turns.isEmpty()) 0 else turns.sum() / turns.size

    /** The highest turn of any leg. */
    val bestTurn: Int get() = legs.maxOfOrNull { it.bestTurn } ?: 0

    /** The lowest turn of any leg. */
    val worstTurn: Int get() = legs.minOfOrNull { it.worstTurn } ?: 0

    /** How many turns of 100 or more. */
    val tons: Int get() = legs.sumOf { it.tons }

    /** The average number of turns a leg takes. */
    val averageLegLength: Int
        get() = if (legs.isEmpty()) 0 else legs.sumOf { it.turns } / legs.size

    /** How many legs were finished on a double. */
    val checkouts: Int get() = legs.sumOf { it.checkouts }

    /** How many legs were started. */
    val finishedLegs: Int get() = legs.size

    /**
     * The checkout percentage.
     *
     * This is the share of legs that were finished on a double. It is the
     * number that separates a player who can finish from one who cannot.
     */
    val checkoutPercent: Int
        get() = if (legs.isEmpty()) 0 else checkouts * 100 / legs.size

    /**
     * The average score left on the last turn of a leg.
     *
     * This is the average checkout score - 170 is the best, and a player who
     * regularly leaves 40 is finishing from a much easier position.
     */
    val averageCheckoutScore: Int
        get() {
            val scores = legs.mapNotNull { it.checkoutScore }
            return if (scores.isEmpty()) 0 else scores.sum() / scores.size
        }

    /**
     * The best checkout score.
     *
     * 170 is the highest there is, so anything at 170 is a perfect leg.
     */
    val bestCheckoutScore: Int
        get() = legs.mapNotNull { it.checkoutScore }.maxOrNull() ?: 0

    /** How many legs were finished from 170. */
    val perfectLegs: Int
        get() = legs.count { it.checkoutScore == Darts.MAX_CHECKOUT }

    /** How many legs were finished from a bogey number. */
    val bogeyLegs: Int
        get() = legs.count { it.checkoutScore?.let { Darts.isBogey(it) } == true }

    companion object {
        /** No legs, no statistics. */
        val EMPTY = Stats()
    }
}

/**
 * The statistics screen's view of the player.
 *
 * This is what the screen shows: the headline numbers, and the breakdown
 * of which segments are costing the most.
 */
data class StatsView(
    /** The statistics. */
    val stats: Stats,
    /** What the coach has learned about the player's accuracy. */
    val learner: HitLearner,
) {
    /** The headline numbers. */
    val averageTurn: Int get() = stats.averageTurn
    val bestTurn: Int get() = stats.bestTurn
    val tons: Int get() = stats.tons
    val legCount: Int get() = stats.legCount
    val averageLegLength: Int get() = stats.averageLegLength
    val checkoutPercent: Int get() = stats.checkoutPercent
    val averageCheckoutScore: Int get() = stats.averageCheckoutScore
    val bestCheckoutScore: Int get() = stats.bestCheckoutScore

    /**
     * The segments that are costing the player the most.
     *
     * These are the segments the coach aims at most, and the ones the
     * player misses most. They are the ones worth practising.
     */
    val weakSegments: List<WeakSegment>
        get() {
            val out = mutableListOf<WeakSegment>()
            for (segment in learner.stats.seenSegments()) {
                val attempts = learner.stats.attemptsFor(segment)
                if (attempts == 0) continue
                val onTarget = learner.stats.onTargetFor(segment)
                out.add(
                    WeakSegment(
                        segment = segment,
                        attempts = attempts,
                        onTarget = onTarget,
                        rate = onTarget.toDouble() / attempts,
                    )
                )
            }
            // The worst first, and the ones the coach aims at most first
            // among equals.
            return out.sortedWith(
                compareBy(
                    { it.rate },
                    { -it.attempts },
                    { it.segment.label },
                )
            )
        }
}

/**
 * A segment, and how well the player does on it.
 */
data class WeakSegment(
    /** The segment. */
    val segment: Segment,
    /** How many times it was aimed at. */
    val attempts: Int,
    /** How many of those landed on it. */
    val onTarget: Int,
    /** The share that landed on it. */
    val rate: Double,
) {
    /** The label: "T20", "D16". */
    val label: String get() = segment.label

    /** The rate as a percentage. */
    val percent: Int get() = (rate * 100).toInt()
}
