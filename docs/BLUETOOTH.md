# Bluetooth headphones

Measured on a Pixel 7 with Bose QuietComfort 35 headphones, 13 November 2026.

## The answer

**Yes, and it is better than the phone's own speaker.**

- **Output** goes to the headphones over A2DP. The system reports
  `type:bt_a2dp addr:XX:XX:XX:XX:FF:28` as the active output device.
- **Input** stays on the phone's built-in microphone.

## The measurement

The echo spike plays the coach's voice and measures what reaches the
microphone. With the headphones on, the coach's voice goes to the headphones
and never reaches the phone's microphone at all:

| | Phone speaker | Bose headphones |
|---|---|---|
| Room floor (mean) | 0.00048 | 0.00048 |
| Echo (mean) | 0.00158 | **0.00034** |
| Echo (peak) | 0.02810 | **0.00193** |

**The echo peak drops by a factor of 14.5, and ends up below the room
floor.** With headphones on, the coach literally cannot hear itself.

## Why this matters

The half-duplex gate exists to stop the coach hearing its own voice. With
headphones on, the problem almost entirely disappears - the coach's voice
never reaches the phone's microphone.

That does not mean the gate can be dropped, for two reasons:

1. **The gate is what makes the phone's own speaker case work.** The same
   measurement on the phone speaker puts the echo peak at 0.028, which is
   **above** the 0.0150 threshold the Windows VAD used. Without the gate the
   coach would transcribe its own reply as speech.
2. **The player might take the headphones off mid-leg**, or the battery might
   die. The gate has to be there for the case where it is needed.

But the gate can be **much less conservative with headphones on**, and that is
worth knowing: a long tail after the coach finishes speaking is a delay
before the player can be heard. If the echo is genuinely gone, the tail can be
short.

## Which microphone is used, and why that is the right answer

Bluetooth earbuds carry two profiles:

| Profile | Carries | Bandwidth |
|---|---|---|
| **A2DP** | stereo music | high quality |
| **HFP / HSP** | microphone, mono audio | **8 kHz** |

The earbud microphone only exists in HFP, which is 8 kHz mono.

That is exactly the limitation that killed Bluetooth microphones on the
Windows build. From `update_summary.md` step 16:

> 8 kHz telephony audio is exactly the bandwidth that destroys the difference
> between "eighty" and "eight"

The coach's whole job is hearing that difference, so an 8 kHz microphone is
not usable for the score.

**Android avoids it.** The app captures with `AudioSource.VOICE_RECOGNITION`,
and Android's audio policy routes voice-recognition capture to the built-in
microphone even while A2DP audio plays in the headphones. The result is the
ideal arrangement:

- the coach speaks into the player's ears, in full quality
- the score is heard by a 16 kHz microphone, in full quality

There is no echo to cancel, because the speaker and the microphone are
different devices.

## On the Windows build, by contrast

On Windows the Bluetooth microphone only appeared under the WDM-KS host API,
which PortAudio cannot open at all. The conclusion there was that a Bluetooth
headset could never be a good microphone. On Android the same headphones are
ideal, because the platform routes the two directions separately.

## What this means for the app

1. **Use `AudioSource.VOICE_RECOGNITION`.** It is already what the spike
   uses. `MIC` would be routed differently, and `VOICE_COMMUNICATION` would
   turn on the platform's echo canceller and noise suppressor, which the
   Windows build deliberately avoided.
2. **Keep the half-duplex gate.** It is required for the phone's own speaker.
3. **The echo tail can be shortened when headphones are connected.** Worth
   measuring once the real app is running, because a long tail is a delay
   before the player can speak.
4. **No special Bluetooth handling is needed.** The platform does the
   routing, and the app just has to not fight it.

## The headphone route, measured on the real app

The route was finally measured on the running app on 13 November 2026, with
the Bose QuietComfort 35 connected over A2DP.

```
adb shell dumpsys audio | grep -o "type:[a-z_]*" | tail -3
```

The Bose was connected (`ConnectionState: STATE_CONNECTED`) and `bt_a2dp` was
the active output. The app's own route log agreed:

```
DartsCoach: Route: bt_a2dp (headphones); Speaking: 401. Treble twenty, ...
DartsCoach: Gate open after 4759ms (speech 3931ms, tail 0ms)
```

So both settings are measured now, not assumed:

| Route | Echo tail | Barge-in |
|---|---|---|
| The phone's own speaker | 0.35 s | off |
| Bluetooth headphones | 0 s | on |

The echo tail measurement on the headphone route gave 0 ms for all three
phrases. The room floor and the speech level at the microphone were both
about 0.00035, which is the point: the coach's voice never reached the
microphone at all.

### How the app decides

The decision is made before the coach speaks, so there is no playing
configuration to ask yet. `AudioManager.getDevices()` is the only thing
available, and it is not enough on its own: it lists every output that
*could* be used, so the phone's own speaker is on the list even when the
headphones are carrying the audio.

Android's own rule settles it. Media goes to Bluetooth when a Bluetooth
output is available and only falls back to the speaker when there is none.
So the app looks for a connected A2DP output first and treats that as the
headphone route; the speaker is used when there is no Bluetooth output.

**This was the bug the measurement found.** The first version walked the
device list and returned "speaker" as soon as it saw one, so with the Bose
connected the app used the phone-speaker settings - the longer tail and
barge-in off - even though the headphones were carrying the audio. The
route log exists because of that: it made the mistake visible.

### The hidden route check

The route can be checked without a second person:

```
adb shell am start -S -n com.jr12866.dartcoach/.MainActivity --ez route true
adb logcat -s DartsCoach
```

The app says one fixed line and logs the route it used:

```
DartsCoach: Route: bt_a2dp (headphones); Speaking: 401. ...
```

## Not tested


- The earbud microphone itself (HFP, 8 kHz). It is not used, and it should
  not be: the score is heard by the phone.
- A USB-C or 3.5 mm headset. Android routes those as ordinary wired devices
  and they may well be better than the phone's microphone at the oche.
- A second player, or a room with the darts board and other noise.