# Darts Coach Pro — the paid tier

The plan for turning Darts Coach into a paid app: a **free coaching tier** and
a **Pro tier** bought with a one-time in-app purchase.

The Pro features are built and gated **from the first release**. Nothing is
shipped free first and paid later: once a player has seen the personalisation,
there is no way to take it back, and the free tier would never be worth
upgrading to.

---

## The shape of it

| | Free | Pro |
|---|---|---|
| Report scores, read-back, undo, new game | yes | yes |
| Live suggestion at population averages | yes | yes |
| Preferred double, for the current leg | yes | yes |
| **Personalised hit rates** — probabilities from your own accuracy | | yes |
| **Out Chart** — every score 2..170, with routes and percentages | | yes |
| **Setup coaching** — what to aim at from 171..350 | | yes |
| **Preferred double that persists** across legs and restarts | | yes |
| **Session stats** — real checkout %, per-segment accuracy | | yes |
| **Missed-target corrections** — replace the last dart and re-plan the turn | | yes |

**The line: the free coach knows darts. The Pro coach knows you.**

The free tier is a working coach, not a demo. That is deliberate. In a
Play-driven market the free tier is the only thing most reviewers and the
ranking algorithm ever see, so a thin free tier costs rating volume, and
rating volume is what makes the next install cheap. The Pro tier is gated on
*depth* — the chart, the personalisation, the history — not on whether the app
works.

### The free tier, in detail

| | Free | Pro |
|---|---|---|
| Scorekeeping, read-back, undo, new game | ✓ | ✓ |
| Live suggestion at the population averages | ✓ | ✓ |
| Preferred double, for the current leg | ✓ | ✓ |
| Out Chart browse, 2..170 | | ✓ |
| Personalised probabilities | | ✓ |
| Setup coaching, 171..350 | | ✓ |
| Preferred double that persists | | ✓ |
| Session stats | | ✓ |
| Missed-target corrections | | ✓ |

Two rules for the free tier, and they matter more than the list:

1. **The free tier must not show precise percentages.** If free says "T20
   T20 D20 — 52%" and Pro says "T20 T20 D20 — 63% (your numbers)", the free
   tier is a working demo of the paid thing and people stay on it. The free
   tier gives the *plan* — which dart to throw — but does not promise a number
   it cannot personalise.
2. **The Out Chart is locked.** It is a reference, not something needed
   mid-leg, and it is the feature most likely to be the reason someone buys.

The soft gate is also the one that can be reversed. If the conversion turns
out to be too low, moving one feature from Pro to free is a single release —
and the people who already paid keep what they bought. A hard gate has no such
move.

### Decisions taken

| Decision | Choice | Why |
|---|---|---|
| Model | One-time IAP | The value is a static algorithm and a static dataset. A subscription over that churns into refunds. |
| Price | **$2.99** | The 325 MB install is paid *before* the paywall is seen. Small prices convert better at that point. |
| Free tier | **Soft gate** | The free tier coaches the live leg; Pro adds the depth. In a Play-driven market the free tier is the only thing most reviewers and the ranking algorithm see, so a demo-tier costs rating volume, and rating volume is what makes the next install cheap. |
| Hit rates | Learned by default, manual override | Manual-only is 40 sliders nobody touches. Learned-only is nonsense for the first few sessions. |
| Checkout | Play Billing only | Keeps "no account" true and needs no server. A direct purchase can be added later and set the same local flag. |
| Missed target | Correct the last dart in the current turn | It is the common case and has an unambiguous voice form. Correcting any dart or a confirmed turn is deferred. |
| Release | Pro features and paywall ship together | Never ship a locked door with nothing behind it. |

**Still open:** the price can change without a release (Play allows it). The
*set of gated features* cannot — that needs a new build. So the gate
configuration is the thing to get right early, which is why every gate goes
behind one `Entitlement` interface.

---

## The rules this plan must not break

1. **The default profile must produce byte-identical output to today's
   `AIMS`.** The aim order breaks ties in the planner search
   (`Planner.kt:64-67`). Change the order and the 7,663 parity cases fail.
2. **The parity corpus is the safety net.** Every change to `core` is proved by
   the existing suite before it is believed.
3. **"No ads" and "no analytics" stay true.** They are the differentiator
   against every ad-supported darts scoreboard.
