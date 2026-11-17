package com.jr12866.dartcoach.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jr12866.dartcoach.core.HitLearner
import com.jr12866.dartcoach.core.HitStats
import com.jr12866.dartcoach.core.HitStatsSnapshot
import com.jr12866.dartcoach.core.Leg
import com.jr12866.dartcoach.core.LegsSnapshot
import com.jr12866.dartcoach.core.PlayerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.properties.ReadOnlyProperty


/**
 * The store, on the application context.
 *
 * This is a top-level extension property on purpose. `preferencesDataStore`
 * hands back a delegate that caches the store it creates, and the store has
 * to be a singleton - if the file is opened twice, the app refuses to
 * start. A property on the class would make a new one each time.
 *
 * The context it is read through is the application context, not the
 * activity's, for two reasons: the store outlives the activity, and the
 * application context is ready from the moment the app starts. The last
 * part matters, because the delegate reaches for the context when it
 * creates the store, and doing that while the activity is still being
 * constructed gave a null pointer and killed the app on launch.
 */
private val Context.store: DataStore<Preferences> by preferencesDataStore(
    name = "dartcoach",
)

/**
 * The player's settings and history, stored on the phone.
 *
 * Four things are kept:
 *
 * - the **manual overrides**, the segments the player has said they cannot
 *   hit;
 * - the **hit counts**, so the learning carries on across restarts rather
 *   than starting again from the population averages. These include where
 *   the player's misses landed, which is what tells the coach which way
 *   their darts go wrong;
 * - the **preferred double**, which the player would rather not have to say
 *   every leg;
 * - the **session history**, for the stats screen.
 *
 * The manual overrides and the learned rates are kept apart on purpose. A
 * player who has said "I cannot hit D16" should still have that after the
 * coach has learned something else, so the two never overwrite each other.
 * The learned rates are not stored at all - they come back out of the
 * counts.
 */
class SettingsStore(private val activity: Context) {

    /**
     * The context the store is read through.
     *
     * This is the application context, not an activity's. The store
     * outlives the activity, and the application context is ready from the
     * moment the app starts - which matters, because the delegate reaches
     * for the context when it creates the store, and doing that while the
     * activity is still being constructed gave a null pointer and killed
     * the app on launch.
     */
    private val context: Context = activity.applicationContext

    /**
     * The store itself, created when it is first needed.
     *
     * Everything in this class is lazy for the same reason: the store
     * reaches for the application context when it is created, and doing
     * that while the activity is still being constructed gets a null
     * context and kills the app on launch.
     */
    private val store: DataStore<Preferences> by lazy {
        context.store
    }

    /** The manual overrides, as they change. */
    val manual: Flow<PlayerProfile> by lazy {
        store.data.map { it.toManual() }
    }

    /** The double the player finishes on by default, if they have set one. */
    val preferredDouble: Flow<Int?> by lazy {
        store.data.map { prefs ->
            prefs[KEY_PREFERRED_DOUBLE]
        }
    }

    /** The counts the coach has built up, as they change. */
    val stats: Flow<HitStats> by lazy {
        store.data.map { prefs ->
            prefs.toStats()
        }
    }

    /**
     * The legs that have been played, as they change.
     *
     * The statistics are worked out from these, so they have to survive a
     * restart. A player who has thrown a hundred legs should not lose the
     * record of them because the app was closed.
     */
    val legs: Flow<List<Leg>> by lazy {
        store.data.map { prefs ->
            prefs.toLegs()
        }
    }

    /**
     * The measured echo tail, in seconds, if it has been measured.
     *
     * This is how long the coach waits after speaking before it listens
     * again. It depends on the phone - the speaker's ringing and the
     * acoustic path to the microphone - so it is measured rather than
     * assumed.
     */
    val measuredTail: Flow<Double?> by lazy {
        store.data.map { prefs ->
            prefs[KEY_MEASURED_TAIL]
        }
    }

    /**
     * Whether the player has bought Pro, as it changes.
     *
     * This is a cache of what Play says. Play is the truth - it holds the
     * purchase and hands it back on any device the player signs in to - but
     * the app has to work when Play cannot be reached, so the last known
     * answer is kept on the phone.
     */
    val isPro: Flow<Boolean> by lazy {
        store.data.map { prefs ->
            prefs[KEY_IS_PRO] ?: false
        }
    }

    /** Reads the manual overrides once. */
    suspend fun readManual(): PlayerProfile = manual.first()

    /** Reads the preferred double once. */
    suspend fun readPreferredDouble(): Int? = preferredDouble.first()

