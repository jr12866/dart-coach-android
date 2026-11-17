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
2. The player says what actually landed, for example:
   - "I hit single eight instead"
   - "I hit 8 instead"
   - "I hit the bull instead"
   - "I missed double 16 and hit single 8"
3. The coach replaces the dart, recalculates the turn, and gives a new plan for the darts remaining.

The player does not have to have reported a dart first. See "A turn with
nothing on the books" below.

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

## A turn with nothing on the books

Most players report a turn as a number - "one forty" - rather than naming
the darts. That leaves the coach with no dart to correct, and until
15 November 2026 it refused the correction:

> Tell me the dart you missed first, then what you hit instead.

which is wrong twice over when the player has already named both the dart
they missed and what landed. It is now corrected in two ways, depending on
what the coach is holding.

**A total is waiting to be confirmed.** The player reported the turn as a
number and the coach read it back. The correction is to that number: the
dart that was missed comes off it and the dart that landed goes on, and the
corrected total is read back.

> You hit 8 instead of D16. That makes the turn 8. You said eight. Right?

The target is the one the player named, or the last dart the coach aimed at.
A correction that does not add up to a turn anybody could have thrown - the
dart that was missed was not in the turn at all - is refused, and the total
the player gave stands.

**Nothing is waiting.** The dart the player is describing has not been
reported at all, so it is the first dart of the turn, and it is recorded as
one. The rest of the turn is planned around it.

> You hit 8 instead. That leaves 493 with 2 darts left. Setup. Treble twenty
> and treble twenty.

The target is the one the player named, or the first thing the coach aimed
at, which is what they were throwing at when they missed it.

**The limit of this.** The landing is recorded as the first dart of the
turn, because the darts the player has not reported are the coach's to
guess at. A player who throws two darts, reports neither and then says what
landed on the third has their landing recorded as the first. The coach says
what it understood after every correction, so the player can see and correct
it.

## What the coach does

For a correction in the current turn, `CoachEngine`:

1. Checks that missed-target corrections are available.
2. Checks whether the turn has a dart to replace.
3. Determines the intended target.
4. Replaces the last dart with the actual landing, or corrects the total
   that is waiting, or records the landing as the first dart of the turn.
5. Records the miss in the hit learner.
6. Recalculates the current turn score.
7. Works out how many darts remain.
8. Calls `Planner.planFor(scoreRemaining, dartsRemaining)`.
9. Labels the result explicitly as **Checkout** or **Setup**.
10. Speaks and displays the revised plan.

Example response:

> You hit 8 instead. That leaves four hundred and ninety-three with 2 darts
> left. Setup. Treble twenty and treble twenty. If it drops to the single bed
> you're on four hundred and seventy-three, then treble twenty.

The screen shows the current turn's dart count and score, the Checkout/Setup
heading, only the darts remaining in the turn, and the contingency:

```
SETUP
T20 T20
leaves 233
If it drops to the single: 333, then T20
If it slides into 1 or 5: 352 or 348
```

If the corrected score busts the leg, the turn is cleared and the coach asks
for the turn again.

## The contingency

The planner has always worked out what happens if the first dart of a plan
drops into its single bed - that is what makes a leave of 40 better than a
leave of 38 - but it kept it to itself. The coach recommended a treble
without saying what happens when it comes off as a single, and at 388 with
two darts left that is the difference between leaving 268 and leaving 368,
where a hundred and eighty leaves 188 and there is no out.

The plan now carries a `Contingency`:

- the score standing if the first dart drops into its bed;
- the darts to throw for the rest of the turn instead;
- the score those darts leave.

`Planner.planFor` only worked the fallback out when there was more than one
dart left in the turn. A turn with one dart left has no darts to fall back
on, but it has a score to fall back on, and that is the case the player
needs: the dart either leaves the score the coach planned for or it does
not.

The coach says the contingency when it re-plans the darts left in a turn,
which is the missed-target correction. That is the one moment the question
is live: the player has just missed one, so the next one landing in the bed
below is a live possibility. The screen shows it for the standing plan as
well.

