# Phone screenshots

These are real captures from the app on a Pixel 7, cropped to 1080 x 1920
PNG files, which is the size Play recommends for phone screenshots.

| File | What it shows |
|---|---|
| `01-main.png` | The scoreboard: remaining score, the score and suggestion area, and the buttons. |
| `02-suggestion.png` | The score and the suggestion after a visit: 401, "Treble twenty, treble twenty and treble twenty", T20 T20 T20, leaving 221. |
| `03-privacy.png` | The privacy policy opened from inside the app. |
| `04-readback.png` | The read-back: "You said one hundred and forty. Right?" |

The screenshots still contain the Android status bar. That is acceptable to
Play, but it is cleaner to crop it off before uploading.

## How they were taken

The two captures that need the app to hear a score were taken with the
self-hearing test, which plays a phrase through the speaker with the
microphone gate left open:

```
adb shell am start -n com.jr12866.dartcoach/.MainActivity \
    --ez testpro true --ez selfhears true --es selfhear 'one\ hundred,yes'
```

`--es selfhear` takes the phrases as one comma-separated string. The
backslashes are because the command line is a shell one and a shell splits a
list of arguments on the spaces. `one\ hundred,yes` says "one hundred" and
then "yes", which is what a player does when the coach reads a score back.

To take any further captures the same way:

```
adb exec-out screencap -p > screen.png
python tools/crop-screenshots.py
```

The crop tool trims a 1080 x 2400 capture down to 1080 x 1920 and flattens
it onto an opaque background. Play does not pad the image for you, and the
extra strip comes off the bottom, where the buttons and the navigation bar
are.

The screenshots that would still be useful for the listing, and are not here
yet:

- the checkout plan on its own ("That leaves 221" as a spoken plan rather
  than as part of the suggestion);
- the out chart, the setup chart and the statistics screen.
