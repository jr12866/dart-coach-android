# Publishing to Google Play

The app is published to the Play Store, so it installs like any other app. The
models ship inside the install as a Play Asset Delivery pack, so there is no
download step on first launch.

## The distribution shape

| | |
|---|---|
| Base module | The app: Kotlin code, Compose UI, `sherpa-onnx-jni.so`. A few MB. |
| Asset pack `models` | The models: ~325 MB, `install-time` delivery. |

Size limits, verified against Google's documentation:

| Component | Compressed download limit |
|---|---|
| Base module | 500 MB |
| Individual asset packs | 1.5 GB |
| Cumulative, all modules + install-time packs | 4 GB |

The 200 MB figure is the *legacy APK* limit, for apps still publishing APKs
instead of app bundles. An app bundle gets 500 MB for the base module alone.

## One-time setup

1. **Developer account.** One-time $25 fee at
   https://play.google.com/console. Use a personal account unless there is a
   reason not to. Note that new personal accounts have additional testing
   requirements - see "App testing requirements" in the Play Console help.

2. **Release keystore.** Generate one and keep it safe:

   ```
   keytool -genkeypair -v -keystore dart-coach-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias dart-coach
   ```

   The keystore and its passwords must never be committed. Play App Signing
   means the upload key can be rotated later if it leaks, but losing the only
   key means losing the ability to update the app.

3. **`local.properties`** - keep out of version control:

   ```
   dartcoach.keystore=/absolute/path/to/dart-coach-release.jks
   dartcoach.keystore.password=...
   dartcoach.key.alias=dart-coach
   dartcoach.key.password=...
   ```

4. **`signingConfigs`** in `app/build.gradle.kts`, reading those properties.

5. **Play App Signing** - accept the upload key reset in Play Console, so the
   upload key is not the signing key of record.

## Building

```
./gradlew bundleRelease
```

The output is `app/build/outputs/bundle/release/app-release.aab`. Upload that
to Play Console, not an APK.

To estimate the download size before uploading:

```
bundletool build-apks --bundle=app-release.aab --output=/tmp/apks
```

## The store listing

Play requires:

- **App icon** (512x512 PNG) and **feature graphic** (1024x500).
- **Phone screenshots** - at least two, 16:9 or 9:16.
- **Short description** (80 chars) and **full description** (4000 chars).
- **Privacy policy**, hosted at a public URL. Required.
- **Content rating** questionnaire.
- **Data safety** form.

## The Data safety form, and the privacy policy

This is the part that needs care, because the app records audio.

The facts:

- The microphone is used to hear the player's score.
- Audio is processed **on the device** by the bundled speech recogniser.
- Audio is **never transmitted** and never stored beyond the recognition
  buffer.
- The app has no analytics, no ads, no crash reporting that leaves the device.

In the Data safety form that means: microphone data is collected, and it is
**not** shared and **not** transferred off the device. The privacy policy must
say the same thing in plain language. Google checks that the two agree, and a
mismatch is a common cause of rejection.

## Review expectations

A microphone-using app attracts scrutiny. Keep the claims simple and true:

- On-device speech recognition. No server, no account, no telemetry.
- The app requests `RECORD_AUDIO` and nothing else. No `INTERNET` permission
  is declared at all, which is a strong and verifiable claim - the app
  physically cannot send anything anywhere.

That last point is worth keeping true. The `INTERNET` permission being absent
from the manifest is what makes "nothing leaves the phone" a property of the
build rather than a promise.

## Testing tracks

Ship to **internal testing** first. It is available within minutes, needs no
review, and is where the install of a 325 MB app over mobile data should be
tested first.

Google also publishes a pre-launch report that runs the app against common
crash scenarios before a release goes to review. Run it and read it.

## Target API level

Play requires the target SDK to be within one year of the latest Android
release. `targetSdk = 35` is current as of this plan; check
https://developer.android.com/google/play/requirements/target-sdk before
shipping, and bump it when required.