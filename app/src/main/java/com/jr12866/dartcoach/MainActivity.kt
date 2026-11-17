package com.jr12866.dartcoach

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jr12866.dartcoach.audio.AudioEngine
import com.jr12866.dartcoach.billing.BillingRepository
import com.jr12866.dartcoach.core.CoachEngine
import com.jr12866.dartcoach.core.HitLearner
import com.jr12866.dartcoach.core.Planner
import com.jr12866.dartcoach.core.PlayerProfile
import com.jr12866.dartcoach.core.ProfileView
import com.jr12866.dartcoach.core.Stats
import com.jr12866.dartcoach.core.StatsView
import com.jr12866.dartcoach.core.TailSetting
import com.jr12866.dartcoach.data.SettingsStore
import com.jr12866.dartcoach.entitlement.Entitlement
import com.jr12866.dartcoach.entitlement.FeatureGated
import com.jr12866.dartcoach.entitlement.NotPro
import com.jr12866.dartcoach.entitlement.Pro
import com.jr12866.dartcoach.speech.PiperTts
import com.jr12866.dartcoach.ui.AppNavHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The coach.
 *
 * The phone does everything: it hears the score, works out what to throw,
 * and says it. There is no server and no network.
 *
 * The screen is deliberately plain. At the oche the player wants to read the
 * score and the suggestion, not admire a design.
 */
class MainActivity : ComponentActivity() {

    /**
     * The coach.
     *
     * It is created with the player's own profile once the settings have
     * loaded, so the advice is theirs from the first suggestion rather than
     * the population's. It is state, because the screen has to redraw when
     * it is replaced.
     */
    private var coach by mutableStateOf(CoachEngine())

    /**
     * The player's settings, on the phone.
     *
     * This is created in `onCreate` rather than here, because a field
     * initialiser runs before the activity has been attached to the
     * application, and the store needs the application context. Asking for
     * it too early gave a null pointer and killed the app on launch.
     */
    private lateinit var store: SettingsStore

    private val billing = BillingRepository(this)
    private lateinit var audio: AudioEngine
    private lateinit var tts: PiperTts

    /** The work that is not on the main thread: saving the settings. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * What the player has bought.
     *
     * This starts as not-Pro and is replaced once Play has been asked. The
     * whole gate is decided in one place, so that changing what is behind
     * the paywall is a change to one list.
     */
    private var entitlement by mutableStateOf<Entitlement>(NotPro)

    /**
     * The gate as it is right now.
     *
     * This is the feature list with the purchase on or off, so the screen
     * asks "is this feature available" rather than "has the player bought
     * Pro". That is what lets a feature be moved out of the paywall by
     * editing one list.
     */
    private val gate: Entitlement get() = FeatureGated(
        purchased = entitlement.isPro || testPro,
    )

    /** What Pro costs, in the player's own currency. */
    private var price by mutableStateOf<String?>(null)

    /** What went wrong with the purchase, if something did. */
    private var purchaseError by mutableStateOf<String?>(null)

    /**
     * The profile screen's view of how the player throws.
     *
     * It is rebuilt whenever the counts or the player's own settings change,
     * so the screen always shows the current numbers.
     */
    private var profileView by mutableStateOf(
        ProfileView.of(HitLearner(), PlayerProfile())
    )

    /**
     * The statistics screen's view of the player.
     *
     * It is rebuilt whenever a leg is finished or the counts move.
     */
    private var statsView by mutableStateOf(
        StatsView(Stats.EMPTY, HitLearner())
    )

    /**
     * The measured echo tail, in seconds.
     *
     * Null until it has been measured, in which case the default is used.
     * The measurement depends on the phone, so it is measured rather than
     * assumed.
     */
    private var measuredTail by mutableStateOf<Double?>(null)

    /** What the tail measurement is doing right now. */
    private var measuringTail by mutableStateOf(false)

    /** What the last measurement found, for the screen. */
    private var tailMessage by mutableStateOf("")

    /**
     * What the last clearing of the learned counts did, for the screen.
     *
     * The reset is not something the coach says - it is something the player
     * did on the settings screen - so it gets its own line rather than
     * replacing the coach's message.
     */
    private var learnedMessage by mutableStateOf("")

