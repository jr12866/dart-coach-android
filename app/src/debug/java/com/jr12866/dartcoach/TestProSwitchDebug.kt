package com.jr12866.dartcoach

import android.content.Context

/**
 * The test-Pro switch, in a debug build.
 *
 * The switch is sticky, because it is no use turning it on for one launch
 * and then having to set it again. It is stored in its own shared
 * preferences file, separate from the player's settings, so that clearing
 * the app's settings does not quietly turn Pro back on.
 *
 * This function is not in the release build, so a release build has no way
 * of being told to enable Pro without a purchase.
 */
internal fun testProSwitchEnabled(context: Context, testProExtra: Boolean): Boolean {
    val prefs = context.getSharedPreferences(
        TestProSwitch.PREFS,
        Context.MODE_PRIVATE,
    )
    if (testProExtra) {
        prefs.edit().putBoolean(TestProSwitch.KEY_TEST_PRO, true).apply()
    }
    return prefs.getBoolean(TestProSwitch.KEY_TEST_PRO, false)
}
