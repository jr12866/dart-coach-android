# Building sherpa-onnx for Android

This is the longest single unknown in the project, so it was done first and in
isolation. It is now **done and verified**: the native library builds, the
Kotlin API compiles, and the app assembles and bundles.

## What the build produces

| Output | Size | Where it goes |
|---|---|---|
| `libsherpa-onnx-jni.so` | 85.7 MB | `app/src/main/jniLibs/arm64-v8a/` |
| `libonnxruntime.so` | 21.2 MB | `app/src/main/jniLibs/arm64-v8a/` |
| `libc++_shared.so` | 1.7 MB | `app/src/main/jniLibs/arm64-v8a/` |
| 23 Kotlin API classes | - | `app/src/main/java/com/k2fsa/sherpa/onnx/` |

In the packaged APK the native libraries are compressed, so they add about
26 MB rather than 108 MB.

## Running it

```
git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx.git third_party/sherpa-onnx
./tools/build-sherpa.ps1
```

The script does the whole thing: downloads ONNX Runtime, configures CMake,
compiles, installs, and copies the results into the app.

It needs, from the Android SDK:

```
sdkmanager "ndk;27.2.12479018" "cmake;3.31.6"
```

## The versions matter, and these are the ones that work

| | Why |
|---|---|
| NDK **27.2.12479018** | The version the sherpa-onnx F-Droid reproducible-builds document names. |
| CMake **3.31.6** | CMake 3.22.1 links with `-fuse-ld=gold`, which NDK r27 removed in favour of LLD. The link step fails with `invalid linker name`. |
| ONNX Runtime **1.28.2** | The version the upstream build script uses. |
| Generator **Ninja** | CMake defaults to NMake Makefiles on Windows, and then fails because nmake is not installed. Ninja ships inside the SDK's cmake package. |

## Things that will bite you

**`libc++_shared.so` has to travel with the library.** `libsherpa-onnx-jni.so`
is linked against the shared C++ runtime, so it must be in the APK as well.
Without it the app dies at startup with `UnsatisfiedLinkError`, which is a
confusing way to discover that one file is missing. The build script copies it
from the NDK.

**The Kotlin API goes in the app module, not the core one.** The classes take
an `android.content.res.AssetManager`. The `core` module is deliberately pure
JVM so the game logic can be tested without Android, and putting them there
breaks that.

**ONNX Runtime must live outside the build directory.** The generator is
baked into the CMake cache, so a reconfigure has to clear the build
directory. If ONNX Runtime is extracted inside it, clearing the directory
deletes it too. The build script puts it in `third_party/sherpa-onnx/`.

**PowerShell treats native stderr as an error.** With
`$ErrorActionPreference = 'Stop'`, a deprecation warning from the NDK's own
toolchain file kills the build. The script sets it to `Continue` and checks
exit codes explicitly.

## The CMake flags, and why

```
-DANDROID_ABI=arm64-v8a         the Pixel 7 and every phone worth buying
-DANDROID_PLATFORM=android-26   the app's minSdk
-DANDROID_STL=c++_shared        the Kotlin API expects the shared STL
-DSHERPA_ONNX_ENABLE_TTS=ON     the Piper voice is a VITS model
-DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF
                                upstream defaults this on; the coach has no
                                use for it and it is real build time
-DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF
                                Android uses AudioTrack, not PortAudio
-DSHERPA_ONNX_ENABLE_BINARY=OFF no command line tools on a phone
```

## What was verified

- Both native libraries are **ELF64 AArch64** shared objects, checked with
  `llvm-readelf`.
- `libsherpa-onnx-jni.so` needs `libonnxruntime.so`, `libc++_shared.so`,
  `libandroid.so`, `liblog.so`, `libm.so`, `libdl.so` and `libc.so`. All are
  either packaged or part of Android itself.
- `:app:assembleDebug` and `:app:bundleRelease` both succeed.
- The release bundle is **153 MB**: a base module of 26 MB compressed (66 MB on
  the device) and a models pack of 117 MB compressed (189 MB on the device).