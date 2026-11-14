# Port Darts Coach to a Pixel 7 — self-contained, offline after first run

> **Where this is and what is left: `docs/HANDOVER.md`.**

## Goal

The coach runs on the phone: the Pixel 7 captures the microphone, transcribes,
parses, scores, and speaks. No laptop, no server. Published on Google Play, so
it installs like any other app. The models are downloaded once; after that the
app works with no network at all.

## Verdict

Feasible. The right way is **native Android (Kotlin) + sherpa-onnx**, with the
models delivered as a **Play Asset Delivery** pack.

### Why sherpa-onnx

One library covers all three hard parts, with official Kotlin APIs and Android
arm64 support:

| Piece | sherpa-onnx class | Model |
|---|---|---|
| VAD | `Vad` (silero-vad) | `silero_vad.onnx` (~2 MB) |
| ASR | `OfflineRecognizer` | Whisper `base.en` ONNX |
| TTS | `OfflineTts` | Piper `en_US-lessac-medium` ONNX |

Verified upstream:
- `sherpa-onnx/kotlin-api/` contains `Vad.kt`, `OfflineRecognizer.kt`, `Tts.kt` —
  real Kotlin classes over JNI bindings, with `AssetManager` constructors.
- Android `arm64-v8a` is a supported platform.
- Pre-built APKs exist for ASR and TTS.
- Piper voices are converted and hosted by sherpa-onnx (`tts-models` release).
- Whisper tiny.en int8 runs at **RTF 0.386 on a Raspberry Pi 4**. The Pixel 7's
  Tensor G3 is far faster, so `base.en` int8 should be comfortable.

### Why not Python on Android (Termux / Pydroid)

`faster-whisper` (CTranslate2) and `piper-tts` have **no Android wheels**. A
Termux port still needs native ONNX Runtime + a CTranslate2-equivalent, plus a
resampler and an audio backend. More work than writing the app, and it leaves
the game logic in the language with the weakest Android story.

---

## Model delivery — the Play Store answer

### The size limits, as verified against Google's documentation

| Component | Compressed download limit |
|---|---|
| Base module | **500 MB** |
| Individual feature modules | 500 MB |
| Individual asset packs | **1.5 GB** |
| Cumulative, all modules + install-time packs | 4 GB |
| Cumulative, on-demand + fast-follow packs | 30 GB |

The 200 MB figure is the **legacy APK** limit for apps still publishing APKs
instead of app bundles. An app bundle gets 500 MB for the base module alone.

**This corrects an earlier assumption.** The first draft of this plan treated
200 MB as the hard ceiling and concluded the app had to be sideloaded. That was
wrong, and it was the wrong kind of wrong — it would have pushed the whole
design toward a download-and-verify-the-checksum scheme for no reason.

Our assets:

| Asset | Size |
|---|---|
| Whisper `base.en` int8 (encoder + decoder + tokens) | ~250 MB |
| Piper `en_US-lessac-medium` | ~63 MB |
| silero-vad | ~2 MB |
| espeak-ng data | ~10 MB |
| **Total** | **~325 MB** |

That fits in a **single install-time asset pack** with room to spare, and the
base module (the app itself, with the Kotlin code and the `sherpa-onnx-jni.so`
native library) stays small — a few MB.

### The three delivery modes

| Mode | When | Contributes to store size? | Needs download code? |
|---|---|---|---|
| `install-time` | With the app install | Yes | **No** — read via `AssetManager` |
| `fast-follow` | Automatically after install | No | Yes — `AssetPackManager` |
| `on-demand` | While the app runs | No | Yes — `AssetPackManager` |

**Recommendation: `install-time`.**

The app installs and the models are there. There is no download screen, no
progress bar, no "first launch failed" state, no resume logic, no checksum
verification to write. The model paths are read with the ordinary
`AssetManager`, exactly as they would be if they were in the app's own
`assets/`.

That is a *much* simpler app than on-demand delivery, and it is worth being
explicit about why: the models are 325 MB, and a user who has just installed a
darts coach wants to throw darts, not watch a progress bar. The friction of
on-demand delivery buys nothing here because the app has no use until the
models arrive anyway.

Trade-off accepted: the install is ~325 MB, and Play shows users a warning
dialog for apps over 200 MB when on mobile data. That is honest and expected —
it is a large app because it contains a speech recogniser and a voice.

### If on-demand turns out to be wanted later

The Gradle change is one line (`deliveryType`), and the API is
`AssetPackManager`:

```kotlin
val location = assetPackManager.getPackLocation("models")
    ?: return null   // not downloaded yet
val assetsPath = location.assetsPath()
```

The catch, from Google's docs, is worth knowing in advance:

> The app can't assume the existence of these files or their locations because
> these files may be deleted by the user or moved by the Play Asset Delivery
> Library across play sessions.

