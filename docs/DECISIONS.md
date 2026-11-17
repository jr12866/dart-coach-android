# Decisions

Why this is built the way it is. Each entry records a choice that was not
obvious, what the alternatives were, and what would reverse it.

---

## 1. Native Android with sherpa-onnx, not Python on Android

**Decision.** A Kotlin Android app. sherpa-onnx provides the VAD, the ASR and
the TTS.

**Alternatives considered.**

| Option | Why not |
|---|---|
| Termux / Pydroid, running the existing Python | `faster-whisper` (CTranslate2) and `piper-tts` have no Android wheels. A Termux build still needs native ONNX Runtime plus a CTranslate2 equivalent, plus a resampler and an audio backend. More work than writing the app, and it leaves the game logic in the language with the weakest Android story. |
| Flutter / React Native | Adds a layer over native audio and ML bindings without removing any of the underlying work. |
| Keep the laptop as the engine, phone as client | Cheapest by far, and it is what `src/phone/server.py` already does. But it does not meet the goal: the coach must run with no laptop. |

**What would reverse it.** If sherpa-onnx could not be built for arm64, or if
the ASR quality on the device turned out to be unusable, then a Termux build
with `whisper.cpp` and a separate Piper build would be the fallback.

---

## 2. The game logic is ported, not rewritten

**Decision.** `src/game/` is ported to Kotlin file for file, and checked
against the Python original by a parity harness.

**Why.** The Windows `src/game/` is pure Python with no native dependencies -
only `re`, `json`, `dataclasses`, `pathlib` and `typing`. It is the entire
value of the coach, and it has been tuned through a lot of live testing
(the confirmation rules, the `one eighteen` disambiguation, the bogey
numbers). Rewriting it in Kotlin from scratch would mean re-deriving all of
that from nothing, and there would be no way to tell a correct port from a
plausible one.

**How it is verified.** `docs/PARITY.md` describes the corpus format. The
Python side generates the corpus; the Kotlin side asserts against it.

---

## 3. No LLM on the phone

**Decision.** The coach phrases its suggestions from the search in
`Darts.kt`. No language model runs on the device.

**Why.** This is already how the Windows coach behaves: `--use-llm` is off by
default, and `src/llm/reasoner.py` says so plainly - *"The plan says it all;
the model only adds two seconds and the risk of saying something else."* The
numbers come from the checkout and setup tables, not from a model. Porting the
behaviour means porting the behaviour, not adding a capability.

**What would reverse it.** If there were a use for a model that the tables
cannot serve - free-form advice, a difficulty curve that adapts to how the
player is throwing - a small on-device model (MediaPipe LLM Inference or
llama.cpp) would be feasible on a Pixel 7. That is scope creep, not a
requirement.

---

## 4. sherpa-onnx rather than whisper.cpp

**Decision.** sherpa-onnx for all three models.

**Why.** It is one library rather than two or three, it has official Kotlin
APIs rather than a JNI wrapper written for the project, and it provides the
VAD (silero-vad) alongside the ASR and TTS. The VAD matters: the Windows coach
uses a hand-rolled energy VAD which took several phases of measurement to get
right (`update_summary.md` steps 12, 19, 20). A learned VAD is a different
kind of thing and removes a whole class of tuning work.

`whisper.cpp` has a good Android story too, and `examples/whisper.android` is
a working sample, so this was close. But it would have meant whisper.cpp for
ASR and something else for VAD and TTS.

**What would reverse it.** Nothing found so far. The one thing sherpa-onnx
cannot do that whisper.cpp can is not relevant here (see decision 5).

---

## 5. Whisper is the starting point, but hotwords are not available for it

**Decision.** Start with Whisper `base.en` int8. Keep a Zipformer transducer
in reserve.

**The problem.** On the Windows laptop, "one eighty" is transcribed as
"One 18.", which the parser reads as 19 rather than 180
(`update_summary.md` step 13). This is a *vocabulary* problem, not a capture
problem - the same audio decoded directly by whisper is correct. The Windows
build handles it in the parser, with the rule that the second number must be
20 or more for "one eighty" to mean 180.

**The tempting fix.** sherpa-onnx has hotwords / contextual biasing, which is
exactly the right tool: feed the recogniser the darts vocabulary and it
biases towards it. The sherpa-onnx docs show it working well on real
mistakes.

