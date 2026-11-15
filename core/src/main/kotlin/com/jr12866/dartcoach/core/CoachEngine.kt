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
    /**
     * The planner the coach thinks with.
     *
     * The shared planner is the one the coach was ported with: the
     * population averages. A player with a profile gets a planner built from
     * it, so the advice is theirs.
     */
    planner: Planner? = null,
    /**
     * What the coach has learned about how the player throws.
     *
     * The learner is how the coach turns a player's own play into their own
     * probabilities. It is separate from the planner, because a planner is
     * built from a profile and the profile comes out of the learner.
     */
    val learner: HitLearner = HitLearner(),
) {

    /**
     * The planner in use.
     *
     * This is not a default parameter value because the shared planner is a
     * lazy delegate, and a default argument is evaluated when the call is
     * made rather than when the constructor runs.
     */
    val planner: Planner = planner ?: Planner.shared

    /**
     * The legs that have been finished.
     *
     * This is what the statistics are worked out from. The app keeps them
     * between sessions.
     */
    val legs: MutableList<Leg> = mutableListOf()

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

    /** The intended target of the dart currently being corrected, if any. */
    var pendingMissTarget: Segment? = null
        private set

    /**
     * Whether missed-target corrections are available.
     *
     * The core stays free of Android billing. The app sets this from its
     * entitlement, so a correction can be gated without putting purchase
     * logic in the game rules.
     */
    var missedTargetCorrectionAvailable: Boolean = true

    /**
     * Whether the last correction the coach handled actually changed the
     * turn.
     *
     * A correction can be refused - the landing could not be identified, or
     * the feature is not available - and a refused correction is a question
     * to the player rather than a new score. The caller needs to tell the
     * two apart, because the score read-back is only still true in the
     * second case.
     */
    private var correctionApplied: Boolean = false

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
                    // A correction is not an answer to the read-back. It
                    // changes the turn the read-back is about, so it must be
                    // handled before the read-back is discarded.
                    val correction = Parser.parse(stripped, state)
                    if (correction.command == "report_miss") {
                        val said = reportMiss(correction)
                        if (!correctionApplied) {
                            // The correction was refused - the landing could
                            // not be identified, or the feature is not
                            // available. The turn has not changed, so the
                            // read-back is still the right question and the
                            // refusal is the whole answer. Appending the
                            // read-back to it would put two questions in
                            // one breath, and the player would not know
                            // which one to answer.
                            if (said != null) {
                                message = said
                            }
                            return message
                        }
                        pendingScore = state.currentTurnScore()
                        if (said != null && pendingScore != null) {
                            message = if (state.currentDarts.size >= MAX_DARTS_PER_TURN) {
                                "$said You said ${numberToWords(pendingScore!!)}. Right?"
                            } else {
                                said
                            }
                            return message
                        }
                        return said
                    }

                    // Anything else supersedes the question. The player has
                    // moved on to something else, and leaving the question
                    // armed would let a later "yes" meant for a score
                    // read-back undo a turn instead.
                    pendingScore = null
                    pendingMissTarget = null
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
            "whats_the_leave" -> {
                message = whatWouldThatLeave()
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
            "report_miss" -> {
                return reportMiss(result)
            }
            "turn_score", "full_score" -> {
                // Read the score back before it counts. The headset hears a
                // wrong number often enough that the player has to be able to
                // correct it. A total that finishes the leg waits too: it is
                // the one score that cannot be argued with afterwards.
                pendingScore = result.score
                message = "You said ${numberToWords(result.score!!)}. Right?"
                return message
            }
            "qualified_darts" -> {
                // The parser reports the darts; recording them is the
                // engine's job. In the Windows build this is main.py's
                // game_state.record_darts(result.darts).
                state.recordDarts(result.darts!!, result.dartSegments)
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
                message = "You said ${numberToWords(score)}. Right?"
                return message
            }
        }

        if (state.remainingScore == 0) {
            return null
        }

        return giveSuggestion()
    }

    /**
     * Corrects the last dart in the current turn.
     *
     * The intended target may be named by the player or inferred from the
     * coach's last suggestion. The actual landing replaces the last dart,
     * and the learner is told about the miss before the turn is confirmed.
     *
     * A correction that cannot be acted on - because the landing could not
     * be identified, or because the feature is not available - asks the
     * player to say it again rather than guessing. The turn is left exactly
     * as it was, so a refused correction cannot change a score.
     */
    private fun reportMiss(result: ParseResult): String? {
        correctionApplied = false

        if (!missedTargetCorrectionAvailable) {
            message = "Missed-target corrections are part of Pro."
            return message
        }

        val actual = result.actualSegment
        if (actual == null) {
            // The phrase was shaped like a correction, but no landing could
            // be read out of it. Whisper was heard to write the spoken "8"
            // as "eat", so this happens in practice, and saying nothing
            // would leave the player at the oche with no idea that the
            // coach had not understood.
            message = if (result.intendedSegment != null) {
                "I heard the ${result.intendedSegment.speech} you missed, but not " +
                    "what you hit. Say what you hit instead."
            } else {
                "I didn't catch which dart you hit. Please repeat."
            }
            return message
        }

        if (state.currentDarts.isEmpty()) {
            message = "Tell me the dart you missed first, then what you hit instead."
            return message
        }

        val target = result.intendedSegment ?: intendedTargetForLastDart()
        if (target == null) {
            message = "I don't know which target you missed. Please name it and what you hit instead."
            return message
        }

        if (!state.replaceLastDart(actual.value, actual)) {
            message = "I couldn't correct that dart. Please say the turn again."
            return message
        }

        correctionApplied = true

        learner.learnMiss(target, actual)
        pendingMissTarget = target
        val dartsLeft = MAX_DARTS_PER_TURN - state.currentDarts.size
        val turnScore = state.currentTurnScore()
        val scoreLeft = state.remainingScore - turnScore

        if (scoreLeft < 0) {
            state.resetDarts()
            pendingMissTarget = null
            message = "Bust! ${numberToWords(turnScore)} is more than the " +
                "${numberToWords(state.remainingScore)} you had left. Say the turn again."
            return message
        }

        val plan = planner.planFor(scoreLeft, dartsLeft)
        val kind = if (plan.finishes) "Checkout" else "Setup"
        val spoken = if (plan.aims.isEmpty()) {
            "$kind. There is no finish from ${numberToWords(scoreLeft)} with " +
                "$dartsLeft dart${if (dartsLeft == 1) "" else "s"} left."
        } else {
            "$kind. ${sentence(plan.speak())}."
        }
        suggestion = (listOf(kind) + plan.displayLines).joinToString("\n")
        message = "You hit ${actual.speech} instead. That leaves " +
            "${numberToWords(scoreLeft)} with $dartsLeft dart${if (dartsLeft == 1) "" else "s"} left. $spoken"
        return message
    }

    /**
     * The target the coach aimed at for the last dart in the current turn.
     *
     * The learner keeps the aims in the order they were suggested. The last
     * reported dart is matched to the last aim, so the first two darts in a
     * three-dart turn can be corrected without naming their targets.
     *
     * A qualified dart report carries its own segment. When that segment is
     * the target itself, that is the target the player missed; it is the
     * coach's plan that matters when the report was a bare value.
     */
    private fun intendedTargetForLastDart(): Segment? {
        val dartIndex = state.currentDarts.lastIndex
        if (dartIndex < 0) return null

        val reported = if (dartIndex < state.currentSegments.size) {
            state.currentSegments[dartIndex]
        } else {
            null
        }
        val aims = learner.aimed
        if (dartIndex >= aims.size) return reported

        // The aim list is for the whole turn. When it has fewer entries than
        // the turn, the report is the only reliable source for this dart.
        val planned = aims[dartIndex]
        return reported ?: planned
    }

    /** The score is confirmed, so it counts. */
    private fun confirmPendingScore() {
        val score = pendingScore ?: return
        pendingScore = null
        pendingMissTarget = null
        // Learn from the turn before the state is cleared, because the
        // segments are what is being learned from. A turn the player
        // reported as a bare total has nothing to attribute, so the learner
        // ignores it.
        learner.learn(state.currentSegments.toList())
        state.recordTurn(score)

        if (state.remainingScore == 0) {
            recordLeg(score)
            message = "Congratulations! You finished in ${state.turnsPlayed} turns!"
            return
        }
        giveSuggestion()
    }

    /**
     * Records the leg that has just been finished.
     *
     * This is the statistics. The leg is recorded when it is won, rather
     * than being counted turn by turn, so that an undo cannot leave a
     * half-finished leg in the numbers.
     */
    private fun recordLeg(checkoutScore: Int) {
        if (state.turnHistory.isEmpty()) return
        legs.add(
            Leg(
                turns = state.turnsPlayed,
                scores = state.turnHistory.map { it.score },
                darts = state.turnHistory.map { it.segments },
                checkouts = 1,
                checkoutScore = checkoutScore,
            )
        )
    }

    /** Carries out a command the player has just confirmed. */
    private fun runCommand(command: String) {
        pendingCommand = null
        pendingScore = null
        pendingMissTarget = null
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
     * What the current plan would leave.
     *
     * The leave is recomputed from the score standing rather than taken
     * from the last suggestion, so it is right even if the score has moved
     * on since.
     *
     * When the player has named a double, it is the route onto that double
     * that is meant - the leave of the route is the shot at their double.
     */
    private fun whatWouldThatLeave(): String {
        val remaining = state.remainingScore
        if (remaining <= 0) {
            return "The leg is finished."
        }
        if (remaining == 1) {
            // The smallest double is 2, so one point can never be finished.
            return "One point left. That cannot be finished - the smallest " +
                "double is two."
        }

        val preferred = state.preferredDouble
        if (preferred != null) {
            val route = planner.routeToDouble(remaining, preferred)
            if (route != null) {
                return if (route.finishes) {
                    "${doubleSpeech(preferred)} finishes it."
                } else {
                    "That leaves ${route.leaves} for ${doubleSpeech(preferred)}."
                }
            }
        }

        val plan = planner.planFor(remaining)
        if (plan.finishes) {
            return "That wins the leg."
        }
        if (plan.leaves == 0) {
            return "That leaves nothing to finish from."
        }
        return "That leaves ${plan.leaves}."
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
        if (planner.routeToDouble(state.remainingScore, double) == null) {
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
        pendingMissTarget = null
        if (preferred != null &&
            planner.routeToDouble(remaining, preferred) == null
        ) {
            // The double the score has dropped below is not on the board any
            // more, so the coach says so, forgets the choice and gives its
            // own out.
            state.preferredDouble = null
            val note = "No shot at ${doubleSpeech(preferred)} from $remaining."
            preferred = null
            suggestion = planner.planFor(remaining).displayLines.joinToString("\n")
            message = "$note $suggestion"
            return message
        }

        val plan = if (preferred != null) {
            planner.routeToDouble(remaining, preferred)
        } else {
            null
        }
        val text = if (plan != null) {
            // The route onto the double the player asked for. The leave is
            // named because it is the point of the route: it is the shot at
            // their double.
            "$remaining. ${sentence(plan.speak())}. Leave ${plan.leaves} for " +
                "${doubleSpeech(preferred!!)}."
        } else {
            val p = planner.planFor(remaining)
            "$remaining. ${sentence(p.speak())}."
        }
        // The suggestion is what the coach says, so it has to be the message
        // as well as the thing that is returned. In the Windows build
        // _give_suggestion() speaks directly; here the caller speaks
        // `message`, so a suggestion that is only returned would leave the
        // coach repeating the previous line.
        message = text
        suggestion = planner.planFor(remaining).displayLines.joinToString("\n")
        // Remember what was aimed at, so the darts the player reports back
        // can be matched against it. That match is the lesson.
        learner.setAimed(planner.planFor(remaining).aims)
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

/**
 * Capitalises the first word of a phrase.
 *
 * The aim speech was a fragment that followed "Throw", so it was lower case:
 * "treble twenty". It now starts the line, so it reads "Treble twenty".
 */
internal fun sentence(text: String): String =
    if (text.isEmpty()) text else text.replaceFirstChar { it.uppercase() }