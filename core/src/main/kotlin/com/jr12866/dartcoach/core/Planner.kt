package com.jr12866.dartcoach.core

/**
 * Works out what to throw at a given score.
 *
 * Ported from `Planner` in `src/game/darts.py`.
 *
 * The search is not cheap, so it is built once and shared. In Kotlin that
 * means a lazily-built singleton rather than the Python module-level global.
 */
class Planner private constructor() {

    /**
     * The darts to throw at `score`, plus the plan if the first one drops.
     *
     * Ported from `Planner.plan_for`.
     */
    fun planFor(score: Int): Plan {
        // TODO Phase 2
        return Plan(score = score)
    }

    /**
     * The darts that take `score` onto the double the player asked for, or
     * null when there is no shot at it.
     *
     * Ported from `Planner.route_to_double`.
     */
    fun routeToDouble(score: Int, double: Int): Route? {
        // TODO Phase 2
        return null
    }

    companion object {
        /** The shared planner. Building it is the expensive part. */
        val shared: Planner by lazy { build() }

        /**
         * Builds the planner.
         *
         * Ported from `Planner.build`.
         */
        fun build(): Planner {
            // TODO Phase 2
            return Planner()
        }
    }
}