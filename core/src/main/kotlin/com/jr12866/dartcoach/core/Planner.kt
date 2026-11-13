package com.jr12866.dartcoach.core

/**
 * Works out what to throw at a given score.
 *
 * Ported from `Planner` in `src/game/darts.py` in the Windows repo.
 *
 * The planner is a depth-first search over three darts. Every dart has three
 * outcomes - hit, drop to the single below, or nothing - and each outcome
 * leads to a new remaining score. Busting is treated as a wasted turn, so a
 * plan that risks going below zero is only chosen when nothing else works.
 *
 * The search is not cheap, so it is built once and shared.
 */
class Planner private constructor(private val maxScore: Int) {

    /** The best dart and its value, for a score and a number of darts left. */
    private data class Entry(val value: Double, val aim: Aim?)

    /** The two searches, memoised on (score, darts left). */
    private val checkoutMemo = mutableMapOf<Pair<Int, Int>, Entry>()
    private val setupMemo = mutableMapOf<Pair<Int, Int>, Entry>()

    /**
     * The memo for the score currently being settled.
     *
     * A setup score can be left untouched, so its value is solved as a fixed
     * point. The guesses for that score live here rather than in the shared
     * setup memo, because they are only valid while that one score is being
     * iterated.
     */
    private val local = mutableMapOf<Pair<Int, Int>, Entry>()

    /** The value of every score: the chance of checking out in a turn. */
    private val checkoutValue = mutableMapOf<Int, Double>()
    private val setupValue = mutableMapOf<Int, Double>()

    /**
     * The best darts per score, once per double the player might insist on.
     *
     * Keyed by the finishing dart's label, because that is what identifies
     * the double.
     */
    private val routeCache = mutableMapOf<String, Pair<Int, RouteTables>>()

    /** The score being settled, while the setup sweep runs. */
    private var current: Int = maxScore

    private data class RouteTables(
        val checkout: Map<Pair<Int, Int>, Entry>,
        val setup: Map<Pair<Int, Int>, Entry>,
    )

    // -- the two searches --------------------------------------------------

    /** Chance of finishing `cur` in the remaining darts of this turn. */
    private fun checkoutSolve(cur: Int, darts: Int): Entry {
        if (darts == 0) return Entry(0.0, null)

        val key = cur to darts
        checkoutMemo[key]?.let { return it }

        var best = Entry(-1.0, null)
        for (aim in AIMS) {
            val value = branch(cur, darts, aim, ::checkoutSolve)
            if (value > best.value) best = Entry(value, aim)
        }
        checkoutMemo[key] = best
        return best
    }

    /** Chance of ending the turn on a score worth checking out from. */
    private fun setupSolve(cur: Int, darts: Int, guess: Double): Entry {
        if (darts == 0) return Entry(leaveValue(cur, guess), null)

        val store = if (cur == current) local else setupMemo
        val key = cur to darts
        store[key]?.let { return it }

        var best = Entry(-1.0, null)
        for (aim in AIMS) {
            val value = branch(cur, darts, aim) { s, l -> setupSolve(s, l, guess) }
            if (value > best.value) best = Entry(value, aim)
        }
        store[key] = best
        return best
    }

    /**
     * The value of throwing one dart.
     *
     * A dart has three outcomes: it hits, it drops into the single bed
     * above it, or it misses entirely. A bust is a wasted turn, so it scores
     * nothing.
     */
    private inline fun branch(
        cur: Int,
        darts: Int,
        aim: Aim,
        solve: (Int, Int) -> Entry,
    ): Double {
        var total = 0.0
        val rest = solve(cur, darts - 1).value

        val after = cur - aim.value
        if (after == 0) {
            total += aim.pHit * (if (aim.finishes) 1.0 else rest)
        } else if (after > 0) {
            total += aim.pHit * solve(after, darts - 1).value
        }
        // after < 0 is a bust: the turn is wasted, so it scores nothing.

        if (aim.wobbleValue != 0) {
            val wobbled = cur - aim.wobbleValue
            if (wobbled > 0) {
                total += aim.pWobble * solve(wobbled, darts - 1).value
            } else if (wobbled == 0) {
                // The wobble lands in a single bed, so it can never be the
                // double that finishes the game: the dart is simply wasted.
                total += aim.pWobble * rest
            }
        }

        total += (1.0 - aim.pHit - aim.pWobble) * rest
        return total
    }

    // -- values ------------------------------------------------------------

    /** Chance of checking out in the coming turn. */
    fun turnValue(score: Int): Double {
        if (score <= 0) return 0.0
        if (score <= Darts.MAX_CHECKOUT) return checkoutValue[score] ?: 0.0
        return setupValue[score] ?: 0.0
    }

