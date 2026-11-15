package com.jr12866.dartcoach.core

/**
 * What the parser made of one phrase.
 *
 * Ported from `ParseResult` in `src/game/parser.py`.
 */
data class ParseResult(
    /**
     * "turn_score", "qualified_darts", "bust", "new_game", "undo",
     * "whats_my_score", "continue", "prefer_double", "report_miss", or
     * "invalid".
     */
    val command: String,
    /** The turn score, when the command carries one. */
    val score: Int? = null,
    /** The individual darts, when the player reported them one at a time. */
    val darts: List<Int>? = null,
    /**
     * The same darts as segments, when the player said which bed and which
     * ring each one was.
     *
     * The values alone cannot say whether a sixteen was a single sixteen or
     * a double eight, and the learner needs to know. This is null when the
     * player reported a bare total instead of naming the darts.
     */
    val dartSegments: List<Segment>? = null,
    /** The double the player asked to finish on. */
    val double: Int? = null,
    /** The target the player says they meant to hit, when correcting a miss. */
    val intendedSegment: Segment? = null,
    /** The segment that actually landed, when correcting a miss. */
    val actualSegment: Segment? = null,
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

    /** The multipliers a dart qualifier carries. */
    private val QUALIFIERS = mapOf(
        "single" to 1, "double" to 2, "triple" to 3, "treble" to 3,
        // Whisper writes "treble" as "trouble" often enough that treating it
        // as a treble is safer than silently hearing a bare number.
        "trouble" to 3,
    )

    /** The number words, including zero and the tens. */
    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    /**
     * The words that turn a phrase into a request for a particular double.
     * Only an explicit preference counts: "I prefer double 20" and "I'd
     * rather have double eighteen" ask the coach to finish on that double,
     * while a bare "double 20" is a dart report and "I want double eight" is
     * the player talking to themselves. Every one of these is a word the
     * score gate already refuses, so a score report can never be read as a
     * preference.
     */
    private val PREFERENCE_WORDS = setOf(
        "prefer", "prefers", "preferred", "preference", "rather",
    )

    /** A double is one of the twenty beds or the bull. */
    private val DOUBLE_WORD_NUMBERS = NUMBER_WORDS
        .filter { it.value in 1..20 }
        .keys
        .sortedByDescending { it.length }

    private val HUNDREDS_MULTIPLIER = mapOf("hundred" to 100)
    private val THOUSANDS_MULTIPLIER = mapOf("thousand" to 1000)

    /**
     * Words that hold a number together without being a number themselves.
     * "and" is whisper writing the connective in "one hundred and thirty
     * nine"; the rest are the beds a dart can land in.
     */
    private val NUMBER_WORDS_ALL: Set<String> = buildSet {
        addAll(NUMBER_WORDS.keys)
        addAll(HUNDREDS_MULTIPLIER.keys)
        addAll(THOUSANDS_MULTIPLIER.keys)
        addAll(QUALIFIERS.keys)
        addAll(
            listOf(
                "and", "bull", "outer", "inner", "green", "belly", "score",
                "points",
            )
        )
    }

    /**
     * A score is the whole utterance - "one thirty nine", "125", "triple 20
     * double 16". That is what makes it safe to accept without the wake
     * word. Conversation is the same words in a different shape, and whisper
     * obligingly hears the number in it: "it was one of those days" scores 1,
     * "leave me twenty" scores 20, "is it forty or fourteen" scores 54.
     * None of these words belongs in a score report, so one of them ends the
     * utterance.
     *
     * These are the words that give a sentence away. They are grouped by why
     * they are here, because the grouping is the reasoning.
     */
    private val NON_SCORE_WORDS: Set<String> = buildSet {
        // who is talking
        addAll(
            (
                "i im me my mine you your yours he him his she her it its we " +
                    "us our they them their this that these those there here " +
                    "who what which whom someone somebody something anyone " +
                    "anybody anything"
                ).split(" ")
        )
        // what they are doing about it
        addAll(
            (
                "want wanted need leave left give gave get got take took make " +
                    "made prefer prefers preferred preference throw threw " +
                    "thrown hit hits miss missed misses say said think thinks " +
                    "thought know knew mean means hope hoped wish wish go " +
                    "going gonna check checked try trying tell told ask asked " +
                    "answer answered show shows finish finished"
                ).split(" ")
        )
        // being and having
        addAll(
            (
                "is am are was were be been being do does did have has had " +
                    "will would can could should shall may might must"
                ).split(" ")
        )
        addAll(
            (
                "dont doesnt cant couldnt shouldnt wasnt werent isnt arent " +
                    "didnt hasnt havent wont imve ive id"
                ).split(" ")
        )
        // joining things up. Deliberately not "so", "then" or "now": a player
        // opens a report with those ("so, one forty then") and a sentence
        // containing them also contains a pronoun or a verb, which is what
        // actually gives it away.
        addAll(
            (
                "of in on at to for with without about than but if when while " +
                    "as into from up down out off"
                ).split(" ")
        )
        // how much of it. "just", "only", "still" and "again" are left out:
        // they modify a score ("only 140", "140 again") and a sentence using
        // them also uses a pronoun or a verb.
        addAll(
            (
                "too very really quite rather bit little more most some any " +
                    "many much few all both each every another other such " +
                    "always never once first second third next last"
                ).split(" ")
        )
        // talking to the room. Not "dart" or "darts": "darts coach" is how
        // the app gets addressed, and a score with "dart" in it is caught by
        // the words around it. Not "coach" either, which is the wake word
        // and counts as filler.
        addAll(
            (
                "oh wow damn crap argh hmm um listen wait hold minute minutes " +
                    "seconds day days time times thing things way lot rest " +
                    "board leg legs set sets checkout"
                ).split(" ")
        )
    }

    /**
     * A score may carry a couple of other words - "bus 40", "a hundred",
     * "triple 20 125" - but past this it is a sentence. "leave me twenty" is
     * two words over.
     */
    private const val MAX_OTHER_WORDS = 2

    /** Parses one phrase in the context of the current game. */
    fun parse(text: String, state: GameState): ParseResult {
        var t = text.lowercase().trim()
        // whisper writes "eighty-nine" and "one hundred and thirty-nine"; the
        // hyphen is just a separator between number words.
        t = t.replace("-", " ").replace('\u2019', '\'')
        // whisper also punctuates a spoken compound as a decimal: "one
        // eighty" comes back as "1.80". No darts number has a decimal point
        // in it, so a point between digits is another separator. Splitting it
        // lets the merge in extractBareNumbers recompose 1 and 80 into 180.
        t = t.replace(DECIMAL_POINT, " ")

        if (isNewGame(t)) return command("new_game")
        if (isUndo(t)) return command("undo")
        if (isWhatsMyScore(t)) return command("whats_my_score")
        if (isContinue(t)) return command("continue")
        if (isBust(t)) return command("bust")

        if (isWhatsTheLeave(t)) return command("whats_the_leave")

        // A miss is checked before the score gate because these phrases are
        // deliberately not score reports: "I hit 8 instead" would otherwise
        // be heard as a turn worth eight.
        reportMiss(t)?.let { return it }

        // The player naming the double they want to finish on. This is asked
        // for before the score gate, because a phrase with a preference word
        // in it is never a score report, and "double 20" on its own is a dart
        // rather than a request for a different finish.
        preferredDouble(t)?.let { return ParseResult(command = "prefer_double", double = it) }

        // Last gate before a number becomes a score. Everything above this is
        // a named command, so the player meant it; from here the parser is
        // only looking for numbers, and a number is not a score on its own.
        if (!isScoreUtterance(t)) {
            // Deliberately not flagged as refused: this was never a score
            // report, so there is nothing for the coach to correct.
            return ParseResult(command = "invalid")
        }

        val qualifiedSegments = extractQualifiedSegments(t)
        val qualifiedDarts = qualifiedSegments?.map { it.value }
        val bareNumbers = extractBareNumbers(t)

        // From here the utterance has passed the score gate and a number was
        // found, so the player was reporting a turn. Any refusal from here on
        // is flagged: silence would leave them believing the score went in.
        if (qualifiedDarts != null && bareNumbers != null) {
            val total = bareNumbers.sum()
            if (!isValidThreeDartScore(total)) {
                return ParseResult(command = "invalid", refused = true)
            }
            return ParseResult(command = "turn_score", score = total)
        }

        if (qualifiedDarts != null) {
            if (qualifiedDarts.size > MAX_DARTS_PER_TURN) {
                return ParseResult(command = "invalid", refused = true)
            }
            if (!isValidThreeDartScore(qualifiedDarts.sum())) {
                return ParseResult(command = "invalid", refused = true)
            }
            return ParseResult(
                command = "qualified_darts",
                darts = qualifiedDarts,
                dartSegments = qualifiedSegments,
            )
        }

        if (bareNumbers != null) {
            if (bareNumbers.size > MAX_DARTS_PER_TURN) {
                // More numbers than darts is not a turn anybody threw. Either
                // the player has counted their darts and their total in one
                // breath ("20 20 20 is 60"), or the room has been counting
                // out loud. Adding them up would move the game to a total
                // off a throw that never happened, so this is refused the
                // same way too many qualified darts are.
                return ParseResult(command = "invalid", refused = true)
            }
            return handleScore(bareNumbers.sum(), state)
        }

        // Score-shaped but no number in it, such as a cut-off "double".
        // Nothing was refused, so the coach waits for the rest rather than
        // correcting it.
        return ParseResult(command = "invalid")
    }

    /**
     * A named command the player said out loud. The ones that throw work
     * away are flagged so the coach asks for a second word first.
     */
    private fun command(name: String): ParseResult =
        ParseResult(command = name, needsConfirmation = name in DESTRUCTIVE_COMMANDS)

    /**
     * True when the words are a score report rather than a sentence that
     * happens to contain a number.
     *
     * The microphone heard these correctly - "it was one of those days"
     * really was said, and 1 really is a legal turn - so this belongs with
     * the other score rules and not in the VAD. A score is the whole
     * utterance; a number with conversation around it is the player talking
     * to themselves, and the coach has no business interrupting a leg to
     * confirm it.
     */
    private fun isScoreUtterance(text: String): Boolean {
        var other = 0
        for (word in WORD_PATTERN.findAll(text).map { it.value }) {
            // "that's" and "it's" are conversation whichever way they are
            // written.
            if (word in NON_SCORE_WORDS || word.substringBefore('\'') in NON_SCORE_WORDS) {
                return false
            }
            if (!word.all { it.isDigit() } && word !in NUMBER_WORDS_ALL) {
                other += 1
                if (other > MAX_OTHER_WORDS) return false
            }
        }
        return true
    }

    private fun isNewGame(text: String): Boolean =
        NEW_GAME_PATTERN.containsMatchIn(text)

    private fun isUndo(text: String): Boolean =
        UNDO_PATTERN.containsMatchIn(text)

    private fun isWhatsMyScore(text: String): Boolean =
        WHATS_MY_SCORE_PATTERN.containsMatchIn(text)

    private fun isContinue(text: String): Boolean =
        CONTINUE_PATTERN.containsMatchIn(text)

    /**
     * Is the player asking what the plan would leave?
     *
     * The coach no longer says the leave with every suggestion, because the
     * screen shows it and saying it cost half a second. But a player
     * looking at the board rather than the phone can ask.
     *
     * The patterns are deliberately specific. "what would that leave" and
     * "what does that leave" are the question; a bare "leave" is not,
     * because "leave me twenty" is a player talking to themselves and the
     * Windows build already refuses it as a score report.
     */
    private fun isWhatsTheLeave(text: String): Boolean =
        WHATS_THE_LEAVE_PATTERN.containsMatchIn(text)

    /**
     * "bust" is a documented voice command. Until now the only way to get a
     * bust was to say a number above the remaining score, so the word on its
     * own came back as invalid. Whisper also hears the coach say it as
     * "Bus.", so a lone "bus" with no numbers in it counts too.
     */
    private fun isBust(text: String): Boolean {
        if (BUST_PATTERN.containsMatchIn(text)) return true
        return BUS_FULLMATCH.matches(text.trim())
    }

    /**
     * The player's report of a missed dart.
     *
     * The common spoken forms are supported: "I missed double 16 and hit
     * single 8", "I hit single 8 instead", and "I hit 8". The intended
     * target may be omitted; the coach then uses the target it aimed at.
     *
     * A phrase that is shaped like a correction but does not say what
     * landed comes back as [ParseResult.command] "report_miss" with no
     * actual segment. That is not the same as not being a correction: the
     * player has said something about a miss, and the coach has to ask them
     * to say the landing again rather than standing silent. The reason is
     * that Whisper was heard to write the spoken "8" as "eat", and a
     * player who gets no reply has no way to know the coach did not
     * understand.
     */
    private fun reportMiss(text: String): ParseResult? {
        if (!MISS_PATTERN.containsMatchIn(text)) return null

        val segments = extractQualifiedSegments(text)

        // The landing and the intended target are told apart by the verb in
        // front of them, not by what they are.
        //
        // "I missed double 16 and hit single 8" names two darts: the one
        // after "missed" is the target that was missed and the one after
        // "hit" is what landed. "I hit single 8 instead" names one dart, and
        // it follows "hit", so it is the landing. "I missed double 16" names
        // one dart, and it follows "missed", so it is the target - there is
        // no landing in the sentence at all.
        //
        // The last case is the one that has to be got right. Reading it the
        // other way round would tell the player they hit the very double
        // they said they missed, and would correct the turn to a dart that
        // never landed.
        val missed = dartAfter(text, MISSED_VERB_PATTERN)
        val hit = dartAfter(text, HIT_VERB_PATTERN)

        val actual: Segment?
        val intended: Segment?
        when {
            hit != null -> {
                // The dart that landed is the one after "hit". The one after
                // "missed" is the target.
                actual = hit
                intended = missed
            }
            missed != null -> {
                // Only the target was named. There is no landing in the
                // sentence, so there is nothing to correct to.
                actual = null
                intended = missed
            }
            segments != null && segments.size >= 2 -> {
                // Two darts and no verb to tell them apart. The first is
                // the target and the second is what landed, which is the
                // order the phrase is normally spoken in.
                intended = segments.first()
                actual = segments.last()
            }
            segments != null && segments.size == 1 -> {
                actual = segments.first()
                intended = null
            }
            else -> {
                val value = extractMissValue(text)
                actual = value?.let { DartThrow.fromValue(it)?.segment }
                // A single bare number in a miss report is the landing, not
                // the target: "I hit 8 instead" is what landed, and the
                // target is left to the coach's own last suggestion.
                intended = null
            }
        }

        if (actual == null) {
            // Correction-shaped, but no landing in it. The engine asks for
            // the landing rather than guessing.
            return ParseResult(
                command = "report_miss",
                intendedSegment = intended,
            )
        }

        return ParseResult(
            command = "report_miss",
            intendedSegment = intended,
            actualSegment = actual,
        )
    }

    /**
     * The dart that follows a verb in the phrase, if any.
     *
     * This is what tells "I missed double 16" from "I hit single 8". Both
     * name one dart and both are corrections, but the first is the target
     * that was missed and the second is the landing.
     *
     * The bull is found here too. It is the one part of the board with a
     * name rather than a number, so it has to be read from the words, and
     * it has to be read from the words *after the verb*: "I missed the bull
     * and hit single 8" is the bull missed and the eight landed, and the
     * other way round would be nonsense.
     */
    private fun dartAfter(text: String, verb: Regex): Segment? {
        val match = verb.find(text) ?: return null
        val after = text.substring(match.range.last + 1)
        return nearestDart(after)
    }

    /**
     * The first dart in a phrase, whether it is named by a qualifier, by
     * the name of the bull, or by its value.
     *
     * "First" matters. "I hit the bull and single 8 instead" names two
     * darts, and the one nearest the verb is the landing; taking the
     * qualified one because it is easier to parse would read the eight as
     * the landing and leave the bull unmentioned.
     */
    private fun nearestDart(text: String): Segment? {
        val qualified = QUALIFIED_DART_PATTERN.find(text)
        val bull = BULL_PATTERN.find(text)
        val value = extractBareNumbers(
            BULL_PHRASE_PATTERN.replace(text, " ")
        )?.singleOrNull()

        var bestStart = Int.MAX_VALUE
        var best: Segment? = null

        if (qualified != null) {
            bestStart = qualified.range.first
            best = extractQualifiedSegments(text)?.firstOrNull()
        }

        if (bull != null && bull.range.first < bestStart) {
            bestStart = bull.range.first
            best = extractBullSegment(text)
        }

        if (value != null && bestStart == Int.MAX_VALUE) {
            best = DartThrow.fromValue(value)?.segment
        }

        return best
    }

    /**
     * The bull, when the phrase names it.
     *
     * The bull is the one part of the board that has a name of its own
     * rather than a number, so it has to be read from the words. "the bull"
     * is the double bull and "outer bull" is the twenty-five; a bare "bull"
     * is the double bull, because that is the one players mean when they
     * say "the bull".
     */
    private fun extractBullSegment(text: String): Segment? {
        if (!BULL_PATTERN.containsMatchIn(text)) return null
        val outer = OUTER_BULL_PATTERN.containsMatchIn(text)
        return if (outer) Segment(25, 1) else Segment(25, 2)
    }

    /**
     * The single dart value in a miss report.
     *
     * This deliberately ignores qualified dart phrases, which have already
     * been handled by [extractQualifiedSegments], and the words naming the
     * bull, which are not numbers at all.
     */
    private fun extractMissValue(text: String): Int? {
        val withoutQualified = QUALIFIED_DART_PATTERN.replace(text, " ")
        val withoutBull = BULL_PHRASE_PATTERN.replace(withoutQualified, " ")
        return extractBareNumbers(withoutBull)?.singleOrNull()
    }

    /**
     * The double the player named, if this phrase is them naming one.
     *
     * "I prefer double 20" and "I'd rather have double eighteen" ask the
     * coach to finish on that double. The double has to be named as one: a
     * bare "double 20" is a dart report, and "I prefer treble 20" is not a
     * request for a double at all.
     */
    private fun preferredDouble(text: String): Int? {
        val words = LETTER_PATTERN.findAll(text).map { it.value }.toList()
        if (words.none { it in PREFERENCE_WORDS }) return null

        val match = PREFERRED_DOUBLE_PATTERN.find(text)
        if (match != null) {
            val target = match.groupValues[1]
            if (target == "bull") return Darts.BULL_DOUBLE
            val number = target.toIntOrNull() ?: NUMBER_WORDS[target] ?: return null
            if (number == 25) {
                // Twenty-five is the outer bull, so it is taken to mean the
                // double bull, the same way the planner reads it.
                return Darts.BULL_DOUBLE
            }
            if (number in 1..20) return number
            return null
        }

        if (BULL_PATTERN.containsMatchIn(text)) return Darts.BULL_DOUBLE
        return null
    }

    /** "double 18" and "double eighteen" are the same dart. */
    private fun extractQualifiedDarts(text: String): List<Int>? =
        extractQualifiedSegments(text)?.map { it.value }

    /**
     * The qualified darts in a phrase, as segments.
     *
     * Each one keeps the bed it was on and the ring it was in, which is what
     * the hit-rate learner counts. The values come out of the segments.
     */
    private fun extractQualifiedSegments(text: String): List<Segment>? {
        val matches = QUALIFIED_DART_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) return null

        val segments = mutableListOf<Segment>()
        for (m in matches) {
            val qualifier = m.groupValues[1].lowercase()
            val value = m.groupValues[2].lowercase()
            val multiplier = QUALIFIERS[qualifier] ?: continue
            val number = value.toIntOrNull() ?: NUMBER_WORDS[value] ?: continue
            segments.add(Segment(number, multiplier))
        }
        return segments
    }

    /**
     * The bare numbers in the phrase, with the "one eighty" merge.
     *
     * The merge is the subtle part. "one eighty" is one hundred and eighty,
     * not 1 + 80. A small number in front of a big one is the hundreds, and
     * whisper writes the big one as a digit or as a word, so both have to
     * merge.
     */
    private fun extractBareNumbers(text: String): List<Int>? {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

        // The indices of the words that belong to a qualified dart, so they
        // are not also counted as bare numbers.
        val qualifiedIndices = mutableSetOf<Int>()
        var i = 0
        while (i < words.size) {
            val wordClean = words[i].trimEnd('.', ',', '!', '?', ';')
            if (wordClean in QUALIFIERS && i + 1 < words.size) {
                val nextClean = words[i + 1].trimEnd('.', ',', '!', '?', ';')
                if (nextClean.all { it.isDigit() } && nextClean.isNotEmpty() ||
                    nextClean in NUMBER_WORDS
                ) {
                    qualifiedIndices.add(i)
                    qualifiedIndices.add(i + 1)
                    i += 2
                    continue
                }
            }
            i += 1
        }

        val combined = mutableListOf<Int>()

        i = 0
        while (i < words.size) {
            if (i in qualifiedIndices) {
                i += 1
                continue
            }

            val word = words[i]
            val wordClean = word.trimEnd('.', ',', '!', '?', ';')

            var currentValue: Int? = null
            if (wordClean.isNotEmpty() && wordClean.all { it.isDigit() }) {
                currentValue = wordClean.toInt()
            } else if (wordClean in NUMBER_WORDS) {
                currentValue = NUMBER_WORDS[wordClean]
            } else if (wordClean in HUNDREDS_MULTIPLIER) {
                if (combined.isNotEmpty()) {
                    combined[combined.size - 1] = combined[combined.size - 1] * HUNDREDS_MULTIPLIER.getValue(wordClean)
                } else {
                    combined.add(HUNDREDS_MULTIPLIER.getValue(wordClean))
                }
                i += 1
                continue
            } else if (wordClean in THOUSANDS_MULTIPLIER) {
                if (combined.isNotEmpty()) {
                    combined[combined.size - 1] = combined[combined.size - 1] * THOUSANDS_MULTIPLIER.getValue(wordClean)
                } else {
                    combined.add(THOUSANDS_MULTIPLIER.getValue(wordClean))
                }
                i += 1
                continue
            }

            if (currentValue != null) {
                if (i + 1 < words.size) {
                    val nextClean = words[i + 1].trimEnd('.', ',', '!', '?', ';')
                    val nextValue: Int? = when {
                        nextClean.isNotEmpty() && nextClean.all { it.isDigit() } -> nextClean.toInt()
                        nextClean in NUMBER_WORDS -> NUMBER_WORDS[nextClean]
                        else -> null
                    }
                    if (nextValue != null && currentValue < 10 && nextValue >= 20) {
                        if (currentValue == 1 && nextValue in 101..199) {
                            // "one hundred and sixty one" reaches whisper as
                            // '1. 161.': it writes the word "one" as a digit and
                            // then folds the hundred into 161 as well. The
                            // digit in front is the same hundred spoken
                            // twice, so the number to keep is the one that
                            // already has it. Adding them made 261 and the
                            // turn was refused. Only for one: "two hundred
                            // and sixty one" is above what three darts can
                            // score and has to stay refused rather than
                            // become 161.
                            combined.add(nextValue)
                        } else if (nextValue % 100 == 0) {
                            // "one 100" is whisper writing "one hundred" with
                            // the hundred itself as a digit, so the digit is
                            // the hundreds and the word in front of it counts
                            // them: 100, not 1 + 100 and not 1*100 + 100 =
                            // 200.
                            combined.add(currentValue * nextValue)
                        } else {
                            combined.add(currentValue * 100 + nextValue)
                        }
                        i += 2
                        continue
                    }
                }
                combined.add(currentValue)
            }

            i += 1
        }

        return combined.ifEmpty { null }
    }

    /**
     * A turn score, judged against the score standing.
     *
     * Only ever reached with a score-shaped utterance and a number in it, so
     * every answer here is a refusal the player needs to hear.
     */
    private fun handleScore(score: Int, state: GameState): ParseResult {
        if (score == 0) {
            return ParseResult(command = "invalid", refused = true)
        }

        if (score > state.remainingScore) {
            return ParseResult(command = "bust")
        }

        if (score == state.remainingScore) {
            return if (Darts.canFinish(score)) {
                ParseResult(command = "full_score", score = score)
            } else {
                // A finish no dart can land on: leaving 1, a bogey, or a
                // total above 170 that reaches zero without a double out.
                ParseResult(command = "invalid", refused = true)
            }
        }

        if (!isValidThreeDartScore(score)) {
            // Whisper can hear a total no three darts can throw - a mangled
            // "one hundred and seventy-nine", say. Recording it would move
            // the game to a score the player could not be standing on.
            return ParseResult(command = "invalid", refused = true)
        }

        return ParseResult(command = "turn_score", score = score)
    }

    // -- the answers -------------------------------------------------------

    /**
     * True when the whole phrase is the player agreeing with what was heard.
     *
     * These have to be the whole phrase, not one word inside it: Whisper
     * really does write "no" inside "one hundred" and "I know", and a "no"
     * that is only part of a sentence would throw away a score or a command
     * that was waiting to be confirmed. Matching a bare word anywhere also
     * means a score cannot be an answer, which is what keeps the two paths
     * apart.
     *
     * "all right" and "alright" are left out on purpose. A player says them
     * to move on, not to agree with a number, and the two ways to get this
     * wrong are not equal: a missed "yes" leaves the question open to be
     * answered again, while a "yes" nobody meant starts a new game or erases
     * a turn.
     */
    fun isConfirmation(text: String): Boolean = matchesOnly(text, CONFIRM_WORDS, CONFIRM_PHRASES)

    /** True when the whole phrase is the player rejecting what was heard. */
    fun isCancellation(text: String): Boolean = matchesOnly(text, CANCEL_WORDS, CANCEL_PHRASES)

    /** A player who hesitates is still answering: "oh yes" and "well, no". */
    private val ANSWER_FILLERS = setOf(
        "oh", "ah", "ha", "uh", "um", "er", "well", "please", "so",
    )

    private val CONFIRM_WORDS = setOf(
        "yes", "yeah", "yep", "yup", "yea", "ok", "okay", "sure", "correct",
        "confirm", "confirmed",
    )

    private val CONFIRM_PHRASES = setOf(
        "that's right", "thats right", "that is right",
        "that's correct", "thats correct", "that is correct",
        "you got it",
        // Whisper writes a player who agrees with the number twice over, as
        // a bare yes and then the sentence that explains it.
        "yes that's right", "yes that is right", "yes that's correct",
        "yeah that's right", "yeah that is right", "yeah that's correct",
    )

    private val CANCEL_WORDS = setOf(
        "no", "nope", "nah", "wrong", "incorrect", "cancel",
    )

    private val CANCEL_PHRASES = setOf(
        "not right", "not correct", "that's wrong", "thats wrong", "that is wrong",
        "scratch that", "forget it", "never mind", "nevermind", "no thanks",
    )

    /** Lowercase words of a possible yes/no answer, hesitations removed. */
    private fun answerTokens(text: String): List<String> =
        ANSWER_WORD_PATTERN.findAll(text.lowercase().replace('\u2019', '\''))
            .map { it.value }
            .filter { it !in ANSWER_FILLERS }
            .toList()

    /**
     * True when the answer words and phrases cover the tokens and nothing
     * else.
     */
    private fun matchesOnly(
        text: String,
        words: Set<String>,
        phrases: Set<String>,
    ): Boolean {
        val tokens = answerTokens(text)
        if (tokens.isEmpty()) return false

        var index = 0
        while (index < tokens.size) {
            var matched = false
            for (size in minOf(MAX_ANSWER_WORDS, tokens.size - index) downTo 1) {
                val gram = tokens.subList(index, index + size).joinToString(" ")
                if (gram in phrases || (size == 1 && gram in words)) {
                    index += size
                    matched = true
                    break
                }
            }
            if (!matched) return false
        }
        return true
    }
    // -- the patterns ----------------------------------------------------------

    private val WORD_PATTERN = Regex("[a-z0-9']+")

    private val LETTER_PATTERN = Regex("[a-z']+")

    private val ANSWER_WORD_PATTERN = Regex("[a-z']+")

    /** A point between digits is a separator, not a decimal point. */
    private val DECIMAL_POINT = Regex("(?<=\\d)\\.(?=\\d)")

    private val NEW_GAME_PATTERN = Regex(
        "\\bnew game\\b|\\breset\\b|\\brestart\\b|\\bstart over\\b"
    )

    private val UNDO_PATTERN = Regex(
        "\\bundo\\b|\\btake back\\b|\\bgo back\\b|\\bcorrect\\b"
    )

    private val WHATS_MY_SCORE_PATTERN = Regex(
        "\\bwhat('?s| is) my score\\b|\\bmy score\\b|\\bhow much\\b|" +
            "\\bwhat do i have\\b|\\bremaining\\b"
    )

    private val CONTINUE_PATTERN = Regex("\\bcontinue\\b|\\bkeep (going|playing)\\b")

    /**
     * The question "what would that leave".
     *
     * It has to be the whole idea, not just the word "leave": "leave me
     * twenty" is a player talking to themselves, and the score gate
     * already refuses it.
     */
    private val WHATS_THE_LEAVE_PATTERN = Regex(
        "\\bwhat\\s+(?:would|does|do|did|will|should)\\s+(?:that|this|it|the)\\s+" +
            "leaves?\\b|\\bwhat(?:'s| is)\\s+the\\s+leaves?\\b|" +
            "\\bwhat\\s+leaves?\\b|\\bwhat\\s+(?:would|does)\\s+leaves?\\b",
    )

    private val BUST_PATTERN = Regex("\\bbust(ed)?\\b")

    private val BUS_FULLMATCH = Regex("bus\\.?")

    /**
     * A phrase that reports a missed dart.
     *
     * "Missed" on its own is enough. "I hit 8 instead" is also enough,
     * because "instead" makes it a correction rather than an ordinary dart
     * report.
     *
     * A bull named with "instead" counts too. The bull is the one part of
     * the board with a name rather than a number, so without this a player
     * who missed T20 and hit the bull would say "I hit the bull instead" and
     * be told nothing at all.
     */
    private val MISS_PATTERN = Regex(
        "\\bmiss(?:ed)?\\b|\\binstead\\b|\\bgot\\s+\\d+\\b|" +
            "\\bhit\\s+(?:the\\s+)?(?:outer\\s+|double\\s+)?bull\\b",
    )

    private val BULL_PATTERN = Regex("\\bbull\\b")

    /**
     * The outer bull, named as such.
     *
     * A bare "bull" is the double bull, which is the one players mean. The
     * outer bull has to be said as "outer bull" or "25".
     */
    private val OUTER_BULL_PATTERN = Regex("\\bouter\\s+bull\\b|\\bgreen\\s+bull\\b")

    /**
     * The verb that names the target that was missed.
     *
     * The dart after this verb is what the player was aiming at and did not
     * get, so it is the target of the correction and not the landing.
     */
    private val MISSED_VERB_PATTERN = Regex("\\bmiss(?:ed)?\\b")

    /**
     * The verb that names what actually landed.
     *
     * The dart after this verb is the landing, so it is what the last dart
     * in the turn is replaced with.
     */
    private val HIT_VERB_PATTERN = Regex("\\b(?:hit|landed|scored)\\b")

    /**
     * The words that name the bull, for taking the numbers out of a phrase.
     *
     * The bull is not a number, so it has to be taken out before the bare
     * numbers are read, or "I hit the bull and 8" would be read as one
     * number.
     */
    private val BULL_PHRASE_PATTERN = Regex(
        "\\b(?:the\\s+)?(?:outer\\s+|double\\s+)?bull\\b|\\bgreen\\s+bull\\b",
    )

    private val PREFERRED_DOUBLE_PATTERN = Regex(
        "\\bdouble\\s+(\\d{1,2}|${DOUBLE_WORD_NUMBERS.joinToString("|")}|bull)\\b"
    )

    private val QUALIFIED_DART_PATTERN = Regex(
        "\\b(single|double|triple|treble|trouble)\\s+(\\d+|" +
            NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|") +
            ")\\b"
    )

    /** Longest of the multi-word answers, so the longest match is tried first. */
    private val MAX_ANSWER_WORDS =
        (CONFIRM_PHRASES + CANCEL_PHRASES).maxOf { it.split(" ").size }
}
