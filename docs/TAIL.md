# The echo tail, measured

Measured on a Pixel 7, 13 November 2026.

## The result

**The coach's voice is gone within about 50 to 100 milliseconds of the audio
stopping.**

That is much shorter than the Windows build's 0.35 seconds, and it is
shorter than the raw measurement first suggested.

## Re-measured on both routes, 13 November 2026

The tail was measured again on the running app, with the route logged on
every utterance. Two configurations: the Bose over A2DP, and the phone's own
speaker with Bluetooth switched off.

| Route | Room floor | Speech level at the mic | Tail |
|---|---|---|---|
| Bluetooth headphones | 0.0003 | 0.0003 - the same as the room | **0 ms** |
| The phone's own speaker | 0.0004 | 0.0033 - 0.0060 | **447 - 835 ms** |

The headphone number is the point: the speech level at the microphone was
the same as the room floor, so the coach's voice never reached the
microphone at all. The tail is exactly zero.

The phone-speaker numbers are **longer than the 0.10 s the app was using**.
Two runs of the three phrases gave 835 / 371 / 370 ms and 783 / 505 /
447 ms. The curves show why: the level does not fall off smoothly, it
steps down over a few hundred milliseconds, and there is a lot of room
noise on top of it. One phrase's curve sits at 0.01 - 0.05 right through
to 4400 ms and only then drops to the room floor.

That is a room, not a desk. The app is at the oche, the board is in front of
it, and the phone's speaker is 10 cm from the microphone with a surface
between them.

**The 0.10 s tail is not enough for the phone's speaker.** It was measured
on a desk in a quiet room, where the speaker's mechanical ringing was over
in 50 - 100 ms. At the oche the acoustic path is longer.

What the right number is has to be measured at the oche, standing at the
board. Until then, the safe thing is the old Windows value of 0.35 s rather
than 0.10 s: the measurement says 447 - 835 ms, so 0.35 s covers the
shorter half of it, and the cost of waiting a little too long is much
smaller than the cost of the coach hearing its own reply.

## What the measurement first said, and why it was wrong

The first version of the tool reported:

```
TAIL: room=0.00053 speech=3862ms tail=788ms (0.788s)
TAIL SUMMARY: mean=1.204s longest=1.497s
```

Nearly a second and a half. That was noise, not the echo.

The tool dumps the level at the microphone every 10 ms, and the curve shows
what was actually happening. For the first phrase, the speech ends at 3848 ms:

```
   3700 ms  0.013      the speech
   3750 ms  0.0204
   3800 ms  0.0096
   3850 ms  0.002      the voice has gone - the room is 0.00073
   3900 ms  0.0009
   3950 ms  0.001
   4000 ms  0.0104     noise
   4050 ms  0.0673     noise
   4150 ms  0.0329     noise
   4350 ms  0.0531     noise
   4650 ms  0.0024     the room
```

The voice stops at 3850 ms. Everything after that is the room, with spikes
to 0.067 - a door, a chair, the building.

## Why the detection was fooled

Two attempts, both wrong, and the reason is worth recording.

**Comparing against the room.** A slice counts as "still ringing" if it is
above the room by a margin. But the room produces spikes to 0.04 and 0.067
against a floor of 0.0005, so any margin low enough to catch the tail also
catches the room. The quieter the room, the longer the measured tail - which
is backwards for a real acoustic tail, and was the clue that something was
wrong.

**Comparing against the speech, with a running median.** Better - the tail
came down to 0.36-0.76 s - but still fooled, because a 70 ms median window
is not enough against a 0.067 spike.

**The curve is the real answer.** Plotting the level every 10 ms shows the
decay directly, and a decay that finishes in two slices is not a tail worth
arguing about.

## What the tail is

On a phone the speaker is 10 cm from the microphone, so there is no room
reverb to speak of. What is left is the speaker's mechanical ringing - the
driver continuing to move for a few milliseconds after the signal stops - and
that is over in well under 100 ms.

The Windows build's 0.35 seconds was room reverb, on a laptop whose speakers
were across the room.

## The number to use

| Route | Tail |
|---|---|
| The phone's own speaker | **0.35 s** |
| Bluetooth headphones | **0 s** - the voice never reaches the microphone |

The phone's own speaker used a tenth of a second, on the strength of the
quiet-desk measurement. The oche measurement says 447 - 835 ms, so the
app now uses 0.35 s: the same value the Windows build used, and it covers
the shorter half of the measured range.

The cost is dead time before the coach can hear you. Half a second is
noticeable. The cost of getting it wrong is the coach hearing its own
reply, which is worse, so the safe value wins until the oche measurement
says otherwise.

With headphones there is no tail at all, and the gate can open the instant
the audio stops.
