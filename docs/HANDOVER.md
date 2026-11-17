# Where this is, and what is left

Written 13 November 2026, at a natural stopping point. Extended 14 November
2026, after the missed-target correction was proved over the whole board.
Extended 17 November 2026, when the screens were given a help panel.

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

### 5. Clearing what the coach has learned - done

The coach learns how well the player throws from the darts they report, and
that memory is only as good as what went into it. A misheard game, or a test
rather than a leg, leaves the coach believing something untrue - and it
believes it firmly, because six misses at the treble twenty is enough to
move the coach off the treble twenty at 361 and onto the treble nineteen.

The settings screen now has a **Forget what I've learned** button, under
"What the coach has learned". It says how many darts the coach has learned
from, asks before doing it, and then:

- clears the learned counts from the store;
- builds the coach again with a fresh learner, so the advice is planned from
  the population averages rather than the old counts;
- plans again for the score standing, so the suggestion on the screen is
  advice the coach still believes.

Three things are deliberately left alone: the player's own rates, their leg
history, and the leg in play - the score stands where it is.

The core has one new method for this, `CoachEngine.replan()`, and one new
test file, `ReplanTest`. The reset was proved on a Pixel 7: eight learned
misses were cleared with the button, the store came back with no counts in
it, and the coach went back to "361. Treble twenty, treble twenty and treble
twenty."

**The same session found why the counts went wrong in the first place.** When
Whisper writes the target in a form the parser cannot read - it wrote "missed
treble 20" as "mist-traveled 20" - the coach fell back on the segment the
player had reported for that dart, which by then was the dart that had
*landed*. So the coach learned "the player aims at the single eight and hits
it" out of a correction that was entirely about the treble twenty. Eight
learned darts later, the coach was aiming at the treble nineteen at 361
instead of the treble twenty. The fix is in `docs/MISSED-TARGET.md`, problem
10, and the test is `a correction with an unreadable target is learned
against the aim`.

### 6. The contingency - the coach says what happens if it comes off as a single

The planner has always worked out what happens if the first dart of a plan
drops into its single bed. It is why a leave of 40 is better than a leave of
38: a missed double twenty still leaves 20 for double ten, while a missed
double nineteen leaves 19, which no double can take. But it kept it to
itself, so the coach recommended a treble without saying what happens when
it comes off as a single.

At 388 with two darts left the plan is two treble twenties. If the first
comes off as a single twenty the player is on 368, where a hundred and
eighty leaves 188 - no out - while the score the coach planned for leaves
268. The planner knew; it did not say.

