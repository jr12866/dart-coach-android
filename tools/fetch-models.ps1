#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Fetch the models the coach needs into the asset pack.

.DESCRIPTION
    Three models, all running on the device:

      silero_vad.onnx       voice activity detection
      whisper base.en       speech to text
      piper lessac-medium  text to speech

    They are published as release assets by sherpa-onnx. The URLs below were
    checked against the live release, not guessed: the Piper voice in
    particular is published as "vits-piper-<voice>.tar.bz2", and the
    int8 variant is a third of the size of the float32 one for the same
    voice.

    The Windows coach uses base.en and the en_US-lessac-medium Piper voice,
    so this port matches it rather than choosing new ones.

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
    Write-Host "=== Piper voice (en_US-lessac-medium, int8) ==="
    $piperArchive = 'vits-piper-en_US-lessac-medium-int8.tar.bz2'
    $piperZip = Join-Path $tmp $piperArchive
    Get-Remote "$Tts/$piperArchive" $piperZip
    $piperDir = Join-Path $tmp 'vits-piper-en_US-lessac-medium-int8'
    Expand-ArchiveBzip2 $piperZip $tmp

    # The voice goes in its own directory, with its tokens file beside it.
    # That is the layout sherpa-onnx expects: getOfflineTtsConfig takes a
    # model directory and a model name.
    #
    # The espeak-ng data in the archive is deliberately NOT copied. Since
    # sherpa-onnx v2.0.0 the text frontend is the piper-phonemize library,
    # which bundles its own copy of the espeak data. The upstream build
    # script drops it for the same reason, and it is 17 MB.
    $voiceDir = Join-Path $OutDir 'vits-piper-en_US-lessac-medium-int8'
    if (Test-Path $voiceDir) { Remove-Item $voiceDir -Recurse -Force }
    New-Item -ItemType Directory -Path $voiceDir -Force | Out-Null
    Copy-Item (Join-Path $piperDir 'en_US-lessac-medium.onnx') $voiceDir -Force
    Copy-Item (Join-Path $piperDir 'en_US-lessac-medium.onnx.json') $voiceDir -Force
    Copy-Item (Join-Path $piperDir 'tokens.txt') (Join-Path $voiceDir 'tokens.txt') -Force

    Write-Host ""
    Write-Host "=== models are in $OutDir ==="
    Get-ChildItem $OutDir | Where-Object { -not $_.PSIsContainer } |
        Sort-Object Length -Descending |
        ForEach-Object { "  {0,-40} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB) }
    $espeakSize = (Get-ChildItem $espeak -Recurse -File | Measure-Object -Property Length -Sum).Sum
    Write-Host ("  {0,-40} {1,8:N1} MB" -f 'espeak-ng-data/ (many files)', ($espeakSize / 1MB))
}
finally {
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
}