**The catch.** The sherpa-onnx documentation is explicit:

> Only transducer models support hotwords in sherpa-onnx. That is, only models
> from Offline transducer models and Online transducer models support hotwords.
> All other models don't support hotwords.

Whisper is not a transducer. So hotwords are **not available** for Whisper.

**The fallback.** A Zipformer transducer (for example
`sherpa-onnx-streaming-zipformer-en-2023-02-17`, 20M parameters, English)
*does* support hotwords with `modified_beam_search`. If the Phase 1 spike
shows Whisper `base.en` mishearing number words at the oche, switch to it.

**This is the one genuine fork in the road**, and it is why Phase 1 is a
measurement spike rather than implementation.

---

## 6. The confirmation rules are ported literally

**Decision.** The confirmation state machine from `src/main.py` is ported
exactly, including its reasoning.

**Why it matters.** These rules exist because of live failures, not theory:

- Nothing counts on the first pass. A score is read back and waits for a
  "yes".
- `new game` and `undo` need **"coach yes"**, not a bare "yes". A 440 Hz
  tone was transcribed as "I'm going to go back" and undid a real turn. The
  score gate cannot help here, because it only judges utterances that report a
  score, and commands are matched by name rather than by shape.
- A refused score is spoken aloud. Silence would let a player walk back to
  the oche believing the score went in.

Each of these is a comment in the Python source explaining a specific failure.
The comments travel with the port.

---

## 7. The echo gate is stricter on a phone than on a laptop

**Decision.** The microphone is muted from the start of TTS playback until
playback ends plus an echo tail, as on the Windows build.

**Why it is harder here.** On the laptop the coach's voice comes out of
speakers across the room, and the laptop's microphone hears it faintly. On the
phone the speaker and the microphone are a few centimetres apart, in the same
enclosure. The echo is much stronger.

**The open question.** Whether to enable Android's `AcousticEchoCanceler` on
the capture session. The Windows path deliberately left `echoCancellation`
off in the browser, because the recogniser was tuned against raw room audio.
The same reasoning may apply here, but it has to be measured rather than
assumed: a phone that cancels too aggressively will reshape digits in the same
way. Phase 4 measures it.

---

## 8. The models ship as an install-time Play Asset Delivery pack

**Decision.** One PAD asset pack, `deliveryType = "install-time"`. The models
are part of the Play install, not downloaded by the app.

**Why.** The size limits, verified against Google's documentation:

| Component | Compressed download limit |
|---|---|
| Base module | 500 MB |
| Individual asset packs | 1.5 GB |
| Cumulative, all modules + install-time packs | 4 GB |

Our assets are ~189 MB in total: Whisper `base.en` int8 (153 MB), the Piper
voice int8 (17.7 MB), silero-vad (0.6 MB) and espeak-ng data (17.2 MB). That
fits in a single install-time pack with room to spare.

The int8 variants are used throughout. The Piper voice is 17.7 MB int8 against
64 MB in float32, for the same voice, and the Whisper int8 pair is a third of
the float32 size.

The 200 MB figure is the *legacy APK* limit, for apps still publishing APKs
instead of app bundles. An app bundle gets 500 MB for the base module alone.
An earlier draft of this plan assumed 200 MB was the ceiling and concluded the
app had to be sideloaded; that was wrong, and it would have pushed the design
toward a download-and-verify-the-checksum scheme for no reason.

**Why install-time rather than on-demand.** The app has no use until the models
arrive, so a download screen buys nothing - the user installed a darts coach
and wants to throw darts, not watch a progress bar. Install-time also means no
download code at all: the models are read with the ordinary `AssetManager`,
exactly as if they were in the app's own assets.

**The one cost.** The download is about 130 MB and the app occupies about 240 MB
on the phone. That is honest: the app is large because it contains a speech
recogniser and a voice. It is under the 200 MB that makes Play warn the user
before an install over mobile data.

**The alternative, if it is ever wanted.** `fast-follow` or `on-demand`, using
`AssetPackManager`. The Gradle change is one line. The catch, from Google's
docs, is worth knowing in advance: *"The app can't assume the existence of
these files or their locations because these files may be deleted by the user
or moved by the Play Asset Delivery Library across play sessions."* So the
model loader must check for the pack at every launch rather than caching the
path. With install-time this problem does not exist.