So the model loader must check for the pack **at every launch**, not cache the
path. And with `install-time` this problem does not exist at all, because the
models live in the app's own asset directory.

### Fallback if Play publishing stalls

A plain HTTPS download from GitHub Releases, with a manifest carrying
`sha256` per asset and resumable transfers. This is the plan for a non-Play
distribution and it is deliberately *not* the primary path — it means writing
download code, a progress UI, checksum verification, resume handling, and a
"models not ready" state that the app must handle at startup.

### PAD for a non-game app

The Play Asset Delivery documentation is written for games, but the mechanism
is general: an `com.android.asset-pack` Gradle plugin, an asset pack module,
and the `asset-delivery-ktx` runtime library. Nothing in it is game-specific.

---

## Architecture

```
┌──────────────────── Pixel 7 (one app, one install) ─────────────────────┐
│                                                                          │
│  AudioRecord (16 kHz mono)                                               │
│      ↓                                                                   │
│  sherpa-onnx Vad  ──→  speech segments                                   │
│      ↓                                                                   │
│  sherpa-onnx OfflineRecognizer (Whisper base.en int8)                    │
│      ↓ transcript                                                        │
│  ┌────────────────────────────────────────────────────────┐              │
│  │  core/ (pure Kotlin — ported from the Python src/game/) │              │
│  │    Darts.kt      → planner, checkout routes, setup plans │              │
│  │    Parser.kt     → ParseResult, number words             │              │
│  │    GameState.kt  → turns, preferred double               │              │
│  │    CoachEngine.kt→ the confirmation state machine        │              │
│  └────────────────────────────────────────────────────────┘              │
│      ↓ suggestion text                                                   │
│  sherpa-onnx OfflineTts (Piper)  ──→  AudioTrack                        │
│                                                                          │
│  Compose UI: remaining score, suggestion, read-back question, buttons   │
└──────────────────────────────────────────────────────────────────────────┘
```

### What is portable and what is not

**Portable as-is — pure stdlib Python, port these to Kotlin:**

| File | Lines | Notes |
|---|---|---|
| `src/game/darts.py` | 712 | The planner. The whole value of the coach. |
| `src/game/parser.py` | 452 | Number-word parsing, `is_confirmation`/`is_cancellation` |
| `src/game/state.py` | 44 | `GameState`, `Turn` |
| `src/game/persistence.py` | 39 | JSON save/load → DataStore |
| `src/display/state.py` | 66 | Scoreboard state |

Only `re`, `json`, `dataclasses`, `pathlib`, `typing`. Nothing touches audio,
models or the network.

**Not portable — replace:**

| Windows | Android |
|---|---|
| `src/audio/vad.py` (1295 lines, energy VAD) | sherpa `Vad` (silero) — simpler and better |
| `src/audio/asr.py` (faster-whisper) | sherpa `OfflineRecognizer` |
| `src/speech/tts.py` (piper + PortAudio) | sherpa `OfflineTts` + `AudioTrack` |
| `src/main.py` (coach loop) | Kotlin `CoachEngine` |
| `src/phone/server.py` | deleted — the phone **is** the coach |
| `src/llm/reasoner.py` | deleted — LLM was off by default; the search produces the numbers |

---

## Two risks to plan around

**Risk 1 — ASR quality drops a model tier.** Windows uses `base.en`; sherpa
offers `tiny.en`/`base.en`/`small.en`. Whisper `base.en` int8 on the Pixel 7
should be real-time, but this must be *measured* on the device.

**Risk 2 — "one eighty" → "One 18."** (the Windows Step 13 finding) was a
*vocabulary* problem, not a capture problem. Two mitigations:
- sherpa-onnx **hotwords/contextual biasing** — but the docs are explicit:
  *"Only transducer models support hotwords... All other models don't."*
  Whisper is **not** a transducer, so hotwords are unavailable for it.
- The alternative is a **Zipformer transducer** (e.g.
  `sherpa-onnx-streaming-zipformer-en-2023-02-17`, 20M params, English) which
  *does* support hotwords with `modified_beam_search`.

**This was the one genuine fork in the road, and Phase 1 settled it.** Measured
on the Pixel 7, Whisper `base.en` int8 hears "one eighty" as `180`, "bust" as
`bust` and "yes" as `Yes.` - all three of the words that had broken on the
laptop - at **RTF 0.11 to 0.20**. The hotwords that would have fixed the
vocabulary problem are not needed, so the Zipformer path is dropped and the
parser's assumptions stay intact. See `docs/PHASE1-FINDINGS.md`.

---

## Phases

### Phase 0 — Repo layout (done)

Separate repository, `C:\git\dart-coach-android`, created 2026-11-13 and
pushed to https://github.com/jr12866/dart-coach-android.

