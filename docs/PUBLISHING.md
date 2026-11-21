# Publishing to Google Play

The app is published to the Play Store, so it installs like any other app. The
models ship inside the install as a Play Asset Delivery pack, so there is no
download step on first launch.

## The distribution shape

| | |
|---|---|
| Base module | The app: Kotlin code, Compose UI, the native libraries, and the espeak-ng data that `piper-phonemize` needs. 66 MB on the device, 26 MB compressed. |
| Asset pack `models` | The models: 189 MB on the device, 117 MB compressed. `install-time` delivery. |

The whole app is therefore about **143 MB to download** and takes about
**255 MB on the phone**. Those are the numbers measured on the built bundle,
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

   **On Windows, write the path with forward slashes.** The build reads it
   with `rootProject.file()`, and a path with backslashes is not recognised
   as absolute - the build fails with a mangled path such as
   `C:gitdart-coach-androidC:Users...`. Forward slashes work on both
   platforms.

   The keystore that is now in place:

   ```
   dartcoach.keystore=C:/Users/jr128/dart-coach-keys/dart-coach-release.jks
   dartcoach.key.alias=dart-coach
   ```

   The passwords are in `local.properties` and nowhere else. **Copy them into
   a password manager**, along with the keystore file. They are the only
   copy.

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
- `docs/store/privacy-policy.html` - the public page;
- `app/src/main/res/raw/privacy_policy.html` - the copy bundled in the app,
  linked from the **Privacy policy** button;
- `site/index.html` - the copy GitHub Pages publishes.

The last three are **generated from the first**, so they cannot drift apart:

```
python tools\generate-privacy-policy.py
```

Edit the markdown, run the script, and all four copies agree. Run it before
every release.

The policy is published at **`https://jr12866.github.io/dart-coach-android/`**.
That is the URL to put in Play Console, under **Policy → App content →
Privacy policy**, and it is already in `privacy_policy_url` in
`app/src/main/res/values/strings.xml`, so the **Privacy policy** button opens
the public page in the browser. The bundled copy is still what the app shows
if the phone is offline or the page cannot be reached, so the link works on a
fresh install with no network at all.

The published page updates itself. A push to `master` that touches `site/`
triggers `.github/workflows/pages.yml`, which deploys the folder to GitHub
Pages. The developer name in the listing must match the one in the policy:
`Joe Rutsky`.

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
app will not find the product. **It cannot be changed once the product has
been created**, so it has to be right the first time.

This used to be called an "in-app product". It is now a **one-time product**,
and the old "In-app products" page still exists for apps created before the
change.

### The purchase option

The product needs at least one **purchase option**, and that is what carries
the price:

| | |
|---|---|
| Purchase option id | `buy` |
| Purchase type | Buy |
| Digital content or service | **Digital content** |
| Price | **$2.99** |

The app reads `oneTimePurchaseOfferDetailsList` from the product details,
which is the purchase option's price and offer token, so it works against
the current model rather than the old one.

The product has to be **activated** after it is saved. A product that is not
active does not appear in the purchase flow at all.

### The order matters

**A bundle has to be in a track before the product can be created.** Play
will not let you configure monetisation for an app that has no release
uploaded. So the upload to internal testing comes before the product, not
after it.

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
review, and is where the install of a 143 MB app over mobile data should be
tested first.

### The closed test, if the account is a new personal one

If the developer account is a **personal account created after 13 November
2023**, internal testing is not enough to reach production. Google requires:

- a **closed** test with at least **12 testers** opted in **continuously for
  at least 14 days**;
- then **Apply for production** on the Dashboard, answering questions about
  the closed test, the app, and production readiness;
- then a production release.

Three things catch people out:

- **Internal testing days do not count.** The 14 days have to be on a closed
  test.
- The counter tracks the **testers' opt-in**, not the builds. Shipping a new
  release to the closed track during the 14 days resets nothing.
- A tester who opts out and back in starts their 14 days again. Recruit more
  than 12, because a dropout on day 13 costs another fortnight.

Budget **four to six weeks** for this on top of everything else. It is the
longest pole in the release, and the closed test can start as soon as the
internal test is stable - it does not have to wait for the rest.

An install-time asset pack only arrives through Play, so the release bundle
cannot be tested with `adb install`. The local `-PwithLocalModels` build is
for development only; the Play install is the test that counts.

Google also publishes a pre-launch report that runs the app against common
crash scenarios before a release goes to review. Run it and read it.

## The screen orientation is not a hardware requirement

**`android.hardware.screen.portrait` is declared as `required="false"`.**

This is not a preference. Play refused to send the first upload for review with
*"Required hardware feature declarations are restricting your app's
availability"*, naming `android.hardware.screen.portrait` as the cause and
listing Phone, Tablet, Desktop and Android XR as the affected users.

The cause was not in the source manifest. The activity asks for portrait with
`android:screenOrientation="portrait"`, and **AAPT2 turns that into an
implicit requirement** for portrait-capable hardware. That is a store filter,
so Play hides the app from devices with no portrait display.

