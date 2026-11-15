# Missed-target corrections

A Pro feature for correcting the last dart in a turn after the player missed
the intended target. The player says what actually landed; the coach replaces
the dart, recalculates the turn, and gives a new plan for the darts remaining.

Written 14 November 2026, after implementation and physical-device testing on
a Pixel 7. Extended 14 November 2026, after the board and the checkout branch
were proved in core tests. Two bugs were found by that work, and the
debug-only switch was moved out of the production source.

---

## What the player does

1. The coach gives a suggestion.
2. The player reports at least one dart in the current turn.
3. The player says what actually landed, for example:
   - "I hit single eight instead"
   - "I hit 8 instead"
   - "I hit the bull instead"
   - "I missed double 16 and hit single 8"
4. The coach replaces the last dart and re-plans the rest of the turn.

Only the last dart can be corrected. Earlier darts in the turn are left alone.
A turn that has already been confirmed cannot be corrected in the first
release.

The player does not have to name the intended target. The coach uses the
segment it aimed at, or the qualified segment in the player's dart report, as
the target that was missed.

**The player may name the target as well as the landing.** "I missed double
16 and hit single 8" names both: the target and the landing. The two are
told apart by the verb in front of them, so "I missed double 16" on its own
is read as the target with no landing, and the coach asks for the landing
rather than guessing it.

**The bull can be named.** "I hit the bull instead" and "I hit outer bull
instead" both work, as do "25" and "50". The bull is the one part of the
board with a name rather than a number, and before this it could not be
named at all.

**When two darts are named, the one nearest the verb is the one it belongs
to.** "I missed the bull and hit single 8" is the bull missed and the eight
landed.

## What the coach does

For a correction in the current turn, `CoachEngine`:

1. Checks that missed-target corrections are available.
2. Checks that the current turn contains a dart.
3. Determines the intended target.
4. Replaces the last dart with the actual landing.
5. Records the miss in the hit learner.
6. Recalculates the current turn score.
7. Works out how many darts remain.
8. Calls `Planner.planFor(scoreRemaining, dartsRemaining)`.
9. Labels the result explicitly as **Checkout** or **Setup**.
10. Speaks and displays the revised plan.

Example response:

> You hit 8 instead. That leaves four hundred and ninety-three with 2 darts
> left. Setup. Treble twenty and treble twenty.

The screen shows the current turn's dart count and score, the Checkout/Setup
heading, and only the darts remaining in the turn.

If the corrected score busts the leg, the turn is cleared and the coach asks
for the turn again.

## Where it lives

| Concern | File |
|---|---|
| Parsing correction phrases | `core/.../Parser.kt` |
| Correction and re-plan state machine | `core/.../CoachEngine.kt` |
| Replacing the last dart | `core/.../GameState.kt` |
| Planning with fewer darts left | `core/.../Planner.kt` |
| Learning from the miss | `core/.../HitLearner.kt` |
| Pro entitlement | `app/.../entitlement/Entitlement.kt` |
| Activity plumbing and persistence | `app/.../MainActivity.kt` |
| Coach-screen state | `app/.../ui/CoachScreen.kt` |
| The debug-only Pro switch | `app/src/debug/.../TestProSwitchDebug.kt` |
| Tests | `core/src/test/.../MissedTargetTest.kt` |
| Tests, the whole board | `core/src/test/.../MissedTargetBoardTest.kt` |

The core has no billing dependency. `CoachEngine.missedTargetCorrectionAvailable`
is set by the app from the entitlement, so purchase logic stays outside the
game rules.

## Entitlement

The feature is gated by:

```kotlin
Entitlement.Feature.MISSED_TARGET_CORRECTION
```

It is in `PRO_FEATURES`. A free player hears:

> Missed-target corrections are part of Pro.

The current dart and turn score are not changed when the gate refuses a
correction.

The feature is listed on the paywall as **Missed-target corrections**.

---

# Device findings

