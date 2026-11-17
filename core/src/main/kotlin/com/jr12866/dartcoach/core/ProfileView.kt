package com.jr12866.dartcoach.core

/**
 * What the coach thinks about one segment, and what the player can say
 * about it.
 *
 * The screen needs three numbers for each segment: what the coach is using
 * now, what the coach has learned from the player's own play, and what the
 * player has set by hand. The player's number wins; the learned one is what
 * it would be if they had not said anything.
 */
data class SegmentView(
    /** The segment: a bed and a ring. */
    val segment: Segment,
    /** The rate the coach is using now. */
    val effective: Double,
    /** The rate the coach learned from the player's own play. */
    val learned: Double,
    /** The rate the player set by hand, if they did. */
    val manual: Double?,
    /** How many times the segment was aimed at. */
    val attempts: Int,
    /** How many times the segment was hit. */
    val hits: Int,
) {
    /** The label: "T20", "D16", "Bull". */
    val label: String get() = segment.label

    /** True when the player has set this one by hand. */
    val isManual: Boolean get() = manual != null

    /**
     * True when the player's own play has moved the rate away from the
     * population average by enough to matter.
     *
     * A small difference is not worth showing - it is the prior pulling, not
     * a lesson.
     */
    val hasLearnedSomething: Boolean
        get() = attempts > 0 && kotlin.math.abs(learned - prior) > 0.02

    /** The population average for this segment, which is the prior. */
    val prior: Double get() = priorFor(segment)

    /** The rate as a percentage, for the screen. */
    fun percentText(rate: Double): String = "${(rate * 100).toInt()}%"

    companion object {
        /**
         * The population average for a segment.
         *
         * This is the prior the learned rate is pulled towards, so it is
         * what the coach would use with no history at all.
         */
        fun priorFor(segment: Segment): Double = when (segment.multiplier) {
            3 -> PlayerProfile.defaultTrebleHit(segment.bed)
            2 -> if (segment.bed == 25) {
                PlayerProfile.DEFAULT_DB_HIT
            } else {
                PlayerProfile.defaultDoubleHit(segment.bed)
            }

            else -> if (segment.bed == 25) {
                PlayerProfile.DEFAULT_SB_HIT
            } else {
                PlayerProfile.DEFAULT_SINGLE_HIT
            }
        }
    }
}

/**
 * Where one segment's misses landed.
 *
 * This is the picture of how a player misses: the treble twenty that keeps
 * coming off as the single five is a player who slides right, and the one
 * that keeps dropping into the single twenty is a player who drops short.
 * The two need different answers, so the coach has to know which one it is.
 */
data class MissLanding(
    /** The segment that was aimed at. */
    val segment: Segment,
    /** Where the misses landed, as landing segment to count. */
    val landings: Map<Segment, Int>,
) {
    /** The label of the segment aimed at: "T20". */
    val label: String get() = segment.label

    /** How many misses there were. */
    val total: Int get() = landings.values.sum()

    /**
     * The landings, most often first: "20 x4, 5 x2".
     *
     * The landing is labelled the way the board labels it, so a single is
     * "5" and a treble is "T19".
     */
    fun describe(): String = landings.entries
        .sortedWith(compareByDescending<Map.Entry<Segment, Int>> { it.value }.thenBy { it.key.bed })
        .joinToString(", ") { "${it.key.label} x${it.value}" }
}

/**
 * The whole picture of how the player throws.
 *
 * This is what the profile screen shows: every segment the coach has an
 * opinion about, with the rate, where the rate came from, and how much
 * evidence there is behind it.
 */
