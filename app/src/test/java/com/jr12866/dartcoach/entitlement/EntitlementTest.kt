package com.jr12866.dartcoach.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The gate.
 *
 * Two things are checked here. The first is what the gate does: what is
 * behind the paywall, and what happens when the gate is emptied. The second
 * is that every flag in the list is actually asked somewhere, which is the
 * bug this file exists because of.
 */
class EntitlementTest {

    /**
     * Every flag in the gate has to be asked for somewhere in the app.
     *
     * Three of the eight were declared and never asked, so the features they
     * name were not locked at all: the out chart and its percentages were
     * free. Nothing in the build said so. The plan said they were Pro.
     *
     * A flag that is never asked is a flag that does nothing, and a flag
     * that does nothing is a promise the app does not keep. This test reads
     * the app's own source and fails when a flag is declared and not used.
     */
    @Test
    fun `every feature in the gate is asked for somewhere`() {
        val sources = appSources()
        assertTrue(
            "The app's source was not found, so this test cannot check " +
                "anything. Looked in ${File(".").absolutePath}",
            sources.isNotEmpty(),
        )

        val dead = mutableListOf<String>()
        for (feature in Entitlement.Feature.values()) {
            val token = "Feature.${feature.name}"
            val used = sources.any { file ->
                file.name != "Entitlement.kt" && file.readText().contains(token)
            }
            if (!used) dead.add(feature.name)
        }

        assertEquals(
            "These features are in PRO_FEATURES but nothing asks for them, " +
                "so they are not actually locked: ${dead.joinToString(", ")}",
            emptyList<String>(),
            dead,
        )
    }

    /**
     * A feature that is not in the gate is free.
     *
     * This is what makes moving a feature out of the paywall a config edit
     * rather than a hunt through the code.
     */
    @Test
    fun `a feature that is not in the gate is free`() {
        val gate = FeatureGated(gated = emptySet(), purchased = false)

        for (feature in Entitlement.Feature.values()) {
            assertTrue(
                "${feature.name} should be free when the gate is empty",
                gate.has(feature),
            )
        }
    }

    /**
     * A feature that is in the gate needs the purchase.
     */
    @Test
    fun `a feature in the gate needs the purchase`() {
        val gate = FeatureGated(gated = PRO_FEATURES, purchased = false)

        for (feature in Entitlement.Feature.values()) {
            assertFalse(
                "${feature.name} should be behind the paywall",
                gate.has(feature),
            )
        }
    }

    /**
     * A player who has bought Pro has everything.
     */
    @Test
    fun `a player who has bought Pro has everything`() {
        val gate = FeatureGated(gated = PRO_FEATURES, purchased = true)

        for (feature in Entitlement.Feature.values()) {
            assertTrue(
                "${feature.name} should be available to a player who bought Pro",
                gate.has(feature),
            )
        }
    }

    /**
     * The gate is the list, and the list is what the paywall says.
     *
     * The three features that were not wired up are named here, so that
     * removing one from the gate is a deliberate act rather than an accident.
     */
    @Test
    fun `the features that were not wired up are now asked for`() {
        val gate = FeatureGated(gated = PRO_FEATURES, purchased = false)

        assertFalse(
            "The out chart should be behind the paywall",
            gate.has(Entitlement.Feature.OUT_CHART),
        )
        assertFalse(
            "The percentages should be behind the paywall",
            gate.has(Entitlement.Feature.PERSONALISED_PROBABILITIES),
        )
        assertFalse(
            "The kept preferred double should be behind the paywall",
            gate.has(Entitlement.Feature.PERSISTENT_PREFERENCES),
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