The implementation was tested on a connected Pixel 7 with the debug build and
Whisper `base.en` int8 through sherpa-onnx.

## What worked

### The correction worked

Whisper heard:

> I hit single eight instead.

The coach responded:

> You hit 8 instead. That leaves four hundred and ninety-three with 2 darts
> left. Setup. Treble twenty and treble twenty.

The screen showed:

- `Turn: 1 dart`
- `Scored 8`
- `SETUP`
- `T20 T20`
- `leaves 373`

The corrected score was 8, the leg score stayed at 501 until the turn was
confirmed, and the plan contained only the two darts remaining.

### Corrections worked at each position

A correction was tested after:

- the first dart;
- the second dart;
- the third dart while the score read-back was pending.

### The Pro gate worked

Before test Pro was enabled, the app heard the correction correctly and said:

> Missed-target corrections are part of Pro.

The current dart was left unchanged.

### Normal voice flow still worked

The device test also confirmed:

- `no` discarded a pending score;
- `yes` confirmed the following suggestion;
- qualified dart reports and score read-back continued to work.

## Problems found and fixed

### 1. A correction during a pending read-back was discarded

The coach's state machine handled a pending score before parsing new input.
A correction therefore looked like an unrelated phrase: the pending score was
discarded and the correction was lost.

**Fix:** when a score is pending, `CoachEngine` now parses the phrase for a
correction before treating it as a superseding answer. A correction during a
third-dart read-back replaces the dart and asks for confirmation of the new
total.

**Tests added:**

- correction while the score is awaiting confirmation;
- correction before the third dart does not create a false read-back.

### 2. A correction before the third dart created a false read-back

The first implementation of the read-back fix set `pendingScore` after every
correction. A correction after one or two darts could therefore produce:

> You said twenty. Right?

even though the turn was not complete.

**Fix:** a new read-back is produced only when the corrected turn contains
three darts. With one or two darts, the coach gives the revised plan and waits
for the remaining darts.

### 3. Whisper wrote "treble" as "trouble"

On the device, "treble twenty" was heard as "trouble 20". The parser treated
that as a bare score of 20 rather than a treble.

**Fix:** the parser now recognises `trouble` as a treble. This is deliberately
a transcription variant, not a new darts term.

### 4. A debug install without local models crashed at startup

Reinstalling a debug APK built without `-PwithLocalModels` caused a native
failure while loading `silero_vad.onnx`. This was a packaging problem, not a
correction-flow problem.

**Fix for local testing:** build debug installs with:

```powershell
.\gradlew.bat -PwithLocalModels :app:assembleDebug
```

The release bundle is unaffected: its models are in the Play Asset Delivery
pack.

### 5. The bull could not be named

Found by the board tests, not by the device test: the device test never
reached the bull.

"I hit the bull instead" was not recognised as a correction at all. The bull
is the one part of the board with a name rather than a number, so the parser
could not read it out of the phrase, and the coach said **nothing at all**.

**Fix:** the bull can now be named as "the bull", "double bull" and "outer
bull", and by its value - "25" and "50", which already worked. A bare "bull"
is the double bull, because that is the one players mean.

### 6. A missed target was read as the landing

Also found by the board tests.

"I missed double 16" - the player naming the target and not saying what
landed - was read as "you hit double sixteen". The coach told the player
they had hit the very double they said they had missed, and corrected the
turn to a dart that never landed.

**Fix:** the landing and the target are told apart by the verb in front of
them. See the table in "What the player does" above.

**A third bug came out of the same fix.** The first version of the fix read
the bull as the landing whenever the phrase named it, whatever the verb
said. "I missed the bull and hit single 8" was therefore read as "you hit
the eight", with the bull and the eight the wrong way round. The bull has
to be read from the words *after* the verb, like any other dart, and the
dart nearest the verb is the one that counts.

### 7. A correction-shaped phrase with no landing said nothing

"I hit single eat instead" - the transcription Whisper produced for "I hit
single 8 instead" - was not recognised as a correction, because there was no
number in it to read. The coach said nothing, and the player had no way of
knowing that the coach had not understood.

