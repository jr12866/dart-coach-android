package com.jr12866.dartcoach.core

/**
 * The darts knowledge: what can be thrown, what it is worth, and the small
 * pure functions the coach leans on.
 *
 * Ported from `src/game/darts.py` in the Windows repo.
 */

/**
 * The number words the coach understands.
 *
 * Ported from `NUMBER_WORDS` in `src/game/darts.py`.
 */
val NUMBER_WORDS: Map<Int, String> = mapOf(
    1 to "one", 2 to "two", 3 to "three", 4 to "four", 5 to "five",
    6 to "six", 7 to "seven", 8 to "eight", 9 to "nine", 10 to "ten",
    11 to "eleven", 12 to "twelve", 13 to "thirteen", 14 to "fourteen",
    15 to "fifteen", 16 to "sixteen", 17 to "seventeen", 18 to "eighteen",
    19 to "nineteen", 20 to "twenty",
)

/**
 * Share of a miss that drops into the single bed above the target.
 *
 * This "wobble" is what makes 40 a better leave than 38: a missed D20 still
 * leaves 20 for D10, while a missed D19 leaves 19, which no double can take.
 */
private const val WOBBLE_CHANCE = 0.5

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
) {
    /**
     * The segment this aim is, for the hit-rate learner.
     *
     * The bull is bed 25, and the two bulls are told apart by the ring
     * rather than the bed, the same way the value is.
     */
    val segment: Segment
        get() = when (kind) {
            "treble" -> Segment(label.removePrefix("T").toInt(), 3)
            "double" -> Segment(label.removePrefix("D").toInt(), 2)
            "bull" -> if (label == "Bull") Segment(25, 2) else Segment(25, 1)
            else -> Segment(value, 1)
        }
}

/**
 * Every dart the player can throw at, in the order the planner prefers them.
 *
 * Ported from `_build_aims` in `src/game/darts.py`. The order matters: the
 * planner takes the first aim that beats the current best, so two aims with
 * the same value are broken by this order.
 */
internal val AIMS: List<Aim> = buildAims(PlayerProfile())

/**
 * The same list, built for a player rather than for the population.
 *
 * The order is the same as [AIMS] and must stay the same: the planner breaks
 * ties on the order of this list, so reordering it changes the advice the
 * coach gives. Only the probabilities differ.
 */
internal fun buildAims(profile: PlayerProfile): List<Aim> = buildList {
    for (n in 20 downTo 2) {
        val p = profile.trebleChance(n)
        add(
            Aim(
                label = "T$n",
                speech = "treble ${NUMBER_WORDS[n]}",
                value = 3 * n,
                kind = "treble",
                pHit = p,
                pWobble = (1 - p) * WOBBLE_CHANCE,
                wobbleValue = n,
                finishes = false,
            )
        )
    }

    add(
        Aim(
            label = "Bull",
            speech = "double bull",
            value = 50,
            kind = "bull",
            pHit = profile.doubleBullHit,
            pWobble = (1 - profile.doubleBullHit) * WOBBLE_CHANCE,
            wobbleValue = 25,
            finishes = true,
        )
    )

    add(
        Aim(
            label = "25",
            speech = "outer bull",
            value = 25,
            kind = "bull",
            pHit = profile.outerBullHit,
            pWobble = 0.0,
            wobbleValue = 0,
            finishes = false,
        )
    )

    for (n in 20 downTo 1) {
        val p = profile.doubleChance(n)
        add(
            Aim(
                label = "D$n",
                speech = "double ${NUMBER_WORDS[n]}",
                value = 2 * n,
                kind = "double",
                pHit = p,
                pWobble = (1 - p) * WOBBLE_CHANCE,
                wobbleValue = n,
                finishes = true,
            )
        )
    }

    for (n in 20 downTo 1) {
        add(
            Aim(
                label = n.toString(),
                speech = "single ${NUMBER_WORDS[n]}",
                value = n,
                kind = "single",
                pHit = profile.singleHit,
                pWobble = 0.0,
                wobbleValue = 0,
                finishes = false,
            )
        )
    }
}

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
 * Every score three darts can add up to.
 *
 * A turn is three darts, and the player throws all three before the coach
 * hears about it. A dart lands on a single, double or treble of 1-20, the
 * outer bull (25, or 50 for the double), or misses the scoring area
 * altogether.
 */
