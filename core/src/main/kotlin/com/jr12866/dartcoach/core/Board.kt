package com.jr12866.dartcoach.core

/**
 * The shape of the board: which sectors sit next to which.
 *
 * The coach already knows what a miss that falls straight down is worth. A
 * treble twenty comes off as the single twenty and leaves 368 at 388, and
 * that is a different problem from a treble nineteen, which comes off as
 * the single nineteen and leaves an odd number no double can take. That
 * knowledge sits in the planner, where a miss is one of the three branches
 * of the search.
 *
 * What the planner cannot see is a miss that goes *sideways*. A dart that
 * slides off the treble twenty does not always fall into the single twenty:
 * if it crosses a wire it lands in the single bed of a neighbouring sector,
 * and which sector that is depends on where the sector sits on the board.
 * The treble twenty's neighbours are the single one and the single five; the
 * treble nineteen's are the single three and the single seven.
 *
 * This is the board's geometry rather than a guess about the player, so it
 * is data. It is used to tell the player what a sideways miss would leave
 * them with.
 *
 * Ported from `BOARD_CLOCKWISE` in `src/game/darts.py` in the Windows repo.
 */
object Board {

    /**
     * The sectors of a standard board, clockwise from twenty.
     *
     * The order is the physical one: twenty at the top, then one to its
     * right, then eighteen, and so on round to five and back to twenty.
     * Two sectors are neighbours when they sit next to each other in this
     * list.
     */
    val CLOCKWISE: List<Int> = listOf(
        20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5,
    )

    /**
     * The sectors either side of `bed`.
     *
     * The first is the one to the left of the bed and the second is the one
     * to its right, as the board is hung: twenty is at the top, so one is to
     * the right of twenty and five is to its left.
     */
    fun neighbours(bed: Int): List<Int> {
        val index = CLOCKWISE.indexOf(bed)
        require(index >= 0) { "not a bed on the board: $bed" }
        val size = CLOCKWISE.size
        return listOf(
            CLOCKWISE[(index - 1 + size) % size],
            CLOCKWISE[(index + 1) % size],
        )
    }

    /**
     * The single beds a dart aimed at `aim` can slide into.
     *
     * Only trebles and doubles are given a sideways miss. A single is a
     * large target and is missed rarely, and the bull scatters its misses
     * across the whole board, so neither has a neighbour worth naming.
     */
    fun wireBeds(aim: Aim): List<Int> = when (aim.kind) {
        "treble", "double" -> neighbours(aim.segment.bed)
        else -> emptyList()
    }
}
