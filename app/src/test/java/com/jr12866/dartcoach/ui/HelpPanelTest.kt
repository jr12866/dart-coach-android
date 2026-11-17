package com.jr12866.dartcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The help panel.
 *
 * Two things are checked here. The first is that the guide is actually a
 * guide: every topic says something, and what it says is short enough to be
 * read at the oche. The second is that every main screen carries one, which
 * is the bug this file exists because of - a screen with no way to ask what
 * it is showing is a screen nobody can learn.
 */
class HelpPanelTest {

    /**
     * Every topic has something to say.
     *
     * An empty topic is worse than no topic, because the player has found the
     * question mark and been told nothing.
     */
    @Test
    fun `every topic has a title and some points`() {
        for (topic in HelpTopic.values()) {
            assertTrue(
                "${topic.name} has no title",
                topic.title.isNotBlank(),
            )
            assertTrue(
                "${topic.name} has no points, so the panel would be empty",
                topic.points.size >= 3,
            )
        }
    }

    /**
     * Every point says something.
     */
    @Test
    fun `every point has a heading and a body`() {
        for (topic in HelpTopic.values()) {
            for (point in topic.points) {
                assertTrue(
                    "${topic.name} has a point with no heading",
                    point.heading.isNotBlank(),
                )
                assertTrue(
                    "${topic.name} has a point with no body: ${point.heading}",
                    point.body.isNotBlank(),
                )
            }
        }
    }

    /**
     * The panel is short.
     *
     * A panel that has to be scrolled through is a panel nobody reads, and
     * the player is standing at the oche. The limits are here so that a long
     * paragraph added later fails the build rather than the reader.
     */
    @Test
    fun `the panel stays short`() {
        val longestBody = 220
        val longestHeading = 40

        for (topic in HelpTopic.values()) {
            for (point in topic.points) {
                assertTrue(
                    "${topic.name}: \"${point.heading}\" is " +
                        "${point.body.length} characters, which is too long " +
                        "to read at the oche",
                    point.body.length <= longestBody,
                )
                assertTrue(
                    "${topic.name}: the heading \"${point.heading}\" is too long",
                    point.heading.length <= longestHeading,
                )
            }
        }
    }

    /**
     * Every main screen has a help button, and every topic is used.
     *
     * The two halves are the same check from opposite ends. A screen with no
     * panel is a dead end for a player who does not know what it is showing;
     * a topic that no screen opens is a panel nobody can reach.
     */
    @Test
    fun `every main screen carries a help button`() {
        val expected = mapOf(
            "CoachScreen.kt" to "COACH",
            "OutChartScreen.kt" to "OUT_CHART",
            "SetupScreen.kt" to "SETUP",
            "ProfileScreen.kt" to "PROFILE",
            "StatsScreen.kt" to "STATS",
            "SettingsScreen.kt" to "SETTINGS",
        )

        val sources = appSources()
        assertTrue(
            "The app's source was not found, so this test cannot check " +
                "anything. Looked in ${File(".").absolutePath}",
            sources.isNotEmpty(),
        )

        val missing = mutableListOf<String>()
        val unused = mutableListOf<String>()

        for ((file, topic) in expected) {
            val source = sources.firstOrNull { it.name == file }
            if (source == null) {
                missing.add("$file (the screen is gone)")
                continue
            }
            val text = source.readText()
            if (!text.contains("HelpButton(HelpTopic.$topic)")) {
                missing.add("$file has no help button for $topic")
            }
        }

        for (topic in HelpTopic.values()) {
            val used = expected.containsValue(topic.name)
            if (!used) unused.add(topic.name)
        }

        assertEquals(
            "These screens have no help button, so a player who does not " +
                "know what they are looking at has nowhere to ask: " +
                missing.joinToString(", "),
            emptyList<String>(),
            missing,
        )
        assertEquals(
            "These topics are written but no screen opens them, so they are " +
                "unreachable: ${unused.joinToString(", ")}",
            emptyList<String>(),
            unused,
        )
    }

    /**
     * The app's own Kotlin source.
     *
     * The test runs from the module directory, so the source is a directory
     * below it. The search walks up as well, in case the working directory
     * is not where it is expected to be.
     */
    private fun appSources(): List<File> {
        val found = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "src/main/java/com/jr12866/dartcoach") }
            .firstOrNull { it.isDirectory }
            ?: return emptyList()

        return found.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }
}