**What would reverse it.** Only a decision to distribute outside Play, in which
case the fallback is a plain HTTPS download from GitHub Releases with a
manifest carrying `sha256` per asset, resumable transfers, and a "models not
ready" startup state.

---

## 9. The sherpa-onnx build is scripted, not documented as steps

**Decision.** `tools/build-sherpa.ps1` does the whole build: download ONNX
Runtime, configure CMake, compile, install, and copy the results into the app.

**Why.** The upstream recipe is `build-android-arm64-v8a.sh`, which needs
bash and a Unix environment. That is fine for the sherpa-onnx maintainers and
not fine for a Windows machine. More importantly, the build has four version
constraints that all have to be right at once, and getting one of them wrong
produces an error that does not say what is wrong:

| | |
|---|---|
| NDK 27.2.12479018 | the version the sherpa-onnx F-Droid document names |
| CMake 3.31.6 | 3.22.1 links with `-fuse-ld=gold`, which NDK r27 removed |
| Ninja | CMake defaults to NMake on Windows, and nmake is not installed |
| ONNX Runtime 1.28.2 | the version the upstream script uses |

The script also copies `libc++_shared.so` into the APK. The library is linked
against the shared C++ runtime, and without it the app dies at startup with
`UnsatisfiedLinkError` - a confusing way to find out that one file is missing.

**What it costs.** The script is longer than the four commands it replaces,
and it is a file to maintain when upstream changes. In exchange, the build
works the same way for whoever picks this up next.

---

## 10. The Compose compiler plugin is a separate plugin

**Decision.** `org.jetbrains.kotlin.plugin.compose` is applied to the app
module.

**Why it matters.** Since Kotlin 2.0 the Compose compiler is a Kotlin plugin
of its own rather than something bundled with the Kotlin compiler. Without it,
composable functions fail with an internal backend error:

```
Couldn't inline method call: CALL 'public final fun Column (...)'
```

which says nothing about the actual cause. This cost an hour to track down,
because the error looks like a Kotlin bug rather than a missing plugin.

**Also.** `androidx.compose.foundation` is a separate dependency from
`androidx.compose.ui`. `Column`, `Row` and the rest of the layout primitives
live in foundation. Without it, the same internal error appears, for the
same reason.

---

## 11. The hit rates are a profile, and the profile is the Pro feature

**Decision.** The probability model is built from a `PlayerProfile` rather
than being fixed. `AIMS` is `buildAims(PlayerProfile())`, and the planner is
built for a profile. The default profile is the population the coach was
ported with, so nothing changes for a player who has never been measured.

**Why.** The coach's advice is only as good as the numbers behind it, and the
ported numbers are averages of every player everywhere. A player who cannot
hit D16 is told to go for D16 because that is what works on average. The
tables and the search were already there; only the probabilities needed to
become the player's.

**How it is kept honest.** The aim list is *ordered*, and the planner breaks
ties on that order, so a reordering changes the advice. `ProfileTest` asserts
that the default profile produces exactly the same aim list, in the same
order, and that a default planner matches the shared one for every score from
1 to 170. The parity corpus - 501 plan cases, 6,513 route cases, 502 helper
cases - checks the rest.

**Why the profile is the paid feature and not the general one.** The
population averages are the coach working. The player's own numbers are the
coach knowing them. That is the line the free tier draws, and it is the line
the Pro tier is worth crossing.

**What would reverse it.** Nothing found. The profile is a superset of the
old behaviour, and the old behaviour is still reachable by using the default
profile.

---

## 12. The paid tier is bought once, and the free tier still works

**Decision.** Pro is a one-time in-app purchase at $2.99. The free tier is a
working coach: scorekeeping, read-back, undo, new game, and a live suggestion
at the population averages. Pro adds the personalisation, the out chart, the
setup coaching, persistent preferences and the session stats.

**Why one-time and not a subscription.** The value is a static algorithm and
a static dataset. There is nothing that changes month to month, so there is
nothing a subscription renews. A subscription over a fixed dataset churns
into refunds and bad reviews, and the review text says "nothing new".

