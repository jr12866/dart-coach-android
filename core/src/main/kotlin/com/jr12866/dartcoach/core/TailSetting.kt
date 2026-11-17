package com.jr12866.dartcoach.core

/**
 * How long the coach waits after speaking before it listens again.
 *
 * The wait is there because the phone's speaker is centimetres from the
 * microphone: the coach's own voice is still audible at the microphone
 * after the audio has stopped, and if the gate opens too early the coach
 * hears itself and cuts itself off.
 *
 * The length of that wait depends on the phone - the speaker's ringing and
 * the acoustic path to the microphone - so it is measured rather than
 * assumed. The measurement is described in `docs/TAIL.md`.
 */
object TailSetting {

    /**
     * The tail used when nothing has been measured.
     *
     * This is the value the app shipped with, measured on a Pixel 7 at the
     * oche. It is deliberately on the safe side: waiting too long costs a
     * moment of dead time, and waiting too short makes the coach cut itself
     * off.
     */
    const val DEFAULT_TAIL_SECONDS = 0.35

    /** The shortest tail that is ever used. */
    const val MIN_TAIL_SECONDS = 0.05

    /** The longest tail that is ever used. */
    const val MAX_TAIL_SECONDS = 1.0

    /**
     * The tail to use, given what was measured.
     *
     * The measurement is the longest of the samples, because the cost of
     * being too short is worse than the cost of being too long. It is then
     * given a little margin, because a measurement taken in one room is not
     * the measurement in the next one.
     *
     * A measurement of zero means the voice never reached the microphone,
     * which is the case with headphones, and the gate can open immediately.
     * A measurement of a millisecond is not zero - it is a very short
     * tail, and it still gets the floor applied to it.
     */
    fun fromMeasurement(samples: List<Double>): Double {
        val usable = samples.filter { it.isFinite() && it >= 0.0 }
        if (usable.isEmpty()) return DEFAULT_TAIL_SECONDS

        val longest = usable.max()
        if (longest <= 0.0) {
            // The voice never reached the microphone. There is nothing to
            // wait for.
            return 0.0
        }

        // A quarter more than the longest sample, so the gate is open by the
        // time the next one arrives.
        val withMargin = longest * 1.25

        return withMargin.coerceIn(MIN_TAIL_SECONDS, MAX_TAIL_SECONDS)
    }

    /**
     * The tail to use, given what was measured and whether the voice is
     * going through the phone's own speaker.
     *
     * With headphones the voice never reaches the microphone, so the wait
     * is nothing regardless of what was measured.
     */
    fun forRoute(measured: Double?, onThePhoneSpeaker: Boolean): Double {
        if (!onThePhoneSpeaker) {
            // Headphones. The voice is not in the room, so it cannot come
            // back through the microphone.
            return 0.0
        }
        if (measured == null) return DEFAULT_TAIL_SECONDS
        return measured.coerceIn(MIN_TAIL_SECONDS, MAX_TAIL_SECONDS)
    }

    /**
     * The tail as the screen shows it.
     *
     * A tail of nothing is worth saying out loud, because it is the
     * difference between a coach that answers instantly and one that
     * pauses.
     */
    fun describe(seconds: Double): String = when {
        seconds <= 0.0 -> "none - headphones"
        seconds < 0.1 -> "${(seconds * 1000).toInt()}ms"
        else -> "${hundredths(seconds)}s"
    }

    /**
     * The seconds to two decimal places, written out by hand.
     *
     * `String.format` is a JVM thing, and this file is compiled for the
     * browser as well as the phone. The number is rounded half up, the same
     * way the formatter rounded it.
     */
    private fun hundredths(seconds: Double): String {
        val value = kotlin.math.round(seconds * 100).toInt()
        val whole = value / 100
        val fraction = value % 100
        return "$whole.${fraction.toString().padStart(2, '0')}"
    }
}
