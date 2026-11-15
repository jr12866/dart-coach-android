# Publishing to Google Play

The app is published to the Play Store, so it installs like any other app. The
models ship inside the install as a Play Asset Delivery pack, so there is no
download step on first launch.

## The distribution shape

| | |
|---|---|
| Base module | The app: Kotlin code, Compose UI, the native libraries, and the espeak-ng data that `piper-phonemize` needs. 66 MB on the device, 26 MB compressed. |
| Asset pack `models` | The models: 172 MB on the device, 104 MB compressed. `install-time` delivery. |

The whole app is therefore about **130 MB to download** and takes about
**240 MB on the phone**. Those are the numbers measured on the built bundle,
not estimates.

The espeak-ng data is about 20 MB and is bundled by `piper-phonemize` itself,
so it lands in the base module rather than the asset pack. It is needed for
the text frontend and cannot be moved without changing the dependency.

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

   `*.jks` and `*.keystore` are ignored by git.

3. **`local.properties`** - keep out of version control:

   ```
   dartcoach.keystore=/absolute/path/to/dart-coach-release.jks
   dartcoach.keystore.password=...
   dartcoach.key.alias=dart-coach
   dartcoach.key.password=...
   ```

   `local.properties` is already ignored by git.

4. **Signing configuration.** `app/build.gradle.kts` reads those properties
   and creates the `release` signing configuration. If all four properties
   are present, the release build type is signed with it. If they are absent,
   debug builds still work but the release bundle is unsigned and Play will
   reject it.

5. **Play App Signing** - accept the upload key reset in Play Console, so the
   upload key is not the signing key of record.

## Building

```
./gradlew bundleRelease
```

The output is `app/build/outputs/bundle/release/app-release.aab`. Upload that
to Play Console, not an APK.

The models must be present in `models-pack/src/main/assets/` before the
release bundle is built. The fetch script puts them there:

```
.\tools\fetch-models.ps1
```

**Do not pass `-PwithLocalModels` to the release build.** That flag copies
the models into the base module as well, and then the base module and the
asset pack claim the same asset and the bundle fails:

```
Both modules 'base' and 'models' contain asset entry 'assets/base.en-decoder.int8.onnx'.
```

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

The copy, the graphic specifications, the screenshot plan, and the exact
Play Console answers are in `docs/STORE-LISTING.md`.

The icon and feature graphic are already generated in `docs/store/`. They are
made by a script rather than being opaque binary files:

```
python tools\generate-store-assets.py
```

Two real phone screenshots are in `docs/store/screenshots/` already: the main
screen and the privacy policy. The rest need the app in the right state, so
they have to be taken at the phone.

## The Data safety form, and the privacy policy

This is the part that needs care, because the app records audio and now also
sells something.

The facts:

- The microphone is used to hear the player's score.
- Audio is processed **on the device** by the bundled speech recogniser.
- Audio is **never transmitted** and never stored beyond the recognition
  buffer.
- The app has no analytics, no ads, no crash reporting that leaves the device.
- The app **does** declare `INTERNET`, and it is used for exactly one thing:
  talking to Google Play about the Pro purchase.

Google defines "collected" as data retrieved from the device and sent off it.
The microphone audio is processed only on the device and never sent off it, so
it is not disclosed as collected. The purchase data *is* sent - to Google
Play, by the Play Billing Library - so it *is* disclosed, as purchase history.

That is the honest answer: the app collects the purchase and nothing else.
The exact wording for each question is in `docs/STORE-LISTING.md`.

The privacy policy text is in:

- `docs/PRIVACY-POLICY.md` - the source copy;
- `docs/store/privacy-policy.html` - the public page to upload;
- `app/src/main/res/raw/privacy_policy.html` - the copy bundled in the app,
  linked from the **Privacy policy** button.

The last two are **generated from the first**, so they cannot drift apart:

```
python tools\generate-privacy-policy.py
```

Edit the markdown, run the script, and all three copies agree. Run it before
every release.

The in-app link is required by Google as well as the Play Console URL. The
**Privacy policy** button opens the public URL once
`privacy_policy_url` in `app/src/main/res/values/strings.xml` is filled in.
Until then it shows the bundled copy in a dialog, so the link works offline
and on a fresh install.

