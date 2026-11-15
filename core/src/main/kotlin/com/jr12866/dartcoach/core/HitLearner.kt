package com.jr12866.dartcoach.core

/**
 * Learns how well the player throws, from what they actually threw.
 *
 * The coach knows what it aimed at. The player reports what landed. Those
 * two are different things, and the difference is the lesson: a dart aimed at
 * D16 that came back as a single eight is a miss on D16, and a dart aimed at
 * T20 that landed on T20 is a hit.
 *
 * The player often reports a bare total instead of naming the darts, and
 * then there is nothing to learn from that turn - the total could have come
 * from anywhere. Those turns are counted but not attributed.
 */
class HitLearner(
    /**
     * What the coach has learned.
     *
     * A learner built from saved counts starts where the player left off
     * rather than from the population averages, which is how the learning
     * survives a restart.
     */
    val stats: HitStats = HitStats(),
) {
    /**
     * The coach's advice for the current turn, as segments.
     *
     * This is what the player was aiming at when they reported the turn, so
     * it is what their darts are matched against. It is set when the coach
     * gives its suggestion.
     */
    var aimed: List<Segment> = emptyList()
        private set

    /**
     * Remembers what the coach aimed at.
     *
     * Called when the coach gives a suggestion, so the darts that come back
     * can be matched against it.
     */
    fun setAimed(aims: List<Aim>) {
        aimed = aims.map { it.segment }
    }

    /**
     * Remembers what the coach aimed at, as segments.
     *
     * This is the same thing [setAimed] does, for callers that already have
     * the segments rather than the aims.
     */
    fun setAimedSegments(segments: List<Segment>) {
        aimed = segments
    }

    /**
     * Learns from a confirmed turn.
     *
     * `landed` is what the player reported, in the order they reported it.
     * Empty when the player reported a bare total rather than naming the
     * darts.
     */
    fun learn(landed: List<Segment>) {
        if (landed.isEmpty() || aimed.isEmpty()) {
            // Nothing to attribute. The turn still happened, but a bare
            // total says nothing about which segment was aimed at.
            return
        }

        // The darts are matched in order, as far as the two lists overlap.
        // A player who names two of three darts has still told us something
        // about the first two.
        val count = minOf(aimed.size, landed.size)
        for (i in 0 until count) {
            val target = aimed[i]
            val actual = landed[i]
            if (target == actual) {
                stats.record(target, target)
            } else {
                // A miss on the target. Where it landed is still worth
                // knowing, because a dart that keeps dropping into the single
                // bed below is a different problem from one that goes
                // anywhere.
                stats.record(target, actual)
            }
        }
    }

    /**
     * Learns from one dart the player says missed its intended target.
     *
     * This is used when the player corrects the last dart in a turn before
     * the turn is confirmed. The target is the segment the coach aimed at,
     * and the landing is what the player says actually landed.
     */
    fun learnMiss(aimed: Segment, landed: Segment) {
        stats.record(aimed, landed)
    }

    /**
     * Learns from a turn the player reported as a bare total.
     *
     * The turn happened, but nothing can be attributed to a segment, so
     * nothing is learned. This is here so the caller does not have to
     * special-case it.
     */
    fun learnNothing() {
        // Deliberately empty.
    }

    /**
     * The profile that comes out of what has been learned.
     *
     * The rates are pulled towards the population averages until there is
     * enough of the player's own play behind them.
     */
    fun profile(): PlayerProfile {
        val trebles = mutableMapOf<Int, Double>()
        val doubles = mutableMapOf<Int, Double>()
        for (bed in 2..20) {
            val treble = stats.trebleRate(bed, PlayerProfile.defaultTrebleHit(bed))
            val double = stats.doubleRate(bed, PlayerProfile.defaultDoubleHit(bed))
            // Only carry an override when the player has actually thrown at
            // the segment. Carrying every segment would mean the profile
            // never changes the advice, because the defaults are the same
            // numbers.
            if (stats.attemptsFor(Segment(bed, 3)) > 0) trebles[bed] = treble
            if (stats.attemptsFor(Segment(bed, 2)) > 0) doubles[bed] = double
        }

        val single = stats.singleRate(PlayerProfile.DEFAULT_SINGLE_HIT)
        val bull = stats.rate(
            Segment(25, 2),
            PlayerProfile.DEFAULT_DB_HIT,
        )

        return PlayerProfile(
            trebleHit = trebles,
            doubleHit = doubles,
            singleHit = if (stats.totalAttempts == 0) {
                PlayerProfile.DEFAULT_SINGLE_HIT
            } else {
                single
            },
            doubleBullHit = bull,
        )
    }
}
