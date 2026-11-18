#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Fetch the models the coach needs into the asset pack.

.DESCRIPTION
    Three kinds of model, all running on the device:

      silero_vad.onnx       voice activity detection
      whisper base.en       speech to text
      piper voices          text to speech, one for each voice the player
                            can choose

    They are published as release assets by sherpa-onnx. The URLs below were
    checked against the live release, not guessed: the Piper voices in
    particular are published as "vits-piper-<voice>.tar.bz2", and the int8
    variant is a third of the size of the float32 one for the same voice.

    The Windows coach uses base.en and the en_US-lessac-medium Piper voice.
    The Android app ships that one and a British one, and the player picks
    between them in the settings.

.PARAMETER OutDir
    Where to put the models. Defaults to the asset pack.

.PARAMETER Whisper
    Which Whisper to fetch: base.en, tiny.en, small.en.
#>
[CmdletBinding()]
param(
    [string] $OutDir = (Join-Path (Split-Path -Parent $PSScriptRoot) 'models-pack/src/main/assets'),
    [ValidateSet('tiny.en', 'base.en', 'small.en')]
    [string] $Whisper = 'base.en'
)

$ErrorActionPreference = 'Stop'

$Asr = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models'
$Tts = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models'

# The voices the coach can speak with.
#
# Each one is a Piper VITS model in the int8 variant, published by
# sherpa-onnx as "vits-piper-<voice>-int8". The int8 model is a third of the
# size of the float32 one for the same voice, which matters: every voice in
# here is around 20 MB and the pack is delivered with the app.
#
# The app's CoachVoice enum holds the espeak phoneme voice that goes with
# each of these. They have to agree: a British voice phonemised with the
# American phonemes is pronounced with American vowels.
$PiperVoices = @(
    'en_GB-alba-medium',
    'en_US-lessac-medium'
)

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$tmp = Join-Path $env:TEMP "darts-models-$([guid]::NewGuid().ToString('N').Substring(0,8))"
New-Item -ItemType Directory -Path $tmp -Force | Out-Null

function Get-Remote {
    param([string] $Url, [string] $Dest)
    if (Test-Path $Dest) {
        Write-Host "  have $(Split-Path -Leaf $Dest)"
        return
    }
    Write-Host "  fetching $(Split-Path -Leaf $Dest)"
    Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
}

function Expand-ArchiveBzip2 {
    param([string] $Archive, [string] $Dest)
    # tar.exe on Windows handles bzip2.
    & tar.exe -xjf $Archive -C $Dest
    if ($LASTEXITCODE -ne 0) { throw "could not unpack $Archive" }
}

try {
    Write-Host "=== voice activity detection ==="
    Get-Remote "$Asr/silero_vad.onnx" (Join-Path $OutDir 'silero_vad.onnx')

    Write-Host ""
    Write-Host "=== Whisper $Whisper ==="
    $whisperArchive = "sherpa-onnx-whisper-$Whisper.tar.bz2"
    $whisperZip = Join-Path $tmp $whisperArchive
    Get-Remote "$Asr/$whisperArchive" $whisperZip
    $whisperDir = Join-Path $tmp "sherpa-onnx-whisper-$Whisper"
    Expand-ArchiveBzip2 $whisperZip $tmp
    foreach ($f in @("$Whisper-encoder.int8.onnx", "$Whisper-decoder.int8.onnx", "$Whisper-tokens.txt")) {
        $src = Join-Path $whisperDir $f
        if (-not (Test-Path $src)) { throw "missing $f in the Whisper archive" }
        Copy-Item $src $OutDir -Force
    }

    Write-Host ""
    Write-Host "=== Piper voices ==="
    foreach ($voice in $PiperVoices) {
        $archive = "vits-piper-$voice-int8.tar.bz2"
        $zip = Join-Path $tmp $archive
        Get-Remote "$Tts/$archive" $zip
        $piperDir = Join-Path $tmp "vits-piper-$voice-int8"
        Expand-ArchiveBzip2 $zip $tmp

        # The voice goes in its own directory, with its tokens file beside it.
        # That is the layout sherpa-onnx expects: getOfflineTtsConfig takes a
        # model directory and a model name.
        #
        # The espeak-ng data in the archive is deliberately NOT copied. Since
        # sherpa-onnx v2.0.0 the text frontend is the piper-phonemize library,
        # which bundles its own copy of the espeak data. The upstream build
        # script drops it for the same reason, and it is 17 MB.
        $voiceDir = Join-Path $OutDir "vits-piper-$voice-int8"
        if (Test-Path $voiceDir) { Remove-Item $voiceDir -Recurse -Force }
        New-Item -ItemType Directory -Path $voiceDir -Force | Out-Null
        Copy-Item (Join-Path $piperDir "$voice.onnx") $voiceDir -Force
        Copy-Item (Join-Path $piperDir "$voice.onnx.json") $voiceDir -Force
        Copy-Item (Join-Path $piperDir 'tokens.txt') (Join-Path $voiceDir 'tokens.txt') -Force
        Write-Host "  have $voice"
    }

    Write-Host ""
    Write-Host "=== models are in $OutDir ==="
    Get-ChildItem $OutDir | Where-Object { -not $_.PSIsContainer } |
        Sort-Object Length -Descending |
        ForEach-Object { "  {0,-40} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB) }
}
finally {
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
}