    /**
     * What a leave of `score` is worth.
     *
     * A checkout leave is worth its checkout percentage. A score that still
     * needs a turn of setup is worth a fraction of that. A bogey is worth
     * nothing.
     */
    private fun leaveValue(score: Int, guess: Double = 0.0): Double {
        if (score <= 0) return 0.0
        if (score <= Darts.MAX_CHECKOUT) return checkoutValue[score] ?: 0.0
        if (score == current) return SETUP_FACTOR * guess
        return SETUP_FACTOR * (setupValue[score] ?: 0.0)
    }

    /** Chance of checking out in the coming turn. Zero above 170. */
    fun checkoutValue(score: Int): Double {
        if (score <= 0 || score > Darts.MAX_CHECKOUT) return 0.0
        return checkoutValue[score] ?: 0.0
    }

    /**
     * Pre-computes the value of every remaining score.
     *
     * A score's value depends on the values below it, so the sweep runs
     * upwards: first the checkout values up to 170, then the setup values
     * above them.
     */
    fun build(): Planner {
        for (cur in 1..Darts.MAX_CHECKOUT) {
            checkoutValue[cur] = checkoutSolve(cur, 3).value
        }

        // A setup score can be left untouched, which sends the turn back to
        // the same score, so its value is solved as a fixed point.
        for (cur in Darts.MAX_CHECKOUT + 1..maxScore) {
            current = cur
            var guess = 0.0
            repeat(64) {
                local.clear()
                val value = setupSolve(cur, 3, guess).value
                if (kotlin.math.abs(value - guess) < 1e-12) return@repeat
                guess = value
                if (it == 63) {
                    // The loop ran out. The value is as settled as it is going
                    // to get.
                }
            }
            setupValue[cur] = guess
            setupMemo.putAll(local)
            local.clear()
        }

        current = maxScore
        return this
    }

    // -- lookups -----------------------------------------------------------

    private fun aimAt(cur: Int, darts: Int, setup: Boolean): Aim? {
        if (cur <= 0) return null
        return if (setup) {
            val guess = setupValue[current] ?: 0.0
            setupSolve(cur, darts, guess).aim
        } else {
            checkoutSolve(cur, darts).aim
        }
    }

    /**
     * The darts to throw, one after another, assuming each one hits.
     *
     * Returns the aims, the score left standing, and whether they win the
     * leg.
     */
    private fun walk(
        score: Int,
        cur: Int,
        darts: Int,
        setup: Boolean,
    ): Triple<List<Aim>, Int, Boolean> {
        val aims = mutableListOf<Aim>()
        var remaining = cur
        var left = darts
        while (left > 0 && remaining > 0) {
            val aim = aimAt(remaining, left, setup) ?: break
            aims.add(aim)
            left -= 1
            if (aim.value == remaining) {
                if (aim.finishes) return Triple(aims, 0, true)
                continue
            }
            remaining -= aim.value
            if (remaining == 0) return Triple(aims, 0, true)
        }
        return Triple(aims, remaining, false)
    }

    /** Doubles reachable while playing `score` - the more the better. */
    fun doubleChances(score: Int, darts: Int = 3): List<String> {
        val found = sortedSetOf<String>()

        fun walk(cur: Int, left: Int) {
            if (left == 0 || cur <= 0) return
            val aim = aimAt(cur, left, false) ?: return
            if (aim.finishes) found.add(aim.label)
            val after = cur - aim.value
            if (after > 0) walk(after, left - 1)
            if (aim.wobbleValue != 0) {
                val wobbled = cur - aim.wobbleValue
                if (wobbled > 0) walk(wobbled, left - 1)
            }
        }

        walk(score, darts)
        return found.sortedWith(compareBy({ doubleOrder(it).first }, { doubleOrder(it).second }))
    }

    // -- the double the player asked for ------------------------------------

    /**
     * The darts that get a shot at `double` from `score`, or null if there is
     * no way.
     *
     * This is the planner's usual search with one double made compulsory: the
     * other finishing darts may still be thrown, but only this one may win
     * the leg, so what it maximises is the chance of winning on the double
     * the player asked for. Where that is a shot this visit it is a checkout;
     * where it cannot be reached in three darts the same search plans the
     * setup that leaves twice the double's number standing for the next
     * visit.
     */
    fun routeToDouble(score: Int, double: Int, darts: Int = 3): Route? {
        val finish = aimForDouble(double) ?: return null
        if (score <= 0 || score > maxScore || score == 1) return null

        val tables = routeTables(finish, score)
        val entry = routeEntry(tables, score, darts)
        val first = entry.aim ?: return null
        if (entry.value <= 0.0) return null

        val walked = walkTable(tables, score, darts, finish)
        val aims = walked.first
        val leaves = walked.second
        val finished = walked.third

        // What to throw if the first dart drops into its single bed. The
        // route has no second chance at its own double, so this is often
        // nothing.
        var fallback: List<Aim> = emptyList()
        var fallbackLeaves = score
        if (aims.isNotEmpty() && aims[0].wobbleValue != 0) {
            val wobbled = score - aims[0].wobbleValue
            if (wobbled > 0) {
                val w = walkTable(tables, wobbled, darts - 1, finish)
                fallback = w.first
                fallbackLeaves = w.second
            }
        }

        return Route(
            score = score,
            double = doubleFromLabel(finish.label) ?: double,
            aims = aims,
            leaves = leaves,
            finishes = finished,
            probability = entry.value,
            fallbackAims = fallback,
            fallbackLeaves = fallbackLeaves,
        )
    }