**Why $2.99.** The download is around 130 MB, and that cost is paid *before*
the player ever sees the paywall. By the time they reach the paywall they
have already committed to a large download, which is the moment a small
price converts best. The price can be changed in Play Console without a
release, so it is not a permanent commitment either way.

**Why the free tier still works.** In a Play-driven market the free tier is
the only thing most reviewers and the ranking algorithm ever see. A free tier
thin enough to be a demo costs rating volume, and rating volume is what makes
the next install cheap. The gate is on *depth* - the chart, the
personalisation, the history - not on whether the app works.

The thing being sold is personalisation, and personalisation is not
meaningful until the player trusts the coach. Someone has to use the coach
for a few legs before "it knows my doubles" means anything, so the free tier
has to give them the generic version to trust in the first place.

**Why the free tier does not show percentages.** If free says "T20 T20 D20 -
52%" and Pro says "T20 T20 D20 - 63% (your numbers)", the free tier is a
working demonstration of the paid thing and there is no reason to upgrade. The
free tier gives the plan, which is what a player needs at the oche, and does
not promise a number it cannot personalise.

**Why paid from the first release.** The personalisation cannot be taken
back once it has been given away, and a free tier that has already got the
feature is not a tier anyone upgrades from. The features and the paywall ship
together.

**What would reverse it.** If the paywall turns out to be wrong, the price can
be changed in Play Console without a release. The *set of gated features*
cannot, which is why every gate goes behind one entitlement interface rather
than being scattered through the UI as `if (isPro)` checks. Moving one
feature from Pro to free is then a single release, and the people who already
paid keep what they bought - a hard gate would have no such move.

---

## 13. The app keeps the INTERNET permission, for the purchase

**Decision.** The manifest declares `INTERNET`, and it is used for one thing:
talking to Google Play about the Pro purchase.

**Why the permission was there before.** The app used to declare no
`INTERNET` permission at all, and that was deliberately a *verifiable*
property of the build rather than a promise in the README: the coach could
not talk to a server because it had no way to. `docs/PUBLISHING.md` said
plainly that this was "worth keeping true".

**Why it is declared now.** Taking a payment needs the network. There is no
way to sell something without talking to someone, and pretending otherwise
would only mean the purchase does not work.

**What has been kept.** The claim has been narrowed rather than withdrawn.
The coach still works with the phone in aeroplane mode. The microphone audio
is still processed in memory and never sent, and no code in the app reads the
audio buffer and writes it to a socket. There is still no analytics, no
advertising, and no crash reporting.

**What has been said honestly.** The store listing, the privacy policy, the
in-app text and the README now all say that the one network connection is to
Google Play. The manifest says the same thing in a comment, so a reviewer
reading the source sees the reason rather than having to infer it.

**Why the Data safety answer changed from "No data is collected".** Google's
definition of collection is data retrieved from the device and sent off it.
The purchase data *is* sent, to Play, by the Play Billing Library, so it is
disclosed as purchase history. The microphone audio is not sent, so it is not
disclosed. The answer is now "yes, purchases only" rather than "no", and it
is exactly true.

**What would reverse it.** Nothing found. A direct web checkout would need
the network too, and would need an account and a server to hold the
entitlement, which is a bigger change than the permission was.

---

## 14. The echo tail is measured per phone, not assumed

**Decision.** The wait after the coach speaks - before it listens again - is
measured on the player's phone, from the **Settings** screen. The value
measured on a Pixel 7 is the default, for a phone that has not been
measured.

**Why it was a fixed number.** The tail is how long the coach's voice is
still audible at the microphone after the audio has stopped. On a laptop it
was room reverb and 0.35 s was right. On a phone the speaker is 10 cm from
the microphone, so it is the speaker's mechanical ringing, and it measured
447 - 835 ms at the oche.

That number is a property of *that phone, in that room*. A different
speaker rings differently, and a different room has a different noise
floor, so the same 0.35 s is wrong somewhere. It was tuned on one device
and the app was never tested on another.

**Why measuring is the right answer and not a setting.** A setting means
every player has to know what an echo tail is and work out what to put in
it. A measurement means the phone works it out, and the player just has to
press the button.