The fix is the one Google's own documentation gives:

```xml
<uses-feature
    android:name="android.hardware.screen.portrait"
    android:required="false" />
```

Declaring the feature explicitly with `required="false"` removes the store
filter **without changing the app**. The activity still runs portrait on a
phone, because `android:screenOrientation` is a runtime request and this
declaration does not touch it. That was proved on the Pixel 7: with the device
forced into landscape, the app still rendered portrait, at 1080 x 2400.

`android.hardware.screen.landscape` is declared the same way. The app does not
ask for it, but Play's check covers both orientations, and declaring both
stops the same issue returning when the manifest changes.

Verify it against the merged manifest, which is what the bundle actually
carries, and with `aapt2 dump badging`, which is what Play reads:

```
uses-feature: name='android.hardware.microphone'
uses-feature-not-required: name='android.hardware.screen.landscape'
uses-feature-not-required: name='android.hardware.screen.portrait'
```

The microphone stays required. The app cannot hear the score without it.

## Building an APK, and one lint check

`bundleRelease` is the release build and is what Play takes. `assembleRelease`
builds the APK, and it runs `lintVitalRelease`, which the bundle task does
not.

One lint check is disabled, with the reason in `app/build.gradle.kts`:
`InvalidFragmentVersionForActivityResult`. It fires when
`registerForActivityResult()` is used and the resolved `androidx.fragment` is
older than 1.3.0. Since androidx.activity 1.9.0, `ComponentActivity` no
longer extends `FragmentActivity`, and this app has no `androidx.fragment` on
the classpath at all - so lint finds no fragment version and assumes the old,
broken one. There is no fragment here to upgrade, and the permission request
works: the dialog appears and the grant arrives through the callback.

## Target API level

**`targetSdk = 36`.** This is a requirement, not a preference.

From **31 August 2026**, a new app or an app update has to target Android 16
(API level 36) or higher to be accepted by Google Play. The extension to
1 November 2026 that Google offered has passed, so `targetSdk = 35` is no
longer accepted for a new release. The app was on 35 until this was checked
on 21 November 2026, which is the kind of thing that gets a release rejected
at the upload rather than at the review.

The requirement and the dates are in the Play Console Help:
https://support.google.com/googleplay/android-developer/answer/11926878

`compileSdk` was raised to 36 at the same time, because `targetSdk` cannot be
higher than the SDK it is compiled against. The Android Gradle Plugin in use
was released before API 36 was final, so it prints a warning about an
untested `compileSdk`. That warning is expected and is suppressed in
`gradle.properties` with `android.suppressUnsupportedCompileSdk=36`.

Check the requirement again before every release:
https://developer.android.com/google/play/requirements/target-sdk

## 16 KB page sizes

**Satisfied.** Since 1 November 2025, a new app or an update to an app
targeting Android 15 or higher has to support 16 KB page sizes, because
devices with 16 KB pages exist and an app that is not built for them does not
run on them.

The app ships four native libraries - `libsherpa-onnx-jni.so`,
`libonnxruntime.so`, `libpiper-phonemize-jni.so` and `libc++_shared.so` - and
all of them are built with the NDK, so this is a real question here rather
than a formality.

Every `LOAD` segment in every one of them is aligned to `0x4000`, which is
16384 bytes, which is 16 KB. That is checked against the built bundle, not
assumed:

```
$ llvm-readelf -l app/build/outputs/bundle/release/app-release.aab
libsherpa-onnx-jni.so  LOAD align: 0x4000, 0x4000, 0x4000
libonnxruntime.so      LOAD align: 0x4000, 0x4000, 0x4000, 0x4000
libpiper-phonemize-jni.so  LOAD align: 0x4000, 0x4000, 0x4000
libc++_shared.so       LOAD align: 0x4000, 0x4000, 0x4000, 0x4000
```

If the NDK is ever changed, this has to be checked again. A library aligned
to 4 KB loads on a 16 KB device, but it is loaded at the wrong alignment and
the device refuses it.

## What is still manual

The repository side of Phase 5 is now done. These parts need the Play
Console account and a real device:

- the developer account and the app's Play Console entry;
- upload the first `app-release.aab` to **internal testing**;
- enter the published policy URL, `https://jr12866.github.io/dart-coach-android/`,
  in **Policy → App content → Privacy policy**;
- upload the icon, feature graphic, and real screenshots;
- fill in the content rating questionnaire;
- complete the Data safety form using `docs/STORE-LISTING.md`;
- enable Play App Signing and accept the upload-key reset;
- create the `dartcoach_pro` one-time product and its purchase option;
- run the pre-launch report and test the install over mobile data.

**`docs/CONSOLE-SETUP.md` is the runbook for all of that.** It has every
field, in the order it has to be filled in, with the exact value for each
one.

## If the paywall turns out to be wrong

`docs/ROLLBACK.md` is the note: how to make the app free again, how to change
the price without a release, and what cannot be undone at all.

Read it before the first release, not after.
