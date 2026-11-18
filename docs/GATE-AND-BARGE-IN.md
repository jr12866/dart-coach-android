# The echo gate and barge-in

Both were measured on a Pixel 7.

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

**Cause.** The log showed the "yes" *was* heard, and then ignored. The gate
was closed for the whole of the coach's speech, and the player had answered
during it.

**This is not a porting bug.** The Windows coach has the same shape: the
gate is held for the whole of the speech plus a tail, and barge-in was
listed as an option in `update_summary.md` and never taken, because on a
laptop the coach is across the room.

On a phone the coach is 10 cm from the player's ear, so they can answer
without waiting for it.

## Barge-in, and how it nearly went wrong

The coach now stops when the player starts talking. While it is speaking
the audio is still analysed rather than thrown away.

The first version of the detector compared the **peak** of each chunk
against a fixed threshold, and it cut the coach off mid-sentence:

```
Barge-in: the player is speaking (level 0.0478)
TTS: 48384 samples = 2194ms, played in 296ms
```

Three things were wrong with it.

**The threshold was measured in the wrong conditions.** It was set from
the Bose headphones, where the coach's voice never reaches the microphone.
With the headphones off, Android had fallen back to the phone's own
speaker, and the coach's voice peaks at 0.21 - louder than the player.

**A peak is the wrong measure.** Through the phone's speaker, the coach's
own voice has a mean of 0.0014 but a peak of 0.21: a plosive is louder
than the player. Any single chunk can cross a peak-based threshold. The
player's voice is sustained, so the mean over a chunk is what tells them
apart:

```
peak: n=140 p50=0.0416 p90=0.1571 max=0.2819
mean: n=140 p50=0.0069 p90=0.0302 max=0.0531
```

**Barge-in is not safe on the phone's own speaker at all.** Even with the
right measure the gap is narrow, so the app now asks Android where the
audio is going and enables barge-in only when the answer is not the phone's
own speaker.

The route has to be read from the *playback configurations*, not the device
list. The Bose is paired and present even after Android has fallen back to
the speaker, and that fallback is exactly what made the coach cut itself
off.

## The tail is a window, not a wall

The tail is the wait after the coach stops speaking, for its own voice to die
away. It was dead time: the microphone was shut for the whole of it, and a
player who answers a question at once - which is what players do - had the
first half of their answer thrown away before the microphone was ever opened.

The tail is now a **window**. The microphone stays open through it, the audio
goes to the voice detector as usual, and the **level** decides afterwards what
it was: the player answering, or the coach's own voice still ringing in the
room.

The levels were measured in the room the app is played in, on the Pixel 7:

| What | Level at the microphone |
|---|---|
| The room, with nobody talking | 0.0003 - 0.0006 |
| The coach's own voice ringing in the room | chunk mean at most **0.022** |
| The player's voice | chunk mean **0.052 - 0.055** |

So a chunk at **0.03** or above is the player, and a chunk below it is the
coach. One chunk is not enough - the coach's own voice has the occasional
loud transient in it - so **two** chunks over the level are needed before the
tail is taken for the player.

```
Tail: 11 chunk(s), 0 over the player's level, so it was the echo
```

Only an **answer** is taken from the tail. The coach is waiting for one, and
anything else in the tail is the echo of what the coach just said. If the
echo of "You said one hundred and forty" were read back as a score, the
coach would read it back again, and again.

A longer measured tail is now safe, because the tail is no longer a period in
which the player cannot be heard. That is what the measured tail in this room
turned out to mean: 0.671 s, not the 0.35 s the Windows build used.

## The numbers

With headphones, barge-in is safe and the coach speaks the whole phrase:

```
TTS: 88242 samples at 22050 Hz = 4001ms, played in 4280ms
```

With the phone's own speaker, barge-in is off and the phrase still plays in
full.

## What is still not right

**The echo tail is measured per phone, and the number in this room is long.**
The player measures their own from Settings; the default is the Windows
value, 0.35 s. In the room this app was tested in the tail measured 0.671 s,
which used to be two thirds of a second of dead time and is now a window in
which the player can be heard.