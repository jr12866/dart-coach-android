package com.jr12866.dartcoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The score, drawn as a seven segment LED display.
 *
 * A real seven segment font would have been the obvious way to do this, but
 * it would mean shipping a font file with the app: a licence to keep track
 * of, a download that has to work on a phone that may never be online, and
 * a glyph for every character the score might need.
 *
 * Drawing the digits instead is less work than the paperwork. There are
 * seven bars per digit and the shape of each bar is fixed, so the whole
 * display is a table of which bars are lit and a handful of numbers. It
 * also means the unlit bars can be drawn faintly, which is what makes a
 * real LED display look like a real LED display rather than like a font.
 *
 * The score is the one thing the player looks at mid-leg, so it is drawn
 * rather than typed: it stays readable from the oche, it cannot be reflowed
 * by a long message, and it never changes size.
 */
@Composable
fun LedScore(
    value: Int,
    modifier: Modifier = Modifier,
    color: Color = AccentGreen,
    height: Dp = 104.dp,
) {
    // A negative score cannot happen, but the board should not look broken
    // if one ever does, so the sign is drawn as a bar rather than dropped.
    val text = if (value < 0) "-${-value}" else value.toString()

    BoxWithConstraints(modifier = modifier) {
        val cells = text.length
        if (cells == 0) return@BoxWithConstraints

        // The width of the whole display for a given digit height. A real
        // seven segment digit is a little over half as wide as it is tall,
        // and the gap between two digits is about a third of a digit.
        val unitsPerDigit = 0.60f
        val unitsPerGap = 0.16f
        val widthUnits = unitsPerDigit * cells + unitsPerGap * (cells - 1)

        // The display is as tall as it was asked to be, but never so tall
        // that the digits run off the side of the screen.
        val digitHeight = minOf(height, maxWidth / widthUnits)
        val digitWidth = digitHeight * unitsPerDigit
        val gap = digitHeight * unitsPerGap
        val totalWidth = digitWidth * cells + gap * (cells - 1)

        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .width(totalWidth)
                .height(digitHeight),
        ) {
            drawLedText(
                text = text,
                digitWidth = digitWidth.toPx(),
                digitHeight = digitHeight.toPx(),
                gap = gap.toPx(),
                on = color,
                off = color.copy(alpha = 0.12f),
            )
        }
    }
}

/**
 * Draws the text as seven segment digits.
 *
 * The unlit bars are drawn first, in a faint colour, and the lit ones are
 * drawn over the top of them. That is the order a real display works in:
 * every bar is there, and the lit ones are brighter.
 */
private fun DrawScope.drawLedText(
    text: String,
    digitWidth: Float,
    digitHeight: Float,
    gap: Float,
    on: Color,
    off: Color,
) {
    // The thickness of a bar.
    val thickness = digitHeight * 0.115f

    // Every bar of every digit, lit or not.
    val bars = mutableListOf<Bar>()
    var left = 0f
    for (character in text) {
        val segments = SEGMENTS[character] ?: SEGMENTS.getValue(' ')
        bars += barsOf(
            left = left,
            width = digitWidth,
            height = digitHeight,
            thickness = thickness,
            segments = segments,
        )
        left += digitWidth + gap
    }

    // The unlit bars are drawn first, so that every bar of the display is
    // there whether or not it is lit. That is what makes it look like a
    // display rather than like a font.
    for (bar in bars) {
        if (bar.lit) continue
        drawLine(
            color = off,
            start = bar.start,
            end = bar.end,
            strokeWidth = thickness * 0.8f,
            cap = StrokeCap.Round,
        )
    }

    // The soft edge of the lit bars, drawn wider and dimmer so the display
    // glows the way a real LED does.
    for (bar in bars) {
        if (!bar.lit) continue
        drawLine(
            color = on.copy(alpha = 0.16f),
            start = bar.start,
            end = bar.end,
            strokeWidth = thickness * 2.4f,
            cap = StrokeCap.Round,
        )
    }

    // The lit bars themselves, on top of everything.
    for (bar in bars) {
        if (!bar.lit) continue
        drawLine(
            color = on,
            start = bar.start,
            end = bar.end,
            strokeWidth = thickness,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * One bar of a digit: which segment it is, and where it runs.
 */
private data class Segment(
    /** The bit for this segment. */
    val flag: Int,
    /** Where the bar starts. */
    val start: Offset,
    /** Where the bar ends. */
    val end: Offset,
)

/**
 * One bar of a digit, lit or not.
 */
private data class Bar(
    /** Where the bar starts. */
    val start: Offset,
    /** Where the bar ends. */
    val end: Offset,
    /** Whether this segment is lit for the digit being drawn. */
    val lit: Boolean,
)

/**
 * The seven bars of one digit, each marked lit or unlit.
 *
 * The bars meet at their centres, so the digit is a connected skeleton
 * rather than a set of separate marks. That is how a real seven segment
 * display is built, and it is what makes the shape read as a digit.
 */
private fun barsOf(
    left: Float,
    width: Float,
    height: Float,
    thickness: Float,
    segments: Int,
): List<Bar> {
    val half = thickness / 2f
    val middle = height / 2f
    val right = left + width

    // Every bar of the digit, as a pair of points.
    //
    // The points are inset by half the thickness, because a round cap adds
    // that much to each end of the bar.
    val segmentsOfDigit = listOf(
        // A: the top bar.
        Segment(1, Offset(left + half, half), Offset(right - half, half)),
        // B: the upper right bar.
        Segment(2, Offset(right - half, half), Offset(right - half, middle)),
        // C: the lower right bar.
        Segment(4, Offset(right - half, middle), Offset(right - half, height - half)),
        // D: the bottom bar.
        Segment(8, Offset(left + half, height - half), Offset(right - half, height - half)),
        // E: the lower left bar.
        Segment(16, Offset(left + half, middle), Offset(left + half, height - half)),
        // F: the upper left bar.
        Segment(32, Offset(left + half, half), Offset(left + half, middle)),
        // G: the middle bar.
        Segment(64, Offset(left + half, middle), Offset(right - half, middle)),
    )

    return segmentsOfDigit.map { segment ->
        Bar(
            segment.start,
            segment.end,
            (segments and segment.flag) != 0,
        )
    }
}

/**
 * Which bars are lit for each character.
 *
 * The bits are the seven segments: A (top), B (upper right), C (lower
 * right), D (bottom), E (lower left), F (upper left) and G (middle).
 */
private val SEGMENTS: Map<Char, Int> = mapOf(
    '0' to 0b0111111,
    '1' to 0b0000110,
    '2' to 0b1011011,
    '3' to 0b1001111,
    '4' to 0b1100110,
    '5' to 0b1101101,
    '6' to 0b1111101,
    '7' to 0b0000111,
    '8' to 0b1111111,
    '9' to 0b1101111,
    '-' to 0b1000000,
    ' ' to 0b0000000,
)