**The advice did not change.** The parity corpus is green as it was, because
the corpus pins what the coach tells the player to throw, not what it says
about it. The Python build was changed to match, so the two coaches give the
same advice and show the same line; the mid-turn voice is Android-only,
because it belongs to this feature.

## The sideways miss

There is a second way for a treble to miss, and it is the one the board's
geometry explains. A dart that falls straight out of the treble twenty lands
in the single twenty - that is the drop into the bed, and it is what the
contingency has always been about. A dart that *crosses a wire* lands in the
single bed of a neighbouring sector instead, and which sector that is
depends on where the sector sits on the board:

| Aimed at | Its neighbours | So a wire into |
|---|---|---|
| T20 | 5 and 1 | 5 or 1 |
| T19 | 3 and 7 | 3 or 7 |
| D16 | 7 and 8 | 7 or 8 |

`core/.../Board.kt` holds that geometry: the sectors of a standard board,
clockwise from twenty, and the two either side of any bed. It is the
board's own shape rather than a guess about the player, so it is data.

The screen tells the player about it, under the drop into the bed:

```
If it drops to the single: 368, then T20
If it slides into 1 or 5: 387 or 383
```

**The advice did not change.** A wire miss is not in the planner's search:
the coach still plans as if a miss either drops into the bed or scores
nothing. What changed is that the coach now knows what a sideways miss
leaves and can say so.

Modelling the wire inside the search would move the advice, and how far it
moves depends on a number nobody has measured: the share of misses that
slide sideways. Measured on the Python planner, the plans that change are 51
of the 500 at fifteen percent of misses sliding into each neighbour, 93 at
thirty percent, and 108 at forty-five percent. The advice would be riding on
that constant, so the constant has to be measured rather than guessed.

The wire is on the screen and not in the spoken line. The spoken correction
line is already 10.7 s, and the sideways miss is the less likely of the two
misses: the coach has no plan for it, because the player will report what
landed and the coach will plan again.

## Where the misses go

The board says where a dart *could* land. The player's own play says where
their darts *do* land, and the coach now keeps the two together: when the
player corrects a dart, the coach remembers where that dart actually landed,
not just that it missed.

A dart aimed at the treble twenty that lands in the single five has gone
sideways; one that lands in the single twenty has dropped short. Those are
different problems and they need different answers, so the counts are kept
per aimed segment:

| Aimed at | Landed in | Times |
|---|---|---|
| T20 | 5 | 3 |
| T20 | 1 | 1 |
| D16 | 8 | 2 |

The counts are saved with the other learned counts, so they carry on across
restarts, and **Forget what I've learned** clears them with everything else.
The profile screen shows them under **Where your misses go**:

```
Where your misses go
What your darts landed on when they missed the bed they were aimed at.
T20    5 x3, 1 x1
D16    8 x2
```

**The advice did not change.** The wire is still not in the planner's search.
What the learning buys is the fact that would replace the guess: the share
of misses that slide sideways is currently assumed, and the player's own
counts are what would settle it.

## Where it lives

| Concern | File |
|---|---|
| Parsing correction phrases | `core/.../Parser.kt` |
| Correction and re-plan state machine | `core/.../CoachEngine.kt` |
| Replacing the last dart | `core/.../GameState.kt` |
| Planning with fewer darts left | `core/.../Planner.kt` |
| The contingency for a plan | `core/.../Darts.kt` (`Contingency`) |
| Which beds are next to which | `core/.../Board.kt` |
| Where the misses landed | `core/.../DartThrow.kt` (`HitStats`) |
| Reading the sideways misses back | `core/.../HitLearner.kt` |
| Learning from the miss | `core/.../HitLearner.kt` |
| Pro entitlement | `app/.../entitlement/Entitlement.kt` |
| Activity plumbing and persistence | `app/.../MainActivity.kt` |
| Coach-screen state | `app/.../ui/CoachScreen.kt` |
| The debug-only Pro switch | `app/src/debug/.../TestProSwitchDebug.kt` |
| Tests | `core/src/test/.../MissedTargetTest.kt` |
| Tests, the whole board | `core/src/test/.../MissedTargetBoardTest.kt` |
| Tests, the geometry | `core/src/test/.../BoardTest.kt` |
| Tests, where the misses go | `core/src/test/.../MissLandingTest.kt` |

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

