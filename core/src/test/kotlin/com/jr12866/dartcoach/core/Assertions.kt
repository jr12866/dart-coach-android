package com.jr12866.dartcoach.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Assertions that compare a nullable value against an expected nullable
 * value, with no overload resolution to get wrong.
 *
 * `kotlin.test.assertEquals` has overloads for `Double`, and when the actual
 * value's type cannot be resolved - which happens while the port is still
 * being written - Kotlin silently picks the `Double` one and reports a
 * confusing type error instead of the real one. These helpers name the type
 * they compare, so the error message is always about the thing that is
 * actually wrong.
 */
fun assertSameNullable(
    expected: Int?,
    actual: Int?,
    message: String,
) {
    assertEquals(expected, actual, message)
}

fun assertSameNullable(
    expected: String?,
    actual: String?,
    message: String,
) {
    assertEquals(expected, actual, message)
}

fun assertSameNullable(
    expected: Boolean?,
    actual: Boolean?,
    message: String,
) {
    assertEquals(expected, actual, message)
}

fun assertSameIntList(
    expected: List<Int>?,
    actual: List<Int>?,
    message: String,
) {
    assertEquals(expected, actual, message)
}

fun assertSameStringList(
    expected: List<String>,
    actual: List<String>,
    message: String,
) {
    assertEquals(expected, actual, message)
}

/**
 * Fails the test with a useful message if `cases` is smaller than `minimum`.
 *
 * A corpus that came back empty or truncated would otherwise make the test
 * pass without checking anything, which is worse than no test at all.
 */
fun assertCorpusSize(actual: Int, minimum: Int, what: String) {
    assertTrue(
        actual >= minimum,
        "The $what corpus should have at least $minimum cases but has $actual. " +
            "Regenerate it with:\n" +
            "  cd ../darts-coach-windows\n" +
            "  python tools/parity/parity_gen.py --out ../dart-coach-android/corpus",
    )
}