    /**
     * The route's own choice at `score` with `darts` darts left.
     *
     * At or below 170 the checkout table answers "how do I win this visit",
     * which is what the coach does at those scores. When it has no answer -
     * the double they asked for cannot be reached in the darts left - the job
     * becomes a setup one, and the leave has to be on their double.
     */
    private fun routeEntry(tables: RouteTables, score: Int, darts: Int): Entry {
        if (score <= Darts.MAX_CHECKOUT) {
            val found = tables.checkout[score to darts]
            if (found != null && found.value > 0.0 && found.aim != null) return found
        }
        return tables.setup[score to darts] ?: Entry(0.0, null)
    }

    /**
     * Best dart for every score when the leg may only end on `finish`.
     *
     * The same two sweeps as [build], run up to the planner's ceiling and
     * cached per double: first the checkout scores up to 170, then the setup
     * scores above them, whose value is solved as a fixed point because a
     * visit can leave them standing untouched.
     *
     * A score only ever depends on lower scores and fewer darts, so one table
     * answers every score in the game: the coach builds it the first time
     * the player names a double and reads it for the rest of the leg.
     */
    private fun routeTables(finish: Aim, score: Int): RouteTables {
        routeCache[finish.label]?.let { (top, tables) ->
            if (top >= score) return tables
        }

        val checkout = mutableMapOf<Pair<Int, Int>, Entry>()
        val setup = mutableMapOf<Pair<Int, Int>, Entry>()
        val checkoutValues = mutableMapOf<Int, Double>()
        val setupValues = mutableMapOf<Int, Double>()
        val local = mutableMapOf<Pair<Int, Int>, Entry>()

        // Only the double they asked for may end the leg. The other finishing
        // darts stay on the board as ordinary scoring darts, because a player
        // who wants double sixteen still wants to throw at the bull.
        val options = AIMS.map { aim ->
            if (!aim.finishes || aim.label == finish.label) aim
            else aim.copy(finishes = false)
        }

        var current = 0
        var guess = 0.0

        fun leaveValue(cur: Int): Double {
            if (cur <= 0) return 0.0
            if (cur <= Darts.MAX_CHECKOUT) return checkoutValues[cur] ?: 0.0
            if (cur == current) return SETUP_FACTOR * guess
            return SETUP_FACTOR * (setupValues[cur] ?: 0.0)
        }

        fun solveCheckout(cur: Int, darts: Int): Entry {
            // Best dart for `cur` when the visit must be won right now.
            if (darts == 0) return Entry(0.0, null)
            val key = cur to darts
            checkout[key]?.let { return it }
            var best = Entry(-1.0, null)
            for (aim in options) {
                val value = branch(cur, darts, aim, ::solveCheckout)
                if (value > best.value) best = Entry(value, aim)
            }
            checkout[key] = best
            return best
        }

        fun solveSetup(cur: Int, darts: Int): Entry {
            // Best dart for `cur` when the visit only needs a good leave.
            if (darts == 0) return Entry(leaveValue(cur), null)
            val store = if (cur == current) local else setup
            val key = cur to darts
            store[key]?.let { return it }
            var best = Entry(-1.0, null)
            for (aim in options) {
                val value = branch(cur, darts, aim) { s, l -> solveSetup(s, l) }
                if (value > best.value) best = Entry(value, aim)
            }
            store[key] = best
            return best
        }

        val top = maxScore
        for (cur in 1..Darts.MAX_CHECKOUT) {
            checkoutValues[cur] = solveCheckout(cur, 3).value
        }
        for (cur in 1..top) {
            // Below 170 the leave is always a checkout, so nothing refers back
            // to this score and one pass is enough. Above it the leave can be
            // this same score again, so the value is iterated until it
            // settles.
            if (cur > Darts.MAX_CHECKOUT) {
                current = cur
                guess = 0.0
                repeat(64) {
                    local.clear()
                    val value = solveSetup(cur, 3).value
                    if (kotlin.math.abs(value - guess) < 1e-12) return@repeat
                    guess = value
                }
            } else {
                local.clear()
                guess = solveSetup(cur, 3).value
            }
            setupValues[cur] = guess
            setup.putAll(local)
            local.clear()
        }

        val tables = RouteTables(checkout, setup)
        routeCache[finish.label] = top to tables
        return tables
    }

