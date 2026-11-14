# The echo tail, measured

Measured on a Pixel 7, 13 November 2026.

## The result

**The coach's voice is gone within about 50 to 100 milliseconds of the audio
stopping.**

That is much shorter than the Windows build's 0.35 seconds, and it is
shorter than the raw measurement first suggested.

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
| The phone's own speaker | **0.10 s** |
| Bluetooth headphones | **0 s** - the voice never reaches the microphone |

The phone's own speaker keeps a tenth of a second of margin over the
measured decay, which is enough to cover the ringing without making the
player wait.

With headphones there is no tail at all, and the gate can open the instant
the audio stops. That is worth having: the current 0.35 seconds is dead time
before the coach can hear you.