    private var remaining by mutableStateOf(501)
    private var turns by mutableStateOf(0)
    private var suggestion by mutableStateOf("")
    private var contingency by mutableStateOf("")
    private var message by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var double by mutableStateOf("")
    private var turnDarts by mutableStateOf(0)
    private var turnScore by mutableStateOf(0)
    private var planKind by mutableStateOf("")
    private var correctionAvailable by mutableStateOf(false)

    /**
     * The preferred double that was stored, if there is one.
     *
     * It is not put on the coach until the entitlement is known, because
     * keeping the preferred double between legs and restarts is Pro. The
     * free tier still has the preferred double for the leg it is playing.
     */
    private var storedPreferredDouble by mutableStateOf<Int?>(null)

    /**
     * A debug-only switch for testing the Pro flows without buying Pro.
     *
     * The switch itself lives in the debug source set, so a release build
     * has no code that could turn Pro on. This field is only ever true in a
     * debug build.
     */
    private var testPro: Boolean = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A debug-only way to exercise the Pro flows without a Play
        // purchase. The release build's version of this does nothing.
        testPro = TestProSwitch.applyTestPro(
            applicationContext,
            intent.getBooleanExtra("testpro", false),
        )

        // The phone is at the oche and the screen must not sleep mid-leg.
        //
        // FLAG_KEEP_SCREEN_ON is enough for a foreground activity and needs
        // no permission. A PowerManager wake lock would need
        // WAKE_LOCK permission, and it would be the wrong tool: this keeps
        // the screen on, not the CPU.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // The store is created here, with the application context, rather
        // than as a field initialiser. A field initialiser runs before the
        // activity has been attached to the application, and the store
        // needs the application context - asking for it too early gave a
        // null pointer and killed the app on launch.
        store = SettingsStore(applicationContext)

        audio = AudioEngine(this)
        tts = PiperTts(this)

        audio.onPhrase = { phrase -> onHeard(phrase) }
        audio.onStateChanged = { running -> listening = running }
        audio.onBargeIn = { tts.stop() }

        // A purchase that finishes anywhere in the app turns Pro on.
        billing.onPurchased = {
            scope.launch {
                entitlement = Pro
                refresh()
                store.saveIsPro(true)
                price = billing.price.value
            }
        }