**Fix:** the phrase is recognised as a correction, and the coach asks for
the landing:

> I didn't catch which dart you hit. Please repeat.

The turn is left exactly as it was, so a refused correction cannot change a
score. This is the same rule the coach has always followed for a refused
score: say what happened, rather than leave the player believing it went in.

## Transcription findings

Whisper was not completely reliable with short board numbers:

| Spoken | Heard | Result |
|---|---|---|
| "triple 20" | "triple 20" | Correct |
| "double 16" | "double 16" once; "16" another time | Sometimes dropped the qualifier |
| "treble 20" | "trouble 20" | Fixed in the parser |
| "single 8" | "single eat" | No correction was possible |
| "I hit 8 instead" | "I hit single eat instead" | No correction was possible |
| "I hit single eight instead" | Same text | Correct |

Speaking the number as a word was noticeably more reliable than speaking the
digit. In particular, "eight" was recognised correctly where "8" was heard as
"eat".

The app cannot safely infer a dart from "eat", so it does not treat it as
one. This is the correct conservative behaviour, and the turn is left alone
when the landing cannot be read.

What it does now is **say so**, rather than saying nothing:

> I didn't catch which dart you hit. Please repeat.

Before, the player received no spoken correction request for that
utterance, and had no way of knowing the coach had not understood.

---

# What is left to do

## Required before release

### 1. Improve spoken correction reliability - the first two options are done

The feature depends on Whisper hearing the actual landing correctly. The
device test showed that a short number can be lost or transcribed as a word.

Options, in order of preference:

- ~~add a short confirmation after a correction, so the player can see and
  hear what the coach understood and repeat it if it is wrong;~~
- ~~make the correction request explicit when the phrase is
  correction-shaped but the actual landing cannot be identified;~~
- support a small set of common ASR substitutions only where they are
  unambiguous, rather than guessing;
- test a larger set of board numbers in a noisy room before deciding whether
  a board picker is needed.

**The first option is done, in the form the second option needed.** The
coach now says what it understood after every correction:

> You hit 8 instead. That leaves four hundred and ninety-three with 2 darts
> left. Setup. Treble twenty and treble twenty.

That line already carries the landing, the score left and the plan, so the
player can see at a glance whether the coach heard them correctly, and
correct it without asking.

**The second option is done as well.** A phrase that is shaped like a
correction but does not say what landed is now asked for rather than
ignored:

> I didn't catch which dart you hit. Please repeat.

Before this, the coach said **nothing at all** in that case. The player was
left at the oche with no idea that the coach had not understood, which is
the same failure as a refused score with no read-back - and the refusal of a
score has always been spoken for exactly that reason.

The turn is left exactly as it was when a correction cannot be acted on, so
a refused correction cannot change a score.

**What is still open.** The last two options. The ASR substitutions are
deliberately not done: "eat" is not an unambiguous substitute for "8" on
its own, and guessing would change the player's score on the strength of a
transcription. The board picker needs the noisy-room test first, because
that is what would say whether the spoken form is good enough.

### 2. Test all board numbers and the checkout branch - done in core tests

The physical-device test covered setup plans at 501. It did not cover:

- a corrected turn at a checkout score;
- a correction that changes a setup plan into a checkout;
- a correction that changes a checkout plan into a setup;
- a correction at a bogey score;
- every number from 1 to 20 as the actual landing;
- the bull and outer bull.

**All of these are now covered by `MissedTargetBoardTest`**, which is a
core test rather than a device test because the correction is pure logic.
The arithmetic does not need a phone.

Two of them found real bugs, which are described in the next section.

A smaller set should still be checked on the device, because the *spoken
form* is what the device test is for.

### 3. Decide whether the corrected turn needs an explicit read-back

After a correction before the third dart, the coach gives the revised plan but
does not read the corrected partial total back. After correcting the third
dart, it reads back the corrected total.

