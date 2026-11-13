#!/usr/bin/env bash
#
# Fetch the models the coach needs into app/src/main/assets/.
#
# Three models, all running on the device:
#   silero_vad.onnx       voice activity detection
#   whisper base.en       speech to text
#   piper lessac-medium   text to speech
#
# The URLs are the ones published in the sherpa-onnx release assets. The
# Windows coach uses base.en and the en_US-lessac-medium Piper voice, so this
# port matches it rather than choosing new ones.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS="$HERE/../app/src/main/assets"

ASR_RELEASES="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
TTS_RELEASES="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

# Which Whisper to fetch. base.en matches the Windows coach. tiny.en is about
# half the size and measurably worse at number words - see PLAN.md Phase 1.
WHISPER="${WHISPER:-base.en}"

mkdir -p "$ASSETS"

fetch() {
    local url="$1" dest="$2"
    if [ -f "$dest" ]; then
        echo "  have $(basename "$dest")"
        return
    fi
    echo "  fetching $(basename "$dest")"
    curl -fL --progress-bar "$url" -o "$dest"
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Voice activity detection:"
fetch "$ASR_RELEASES/silero_vad.onnx" "$ASSETS/silero_vad.onnx"

echo "Whisper ($WHISPER):"
archive="sherpa-onnx-whisper-$WHISPER.tar.bz2"
fetch "$ASR_RELEASES/$archive" "$tmp/$archive"
tar xvf "$tmp/$archive" -C "$tmp"
srcdir="$tmp/sherpa-onnx-whisper-$WHISPER"
for f in "$srcdir/$WHISPER-encoder.int8.onnx" \
         "$srcdir/$WHISPER-decoder.int8.onnx" \
         "$srcdir/$WHISPER-tokens.txt"; do
    [ -f "$f" ] || { echo "  MISSING: $f" >&2; exit 1; }
    cp "$f" "$ASSETS/"
done

echo "Piper voice (en_US-lessac-medium):"
tarball="vits-en_US-lessac-medium.tar.bz2"
fetch "$TTS_RELEASES/$tarball" "$tmp/$tarball"
tar xvf "$tmp/$tarball" -C "$tmp"
find "$tmp" -name "en_US-lessac-medium.onnx" -exec cp {} "$ASSETS/" \;
find "$tmp" -name "en_US-lessac-medium.onnx.json" -exec cp {} "$ASSETS/" \;
find "$tmp" -name "tokens.txt" -exec cp {} "$ASSETS/piper-tokens.txt" \;

echo "espeak-ng data (shared by every Piper voice):"
fetch "$TTS_RELEASES/espeak-ng-data.tar.bz2" "$tmp/espeak-ng-data.tar.bz2"
tar xvf "$tmp/espeak-ng-data.tar.bz2" -C "$ASSETS"

echo
echo "Models are in app/src/main/assets:"
ls -lh "$ASSETS" | grep -v '^total'
