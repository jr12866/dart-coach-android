package com.jr12866.dartcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The board's geometry: which sectors sit next to which.
 *
 * The planner has always known what a miss that falls straight down is
 * worth. What it could not see was a miss that goes sideways, and the
 * geometry is what tells it where such a dart lands.
 */
class BoardTest {

    @Test
    fun `the treble twenty's neighbours are the single five and the single one`() {
        // Twenty is at the top of the board, with five to its left and one to
        // its right.
        assertEquals(listOf(5, 1), Board.neighbours(20))
    }

    @Test
    fun `the treble nineteen's neighbours are the single three and the single seven`() {
        assertEquals(listOf(3, 7), Board.neighbours(19))
    }

    @Test
    fun `the double sixteen's neighbours are the single seven and the single eight`() {
        // This is why the double sixteen is a forgiving one: a dart that
        // crosses a wire lands in the single eight, which is still an even
        // number that can be divided down to a double.
        assertEquals(listOf(7, 8), Board.neighbours(16))
    }

    @Test
    fun `every bed has two neighbours that are neighbours of it back`() {
        // The geometry has to hang together, or the coach names the wrong bed.
        for (bed in 1..20) {
            val neighbours = Board.neighbours(bed)
            assertEquals(2, neighbours.size, "for $bed")
            assertTrue(neighbours[0] != neighbours[1], "for $bed")
            assertTrue(bed in Board.neighbours(neighbours[0]), "for $bed")
            assertTrue(bed in Board.neighbours(neighbours[1]), "for $bed")
        }
    }

    @Test
    fun `every bed is on the board exactly once`() {
        assertEquals(20, Board.CLOCKWISE.size)
        assertEquals((1..20).toSet(), Board.CLOCKWISE.toSet())
    }

    @Test
    fun `a treble can slide into its neighbours`() {
        val aim = AIMS.first { it.label == "T20" }
        assertEquals(listOf(5, 1), Board.wireBeds(aim))
    }

    @Test
    fun `a double can slide into its neighbours`() {
        val aim = AIMS.first { it.label == "D16" }
        assertEquals(listOf(7, 8), Board.wireBeds(aim))
    }

    @Test
    fun `a single and the bull have no sideways miss`() {
        // A single is a large target and is missed rarely, and a bull miss
        // scatters across the whole board, so neither has a neighbour worth
        // naming.
        val single = AIMS.first { it.label == "1" }
        val bull = AIMS.first { it.label == "Bull" }
        val outerBull = AIMS.first { it.label == "25" }
        assertEquals(emptyList(), Board.wireBeds(single))
        assertEquals(emptyList(), Board.wireBeds(bull))
        assertEquals(emptyList(), Board.wireBeds(outerBull))
    }

    @Test
    fun `the contingency names where a sideways miss lands`() {
        val plan = Planner.shared.planFor(388, 2)

        val contingency = assertNotNull(plan.contingency)
        // The treble twenty's neighbours are the single five and the single
        // one: 388 - 5 and 388 - 1.
        assertEquals(listOf(5, 1), contingency.wires.map { it.bed })
        assertEquals(listOf(383, 387), contingency.wires.map { it.score })
    }

    @Test
    fun `the screen carries both kinds of miss`() {
        val plan = Planner.shared.planFor(388, 2)

        val contingency = assertNotNull(plan.contingency)
        assertEquals(
            "If it drops to the single: 368, then T20\n" +
                "If it slides into 1 or 5: 387 or 383",
            contingency.line,
        )
    }

    @Test
    fun `the advice itself does not change`() {
        // The sideways miss is told to the player, but it does not move the
        // advice: the plan is what the parity corpus pins.
        val plan = Planner.shared.planFor(388, 2)
        assertEquals(listOf("T20", "T20"), plan.labels)
        assertEquals(268, plan.leaves)
    }
}
