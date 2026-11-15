package com.jr12866.dartcoach.core

/**
 * The setup chart: what to aim at when the leg is too high to finish from.
 *
 * This is the middle of the leg, where the job is not to score as much as
 * possible but to leave a score that can be finished from. At 350 a 180
 * leaves the 170 out still available; at 349 the same 180 leaves 169, which
 * no three darts can finish.
 *
 * The numbers come out of the same [Planner] the coach thinks with, so the
 * chart and the coach always agree. The notes are the strategy, which is
 * knowledge rather than arithmetic - the planner can tell you what leaves
 * 170, but it cannot tell you that 170 is the good one.
 */
object SetupChart {

    /** The lowest score that needs setting up rather than checking out. */
    const val MIN_SCORE = Darts.MAX_CHECKOUT + 1

    /** The score a leg starts at. */
    const val MAX_SCORE = Darts.MAX_SCORE

    /** Every score that needs setting up, in order. */
    val scores: List<Int> = (MIN_SCORE..MAX_SCORE).toList()

    /**
     * The chart for one score.
     *
     * A setup row says what to aim at, what it leaves, what to do if the
     * first dart drops into the single bed, and the doubles that are still
     * on from the leave.
     */
    fun rowFor(score: Int, planner: Planner = Planner.shared): SetupRow {
        // A score of 170 or less is a checkout, not a setup, and it is in
        // the out chart rather than here. The bogey numbers are the
        // exception: they cannot be finished, so they are set up.
        if (score < MIN_SCORE || score > MAX_SCORE) {
            return SetupRow.empty(score)
        }

        val plan = planner.planFor(score)
        val note = noteFor(score, plan)

        return SetupRow(
            score = score,
            aims = plan.labels,
            leaves = plan.leaves,
            fallback = plan.fallbackLabels,
            fallbackLeaves = plan.fallbackLeaves,
            doubles = plan.doubles,
            note = note,
            isBogey = Darts.isBogey(score),
        )
    }

    /** The whole chart, for every score that needs setting up. */
    fun all(planner: Planner = Planner.shared): List<SetupRow> =
        scores.map { rowFor(it, planner) }

    /**
     * The note on a row.
     *
     * This is the strategy, and it is the part the planner cannot work out.
     * There are four kinds:
     *
     * - a score that cannot be finished at all, so it has to be set up;
     * - the classic 170 out, which is the score every player wants to leave;
     * - a score that leaves a clean score for the next turn.
     *
     * The note is about the *leave*, not about the score, because at these
     * scores the job is what is left behind.
     */
    private fun noteFor(score: Int, plan: Plan): String = when {
        Darts.isBogey(score) -> "cannot finish - set up instead"

        // The classic 170 out. A 180 from 350 leaves it, and it is the best
        // leave on the board.
        plan.leaves == Darts.MAX_CHECKOUT -> "the classic 170 out"

        // A leave that can be finished from in the coming visit.
        plan.leaves in 2..Darts.MAX_CHECKOUT -> "leaves ${plan.leaves} for the next turn"

        else -> "set up"
    }

    /**
     * The scores worth aiming for.
     *
     * These are the leaves that can be finished from, and they are the ones
     * the coach aims at. A player who knows them can aim at a leave rather
     * than at a number.
     *
     * The test is the one from the strategy: a leave is good if a 180 from
     * the score that is *leaving* it would still leave something that can be
     * finished. In practice the good leaves are the ones that divide down
     * to a double and are not bogeys.
     */
    fun goodLeaves(planner: Planner = Planner.shared): List<Int> =
        (2..Darts.MAX_CHECKOUT).filter { leave ->
            Darts.canFinish(leave) && leave % 2 == 0
        }
}

/**
 * One line of the setup chart.
 */
data class SetupRow(
    /** The score. */
    val score: Int,
    /** The darts to throw, in order. */
    val aims: List<String> = emptyList(),
    /** What is left when the plan lands. */
    val leaves: Int = 0,
    /** What to throw if the first dart drops into the single bed. */
    val fallback: List<String> = emptyList(),
    /** What that leaves. */
    val fallbackLeaves: Int = 0,
    /** The doubles that can still be aimed at from the leave. */
    val doubles: List<String> = emptyList(),
    /** The strategy note. */
    val note: String = "",
    /** True when the score cannot be finished in three darts. */
    val isBogey: Boolean = false,
) {
    /** The plan as a line of text: "T20 T20 T20". */
    val aimsText: String get() = aims.joinToString(" ")

    /** The fallback as a line of text. */
    val fallbackText: String get() = fallback.joinToString(" ")

    companion object {
        /** A row for a score that is not in the chart. */
        fun empty(score: Int) = SetupRow(score = score)
    }
}