4. **"No account" stays true.** Play Billing needs no account of ours.
5. **Audio never leaves the phone.** Adding `INTERNET` for billing must not
   change that, and the privacy policy must keep saying so.

---

## Phase 1 — `core`: make the coach personalisable

Pure Kotlin, no Android. Everything here is proved by the JVM test suite.

- [x] **1.1 `PlayerProfile` in `core`**
  A data class holding the player's accuracy: per-treble hit rates, per-double
  hit rates, single / outer bull / double bull rates. Defaults match today's
  constants exactly.
- [x] **1.2 `buildAims(profile)`**
  Refactor `Darts.kt` so the aim list is built from a profile. `AIMS` becomes
  `buildAims(PlayerProfile())` and must be byte-identical to the current list.
  The construction order is preserved exactly: trebles 20→2, Bull, 25, doubles
  20→1, singles 20→1.
- [x] **1.3 Profile-aware `Planner`**
  `Planner(maxScore, profile)`. `Planner.shared` keeps today's behaviour.
  Measured build cost is ~100–170 ms, so the planner can be rebuilt on demand
  whenever a hit rate changes — no background thread, no caching layer.
- [x] **1.4 `CoachEngine` takes an injected planner**
  So the app can hand it a personalised one.
- [x] **1.5 Record what was aimed at, not just what was hit**
  The hit-rate learner needs the segment, not the value. `T20` and `60` mean
  different things to the learner.
- [x] **1.6 Hit-rate learning**
  A running estimate per segment, prior-weighted so early advice stays sane:
  `estimate = (prior × k + hits) / (k + attempts)`, `k ≈ 15`. The population
  defaults pull until there is real evidence.
- [x] **1.7 Tests**
  Profile-driven aims match the default aims exactly; the planner with the
  default profile matches the corpus; a profile that says D16 is weak produces
  routes that prefer D20.

**Gate:** the full `core` suite is green, including all 7,663 parity cases.

> **1.1–1.7 are done.** The suite is 76 tests, all passing, and the corpus
> is unchanged: 501 plan cases, 6,513 route cases, 502 helper cases.
> `ProfileTest` asserts that the default profile produces exactly the same
> aim list, in the same order, and that a default planner matches the shared
> one for every score from 1 to 170.
>
> **Note for 1.4:** `CoachEngine`'s planner is a nullable constructor argument
> resolved in the body, not a default argument. The shared planner is a lazy
> delegate, and a default argument is evaluated at the call site rather than
> when the constructor runs.
>
> **How the learning works.** The coach knows what it aimed at. The player
> reports what landed. The difference between the two is the lesson: a dart
> aimed at D16 that came back as a single eight is a miss on D16, and a dart
> aimed at T20 that landed on T20 is a hit. A turn reported as a bare total
> says nothing about any segment, so it is counted but not attributed.
>
> **Note for 1.5:** `GameState.currentDarts` stays a list of `Int`, because
> the parity corpus checks it. The segments are kept beside it in
> `currentSegments`, and a `Turn` carries both.


---

## Phase 2 — Persistence

Nothing survives a restart today. `GameState` lives in memory
(`MainActivity.kt:63`), and `PLAN.md` already called for DataStore
(`PLAN.md:315-316`).

- [x] **2.1 DataStore for the profile**
  The manual overrides and the learned rates are kept apart. A player who
  has said "I cannot hit D16" still has that after the coach has learned
  something else. The learned rates are not stored at all — they come back
  out of the counts, so the history keeps accumulating.
- [x] **2.2 DataStore for the preferred double**
- [x] **2.3 DataStore for the hit counts**
  The counts are saved rather than the rates, because a rate cannot be
  turned back into a count.
- [x] **2.4 DataStore for the legs**
  The statistics are worked out from the legs that have been won, so they
  have to survive a restart. A leg is recorded when it is finished rather
  than counted turn by turn, so an undo cannot leave a half-finished leg in
  the numbers.

> **2.1–2.3 are done.** `SettingsStore` keeps the manual overrides, the
> counts, and the preferred double. The coach is built from the merged
> profile when the app starts, and the counts are saved after every confirmed
> turn.
>
> **Note:** `PlayerProfile.merge(learned, manual)` is in `core` rather than
> in the app, because it is pure logic and it is worth a test. The player's
> own number for a segment wins over the learned one.