    /**
     * Follow the route's own choices dart by dart, assuming each one hits.
     */
    private fun walkTable(
        tables: RouteTables,
        score: Int,
        darts: Int,
        finish: Aim,
    ): Triple<List<Aim>, Int, Boolean> {
        val aims = mutableListOf<Aim>()
        var cur = score
        var left = darts
        while (left > 0 && cur > 0) {
            val chosen = routeEntry(tables, cur, left).aim ?: break
            if (chosen.value > cur) {
                // The dart would bust, so what follows it is not a plan.
                break
            }
            aims.add(chosen)
            left -= 1
            if (chosen.label == finish.label && chosen.value == cur) {
                return Triple(aims, 0, true)
            }
            cur -= chosen.value
        }
        return Triple(aims, cur, false)
    }

    /** Chance of winning `score` this visit on `double`. */
    fun chanceToFinishOn(score: Int, double: Int, darts: Int = 3): Double {
        val route = routeToDouble(score, double, darts) ?: return 0.0
        return route.probability
    }

    /**
     * The darts to throw at `score`, plus the plan if the first one drops.
     */
    fun planFor(score: Int): Plan {
        if (score <= 0) return Plan(score = score)
        if (score == 1) {
            // The smallest double is 2, so one point can never be finished.
            return Plan(score = score)
        }

        // Scores that cannot be finished in three darts (bogey numbers) are
        // set up instead of checked out.
        val setup = score > Darts.MAX_CHECKOUT || checkoutValue(score) == 0.0
        val walked = walk(score, score, 3, setup)
        val aims = walked.first
        val leaves = walked.second
        val finishes = walked.third

        var fallback: List<Aim> = emptyList()
        var fallbackLeaves = score
        if (aims.isNotEmpty() && aims[0].wobbleValue != 0) {
            val wobbled = score - aims[0].wobbleValue
            if (wobbled > 0) {
                val w = walk(score, wobbled, 2, setup)
                fallback = w.first
                fallbackLeaves = w.second
            }
        }

        val doubles = if (setup) {
            if (leaves > 0) doubleChances(leaves) else emptyList()
        } else {
            doubleChances(score)
        }

        return Plan(
            score = score,
            aims = aims,
            leaves = leaves,
            finishes = finishes,
            fallbackAims = fallback,
            fallbackLeaves = fallbackLeaves,
            probability = turnValue(score),
            doubles = doubles,
        )
    }

    companion object {
        /**
         * A leave that still needs another turn of setup is worth much less
         * than a leave you can check out from straight away, so the search
         * always prefers a checkout leave when one is reachable.
         */
        private const val SETUP_FACTOR = 0.15

        /** The shared planner. Building it is the expensive part. */
        val shared: Planner by lazy { build() }

        /** Builds the planner. */
        fun build(): Planner = Planner(Darts.MAX_SCORE).build()
    }
}

/** The order doubles are listed in: the bull first, then by number. */
internal fun doubleOrder(label: String): Pair<Int, String> =
    if (label == "Bull") 0 to label else label.removePrefix("D").toInt() to label

/**
 * The finishing dart for a double number: 16 is D16, 50 is the bull.
 *
 * The bull is worth 50 rather than twice its number, which is why every
 * double is looked up by what its dart is worth. Twenty-five is the outer
 * bull, so it is taken to mean the double bull.
 */
internal fun aimForDouble(double: Int): Aim? {
    val target = if (double == 25) Darts.BULL_DOUBLE else double
    if (target !in 1..20 && target != Darts.BULL_DOUBLE) return null
    val value = if (target == Darts.BULL_DOUBLE) Darts.BULL_DOUBLE else 2 * target
    return FINISH_BY_VALUE[value]
}

/** The dart that finishes a leg on each number, looked up by what it is worth. */
private val FINISH_BY_VALUE: Map<Int, Aim> =
    AIMS.filter { it.finishes }.associateBy { it.value }

/** The number of the double a plan label names: `D16` is 16, `Bull` is 50. */
internal fun doubleFromLabel(label: String): Int? =
    if (label == "Bull") Darts.BULL_DOUBLE else label.removePrefix("D").toIntOrNull()

/** The plan label for a double number: 16 is `D16`, 50 is `Bull`. */
fun doubleLabel(double: Int): String? = aimForDouble(double)?.label

/** How a double is said out loud: "double twenty", "double bull". */
fun doubleSpeech(double: Int): String =
    aimForDouble(double)?.speech ?: "double $double"