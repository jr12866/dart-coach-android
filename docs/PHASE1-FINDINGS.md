# Phase 1 findings: the Pixel 7 hears a darts score

Measured on a Pixel 7 (Tensor G3, Android 16), 13 November 2026.

## The result

**Whisper `base.en` int8 works on the phone, and it is fast.**

| Heard | Audio | Decode time | RTF |
|---|---|---|---|
| `180` | 2.24 s | 0.45 s | 0.20 |
| `180` | 1.41 s | 0.20 s | 0.14 |
| `120` | 1.66 s | 0.19 s | 0.12 |
| `180` | 1.73 s | 0.19 s | 0.11 |
| `Triple 20.` | 1.73 s | 0.25 s | 0.14 |
| `Triple 20, Triple 18, Triple 16.` | 3.71 s | 0.41 s | 0.11 |
| `Double 20.` | 1.47 s | 0.23 s | 0.16 |
| `New game.` | 1.73 s | 0.24 s | 0.14 |
| `What's my score?` | 1.98 s | 0.29 s | 0.14 |
| `I prefer double 20.` | 2.75 s | 0.30 s | 0.11 |
| `bust` | 1.15 s | 0.19 s | 0.16 |
| `Yes.` | 1.41 s | 0.21 s | 0.15 |

**RTF 0.11 to 0.20**, on four threads. The coach answers before the player has
finished putting the darts down.

For comparison, the sherpa-onnx documentation measures Whisper `tiny.en`
int8 at **RTF 0.386 on a Raspberry Pi 4**. `base.en` on a Pixel 7 is several
times better than `tiny.en` on a Pi.

## The three words that had broken before

The Windows coach had three specific failures. All three are fixed here.

| Spoken | Windows | Pixel 7 |
|---|---|---|
| "one eighty" | `One 18.` - scored 19 | `180` |
| "bust" | `Bus.` - invalid | `bust` |
| "yes" | `Yes.` - invalid | `Yes.` |

The first is the important one. On the laptop, "one eighty" was heard as
"One 18.", which the parser reads as 19 rather than 180 - a legal score, so
nothing refused it, and the turn was simply wrong. The Windows build coped
with it in the parser, with the rule that the second number has to be 20 or
more for "one eighty" to mean 180.

**On the phone it is not needed.** Whisper `base.en` hears "one eighty" as
`180` outright.

That is not because the phone is a better microphone. It is a different
recogniser: `faster-whisper` on the laptop versus Whisper through
sherpa-onnx, with different decode settings and a different segmentation
around the phrase. The Windows rule stays in the parser either way, because
it is harmless and it protects against a phrase that does get misheard.

## The command words

All the commands the coach understands were recognised correctly:

| Spoken | Heard |
|---|---|
| "new game" | `New game.` |
| "what is my score" | `What's my score?` |
| "I prefer double 20" | `I prefer double 20.` |
| "bust" | `bust` |
| "yes" | `Yes.` |

## The decision this settles

**Whisper `base.en` int8 is the recogniser.** The fallback was a Zipformer
transducer, because sherpa-onnx's hotwords feature would fix the "one eighty"
problem properly - but only for transducer models, and Whisper is not one.

The hotwords are not needed: the problem does not occur with `base.en`. The
Zipformer path is dropped, which removes a whole branch from the plan and
keeps the parser's assumptions intact.

## What was measured and what was not

Measured on the device, with the phone held at arm's length:

- Real-time factor for Whisper `base.en` int8 on four threads.
- Recognition of the scores, the commands and the answers.

Not yet measured:

- The echo gate. The coach has not spoken on the phone yet, so the
  half-duplex gate is untested. This is the next thing to do, and it is the
  one that behaves differently from the laptop: on a phone the speaker and
  the microphone are centimetres apart in the same enclosure.
- Recognition at the oche, with the darts board and other room noise.
- Whether `tiny.en` would be good enough, which would halve the model size.
  Only worth asking if `base.en` proves too big for the install.

## The spike code

`app/src/main/java/com/jr12866/dartcoach/MainActivity.kt` is the spike. It
captures 16 kHz mono, runs the silero VAD, and transcribes each phrase,
printing the transcript and the RTF.

It is a measuring instrument, not the coach. It gets replaced in Phase 3.

To run it:

```
./gradlew :app:assembleDebug -PwithLocalModels
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s DartsCoach
```

`-PwithLocalModels` copies the models into the app's own assets, because the
Play Asset Delivery pack only arrives through the Play Store. A debug APK
installed with `adb install` never gets the pack.