---

## Phase 3 — The Pro screens

There is no navigation library today — `MainActivity.kt:97-116` renders one
`CoachScreen`. This phase adds `navigation-compose` and real screens.

- [x] **3.1 Navigation**: `navigation-compose`, a nav host with five routes
  (coach, out chart, setup, profile, stats) and the paywall
- [x] **3.2 The out chart screen** — every score 2..170, with the route, the
  percentage, the doubles still available, and the routes onto each of the
  player's doubles. The score can be typed in or tapped.
- [x] **3.3 Profile screen** — per-segment hit rates, manual override, and
  where each rate came from. Setting a rate by hand is **free**, because it
  makes the free coach better for that player; the analysis of what the
  coach has learned is Pro.
- [x] **3.4 Setup screen** — the 171..350 strategy table, read from `core`
  rather than the static markdown, so it cannot drift from the coach
- [x] **3.5 Stats screen** — the average turn, the share of legs won, the
  average checkout score, and where the misses are going. The legs are
  recorded when they are won, and kept between sessions.
- [x] **3.6 The `Entitlement` interface**
  One place that decides what is gated. Changing the gate set is a config
  edit, not a refactor.
- [x] **3.7 The paywall screen** — shown at the moment of intent, listing
  what Pro is. The billing behind it is Phase 4.
- [x] **3.8 The settings screen**
  The echo delay, which is measured on the player's own phone. The number
  measured on a Pixel 7 is now only the default; a phone that has not been
  measured uses it, and the player can measure theirs from here.

> **3.1, 3.2, 3.6 and 3.7 are done.**
>
> **The chart is the coach's own advice written down.** `OutChart` in `core`
> reads from the same `Planner` the coach thinks with, so a personalised
> chart is the player's own routes rather than a separate table that can
> drift away from the coach. `OutChartTest` checks the chart against the
> coach's plan for every score from 2 to 170.
>
> **The screens moved out of `MainActivity`** into `app/.../ui/`, so that
> the activity is about audio and the plumbing and the screens are about
> darts. `CoachScreen`, `OutChartScreen`, `PaywallScreen` and `AppNavHost`
> are separate files there, and the shared colours live in one place.
>
> **Note:** `CoachEngine.planner` is now public, so the out chart can be
> built from the same planner the coach is using rather than the population
> one.
>
> **3.3 is done.** `ProfileView` in `core` assembles what the screen shows:
> the rate the coach is using, the rate it learned, the rate the player set,
> and how much evidence there is behind it. `ProfileViewTest` covers it.
>
> **Two counts, not one.** `HitStats` keeps where the darts *landed* and
> how many landed *on the bed they were aimed at*. The second is the number
> that matters to a player: a dart aimed at D16 that dropped into the single
> eight is a miss, even though the dart landed on something. The overall
> rate is computed from the second count.
>
> **The profile screen is mostly free.** Setting a hit rate by hand is free,
> because it makes the free coach better for that player and stops them
> having to wait for the coach to work out a weakness over several legs.
> What is Pro is the *analysis* - the overall on-target rate and where the
> learning came from. This is the `LEARNED_ANALYSIS` feature flag.
>
> **3.4 is done.** `SetupChart` in `core` reads from the same `Planner` the
> coach thinks with, so the chart and the coach always agree. The notes are
> the strategy - the part the planner cannot work out, because a planner can
> say what leaves 170 but not that 170 is the good one.
>
> **The table in `scoring.md` is checked by a script**,
> `tools/check-setup-chart.py`. It reads the 180 rows of the setup table and
> checks that they run contiguously from 350 to 171 and that every row has a
> note that agrees with the score it leaves. The Kotlin test checks the
> chart against the planner.
>
> **3.5 is done.** `Stats` and `StatsView` in `core` work out the numbers
> from the legs the player has won. A leg is recorded when it is finished
> rather than counted turn by turn, so an undo cannot leave a half-finished
> leg in the numbers. The legs are saved and restored by `LegsSnapshot`.
>
> **The statistics are Pro** (`SESSION_STATS`), because they are the
> clearest proof of what the coach has learned about the player. The profile
> screen is mostly free, and the statistics are the bit that is worth
> paying for.
>
> **3.8 is done.** `TailSetting` in `core` works out how long to wait from
> the measured samples. The measurement itself is the one the hidden intent
> already ran, so it was refactored into a method that returns the samples
> rather than only logging them.
>
> **The measurement is not the mean.** It is the *longest* of the three
> samples plus a quarter, because a tail that is too short makes the coach
> cut itself off and a tail that is too long only costs a moment of dead
> time. The floor is 50 ms, so a measurement of a millisecond is a very
> short tail rather than no tail.


