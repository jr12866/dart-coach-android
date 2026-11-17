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
    /**
     * Chance of hitting a single.
     *
     * The singles are pooled: there is one number for all twenty beds,
     * because there is never enough play at a single bed to say much about
     * it. Null means nobody has set one, so the population average is used.
     */
    val singleHit: Double? = null,
    /**
     * Chance of hitting the outer bull.
     *
     * Null means nobody has set one, so the population average is used. The
     * outer bull is not a bed either, so it has a field of its own like the
     * double bull.
     */
    val outerBullHit: Double? = null,
    /**
     * Chance of hitting the double bull.
     *
     * Null means nobody has set one, so the population average is used. The
     * bull is the one segment that is not in the bed maps, because it is not
     * a bed: it has its own field, and it needs one that can say "not set".
     * The trebles and the doubles leave that out of their map, so a rate
     * that is not in the map is a rate nobody has set.
     */
    val doubleBullHit: Double? = null,
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

        /**
         * The chance of hitting the double bull, the inner twenty-five.
         *
         * This was 0.50, which was a guess with nothing behind it, and it was
         * a guess that was too generous: published professional figures put the
         * inner bull at 20-25% for a professional and around 30% for the very
         * best, so 0.50 was above any player who has ever lived.
         *
         * The number mattered more than it looked. The bull is one of the few
         * targets the coach can plan a leg around - 170 is finished with
         * T20 T20 Bull and nothing else - so an optimistic bull rate made the
         * coach steer players into bull finishes they would then miss.
         * Lowering it to the middle of the published band moves the advice off
         * the bull: the setup the coach leaves at 321 becomes "T20 T20 T16"
         * leaving 153, which is finished T20 T19 D18 with no bull in it.
         *
         * The treble curve is still a guess, and a suspicious one: it
         * descends with the bed number, which is how often a bed is thrown at
         * rather than how often it is hit. All the treble beds are the same
         * size, so a real hit rate should be much flatter. Fixing that needs
         * per-bed data, so it is separate work.
         */
        const val DEFAULT_DB_HIT = 0.225

        /**
         * Combines what the coach has learned with what the player has set.
         *
         * The player's own number for a segment wins. They said they cannot
         * hit D16, and that is not something the coach should argue with
         * because its own count says otherwise.
         *
         * The plain numbers - the singles, the outer bull and the double
         * bull - can say "not set", so the merge falls back to what the coach
         * learned, and the planner falls back to the population average when
         * there is neither.
         */
        fun merge(learned: PlayerProfile, manual: PlayerProfile): PlayerProfile =
            PlayerProfile(
                trebleHit = learned.trebleHit + manual.trebleHit,
                doubleHit = learned.doubleHit + manual.doubleHit,
                singleHit = manual.singleHit ?: learned.singleHit,
                outerBullHit = manual.outerBullHit ?: learned.outerBullHit,
                doubleBullHit = manual.doubleBullHit ?: learned.doubleBullHit,
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
