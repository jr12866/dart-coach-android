# Port Darts Coach to run entirely on a Pixel 7 (offline, self-contained)

## Goal

The coach runs with no laptop, no network and no server: the Pixel 7 captures the
microphone, transcribes, parses, scores, and speaks. Nothing is left running on
the Windows machine.

## Verdict

Feasible, and the right way to do it is **native Android (Kotlin) + sherpa-onnx**,
not a Python-on-Android runtime.

### Why sherpa-onnx

One library covers all three of the hard parts, with official Kotlin APIs and
Android arm64 support:

| Piece | sherpa-onnx class | Model |
|---|---|---|
| VAD | `Vad` (silero-vad) | `silero_vad.onnx` (~2 MB) |
| ASR | `OfflineRecognizer` | Whisper `tiny.en` / `base.en` ONNX |
| TTS | `OfflineTts` | Piper `en_US-lessac-medium` ONNX |

Confirmed available:
- `sherpa-onnx/kotlin-api/Vad.kt`, `OfflineRecognizer.kt`, `Tts.kt` — real
  Kotlin data classes + JNI bindings, `AssetManager` constructors.
- Android arm64 listed as supported; pre-built APKs exist for ASR and TTS.
- Piper models converted and hosted by sherpa-onnx (`tts-models` release tag).
- Whisper tiny.en int8 RTF **0.386 on a Raspberry Pi 4**. The Pixel 7's Tensor G3
  (2x Cortex-X1) is far faster, so `base.en` int8 should be comfortable.

### Why not Python on Android (Termux / Pydroid)

`faster-whisper` (CTranslate2) and `piper-tts` have **no Android wheels**. A
Termux port still needs native builds of ONNX Runtime + a CTranslate2-equivalent,
plus a resampler and an audio backend. That is more work than writing the app,
and it leaves the game logic in a language whose Android support is weakest.
Native Kotlin gets us `AudioRecord`, `AudioTrack`, and a maintained JNI library
for free.

## Architecture

```
┌──────────────────────── Pixel 7 (one APK, no network) ────────────────────────┐
│                                                                                │
│  AudioRecord (16 kHz mono, 4096-frame reads)                                   │
│      ↓                                                                         │
│  sherpa-onnx Vad  ──→  segments (FloatArray)                                   │
│      ↓                                                                         │
│  sherpa-onnx OfflineRecognizer (Whisper base.en int8)                          │
│      ↓ transcript string                                                       │
│  ┌────────────────────────────────────────────────────────────┐                │
│  │  shared/core  (pure Python stdlib — ported to Kotlin)      │                │
│  │    parser.py  → ParseResult                               │                │
│  │    darts.py   → planner(), checkout routes, setup plans    │                │
│  │    state.py   → GameState, turns, preferred double         │                │
│  │    confirmation rules from main.py                         │                │
│  └────────────────────────────────────────────────────────────┘                │
│      ↓ suggestion text                                                         │
│  sherpa-onnx OfflineTts (Piper)  ──→  AudioTrack                              │
│                                                                                │
│  Jetpack Compose UI: remaining score, suggestion, "You said 140, is that     │
│  right?", buttons for New Game / Undo / confirm                                 │
└────────────────────────────────────────────────────────────────────────────────┘
```

### The shared core: what is portable and what is not

**Portable as-is (pure stdlib, zero native deps) — port this to Kotlin:**

| File | Lines | Notes |
|---|---|---|
| `src/game/darts.py` | 712 | The planner. The whole value of the coach. |
| `src/game/parser.py` | 452 | Number-word parsing, `is_confirmation`/`is_cancellation` |
| `src/game/state.py` | 44 | `GameState`, `Turn` |
| `src/game/persistence.py` | 39 | JSON save/load → swap for DataStore/SharedPreferences |
| `src/display/state.py` | 66 | Scoreboard state (uses `threading` → swap for Kotlin state holder) |

Only `re`, `json`, `dataclasses`, `pathlib`, `typing` are imported. Nothing here
touches audio, models, or the network.

**Not portable — rewrite against sherpa-onnx:**

| Windows | Android |
|---|---|
| `src/audio/vad.py` (energy VAD, 1295 lines) | sherpa `Vad` (silero) — *simpler and better* |
| `src/audio/asr.py` (faster-whisper) | sherpa `OfflineRecognizer` |
| `src/speech/tts.py` (piper + PortAudio) | sherpa `OfflineTts` + `AudioTrack` |
| `src/main.py` (coach loop) | Kotlin `CoachEngine` |
| `src/phone/server.py` | *deleted* — the phone **is** the coach now |
| `src/llm/reasoner.py` | *deleted* — LLM was already off by default; the search produces the numbers |

**Not needed on the phone:** the LLM (`--use-llm` was off by default; the
checkout/setup tables from `darts.py` are the source of truth), Ollama, Tailscale,
HTTPS, and the whole `src/phone/` websocket transport.

