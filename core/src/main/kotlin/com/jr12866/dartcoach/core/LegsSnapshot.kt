package com.jr12866.dartcoach.core

/**
 * The legs that have been played, in a form that can be saved.
 *
 * The scores are what matter and they are saved as a plain list. The darts
 * are saved too, because they are what the weak-segment analysis is worked
 * out from, and a player who has thrown a hundred legs should not lose the
 * record of them because the app was closed.
 */
data class LegsSnapshot(
    /** One entry per leg: "turns,checkoutScore,score,score,score". */
    val legs: List<String>,
) {
    /** Puts the legs back. */
    fun toLegs(): List<Leg> {
        val out = mutableListOf<Leg>()
        for (entry in legs) {
            val parts = entry.split(",")
            if (parts.size < 3) continue
            val turns = parts[0].toIntOrNull() ?: continue
            val checkout = parts[1].toIntOrNull() ?: continue
            val scores = parts.drop(2).mapNotNull { it.toIntOrNull() }
            if (scores.isEmpty()) continue
            out.add(
                Leg(
                    turns = turns,
                    scores = scores,
                    checkouts = 1,
                    checkoutScore = checkout,
                )
            )
        }
        return out
    }

    companion object {
        /** Takes a snapshot of the legs. */
        fun of(legs: List<Leg>): LegsSnapshot = LegsSnapshot(
            legs = legs.map { leg ->
                val scores = leg.scores.joinToString(",")
                "${leg.turns},${leg.checkoutScore ?: 0},$scores"
            }
        )

        /** The empty snapshot, for a player who has not finished a leg. */
        val EMPTY = LegsSnapshot(emptyList())
    }
}
