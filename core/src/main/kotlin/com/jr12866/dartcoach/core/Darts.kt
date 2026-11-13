package com.jr12866.dartcoach.core

/**
 * The API the Kotlin core exposes.
 *
 * This file declares the shapes, not the behaviour. The behaviour is a port
 * of the Python in `darts-coach-windows`, and the parity corpus in
 * `corpus/` is what says whether the port is faithful.
 *
 * Phase 2 fills these in. Until then the tests fail to compile, which is the
 * honest state of the work.
 */

/**
 * A target the player can throw at.
 *
 * Ported from `Aim` in `src/game/darts.py`.
 */
data class Aim(
    /** "T20", "D16", "Bull", "25", "7". */
    val label: String,
    /** How it is said out loud: "treble twenty", "double sixteen". */
    val speech: String,
    /** What it is worth: T20 is 60, D16 is 32, Bull is 50. */
    val value: Int,
    /** "treble", "double", "single" or "bull". */
    val kind: String,
    /** Chance of hitting it. */
    val pHit: Double,
    /** Chance of dropping into the single bed above it. */
    val pWobble: Double,
    /** What the wobble leaves on the board: T20 wobbles to 20. */
    val wobbleValue: Int,
    /** True for the darts that can finish a leg: the doubles and the bull. */
    val finishes: Boolean,
)

/**
 * What to throw at a given remaining score.
 *
 * Ported from `Plan` in `src/game/darts.py`.
 */
data class Plan(
    val score: Int,
    val aims: List<Aim> = emptyList(),
    /** The score this turn hands to the next one. */
    val leaves: Int = 0,
    /** True when these darts win the leg. */
    val finishes: Boolean = false,
    /** What to throw if the first dart drops into its single bed. */
    val fallbackAims: List<Aim> = emptyList(),
    val fallbackLeaves: Int = 0,
    val probability: Double = 0.0,
    /** The doubles still available at this score. */
    val doubles: List<String> = emptyList(),
) {
    val labels: List<String> get() = aims.map { it.label }
    val fallbackLabels: List<String> get() = fallbackAims.map { it.label }

    /** The compact form for the small screen. */
    val displayLines: List<String>
        get() {
            val darts = labels.joinToString(" ")
            return if (finishes) {
                listOf(darts, "to win")
            } else {
                listOf(darts, "leaves $leaves")
            }
        }

    /** How the plan is said out loud. */
    fun speak(): String = sayDarts(aims)
}

/**
 * The darts that take a remaining score onto one named double.
 *
 * Ported from `Route` in `src/game/darts.py`.
 */
data class Route(
    val score: Int,
    /** The double the player asked for: 16 is D16, 50 is the bull. */
    val double: Int,
    val aims: List<Aim> = emptyList(),
    val leaves: Int = 0,
    val finishes: Boolean = false,
    val probability: Double = 0.0,
    val fallbackAims: List<Aim> = emptyList(),
    val fallbackLeaves: Int = 0,
) {
    val labels: List<String> get() = aims.map { it.label }
    val fallbackLabels: List<String> get() = fallbackAims.map { it.label }

    val displayLines: List<String>
        get() {
            val darts = labels.joinToString(" ")
            return if (finishes) {
                listOf(darts, "to win")
            } else {
                listOf(darts, "leaves $leaves")
            }
        }

    fun speak(): String = sayDarts(aims)
}

/**
 * "treble twenty, treble twenty and double five".
 *
 * Ported from `_say_darts` in `src/game/darts.py`.
 */
internal fun sayDarts(aims: List<Aim>): String {
    if (aims.isEmpty()) return ""
    val parts = aims.map { it.speech }
    if (parts.size == 1) return parts[0]
    return parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}
/**
 * The pure darts facts the coach leans on.
 *
 * Ported from the module-level functions in `src/game/darts.py`:
 * `can_finish`, `is_bogey`, `chain`, `double_options` and `number_to_words`.
 */
object Darts {

    /** The highest score that can be finished in three darts. */
    const val MAX_CHECKOUT = 170

    /**
     * Scores that cannot be finished in three darts.
     *
     * A bogey can still be thrown; it just cannot be finished on.
     */
    val BOGEY_NUMBERS = setOf(159, 162, 163, 165, 166, 168, 169)

    /** The double bull is worth 50, so a player asking for it asks for D50. */
    const val BULL_DOUBLE = 50

    /** The highest score the planner is built for. A leg starts at 501. */
    const val MAX_SCORE = 501

    /** True when the score can be finished in three darts. */
    fun canFinish(score: Int): Boolean {
        // TODO Phase 2
        return false
    }

    /** True when the score is one that cannot be finished on. */
    fun isBogey(score: Int): Boolean {
        // TODO Phase 2
        return false
    }

    /**
     * The double chain: the leaves that divide down to a double.
     *
     * 40 gives D20, a drop to 20 gives D10, a drop to 10 gives D5. The longer
     * the chain, the more chances to hit a double.
     */
    fun chain(score: Int): List<String> {
        // TODO Phase 2
        return emptyList()
    }

    /** The doubles still available at this score. */
    fun doubleOptions(score: Int): List<String> {
        // TODO Phase 2
        return emptyList()
    }

    /** The score as words: 140 is "one hundred and forty". */
    fun numberToWords(number: Int): String {
        // TODO Phase 2
        return ""
    }
}