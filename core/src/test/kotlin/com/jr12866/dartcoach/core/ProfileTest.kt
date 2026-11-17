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

    // -- the bull ------------------------------------------------------------

    /**
     * A player who can hit the bull is set up for the 170 out.
     *
     * The 170 out is finished with T20 T20 and the double bull, so it is
     * only worth aiming at when the player can hit the bull. With the
     * population average bull rate the coach sets up instead, and that is
     * right - but a player who says they hit the bull has to get the
     * classic answer back.
     */
    @Test
    fun aBullHittingPlayerIsSetUpForThe170Out() {
        val planner = Planner.build(PlayerProfile(doubleBullHit = 0.45))

        for (score in listOf(350, 347, 344)) {
            val plan = planner.planFor(score)
            assertEquals(
                170,
                plan.leaves,
                "a 180 from $score should leave 170, got ${plan.labels}",
            )
        }
    }

    /** A player who cannot hit the bull is not set up for it. */
    @Test
    fun aPlayerWhoCannotHitTheBullIsNotSetUpForThe170Out() {
        val plan = Planner.build().planFor(350)
        assertTrue(
            plan.leaves != 170,
            "the bull out is not worth aiming at with the average bull rate",
        )
    }

    /** The learned bull rate is used when the player has not set one. */
    @Test
    fun theLearnedBullRateIsUsedWhenThePlayerHasNotSetOne() {
        val learned = PlayerProfile(doubleBullHit = 0.50)
        val merged = PlayerProfile.merge(learned, PlayerProfile())
        assertEquals(0.50, merged.doubleBullHit!!, 1e-12)
    }

    /** The player's own bull rate wins over the learned one. */
    @Test
    fun thePlayerOwnBullRateWins() {
        val learned = PlayerProfile(doubleBullHit = 0.50)
        val manual = PlayerProfile(doubleBullHit = 0.80)
        val merged = PlayerProfile.merge(learned, manual)
        assertEquals(0.80, merged.doubleBullHit!!, 1e-12)
    }

    /** A profile with no bull rate of its own uses the population average. */
    @Test
    fun anUnsetBullRateKeepsTheDefault() {
        val bull = buildAims(PlayerProfile()).first { it.label == "Bull" }
        assertEquals(PlayerProfile.DEFAULT_DB_HIT, bull.pHit, 1e-12)
    }

    // -- the singles and the outer bull --------------------------------------

    /**
     * The single rate the player configured is the one the coach throws with.
     *
     * The singles are pooled, so one number covers all twenty beds.
     */
    @Test
    fun aConfiguredSingleRateIsUsed() {
        val aims = buildAims(PlayerProfile(singleHit = 0.50))
        for (bed in 1..20) {
            assertEquals(
                0.50,
                aims.first { it.label == bed.toString() }.pHit,
                1e-12,
                "the single $bed should use the configured rate",
            )
        }
    }

    /** The outer bull rate the player configured is the one the coach uses. */
    @Test
    fun aConfiguredOuterBullRateIsUsed() {
        val aims = buildAims(PlayerProfile(outerBullHit = 0.40))
        assertEquals(0.40, aims.first { it.label == "25" }.pHit, 1e-12)
    }

    /** A learned single rate is used when the player has not set one. */
    @Test
    fun theLearnedSingleRateIsUsedWhenThePlayerHasNotSetOne() {
        val learned = PlayerProfile(singleHit = 0.50)
        val merged = PlayerProfile.merge(learned, PlayerProfile())
        assertEquals(0.50, merged.singleHit!!, 1e-12)
        assertEquals(
            0.50,
            buildAims(merged).first { it.label == "7" }.pHit,
            1e-12,
        )
    }

    /** The player's own single rate wins over the learned one. */
    @Test
    fun thePlayerOwnSingleRateWins() {
        val learned = PlayerProfile(singleHit = 0.50)
        val manual = PlayerProfile(singleHit = 0.80)
        val merged = PlayerProfile.merge(learned, manual)
        assertEquals(0.80, merged.singleHit!!, 1e-12)
    }

    /** A learned outer bull rate is used when the player has not set one. */
    @Test
    fun theLearnedOuterBullRateIsUsedWhenThePlayerHasNotSetOne() {
        val learned = PlayerProfile(outerBullHit = 0.40)
        val merged = PlayerProfile.merge(learned, PlayerProfile())
        assertEquals(0.40, merged.outerBullHit!!, 1e-12)
        assertEquals(
            0.40,
            buildAims(merged).first { it.label == "25" }.pHit,
            1e-12,
        )
    }

    /** The player's own outer bull rate wins over the learned one. */
    @Test
    fun thePlayerOwnOuterBullRateWins() {
        val learned = PlayerProfile(outerBullHit = 0.40)
        val manual = PlayerProfile(outerBullHit = 0.70)
        val merged = PlayerProfile.merge(learned, manual)
        assertEquals(0.70, merged.outerBullHit!!, 1e-12)
    }

    /**
     * The coach learns the player's singles and uses what it learned.
     *
     * This is the whole point of the merge preferring the learned number
     * when the player has not said otherwise: a player who misses a lot of
     * singles was never priced for it before.
     */
    @Test
    fun theLearnedSingleRateReachesTheCoach() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(listOf(Segment(7, 1)))
            learner.learn(listOf(Segment(7, 2)))
        }

        val merged = PlayerProfile.merge(learner.profile(), PlayerProfile())
        val learned = merged.singleHit!!
        assertTrue(
            learned < PlayerProfile.DEFAULT_SINGLE_HIT,
            "thirty missed singles should pull the rate down, was $learned",
        )
        assertEquals(
            learned,
            buildAims(merged).first { it.label == "7" }.pHit,
            1e-12,
        )
    }

    /**
     * The coach learns the player's outer bull and uses what it learned.
     *
     * The outer bull is the one bull the learner used to skip, so its rate
     * sat at the population average whatever the player's own play said.
     */
    @Test
    fun theLearnedOuterBullRateReachesTheCoach() {
        val learner = HitLearner()
        repeat(30) {
            learner.setAimedSegments(listOf(Segment(25, 1)))
            learner.learn(listOf(Segment(20, 1)))
        }

        val merged = PlayerProfile.merge(learner.profile(), PlayerProfile())
        val learned = merged.outerBullHit!!
        assertTrue(
            learned < PlayerProfile.DEFAULT_SB_HIT,
            "thirty missed outer bulls should pull the rate down, was $learned",
        )
        assertEquals(
            learned,
            buildAims(merged).first { it.label == "25" }.pHit,
            1e-12,
        )
    }

    /** A profile with no rates of its own uses the population averages. */
    @Test
    fun anUnsetRateKeepsTheDefault() {        val aims = buildAims(PlayerProfile())
        assertEquals(
            PlayerProfile.DEFAULT_SINGLE_HIT,
            aims.first { it.label == "7" }.pHit,
            1e-12,
        )
        assertEquals(
            PlayerProfile.DEFAULT_SB_HIT,
            aims.first { it.label == "25" }.pHit,
            1e-12,
        )
    }
}
