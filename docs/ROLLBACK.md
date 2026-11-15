# Rolling back the Pro tier

What can be undone, what cannot, and what each one costs.

The rule that makes the rest of this short: **the price can be changed without
a release; the set of gated features cannot.** Everything below follows from
those two facts.

---

## The one that needs no release: the price

Play Console, then the pricing for the in-app product. Change it, and it
changes for everybody. There is no build, no rollout, no review, and nobody
has to reinstall anything.

---

## The one that needs a release: what is behind the paywall

`PRO_FEATURES` in
`app/src/main/java/com/jr12866/dartcoach/entitlement/Entitlement.kt` is the
list of what is gated. It is the only place that decides.

### Make the app free again

Empty the set:

```kotlin
val PRO_FEATURES: Set<Entitlement.Feature> = emptySet()
```

`FeatureGated.has()` returns true for any feature that is *not* in the gate,
so an empty gate means every feature is free. The screens lock themselves by
asking `entitlement.has(...)`, so with an empty gate nothing shows a lock and
the paywall is never reachable.

The live coach is untouched by this. It was never gated: the scorekeeping,
the read-back, the undo and the suggestion at the oche all work either way.

This is one edit and one release. Everyone who already paid keeps what they
bought, because `Pro` grants everything regardless of what the list says.

### Widen the gate

Add the feature to the `Entitlement.Feature` enum, then to `PRO_FEATURES`.
Same cost: a release.

### Narrow the gate

Remove it from `PRO_FEATURES`. The feature becomes free.

---

## What the gate actually controls, and what it does not

This is worth writing down before the day it is needed, because the list and
the code are not quite the same thing. Three of the eight flags are
**declared but never asked**:

| Flag | Where it is meant to bite | Asked anywhere? |
|---|---|---|
| `OUT_CHART` | the out chart itself | **No** |
| `PERSONALISED_PROBABILITIES` | the percentages in the chart | **No** |
| `PERSISTENT_PREFERENCES` | the preferred double kept across restarts | **No** |
| `PERSONALISED_ROUTES` | the routes onto each double | Yes |
| `LEARNED_ANALYSIS` | the analysis of what the coach has learned | Yes |
| `SETUP_COACHING` | the setup chart | Yes |
| `SESSION_STATS` | the session statistics | Yes |
| `MISSED_TARGET_CORRECTION` | correcting a missed dart | Yes |

So emptying `PRO_FEATURES` today frees the setup chart, the statistics, the
learned analysis, the personalised routes and the missed-target correction.
The out chart is already free, because nothing asks whether it should be.

That is not a rollback problem. It is a gap between `docs/PRO-PLAN.md` and the
build: the plan says the out chart is locked and that the free tier must not
show precise percentages, and neither is true yet. **Fix that before the
first release**, because the whole point of the paywall is that these three
things are what the player is paying for.

The fix is the same shape as everything else here: ask the question in the
screen. `OutChartScreen` already asks about `PERSONALISED_ROUTES`; it needs
to ask about `OUT_CHART` before drawing the chart, and about
`PERSONALISED_PROBABILITIES` before drawing a percentage. The preferred
double needs its persistence gated where it is saved.

---

## The emergency case: the paywall is blocking the coach

The live coach was never behind the paywall, so a paywall that locks the
scorekeeping is a bug, not a policy. What to check, in order:

1. **`PRO_FEATURES`** - is something in it that should not be?
2. **`FeatureGated.has()`** - a feature that is not in the gate is free. If
   this function is wrong, every lock is wrong at once.
3. **The entitlement cache** - the app reads what Play says the account owns
   and caches it, so a Play outage does not take the gate away from someone
   who bought it. A *new* buyer with no network gets the free tier until Play
   answers, which is the intended behaviour and not a bug.
4. **The release build's switch** - the debug test-Pro switch is in
   `src/debug`, and the release source set has a no-op, so a release build
   cannot have Pro turned on by an intent extra. If a release build is
   refusing a purchase that was made, that is a bug in the billing path, not
   in the gate.

The fastest way out is the empty `PRO_FEATURES`: one release, and the app is
free while the real cause is found.

---

## Turning off the sale without a release

The in-app product can be made unavailable in the Play Console. Nobody new
can buy, and nobody who already bought loses anything. That is the right move
while a billing bug is being fixed: it stops the refunds without touching the
build.

Taking the app off the Play Store entirely also stops new purchases, and
existing owners keep their purchase and their entitlement, because Play holds
it.

---

## What cannot be undone

**The purchases.** A non-consumable belongs to the account, not to the
version of the app. There is no way to take Pro away from somebody who bought
it, and there should not be: they paid for it, and the promise was that it
would keep working.

If a purchase has to be taken back, that is a refund, done one purchase at a
time in the Play Console.

**The free tier cannot be un-shipped.** If a feature has been free in a
released version and is then put behind the paywall, the people who used it
will be annoyed. This is why the gate set is supposed to be right the first
time, and why every gate went behind one `Entitlement` interface early: it
is a config edit rather than a hunt through the UI.

---

## The release mechanics

Version code up by one, `bundleRelease`, upload, and use a staged rollout, so
that a bad build can be halted before it reaches everybody.

```powershell
.\gradlew.bat :app:bundleRelease
```

The bundle is unsigned until the four signing properties are in
`local.properties`. See `docs/PUBLISHING.md` for the keystore and the
uploading.

---

## There is no server

There is nothing to roll back on a server, because there is no server. The
purchase lives in the Play Store, the app asks Play what the account owns, and
the answer is cached on the phone. There is no user database, no entitlement
server, and no data to migrate between versions.

That also means there is no way to grant Pro to somebody who did not buy it,
and no way to revoke it from somebody who did, apart from a refund. Both of
those are features rather than limitations.

---

## The check to run before shipping

Ask these two questions, and answer them in writing in the release notes:

1. **If the paywall is wrong in this build, how long until the app is free
   again?** The answer is one edit and one release.
2. **If the price is wrong, how long until it is right?** The answer is a
   change in the Play Console and no build at all.

If either answer is "I am not sure", the rollback has not been thought
through.
