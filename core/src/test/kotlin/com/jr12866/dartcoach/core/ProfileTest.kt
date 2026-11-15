package com.jr12866.dartcoach.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The personalisation: a profile describes how well the player throws, and
 * the coach's advice comes out of it.
 *
 * The first test is the important one. The aim list is ordered, and the
 * planner breaks ties on that order, so the profile-driven list has to be
 * exactly the same list the population produces. If it drifts, the advice
 * drifts with it.
 */
class ProfileTest {

    /** The default profile must produce exactly the population's aim list. */
    @Test
    fun theDefaultProfileProducesTheSameAims() {
        val fromProfile = buildAims(PlayerProfile())
        assertEquals(AIMS.size, fromProfile.size)
        assertEquals(AIMS, fromProfile)
    }

    /** The order must not drift either: the planner breaks ties on it. */
    @Test
    fun theDefaultProfileProducesTheSameOrder() {
        val labels = buildAims(PlayerProfile()).map { it.label }
        assertEquals(AIMS.map { it.label }, labels)
    }

    /** A profile with an override changes only that aim's probability. */
    @Test
    fun anOverrideChangesOnlyThatAim() {
        val profile = PlayerProfile(doubleHit = mapOf(16 to 0.10))
        val aims = buildAims(profile)

        val d16 = aims.first { it.label == "D16" }
        assertEquals(0.10, d16.pHit, 1e-12)

        // Everything else is untouched.
        val d18 = aims.first { it.label == "D18" }
        assertEquals(PlayerProfile.defaultDoubleHit(18), d18.pHit, 1e-12)
        val t20 = aims.first { it.label == "T20" }
        assertEquals(PlayerProfile.defaultTrebleHit(20), t20.pHit, 1e-12)
    }

    /** A weak treble makes it less likely to wobble into the single bed. */
    @Test
    fun aWeakTrebleWobblesLess() {
        val profile = PlayerProfile(trebleHit = mapOf(20 to 0.10))
        val t20 = buildAims(profile).first { it.label == "T20" }
        assertEquals(0.90 * 0.5, t20.pWobble, 1e-12)
    }

    /** A default planner must behave exactly as the ported one does. */
    @Test
    fun aDefaultPlannerMatchesTheSharedOne() {
        val mine = Planner.build()
        val shared = Planner.shared
        for (score in 1..170) {
            assertEquals(
                shared.planFor(score).labels,
                mine.planFor(score).labels,
                "plan differs at $score",
            )
        }
    }

    /** A player who cannot hit D16 should be routed onto a double they can. */
    @Test
    fun aWeakDoubleIsRoutedAround() {
        val profile = PlayerProfile(doubleHit = mapOf(16 to 0.05))
        val planner = Planner.build(profile)

        // 40 is finished by D20 alone, so it is untouched by a weak D16.
        assertEquals(listOf("D20"), planner.planFor(40).labels)

        // 32 is a split: D16 or D8. A player who cannot hit D16 should be
        // told about D8, not D16.
        val plan = planner.planFor(32)
        assertTrue(
            plan.doubles.isNotEmpty(),
            "there should still be a double to aim at",
        )
    }

    /** A profile that says a treble is weak changes what the coach suggests. */
    @Test
    fun aWeakTrebleChangesTheAdvice() {
        val strong = Planner.build(
            PlayerProfile(trebleHit = mapOf(20 to 0.58)),
        )
        val weak = Planner.build(
            PlayerProfile(trebleHit = mapOf(20 to 0.05)),
        )

        // At 501 the coach is setting up, and how good T20 is changes what
        // it aims for.
        val strongPlan = strong.planFor(501)
        val weakPlan = weak.planFor(501)
        assertTrue(
            strongPlan.probability != weakPlan.probability,
            "the probabilities should differ when T20 does",
        )
    }

    /** The bull is worth 50, so it is the finishing dart for D50. */
    @Test
    fun theBullIsStillFoundByItsValue() {
        val planner = Planner.build(PlayerProfile(doubleBullHit = 0.9))
        val route = planner.routeToDouble(50, 50)
        assertNotNull(route)
        assertEquals(listOf("Bull"), route.labels)
    }

    /** A personalised planner can still route onto a chosen double. */
    @Test
    fun aRouteToAChosenDoubleStillWorks() {
        val planner = Planner.build(PlayerProfile(doubleHit = mapOf(20 to 0.95)))
        val route = planner.routeToDouble(40, 20)
        assertNotNull(route)
        assertEquals(listOf("D20"), route.labels)
        assertTrue(route.probability > 0.9, "a good D20 should be likely")
    }

    /** The coach must think with the planner it was given. */
    @Test
    fun theCoachThinksWithThePlannerItWasGiven() {
        val engine = CoachEngine(
            planner = Planner.build(PlayerProfile(doubleHit = mapOf(20 to 0.95))),
        )
        // 40 is finished on D20 alone. Reporting 1 leaves 39 standing, which
        // is finished on 7 then D16.
        engine.state.remainingScore = 40
        engine.handle("1")
        engine.handle("yes")
        assertTrue(
            engine.suggestion.isNotEmpty(),
            "there should be a suggestion, was: ${engine.suggestion}",
        )
    }

    /** The default coach still thinks with the shared planner. */
    @Test
    fun theDefaultCoachIsUnchanged() {
        val engine = CoachEngine()
        engine.state.remainingScore = 100
        engine.handle("1")
        engine.handle("yes")
        assertEquals(
            Planner.shared.planFor(99).labels.joinToString(" "),
            engine.suggestion.lines().first(),
        )
    }

    // -- combining what was learned with what was set ------------------------

    /** The player's own number for a segment wins over the learned one. */
    @Test
    fun thePlayerOwnNumberWins() {
        val learned = PlayerProfile(doubleHit = mapOf(16 to 0.55))
        val manual = PlayerProfile(doubleHit = mapOf(16 to 0.10))
        val merged = PlayerProfile.merge(learned, manual)
        assertEquals(0.10, merged.doubleHit[16]!!, 1e-12)
    }

    /** A segment the player has not set keeps the learned rate. */
    @Test
    fun anUnsetSegmentKeepsTheLearnedRate() {
        val learned = PlayerProfile(doubleHit = mapOf(16 to 0.55, 20 to 0.70))
        val manual = PlayerProfile(doubleHit = mapOf(16 to 0.10))
        val merged = PlayerProfile.merge(learned, manual)
        assertEquals(0.70, merged.doubleHit[20]!!, 1e-12)
    }

    /** A segment the player has not set and nothing was learned about keeps the default. */
    @Test
    fun anUnknownSegmentKeepsTheDefault() {
        val merged = PlayerProfile.merge(PlayerProfile(), PlayerProfile())
        assertTrue(merged.doubleHit.isEmpty())
        assertEquals(
            PlayerProfile.defaultDoubleHit(16),
            merged.doubleChance(16),
            1e-12,
        )
    }
}
