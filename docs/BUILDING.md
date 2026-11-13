# Building sherpa-onnx for Android

This is the longest single unknown in the project, so it is done first and in
isolation (Phase 1 of `PLAN.md`). Nothing else can be built until the JNI
library exists.

## Why it has to be built

sherpa-onnx publishes prebuilt APKs for demos, and a Python wheel, but not an
AAR that can be dropped into a Gradle project. The native library has to be
cross-compiled with the NDK.

## Verified upstream facts

These were confirmed against the sherpa-onnx sources and docs:

- The Kotlin API lives in `sherpa-onnx/kotlin-api/` and contains `Vad.kt`,
  `OfflineRecognizer.kt`, `Tts.kt`, `OfflineStream.kt` and others. Each is a
  plain Kotlin class over an `external` JNI binding, with constructors that
  take an `android.content.res.AssetManager` so models can be read from
  `assets/`.
- `Vad.kt` loads `sherpa-onnx-jni` via `System.loadLibrary` in a companion
  `init` block, so the `.so` must be in `jniLibs/arm64-v8a/`.
- Android `arm64-v8a` is listed as a supported platform.
- The `Vad` config defaults to `sampleRate = 16000`, which matches the coach.
- `silero_vad.onnx` is published in the `asr-models` release.
- Whisper models are exported to ONNX as a separate encoder and decoder. The
  `tiny.en` int8 pair is ~12 MB + ~105 MB.
- Piper voices are converted to ONNX by sherpa-onnx and published under the
  `tts-models` release tag, together with a shared `espeak-ng-data` archive.

## Procedure

```
git clone --recursive https://github.com/k2-fsa/sherpa-onnx
cd sherpa-onnx
```

Build for arm64-v8a. `ANDROID_PLATFORM` must be 26 or higher.

```
export ANDROID_NDK=$HOME/Android/Sdk/ndk/26.1.10909125
export ANDROID_ABI=arm64-v8a

cmake -B build-android-arm64 \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=$ANDROID_ABI \
  -DANDROID_PLATFORM=android-26 \
  -DSHERPA_ONNX_ENABLE_TTS=ON \
  -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF \
  -DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF \
  -DCMAKE_BUILD_TYPE=Release \
  .

cmake --build build-android-arm64 --target sherpa-onnx-jni -j$(nproc)
```

The output is `build-android-arm64/libsherpa-onnx-jni.so`. Copy it to
`app/src/main/jniLibs/arm64-v8a/`.

## Gotchas

- **TTS is off by default.** `SHERPA_ONNX_ENABLE_TTS=ON` is required or the
  Piper voice will not load. The TTS code also pulls in espeak-ng data.
- **PortAudio is irrelevant on Android.** Leave it off; the app uses the
  Android `AudioTrack` API.
- **Only `arm64-v8a` is needed.** The Pixel 7 is arm64. Building the other
  ABIs doubles the build time for no benefit.
- **Release build.** A Debug build of the native library is several times
  slower, which will make the ASR look far worse than it is.
- **Static vs shared.** The upstream CMake produces a shared library. If the
  Gradle link step complains about missing symbols, check that the Kotlin
  module declares the matching `abiFilters`.

## What to check once it builds

A minimal app that loads the library, reads a WAV from `assets/`, and prints
the transcript to logcat is enough to prove the whole toolchain works. That is
the first thing Phase 1 does.