**The coach now says it.** The plan carries a `Contingency`: the score
standing if the first dart drops, and the darts to throw for the rest of the
turn instead. The screen shows it under the plan ("If it drops to the single:
333, then T20"), and the coach says it when it re-plans the darts left in a
turn - which is the missed-target correction, the one moment the question is
live.

Two things were needed:

- `Planner.planFor` only worked out the fallback when there was more than one
  dart left. A turn with one dart left has no darts to fall back on, but it
  has a score to fall back on, and that is the case the player needs: the
  dart either leaves the score the coach planned for or it does not.
- The advice itself did not change. The parity corpus is green as it was,
  because the corpus pins what the coach tells the player to throw, not what
  it says about it.

**The Python build was changed to match**, in `src/game/darts.py` and
`src/llm/reasoner.py`: the same `Contingency`, and the same line on the
screen. The Windows coach has no partial-turn planning, so the mid-turn
voice is Android-only - it belongs to the Pro missed-target correction, which
is Android-only too. The two coaches still give the same advice.

**The sideways miss is told as well.** There is a second way for a treble to
miss: instead of falling straight out of the treble bed into its own single,
the dart can cross a wire and land in the single bed of a neighbouring
sector. Which sector depends on where the sector sits on the board, and
`core/.../Board.kt` holds that geometry - the sectors of a standard board,
clockwise from twenty, and the two either side of any bed. T20's neighbours
are 5 and 1, T19's are 3 and 7, D16's are 7 and 8.

The screen says it under the drop into the bed ("If it slides into 1 or 5:
352 or 348"). The advice did not move: the wire is not in the planner's
search. Adding it would move the advice at 51 to 108 of the 500 plans,
depending on the share of misses that slide sideways - fifteen percent of
misses into each neighbour gives 51 plans changed, thirty percent gives 93,
forty-five percent gives 108 - so the advice would be riding on a constant
nobody has measured. The wire is on the screen and not in the spoken line,
because the spoken correction line is already 10.7 s and a sideways miss is
the less likely of the two.

**Measured on the Pixel 7.** The correction line with the contingency is
10.7 s of speech and the gate is closed for 14.2 s. That is long, and it is
the one number to watch: the line before the contingency was 5.5 s. The
suggestion at the start of a turn is unchanged at 3.5 s, because the plan for
a whole turn is robust - if the first dart drops, the second and third are
still worth throwing.

### 7. The coach learns where the misses go - done

The wire miss is the one thing the board can say and the player's own play
can confirm. The coach now keeps the two together: when the player corrects
a dart, the coach remembers **where the dart actually landed**, not just that
it missed.

A dart aimed at the treble twenty that lands in the single five has gone
sideways; one that lands in the single twenty has dropped short. Those are
different problems and they need different answers, so the counts are kept
per aimed segment:

- `HitStats` gained `landingCounts`: the segment that was aimed at, then the
  segment the dart landed on, then how many times.
- `HitLearner.wireMisses(segment)` reads back the two beds either side of
  the target - the sideways misses - which is the number the planner would
  need.
- The counts are saved with the other learned counts, as
  "aimBed,aimMultiplier,landBed,landMultiplier,count". The field is
  optional, so a phone that saved its settings before this existed still
  loads them.
- **Forget what I've learned** clears them with everything else, and the
  profile screen shows them under **Where your misses go**.

**The advice did not change.** The wire is still not in the planner's search,
because the share of misses that slide sideways is still a guess. What the
learning buys is the fact that replaces the guess, once there is enough of
the player's own play to replace it with.

**This is Android-only.** The Windows coach does not learn anything at all -
it has no hit learner - so there is nothing to mirror there. The board's
geometry is mirrored, because the Windows build shares the darts model.

### 8. The bull rate - the default was twice the professional rate

The coach had always believed it could hit the double bull half the time.
That number was a guess with nothing behind it, and it was a guess that was
too generous: published professional figures put the inner bull at 20-25% for
a professional and around 30% for the very best, so 0.50 was above any player
who has ever lived. It is now **0.225**, the middle of the published band.

The number mattered more than it looked, because the bull is one of the few
targets the coach can plan a leg around - 170 is finished with T20 T20 Bull
and nothing else. An optimistic bull rate made the coach steer players into
bull finishes they would then miss.

**What changed, measured over the 501-plan parity corpus:**

| | |
|---|---|
| Plans that changed | 138 of 501 |
| The setup left at 321 | was T20 T17 D20 leaving 170 (a bull finish), now **T20 T20 T16 leaving 153**, finished T20 T19 D18 |
| The setup left at 350 | was the classic 170 out, now T20 T20 T19 leaving 173 |
| Scores that leave 170 | none - the coach no longer sets anyone up to finish on the bull |
| Checkouts in the 100-135 range | moved off the bull onto ordinary doubles - 135 is now T18 T17 D15 rather than T20 25 Bull |

**The order of work was the usual one:** the Python coach first
(`darts.py`, `DB_HIT`), then the parity corpus regenerated, then the Kotlin
port (`PlayerProfile.DEFAULT_DB_HIT`), and the corpus test proved the three
coaches still agree. The five tests that pinned the old advice were updated
to the new one, and the generated `scoring.md` and `skill.md` were brought
round in both repositories.

**The treble curve was left alone on purpose.** Only the treble twenty and
the double bull have published figures, and the model's treble curve descends
with the bed number, which is how often a bed is thrown at rather than how
often it is hit - all the treble beds are the same size, so a real hit rate
should be much flatter. Fixing that needs per-bed data, so it is separate
work rather than a number to guess at.

**The player can now set the bull rate by hand.** The profile screen takes the
bull to 40% and the coach plans around it: at 350 the setup chart leaves the
classic 170 out - T20 T20 T20, finished T20 T20 Bull. The setting is stored, and
it survives a reinstall. Proved on a Pixel 7 on a debug build with the built-in
test-Pro switch (`am start -n com.jr12866.dartcoach/.MainActivity --ez testpro
true`), which is also the build AVG AntiVirus flags as suspicious.

**The profile dialog had a broken string.** `"How often do you hit $segment.label?"`
parses as `$segment` followed by the literal text `.label`, so the dialog printed
the whole `SegmentView(...)` to the screen. It now reads `${segment.label}` and
asks "How often do you hit Bull?".

### 9. The learner was dividing one count by another

The hit learner keeps two counts for every segment: how many darts were
**aimed** at it, and how many darts **landed** on it. They are different
populations. A dart aimed at T16 that slides into T20 is a hit on T20 and a
miss on T16.

The rate the coach learned was

```
(prior * 15 + darts that landed on the segment) / (15 + darts aimed at it)
```

which is a ratio of two unrelated numbers. The effect is small but always in
the same direction: a segment that other targets happen to land on looks
better than the player is, and a segment the player misses looks worse.

It was found by building the web coach, where the page has to *show* what the
coach has learned. The panel said "T20 2/2, 63%" and the two numbers did not
agree: the player aimed at T20 twice and hit it twice, and the coach had
worked out 63% rather than the 62% the prior and the two hits make. The
extra came from the third dart, which was aimed at T16 and landed on T20.

**The fix** is the numerator: the rate now counts the darts that went where
they were aimed, which is what the planner's `pHit` means. It is one line in
`HitStats.rate()`, it is the shared `onTarget` count that was already being
kept for exactly this purpose, and the statistics screen was already using
it. Two tests were added to pin it.

| | |
|---|---|
| Where | `core/.../DartThrow.kt`, `HitStats.rate()` |
| Effect | Treble, double and bull rates are now the share of darts aimed at a segment that landed on it |
| Effect on the advice | None for an unmeasured player: the defaults are unchanged and the corpus still passes |
| Effect on a measured player | Rates move by a few points, in the direction of the truth |
| Python | No change. The learner has never existed in the Windows coach, so there is no parity corpus entry for it |

**One wart was still there, and it is not in the learner.** `PlayerProfile.merge`
takes the player's own settings over the learned ones, which is right for the
trebles and the doubles - they are maps, and the player's number wins for the
beds they have set. The bull and the singles are plain numbers rather than
maps, so there is no way to say "not set", and the merge always takes the
player's side of them. **A learned bull rate therefore never reached the
planner**, on the phone or in the browser, which made the last paragraph of
decision 16 untrue as it stood.

**The plain numbers are fixed now.** The bull, the outer bull and the singles
are plain numbers rather than maps, so they can now say "not set": the merge
takes the player's own number if there is one and the learned number if there
is not, and the planner falls back to the population average when there is
neither. The profile screen writes each of them into the field the planner
reads, and the settings store saves them and reads them back, so a player
who says they hit the bull get the classic 170 advice back: at a bull rate of
about 27% and above the coach leaves 170 at 350, 347 and 344, and below that
it sets up instead.

**What that bought.** A learned single rate now reaches the planner, which it
never did: the coach used the population average for every single dart
whatever the player's own play said. The singles are pooled - one number for
all twenty beds - so the screen shows the same rate on every single row, and
setting any one of them sets the pooled rate.

**One thing was still not learned, and now is.** The outer bull was not in
the learner, though the double bull was and the counts were kept for it, so
its rate sat at the population average unless the player set it by hand. The
learner now reads the outer bull's rate out of those counts, the same way it
reads the double bull's, so a player who finds the outer bull gets it priced
in and a player who never finds it gets it priced out. It is not pooled with
the singles, because it is a bull and not a bed.

**Nothing is left in the plain numbers.** The bull, the outer bull and the
singles all say "not set" now, all three are learned and all three can be
set by hand, and all three are saved and read back.

### 10. Nothing explained the screens - the "?" panel

The coach explains a score, but nothing explained the app. A new player lands
on the coach screen and has to work out for themselves what Out chart, Setup,
Your profile and Your statistics are for, and what the coach expects it to
hear. There was no help anywhere in the app, and no first-run tutorial either.

The fix is a **?** button next to each screen's title, and a short panel
behind it. Six topics, one per screen: the coach, the out chart, the setup
chart, the profile, the statistics and the settings. Each one is four or five
points, a heading and two or three lines of plain text, in the app's own
voice.

Why a "?" and not a Help label: the player is at the oche with a score to
shoot, and the button has to be as quiet as possible. Why a dialog and not a
screen or a carousel: the same reason - the guide has to be one tap away and
one tap back. The button owns its own open state, so no screen had to carry a
"help is open" flag through navigation.

| | |
|---|---|
| Where | `app/.../ui/HelpPanel.kt` |
| Topics | Six, one per main screen |
| Test | `app/.../ui/HelpPanelTest.kt` |
| Checks | Every topic has a title and at least three points; every point has a heading and a body; bodies under 220 characters, headings under 40 |
| Also checked | A source scan asserts that each of the six screens really carries its button, so a screen cannot quietly lose its help |

**One defect was found while putting the button in.** The app targets SDK 35,
and Android 15 enforces edge-to-edge, so every screen's header was running
under the status bar - the out-chart title sat under the clock. It was
invisible on the coach screen, where the header is the score, and obvious on
the nav screens. The root column of all seven screens now carries a status
bar padding. The app is pre-release, so this was cheap to correct now.

Verified on the Pixel 7: the button and the panel were opened on all six
screens.

Not built, deliberately: a first-run tutorial carousel, and a coached practice
turn. The read-back wording - "You said one hundred and forty" could become
"That makes you three hundred and seventy-six" - was recommended as the
highest-value onboarding change and has **not** been done.

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
| `docs/LEAVE-QUESTION.md` | What "what does that leave?" answers, and what it counts. |
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