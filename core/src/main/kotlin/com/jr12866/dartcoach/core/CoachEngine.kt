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
     *
     * It can be replaced, because the planner is built from the player's
     * profile and the profile moves when the coach learns something new.
     * Replacing the planner in place - rather than building a whole new coach
     * - is what keeps the leg where it is: the score, the darts already in
     * hand and the question waiting to be answered all stay as they are. That
     * matters because the coach learns from a turn in the middle of a turn,
     * and a player who has been asked "you said one hundred, right?" must
     * still be able to answer yes.
     */
    var planner: Planner = planner ?: Planner.shared

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

    /**
     * The contingency for the standing plan: what to do if the first dart
     * drops into its single bed.
     *
     * It is kept apart from the suggestion because it is shown differently:
     * it is a quiet line under the plan rather than part of the plan, and
     * the plan itself is what the parity corpus pins.
     *
     * It is empty when there is nothing to say - when the plan has no darts
     * that can drop into a bed, or when there is no plan.
     */
    var contingency: String = ""
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
                        if (state.currentDarts.isEmpty()) {
                            // The player reported the turn as a number, so
                            // the correction has already corrected that
                            // number. It must not be overwritten with the
                            // sum of no darts at all.
                            return said
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

                    // The leave question is a question about the turn the
                    // player has just reported, so it is answered with that
                    // turn in. The read-back is re-asked afterwards, rather
                    // than thrown away, because the player has not refused
                    // the score - they have asked a question about it.
                    if (correction.command == "whats_the_leave") {
                        val leave = whatWouldThatLeave()
                        val readBack = "You said ${numberToWords(pending)}. Right?"
                        message = "$leave $readBack"
                        return message
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
     * Plans again for the score standing and says it.
     *
     * This is used when the coach has been rebuilt - because the player has
     * changed their own rates, or because they have cleared what the coach
     * had learned. The advice the coach gave was planned with the old
     * numbers, so it has to be planned again for the advice to match the
     * coach's memory.
     *
     * The leg is not touched: the score stands where it is, and the turn in
     * play keeps the darts the player has already reported.
     */
    fun replan(): String? {
        // A leg that is finished has nothing to plan for. Planning for a
        // score of zero would produce a plan for a score nobody can stand
        // on.
        if (state.remainingScore <= 0) return null

        // The darts the player has already thrown were aimed at the plan
        // that was given before the rebuild, so that part of the aim is
        // kept. Mixing them up would learn the wrong thing from the rest of
        // the visit.
        val alreadyAimed = learner.aimed.take(state.currentDarts.size)

        val said = giveSuggestion()

        if (alreadyAimed.isNotEmpty()) {
            val fresh = learner.aimed
            learner.setAimedSegments(alreadyAimed + fresh.drop(alreadyAimed.size))
        }
        return said
    }

    /**
     * Corrects the last dart in the current turn.
     *
     * The intended target may be named by the player or inferred from the
     * coach's last suggestion. The actual landing replaces the last dart,
     * and the learner is told about the miss before the turn is confirmed.
     *
     * A turn with nothing on the books is corrected differently: see
     * [correctUnreportedTurn].
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
            // There is no dart on the books to replace, so this is not the
            // same correction.
            return correctUnreportedTurn(result, actual)
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

        // The miss is learned here, and the dart's place in the turn is
        // passed with it, so that the turn does not learn from it again
        // when the turn is confirmed.
        learner.learnMiss(target, actual, state.currentDarts.lastIndex)
        pendingMissTarget = target
        return replanAfterACorrection(actual)
    }

    /**
     * A correction of a turn that has nothing on the books.
     *
     * There are two cases, and they are different corrections.
     *
     * A total is waiting to be confirmed. The player reported the turn as a
     * number, and the correction says one of the darts in that number was
     * different: the dart that was missed comes off the total and the dart
     * that landed goes on. The corrected total is read back.
     *
     * Nothing is waiting. The dart the player is describing has not been
     * reported at all, so it is the first dart of the turn, and it is
     * recorded as one. The rest of the turn is planned around it.
     *
     * This is the case a player runs into when they report their turn as a
     * number, which is how most people play, and then correct the dart they
     * missed. Refusing it - which is what the coach used to do - left the
     * player at the oche with a correction that had not been made and a
     * message that asked for a dart they had already named.
     */
    private fun correctUnreportedTurn(result: ParseResult, actual: Segment): String? {
        val pending = pendingScore
        if (pending != null) {
            // The turn was reported as a number, so the correction is to that
            // number. The dart that was missed is the one the player named,
            // or the last dart the coach aimed at.
            val target = result.intendedSegment ?: learner.aimed.lastOrNull()
            if (target == null) {
                message = "I don't know which dart you missed. Please name it and " +
                    "what you hit instead."
                return message
            }

            val corrected = pending - target.value + actual.value
            if (corrected < 0 || !isValidThreeDartScore(corrected)) {
                // The correction does not add up to a turn anybody could
                // have thrown, so the total the player gave stands.
                message = "That doesn't add up. Please say the turn again."
                return message
            }

            correctionApplied = true
            learner.learnMiss(target, actual)
            pendingMissTarget = target
            pendingScore = corrected
            message = "You hit ${actual.speech} instead of ${target.speech}. That " +
                "makes the turn ${numberToWords(corrected)}. You said " +
                "${numberToWords(corrected)}. Right?"
            return message
        }

        // Nothing is waiting, so the landing is the first dart of the turn.
        //
        // The target is the one the player named, or the first thing the
        // coach aimed at, which is what they were throwing at when they
        // missed it.
        val target = result.intendedSegment ?: learner.aimed.firstOrNull()
        state.recordDarts(listOf(actual.value), listOf(actual))
        if (target != null) {
            correctionApplied = true
            learner.learnMiss(target, actual, 0)
            pendingMissTarget = target
        }
        return replanAfterACorrection(actual)
    }

    /**
     * Plans the darts that are left after a correction, and says it.
     *
     * The turn is not confirmed here: the leg score does not move until the
     * player has confirmed the turn.
     */
    private fun replanAfterACorrection(actual: Segment): String? {
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

        // The contingency is the answer to the question the player is about
        // to ask: what if this dart comes off as a single? After a
        // correction the player has just missed one, so the question is
        // live, and the plan is for the darts left in the turn - the case
        // where a single leaves a score a hundred and eighty cannot be
        // finished from.
        val planContingency = plan.contingency
        contingency = planContingency?.line ?: ""
        message = "You hit ${actual.speech} instead. That leaves " +
            "${numberToWords(scoreLeft)} with $dartsLeft dart${if (dartsLeft == 1) "" else "s"} left. $spoken" +
            (planContingency?.let { " ${it.speech}" } ?: "")
        return message
    }

    /**
     * The target the coach aimed at for the last dart in the current turn.
     *
     * The learner keeps the aims in the order they were suggested. The last
     * reported dart is matched to the last aim, so the first two darts in a
     * three-dart turn can be corrected without naming their targets.
     *
     * The aim comes first, because the aim is what the player was aiming at.
     * The segment the player reported is what landed, so it is the wrong
     * answer for a target: taking it as the target taught the coach that a
     * segment the player had missed was a segment they hit every time, and
     * that is how the coach's memory of a leg gets quietly wrong.
     *
     * The report is only used when there is no aim for that dart, which
     * happens when the player has thrown more darts than the coach planned.
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
        return planned ?: reported
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
     * The darts the player has already thrown count. A player who has hit
     * a treble twenty and then asks what it leaves wants the answer for
     * the score they are on, not the score they were on before the dart.
     * The leave is the one for the darts left in the visit, which is what
     * the coach will plan from when the visit is over.
     *
     * When the player has named a double, it is the route onto that double
     * that is meant - the leave of the route is the shot at their double.
     */
    private fun whatWouldThatLeave(): String {
        // The darts the player has already thrown count. A player who has
        // hit a treble twenty and then asks what it leaves wants the answer
        // for the score they are on, not the score they were on before the
        // dart.
        //
        // A turn reported as a bare total has no darts in it - the total is
        // the pending score - but it is a whole visit all the same.
        val dartsInHand = state.currentDarts.size
        val bareTotal = dartsInHand == 0 && pendingScore != null
        val inHand = if (dartsInHand > 0) state.currentTurnScore() else (pendingScore ?: 0)
        val remaining = state.remainingScore - inHand
        if (remaining < 0) {
            // The dart in hand has already busted the leg.
            return "That's more than you had left. That's a bust."
        }
        if (remaining == 0) {
            return "The leg is finished."
        }
        if (remaining == 1) {
            // The smallest double is 2, so one point can never be finished.
            return "One point left. That cannot be finished - the smallest " +
                "double is two."
        }

        // The darts left in the visit. A visit that is over leaves the next
        // one with three darts.
        val visitOver = bareTotal || dartsInHand >= MAX_DARTS_PER_TURN
        val dartsLeft = if (visitOver) {
            MAX_DARTS_PER_TURN
        } else {
            MAX_DARTS_PER_TURN - dartsInHand
        }

        val where = whereThePlayerIs(remaining, dartsLeft, inHand, visitOver)

        val preferred = state.preferredDouble
        if (preferred != null) {
            val route = planner.routeToDouble(remaining, preferred, dartsLeft)
            if (route != null) {
                return where + if (route.finishes) {
                    "${doubleSpeech(preferred)} finishes it."
                } else {
                    "That leaves ${route.leaves} for ${doubleSpeech(preferred)}."
                }
            }
        }

        val plan = planner.planFor(remaining, dartsLeft)
        return where + when {
            plan.finishes -> "That wins the leg."
            plan.leaves == 0 -> "That leaves nothing to finish from."
            else -> "That leaves ${plan.leaves}."
        }
    }

    /**
     * Says where the player is, when they have darts in hand.
     *
     * "What does that leave" can be asked about the score or about the
     * plan, so the answer says both: the score they are on, and the leave
     * of the plan from there. Without a dart in hand there is nothing to
     * say, and the answer is left as it was.
     */
    private fun whereThePlayerIs(
        remaining: Int,
        dartsLeft: Int,
        inHand: Int,
        visitOver: Boolean,
    ): String {
        if (inHand == 0) return ""
        if (visitOver) {
            // The visit is over. The next one is three darts.
            return "You're on ${numberToWords(remaining)}. "
        }
        return "You're on ${numberToWords(remaining)} with $dartsLeft " +
            "dart${if (dartsLeft == 1) "" else "s"} left. "
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
            val ordinary = planner.planFor(remaining)
            suggestion = ordinary.displayLines.joinToString("\n")
            contingency = ordinary.contingency?.line ?: ""
            message = "$note $suggestion"
            return message
        }

        val plan = if (preferred != null) {
            planner.routeToDouble(remaining, preferred)
        } else {
            null
        }
        val ordinary = planner.planFor(remaining)
        val text = if (plan != null) {
            // The route onto the double the player asked for. The leave is
            // named because it is the point of the route: it is the shot at
            // their double.
            "$remaining. ${sentence(plan.speak())}. Leave ${plan.leaves} for " +
                "${doubleSpeech(preferred!!)}."
        } else {
            "$remaining. ${sentence(ordinary.speak())}."
        }
        // The suggestion is what the coach says, so it has to be the message
        // as well as the thing that is returned. In the Windows build
        // _give_suggestion() speaks directly; here the caller speaks
        // `message`, so a suggestion that is only returned would leave the
        // coach repeating the previous line.
        message = text
        suggestion = ordinary.displayLines.joinToString("\n")
        // The contingency is on the screen but not in the spoken line.
        //
        // The spoken line is what the player waits through before the
        // first dart, and this is the start of a turn: the plan is for
        // three darts and it is robust - if the first dart drops, the
        // second and third are still worth throwing. The screen carries
        // the detail, and the coach says it when it matters, which is when
        // it re-plans the darts left in a turn.
        contingency = ordinary.contingency?.line ?: ""
        // Remember what was aimed at, so the darts the player reports back
        // can be matched against it. That match is the lesson.
        learner.setAimed(ordinary.aims)
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