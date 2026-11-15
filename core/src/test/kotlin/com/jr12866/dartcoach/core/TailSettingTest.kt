package com.jr12866.dartcoach.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The echo tail.
 *
 * The tail is how long the coach waits after speaking before it listens
 * again. Getting it wrong means the coach hears itself and cuts itself off,
 * so the tests are mostly about which way the errors go.
 */
class TailSettingTest {

    /** With nothing measured, the safe default is used. */
    @Test
    fun withNothingMeasuredTheDefaultIsUsed() {
        assertEquals(
            TailSetting.DEFAULT_TAIL_SECONDS,
            TailSetting.fromMeasurement(emptyList()),
        )
    }

    /** A measurement of nothing means headphones: no wait at all. */
    @Test
    fun aMeasurementOfNothingMeansNoWait() {
        assertEquals(0.0, TailSetting.fromMeasurement(listOf(0.0, 0.0)))
    }

    /** The longest sample is the one that counts. */
    @Test
    fun theLongestSampleCounts() {
        val tail = TailSetting.fromMeasurement(listOf(0.2, 0.5, 0.3))
        assertTrue(tail >= 0.5, "the tail should cover the longest sample")
    }

    /** The measurement gets a margin, because a room is not a room. */
    @Test
    fun theMeasurementGetsAMargin() {
        val tail = TailSetting.fromMeasurement(listOf(0.4))
        assertEquals(0.5, tail, 1e-9)
    }

    /** The tail is never shorter than the floor. */
    @Test
    fun theTailIsNeverShorterThanTheFloor() {
        val tail = TailSetting.fromMeasurement(listOf(0.001))
        assertTrue(tail >= TailSetting.MIN_TAIL_SECONDS)
    }

    /** The tail is never longer than the ceiling. */
    @Test
    fun theTailIsNeverLongerThanTheCeiling() {
        val tail = TailSetting.fromMeasurement(listOf(5.0))
        assertTrue(tail <= TailSetting.MAX_TAIL_SECONDS)
    }

    /** Nonsense measurements are ignored, not obeyed. */
    @Test
    fun nonsenseMeasurementsAreIgnored() {
        val tail = TailSetting.fromMeasurement(listOf(Double.NaN, -1.0, 0.3))
        assertEquals(0.375, tail, 1e-9)
    }

    /** Nonsense and nothing else falls back to the default. */
    @Test
    fun onlyNonsenseFallsBackToTheDefault() {
        assertEquals(
            TailSetting.DEFAULT_TAIL_SECONDS,
            TailSetting.fromMeasurement(listOf(Double.NaN, -1.0)),
        )
    }

    /** With headphones the wait is nothing, whatever was measured. */
    @Test
    fun withHeadphonesThereIsNoWait() {
        assertEquals(
            0.0,
            TailSetting.forRoute(0.5, onThePhoneSpeaker = false),
        )
    }

    /** Through the speaker the measured tail is used. */
    @Test
    fun throughTheSpeakerTheMeasuredTailIsUsed() {
        assertEquals(0.4, TailSetting.forRoute(0.4, onThePhoneSpeaker = true))
    }

    /** Through the speaker with nothing measured, the default is used. */
    @Test
    fun throughTheSpeakerWithNothingMeasuredTheDefaultIsUsed() {
        assertEquals(
            TailSetting.DEFAULT_TAIL_SECONDS,
            TailSetting.forRoute(null, onThePhoneSpeaker = true),
        )
    }

    /** A measured tail out of range is brought back into it. */
    @Test
    fun anOutOfRangeTailIsBroughtBackIntoRange() {
        assertEquals(
            TailSetting.MAX_TAIL_SECONDS,
            TailSetting.forRoute(9.0, onThePhoneSpeaker = true),
        )
        assertEquals(
            TailSetting.MIN_TAIL_SECONDS,
            TailSetting.forRoute(-1.0, onThePhoneSpeaker = true),
        )
    }

    /** The tail is described in a way that reads on a phone. */
    @Test
    fun theTailIsDescribedReadably() {
        assertEquals("none - headphones", TailSetting.describe(0.0))
        assertEquals("50ms", TailSetting.describe(0.05))
        assertEquals("0.35s", TailSetting.describe(0.35))
    }
}
