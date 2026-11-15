# Where this is, and what is left

Written 13 November 2026, at a natural stopping point. Extended 14 November
2026, after the missed-target correction was proved over the whole board.

## What works

The coach runs on a Pixel 7, with no laptop and no network.

```
Heard: '140'
Coach says: You said one hundred and forty. Right?
Heard: 'Yes.'
Coach says: 361. Treble twenty, treble twenty and treble twenty.
Heard: 'What does that leave?'
Coach says: That leaves 221.
```

Everything is on the device: the microphone, the speech recognition, the
game logic, the voice. The coach needs no network at all.

The one exception is the Pro purchase, which is why the app now declares the
`INTERNET` permission. It is used for exactly one thing, talking to Google
Play about the purchase. The microphone audio is processed in memory and never
sent anywhere.

| | |
|---|---|
| **Speech to text** | Whisper `base.en` int8, sherpa-onnx. RTF 0.11-0.20. |
| **Voice** | Piper `en_US-lessac-medium` int8, sherpa-onnx, with piper-phonemize. |
| **Speech detection** | silero-vad. |
| **Game logic** | A Kotlin port of the Python `src/game/`, verified against 7663 cases. |
| **The gate** | Measured, not guessed. See `docs/TAIL.md`. |

## The numbers that were measured

| | |
|---|---|
| Recognition speed | RTF 0.11-0.20 on four threads |
| The coach's voice at the microphone, phone speaker | peak 0.21, mean 0.0014 |
| The coach's voice at the microphone, headphones | peak 0.0019 - below the room |
| A player speaking over the coach | mean 0.052-0.055 |
| The echo tail, phone speaker | 447-835 ms at the oche; 50-100 ms on a quiet desk |
| The echo tail, headphones | 0 ms - the voice never reaches the microphone |
| The suggestion | 3.5 s of speech, down from 5.5 s |

## The two things that are configured from the audio route

The app asks Android where the audio is going, and both of these come from
that answer:

| Route | Echo tail | Barge-in |
|---|---|---|
| The phone's own speaker | 0.35 s | off |
| Bluetooth headphones | 0 s | on |

Barge-in is off on the phone's speaker because the echo there is louder than
the player, so the coach would cut itself off. With headphones the voice
never reaches the microphone at all, so barge-in is safe.

Both routes have now been measured on the running app. See
`docs/BLUETOOTH.md` for the headphone route and `docs/TAIL.md` for the
speaker.

## What is left

### 1. The headphone route - done

The Bose was connected over A2DP on 13 November 2026 and the route was
measured on the running app:

```
DartsCoach: Route: bt_a2dp (headphones); Speaking: 401. ...
DartsCoach: Gate open after 4759ms (speech 3931ms, tail 0ms)
```

The echo tail on the headphone route is 0 ms for all three phrases, and the
room floor and the speech level at the microphone are both about 0.00035:
the coach's voice never reaches the microphone at all. So the headphone
path - zero tail, barge-in on - is now measured rather than assumed.

The measurement also found a bug in how the app picked the route. It walked
the device list and stopped at the phone's speaker, which is on the list
even when the headphones are carrying the audio. The app now follows
Android's own rule: a connected A2DP output means the headphones, and the
speaker is the fallback. The route is logged on every utterance.

To check it again:

```
adb shell am start -S -n com.jr12866.dartcoach/.MainActivity --ez route true
adb logcat -s DartsCoach
```

It has to say `bt_a2dp (headphones)`. If it says `speaker`, the headphones
are not carrying the audio and the app will (correctly) use the
phone-speaker settings.

See `docs/BLUETOOTH.md`.

### 2. The echo tail at the oche - the number changed

The tail was measured on a desk in a quiet room, where it came out at 50 -
100 ms and the app used 0.10 s.

Measured again on 13 November 2026 on the running app, with the route
logged, the two routes gave:

| Route | Room floor | Speech level at the mic | Tail |
|---|---|---|---|
| Bluetooth headphones | 0.0003 | 0.0003 - the same as the room | **0 ms** |
| The phone's own speaker | 0.0004 | 0.0033 - 0.0060 | **447 - 835 ms** |

**The app now uses 0.35 s on the phone's speaker**, the same value the
Windows build used. It covers the shorter half of the measured range, and
the cost of waiting a little too long is much smaller than the cost of the
coach hearing its own reply.

What is still missing is a measurement standing at the board, where the room
is not a quiet desk. That is the next thing to do with the phone.

The measurement is a hidden intent:

