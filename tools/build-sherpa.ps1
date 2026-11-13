#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build sherpa-onnx for Android arm64-v8a.

.DESCRIPTION
    sherpa-onnx is not published as an AAR. It has to be cross-compiled with
    the NDK, and the result is a native library plus a set of Kotlin API
    classes that get copied into this project.

    The upstream recipe is build-android-arm64-v8a.sh in the sherpa-onnx
    repository. This is the same build, driven from PowerShell, because the
    upstream script expects bash and a Unix-style environment.

.PARAMETER SdkRoot
    The Android SDK. Defaults to $env:ANDROID_HOME.

.PARAMETER NdkVersion
    The NDK to build against. The default is the version the sherpa-onnx
    F-Droid reproducible-builds document names.

.PARAMETER Jobs
    Parallel compile jobs.

.EXAMPLE
    ./tools/build-sherpa.ps1
#>
[CmdletBinding()]
param(
    [string] $SdkRoot = $env:ANDROID_HOME,
    [string] $NdkVersion = '27.2.12479018',
    [string] $OnnxRuntimeVersion = '1.28.2',
    [int]    $Jobs = [Environment]::ProcessorCount
)

# Native build tools write warnings to stderr. With ErrorActionPreference set
# to Stop, PowerShell turns those into error records and kills the build on a
# deprecation warning from the NDK's own toolchain file. The exit codes are
# checked explicitly instead.
$ErrorActionPreference = 'Continue'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$SherpaDir = Join-Path $RepoRoot 'third_party/sherpa-onnx'
$BuildDir = Join-Path $SherpaDir 'build-android-arm64-v8a'
$InstallDir = Join-Path $BuildDir 'install'