This is internally consistent, but it should be confirmed as the intended
player experience. The alternative is to read back every corrected partial
turn.

### 4. Complete the Play purchase test

The device test used a debug-only test-Pro switch. It did not test:

- buying Pro through Play;
- restoring a purchase;
- correction becoming available immediately after purchase;
- correction being refused after a refund or revoked purchase.

The debug switch is in the debug source set, so it cannot enable Pro in a
release build. See the next section.

### 5. Remove or isolate the debug test-Pro mechanism before release - done

The switch is now **in the debug source set**, not in the production source
with a flag around it.

| File | What it is |
|---|---|
| `app/src/main/.../TestProSwitch.kt` | The switch itself, and the call site. |
| `app/src/debug/.../TestProSwitchDebug.kt` | The working switch. **Only in a debug build.** |
| `app/src/release/.../TestProSwitchRelease.kt` | A no-op. `return false`, always. |

The release build does not contain the debug file at all, so there is
nothing in it to be tricked into turning Pro on. This is checked rather than
assumed: the compiled release class is

```
public static final boolean testProSwitchEnabled(android.content.Context, boolean);
  Code:
     0: aload_0
     1: ldc           #9   // String context
     3: invokestatic  #15  // Intrinsics.checkNotNullParameter
     6: iconst_0
     7: ireturn
```

It loads its arguments and returns false. There is no path through it that
returns anything else.

**What is still open.** The switch can now be deleted outright, along with
the `testpro` intent extra, once the real Play purchase path has been
tested. The source set makes that a one-file change rather than a hunt
through the production code.

### 5a. The bugs the board tests found

Writing `MissedTargetBoardTest` found two real bugs, both of which the
Pixel 7 test could not have found, because both need a case the device test
did not reach.

#### The bull could not be named

"I hit the bull instead" was not recognised as a correction at all. The bull
is the one part of the board with a name rather than a number, so the
parser could not read it out of the phrase, and the coach said **nothing at
all**. A player who missed T20 and hit the bull got no reply and no
correction.

The bull can now be named as "the bull", "double bull" and "outer bull",
and by its value - "25" and "50", which already worked.

#### A missed target was read as the landing

"I missed double 16" - the player naming the target and not saying what
landed - was read as "you hit double sixteen". The coach then told the
player they had hit the very double they said they had missed, and
corrected the turn to a dart that never landed.

The landing and the target are now told apart by **the verb in front of
them**, not by what they are:

| Phrase | Target | Landing |
|---|---|---|
| "I missed double 16 and hit single 8" | D16 | 8 |
| "I hit single 8 instead" | - | 8 |
| "I missed double 16" | D16 | - |

The third row is the one that changed. The coach now asks for the landing
rather than guessing:

> I heard the D16 you missed, but not what you hit. Say what you hit
> instead.



## Product decisions still open

### 6. Correcting a confirmed turn

The first release corrects only the current, unconfirmed turn. A player who
realises after saying "yes" that they missed the intended dart cannot correct
it.

Supporting this requires:

- rewriting the last completed turn;
- recalculating the leg score;
- replacing the turn's dart and segment history;
- updating learned statistics;
- using the existing destructive-command confirmation pattern (`coach yes`),
  because this throws work away.

This is deliberately not implemented in the first release.

### 7. Choosing which dart in the turn was missed

Only the last dart can be corrected. Supporting any dart in the turn needs a
new spoken selection vocabulary and clearer state handling.

### 8. Bare score reports

If the player reports a turn as a bare total, the app does not know which
individual dart was thrown or missed. A correction can still replace the last
reported value, but target attribution cannot always be learned correctly.

The product should decide whether the app:

- accepts the correction with reduced learning;
- asks the player to report the qualified dart; or
- treats bare-total turns as ineligible for correction.

### 9. Preferred double during a correction

The first implementation re-plans with the ordinary planner. It does not
re-plan as a route onto the player's preferred double. This needs to be
checked against the product intent: after a miss, should the coach continue
playing for the chosen double, or choose the best route from the new score?