    /** Reads the counts once. */
    suspend fun readStats(): HitStats = stats.first()

    /** Reads the legs once. */
    suspend fun readLegs(): List<Leg> = legs.first()

    /** Reads the measured echo tail once. */
    suspend fun readMeasuredTail(): Double? = measuredTail.first()

    /**
     * Saves the measured echo tail.
     *
     * Null forgets it, which puts the coach back on the default.
     */
    suspend fun saveMeasuredTail(seconds: Double?) {
        store.edit { prefs ->
            if (seconds == null) {
                prefs.remove(KEY_MEASURED_TAIL)
            } else {
                prefs[KEY_MEASURED_TAIL] = seconds
            }
        }
    }

    /**
     * Saves the player's own settings for the segments they have set.
     *
     * These are the manual overrides, and they are kept apart from what the
     * coach has learned.
     */
    suspend fun saveManual(manual: PlayerProfile) {
        // Read into locals first: the property is in another module, so the
        // compiler cannot smart cast it.
        val bull = manual.doubleBullHit
        val outerBull = manual.outerBullHit
        val single = manual.singleHit
        store.edit { prefs ->
            prefs[KEY_MANUAL_TREBLE] = encodeMap(manual.trebleHit)
            prefs[KEY_MANUAL_DOUBLE] = encodeMap(manual.doubleHit)
            // The bull, the outer bull and the singles have rates of their
            // own rather than ones from the bed maps, so they are saved on
            // their own. Without that the player's own numbers for them were
            // lost every time the app was closed.
            if (bull == null) {
                prefs.remove(KEY_MANUAL_BULL)
            } else {
                prefs[KEY_MANUAL_BULL] = bull
            }
            if (outerBull == null) {
                prefs.remove(KEY_MANUAL_OUTER_BULL)
            } else {
                prefs[KEY_MANUAL_OUTER_BULL] = outerBull
            }
            if (single == null) {
                prefs.remove(KEY_MANUAL_SINGLE)
            } else {
                prefs[KEY_MANUAL_SINGLE] = single
            }
        }
    }

    /** Saves the preferred double. Null clears it. */
    suspend fun savePreferredDouble(double: Int?) {
        store.edit { prefs ->
            if (double == null) {
                prefs.remove(KEY_PREFERRED_DOUBLE)
            } else {
                prefs[KEY_PREFERRED_DOUBLE] = double
            }
        }
    }

    /**
     * Saves the counts the coach has built up.
     *
     * The learned rates are not saved, because they can be worked out again
     * from the counts. Saving the counts means the history carries on
     * accumulating, which is what makes the learning worth anything.
     */
    suspend fun saveStats(stats: HitStats) {
        val snapshot = HitStatsSnapshot.of(stats)
        store.edit { prefs ->
            prefs[KEY_ATTEMPTS] = snapshot.attempts.joinToString(";")
            prefs[KEY_HITS] = snapshot.hits.joinToString(";")
            prefs[KEY_ON_TARGET] = snapshot.onTarget.joinToString(";")
            prefs[KEY_MISS_LANDINGS] = snapshot.missLandings.joinToString(";")
            prefs[KEY_TOTAL_ATTEMPTS] = snapshot.totalAttempts
            prefs[KEY_TOTAL_HITS] = snapshot.totalHits
            prefs[KEY_TOTAL_ON_TARGET] = snapshot.totalOnTarget
        }
    }

    /**
     * Saves what the coach has learned.
     *
     * Only the counts. The learned rates come back out of the counts, and
     * the manual overrides are left alone.
     */
    suspend fun saveLearned(learner: HitLearner) {
        saveStats(learner.stats)
    }

    /**
     * Throws away what the coach has learned.
     *
     * The counts are the coach's memory of how the player throws. Clearing
     * them puts the coach back on the population averages, which is what a
     * player wants when the memory is of a game they did not play - or of a
     * test rather than a leg.
     *
     * The manual overrides and the legs are left alone: those are the
     * player's own choices and their own history, not the coach's memory.
     */
    suspend fun clearStats() {
        store.edit { prefs ->
            prefs.remove(KEY_ATTEMPTS)
            prefs.remove(KEY_HITS)
            prefs.remove(KEY_ON_TARGET)
            prefs.remove(KEY_MISS_LANDINGS)
            prefs.remove(KEY_TOTAL_ATTEMPTS)
            prefs.remove(KEY_TOTAL_HITS)
            prefs.remove(KEY_TOTAL_ON_TARGET)
        }
    }

