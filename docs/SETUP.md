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

See `docs/BUILDING.md` for the full procedure. In outline:

1. Clone `k2-fsa/sherpa-onnx`.
2. Build the shared library for Android with the NDK toolchain:

   ```
   cmake -B build-android-arm64 \
     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
     -DANDROID_ABI=arm64-v8a \
     -DANDROID_PLATFORM=android-26 \
     -DSHERPA_ONNX_ENABLE_TTS=ON \
     -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF \
     -DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF \
     .
   ```

   `SHERPA_ONNX_ENABLE_TTS=ON` is required: the Piper voice is a VITS model,
   and the TTS code is not built by default.

3. Copy the resulting `libsherpa-onnx-jni.so` into
   `app/src/main/jniLibs/arm64-v8a/`.
4. Copy the Kotlin API sources from `sherpa-onnx/kotlin-api/` into
   `core/src/main/kotlin/com/k2fsa/sherpa/onnx/`.

The `core` Gradle module depends on the sherpa-onnx Kotlin classes but not on
the native library, so `core` tests run on the JVM without the NDK.

## Fetch the models

```
models/fetch-models.sh
```

This downloads into `app/src/main/assets/`:

| File | Purpose | Size |
|---|---|---|
| `silero_vad.onnx` | voice activity detection | ~2 MB |
| `tiny.en-encoder.int8.onnx` + decoder + tokens | Whisper ASR | ~120 MB |
| `base.en-encoder.int8.onnx` + decoder + tokens | Whisper ASR (default) | ~250 MB |
| `en_US-lessac-medium.onnx` + tokens | Piper TTS voice | ~63 MB |
| `espeak-ng-data/` | phoneme data for Piper | ~10 MB |

Whisper `base.en` is the default because that is what the Windows coach uses.
`tiny.en` is about half the size and noticeably worse at number words - see
`PLAN.md` Phase 1.

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
