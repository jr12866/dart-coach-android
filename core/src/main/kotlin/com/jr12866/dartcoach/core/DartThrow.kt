package com.jr12866.dartcoach.core

/**
 * One dart, as it landed on the board.
 *
 * A dart's *value* is not enough to learn from. Sixty might have been a
 * treble twenty or a double thirty - the second is not a thing - but the
 * value alone also cannot say whether a player who misses D16 usually drops
 * into the single eight, because a single eight and a double eight are both
 * worth sixteen.
 *
 * The segment is what the learner needs: which bed, and which ring.
 */
data class DartThrow(
    /** The bed the dart was aimed at or landed on: 1..20, or 25 for the bull. */
    val bed: Int,
    /** The ring: 1 for a single, 2 for a double, 3 for a treble. */
    val multiplier: Int,
) {
    /** What the dart was worth. */
    val value: Int
        get() = if (bed == 25) {
            // The outer bull is worth 25 and the double bull 50, and they are
            // told apart by the ring rather than by the bed.
            if (multiplier == 2) 50 else 25
        } else {
            bed * multiplier
        }

    /** True if this was the double bull. */
    val isDoubleBull: Boolean get() = bed == 25 && multiplier == 2

    /** True if this was the outer bull. */
    val isOuterBull: Boolean get() = bed == 25 && multiplier == 1

    /** The label the coach and the charts use: "T20", "D16", "Bull", "25". */
    val label: String
        get() = when {
            isDoubleBull -> "Bull"
            isOuterBull -> "25"
            multiplier == 3 -> "T$bed"
            multiplier == 2 -> "D$bed"
            else -> bed.toString()
        }

    /** The segment this dart belongs to, for the learner. */
    val segment: Segment get() = Segment(bed, multiplier)

    companion object {
        /** The double bull. */
        val DOUBLE_BULL = DartThrow(25, 2)

        /** The outer bull. */
        val OUTER_BULL = DartThrow(25, 1)

        /**
         * The dart worth `value`, when it can be worked out.
         *
         * A value alone is ambiguous - sixteen could be a single sixteen or a
         * double eight - so this returns null when it cannot be told.
         */
        fun fromValue(value: Int): DartThrow? = when {
            value == 0 -> null
            value == 25 -> OUTER_BULL
            value == 50 -> DOUBLE_BULL
            value in 1..20 -> DartThrow(value, 1)
            value % 3 == 0 && value / 3 in 2..20 -> DartThrow(value / 3, 3)
            value % 2 == 0 && value / 2 in 1..20 -> DartThrow(value / 2, 2)
            else -> null
        }
    }
}

/**
 * A place on the board: a bed and a ring.
 *
 * The learner counts hits and attempts per segment, and the segment is the
 * thing the coach aims at.
 */
data class Segment(val bed: Int, val multiplier: Int) {
    /** What a dart on this segment is worth. */
    val value: Int
        get() = if (bed == 25) {
            if (multiplier == 2) 50 else 25
        } else {
            bed * multiplier
        }

    /** The label the coach and the charts use. */
    val label: String
        get() = when {
            bed == 25 && multiplier == 2 -> "Bull"
            bed == 25 && multiplier == 1 -> "25"
            multiplier == 3 -> "T$bed"
            multiplier == 2 -> "D$bed"
            else -> bed.toString()
        }

    /**
     * How this segment is said out loud.
     *
     * The label is for the screen, where "T20" is the shortest thing that
     * says what to throw. The coach speaks, so it needs words: "treble
     * twenty". The bull is the reason the two are different - "Bull" is
     * what the chart calls it, but a player says "double bull" or "outer
     * bull".
     *
     * A single bed is said as the number, not as the word, because that is
     * how the coach has always said it: "You hit 8 instead" rather than
     * "You hit eight instead". The number is short, and the coach is
     * repeating back something the player has just said.
     */
    val speech: String
        get() = when {
            bed == 25 && multiplier == 2 -> "double bull"
            bed == 25 && multiplier == 1 -> "outer bull"
            multiplier == 3 -> "T$bed"
            multiplier == 2 -> "D$bed"
            else -> bed.toString()
        }
}

/**
 * What the player has thrown, in a form that can be saved.
 *
 * The counts are saved rather than the rates, because a rate cannot be
 * turned back into a count. Saving the counts means the history keeps
 * accumulating across restarts, and the prior weighting still works on the
 * way back in.
 */
data class HitStatsSnapshot(
    /** How many times each segment was aimed at, as "bed,multiplier,count". */
    val attempts: List<String>,
    /** How many times each segment was hit, in the same form. */
    val hits: List<String>,
    /** How many darts have been thrown at anything. */
    val totalAttempts: Int,
    /** How many darts have landed. */
    val totalHits: Int,
    /**
     * How many darts landed on the bed they were aimed at, as
     * "bed,multiplier,count".
     *
     * This is a newer count than the others, so a snapshot saved before it
     * existed will not have it. It is optional for that reason.
     */
    val onTarget: List<String> = emptyList(),
    /** How many darts have landed on the bed they were aimed at. */
    val totalOnTarget: Int = 0,
) {
    /** Puts the counts back into a learner. */
    fun toStats(): HitStats = HitStats(
        attempts = attempts.toMap(),
        hits = hits.toMap(),
        totalAttempts = totalAttempts,
        totalHits = totalHits,
        onTargetCounts = onTarget.toMap(),
        totalOnTarget = totalOnTarget,
    )

    companion object {
        /** Takes a snapshot of what has been learned. */
        fun of(stats: HitStats): HitStatsSnapshot = HitStatsSnapshot(
            attempts = stats.attemptCounts().toEntries(),
            hits = stats.hitCounts().toEntries(),
            totalAttempts = stats.totalAttempts,
            totalHits = stats.totalHits,
            onTarget = stats.onTargetCounts().toEntries(),
            totalOnTarget = stats.totalOnTarget,
        )

        /** Reads a "bed,multiplier,count" entry. */
        private fun List<String>.toMap(): Map<Segment, Int> {
            val out = mutableMapOf<Segment, Int>()
            for (entry in this) {
                val parts = entry.split(",")
                if (parts.size != 3) continue
                val bed = parts[0].toIntOrNull() ?: continue
                val multiplier = parts[1].toIntOrNull() ?: continue
                val count = parts[2].toIntOrNull() ?: continue
                out[Segment(bed, multiplier)] = count
            }
            return out
        }

        /** Writes a segment count as "bed,multiplier,count". */
        private fun Map<Segment, Int>.toEntries(): List<String> =
            this.entries.map { "${it.key.bed},${it.key.multiplier},${it.value}" }
    }
}