        loadSettings()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F14),
                ) {
                    AppNavHost(
                        remaining = remaining,
                        turns = turns,
                        suggestion = suggestion,
                        contingency = contingency,
                        message = message,
                        double = double,
                        listening = listening,
                        turnDarts = turnDarts,
                        turnScore = turnScore,
                        planKind = planKind,
                        correctionAvailable = correctionAvailable,
                        entitlement = gate,
                        planner = coach.planner,
                        price = price,
                        purchaseError = purchaseError,
                        profileView = profileView,
                        statsView = statsView,
                        measuredTail = measuredTail,
                        measuringTail = measuringTail,
                        tailMessage = tailMessage,
                        learnedAttempts = profileView.totalAttempts,
                        learnedMessage = learnedMessage,
                        onNewGame = { onNewGame() },
                        onUndo = { onUndo() },
                        onPrivacyPolicy = { openPrivacyPolicy() },
                        onPurchase = { buyPro() },
                        onRestore = { restorePurchase() },
                        onSaveProfile = { view -> saveProfile(view) },
                        onOpenProfile = { openProfile() },
                        onOpenSettings = { openSettings() },
                        onMeasureTail = { measureTailForThePlayer() },
                        onResetTail = { resetTail() },
                        onResetLearned = { resetLearned() },
                    )
                }
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }

        handleMeasurementIntent(intent)

        // A hidden way to make the app hear itself, so that the whole loop
        // - speech in, game logic, speech out - can be checked on a device
        // without anyone having to talk to the phone.
        //
        // The phrase is played through the speaker with the microphone gate
        // left open, which is the one thing the coach never does in normal
        // use. That is the point: it proves the recogniser, the parser and
        // the voice all work together.
        if (intent?.getBooleanExtra("selfhears", false) == true) {
            selfHear()
        }

        // A hidden way to measure the wording on the device.
        if (intent?.getBooleanExtra("measure", false) == true) {
            measureLines(
                arrayOf(
                    "You're at 401. Throw treble twenty, treble twenty and treble twenty to leave 221.",
                    "You're at 401. Throw treble twenty, treble twenty, treble twenty.",
                    "401. Treble twenty, treble twenty, treble twenty.",
                    "You said one hundred. Is that right?",
                    "You said one hundred.",
                    "You said 100. Right?",
                    "Is that right?",
                    "You're at 401. Treble twenty, treble twenty, treble twenty to leave 221.",
                    "401. Treble twenty, treble twenty, treble twenty. Leaves 221.",
                    "401. T20, T20, T20.",
                    "401. Treble 20, treble 20, treble 20.",
                    "401. Treble twenty, treble twenty, treble twenty.",
                    "T20, T20, T20. Leaves 221.",
                    "You said one hundred. Right?",
                    "You said 140. Right?",
                    "Right? 140.",
                    "140. Right?",
                    // The new wording, to measure it against the old.
                    "401. Treble twenty, treble twenty and treble twenty.",
                    "401. Treble twenty, treble twenty, treble twenty.",
                    "You said 140. Right?",
                )
            )
        }
    }

    /**
     * Handles the hidden measurement intents.
     *
     * `am start` on an app that is already running delivers the new intent
     * to the existing instance rather than starting a new one, so the
     * check has to be in onNewIntent as well as onCreate. Without this the
     * measurement silently did nothing, because the app is normally left
     * running between measurements.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMeasurementIntent(intent)
    }

    private fun handleMeasurementIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("tail", false)) {
            Log.i("DartsCoach", "Measuring the echo tail")
            measureTail()
        }
        if (intent.getBooleanExtra("route", false)) {
            Log.i("DartsCoach", "Checking the audio route")
            measureRoute()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.stop()
        scope.cancel()
    }

    private fun startListening() {
        Thread {
            if (!audio.loadModels()) return@Thread
            if (!tts.load()) {
                // The coach can still work without a voice, though it cannot
                // read the score back.
            }
            audio.start()
        }.start()
    }

    /**
     * The coach heard something.
     *
     * The answer is spoken away from the thread that captured it, because
     * speaking takes seconds and the capture loop has to keep running.
     */
    private fun onHeard(phrase: String) {
        Thread {
            val turnsBefore = coach.state.turnsPlayed
            val attemptsBefore = coach.learner.stats.totalAttempts
            val said = coach.handle(phrase)
            Log.i("DartsCoach", "Coach says: $said")
            rememberPreferredDouble()
            if (coach.state.turnsPlayed != turnsBefore) {
                // A turn was confirmed, so the coach has learned something.
                persist()
            } else if (coach.pendingMissTarget != null ||
                coach.learner.stats.totalAttempts != attemptsBefore
            ) {
                // A miss was corrected. The coach learned from it immediately,
                // even though the corrected turn is still waiting to be
                // confirmed.
                persist()
            }
            refresh()
            if (said != null) {
                speak(said)
            }
        }.start()
    }

    /**
     * Says something, with the microphone gated while it is talking.
     *
     * The gate is the half-duplex rule: the coach must not hear itself.
     */
    /**
     * Where is the coach's voice going?
     *
     * It matters because barge-in is only safe when the voice is not going
     * through the phone's own speaker. With headphones the coach cannot hear
     * itself at all; through the phone's speaker the echo peaks at 0.21,
     * which is louder than the player, so the coach would cut itself off
     * mid-sentence.
     */
    private fun isOnThePhoneSpeaker(): Boolean {
        val device = outputDeviceForSpeech() ?: return true
        return device.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
    }

    /**
     * The output the coach's voice is going to.
     *
     * The decision is made *before* the audio is played, so there is no
     * playing configuration to ask yet. The device list is the only thing
     * to go on, and it lists the phone's speaker even when the headphones
     * are carrying the audio.
     *
     * Android's own rule settles it: media goes to Bluetooth when a
     * Bluetooth output is available, and only falls back to the speaker
     * when there is none. So a connected A2DP device means the headphones,
     * whatever else is on the list.
     *
     * The playback configurations are still consulted first, because when
     * something *is* playing they name the device exactly. That matters
     * for the case where the player has turned the phone's speaker on
     * deliberately.
     */
    private fun outputDeviceForSpeech(): AudioDeviceInfo? {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        for (config in manager.activePlaybackConfigurations) {
            val info = config.audioDeviceInfo ?: continue
            if (info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            ) {
                return info
            }
        }

        var bluetooth: AudioDeviceInfo? = null
        for (device in manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                bluetooth == null
            ) {
                bluetooth = device
            }
        }
        if (bluetooth != null) return bluetooth

        for (device in manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return device
        }
        return null
    }

    /**
     * The route the coach's voice is actually going to, for the log.
     *
     * This is deliberately separate from the decision above. The decision
     * only has to be right about the echo and barge-in. The log says what
     * Android reported, so a measurement can be tied to the route it was
     * taken on.
     *
     * `AudioManager.getDevices()` lists every output that *could* be used,
     * including the phone's speaker even when the headphones are carrying
     * the audio. It is therefore not enough on its own: the Bose is on the
     * list whether or not it is the active device.
     *
     * `activePlaybackConfigurations` is the honest answer when something is
     * playing. The device list is the fallback before the first utterance.
     */
    private fun describeAudioRoute(): String {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val playback = manager.activePlaybackConfigurations
            .mapNotNull { it.audioDeviceInfo }
            .map { it.type }
            .distinct()
        if (playback.isNotEmpty()) {
            return "playback=${playback.joinToString(",") { routeName(it) }}"
        }

        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.type }
            .distinct()
        return "outputs=${outputs.joinToString(",") { routeName(it) }}"
    }

    /**
     * The route the coach's voice is going to, for the log.
     *
     * The decision and the log are separate on purpose. The decision only
     * has to be right about the echo and barge-in. The log says what the
     * device was, so a measurement can be tied to the route it was taken
     * on.
     */
    private fun routeDescription(): String {
        val device = outputDeviceForSpeech()
        return if (device == null) "no output device" else routeName(device.type)
    }

    /**
     * A one-off spoken line, for testing a route.
     *
     * The normal path is the coach answering a score, which needs somebody
     * to say the score. This says a fixed line instead, so the route can be
     * checked without a second person.
     */
    private fun measureRoute() {
        Thread {
            if (!tts.load()) {
                Log.i("DartsCoach", "The voice would not load")
                return@Thread
            }
            speak("401. Treble twenty, treble twenty and treble twenty.")
        }.start()
    }

    private fun routeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
        else -> "type=$type"
    }

    private fun speak(text: String) {
        val started = System.currentTimeMillis()
        // Ask the route before speaking, because both the barge-in test
        // and the tail depend on where the voice is going.
        //
        // With headphones the coach's voice never reaches the phone's
        // microphone at all, so there is no echo to wait for and the gate
        // can open the instant the audio stops. Through the phone's own
        // speaker there is a little mechanical ringing, and how much
        // depends on the phone, so it is measured rather than assumed.
        val onThePhone = isOnThePhoneSpeaker()
        audio.bargeInEnabled = !onThePhone
        audio.echoTailSeconds = TailSetting.forRoute(measuredTail, onThePhone)
        audio.beginSpeaking()
        try {
            Log.i(
                "DartsCoach",
                "Route: ${routeDescription()} " +
                    "(${if (onThePhone) "phone speaker" else "headphones"}); " +
                    "tail ${(audio.echoTailSeconds * 1000).toInt()}ms; " +
                    "Speaking: $text",
            )
            val seconds = tts.speak(text)
            if (seconds > 0 && !tts.wasInterrupted()) {
                // The speaker is still ringing. Wait for it to stop before
                // listening again.
                Thread.sleep((audio.echoTailSeconds * 1000).toLong())
            }
            Log.i(
                "DartsCoach",
                "Gate open after ${System.currentTimeMillis() - started}ms " +
                    "(speech ${(seconds * 1000).toInt()}ms, " +
                    "tail ${(audio.echoTailSeconds * 1000).toInt()}ms); " +
                    "${audio.dropped} chunk(s) dropped while speaking; " +
                    "levels ${audio.speechLevelSummary()}",
            )
        } finally {
            audio.endSpeaking()
        }
    }

    /**
     * Loads the player's settings and builds a coach that thinks with them.
     *
     * This happens after the screen is up rather than before, because
     * reading the store touches the disk and the coach should not hold up
     * the first frame. The coach starts on the population averages and is
     * replaced once the player's own profile has been read.
     */
    private fun loadSettings() {
        scope.launch {
            val stats = store.readStats()
            val manual = store.readManual()
            val legs = store.readLegs()
            measuredTail = store.readMeasuredTail()
            storedPreferredDouble = store.readPreferredDouble()

            val learner = HitLearner(stats)
            val profile = PlayerProfile.merge(learner.profile(), manual)
            val coach = CoachEngine(
                planner = Planner.build(profile),
                learner = learner,
            )
            coach.legs.addAll(legs)

            this@MainActivity.coach = coach
            profileView = ProfileView.of(learner, manual)
            statsView = StatsView(Stats(legs), learner)
            refresh()

            loadEntitlement()
        }
    }

    /**
     * Saves the player's own settings for the segments they have set.
     *
     * The coach is rebuilt with the new numbers, so the change takes effect
     * on the next suggestion rather than at the next restart.
     */
    private fun saveProfile(view: ProfileView) {
        profileView = view
        scope.launch {
            store.saveManual(view.manual)
            rebuildCoach()
        }
    }

    /**
     * Rebuilds the coach with the current profile.
     *
     * The planner is built from the profile, so this is what makes a
     * changed hit rate take effect.
     *
     * The learner can be handed in, which is how the counts are cleared: the
     * coach holds its learner, so the counts cannot be emptied inside it -
     * a new learner has to take its place.
     *
     * The leg is carried across, so a rebuild in the middle of a visit does
     * not throw the visit away: the score stands, the darts of the turn in
     * play are kept, and the advice is planned again with the new numbers.
     */
    private suspend fun rebuildCoach(learner: HitLearner = coach.learner) {
        val manual = store.readManual()
        val profile = PlayerProfile.merge(learner.profile(), manual)
        val rebuilt = CoachEngine(
            planner = Planner.build(profile),
            learner = learner,
        )
        rebuilt.missedTargetCorrectionAvailable = coach.missedTargetCorrectionAvailable
        rebuilt.state.preferredDouble = coach.state.preferredDouble
        rebuilt.state.remainingScore = coach.state.remainingScore
        rebuilt.state.turnsPlayed = coach.state.turnsPlayed
        rebuilt.state.turnHistory.addAll(coach.state.turnHistory)
        rebuilt.state.currentDarts.addAll(coach.state.currentDarts)
        rebuilt.state.currentSegments.addAll(coach.state.currentSegments)
        rebuilt.legs.addAll(coach.legs)
        coach = rebuilt
        // The advice the coach gave was planned with the old numbers, so it
        // is planned again. Without this the screen would sit empty until
        // the next turn.
        rebuilt.replan()
        refresh()
    }

    /**
     * Works out whether the player has bought Pro.
     *
     * The cached answer is used first, so the app is usable straight away,
     * and then Play is asked for the real one. Play holds the purchase, so
     * its answer is the truth - including for a player who bought on
     * another device.
     */
    private suspend fun loadEntitlement() {
        val cached = store.isPro.first()
        if (cached) {
            entitlement = Pro
            refresh()
        }

        billing.connect()

        if (billing.entitlement.value) {
            entitlement = Pro
            refresh()
            store.saveIsPro(true)
        } else if (cached) {
            // Play could not be reached, or the purchase has been refunded.
            // The cached answer stands for now and Play is asked again next
            // time the app starts.
        } else {
            store.saveIsPro(false)
        }

        price = billing.price.value

        // The entitlement is only known now, so the preferred double that was
        // stored is applied now, rather than when it was read.
        restorePreferredDouble()
    }

    /**
     * Remembers the double the player asked for, so that it can be kept.
     *
     * The coach forgets a double when the score drops below it, and a new
     * game forgets it as well, because a new leg is a new leg. Neither of
     * those is the player changing their mind, so neither of them clears
     * what is remembered here.
     */
    private fun rememberPreferredDouble() {
        val current = coach.state.preferredDouble
        if (current == null || current == storedPreferredDouble) return

        storedPreferredDouble = current
        if (!gate.has(Entitlement.Feature.PERSISTENT_PREFERENCES)) {
            // The free tier plays the leg it is on with the double the
            // player asked for, and does not keep it, so there is nothing
            // to write.
            return
        }
        scope.launch { store.savePreferredDouble(current) }
    }

    /**
     * Puts the stored preferred double back on the coach, if the player is
     * allowed to keep one.
     *
     * The free tier has the preferred double for the leg it is playing. The
     * double that is kept across legs and restarts is Pro, so a player
     * without it does not get the stored one, and does not keep the one that
     * was there.
     *
     * The double is only put back when the coach can actually route to it
     * from the score standing, because a double that is out of reach would
     * only be announced and dropped again on the next suggestion.
     */
    private fun restorePreferredDouble() {
        val preferred = storedPreferredDouble
        if (!gate.has(Entitlement.Feature.PERSISTENT_PREFERENCES)) {
            if (preferred != null) {
                storedPreferredDouble = null
                scope.launch { store.savePreferredDouble(null) }
            }
            return
        }
        if (preferred == null) return

        val reachable = coach.planner.routeToDouble(
            coach.state.remainingScore,
            preferred,
        ) != null
        if (reachable) {
            coach.state.preferredDouble = preferred
            refresh()
        }
    }

    /**
     * Saves what the coach has learned.
     *
     * This is called after a turn is confirmed, so the player's history
     * carries on across restarts rather than starting again from the
     * population averages.
     */
    private fun persist() {
        scope.launch {
            store.saveLearned(coach.learner)
            // The preferred double is kept between legs and restarts in Pro
            // only. The free tier plays the leg it is on with the double the
            // player asked for, and does not keep it.
            //
            // What is saved is the double the player asked for, rather than
            // the one the coach is playing for right now: the coach drops a
            // double when the score drops below it, and that is not the
            // player changing their mind.
            store.savePreferredDouble(
                if (gate.has(Entitlement.Feature.PERSISTENT_PREFERENCES)) {
                    storedPreferredDouble
                } else {
                    null
                }
            )
            store.saveLegs(coach.legs)
            // The counts have moved, so the profile and the statistics have
            // new numbers.
            profileView = ProfileView.of(coach.learner, profileView.manual)
            statsView = StatsView(Stats(coach.legs), coach.learner)
        }
    }

    /**
     * The player asked to buy Pro.
     *
     * The purchase is started and the result comes back through the billing
     * repository, which tells the screen what happened.
     */
    private fun buyPro() {
        purchaseError = null
        scope.launch {
            price = billing.price.value ?: price
            val started = billing.purchase(this@MainActivity)
            if (!started) {
                purchaseError = billing.error.value
            }
        }
    }

    /**
     * The player asked to check whether they have already bought Pro.
     */
    private fun restorePurchase() {
        scope.launch {
            val owned = billing.restore()
            entitlement = if (owned) Pro else NotPro
            refresh()
            store.saveIsPro(owned)
            if (!owned) {
                purchaseError = "No purchase was found on this account."
            }
        }
    }

    /**
     * Makes the app hear itself.
     *
     * The phrase is played through the speaker with the microphone gate left
     * open, so the recogniser hears it and the coach answers. That proves
     * the whole loop - speech in, game logic, speech out - without anyone
     * having to talk to the phone.
     *
     * The gate is left open on purpose. It is the one thing the coach never
     * does in normal use, and that is exactly what makes this a test.
     */
    private fun selfHear() {
        Thread {
            if (!tts.load()) {
                Log.i("DartsCoach", "The voice would not load")
                return@Thread
            }
            // Wait for the models and the microphone to be ready, or the
            // first phrase is heard before the recogniser is listening.
            Thread.sleep(3000)

            val phrases = arrayOf(
                "one forty",
                "yes",
            )
            for (phrase in phrases) {
                Log.i("DartsCoach", "SELF HEAR: '$phrase'")
                tts.speakUngated(phrase)
                // Give the recogniser time to transcribe and the coach time
                // to answer before the next phrase.
                Thread.sleep(4000)
            }
        }.start()
    }

    /**
     * The player opened their profile.
     *
     * The view is rebuilt here rather than being kept up to date as the
     * counts move, so that opening the screen always shows the numbers as
     * they are now.
     */
    private fun openProfile() {
        scope.launch {
            profileView = ProfileView.of(coach.learner, profileView.manual)
        }
    }

    /**
     * The player opened the settings.
     */
    private fun openSettings() {
        tailMessage = ""
        learnedMessage = ""
    }

    /**
     * The player wants to go back to the default echo delay.
     *
     * This forgets the measurement, so the coach uses the value that was
     * measured on a Pixel 7 until the measurement is run again.
     */
    private fun resetTail() {
        scope.launch {
            store.saveMeasuredTail(null)
            measuredTail = null
            tailMessage = "Back to the default."
        }
    }

    /**
     * The player wants the coach to forget what it has learned.
     *
     * The counts are the coach's memory of how the player throws. Clearing
     * them puts the coach back on the population averages, which is what a
     * player wants when the memory is of a game they did not play - or of a
     * test rather than a leg.
     *
     * Two things are deliberately left alone: the player's own rates, which
     * they set themselves, and the legs they have played, which are their
     * history rather than the coach's memory.
     *
     * The leg in play is not thrown away. The score stands where it is and
     * the advice is planned again from the numbers the coach has left.
     */
    private fun resetLearned() {
        scope.launch {
            store.clearStats()

            // The coach holds its learner, so the counts cannot be emptied
            // where they stand. A new learner takes its place and the coach
            // is built round it.
            val learner = HitLearner()
            rebuildCoach(learner)

            // The counts have moved, so the profile and the statistics have
            // new numbers.
            profileView = ProfileView.of(learner, profileView.manual)
            statsView = StatsView(Stats(coach.legs), learner)

            learnedMessage = "The coach has forgotten what it had learned. " +
                "It is back on the average player."
        }
    }

    private fun refresh() {
        remaining = coach.state.remainingScore
        turns = coach.state.turnsPlayed
        suggestion = coach.suggestion
        contingency = coach.contingency
        message = coach.message
        double = coach.state.preferredDouble?.let { "Playing for D$it" } ?: ""
        turnDarts = coach.state.currentDarts.size
        turnScore = coach.state.currentTurnScore()
        planKind = coach.suggestion.lineSequence()
            .firstOrNull { it == "Checkout" || it == "Setup" }
            ?: ""
        correctionAvailable = gate.has(
            Entitlement.Feature.MISSED_TARGET_CORRECTION
        )
        coach.missedTargetCorrectionAvailable = correctionAvailable
    }

    /**
     * Opens the privacy policy.
     *
     * Google requires the policy to be linked from inside the app as well
     * as from the Play listing. A public URL is preferred so it is the same
     * page Play links to. If none is configured, the policy is shown in a
     * plain dialog, because an `android.resource://` URI cannot be opened
     * by an external browser.
     */
    private fun openPrivacyPolicy() {
        val online = getString(R.string.privacy_policy_url).trim()
        if (online.isNotBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(online))
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w("DartsCoach", "Could not open the privacy policy URL", e)
            }
        }

        val policy = resources.openRawResource(R.raw.privacy_policy)
            .bufferedReader()
            .use { it.readText() }
        android.app.AlertDialog.Builder(this)
            .setTitle("Privacy policy")
            .setMessage(stripHtml(policy))
            .setPositiveButton("Close", null)
            .show()
    }

    /** A plain-text version of the bundled policy, for the dialog. */
    private fun stripHtml(html: String): String =
        html
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<head.*?</head>"), "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|li|h1|h2|div)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
            .trim()

    /**
     * Measures the echo tail on the device.
     *
     * Reached by an intent extra, so the number can be measured where the
     * phone actually is rather than on a desk.
     */
    private fun measureTail() {
        Thread {
            val samples = runTailMeasurement()
            if (samples.isEmpty()) return@Thread
            Log.i(
                "DartsCoach",
                "TAIL SUMMARY: mean=${"%.3f".format(samples.average())}s " +
                    "longest=${"%.3f".format(samples.max())}s " +
                    "(the Windows build used 0.35s)",
            )
        }.start()
    }

    /**
     * Measures the echo tail, and returns the samples.
     *
     * The measurement is the same one the hidden intent runs, but it does
     * not save the result: this is the measuring instrument, and the saved
     * number comes from the button in the settings.
     */
    private fun runTailMeasurement(): List<Double> {
        if (!tts.load()) {
            Log.i("DartsCoach", "The voice would not load")
            return emptyList()
        }

        val minBuffer = android.media.AudioRecord.getMinBufferSize(
            16000,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) {
            Log.i("DartsCoach", "AudioRecord would not start")
            return emptyList()
        }
        val recorder = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, 4096 * 4),
        )
        if (recorder.state != android.media.AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.i("DartsCoach", "AudioRecord did not initialise")
            return emptyList()
        }
        recorder.startRecording()

        // Three phrases, so the answer is not one sample.
        val phrases = arrayOf(
            "You're at 401. Treble twenty, treble twenty, treble twenty.",
            "One hundred and forty. Is that right?",
            "That leaves one hundred and eighty one.",
        )
        val tails = mutableListOf<Double>()
        try {
            for (phrase in phrases) {
                val tail = tts.measureTail(phrase, recorder)
                tails.add(tail)
                Log.i("DartsCoach", "TAIL for '$phrase' was ${"%.3f".format(tail)}s")
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        return tails
    }

    /**
     * Measures the echo tail and saves it.
     *
     * This is the one the player runs from the settings. It saves the
     * result, so the coach waits the right amount on this phone from then
     * on, and it can be run again if the phone is moved somewhere noisier.
     */
    private fun measureTailForThePlayer() {
        if (measuringTail) return
        measuringTail = true
        tailMessage = "Measuring..."

        Thread {
            try {
                val samples = runTailMeasurement()
                if (samples.isEmpty()) {
                    tailMessage = "The measurement could not run."
                    return@Thread
                }

                val chosen = TailSetting.fromMeasurement(samples)
                scope.launch {
                    store.saveMeasuredTail(chosen)
                    measuredTail = chosen
                }
                tailMessage = "Measured: ${TailSetting.describe(chosen)}"
                Log.i(
                    "DartsCoach",
                    "TAIL MEASURED: ${"%.3f".format(chosen)}s from ${samples.size} sample(s)",
                )
            } catch (e: Exception) {
                Log.w("DartsCoach", "The tail measurement failed", e)
                tailMessage = "The measurement failed."
            } finally {
                measuringTail = false
            }
        }.start()
    }

    /**
     * Measures how long each line takes to say.
     *
     * Reached by an intent extra, so the wording can be tuned on the device
     * rather than by eye. Nothing in the app's normal flow calls it.
     */
    private fun measureLines(lines: Array<String>) {
        Thread {
            if (!tts.load()) {
                Log.i("DartsCoach", "The voice would not load")
                return@Thread
            }
            for (line in lines) {
                val seconds = tts.measure(line)
                Log.i("DartsCoach", "MEASURE ${"%.2f".format(seconds ?: 0.0)}s  $line")
            }
        }.start()
    }

    private fun onNewGame() {
        Thread {
            coach.handle("new game")
            coach.handle("coach yes")
            // A new leg is a new leg, so the coach forgets the double. The
            // double the player asked for is put back for the new leg, if
            // they are allowed to keep one.
            restorePreferredDouble()
            refresh()
        }.start()
    }

    private fun onUndo() {
        Thread {
            coach.handle("undo")
            coach.handle("coach yes")
            refresh()
        }.start()
    }
}
