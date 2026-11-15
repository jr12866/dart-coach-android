package com.jr12866.dartcoach

import android.content.Context

/**
 * The test-Pro switch, in a release build.
 *
 * There is no switch. A release build cannot turn Pro on without a
 * purchase, and it cannot be made to by an intent extra, a shared
 * preference, or anything else.
 *
 * The debug build has its own version of this file, in `src/debug`, and the
 * two are chosen by Gradle from the build type. The debug one is not in
 * this build at all, so there is nothing here to be tricked into turning
 * Pro on.
 */
internal fun testProSwitchEnabled(context: Context, testProExtra: Boolean): Boolean = false
