package com.jr12866.dartcoach.entitlement

/**
 * What the player has bought.
 *
 * This is the only place that decides what is gated, so that changing the
 * set of gated features is a change to one list rather than a hunt through
 * the UI for `if (isPro)` checks.
 *
 * The price can be changed in Play Console without a release. The *set of
 * gated features* cannot, which is why this is a list and not a scattering
 * of checks.
 */
interface Entitlement {

    /** True when the player has bought Pro. */
    val isPro: Boolean

    /**
     * Whether a feature is available.
     *
     * The features are named rather than numbered so that the list reads as
     * a description of what Pro is.
     */
    fun has(feature: Feature): Boolean

    /**
     * The features that are behind the paywall.
     */
    enum class Feature {
        /** The out chart: every score from 2 to 170. */
        OUT_CHART,

        /** The routes onto each of the player's own doubles. */
        PERSONALISED_ROUTES,

        /** The probabilities worked out from the player's own accuracy. */
        PERSONALISED_PROBABILITIES,

        /**
         * What the coach has worked out about the player's own play.
         *
         * Setting a hit rate by hand is free - it makes the free coach
         * better for the player - but the analysis of what the coach has
         * learned is what Pro is for.
         */
        LEARNED_ANALYSIS,

        /** What to aim at from 171 to 350. */
        SETUP_COACHING,

        /** The preferred double, kept across legs and restarts. */
        PERSISTENT_PREFERENCES,

        /** The player's own statistics. */
        SESSION_STATS,

        /**
         * Correcting a missed dart and re-planning the rest of the turn.
         *
         * This changes what the coach does with the next darts, so it is a
         * coaching feature rather than another reference screen.
         */
        MISSED_TARGET_CORRECTION,
    }
}

/**
 * The features that are behind the paywall.
 *
 * Everything not in this list is free. The live coach - the suggestion at
 * the oche, the scorekeeping, the read-back - is free on purpose, because
 * it is what makes the app worth having and it is what earns the ratings.
 */
val PRO_FEATURES: Set<Entitlement.Feature> = setOf(
    Entitlement.Feature.OUT_CHART,
    Entitlement.Feature.PERSONALISED_ROUTES,
    Entitlement.Feature.PERSONALISED_PROBABILITIES,
    Entitlement.Feature.LEARNED_ANALYSIS,
    Entitlement.Feature.SETUP_COACHING,
    Entitlement.Feature.PERSISTENT_PREFERENCES,
    Entitlement.Feature.SESSION_STATS,
    Entitlement.Feature.MISSED_TARGET_CORRECTION,
)

/**
 * An entitlement that has not been bought.
 *
 * This is what the app starts with, before Play has been asked.
 */
object NotPro : Entitlement {
    override val isPro: Boolean get() = false
    override fun has(feature: Entitlement.Feature): Boolean = false
}

/**
 * An entitlement that has been bought.
 *
 * Everything is available, whether or not it is in [PRO_FEATURES]: the list
 * says what is *gated*, not what exists.
 */
object Pro : Entitlement {
    override val isPro: Boolean get() = true
    override fun has(feature: Entitlement.Feature): Boolean = true
}

/**
 * An entitlement that gates some things and not others.
 *
 * This is what makes the gate a list rather than a yes or no. A player who
 * has bought Pro has everything; a player who has not has only what is
 * outside the paywall.
 */
class FeatureGated(
    /** The features that are behind the paywall. */
    private val gated: Set<Entitlement.Feature> = PRO_FEATURES,
    /** Whether the player has bought Pro. */
    private val purchased: Boolean = false,
) : Entitlement {

    override val isPro: Boolean get() = purchased

    /**
     * Whether a feature is available.
     *
     * A feature that is not in the gate is free. One that is in it needs
     * the purchase.
     */
    override fun has(feature: Entitlement.Feature): Boolean =
        if (feature in gated) purchased else true
}