**Why the errors are not symmetric.** A tail that is too short makes the
coach hear its own reply and cut itself off, which looks broken. A tail that
is too long costs a moment of dead time before the coach answers. So the
measurement takes the *longest* of the three samples rather than the mean,
and adds a quarter to it for margin. The floor is 50 ms, because a
measurement of a millisecond is a very short tail rather than no tail.

**Why headphones get nothing.** The voice is not in the room, so it cannot
come back through the microphone. The wait is zero whatever the
measurement says, and that has been true since the route detection was
added.

**What would reverse it.** Nothing found. The measurement needs a working
microphone and a working voice, and it needs the room to be quiet enough
that the room floor is meaningful - which is why it can be run again from
the settings when the phone is moved somewhere noisier.


## 15. A missed-target correction replaces the last dart in the current turn

**Decision.** A Pro player can report what actually landed after missing the
intended dart. The coach replaces the last dart in the current turn and
re-plans only the darts remaining, naming the result a **Checkout** or a
**Setup**.

**Why the last dart.** The player's immediate problem is usually the next
dart: they meant T20, hit single 8, and now need to know what to throw with
the darts that are left. Correcting the last dart is enough to answer that
and has one unambiguous voice form. Letting the player choose any dart in the
turn, or correct a turn that has already been confirmed, adds state and
confirmation rules for cases that are less common.

**Why the leg score does not move.** The correction changes the current turn,
not the leg. The corrected total still waits for the normal score read-back
and confirmation, so nothing counts on the first pass. A correction during
the read-back replaces the dart and asks for confirmation of the new total.

**Why Pro.** This changes what the coach does with the next darts, rather
than adding another reference screen. It is coaching depth, and it is gated
by `MISSED_TARGET_CORRECTION` in the same entitlement list as the other Pro
features.

**Why an unrecognised landing is not guessed.** Physical-device testing found
that Whisper sometimes heard the spoken digit "8" as "eat". Treating that as
a dart would silently change the player's score. The turn is left alone when
the landing cannot be identified; improving the spoken form is safer than
guessing.

**But the coach says so.** A correction-shaped phrase with no readable
landing used to produce silence, which is the same failure as a refused
score with no read-back: the player is left at the oche believing the coach
understood. The coach now asks for the landing, and the turn is untouched
while it waits. The rule the coach has always followed - a refusal is
spoken, not silent - applies to the correction too.

**Why the target and the landing are told apart by the verb.** "I missed
double 16 and hit single 8" names two darts and "I hit single 8 instead"
names one, and the parser has to know which is which. The rule is the verb
in front of the dart, not the dart itself: after "missed" is the target,
after "hit" is the landing. Where two darts follow one verb, the one
nearest the verb is the one it belongs to.

The alternative - reading a single named dart as the landing, and the target
as whatever the coach was aiming at - was the first implementation, and it
was wrong. "I missed double 16" would be read as "you hit double sixteen":
the coach would tell the player they had hit the very double they said they
had missed, and correct the turn to a dart that never landed. The board
tests found it; the device test never reached the case.

**Why the bull has to be named.** The bull is the only part of the board
with a name rather than a number, so it cannot be read by the number
rules. "I hit the bull instead" used to be ignored altogether, and the
coach said nothing. A player who missed T20 and hit the bull got no
correction and no reply at all.

**Why the debug Pro switch is in the debug source set.** A switch that
turns Pro on without a purchase is test instrumentation, and test
instrumentation should not be in the code that Play serves. The switch
lives in `src/debug`; the release build has a version that returns false
unconditionally and does not contain the debug one at all.

The implementation, Pixel 7 findings, and remaining work are in
`docs/MISSED-TARGET.md`.

---

## 16. The double bull is priced at the published rate, not at half

**Decision.** The double bull is worth 22.5% - the middle of the published
band for professionals - instead of the 50% the coach was ported with.

**Why.** It was the one hit rate in the model with no source behind it, and
the one that mattered most. The bull is one of the few targets the coach can
plan a leg around: 170 is finished with T20 T20 Bull and nothing else, so an
optimistic bull rate makes the coach steer players into bull finishes they
then miss. Published segment tracking (Ochepedia's figures, as summarised in
the professional hit-rate tables) puts the inner bull at 20-25% for a
professional and around 30% for the very best, so 0.50 described a player who
has never existed.

