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
            learnedDarts.clear()
            return
        }

        // The darts are matched in order, as far as the two lists overlap.
        // A player who names two of three darts has still told us something
        // about the first two.
        val count = minOf(aimed.size, landed.size)
        for (i in 0 until count) {
            // A dart that was corrected has already been learned from.
            if (i in learnedDarts) continue
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

        // The turn is over, so the darts that were learned from it are done
        // with. The next turn starts from nothing.
        learnedDarts.clear()
    }

    /**
     * The darts of the current turn that have already been learned from.
     *
     * A corrected dart is learned when the correction is made, because that
     * is when the miss is known. The turn is learned again when it is
     * confirmed, and without this the same miss would be counted twice: the
     * player would be told they miss D16 twice for the one dart they missed
     * it on.
     */
    private val learnedDarts = mutableSetOf<Int>()

    /**
     * Learns from one dart the player says missed its intended target.
     *
     * This is used when the player corrects a dart in a turn before the turn
     * is confirmed. The target is the segment the coach aimed at, and the
     * landing is what the player says actually landed.
     *
     * `dartIndex` is where the dart sits in the turn, counted from zero, so
     * that the turn does not learn from it a second time. It is -1 when the
     * place of the dart is not known, which is when the player reported the
     * turn as a total: there are no darts on the books to match the turn
     * against later, so there is nothing to skip.
     */
    fun learnMiss(aimed: Segment, landed: Segment, dartIndex: Int = -1) {
        stats.record(aimed, landed)
        if (dartIndex >= 0) learnedDarts.add(dartIndex)
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
     * Where this player's misses landed, for the segment that was aimed at.
     *
     * The landing is the segment the dart actually hit, so a treble twenty
     * that came off as the single five is a landing on the single five.
     */
    fun missLandings(segment: Segment): Map<Segment, Int> = stats.missLandingsFor(segment)

    /**
     * The sideways misses: where the darts went when they crossed a wire.
     *
     * Only the beds either side of the target count here. A miss that
     * dropped into the bed below is a different kind of miss, and one that
     * landed somewhere else on the board is a wild one.
     *
     * This is what turns a guess into a fact: the board says where a dart
     * *could* land, and this says where this player's darts *do* land.
     */
    fun wireMisses(segment: Segment): Map<Int, Int> {
        if (segment.bed !in 1..20) return emptyMap()
        val landings = stats.missLandingsFor(segment)
        return Board.neighbours(segment.bed).mapNotNull { bed ->
            val count = landings[Segment(bed, 1)] ?: 0
            if (count > 0) bed to count else null
        }.toMap()
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

        // The outer bull is the other bull, and it is learned the same way:
        // the counts are kept for it, so the rate should come from the
        // player's own play rather than sitting at the population average.
        // It is not pooled with the singles, because it is a bull and not a
        // bed.
        val outerBull = stats.rate(
            Segment(25, 1),
            PlayerProfile.DEFAULT_SB_HIT,
        )

        return PlayerProfile(
            trebleHit = trebles,
            doubleHit = doubles,
            singleHit = if (stats.totalAttempts == 0) {
                PlayerProfile.DEFAULT_SINGLE_HIT
            } else {
                single
            },
            outerBullHit = outerBull,
            doubleBullHit = bull,
        )
    }
}