data class ProfileView(
    /** The segments, in the order they are shown. */
    val segments: List<SegmentView>,
    /** The player's own settings. */
    val manual: PlayerProfile,
    /** What the coach has learned. */
    val learner: HitLearner,
) {
    /** How many darts have been thrown at anything. */
    val totalAttempts: Int get() = learner.stats.totalAttempts

    /** How many darts have landed. */
    val totalHits: Int get() = learner.stats.totalHits

    /**
     * The overall hit rate, as a percentage.
     *
     * This is the number that matters most to a player: the share of darts
     * that landed on the bed they were aimed at. A dart that dropped into
     * the single below is a miss, because the player was aiming at the
     * double.
     */
    val overallRate: Int
        get() = if (totalAttempts == 0) {
            0
        } else {
            (learner.stats.totalOnTarget * 100 / totalAttempts)
        }

    /** The doubles the player has an opinion about. */
    val doubles: List<SegmentView> get() = segments.filter { it.segment.multiplier == 2 }

    /** The trebles the player has an opinion about. */
    val trebles: List<SegmentView> get() = segments.filter { it.segment.multiplier == 3 }

    /** The singles and the outer bull. */
    val rest: List<SegmentView> get() = segments.filter { it.segment.multiplier == 1 }

    /**
     * Where the player's misses land, the worst first.
     *
     * Only the trebles and the doubles are listed, because those are the
     * ones the coach aims at and the ones whose misses have somewhere to go
     * wrong to. A miss on a single is not interesting, and the bull
     * scatters its misses across the board.
     */
    val missLandings: List<MissLanding>
        get() = learner.stats.missLandingCounts()
            .filterKeys { it.multiplier >= 2 && it.bed != 25 }
            .map { (segment, landings) -> MissLanding(segment, landings) }
            .sortedByDescending { it.total }

    /**
     * Sets a segment by hand.
     *
     * A null rate clears the override, so the coach goes back to what it has
     * learned.
     */
    /**
     * Sets a segment by hand.
     *
     * A null rate clears the override, so the coach goes back to what it has
     * learned.
     *
     * The segments are rebuilt, because the effective rate of the segment
     * that was changed has moved.
     */
    fun withManual(segment: Segment, rate: Double?): ProfileView {
        val updated = when (segment.multiplier) {
            3 -> {
                val map = manual.trebleHit.toMutableMap()
                if (rate == null) map.remove(segment.bed) else map[segment.bed] = rate
                manual.copy(trebleHit = map)
            }

            2 -> {
                if (segment.bed == 25) {
                    // The bull is the double bull, and it has its own field
                    // in the profile. Putting it in the map of doubles would
                    // put it somewhere the planner never looks: the coach
                    // reads the bull from its own field, and a rate set here
                    // would be silently dropped.
                    manual.copy(doubleBullHit = rate)
                } else {
                    val map = manual.doubleHit.toMutableMap()
                    if (rate == null) map.remove(segment.bed) else map[segment.bed] = rate
                    manual.copy(doubleHit = map)
                }
            }

            else -> {
                if (segment.bed == 25) {
                    // The outer bull is not a bed either, so it has a field
                    // of its own like the double bull.
                    manual.copy(outerBullHit = rate)
                } else {
                    // The singles are pooled: one number for all twenty
                    // beds, because there is never enough play at one bed to
                    // say much about it. Setting a single by hand sets the
                    // pooled rate, which is the one the planner reads.
                    manual.copy(singleHit = rate)
                }
            }
        }
        return of(learner, updated)
    }

    companion object {
        /**
         * Builds the view from what the coach knows and what the player set.
         *
         * The order is doubles first, then trebles, then the singles and
         * bulls, because the doubles are what a player is most likely to
         * have an opinion about.
         */
        fun of(learner: HitLearner, manual: PlayerProfile): ProfileView {
            val learned = learner.profile()

            val doubles = (1..20).map { bed ->
                Segment(bed, 2)
            }.map { segment ->
                view(segment, learned, manual, learner)
            }

            val trebles = (2..20).map { bed ->
                Segment(bed, 3)
            }.map { segment ->
                view(segment, learned, manual, learner)
            }

            val rest = listOf(
                Segment(25, 2),
                Segment(25, 1),
            ) + (1..20).map { Segment(it, 1) }

            return ProfileView(
                segments = doubles + trebles + rest.map { segment ->
                    view(segment, learned, manual, learner)
                },
                manual = manual,
                learner = learner,
            )
        }

        /** The view of one segment. */
        private fun view(
            segment: Segment,
            learned: PlayerProfile,
            manual: PlayerProfile,
            learner: HitLearner,
        ): SegmentView {
            val prior = SegmentView.priorFor(segment)
            val learnedRate = when (segment.multiplier) {
                3 -> learned.trebleChance(segment.bed)
                2 -> if (segment.bed == 25) {
                    learned.doubleBullHit ?: PlayerProfile.DEFAULT_DB_HIT
                } else {
                    learned.doubleChance(segment.bed)
                }

                else -> if (segment.bed == 25) {
                    learned.outerBullHit ?: PlayerProfile.DEFAULT_SB_HIT
                } else {
                    learned.singleHit ?: PlayerProfile.DEFAULT_SINGLE_HIT
                }
            }
            val manualRate = when (segment.multiplier) {
                3 -> manual.trebleHit[segment.bed]
                2 -> if (segment.bed == 25) {
                    manual.doubleBullHit
                } else {
                    manual.doubleHit[segment.bed]
                }

                else -> if (segment.bed == 25) {
                    manual.outerBullHit
                } else {
                    // The singles are pooled, so they share one override.
                    manual.singleHit
                }
            }
            val effective = manualRate ?: learnedRate

            return SegmentView(
                segment = segment,
                effective = effective,
                learned = learnedRate,
                manual = manualRate,
                attempts = learner.stats.attemptsFor(segment),
                hits = learner.stats.hitsFor(segment),
            )
        }
    }
}