Correcting one number moved the advice more than anything else in the model:
**138 of the 501 plans** changed. The coach no longer leaves 170 at all, the
setup it leaves at 321 became T20 T20 T16 leaving 153 (finished T20 T19 D18),
and the checkouts in the 100-135 range moved off the bull onto ordinary
doubles.

**Alternatives considered.**

| Option | Why not |
|---|---|
| Leave it at 0.50 | A guess above the best player alive, and the single number that moved the advice most. |
| Lower the treble curve to the professional average as well | The treble twenty has a published figure (40-43%), but only the treble twenty does, and the model's treble curve descends with the bed number - which is how often a bed is thrown at, not how often it is hit. All the treble beds are the same size. Scaling the curve to fit one point dragged the small trebles down to figures nobody can hit (the treble five at 9%). That needs per-bed data, not a scale factor. |
| Change the coach to prefer the lowest leave | The coach maximises the chance of checking out in the coming turn, which is the right quantity. "Lowest" is not a principle: 159 is lower than 170 and cannot be finished at all. |
| Change the Kotlin coach first and the Python after | The Python coach is the reference and the parity corpus is the contract, so the Python change came first, the corpus was regenerated, and the Kotlin port followed it. |

**What would reverse it.** A player's own measured bull rate already
overrides the default - the hit learner prices the bull from their own play - 
so a player who hits the bull gets the classic 170 advice back without
anything changing here. What would move the default itself is better data:
per-bed hit rates for club players, which nobody publishes.

*Correction, added later: the first half of that is not true yet. The
learner does price the bull from the player's own play, but
`PlayerProfile.merge` always takes the player's own bull number - there is no
way to say "not set" for a plain number - so a learned bull rate never
reaches the planner. See decision 17 and section 9 of `HANDOVER.md`.*

*Correction, added later still: the bull is fixed now. The bull's rate is
nullable, so "not set" can be said: the merge takes the player's own number
if there is one, and the learned number otherwise, and the planner falls back
to the population average when there is neither. The player's own number is
saved and read back as well, so a player who says they hit the bull get the
classic 170 advice back.*

*The same fix has been applied to the outer bull and to the singles, so they
say "not set" as well. A learned single rate now reaches the planner, which
it never did: the coach used the population average for every single dart
whatever the player's own play said. The outer bull was not in the learner
at all, though the counts were kept for it; it is now learned from those
counts the same way the double bull is, so nothing is left in the plain
numbers.*

---

## 17. A learned rate counts the darts that went where they were aimed

**Decision.** The rate the hit learner works out for a segment is the share
of the darts **aimed at that segment** that landed **on that segment**. It
was the share of the darts that landed on the segment, over the darts aimed
at it.

**Why.** Those are two different populations. The learner keeps both counts
for every segment, because they answer different questions: the attempts are
the darts thrown at the segment, the hits are the darts that landed there.
A dart aimed at T16 that slides into T20 is a hit on T20 and a miss on T16.

Dividing one by the other gives a number that is not a rate. The effect is
always in the same direction: a segment that other targets happen to land on
looks better than the player is, and a segment the player misses looks
worse. The planner uses this number as `pHit` - the chance that a dart aimed
at a segment lands on it - so the error goes straight into the advice.

It was found by building the web coach, where the page has to show what the
coach has learned, and the panel said "T20 2/2, 63%": the player aimed at T20
twice and hit it twice, and the third dart - aimed at T16, landed on T20 -
had made the coach think the player hits T20 68% of the time.

**Alternatives considered.**

| Option | Why not |
|---|---|
| Leave it | The rate would keep being a ratio of two unrelated numbers, and the coach would keep telling players they are better or worse than they are. |
| Change the denominator to every dart thrown | That would measure "where do the darts land" rather than "how well do they throw at this segment", which is not what the planner needs. |
| Fix it in the browser | The browser has no learner of its own; it uses the shared one. There is one copy of the coach. |
| Change the Python first | There is no Python learner to change: the Windows coach has never had one, so there is no parity corpus entry and nothing to regenerate. |

**What would reverse it.** Nothing about the model depends on it. The
defaults are unchanged, so an unmeasured player gets exactly the advice they
got before, and the parity corpus still passes untouched.