### Two risks to plan around

**Risk 1 — ASR quality drops a model tier.** Windows uses `base.en`; sherpa
offers `tiny.en`/`base.en`/`small.en`. Whisper `base.en` int8 on the Pixel 7
should be real-time, but this must be *measured* on the device, not assumed.

**Risk 2 — "one eighty" → "One 18."** (your Step 13 finding) was a
*vocabulary* problem, not a capture problem. Two mitigations, both worth having:
- sherpa-onnx **hotwords/contextual biasing** — but note the docs are explicit:
  *"Only transducer models support hotwords... All other models don't."*
  Whisper is **not** a transducer, so hotwords are **unavailable** for it. This
  mitigation does not apply if we use Whisper.
- The alternative is a **Zipformer transducer** (e.g.
  `sherpa-onnx-streaming-zipformer-en-2023-02-17`, 20M params, English) which
  *does* support hotwords with `modified_beam_search`. This is the escape hatch
  if Whisper mishears the number words.

**This is the one genuine fork in the road and needs an early spike** (see
Phase 1).

## Phases

### Phase 0 — Settle the repo layout (small, do first)

**Decided: a separate repository, `C:\git\dart-coach-android`**, created 2026-11-13.
The Windows repo `darts-coach-windows` stays intact and working; it is not
touched by this work.

Rationale: the Python and Kotlin codebases have genuinely different toolchains
(Gradle/NDK vs pytest/venv), and keeping them apart lets the Windows build stay
green without any Android tooling installed.

The new repo's layout:

```
dart-coach-android/
├── core/                  Pure Kotlin game logic (no Android, no sherpa).
│                          JUnit tests live here.
├── app/                   The Android app: audio, models, Compose UI.
├── models/                Model files + fetch script.
├── docs/                  Setup, build notes, ADR-style decisions.
├── skill.md               Carried over from the Windows repo (strategy doc).
├── scoring.md             Carried over from the Windows repo (strategy doc).
├── PLAN.md                This plan.
└── README.md
```

`skill.md` and `scoring.md` are copied verbatim from the Windows repo. They are
the human-readable statement of the strategy and are **not** read at runtime —
the plan comes from the search in `Darts.kt`, the same model that generated
`scoring.md` on the Windows side.

**Parity rule:** the Kotlin core is a port, so it needs its own tests. Port the
assertions from `tests/test_darts.py`, `test_parser.py`, `test_state.py`,
`test_gameplay_rules.py`, `test_preferred_double.py` to Kotlin/JUnit, driven
from the same fixtures. The existing Python tests remain the reference spec.

### Phase 1 — Spike: can the Pixel 7 hear a darts score? (1–2 days)

**This is the make-or-break phase. Do it before writing any of the app.**

Goal: prove, on the real Pixel 7, that the number words survive the round trip.

1. Build sherpa-onnx from source for Android arm64 (per
   `k2-fsa.github.io/sherpa/onnx/android/build.html`). This needs the NDK.
   Budget real time here — it is the longest single unknown.
2. Bundle `silero_vad.onnx`, Whisper `base.en` int8, and Piper
   `en_US-lessac-medium` in `assets/`.
3. Minimal app: `AudioRecord` → sherpa `Vad` → sherpa `OfflineRecognizer` →
   print the transcript to logcat. No UI, no game logic.
4. **Measure, in this order:**
   - RTF on the Pixel 7 for `tiny.en` vs `base.en` vs `small.en` int8, 4 threads.
   - Recall on a fixed corpus: the 14 phrases from your own `session.wav`
     corpus, plus live spoken at the oche.
   - Specifically: does "one eighty" transcribe as `180` or `One 18.`?
   - Does "bust" survive? (your Step 13 found `Bus.` → invalid on the laptop;
     your `_is_bust()` already handles the bare `bus.` spelling, so confirm the
     Kotlin port keeps that.)
5. **Decision gate:** if `base.en` mishears number words at the oche, evaluate
   the Zipformer transducer + hotwords path in Phase 1b before proceeding.

Output of this phase: a measured table (model → RTF → transcript accuracy), and
a go/no-go on Whisper vs Zipformer.

### Phase 2 — Port the game core to Kotlin (2–3 days)

Port `src/game/` to Kotlin. Keep it a straight, literal port — no cleverness,
because the point is parity with a heavily-tuned Python original.

- `Darts.kt` ← `darts.py`: the planner, `route_to_double`, `double_speech`,
  `number_to_words`, `planner()` cache.
- `Parser.kt` ← `parser.py`: `ParseResult`, number extraction, the
  `single`/`double`/`triple` qualifier rules, `is_confirmation`,
  `is_cancellation`, `DESTRUCTIVE_COMMANDS`.
- `GameState.kt` ← `state.py`: `GameState`, `Turn`, `preferred_double`.
- `CoachEngine.kt` ← the confirmation state machine from `src/main.py`:
  `pendingScore`, `pendingCommand`, the wake-word handling, the
  `_is_unanswered_conversation` rule, and the read-back-before-counting
  behaviour.

