package com.jr12866.dartcoach.core

/**
 * The coach's state machine: what it heard, what it does about it, and what
 * it says.
 *
 * Ported from the `DartsCoach` class in `src/main.py` in the Windows repo.
 *
 * This is the part of the coach that carries the rules earned from live
 * failures, and they are not obvious from the code alone:
 *
 * - **Nothing counts on the first pass.** A score is read back and waits for
 *   a "yes".
 * - **A destructive command needs the wake word.** "new game" and "undo" are
 *   answered with "coach yes", not a bare "yes". A 440 Hz tone was once
 *   transcribed as "I'm going to go back" and undid a real turn.
 * - **A refused score is spoken aloud.** Silence would leave the player at
 *   the oche believing the score went in.
 * - **A command that throws work away supersedes a pending score.** Saying
 *   "undo" instead of answering a score read-back *is* that score's refusal.
 *
 * Each of those is a comment in the Python explaining what broke. The
 * comments travel with the port.
 */
class CoachEngine(
    /** The parsed state of the current leg. */
    val state: GameState = GameState(),
) {

    /** What the coach is currently saying, for the screen. */
    var message: String = ""
        private set

    /** The suggestion the coach last gave, for the screen. */
    var suggestion: String = ""
        private set

    /** The score waiting to be confirmed, if any. */
    var pendingScore: Int? = null
        private set

    /** The command waiting to be confirmed, if any. */
    var pendingCommand: String? = null
        private set

    /**
     * What the coach says when the player asks for a command that throws
     * work away.
     *
     * Each one says how to agree, because a bare "yes" is not enough.
     */
    private fun commandQuestion(command: String): String = when (command) {
        "new_game" ->
            if (state.remainingScore == 0) {
                "Start a new game at 501? Say coach yes to start."
            } else {
                "Start a new game at 501 and lose this one? Say coach yes to start."
            }
        "undo" -> "Undo the last turn? Say coach yes to undo it."
        else -> "Say coach yes to go ahead."
    }

    /**
     * Handles one thing the microphone heard.
     *
     * Returns what the coach should say, or null if it should stay quiet.
     */
    fun handle(text: String): String? {
        val rawText = text
        val stripped = stripWakeWord(text)

        // The confirmation exchange comes before anything else: while
        // something is waiting on a "yes", that word answers it and nothing
        // else does.
        val pending = pendingScore
        if (pending != null) {
            when (answer(stripped, rawText)) {
                Answer.CONFIRM -> {
                    confirmPendingScore()
                    return message
                }
                Answer.CANCEL -> {
                    pendingScore = null
                    state.resetDarts()
                    message = "Discarded ${numberToWords(pending)}."
                    return message
                }
                else -> {
                    // Anything else supersedes the question. The player has
                    // moved on to something else, and leaving the question
                    // armed would let a later "yes" meant for a score
                    // read-back undo a turn instead.
                    pendingScore = null
                    state.resetDarts()
                }
            }
        } else {
            val command = pendingCommand
            if (command != null) {
                // A bare "yes" is not enough to throw a leg away.
                //
                // The score read-back can rely on "yes" because the coach
                // has just said the number back and the player is answering
                // it; nothing in the room repeats a number. Commands are
                // ordinary sentences, and Whisper writes ordinary sentences
                // out of room noise: a stray "Yes." really does arrive from
                // the microphone, and the only thing that separates it from
                // a player agreeing is who was spoken to. So these need the
                // wake word.
                //
                // The question stays armed, and saying "no" on its own still
                // cancels it, because refusing costs nothing while agreeing
                // costs the leg.
                val addressed = containsWakeWord(rawText)
                when (answer(stripped, rawText)) {
                    Answer.CONFIRM -> {
                        if (!addressed) {
                            // Ignore it. The question is still open.
                            return null
                        }
                        runCommand(command)
                        return message
                    }
                    Answer.CANCEL -> {
                        pendingCommand = null
                        message = "Leaving that alone."
                        return message
                    }
                    else -> {
                        // Anything else supersedes the question.
                        pendingCommand = null
                    }
                }
            } else if (answer(stripped, rawText) != Answer.NONE) {
                // An answer to a question nobody asked. Ignore it quietly.
                return null
            }
        }

        val result = Parser.parse(stripped, state)

        if (isUnansweredConversation(rawText, result)) {
            return null
        }

        if (result.needsConfirmation) {
            pendingCommand = result.command
            message = commandQuestion(result.command)
            return message
        }

        when (result.command) {
            "whats_my_score" -> {
                message = "You're at ${state.remainingScore} " +
                    "after ${state.turnsPlayed} turns."
                return message
            }
            "continue" -> {
                message = "Resuming at ${state.remainingScore}. Say your score."
                return message
            }
            "invalid" -> {
                if (result.refused) {
                    // The player reported a turn and the parser would not
                    // take it. Silence would let them walk back to the oche
                    // believing the score went in, so say what happened.
                    message = "That score doesn't seem right. Please check and repeat."
                    return message
                }
                // Never a score report at all: conversation, or a wake-word
                // phrase the parser does not know. Nothing was refused, so
                // the coach has nothing to correct - unless the player
                // addressed the coach, in which case the coach owns the
                // reply even when it has nothing better to say.
                if (containsWakeWord(rawText)) {
                    message = "I didn't catch that. Please repeat."
                    return message
                }
                return null
            }
            "bust" -> {
                message = "Bust! You can't go below zero. " +
                    "Stay at ${state.remainingScore}. What did you actually score?"
                return message
            }
            "prefer_double" -> {
                return preferDouble(result.double)
            }
            "turn_score", "full_score" -> {
                // Read the score back before it counts. The headset hears a
                // wrong number often enough that the player has to be able to
                // correct it. A total that finishes the leg waits too: it is
                // the one score that cannot be argued with afterwards.
                pendingScore = result.score
                message = "You said ${numberToWords(result.score!!)}. Is that right?"
                return message
            }
            "qualified_darts" -> {
                // The parser reports the darts; recording them is the
                // engine's job. In the Windows build this is main.py's
                // game_state.record_darts(result.darts).
                state.recordDarts(result.darts!!)
                val score = state.currentTurnScore()
                val remaining = state.remainingScore
                if (!state.isTurnComplete() && score != remaining) {
                    // Still going: more darts to come.
                    return null
                }
                if (score > remaining) {
                    state.resetDarts()
                    message = "Bust! ${numberToWords(score)} is more than the " +
                        "${numberToWords(remaining)} you had left. Say the turn again."
                    return message
                }
                pendingScore = score
                message = "You said ${numberToWords(score)}. Is that right?"
                return message
            }
        }

        if (state.remainingScore == 0) {
            return null
        }

        return giveSuggestion()
    }

    /** The score is confirmed, so it counts. */
    private fun confirmPendingScore() {
        val score = pendingScore ?: return
        pendingScore = null
        state.recordTurn(score)

        if (state.remainingScore == 0) {
            message = "Congratulations! You finished in ${state.turnsPlayed} turns!"
            return
        }
        giveSuggestion()
    }

    /** Carries out a command the player has just confirmed. */
    private fun runCommand(command: String) {
        pendingCommand = null
        pendingScore = null
        when (command) {
            "new_game" -> {
                state.newGame()
                message = "New game. You're at 501. Say your score."
            }
            "undo" -> {
                if (state.turnHistory.isEmpty()) {
                    message = "Nothing to undo."
                } else {
                    state.undo()
                    message = "Turn undone. You're back at ${state.remainingScore}."
                }
            }
        }
    }

    /**
     * The player named the double they want to finish on.
     *
     * A double the player cannot reach from the score standing is not a
     * choice the coach can make for them: the double is worth more than the
     * score, so there is no shot at it. The coach says so, forgets the
     * choice and gives the out it had.
     */
    private fun preferDouble(double: Int?): String? {
        if (double == null) return null
        if (state.remainingScore == 0) {
            // The leg is over. There is nothing left to play for.
            return null
        }
        if (Planner.shared.routeToDouble(state.remainingScore, double) == null) {
            state.preferredDouble = null
            message = "No shot at ${doubleSpeech(double)} from ${state.remainingScore}."
            return message + " " + (giveSuggestion() ?: "")
        }
        state.preferredDouble = double
        message = "${doubleSpeech(double).replaceFirstChar { it.uppercase() }} it is."
        return message + " " + (giveSuggestion() ?: "")
    }

    /**
     * Works out what to throw at the score standing, and says it.
     *
     * When the player has named a double, the coach plays for it: the route
     * onto that double rather than its own choice.
     */
    private fun giveSuggestion(): String? {
        val remaining = state.remainingScore
        var preferred = state.preferredDouble
        if (preferred != null &&
            Planner.shared.routeToDouble(remaining, preferred) == null
        ) {
            // The double the score has dropped below is not on the board any
            // more, so the coach says so, forgets the choice and gives its
            // own out.
            state.preferredDouble = null
            val note = "No shot at ${doubleSpeech(preferred)} from $remaining."
            preferred = null
            suggestion = Planner.shared.planFor(remaining).displayLines.joinToString("\n")
            return "$note $suggestion"
        }

        val plan = if (preferred != null) {
            Planner.shared.routeToDouble(remaining, preferred)
        } else {
            null
        }
        val text = if (plan != null) {
            "You're at $remaining. Throw ${plan.speak()} to leave ${plan.leaves} " +
                "for ${doubleSpeech(preferred!!)}."
        } else {
            val p = Planner.shared.planFor(remaining)
            if (p.finishes) {
                "You're at $remaining. Throw ${p.speak()} to win."
            } else {
                "You're at $remaining. Throw ${p.speak()} to leave ${p.leaves}."
            }
        }
        suggestion = Planner.shared.planFor(remaining).displayLines.joinToString("\n")
        return text
    }

    // -- the wake word and the answers -------------------------------------

    /**
     * Removes a leading wake word: "Coach, one eighty" is "one eighty".
     *
     * Returns the text unchanged if there was no leading wake word.
     */
    private fun stripWakeWord(text: String): String {
        val cleaned = text.trim()
        val match = LEADING_WAKE_WORD.find(cleaned)
        return if (match != null) {
            cleaned.removeRange(match.range).trim()
        } else {
            cleaned
        }
    }

    /**
     * Was the coach named in the words as they were heard?
     *
     * This is checked on the raw text, not on the text after the wake word
     * has been stripped: Whisper writes the name on whichever side the
     * player said it, so searching the leftovers would only ever find
     * nothing.
     */
    private fun containsWakeWord(rawText: String): Boolean =
        WAKE_WORD.containsMatchIn(rawText)

    /**
     * Should this phrase be ignored rather than answered?
     *
     * The parser calls everything it does not recognise "invalid", but two
     * very different things arrive there. A player who reported a turn and
     * was refused needs to be told; a player talking to someone else does
     * not. Only the second one is silence.
     */
    private fun isUnansweredConversation(rawText: String, result: ParseResult): Boolean {
        if (result.command != "invalid") return false
        if (result.refused) return false
        if (containsWakeWord(rawText)) return false
        val stripped = stripWakeWord(rawText)
        return !(Parser.isConfirmation(stripped) || Parser.isCancellation(stripped))
    }

    /**
     * Does this phrase answer the question the coach just asked?
     *
     * The wake word is allowed but not needed: "coach yes" answers as well as
     * "yes", so it is stripped before matching.
     */
    private fun answer(stripped: String, rawText: String): Answer {
        val addressed = containsWakeWord(rawText)
        val phrase = WAKE_WORD.replace(stripped, " ").replace(Regex("\\s+"), " ").trim()
        if (Parser.isConfirmation(phrase)) return Answer.CONFIRM
        if (Parser.isCancellation(phrase)) return Answer.CANCEL
        return Answer.NONE
    }

    companion object {
        /**
         * The coach's name inside an answer.
         *
         * Unlike a command, a short answer can have the name anywhere in it
         * - "yes coach" is as common as "coach yes" - so this is its own
         * pattern rather than the leading-wake-word one.
         */
        private val WAKE_WORD = Regex("\\bcoach\\b", RegexOption.IGNORE_CASE)

        /** A wake word that opens the sentence. */
        private val LEADING_WAKE_WORD = Regex(
            "^\\s*(?:coach[,.\u005cs]*|hey[, ]+coach[,.\u005cs]*|ok[, ]+coach[,.\u005cs]*)\\s*",
            RegexOption.IGNORE_CASE,
        )
    }
}

/** What the player said in answer to a question. */
enum class Answer {
    /** The player agreed. */
    CONFIRM,

    /** The player refused. */
    CANCEL,

    /** It was not an answer at all. */
    NONE,
}

/** The score as words: 140 is "one hundred and forty". */
internal fun numberToWords(number: Int): String = Darts.numberToWords(number)