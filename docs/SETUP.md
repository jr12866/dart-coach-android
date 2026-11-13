# Setup

## Prerequisites

| | |
|---|---|
| Android Studio | Ladybug or newer, with the NDK installed |
| NDK | r26 or newer (sherpa-onnx needs a recent one) |
| JDK | 17 |
| CMake | via Android SDK, or standalone |
| Device | Pixel 7 (arm64-v8a). Other arm64 devices should work but are unverified. |

Check the NDK version in use:

```
sdkmanager --list | grep ndk
```

## Build sherpa-onnx for Android

sherpa-onnx is not published as a prebuilt AAR. It is built from source for
`arm64-v8a` and produces the `sherpa-onnx-jni` native library plus the Kotlin
API classes.

```
sdkmanager "ndk;27.2.12479018" "cmake;3.31.6"
git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx.git third_party/sherpa-onnx
./tools/build-sherpa.ps1
```

The script downloads ONNX Runtime, configures CMake, compiles, and copies the
native libraries into `app/src/main/jniLibs/arm64-v8a/` and the Kotlin API into
`app/src/main/java/com/k2fsa/sherpa/onnx/`.

See `docs/BUILDING.md` for the versions that matter and the things that will
bite you.


## Fetch the models

```
models/fetch-models.sh
```

This downloads into `models-pack/src/main/assets/`, which is the Play Asset
Delivery pack:

| File | Purpose | Size |
|---|---|---|
| `silero_vad.onnx` | voice activity detection | 0.6 MB |
| `base.en-encoder.int8.onnx` + decoder + tokens | Whisper ASR (default) | 153 MB |
| `tiny.en-encoder.int8.onnx` + decoder + tokens | Whisper ASR (smaller) | ~120 MB |
| `en_US-lessac-medium.onnx` + tokens | Piper TTS voice | 17.7 MB |
| `espeak-ng-data/` | phoneme data for Piper | 17.2 MB |

Total **~189 MB**, which fits in a single install-time asset pack (the limit is
1.5 GB per pack, 500 MB for the base module). See `docs/DECISIONS.md` entry 8.

The int8 variants are used throughout. The Piper voice in particular is 17.7 MB
int8 against 64 MB in float32, for the same voice.

Whisper `base.en` is the default because that is what the Windows coach uses.
`tiny.en` is about half the size and noticeably worse at number words - see
`PLAN.md` Phase 1.

For local development, the pack's assets are also readable directly: an
install-time pack is served as split APKs, so a debug build of the app finds
the models through the ordinary `AssetManager`.

## Build and install

```
./gradlew :app:installDebug
```

## Verify

Launch the app and check the logcat filter:

```
adb logcat -s DartsCoach
```

The startup banner should report the model paths it loaded and the sample rate
it is running at.