private val SINGLE_DART_SCORES: Set<Int> = buildSet {
    add(0)
    for (bed in 1..20) {
        for (multiplier in 1..3) add(bed * multiplier)
    }
    add(25)
    add(50)
}

private val TWO_DART_SCORES: Set<Int> = buildSet {
    for (first in SINGLE_DART_SCORES) {
        for (second in SINGLE_DART_SCORES) add(first + second)
    }
}

/**
 * Every total three darts can add up to.
 *
 * 172 of the 181 totals between 0 and 180 are reachable. The nine that no
 * three darts can add up to are 163, 166, 169, 172, 173, 175, 176, 178 and
 * 179. They are not bogeys: a bogey can be thrown, it just cannot be
 * finished on.
 */
private val THREE_DART_SCORES: Set<Int> = buildSet {
    for (thrown in TWO_DART_SCORES) {
        for (last in SINGLE_DART_SCORES) add(thrown + last)
    }
}

/** True if three darts at a standard board can add up to exactly `score`. */
fun isValidThreeDartScore(score: Int): Boolean = score in THREE_DART_SCORES

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

    /**
     * True when the score can be finished in three darts.
     *
     * Every total from 2 to 170 has a finish except the bogeys. An odd leave
     * below 40 takes a single on the way in: 44 is 4 then double twenty, 3 is
     * 1 then double one. Only 1 is unfinishable below the bogeys, and
     * nothing above 170 can be finished at all - 180 reaches zero on paper
     * but the last dart has to be a double, and the highest double-out
     * three dart finish is 170.
     */
    fun canFinish(score: Int): Boolean =
        score in 2..MAX_CHECKOUT && score !in BOGEY_NUMBERS

    /** True when the score is one that cannot be finished on. */
    fun isBogey(score: Int): Boolean = score in BOGEY_NUMBERS

    /**
     * Divide a score down to a double: 40 gives D20, 20, D10, 10, D5.
     *
     * The longer the chain, the more chances to hit a double.
     */
    fun chain(score: Int): List<String> {
        val parts = mutableListOf<String>()
        var n = score
        while (n >= 2 && n <= 40 && n % 2 == 0) {
            parts.add("D${n / 2}")
            val half = n / 2
            if (half < 2 || half % 2 != 0) break
            parts.add(half.toString())
            n = half
        }
        return parts
    }

    /** The doubles that finish `score` in a single dart. */
    fun doubleOptions(score: Int): List<String> = when {
        score == 50 -> listOf("Bull")
        score in 2..40 && score % 2 == 0 -> listOf("D${score / 2}")
        else -> emptyList()
    }

    /** The score as words: 139 is "one hundred and thirty-nine". */
    fun numberToWords(number: Int): String {
        if (number < 0) return number.toString()
        if (number < 20) return NUMBER_WORDS[number] ?: number.toString()
        if (number < 100) {
            val tens = (number / 10) * 10
            val ones = number % 10
            val word = TENS[tens]!!
            return if (ones != 0) "$word-${NUMBER_WORDS[ones]}" else word
        }
        if (number < 1000) {
            val hundreds = number / 100
            val rest = number % 100
            val word = "${NUMBER_WORDS[hundreds]} hundred"
            return if (rest != 0) "$word and ${numberToWords(rest)}" else word
        }
        return number.toString()
    }

    private val TENS: Map<Int, String> = mapOf(
        20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty",
        60 to "sixty", 70 to "seventy", 80 to "eighty", 90 to "ninety",
    )
}