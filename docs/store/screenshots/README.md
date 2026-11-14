# Phone screenshots

These are real captures from the app on a Pixel 7. They are 1080 x 1920 PNG
files, which is the size Play recommends for phone screenshots.

| File | What it shows |
|---|---|
| `01-main.png` | The scoreboard: remaining score, the score and suggestion area, and the buttons. |
| `03-privacy.png` | The privacy policy opened from inside the app. |

The screenshots still contain the Android status bar. That is acceptable to
Play, but it is cleaner to crop it off before uploading.

Take any further captures the same way:

```
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png docs/store/screenshots/02-suggestion.png
adb shell rm /sdcard/screen.png
```

Screenshots that would be useful for the listing, but still need to be taken
with the app in the right state:

- the score and suggestion after a visit (401, treble twenty x3);
- the read-back confirmation ("You said one hundred and forty. Right?");
- the checkout plan ("That leaves 221").
