package com.jr12866.dartcoach.core

/**
 * The out chart: what to throw at every score.
 *
 * This is the reference the coach works from, laid out as a table. The
 * numbers come out of the same [Planner] the coach thinks with, so a
 * personalised chart is the coach's own advice written down - not a separate
 * table that can drift away from it.
 */
object OutChart {

    /** The scores a leg can be finished from. */
    const val MIN_SCORE = 2

    /** The highest score that can be finished in three darts. */
    const val MAX_SCORE = Darts.MAX_CHECKOUT

    /** Every score from 2 to 170, in order. */
    val scores: List<Int> = (MIN_SCORE..MAX_SCORE).toList()

    /**
     * The chart for one score.
     *
     * A score that cannot be finished is still in the chart, marked as a
     * bogey, because a player who is left on 169 needs to know what to do
     * about it rather than find a gap.
     */
    fun rowFor(score: Int, planner: Planner = Planner.shared): ChartRow {
        if (score < MIN_SCORE) return ChartRow.empty(score)

        val plan = planner.planFor(score)
        val bogey = Darts.isBogey(score) || !Darts.canFinish(score)

        if (bogey) {
            // A bogey cannot be finished, so the chart shows what to aim at
            // instead: the plan that leaves something you can finish from.
            return ChartRow(
                score = score,
                route = plan.labels,
                leaves = plan.leaves,
                probability = 0.0,
                doubles = plan.doubles,
                bogey = true,
            )
        }

        return ChartRow(
            score = score,
            route = plan.labels,
            leaves = plan.leaves,
            probability = plan.probability,
            doubles = plan.doubles,
            bogey = false,
        )
    }

    /** The whole chart, for every score. */
    fun all(planner: Planner = Planner.shared): List<ChartRow> =
        scores.map { rowFor(it, planner) }

    /**
     * The route onto a chosen double from a score.
     *
     * This is the "what if I go for D16 instead" answer, and it is the
     * other half of a personalised chart: the same score has a different
     * route depending on which double the player finishes on.
     */
    fun routeOnto(score: Int, double: Int, planner: Planner = Planner.shared): ChartRoute? {
        val route = planner.routeToDouble(score, double) ?: return null
        return ChartRoute(
            score = score,
            double = double,
            route = route.labels,
            leaves = route.leaves,
            probability = route.probability,
            finishes = route.finishes,
        )
    }

    /**
     * Every route onto every double that can be reached from a score.
     *
     * This is what makes the chart worth reading: for 100 there is more than
     * one way out, and which one is best depends on which double the player
     * can hit.
     */
    fun routesFor(score: Int, planner: Planner = Planner.shared): List<ChartRoute> {
        val doubles = planner.doubleChances(score)
        val routes = mutableListOf<ChartRoute>()
        for (label in doubles) {
            val double = doubleFromLabel(label) ?: continue
            val route = routeOnto(score, double, planner) ?: continue
            routes.add(route)
        }
        // The best route first: the one most likely to win the leg.
        routes.sortByDescending { it.probability }
        return routes
    }
}

/**
 * One line of the chart.
 */
data class ChartRow(
    /** The score. */
    val score: Int,
    /** The darts to throw, in order. */
    val route: List<String> = emptyList(),
    /** What is left when the plan lands. */
    val leaves: Int = 0,
    /** The chance of checking out from this score in the coming turn. */
    val probability: Double = 0.0,
    /** The doubles that can still be aimed at from this score. */
    val doubles: List<String> = emptyList(),
    /** True when the score cannot be finished in three darts. */
    val bogey: Boolean = false,
) {
    /** The route as a line of text: "T20 T20 D20". */
    val routeText: String get() = route.joinToString(" ")

    /** The percentage, as it is shown. */
    val percentText: String
        get() = if (bogey) "--" else "${(probability * 100).toInt()}%"

    companion object {
        /** A row for a score that is not in the chart. */
        fun empty(score: Int) = ChartRow(score = score, bogey = true)
    }
}

/**
 * A route onto a particular double.
 */
data class ChartRoute(
    /** The score. */
    val score: Int,
    /** The double the route finishes on. */
    val double: Int,
    /** The darts to throw, in order. */
    val route: List<String> = emptyList(),
    /** What is left when the plan lands. */
    val leaves: Int = 0,
    /** The chance of winning the leg this way. */
    val probability: Double = 0.0,
    /** True when this route wins the leg in the coming visit. */
    val finishes: Boolean = false,
) {
    /** The route as a line of text. */
    val routeText: String get() = route.joinToString(" ")

    /** The double's label: "D16" or "Bull". */
    val doubleLabel: String get() = doubleLabel(double) ?: "D$double"

    /** The percentage, as it is shown. */
    val percentText: String get() = "${(probability * 100).toInt()}%"
}
