package com.jr12866.dartcoach.core

/**
 * How well the player throws.
 *
 * The coach's probabilities come from this. The defaults are the population
 * averages the coach was ported with, so a player who has never been measured
 * gets exactly the advice the original coach gave.
 *
 * The numbers are the chance that a dart aimed at a given segment lands on
 * that segment.
 */
data class PlayerProfile(
    /**
     * Chance of hitting each treble, keyed by the bed number 2..20.
     *
     * A number that is not in the map uses the default for that bed.
     */
    val trebleHit: Map<Int, Double> = emptyMap(),
    /**
     * Chance of hitting each double, keyed by the bed number 1..20.
     *
     * A number that is not in the map uses the default curve.
     */
    val doubleHit: Map<Int, Double> = emptyMap(),
    /** Chance of hitting a single. */
    val singleHit: Double = DEFAULT_SINGLE_HIT,
    /** Chance of hitting the outer bull. */
    val outerBullHit: Double = DEFAULT_SB_HIT,
    /** Chance of hitting the double bull. */
    val doubleBullHit: Double = DEFAULT_DB_HIT,
) {
    /** The chance of hitting a treble on the given bed. */
    fun trebleChance(bed: Int): Double =
        trebleHit[bed] ?: defaultTrebleHit(bed)

    /** The chance of hitting a double on the given bed. */
    fun doubleChance(bed: Int): Double =
        doubleHit[bed] ?: defaultDoubleHit(bed)

    companion object {
        /** The chance of hitting a single. */
        const val DEFAULT_SINGLE_HIT = 0.90

        /** The chance of hitting the outer bull. */
        const val DEFAULT_SB_HIT = 0.90

        /** The chance of hitting the double bull. */
        const val DEFAULT_DB_HIT = 0.50

        /**
         * Combines what the coach has learned with what the player has set.
         *
         * The player's own number for a segment wins. They said they cannot
         * hit D16, and that is not something the coach should argue with
         * because its own count says otherwise.
         */
        fun merge(learned: PlayerProfile, manual: PlayerProfile): PlayerProfile =
            PlayerProfile(
                trebleHit = learned.trebleHit + manual.trebleHit,
                doubleHit = learned.doubleHit + manual.doubleHit,
                singleHit = manual.singleHit,
                outerBullHit = manual.outerBullHit,
                doubleBullHit = manual.doubleBullHit,
            )

        /**
         * Share of attempts that land in the treble bed.
         *
         * The higher the bed, the more of the treble bed a player tends to
         * find, so the chance rises with the number.
         */
        private val DEFAULT_TREBLE_HIT: Map<Int, Double> = mapOf(
            2 to 0.07, 3 to 0.09, 4 to 0.11, 5 to 0.13, 6 to 0.16, 7 to 0.19,
            8 to 0.22, 9 to 0.25, 10 to 0.29, 11 to 0.33, 12 to 0.38, 13 to 0.42,
            14 to 0.46, 15 to 0.49, 16 to 0.51, 17 to 0.53, 18 to 0.55, 19 to 0.56,
            20 to 0.58,
        )

        /** The default chance of hitting a treble on the given bed. */
        fun defaultTrebleHit(bed: Int): Double = DEFAULT_TREBLE_HIT[bed] ?: 0.0

        /**
         * The default chance of hitting a double on the given bed.
         *
         * The chance of hitting a double gets easier as the double gets
         * bigger.
         */
        fun defaultDoubleHit(bed: Int): Double = 0.30 + 0.0085 * bed
    }
}
