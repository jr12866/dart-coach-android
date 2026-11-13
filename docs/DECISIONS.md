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

## 8. The APK will be too large for the Play Store

**Decision.** Sideload the APK. Do not publish it.

**Why.** The models live in `assets/` so they are available offline with no
download step, which is the whole point. Whisper `base.en` int8 is around
250 MB and the Piper voice around 63 MB. With both ASR models, the APK is
well over the 200 MB Play Store limit.

**Alternatives.** On-demand model download (Play Asset Delivery) would fix the
size but reintroduce a network dependency, which contradicts the goal of
running with no network at all. Shipping `tiny.en` only would roughly halve it
but would cost ASR accuracy on exactly the number words that matter.

**What would reverse it.** Nothing, unless the app is published, in which case
the models would need to move to Play Asset Delivery and the "works with no
network" property would become "works with no network after the first run".