## The second device test - 15 November 2026

A later session on the same Pixel 7 found the two problems numbered 8 and 9
below. The log of that session is what found them:

```
Heard: '60'
Coach says: You said sixty. Right?
Heard: 'No No'
Coach says: Discarded sixty.
Heard: 'I hit a single 20.'
Coach says: null
Heard: 'I miss triple 20, I hit a single 20.'
Coach says: Tell me the dart you missed first, then what you hit instead.
```

The player reported the turn as a number, threw it away, and then said what
had actually landed. The coach heard the correction perfectly well and
refused it, because there was no dart on the books to replace.

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

### 8. A correction was refused when the turn had no dart on the books

Found on the Pixel 7 on 15 November 2026, in the log of a real session:

```
Heard: 'I miss triple 20, I hit a single 20.'
Coach says: Tell me the dart you missed first, then what you hit instead.
```

The player had named both the dart they missed and what landed, and the
coach asked for the dart they had missed anyway. The reason was not the
words: it was the state. The correction replaces a dart, and there was no
dart to replace, because the player had reported the turn as a number.

That is how most people play, so most corrections were being refused. See
"A turn with nothing on the books" above for what it does now.

### 9. A corrected dart was learned twice

Found by the same device session, and by a core test written to go with the
fix above.

The miss is learned when the correction is made, because that is when it is
known. The turn is learned again when it is confirmed, and the corrected
dart was in both. A player who missed one treble twenty was told they had
missed two.

**Fix:** the learner keeps the darts it has already learned from and does not
learn from them twice. The dart's place in the turn is passed with the
miss, and the list is cleared when the turn is confirmed.

### 10. The landing was learned as if it were the target

Found on the Pixel 7 on 15 November 2026, while checking what the coach had
learned after a session of corrections.

When the player names the target - "I missed treble 20 and hit single 8
instead" - the parser reads it and the target is right. But Whisper does not
always write the target in a form the parser can read: on the device it wrote
"missed treble 20" as "mist-traveled 20" more than once. The correction was
still a correction, because "instead" is in it, so the target had to come from
somewhere else.

It came from the wrong somewhere else. With no readable target, the coach
fell back on the segment the player had reported for that dart - which, on
the second correction of a turn, was the dart that had *landed*. So the
coach learned "the player aims at the single eight and hits it" out of a
correction that was entirely about the treble twenty.

On the device this produced five attempts and five hits at the single eight,
against three attempts and no hits at the treble twenty. The coach's memory
of the leg was the opposite of the truth, and it moved the advice away from
the treble twenty accordingly.

**Fix:** the coach's own aim is used for the target, and the reported segment
is only used when there is no aim for that dart. The aim is what the player
was aiming at, which is what a target is. The test is
`a correction with an unreadable target is learned against the aim`.

This is also why the settings screen now has a **Forget what I've learned**
button: a memory that can be poisoned needs a way to throw it away. See
`docs/HANDOVER.md`.

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
| "I missed treble 20 and hit single eight instead" | "mist-traveled 20 and hit single eight instead" | The target could not be read - see problem 10 |

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

### 8. Bare score reports - decided

If the player reports a turn as a bare total, the app does not know which
individual dart was thrown or missed. A correction can still replace the last
reported value, but target attribution cannot always be learned correctly.

**The decision, taken 15 November 2026, is to accept the correction.** The
third option - treating bare-total turns as ineligible for correction - was
rejected because it made the feature useless for most of the play: most
players report a turn as a number, so most corrections were being refused.

The two cases are handled as described in "A turn with nothing on the books"
above:

- a total waiting to be confirmed is corrected as a number, and read back;
- a turn with nothing on the books records the landing as the first dart.

The learning is weaker than it is for a turn reported dart by dart, because
the target has to be inferred from the coach's own aim rather than read off
the player's report. Where the target cannot be inferred at all, the coach
says so and asks for it rather than guessing.

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
- a correction with no dart on the books recording the landing;
- a correction of a pending total correcting that total;
- a correction that does not add up being refused;
- a corrected dart being learned once rather than twice;
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

