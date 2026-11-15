package com.jr12866.dartcoach

import android.content.Context

/**
 * The test-only switch that lets the Pro flows be exercised without a Play
 * purchase.
 *
 * The switch itself lives in the debug source set. A release build has no
 * code that can turn Pro on: the `src/release` version of
 * [testProSwitchEnabled] always returns false, and the release build does
 * not contain the debug one at all.
 *
 * The mechanism is an intent extra, so it can be reached from a terminal
 * without touching the screen:
 *
 * ```
 * adb shell am start -S -n com.jr12866.dartcoach/.MainActivity --ez testpro true
 * ```
 *
 * It was needed to exercise the Pro flows - the gate, the paywall, the
 * missed-target correction - on a device without a Play purchase. Once the
 * real purchase path has been tested, the debug source set can be deleted
 * along with the extra.
 */
object TestProSwitch {

    /**
     * The name of the shared preferences file the switch is kept in.
     *
     * It is its own file, separate from the player's settings, so that
     * clearing the app's settings does not quietly turn Pro back on.
     */
    const val PREFS = "dartcoach_test"

    /** The key the switch is kept under. */
    const val KEY_TEST_PRO = "test_pro"

    /**
     * Turns Pro on for this install, if the intent asked for it.
     *
     * Returns whether the switch is now on.
     */
    fun applyTestPro(context: Context, testProExtra: Boolean): Boolean =
        testProSwitchEnabled(context, testProExtra)
}