**The confirmation rules are the part to be most careful about.** Your codebase
has earned those rules through live failures:
- Nothing counts on the first pass; a score is read back and waits for a "yes".
- `new game` / `undo` need **"coach yes"**, not a bare "yes" (a 440 Hz tone was
  transcribed as "I'm going to go back" and undid a real turn).
- `one eight` vs `one eighteen` disambiguation — the rule that the second number
  must be ≥ 20. Port it exactly, including the reasoning in the comment.

Add a **golden-file parity harness**: a JSON corpus of (input → expected
`ParseResult` + expected spoken reply), generated from the Python side, and a
Kotlin test that asserts identical output. That is how you know the port is
faithful without reading 1200 lines of Kotlin twice.

### Phase 3 — The app (3–5 days)

- **Audio:** `AudioRecord` at 16 kHz mono, `AudioRecord.getMinBufferSize` sized
  for ~64 ms frames, read on a dedicated thread. Note your Windows VAD expects
  16 kHz / 1024-frame chunks — keep that shape so the timing constants stay
  comparable.
- **TTS + the half-duplex gate:** this is the subtle part. `PiperTTS` currently
  mutes the mic during playback with a 0.35 s echo tail. On Android the phone
  speaker is right next to the phone mic, so **the echo problem is worse, not
  better**. Use `AudioRecord` + `AudioTrack` with the same `VOICE_COMMUNICATION`
  source, and keep the mic muted from the moment TTS starts until playback
  finishes plus the echo tail. Consider `AcousticEchoCanceler` on the capture
  session, but measure it — the Windows path explicitly left
  `echoCancellation` off because the recogniser was tuned against raw audio.
- **UI (Jetpack Compose):** big remaining score, the suggestion, the
  read-back question, and buttons for New Game / Undo / Confirm. Portrait,
  screen-on (`FLAG_KEEP_SCREEN_ON`), and a wake lock — the phone is at the oche
  and the screen must not sleep mid-leg.
- **Persistence:** `GameState` to `SharedPreferences`/DataStore as JSON, same
  shape as `persistence.py`, so a saved leg resumes.
- **No network permission at all.** That is the proof it is self-contained.

### Phase 4 — Polish and verify on the oche (2–3 days)

- Test a full 501 leg standing at the board, phone in hand.
- Test the failure modes your Windows build already taught you about:
  - screen lock mid-leg → does the mic keep feeding?
  - the coach hearing itself speak (the echo gate).
  - backgrounding the app (another app takes the mic → mic loss).
  - a phone call interrupting.
- Compare behaviour against the Windows coach on the same phrases.

## Effort estimate

| Phase | Time | Risk |
|---|---|---|
| 0. Repo layout | 0.5 d | Low |
| 1. Sherpa-onnx + ASR spike | 1–2 d | **High** — native build + ASR quality |
| 2. Kotlin core port | 2–3 d | Medium — parity, but mechanical |
| 3. The app | 3–5 d | Medium — echo gate, lifecycle |
| 4. On-the-oche verification | 2–3 d | Medium — real-world behaviour |
| **Total** | **~9–14 days** | |

Roughly **2–3 weeks**. The Windows build stays intact and usable throughout, so
there is always a working fallback.

## Open questions

1. ~~**Repo layout**~~ — **decided: separate repo `C:\git\dart-coach-android`.**
2. **Whisper vs Zipformer** — decided by the Phase 1 spike. If Whisper `base.en`
   mishears "one eighty" at the oche, switch to a Zipformer transducer, which
   supports hotwords and would fix the vocabulary problem properly.
3. **LLM on the phone?** — currently off by default and the numbers come from
   the search, so the answer is probably "no". Adding a small on-device LLM
   (e.g. via MediaPipe LLM Inference / llama.cpp) is possible on a Pixel 7 but
   is scope creep. Recommend: skip it, same as today.
4. **Does the phone need to also be the scoreboard for other people?** — the
   current Windows setup serves a browser scoreboard. On the phone this is
   just the app's own UI. If you want a second screen (a tablet at the bar),
   that is a different feature.

## What stays the same

- `skill.md` and `scoring.md` are copied verbatim into the new repo. The Android
  app does **not** need them at runtime — the plan comes from `darts.py` — but
  they stay as the human-readable documentation of the strategy.
- `scripts/generate_scoring.py` stays in the **Windows** repo, where it
  regenerates `scoring.md` from the Python search. The Kotlin port and the
  Python planner must never disagree; the parity harness in Phase 2 is what
  catches it if they do.
- The Windows coach keeps working, unchanged, throughout. There is always a
  working fallback while the Android build is developed.

## Not doing

- No LLM on the phone (see Q3).
- No phone-as-webcam, no video.
- No multi-player scoring.
- No cloud sync of game state.
