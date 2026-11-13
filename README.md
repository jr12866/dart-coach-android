# Darts Coach — Android

A voice-controlled 501 darts training aid that runs entirely on the phone.

No laptop, no server, no network. The Pixel 7 captures the microphone,
transcribes the score, tracks the leg, works out what to aim at, and says it
back.

## What this is

This is the Android port of the Windows coach in
[`darts-coach-windows`](https://github.com/jr12866/darts-coach-windows). The
Windows build is a separate repository and stays working; this one is the
phone.

## How it works

```
AudioRecord (16 kHz mono)
    |
    v
sherpa-onnx Vad (silero-vad)        <- replaces the Windows energy VAD
    |  speech segments
    v
sherpa-onnx OfflineRecognizer       <- replaces faster-whisper
    |  transcript
    v
core/ (Kotlin)                      <- ported from the Python src/game/
    |  suggestion text
    v
sherpa-onnx OfflineTts (Piper)      <- replaces piper-tts + PortAudio
    |
    v
AudioTrack
```

All three models run on the device. The app requests no network permission.

## The core is a port, and that is deliberate

The Windows coach's value is its darts knowledge - the checkout routes, the
setup plans, the bogey numbers, the leave that divides down to a double. That
lives in `src/game/darts.py` in the Windows repo, and it is pure Python with no
native dependencies.

It is ported here to Kotlin, file for file, rather than rewritten. The Windows
repo's test suite is the specification; `core/src/test/` mirrors it.

| Windows (`darts-coach-windows`) | Android (`dart-coach-android`) |
|---|---|
| `src/game/darts.py` | `core/.../core/Darts.kt` |
| `src/game/parser.py` | `core/.../core/Parser.kt` |
| `src/game/state.py` | `core/.../core/GameState.kt` |
| `src/main.py` (confirmation flow) | `core/.../core/CoachEngine.kt` |
| `src/audio/vad.py` | replaced by sherpa-onnx `Vad` |
| `src/audio/asr.py` | replaced by sherpa-onnx `OfflineRecognizer` |
| `src/speech/tts.py` | replaced by sherpa-onnx `OfflineTts` |
| `src/phone/server.py` | deleted - the phone *is* the coach |
| `src/llm/reasoner.py` | deleted - the LLM was off by default anyway |

## Project layout

```
core/                 Pure Kotlin game logic. No Android, no sherpa, no
                      dependencies beyond the Kotlin stdlib. Tests live here.
app/                  The Android app: audio, models, Compose UI.
models/               Model files and the script that fetches them.
docs/                 Setup, build notes, and decisions.
tools/                Parity harness against the Windows repo.
PLAN.md               The implementation plan this repo was built from.
skill.md              Strategy: checkout tables, bogey numbers, splits.
scoring.md            Strategy: the setup table and the 350 rule.
```

## Setup

See `docs/SETUP.md`. In short:

1. Install the Android SDK and NDK.
2. Build sherpa-onnx for arm64-v8a (see `docs/BUILDING.md`).
3. Run `models/fetch-models.sh` to download the VAD, Whisper and Piper models.
4. `./gradlew :app:installDebug`

## Strategy

`skill.md` and `scoring.md` are carried over from the Windows repo unchanged.
They are the human-readable statement of the strategy - the checkout tables, the
bogey numbers, the split preferences and the 350 rule.

They are **not** read at runtime. The plan the coach gives comes from the
search in `Darts.kt`, which is the same model that generated `scoring.md` on
the Windows side.

## Status

The plan is in `PLAN.md`. Work has not started.