```
adb shell am start -S -n com.jr12866.dartcoach/.MainActivity --ez tail true
adb logcat -s DartsCoach
```

Note the `-S`. Without it the activity is not restarted and the intent is
delivered to the running instance, which is handled but is easier to reason
about with a clean start.

The tool dumps the level every 10 ms, so the curve is in the log too. That
curve is what actually showed the answer - the summary alone was misleading
twice, because the room is noisy enough to produce spikes well above its own
floor.

**To measure the phone speaker, switch Bluetooth off first.** With the Bose
connected the tail is 0 ms because the voice never reaches the microphone,
and that is not a measurement of the speaker:

```
adb shell svc bluetooth disable
```

Put it back afterwards with `adb shell svc bluetooth enable`.

### 3. Phase 5 - the Play Store

The repository work is done. What is left needs the Play Console account and
a real device.

Done in the repository:

- A `release` signing configuration in `app/build.gradle.kts`, reading the
  keystore path and passwords from `local.properties`. The keystore itself
  has to be generated and kept outside the repository; `*.jks` is ignored.
  **This is done now**: the keystore is at
  `C:/Users/jr128/dart-coach-keys/dart-coach-release.jks`, alias `dart-coach`,
  and the four properties are in `local.properties`. The passwords are in
  `local.properties` and nowhere else, so they need copying somewhere safe.
- The install-time Play Asset Delivery pack, and the app-bundle publishing
  configuration. `bundleRelease` builds an `app-release.aab` of about
  140 MB, with the models in the `models` pack. **The signed bundle is
  built and its signature verified.**
- The store copy, the icon, and the feature graphic, generated by
  `tools/generate-store-assets.py` into `docs/store/`.
- Two real screenshots, in `docs/store/screenshots/`.
- The privacy policy, in three places: `docs/PRIVACY-POLICY.md`, the public
  page `docs/store/privacy-policy.html`, and a copy bundled in the app with a
  **Privacy policy** button.
- The Data safety answers, in `docs/STORE-LISTING.md`.
- **`docs/CONSOLE-SETUP.md`**, the runbook for the console: every field, in
  the order it has to be filled in, with the value for each one.

Still manual:

- The developer account and the Play Console app entry.
- Replace the example privacy email in both privacy-policy copies.
- Publish the policy at a public URL and enter it in Play Console and in
  `app/src/main/res/values/strings.xml`.
- Take real phone screenshots and upload them.
- Fill in the content rating questionnaire and the Data safety form.
- Upload the bundle to **internal testing**, enable Play App Signing, and run
  the pre-launch report.
- Create the `dartcoach_pro` one-time product, its purchase option and its
  price. **The upload comes before this**: Play will not let a product be
  created for an app with no bundle in a track.
- Test the purchase, and test the refund.

Two things about the timing, because they are easy to be surprised by:

**The product is a one-time product now.** It used to be an in-app product.
The price lives in a purchase option inside it, and it has to be activated
before it can be bought.

**A new personal developer account cannot go straight to production.** If
the account is personal and was created after 13 November 2023, Play
requires a closed test with at least 12 testers opted in continuously for 14
days, and then an application for production access. That is four to six
weeks of calendar time, and it can start as soon as the internal test is
stable - it does not have to wait for everything else.

`docs/CONSOLE-SETUP.md` has all of it in the order it has to be done.

The Data safety answers are in `docs/STORE-LISTING.md`, and they have moved
on since the app was first packaged. Google's definition of "collected" is data
sent off the device. The app now declares `INTERNET`, for the Pro purchase, so
the answer is **purchase history is collected and nothing else** — the
microphone audio is still never sent, so it is still not declared. The
microphone permission and the privacy policy still describe the recording
accurately.

The models ship as an install-time Play Asset Delivery pack. Measured on the
built bundle: the pack is 172 MB on the phone, the download is about 130 MB,
and the app occupies about 240 MB on the device. The limits are in
`docs/PUBLISHING.md`.

### 4. Missed-target corrections - device tested, hardening remains

The Pro missed-target correction is implemented and tested on a Pixel 7. The
player reports what landed instead of the intended dart, and the coach replaces
the last dart and re-plans only the darts remaining, labelled as a Checkout or
a Setup.

Device testing found and fixed two state-machine problems:

- a correction spoken while the score read-back was pending was discarded;
- a correction before the third dart created a false score read-back.

Whisper also wrote "treble" as "trouble" on the device, so the parser now
accepts that transcription variant.

**Extended 14 November 2026.** The whole board and the checkout branch are
now proved in core tests, in `MissedTargetBoardTest`. That work found two
more bugs, both of which the device test could not have reached:

