# Measuring the echo tail

The tail is how long the coach's voice is still audible at the microphone
after the audio has stopped.

The Windows build used **0.35 seconds**, measured for a laptop whose
speakers are across the room, where the tail is room reverb. On a phone the
speaker is 10 cm from the microphone, so the tail is the speaker's
mechanical ringing - shorter and sharper, but possibly not much shorter if
the phone is doing the talking.

## Running the measurement

The measurement is a hidden intent, so it can be run wherever the phone
actually is.

```
adb shell am start -n com.jr12866.dartcoach/.MainActivity --ez tail true
adb logcat -s DartsCoach
```

It plays three phrases through the speaker, listens to the microphone the
whole time, and reports how long the voice is still there after the audio
ends:

```
TAIL: room=0.00032 speech=3969ms tail=961ms (0.961s) over 550 slices
TAIL for 'You're at 401. Treble twenty, treble twenty, treble twenty.' was 0.961s
TAIL: room=0.00033 speech=2403ms tail=497ms (0.497s) over 392 slices
TAIL for 'One hundred and forty. Is that right?' was 0.497s
TAIL: room=0.00034 speech=2136ms tail=434ms (0.434s) over 366 slices
TAIL for 'That leaves one hundred and eighty one.' was 0.434s
TAIL SUMMARY: mean=0.630s longest=0.961s (the Windows build used 0.35s)
```

It takes about half a minute, and it makes noise - it is the coach talking
three times.

## What to measure, and why

**Both routes.** The tail is completely different depending on where the voice
goes.

| Route | What the tail is |
|---|---|
| The phone's own speaker | The speaker's mechanical ringing |
| Bluetooth headphones | Nothing - the voice never reaches the microphone |

With headphones the tail should measure at or near zero, and the gate can be
opened as soon as the audio stops.

With the phone's speaker the tail is the speaker's ringing, and that is what
the gate has to cover.

**In the room you will actually use.** The measurement on a desk is not the
measurement at the oche: a board, a room, and other people change the level
the room sits at, and the tail is measured against the room.

## Two numbers matter, and only one of them is the tail

The `room=` figure is the level the room sits at. The tail is measured
against it: a slice counts as "still ringing" if it is above twice the room
plus a small margin.

If the room is very quiet - 0.0003 on a desk - then almost anything counts as
ringing. In a noisy room the room level is higher and the same absolute
noise counts as less. So the measurement has to be done where the coach will
be used.

## What the measurement is not

It is not the length of the phrase. The tail is the time after the audio
stops, not the speech itself. A long phrase does not have a long tail.