---

## Phase 4 — Play Billing

- [x] **4.1 `com.android.billingclient:billing-ktx` dependency** (9.1.0)
- [x] **4.2 `INTERNET` permission**, with the reasoning written down in the
  manifest itself
- [x] **4.3 `BillingRepository`** — query purchases on launch, expose
  `isPro`, read the price from Play
- [x] **4.4 Entitlement cached locally**, so the app works if Play's cache is
  unavailable
- [x] **4.5 Paywall UI** — shown at the moment of intent, listing what Pro is
- [x] **4.6 Restore purchases** — a button on the coach screen and in the
  paywall
- [x] **4.7 The free tier stays ungated and un-nagged**

> **The product id is `dartcoach_pro`**, and it has to match the product set
> up in the Play Console exactly.
>
> **There is no server.** The purchase lives in the Play Store, the app asks
> Play what the account owns, and the answer is cached on the phone. That is
> what keeps "no account" true.
>
> **The purchase is acknowledged.** A non-consumable that is not acknowledged
> is refunded by Play after three days and the entitlement is taken away, so
> `acknowledgePurchase` is called on a completed purchase.
>
> **The gate is a list, not a flag.** `PRO_FEATURES` names what is behind the
> paywall, and the screens ask `entitlement.has(feature)` rather than
> `isPro`. Moving a feature out of the paywall is a one-line edit.
>
> **Missed-target corrections are Pro.** The player says what landed instead
> of the intended target; the coach replaces the last dart and re-plans only
> the darts remaining in the turn, labelled as a Checkout or a Setup. The
> implementation and Pixel 7 findings are in `docs/MISSED-TARGET.md`.

---

## Phase 5 — Documentation

- [x] **5.1 `docs/PRIVACY-POLICY.md`** — the Pro purchase, the saved
  settings, and the narrowed network claim
- [x] **5.2 `docs/STORE-LISTING.md`** — "Digital purchases: yes", the Pro
  description, and the Data safety answers: purchase history is collected,
  nothing else is
- [x] **5.3 `docs/PUBLISHING.md`** — the billing setup, the product id, how
  to test the purchase, and what the network permission costs
- [x] **5.4 `docs/DECISIONS.md`** — entries 11 and 12
- [x] **5.5 `app/src/main/AndroidManifest.xml`** — the `INTERNET` permission,
  with the reasoning in the file
- [x] **5.6 The in-app privacy line** — "no ads" stays, the purchase is
  mentioned
- [x] **5.7 `README.md`** — what Pro is
- [x] **5.8 The privacy policy is generated**
  `docs/PRIVACY-POLICY.md` is the source, and a script builds the public page
  and the copy bundled in the app from it, so the three cannot drift apart.

---

## Phase 6 — Ship

- [ ] **6.1 Version name and code** — `0.2.0`, version code 2
- [ ] **6.2 Play Console** — the IAP product, its price, and the listing
- [ ] **6.3 Internal testing first** — the 325 MB install is the thing to
  test over mobile data
- [ ] **6.4 The pre-launch report**, read rather than skimmed
- [ ] **6.5 A rollback note** — how to make the app free again if the paywall
  turns out to be wrong, and how to change the price without a release

---

## Risks

| Risk | What is done about it |
|---|---|
| The paywall is wrong on the first release | Every gate behind one `Entitlement` interface; the price can change without a release; the gate set is one config edit |
| An empty paywall ships ahead of the features | The phases are ordered so the features and the paywall ship together |
| The free tier is too thin and the ratings suffer | The free tier is a working coach, and the gate set can be widened in one release if needed |
| The default profile drifts from the ported behaviour | The parity corpus gates every `core` change; 1.2 asserts byte-identical aims |
| The audio promise is weakened by adding `INTERNET` | The manifest still has no code that sends audio anywhere; the policy keeps saying so; the claim is narrowed rather than withdrawn |