    /**
     * Saves the legs that have been played.
     *
     * The statistics are worked out from these, so they are saved whenever
     * a leg is finished.
     */
    suspend fun saveLegs(legs: List<Leg>) {
        val snapshot = LegsSnapshot.of(legs)
        store.edit { prefs ->
            prefs[KEY_LEGS] = snapshot.legs.joinToString(";")
        }
    }

    /**
     * Saves whether the player has bought Pro.
     *
     * This is a cache. Play holds the purchase, and the app asks Play what
     * the player owns every time it starts. The cached answer is what the
     * app falls back on when Play cannot be reached.
     */
    suspend fun saveIsPro(isPro: Boolean) {
        store.edit { prefs ->
            prefs[KEY_IS_PRO] = isPro
        }
    }

    /** Forgets everything. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    // -- encoding -----------------------------------------------------------

    /** "16:0.4,20:0.6" - the bed and the rate. */
    private fun encodeMap(map: Map<Int, Double>): String =
        map.entries.joinToString(";") { "${it.key}:${it.value}" }

    /** Reads back what [encodeMap] wrote. */
    private fun decodeMap(text: String?): Map<Int, Double> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = mutableMapOf<Int, Double>()
        for (entry in text.split(";")) {
            val parts = entry.split(":")
            if (parts.size != 2) continue
            val key = parts[0].toIntOrNull() ?: continue
            val value = parts[1].toDoubleOrNull() ?: continue
            out[key] = value
        }
        return out
    }

    /** The manual overrides the stored settings describe. */
    private fun Preferences.toManual(): PlayerProfile = PlayerProfile(
        trebleHit = decodeMap(this[KEY_MANUAL_TREBLE]),
        doubleHit = decodeMap(this[KEY_MANUAL_DOUBLE]),
        singleHit = this[KEY_MANUAL_SINGLE],
        outerBullHit = this[KEY_MANUAL_OUTER_BULL],
        doubleBullHit = this[KEY_MANUAL_BULL],
    )

    /** The counts the stored settings describe. */
    private fun Preferences.toStats(): HitStats {
        val snapshot = HitStatsSnapshot(
            attempts = this[KEY_ATTEMPTS]?.split(";")?.filter { it.isNotBlank() }
                ?: emptyList(),
            hits = this[KEY_HITS]?.split(";")?.filter { it.isNotBlank() }
                ?: emptyList(),
            totalAttempts = this[KEY_TOTAL_ATTEMPTS] ?: 0,
            totalHits = this[KEY_TOTAL_HITS] ?: 0,
            onTarget = this[KEY_ON_TARGET]?.split(";")?.filter { it.isNotBlank() }
                ?: emptyList(),
            totalOnTarget = this[KEY_TOTAL_ON_TARGET] ?: 0,
            missLandings = this[KEY_MISS_LANDINGS]?.split(";")
                ?.filter { it.isNotBlank() } ?: emptyList(),
        )
        return snapshot.toStats()
    }

    /** The legs the stored settings describe. */
    private fun Preferences.toLegs(): List<Leg> {
        val snapshot = LegsSnapshot(
            legs = this[KEY_LEGS]?.split(";")?.filter { it.isNotBlank() }
                ?: emptyList(),
        )
        return snapshot.toLegs()
    }

    companion object {
        private val KEY_MANUAL_TREBLE = stringPreferencesKey("manual_treble")
        private val KEY_MANUAL_DOUBLE = stringPreferencesKey("manual_double")
        private val KEY_MANUAL_BULL = doublePreferencesKey("manual_bull")
        private val KEY_MANUAL_OUTER_BULL = doublePreferencesKey("manual_outer_bull")
        private val KEY_MANUAL_SINGLE = doublePreferencesKey("manual_single")
        private val KEY_PREFERRED_DOUBLE = intPreferencesKey("preferred_double")
        private val KEY_ATTEMPTS = stringPreferencesKey("attempts")
        private val KEY_HITS = stringPreferencesKey("hits")
        private val KEY_ON_TARGET = stringPreferencesKey("on_target")
        private val KEY_MISS_LANDINGS = stringPreferencesKey("miss_landings")
        private val KEY_TOTAL_ATTEMPTS = intPreferencesKey("total_attempts")
        private val KEY_TOTAL_HITS = intPreferencesKey("total_hits")
        private val KEY_TOTAL_ON_TARGET = intPreferencesKey("total_on_target")
        private val KEY_LEGS = stringPreferencesKey("legs")
        private val KEY_MEASURED_TAIL = doublePreferencesKey("measured_tail")
        private val KEY_IS_PRO = booleanPreferencesKey("is_pro")
    }
}