- **the bull could not be named.** "I hit the bull instead" was not
  recognised as a correction, and the coach said nothing at all. The bull
  is the one part of the board with a name rather than a number.
- **a missed target was read as the landing.** "I missed double 16" told
  the player they had hit the very double they said they had missed. The
  landing and the target are now told apart by the verb in front of them.

Two more changes came out of the same work:

- **a correction-shaped phrase with no landing is now asked for**, rather
  than ignored. "I hit single eat instead" used to produce silence; the
  coach now says "I didn't catch which dart you hit. Please repeat."
  The turn is left alone, so a refused correction cannot change a score.
- **the debug test-Pro switch is out of the production source.** It is in
  `src/debug` now, and the release build's version returns false
  unconditionally. This was checked in the compiled class, not assumed.

The remaining work is the real Play purchase test, the noisy-room test of
the spoken forms, and the product decisions - whether a confirmed turn can be
corrected, and which dart in the turn was missed. See `docs/MISSED-TARGET.md`
for the full findings and acceptance checklist.

## The repositories

| | |
|---|---|
| `C:\git\dart-coach-android` | This one. Kotlin, Android, the phone app. |
| `C:\git\darts-coach-windows` | The original. Python, the laptop coach, still working. |

They are separate because the toolchains are, and because the Windows build
is a working product that must not be disturbed.

**The Kotlin core is a port of the Python one, and the parity corpus is what
checks it.** If the Python behaviour changes deliberately, regenerate the
corpus and read the diff:

```
cd C:\git\darts-coach-windows
python tools\parity\parity_gen.py --out C:\git\dart-coach-android\corpus
cd C:\git\dart-coach-android
.\gradlew.bat :core:test
```

The corpus is the specification. The Windows test suite stays the reference.

## Building

```
sdkmanager "ndk;27.2.12479018" "cmake;3.31.6"
git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx.git third_party/sherpa-onnx
.\tools\build-sherpa.ps1
.\tools\fetch-models.ps1
.\gradlew.bat :app:assembleDebug -PwithLocalModels
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The `-PwithLocalModels` is needed for local builds: the Play Asset Delivery
pack only reaches the device through the Play Store, and a debug APK
installed with `adb install` never gets it.

The flag is **not** used for the release bundle. A release build ships the
models in the asset pack and nowhere else; if they are also copied into the
base module, the two modules claim the same asset and the bundle build
fails. `bundleRelease` therefore ignores the flag.

`docs/BUILDING.md` has the details, including the four version constraints
that all have to be right at once.

## Three hidden measurement tools

All are reached by an intent extra, and all exist so the numbers can be
measured on the device rather than by eye.

```
# how long each line takes to say
adb shell am start -S -n com.jr12866.dartcoach/.MainActivity --ez measure true

# the echo tail
adb shell am start -S -n com.jr12866.dartcoach/.MainActivity --ez tail true

# which audio route the coach's voice is using
adb shell am start -S -n com.jr12866.dartcoach/.MainActivity --ez route true
```

## Where the documentation is

| | |
|---|---|
| `PLAN.md` | The plan, and where each phase stands. |
| `docs/TAIL.md` | The echo tail measurement, and the two wrong attempts. |
| `docs/PHASE1-FINDINGS.md` | The recognition measurements. |
| `docs/PHASE3-ECHO.md` | The first echo measurement, through the phone speaker. |
| `docs/GATE-AND-BARGE-IN.md` | The gate and barge-in, and how they nearly went wrong. |
| `docs/BLUETOOTH.md` | Bluetooth, and why the microphone stays on the phone. |
| `docs/PARITY.md` | How the port is checked against the Python. |
| `docs/BUILDING.md` | Building sherpa-onnx. |
| `docs/PUBLISHING.md` | The Play Store. |
| `docs/CONSOLE-SETUP.md` | The runbook for the Play Console: every field, in order, with the value for each one. |
| `docs/STORE-LISTING.md` | The store copy, graphics, and Play Console answers. |
| `docs/MISSED-TARGET.md` | The Pro missed-target correction: implementation, Pixel 7 findings, and what is still to do. |
| `docs/PRO-PLAN.md` | The paid tier: what is built, what is gated, and what is left to ship. |
| `docs/ROLLBACK.md` | How to make the app free again if the paywall turns out to be wrong. |
| `docs/PRIVACY-POLICY.md` | The privacy policy. |
| `docs/DECISIONS.md` | The decisions, and what would reverse each. |
| `docs/MEASURING-TAIL.md` | How to run the tail measurement. |