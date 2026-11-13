package com.jr12866.dartcoach.core

/**
 * What the parser made of one phrase.
 *
 * Ported from `ParseResult` in `src/game/parser.py`.
 */
data class ParseResult(
    /**
     * "turn_score", "qualified_darts", "bust", "new_game", "undo",
     * "whats_my_score", "continue", "prefer_double", or "invalid".
     */
    val command: String,
    /** The turn score, when the command carries one. */
    val score: Int? = null,
    /** The individual darts, when the player reported them one at a time. */
    val darts: List<Int>? = null,
    /** The double the player asked to finish on. */
    val double: Int? = null,
    /**
     * True when the parser found a score report and would not take it. The
     * player stood at the oche expecting to be scored, so they have to be
     * told the number was refused. An invalid result without this flag is a
     * phrase that was never a score report at all.
     */
    val refused: Boolean = false,
    /**
     * True when the command throws a turn or a whole game away. The coach
     * asks the player to answer "yes" before acting on it.
     */
    val needsConfirmation: Boolean = false,
)

/**
 * Turns what the player said into a command.
 *
 * Ported from `InputParser` in `src/game/parser.py`.
 *
 * The rules here are not obvious and were not written from first principles.
 * They came from live failures on the Windows build, and each of them has a
 * comment in the Python explaining what broke. The parity corpus in
 * `corpus/parser.json` pins all of them.
 */
object Parser {

    /**
     * The commands that throw work away. They get the same read-back a
     * spoken score gets, because room noise reaches the parser as fluent
     * sentences: Whisper really did hear a 440 Hz tone as "I'm going to go
     * back", and that undid a real turn.
     */
    private val DESTRUCTIVE_COMMANDS = setOf("new_game", "undo")

    /** Parses one phrase in the context of the current game. */
    fun parse(text: String, state: GameState): ParseResult {
        // TODO Phase 2: port the body of InputParser.parse.
        return ParseResult(command = "invalid")
    }

    /**
     * True when the whole phrase is the player agreeing with what was heard.
     *
     * Ported from `is_confirmation` in `src/game/parser.py`.
     */
    fun isConfirmation(text: String): Boolean {
        // TODO Phase 2
        return false
    }

    /**
     * True when the whole phrase is the player rejecting what was heard.
     *
     * Ported from `is_cancellation` in `src/game/parser.py`.
     */
    fun isCancellation(text: String): Boolean {
        // TODO Phase 2
        return false
    }
}