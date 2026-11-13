package com.jr12866.dartcoach.core

/**
 * The state of the leg.
 *
 * Ported from `GameState` in `src/game/state.py`.
 */
data class GameState(
    /** The score left to finish. Starts at 501. */
    var remainingScore: Int = 501,
    /** How many turns have been played. */
    var turnsPlayed: Int = 0,
    /** The turns played, oldest first. */
    val turnHistory: MutableList<Turn> = mutableListOf(),
    /** The darts reported in the current turn. */
    val currentDarts: MutableList<Int> = mutableListOf(),
    /**
     * The double the player asked the coach to finish on, held for the rest
     * of the leg. Null means the coach picks its own.
     */
    var preferredDouble: Int? = null,
) {
    /** Records a whole turn and moves the score on. */
    fun recordTurn(score: Int) {
        turnHistory.add(Turn(score = score, darts = currentDarts.toList()))
        remainingScore -= score
        turnsPlayed += 1
        currentDarts.clear()
    }

    /** Records one dart in the current turn. */
    fun recordDarts(darts: List<Int>) {
        currentDarts.addAll(darts)
    }

    /** What the current turn has scored so far. */
    fun currentTurnScore(): Int = currentDarts.sum()

    /** True once three darts have been reported. */
    fun isTurnComplete(): Boolean = currentDarts.size >= MAX_DARTS_PER_TURN

    /** Throws away the darts reported in the current turn. */
    fun resetDarts() {
        currentDarts.clear()
    }

    /** Takes the last turn back. Returns false if there was nothing to undo. */
    fun undo(): Boolean {
        val last = turnHistory.removeLastOrNull() ?: return false
        remainingScore += last.score
        turnsPlayed -= 1
        return true
    }

    /** Starts a new leg. */
    fun newGame() {
        remainingScore = 501
        turnsPlayed = 0
        turnHistory.clear()
        currentDarts.clear()
        // The double the player asked for belongs to the leg they asked for
        // it in. A new game is a new leg, so the coach goes back to choosing.
        preferredDouble = null
    }
}

/** One turn: the score and the darts that made it. */
data class Turn(
    val score: Int,
    val darts: List<Int> = emptyList(),
)

/** A turn is three darts. */
const val MAX_DARTS_PER_TURN = 3