The Windows repo `darts-coach-windows` stays intact and working; it is not
touched by this work and remains a working fallback.

```
dart-coach-android/
├── core/                Pure Kotlin game logic. No Android, no sherpa.
│                        JUnit tests here.
├── app/                 The Android app: audio, models, Compose UI.
├── models-pack/         The PAD asset pack module (Phase 5).
├── models/              Model files + fetch script.
├── docs/                SETUP, BUILDING, PARITY, DECISIONS, PUBLISHING.
├── corpus/              Parity corpus (Python generates it).
├── skill.md  scoring.md Strategy docs, carried over verbatim.
├── PLAN.md  README.md
```

`skill.md` and `scoring.md` are the human-readable statement of the strategy
and are **not** read at runtime — the plan comes from the search in `Darts.kt`.

**Parity rule:** the Kotlin core is a port, so it needs its own tests. Port the
assertions from `tests/test_darts.py`, `test_parser.py`, `test_state.py`,
`test_gameplay_rules.py`, `test_preferred_double.py` to Kotlin/JUnit, driven
from the same fixtures. The Python tests remain the reference spec.

### Phase 1 — Spike: can the Pixel 7 hear a darts score? (1–2 days) — **DONE**

**Measured on the Pixel 7. Whisper `base.en` int8 works, at RTF 0.11-0.20, and
it hears "one eighty" as `180`, "bust" as `bust` and "yes" as `Yes.` - all
three of the words that had broken on the laptop. See
`docs/PHASE1-FINDINGS.md` for the numbers.**

1. Build sherpa-onnx for `arm64-v8a` (see `docs/BUILDING.md`). Needs the NDK.
   Budget real time — the longest single unknown.
2. Bundle silero-vad, Whisper `base.en` int8, Piper `en_US-lessac-medium`.
3. Minimal app: `AudioRecord` → sherpa `Vad` → sherpa `OfflineRecognizer` →
   print the transcript to logcat. No UI, no game logic.
4. **Measure:**
   - RTF on the Pixel 7 for `tiny.en` / `base.en` / `small.en` int8.
   - Recall on the 14-phrase corpus from the Windows `session.wav`, plus live
     speech at the oche.
   - Does "one eighty" become `180` or `One 18.`?
   - Does "bust" survive? (Windows Step 13 found `Bus.` → invalid; the
     `_is_bust()` rule already handles the bare `bus.` spelling — confirm the
     port keeps it.)
5. **Gate:** if `base.en` mishears number words, evaluate the Zipformer
   transducer + hotwords path before going further.

Output: a measured table (model → RTF → accuracy) and a go/no-go.

### Phase 2 — Port the game core to Kotlin (2–3 days) — **DONE**

Straight, literal port — no cleverness, because the point is parity with a
heavily-tuned original.

- `Darts.kt` ← `darts.py`: planner, `route_to_double`, `double_speech`,
  `number_to_words`, the `planner()` cache.
- `Parser.kt` ← `parser.py`: `ParseResult`, number extraction, the
  `single`/`double`/`triple` qualifier rules, `is_confirmation`,
  `is_cancellation`, `DESTRUCTIVE_COMMANDS`.
- `GameState.kt` ← `state.py`: `GameState`, `Turn`, `preferred_double`.
- `CoachEngine.kt` ← the confirmation state machine from `src/main.py`.

**The confirmation rules need the most care.** They exist because of live
failures, not theory:

- Nothing counts on the first pass; a score is read back and waits for a "yes".
- `new game` / `undo` need **"coach yes"**, not a bare "yes". A 440 Hz tone was
  transcribed as "I'm going to go back" and undid a real turn.
- `one eighteen` vs `one eighty` — the second number must be 20 or more.
  Port it exactly, including the reasoning in the comment.

A **golden-file parity harness**: a JSON corpus of (input → expected
`ParseResult` + expected spoken reply), generated from the Python side, with a
Kotlin test asserting identical output. See `docs/PARITY.md`.

### Phase 3 — The app (3–5 days) - in progress - mostly done - done

- **Audio:** `AudioRecord` at 16 kHz mono, buffer sized for ~64 ms frames, read
  on a dedicated thread. The Windows VAD expects 16 kHz / 1024-frame chunks —
  keep that shape so the timing constants stay comparable.
- **TTS + the half-duplex gate:** done. Piper through sherpa-onnx,
  with the piper-phonemize library for the text frontend. The gate
  mutes the microphone for the length of the speech plus an echo
  tail, because the coach would otherwise hear itself.
- **UI (Compose):** remaining score, suggestion, read-back question, buttons
  for New Game / Undo / Confirm. Portrait, screen-on
  (`FLAG_KEEP_SCREEN_ON`), wake lock — the phone is at the oche and the screen
  must not sleep mid-leg.
