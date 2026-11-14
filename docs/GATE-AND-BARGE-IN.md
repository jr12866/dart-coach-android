# The echo gate and barge-in

Both were measured on a Pixel 7, with Bose QuietComfort 35 headphones
available.

## The clipping

The first symptom was the coach cutting off the last word of "Is that
right?".

**Cause.** The voice was played through `AudioTrack` in `MODE_STATIC`, with
the buffer sized from the sample count. `MODE_STATIC` takes the whole blob
in one write, and the tail gets clipped: the buffer is rounded and the last
word goes with it.

**Fix.** `MODE_STREAM`, which is what the Windows coach uses. Piper's
`synthesize()` yields one chunk per sentence and each is written as it
arrives, so playback starts before the whole reply has been synthesised.
The audio is written in 1024-sample slices with `play()` called first, and
the track is drained properly afterwards - the output buffer holds audio
that has been played but not yet heard, and tearing the track down without
letting it drain clips the last syllable.

Measured after the fix: 48510 samples = 2200 ms of audio, played in 2720 ms.
No underruns.

## The lost answer

The second symptom was that the coach would not accept a "yes" for the
score it had just read back.

**Cause.** Not what it looked like. The log showed the "yes" *was* heard,
and then ignored. The gate was closed for the whole of the coach's speech,
and the player had answered during it.

The timings:

```
19:50:22.533  Gate open after 3203ms
19:50:27.915  Heard: 'Yes'          5.4 seconds later
```

The suggestion is a long sentence - "You're at 401. Throw treble twenty,
treble twenty and treble twenty to leave 221." is about 5.6 seconds of
speech - and the gate was shut for 7.7 seconds after it started. Anything
said in that window was dropped on the floor.

**This is not a porting bug.** The Windows coach has the same shape: the
gate is held for the whole of the speech plus a tail, and barge-in was
listed as an option in `update_summary.md` and never taken, because on a
laptop the coach is across the room.

On a phone it matters much more, because the phone is 10 cm from the
player's ear and they can answer the coach without waiting for it.

## Barge-in

**The coach now stops when the player starts talking.**

While the coach is speaking the audio is still analysed rather than
thrown away. If a chunk is loud enough to be the player, the voice is cut
off and the player is heard.

The danger is that the coach's own voice is far louder than the player's,
so the two have to be told apart by level. Measured on the Pixel 7:

| | Peak level at the microphone |
|---|---|
| The coach's own voice, through the phone's speaker | 0.023 - 0.037 |
| The player's voice, speaking over the coach | 0.052 - 0.055 |

The threshold is **0.045**, between the two. It was set from the
measurement, not guessed.

With Bluetooth headphones the coach's voice does not reach the microphone
at all - the echo peak is 0.0019, below the room floor - so barge-in is
completely safe there.

## The numbers

```
Speaking: You're at 258. Throw treble twenty, treble twenty and treble
          twenty to leave 78.
TTS: 117271 samples at 22050 Hz = 5318ms, played in 5646ms
Gate open after 7030ms (speech 5646ms, tail 350ms); 576 chunk(s) dropped
```

And with the player talking over it:

```
Heard: '100'
Coach says: You said one hundred. Is that right?
Barge-in: the player is speaking (level 0.0548)
Gate open after 1823ms (speech 1414ms, tail 350ms)
```

The coach was cut off 1.4 seconds into a 5.6 second suggestion.

## What is still not right

**The suggestion is a long sentence.** Six seconds of speech before the
player can do anything is a lot, and the wording is the Windows coach's,
which was written for a laptop where the player is sitting at the desk.

Two things could be done about it, and neither is done yet:

- **Say less.** "You're at 258. Throw treble twenty, treble twenty, treble
  twenty." is enough at the oche. The leave is on the screen already.
- **Speak faster.** Piper's `length_scale` is 1.0, the same as the Windows
  coach. At 1.2 the same sentence is about 20% shorter.

Both change what the coach says, so both are the user's call rather than
the implementer's.

**The echo tail is still 0.35 seconds**, which was measured for a laptop
whose speakers are across the room. With headphones the echo is below the
room floor and no tail is needed at all. With the phone's own speaker the
tail has to cover the speaker's mechanical ringing, which is probably much
shorter than 0.35 seconds. That needs measuring with the phone on the
board.