/**
 * What the coach has learned about how the player throws.
 *
 * The counts are what was seen; the rates are what they mean. The rates are
 * pulled towards the population average until there is enough evidence for
 * the player's own numbers to matter, so the coach does not give silly advice
 * to a player who has thrown four darts.
 */
class HitStats(
    /** How many times each segment was aimed at. */
    attempts: Map<Segment, Int> = emptyMap(),
    /** How many times each segment was hit. */
    hits: Map<Segment, Int> = emptyMap(),
    /** How many darts have been thrown at anything. */
    var totalAttempts: Int = 0,
    /** How many darts have landed. */
    var totalHits: Int = 0,
    /** How many darts landed on the bed they were aimed at, per segment. */
    onTargetCounts: Map<Segment, Int> = emptyMap(),
    /** How many darts have landed on the bed they were aimed at. */
    var totalOnTarget: Int = 0,
) {

    /** How many times each segment was aimed at. */
    private val attempts: MutableMap<Segment, Int> = attempts.toMutableMap()

    /** How many times each segment was hit. */
    private val hits: MutableMap<Segment, Int> = hits.toMutableMap()

    /**
     * How many times a dart landed on the bed it was aimed at.
     *
     * This is different from [hits], which counts where the darts landed
     * rather than how many went where they were aimed. A dart aimed at D16
     * that dropped into the single eight is a hit on the single eight and a
     * miss on D16, and the two numbers answer different questions.
     */
    private val onTarget: MutableMap<Segment, Int> = onTargetCounts.toMutableMap()

    /** Records a dart that was aimed at `aimed` and landed on `landed`. */
    fun record(aimed: Segment, landed: Segment?) {
        attempts[aimed] = (attempts[aimed] ?: 0) + 1
        totalAttempts++
        if (landed != null) {
            hits[landed] = (hits[landed] ?: 0) + 1
            totalHits++
        }
        if (landed == aimed) {
            onTarget[aimed] = (onTarget[aimed] ?: 0) + 1
            totalOnTarget++
        }
    }

    /** How many times the segment was aimed at. */
    fun attemptsFor(segment: Segment): Int = attempts[segment] ?: 0

    /** How many times the segment was hit. */
    fun hitsFor(segment: Segment): Int = hits[segment] ?: 0

    /** The attempt counts, so they can be saved. */
    fun attemptCounts(): Map<Segment, Int> = attempts.toMap()

    /** The hit counts, so they can be saved. */
    fun hitCounts(): Map<Segment, Int> = hits.toMap()

    /** The on-target counts, so they can be saved. */
    fun onTargetCounts(): Map<Segment, Int> = onTarget.toMap()

    /** How many darts aimed at the segment landed on it. */
    fun onTargetFor(segment: Segment): Int = onTarget[segment] ?: 0

    /** Every segment that has been aimed at. */
    fun seenSegments(): Set<Segment> = attempts.keys

    /**
     * The chance of hitting a segment, learned from the player's own play.
     *
     * The population average is pulled in until there is evidence. `k` is
     * how many darts' worth of the population average stands for a segment
     * with no history of its own, so a segment with one miss behind it does
     * not swing to zero.
     */
    fun rate(segment: Segment, prior: Double, k: Double = PRIOR_WEIGHT): Double {
        val n = attemptsFor(segment)
        if (n == 0) return prior
        val h = hitsFor(segment)
        return (prior * k + h) / (k + n)
    }

    /**
     * The chance of hitting a treble on a bed, learned from the player's own
     * play.
     */
    fun trebleRate(bed: Int, prior: Double): Double =
        rate(Segment(bed, 3), prior)

    /**
     * The chance of hitting a double on a bed, learned from the player's own
     * play.
     */
    fun doubleRate(bed: Int, prior: Double): Double =
        rate(Segment(bed, 2), prior)

    /**
     * The chance of hitting a single, learned from the player's own play.
     *
     * Singles are hit far more often than anything else, so they are pooled
     * across every bed rather than counted per bed: there is not enough
     * play in a leg to say much about single seven on its own.
     */
    fun singleRate(prior: Double): Double {
        var n = 0
        var h = 0
        for (segment in seenSegments()) {
            if (segment.multiplier != 1 || segment.bed == 25) continue
            n += attemptsFor(segment)
            h += hitsFor(segment)
        }
        if (n == 0) return prior
        return (prior * PRIOR_WEIGHT + h) / (PRIOR_WEIGHT + n)
    }

    companion object {
        /**
         * How many darts' worth of the population average stands for a
         * segment with no history of its own.
         *
         * Fifteen is about half a leg's worth of visits at that segment, so
         * the player's own numbers start to matter after a couple of legs and
         * the coach is not wrong in the meantime.
         */
        const val PRIOR_WEIGHT = 15.0
    }
}