- **Persistence:** `GameState` to DataStore as JSON, same shape as
  `persistence.py`, so a saved leg resumes.

- **The Piper voice needs `piper-phonemize`** for its text frontend, from
  JitPack. The espeak-ng data that ships with the voice is not used:
  sherpa-onnx has ignored `data_dir` since v2.0.0.

### Phase 4 — Polish and verify on the oche (2–3 days) - partly done

- A full 501 leg standing at the board, phone in hand. **Not done.**
- The failure modes the Windows build already taught us:
  - screen lock mid-leg → does the mic keep feeding?
  - the coach hearing itself speak (the echo gate). **Measured on both
    routes: 0.35 s on the phone's speaker, 0 s on Bluetooth headphones.**
  - backgrounding the app (another app takes the mic → mic loss).
  - a phone call interrupting.
- Compare against the Windows coach on the same phrases.
- The echo tail still has to be measured standing at the board, where the
  room is not a quiet desk.

### Phase 5 — Play Store packaging (2–3 days) - repository work done, console work pending

- **PAD asset pack module** (`models-pack/`): `com.android.asset-pack` plugin,
  `deliveryType = "install-time"`, assets under `src/main/assets/`. **Done.**
- **App module:** `assetPacks += listOf(":models-pack")`, and an app-bundle
  publishing configuration. **Done.**
- **Signing:** a `release` signing configuration reading the keystore path and
  passwords from `local.properties`. The keystore itself stays outside the
  repository. **Done.**
- **Play Console:** a developer account (one-time $25), the store listing
  (icon, screenshots, a privacy policy URL), and the **Data safety** form.
  The copy, graphics, and answers are prepared in `docs/STORE-LISTING.md`;
  uploading and filling in the form still need the account.
- **Target API:** `targetSdk = 35`; check
  https://developer.android.com/google/play/requirements/target-sdk before
  shipping.
- **Privacy policy:** written in `docs/PRIVACY-POLICY.md`, published from
  `docs/store/privacy-policy.html`, and bundled in the app with a **Privacy
  policy** button. **Done.**

**Play review note.** A microphone-using app attracts scrutiny. The claims are
kept simple and true: on-device ASR, no data transmitted, no analytics, no ads.
Because the app has no `INTERNET` permission, the Data safety answer is "no
data is collected" under Google's definition.

---

## Effort estimate

| Phase | Time | Risk |
|---|---|---|
| 0. Repo layout | done | — |
| 1. sherpa-onnx + ASR spike | 1–2 d | **done** — RTF 0.11-0.20, all words correct |
| 2. Kotlin core port | 2–3 d | **done** — all 7663 corpus cases match |
| 3. The app | 3-5 d | **done** - works on the Pixel 7 |
| 4. On-the-oche verification | 2-3 d | **routes measured** — full leg still to do |
| 5. Play Store packaging | 2-3 d | **repository done** — console upload pending |
| **Total** | **~9–16 days** | |

The Windows build stays intact throughout, so there is always a working
fallback.

---

## Open questions

1. ~~**Repo layout**~~ — **decided: separate repo `C:\git\dart-coach-android`.**
2. ~~**Whisper vs Zipformer**~~ — **decided: Whisper `base.en` int8.** Measured
   on the Pixel 7: "one eighty" is `180`, "bust" is `bust`, "yes" is `Yes.`,
   at RTF 0.11-0.20. The hotwords that would have fixed the vocabulary
   problem are not needed, and the Zipformer path is dropped.
3. ~~**Where the assets live**~~ - **decided: a Play Asset Delivery pack,
   `install-time`.** The models ship inside the Play install.
   no progress UI, no checksums to write.
4. **LLM on the phone?** — currently off by default and the numbers come from
   the search, so the answer is "no". A small on-device LLM is feasible on a
   Pixel 7 but is scope creep. Recommend: skip it, same as today.
5. **Does the phone need to be the scoreboard for other people?** — the Windows
   setup serves a browser scoreboard. On the phone this is just the app's own
   UI. A second screen (a tablet at the bar) is a different feature.

---

## What stays the same

- `skill.md` and `scoring.md` are carried over verbatim. Not read at runtime —
  the plan comes from `darts.py`/`Darts.kt`.
- `scripts/generate_scoring.py` stays in the **Windows** repo, where it
  regenerates `scoring.md` from the Python search. The Kotlin port and the
  Python planner must never disagree; the parity harness catches it if they do.
- The Windows coach keeps working, unchanged, throughout.

## Not doing

- No LLM on the phone (see Q4).
- No phone-as-webcam, no video.
- No multi-player scoring.
- No cloud sync of game state.
- No analytics, no ads, no crash reporting that leaves the device.
