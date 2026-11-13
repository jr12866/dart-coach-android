# Phase 3 spike: the echo gate

The last structural unknown. Does the coach hear itself speak on a phone,
where the speaker and the microphone are centimetres apart in the same
enclosure?

## The measurement

The spike plays a phrase through the speaker while the microphone stays open,
and reports the level at the microphone. Three runs:

| Run | Room floor (mean) | Room floor (peak) | Echo (mean) | Echo (peak) |
|---|---|---|---|---|
| 1 | 0.00048 | 0.00365 | 0.00158 | 0.02810 |
| 2 | 0.00050 | 0.00372 | 0.00161 | 0.02897 |
| 3 | 0.00048 | 0.00372 | 0.00149 | 0.02443 |

Stable across the runs, which is what makes it usable.

## What it means

**The coach's own voice is about 8 times louder than the room at the
microphone** - 0.0016 against 0.0005 on the mean, and 0.028 against 0.0037 on
the peak.

That is loud enough to be a problem. The Windows build's VAD calibrated a
threshold at 0.0150 against a laptop mic whose speech sat well above its
floor. Here the echo peaks at 0.028, which is **above** that threshold. So
left alone, the coach would hear its own reply as speech and transcribe it.

But the margin is narrower than it looks, and that is the useful part:

- The echo **mean** is 0.0016, which is 3x the room floor.
- The echo **peak** is 0.028, which is 8x the room floor's peak.

Speech at the oche, spoken at arm's length from a phone, is measured
elsewhere in this project at a floor of about 0.0023 and speech peaks around
0.09. The echo is loud enough to cross a speech threshold, and quiet enough
that a speech threshold set above it will still catch a spoken score.

## What this means for the design

**The half-duplex gate is not optional.** It has to be there, and it has to be
tight.

The Windows build's gate is: mute the microphone before speaking, unmute
after an echo tail. That works here too, and the numbers say the echo tail
cannot be too short - the phone's speaker is 10 cm from its microphone, so
the tail is the mechanical ringing of the speaker, not room reverb.

The practical shape:

1. Mute the microphone before the audio reaches the speaker.
2. Play the audio.
3. Wait for the tail - measured, not guessed.
4. Unmute.

**A VAD threshold alone would not be enough.** The echo peak of 0.028 sits
above where a threshold would have to be to catch a spoken score, so a
threshold-only design would put the coach's own voice inside the speech band.

## With headphones, the problem mostly goes away

Measured with Bose QuietComfort 35 headphones connected:

| | Phone speaker | Bose headphones |
|---|---|---|
| Room floor (mean) | 0.00048 | 0.00048 |
| Echo (mean) | 0.00158 | 0.00034 |
| Echo (peak) | 0.02810 | 0.00193 |

The echo peak drops by a factor of 14.5 and ends up **below the room
floor**: with headphones on, the coach cannot hear itself at all, because
its voice goes to the headphones and never reaches the phone's microphone.

The gate still has to exist - the player might take the headphones off, and
the phone's own speaker case still needs it - but the echo tail can be much
shorter when headphones are connected. See `docs/BLUETOOTH.md`.

## The alternative, and why it is worse

Android's `AcousticEchoCanceler` could remove the echo without a gate. But
the Windows build deliberately left the browser's `echoCancellation` **off**,
because the recogniser was tuned against raw room audio and the browser's
processing reshapes digits. The same reasoning applies here: an echo
canceller that is good at removing the coach's voice will also be good at
removing parts of the player's voice.

So: measure it, but do not depend on it.

## What was needed to get the voice working

Three things, all found by reading the upstream sherpa-onnx Android app
rather than guessing:

**1. The Piper voice needs a text frontend.** The model was trained on
phoneme codes, not on text. The `piper-phonemize` library does the
conversion and is a separate dependency:
`com.github.csukuangfj.piper-phonemize:piper-phonemize:v1.5.9`. It is on
**JitPack**, not Maven Central, so the repository has to be added.

The flow is phonemise, then pass the codes in through
`GenerationConfig.phonemeCodepoints`:

```kotlin
val phonemes = piperPhonemizeText(text, "en-us")
val codes = Array(phonemes.numSentences) { i -> phonemes.getPhonemes(i) }
val audio = tts.generateWithConfig(text, GenerationConfig(phonemeCodepoints = codes))
```

**2. The espeak-ng data is not needed.** The first attempt failed with:

```
phoneme_codepoints is empty, no lexicon, and no frontend available.
Please provide phoneme_codepoints via GenerationConfig or a lexicon via --vits-lexicon.
```

The espeak-ng directory was in the APK - all 355 files of it - but sherpa-onnx
has **ignored `data_dir` since v2.0.0**, because the phonemizer bundles its
own copy. The upstream build script says so and deletes it for the same
reason. It was 17 MB of nothing, and it is now gone.

**3. The voice goes in its own directory.** `getOfflineTtsConfig` takes a
model directory and a model name, with the tokens file beside the model:

```
assets/vits-piper-en_US-lessac-medium-int8/
    en_US-lessac-medium.onnx
    en_US-lessac-medium.onnx.json
    tokens.txt
```

## The numbers the TTS produced

- Sample rate 22050 Hz.
- "You're at 247. Try treble twenty, treble twenty and double five." is
  80128 samples, which is 3.6 seconds.

That is a fair length for a coaching callout, and it sets the lower bound on
how long the microphone has to stay muted.