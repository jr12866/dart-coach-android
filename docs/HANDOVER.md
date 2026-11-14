# Where this is, and what is left

Written 13 November 2026, at a natural stopping point.

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
game logic, the voice. The app requests no network permission.

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
- The install-time Play Asset Delivery pack, and the app-bundle publishing
  configuration. `bundleRelease` builds an `app-release.aab` of about
  145 MB, with the models in the `models` pack.
- The store copy, the icon, and the feature graphic, generated by
  `tools/generate-store-assets.py` into `docs/store/`.
- Two real screenshots, in `docs/store/screenshots/`.
- The privacy policy, in three places: `docs/PRIVACY-POLICY.md`, the public
  page `docs/store/privacy-policy.html`, and a copy bundled in the app with a
  **Privacy policy** button.
- The Data safety answers, in `docs/STORE-LISTING.md`.

Still manual:

- The developer account and the Play Console app entry.
- Generate the release keystore, put the four signing properties in
  `local.properties`, and build `bundleRelease`.
- Replace the example privacy email in both privacy-policy copies.
- Publish the policy at a public URL and enter it in Play Console and in
  `app/src/main/res/values/strings.xml`.
- Take real phone screenshots and upload them.
- Fill in the content rating questionnaire and the Data safety form.
- Upload the bundle to **internal testing**, enable Play App Signing, and run
  the pre-launch report.

One correction to the old handover text: Google's definition of "collected"
is data sent off the device. Because the app has no `INTERNET` permission,
the Data safety answer is **no data is collected**, not "microphone data is
collected". The microphone permission and the privacy policy still describe
the recording accurately.

The models ship as an install-time Play Asset Delivery pack, so the app
installs at about 190 MB. The limits are in `docs/PUBLISHING.md`.

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
| `docs/STORE-LISTING.md` | The store copy, graphics, and Play Console answers. |
| `docs/PRIVACY-POLICY.md` | The privacy policy. |
| `docs/DECISIONS.md` | The decisions, and what would reverse each. |
| `docs/MEASURING-TAIL.md` | How to run the tail measurement. |