if (-not $SdkRoot) {
    $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
}
$SdkRoot = $SdkRoot.TrimEnd('\')

$NdkRoot = Join-Path $SdkRoot "ndk/$NdkVersion"
$CmakeExe = Join-Path $SdkRoot 'cmake/3.31.6/bin/cmake.exe'

Write-Host "=== sherpa-onnx for Android arm64-v8a ==="
Write-Host "  SDK:        $SdkRoot"
Write-Host "  NDK:        $NdkRoot"
Write-Host "  CMake:      $CmakeExe"
Write-Host "  ONNX RT:    $OnnxRuntimeVersion"

if (-not (Test-Path $SherpaDir)) {
    throw "sherpa-onnx is not cloned. Run: git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx.git third_party/sherpa-onnx"
}
if (-not (Test-Path $NdkRoot)) {
    throw "The NDK is not installed. Run: sdkmanager 'ndk;$NdkVersion'"
}
if (-not (Test-Path $CmakeExe)) {
    throw "CMake is not installed. Run: sdkmanager 'cmake;3.31.6'"
}

# --- ONNX Runtime -------------------------------------------------------
#
# The prebuilt Android ONNX Runtime is downloaded rather than built: it is
# a large dependency that does not need to be cross-compiled here.
$OrtDir = Join-Path $SherpaDir "onnxruntime-$OnnxRuntimeVersion"
$OrtLib = Join-Path $OrtDir "jni/arm64-v8a/libonnxruntime.so"
$OrtInclude = Join-Path $OrtDir 'headers'

if (-not (Test-Path $OrtLib)) {
    $url = "https://github.com/csukuangfj/onnxruntime-libs/releases/download/v$OnnxRuntimeVersion/onnxruntime-android-$OnnxRuntimeVersion.zip"
    $zip = Join-Path $env:TEMP "onnxruntime-android-$OnnxRuntimeVersion.zip"
    Write-Host "Downloading ONNX Runtime $OnnxRuntimeVersion..."
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    New-Item -ItemType Directory -Path $OrtDir -Force | Out-Null
    Expand-Archive -Path $zip -DestinationPath $OrtDir -Force
    Remove-Item $zip -Force
}
if (-not (Test-Path $OrtInclude)) {
    throw "ONNX Runtime headers are missing from $OrtDir"
}

$env:SHERPA_ONNXRUNTIME_LIB_DIR = Join-Path $OrtDir 'jni/arm64-v8a'
$env:SHERPA_ONNXRUNTIME_INCLUDE_DIR = $OrtInclude

# --- The generator -------------------------------------------------------
#
# The CMake version matters. CMake 3.22.1 links with "-fuse-ld=gold", which
# NDK r27 removed in favour of LLD, so the link step fails with "invalid
# linker name". 3.31.6 works.
#
# CMake picks NMake Makefiles by default on Windows when no generator is
# named, and then fails because nmake is not installed. Ninja ships inside
# the SDK's cmake package, so it is used instead, and the directory holding
# it goes on PATH so CMake can find it.
$CmakeBin = Split-Path -Parent $CmakeExe
if (-not (Test-Path (Join-Path $CmakeBin 'ninja.exe'))) {
    throw "ninja.exe is missing from $CmakeBin"
}
$env:PATH = "$CmakeBin;$env:PATH"

# --- Configure ----------------------------------------------------------
#
# These flags are the upstream ones, with the changes noted:
#
#   SHERPA_ONNX_ENABLE_TTS=ON           upstream default; the Piper voice is a
#                                       VITS model and needs it
#   SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF
#                                       upstream default is ON; the coach has
#                                       no use for it and it is real build time
#   SHERPA_ONNX_ENABLE_PORTAUDIO=OFF     Android uses AudioTrack, not PortAudio
#   SHERPA_ONNX_ENABLE_BINARY=OFF       no command line tools on the phone
#   ANDROID_PLATFORM=android-26         the app's minSdk
#   ANDROID_STL=c++_shared              the Kotlin API expects the shared STL
#
# The generator is baked into the CMake cache, so a directory configured with
# a different one has to be cleared before it can be reconfigured.
if (Test-Path (Join-Path $BuildDir 'CMakeCache.txt')) {
    Write-Host "Clearing the previous CMake cache..."
    Remove-Item $BuildDir -Recurse -Force
}
New-Item -ItemType Directory -Path $BuildDir -Force | Out-Null

$toolchain = Join-Path $NdkRoot 'build/cmake/android.toolchain.cmake'
if (-not (Test-Path $toolchain)) {
    throw "The NDK toolchain file is missing: $toolchain"
}

Write-Host ""
Write-Host "Configuring..."
Push-Location $BuildDir
try {
    & $CmakeExe `
        "-G" "Ninja" `
        "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `r`n        "-DCMAKE_POLICY_VERSION_MINIMUM=3.5" `
        "-DANDROID_ABI=arm64-v8a" `
        "-DANDROID_PLATFORM=android-26" `
        "-DANDROID_STL=c++_shared" `
        "-DCMAKE_BUILD_TYPE=Release" `
        "-DBUILD_SHARED_LIBS=ON" `
        "-DSHERPA_ONNX_ENABLE_TTS=ON" `
        "-DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF" `
        "-DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF" `
        "-DSHERPA_ONNX_ENABLE_BINARY=OFF" `
        "-DSHERPA_ONNX_ENABLE_PYTHON=OFF" `
        "-DSHERPA_ONNX_ENABLE_TESTS=OFF" `
        "-DSHERPA_ONNX_ENABLE_CHECK=OFF" `
        "-DSHERPA_ONNX_ENABLE_JNI=ON" `
        "-DSHERPA_ONNX_ENABLE_C_API=OFF" `
        "-DSHERPA_ONNX_ENABLE_RKNN=OFF" `
        "-DSHERPA_ONNX_ENABLE_QNN=OFF" `
        "-DSHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=OFF" `
        "-DCMAKE_INSTALL_PREFIX=./install" `
        ".."
    if ($LASTEXITCODE -ne 0) { throw "cmake configure failed with exit code $LASTEXITCODE" }

    Write-Host ""
    Write-Host "Building with $Jobs jobs (this takes a while)..."
    & $CmakeExe --build . --config Release --parallel $Jobs --target sherpa-onnx-jni
    if ($LASTEXITCODE -ne 0) { throw "the build failed" }
}
finally {
    Pop-Location
}

# --- Install and collect ------------------------------------------------
& $CmakeExe --install $BuildDir
if ($LASTEXITCODE -ne 0) { throw "cmake install failed" }

$jniLib = Join-Path $InstallDir 'lib/libsherpa-onnx-jni.so'
if (-not (Test-Path $jniLib)) {
    throw "libsherpa-onnx-jni.so was not produced"
}

$dest = Join-Path $RepoRoot 'app/src/main/jniLibs/arm64-v8a'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
Copy-Item $jniLib $dest -Force
Copy-Item $OrtLib $dest -Force

# The shared C++ runtime has to travel with the library.
#
# libsherpa-onnx-jni.so is linked against libc++_shared.so, so it has to be
# in the APK as well. Without it the app dies at startup with
# UnsatisfiedLinkError, which is a confusing way to find out that one file
# is missing.
$stl = Join-Path $NdkRoot 'toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so'
if (-not (Test-Path $stl)) {
    throw "libc++_shared.so was not found in the NDK at $stl"
}
Copy-Item $stl $dest -Force

Write-Host ""
Write-Host "Copied into app/src/main/jniLibs/arm64-v8a/:"
Get-ChildItem $dest | ForEach-Object { "  {0,-32} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB) }

# --- Kotlin API ---------------------------------------------------------
#
# The Kotlin API classes live in the sherpa-onnx repository. They are copied
# in rather than depended on, because they are not published as an artifact.
#
# They go in the app module, not the core one, because they take an
# android.content.res.AssetManager. The core module is deliberately pure
# JVM so that the game logic can be tested without Android.
$kotlinSrc = Join-Path $SherpaDir 'sherpa-onnx/kotlin-api'
$kotlinDest = Join-Path $RepoRoot 'app/src/main/java/com/k2fsa/sherpa/onnx'

if (Test-Path $kotlinSrc) {
    New-Item -ItemType Directory -Path $kotlinDest -Force | Out-Null
    Copy-Item (Join-Path $kotlinSrc '*.kt') $kotlinDest -Force
    Write-Host ""
    Write-Host "Copied the Kotlin API into app/src/main/java/com/k2fsa/sherpa/onnx/:"
    Get-ChildItem $kotlinDest -Filter '*.kt' | ForEach-Object { "  $($_.Name)" }
}
else {
    Write-Warning "The Kotlin API was not found at $kotlinSrc"
}

Write-Host ""
Write-Host "Done."