Before submission, replace the example email address with a monitored address
and make sure the developer name matches the Play listing exactly.

## Selling Pro

Pro is a one-time in-app purchase, handled by Google Play.

### The product

Create it in the Play Console under **Monetization > Products > One-time
products**:

| | |
|---|---|
| Product id | `dartcoach_pro` |
| Name | Darts Coach Pro |
| Description | The personalised coach: your own hit rates, the full out chart, the setup coaching and your statistics. |

The product id has to match `BillingRepository.PRODUCT_ID` exactly, or the
app will not find the product.

### The price

$2.99, or the local equivalent. Play handles the currency and the tax, and
the app shows the price Play gives it rather than a price written into the
code, so it is right for the player's country.

**The price can be changed in the Play Console without a new release.** What
*cannot* be changed without a release is which features are behind the
paywall - that is the `PRO_FEATURES` list in
`app/src/main/java/com/jr12866/dartcoach/entitlement/Entitlement.kt`.

### Testing the purchase

Billing cannot be tested with a sideloaded APK. The purchase flow needs the
Play Store app signed in to a test account.

1. Add a test account in the Play Console, or use your own account.
2. Upload to **internal testing** and install from there.
3. Buy Pro. It is a one-time purchase, so it can be bought as many times as
   needed for testing.
4. Check that the Pro screens open and the paywall does not come back.
5. Refund it from the Play Console to test the other path.

### Restore

There is nothing to restore from - Play holds the purchase, and it follows
the player's Google account to any device. The **Restore purchase** button
asks Play what the account owns. The answer is cached on the phone so the app
works when Play cannot be reached.

### The cost of the network permission

The app used to declare no `INTERNET` permission at all, and that was a
verifiable property of the build rather than a promise. It is now declared,
for the purchase.

What is still true, and what the app says:

- the coach works with the phone in aeroplane mode;
- the microphone audio is processed in memory and never sent;
- no code in the app reads the audio buffer and writes it to a socket;
- there is no analytics, advertising, or crash-reporting SDK.

What has changed is the wording, and it has been narrowed rather than
withdrawn: the coach needs no network, and the only thing that uses one is
the purchase.

## Review expectations

A microphone-using app attracts scrutiny. Keep the claims simple and true:

- On-device speech recognition. No server, no account, no telemetry.
- The app requests `RECORD_AUDIO` and `INTERNET`, and nothing else. The
  `INTERNET` permission is for the Play Store, to take the payment for Pro.
- No advertising, analytics, or crash-reporting SDKs.

The claim that is worth keeping true is that the microphone audio never
leaves the phone. It is processed in memory by the bundled recogniser, and
no code in the app reads the audio buffer and writes it to a socket. The
`INTERNET` permission being declared does not weaken that, and the app's own
copy says so.

## Testing tracks

Ship to **internal testing** first. It is available within minutes, needs no
review, and is where the install of a 130 MB app over mobile data should be
tested first.

An install-time asset pack only arrives through Play, so the release bundle
cannot be tested with `adb install`. The local `-PwithLocalModels` build is
for development only; the Play install is the test that counts.

Google also publishes a pre-launch report that runs the app against common
crash scenarios before a release goes to review. Run it and read it.

## Target API level

Play requires the target SDK to be within one year of the latest Android
release. `targetSdk = 35` is current as of this plan; check
https://developer.android.com/google/play/requirements/target-sdk before
shipping, and bump it when required.

## What is still manual

The repository side of Phase 5 is now done. These parts need the Play
Console account and a real device:

- the developer account and the app's Play Console entry;
- upload the first `app-release.aab` to **internal testing**;
- publish `docs/store/privacy-policy.html` and enter its public URL;
- replace the example privacy email in both privacy-policy copies;
- upload the icon, feature graphic, and real screenshots;
- fill in the content rating questionnaire;
- complete the Data safety form using `docs/STORE-LISTING.md`;
- enable Play App Signing and accept the upload-key reset;
- run the pre-launch report and test the install over mobile data.

## If the paywall turns out to be wrong

`docs/ROLLBACK.md` is the note: how to make the app free again, how to change
the price without a release, and what cannot be undone at all.

Read it before the first release, not after.