## Not required for the first release

- Board-picker UI. The agreed first release is voice-only.
- Correction of earlier darts.
- Corrections after a confirmed turn.
- A new settings toggle for the feature. Its availability is determined by the
  Pro entitlement.

---

# Testing the feature again

## Build and install

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :core:test
.\gradlew.bat -PwithLocalModels :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The `-PwithLocalModels` flag is required for a debug APK installed with adb.
Without it the local VAD model is absent and the app exits during startup.

## Enable Pro without a Play purchase

For debug testing only:

```powershell
adb shell am start -n com.jr12866.dartcoach/.MainActivity --ez testpro true
```

## Watch the app

```powershell
adb logcat -s DartsCoach
```

## Speak each phrase separately

The recogniser combined several short phrases spoken in quick succession into
one utterance. Test one phrase at a time and allow a pause before the next.

A useful sequence is:

1. "triple 20"
2. "I hit single eight instead"
3. "double 16"
4. "I hit single five instead"
5. "single 20"
6. "I hit single seven instead"

The third correction happens while the score read-back is pending and is the
important regression case.

## Run the core tests

```powershell
.\gradlew.bat :core:test --tests '*MissedTargetTest*'
```

The tests cover:

- all supported phrase forms;
- ordinary dart and score reports remaining unchanged;
- replacement of the last dart only;
- the turn score changing while the leg score does not;
- Checkout/Setup labelling;
- plans containing only the darts remaining;
- learning from the miss;
- the Pro gate;
- correction without a current dart;
- correction during a pending read-back;
- no false read-back before the third dart;
- a correction that busts the turn;
- the `treble`/`trouble` transcription variant.

There is a second file, `MissedTargetBoardTest`, which covers the whole
board and the checkout branch:

```powershell
.\gradlew.bat :core:test --tests '*MissedTargetBoardTest*'
```

It covers:

- every number from 1 to 20 as a single, a double and a treble;
- the bull and the outer bull, by name and by value;
- every single bed as the landing, replacing the dart in the turn;
- the target and the landing being told apart by the verb in front of them;
- a landing that cannot be read being asked for rather than guessed;
- a refused correction during a read-back not re-asking the score;
- a correction at a checkout score, and at a setup score;
- a correction that turns a setup into a checkout, and the other way round;
- a correction at a bogey score;
- a correction that cannot be finished;
- the plan holding only the darts that are left;
- the leg score not moving until the turn is confirmed.

---

# Parity - decided: the correction stays Android-only

The correction command is Android-specific, like the existing
`whats_the_leave` command. It is not represented in the Python parser or
the parity corpus.

**The decision is to keep it Android-only and document the distinction.**

The other option was to port `report_miss` to
`C:\git\darts-coach-windows` and extend the corpus. The reason it was not
done is that the Windows coach does not have this command at all: its
`src/game/parser.py` has no `report_miss` and no `whats_the_leave`. Porting
it would mean adding the whole correction state machine to the Windows
`main.py` - the parser command is the small part of it - and the Windows
build is a working product that must not be disturbed.

The gain would be parity on a feature the Windows build does not have, for a
user who never sees the Windows build. The cost would be a change to
something that works.

**The cost of this decision, stated honestly.** These two commands are only
pinned by the Kotlin tests. `MissedTargetTest` and `MissedTargetBoardTest`
are the specification for `report_miss`, in the same way the corpus is the
specification for everything else. There is no Python side to compare
against, so a deliberate change to the correction has to be made in the
tests as well as in the code, and there is nothing to catch the two
drifting apart.

**What would change this.** If the Windows coach is ever given the same
command, the corpus can be extended to cover it, exactly as it covers
everything else. The Kotlin side would need no change at all.

The corpus itself remains green: all 7,663 parity cases pass, along with the
missed-target tests. `docs/PARITY.md` now lists the Android-only commands
alongside what parity does and does not cover.

