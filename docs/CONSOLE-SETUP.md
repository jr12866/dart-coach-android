# Setting up the Play Console

Everything that has to be typed into the Play Console, in the order it has to
be typed in, with the exact value for each field.

This is the runbook for the two things that cannot be done from the
repository: the **app entry** and the **`dartcoach_pro` product**. It is
written so that it can be followed without reading anything else.

The console cannot be scripted. There is no Play Console connector on this
machine and no Play Developer API credentials, so every step below is done by
hand in the browser, and this file is the thing that holds the values.

**The order matters, and one step is not where it looks.** Play will not let
you create a product until the app has a bundle in a track, so the upload
comes *before* the product, not after it.

---

## What is already done, so it does not have to be done again

| | |
|---|---|
| The release keystore | `C:/Users/jr128/dart-coach-keys/dart-coach-release.jks`, alias `dart-coach` |
| The signing properties | in `local.properties`, which is not in git |
| The signed bundle | `app/build/outputs/bundle/release/app-release.aab`, 153 MB, signature verified |

The bundle was built with:

```powershell
.\gradlew.bat :app:bundleRelease
```

**Do not pass `-PwithLocalModels` to the release build.** It copies the models
into the base module as well as the asset pack, and the bundle build fails
because both modules then claim the same asset.

---

## Before you start: three things that are not in the console

1. **The support email and the privacy email.** Two real, monitored addresses.
   `docs/STORE-LISTING.md` has the values for them: `jr12866@outlook.com` for
   both. The developer name in the listing must match the developer name in
   the privacy policy, which is **`Joe Rutsky`**.

2. **The privacy policy, at a public URL.** It has to be an active, public,
   non-geofenced web page - not a PDF, not an editable document. **This is
   done**: the policy is published at
   **`https://jr12866.github.io/dart-coach-android/`**, served by GitHub
   Pages from `site/index.html`. The same text is in
   `docs/PRIVACY-POLICY.md` and in the app itself, and all four copies are
   generated from one source by `tools/generate-privacy-policy.py`, so they
   cannot drift apart. Put that URL in the console and in the app.

3. **The app name.** "Darts Coach" may already be taken on Play. The form in
   step 2 says so before you can create the app, and what to do about it is
   in step 2.

---

## Step 1 — The developer account

The $25 one-time registration is done; the identity verification is what is
waiting. Once it clears, the account is usable.

**Set up the payments profile** as soon as the account is live, because
nothing can be sold without one:

```
Play Console → Settings → Developer account → Payments profile
```

Fill in the business details and add a payment method to receive earnings.
The app has no server and no account of its own, so this is Google's merchant
registration, and it is the one part of the Pro tier that needs Google's
paperwork rather than the app's.

---

## Step 2 — The app entry

```
Play Console → Create app
```

| Field | Value |
|---|---|
| App name | `Darts Coach` |
| Package name | `com.jr12866.dartcoach` |
| Default language | English (United States) |
| App or game | **App** |
| Free or paid | **Free** - it has an in-app purchase, but the app itself is free |
| Declarations | Accept the declarations |

**The package name is typed here, and it is the one thing on this form that
cannot be changed.** Play checks it against the bundle when the first release
is uploaded, and the app record is keyed on it. It has to be
`com.jr12866.dartcoach`, which is the `applicationId` in
`app/build.gradle.kts` - not the keystore's certificate name, which is
`CN=Darts Coach` and is a signing identity rather than a package name.

**Default language.** The form asks for the primary language of the store
listing, not the language of the app, and it can be changed later in **Store
settings**. English (United States) is what the app's own resources use.
English (United Kingdom) would match the wording and the British voice better
- the coach says "treble twenty" - and is a defensible choice; it is a
judgement call rather than a requirement.

The "Check availability" link under the package name field tells you whether
the name has been taken. **"Darts Coach" may already be taken on Play** - if
it is, pick a different name and change `app_name` in
`app/src/main/res/values/strings.xml` to match, so the listing and the
installed app say the same thing.

---

## Step 3 — The app content, before anything can be reviewed

All of these are under **Policy → App content**.

### Privacy policy

```
Policy → App content → Privacy policy
```

Paste in `https://jr12866.github.io/dart-coach-android/`. The in-app copy is
bundled with the app already, and the app has a **Privacy policy** button, so
this is the same document in four places.

### Ads

```
Policy → App content → Ads
```

**No ads.** The app contains no advertising SDK.

### Target audience and content

```
Policy → App content → Target audience and content
```

The app is a training aid for adults. Pick the age groups that apply, and
make sure the chosen ages do not include children, because the app collects
no data from children and the answer on the Data safety form is "no".

### Content rating

```
Policy → App content → Content rating
```

Fill in the questionnaire. The answers are in `docs/STORE-LISTING.md` and
they are short: nothing for violence, sexuality, language or controlled
substances; no user-generated content; users cannot communicate with each
other; **digital purchases: yes**.

### Data safety

```
Policy → App content → Data safety
```

The answers are in `docs/STORE-LISTING.md`. The short version:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** - purchases only |
| Which type? | **Purchase history** |
| Is the data encrypted in transit? | **Yes** - Play handles the purchase |
| Can users request deletion? | **Not applicable** - no account, no server-side data of its own |

The microphone is the one that looks like it should be declared and is not.
Google defines "collected" as data sent off the device, and the microphone
audio is processed in memory and never sent. The purchase *is* sent, so it is
declared.

### Sign-in details (app access)

```
Policy → App content → Sign-in details
```

**"Is any part of your app restricted?" → Yes.**

This is the one declaration that is not about sign-in at all, and it is easy
to answer wrongly. Play's list of what counts as restricted includes
**"payments, such as one-time products, memberships, subscriptions, and/or
access tiers"**, so an app with an in-app purchase answers **Yes** even when
it has no account and no login. The **No** option says *"no account sign in
required and no content requires payment"*, and the Pro tier is a payment.

So the answer is **Yes**, and Play then asks how a reviewer reaches the paid
content. Google's own guidance for exactly this case is: *"If your app does
not require sign-in details but you have functionalities or content behind a
subscription paywall, please provide additional instructions or access
details that will allow us to fully and freely access and review the app
behind the paywall."*

### The tick box has to be ticked

It is the control Play refuses to save without. The App Store Review API
schema calls it `POLICY_QUESTION_ID_TESTING_CREDENTIALS_NO_ADDITIONAL_DETAILS_REQUIRED`
- a multiple choice with a single answer and the condition **"always required
within the group"**. On screen it reads *"Sign in details in this declaration
provide full access to all the features and content within this app, including
premium or paid content"*. Left blank, the console prints a red *"Is any other
information required?"* underneath it and refuses the save with *"Your changes
couldn't be saved"*. That red prompt is the console asking for this one
missing answer; it is not a complaint about the text in the box above it.

Ticking it is a promise: the details in this declaration are enough to get a
reviewer into everything, the paid tier included. With no account and no
credentials that promise cannot be kept, so it is made true by giving the
reviewer a Google account that is on our **licence testing** list. A licence
tester can buy the one-time product as a test purchase, is not charged, and
ends up with Pro unlocked.

#### The review account, made first

1. Create a Google account for the review only - a spare address, a password
   not reused anywhere else, and **no 2-step verification**, because a
   reviewer cannot get past a prompt they cannot answer.
2. Play Console -> **Monetize -> Monetization setup -> License testing**.
   Create an email list (say `Google Play Review`), add the address, **Save
   changes**. Leave the license response at its default, *respond normally*,
   so the test purchase follows the real billing flow with a simulated card.
3. Put the address in the **Username** field and its password in the
   **Password** field of the declaration. These are not app logins - the app
   has no account system - they are the Google account the reviewer signs the
   test device in with.

#### The form, field by field

| Field | What goes in it |
|---|---|
| Name | `Darts Coach reviewer account` |
| Username, email or phone | the review Google account |
| Password | that account's password |
| Any other information required to access your app | The text below |
| The tick box about full access to paid content | **ticked** - it is required, and it is true once the review account can buy Pro |

The "Any other information" text, which is 427 characters and fits the 500
character limit:

```
No app account and no sign-in screen. The coach hears your score, reads it back and plans the visit and the checkout. Pro is a one-time Play purchase ($2.99) that unlocks the out chart, personalised routes and statistics. To review Pro: sign the test device in to Google with the account above (it is on our Play licence testing list), open Upgrade and buy Pro - the purchase is free for licence testers and unlocks everything.
```

#### Two things to know before the review

The licence tester status has to reach the reviewer's device. It is applied
by the Play Store app, so the account must be the **primary** Google account
on the device and the Play Store needs time to refresh. Where it has not
applied, the reviewer still gets the whole free coach - hearing the score,
the read-back, the checkout plan, the setup plan and the learning - which is
the part of the app the ratings come from.

The test purchase also needs the one-time product to be **published**, so
this declaration is written now and *verified* after step 7, before the
release is sent for review.

**If the review account is not wanted at all.** The tick still has to be
ticked, and with nothing to hand over the statement about paid content
cannot honestly be made. Play can still review the free tier, but a reviewer
who cannot reach Pro may hold the release, so this is a last resort rather
than a shortcut.

---

## Step 4 — The store listing

```
Grow → Store presence → Main store listing
```

The copy is in `docs/STORE-LISTING.md`, ready to paste:

- **Short description** (64 characters): `Voice-controlled 501 darts coach. Say your score; get the route to checkout.`
- **Full description**: the long one in `docs/STORE-LISTING.md`
- **App icon**: `docs/store/icon-512.png` (512 x 512, 32-bit PNG with alpha)
- **Feature graphic**: `docs/store/feature-graphic.png` (1024 x 500)
- **Phone screenshots**: all four are in `docs/store/screenshots/`, taken on the
  Pixel 7 and cropped to 1080 x 1920 - `01-main.png`, `02-suggestion.png`,
  `03-privacy.png` and `04-readback.png`. Play wants at least two; four is
  better, and they should be uploaded in that order.

---

## Step 5 — Upload the bundle to internal testing

**This has to happen before the product can be created.** Play will not let
you configure monetisation for an app that has no bundle in a track.

```
Test and release → Testing → Internal testing → Create new release
```

| | |
|---|---|
| Release name | `0.2.0 (2)` |
| Bundle | `app/build/outputs/bundle/release/app-release.aab` |
| Release notes | The text below, which is under the 500 character limit |

```
Darts Coach is a voice-controlled 501 coach.

Say your score after a visit and it works out what to throw next: checkout
routes from 501 to the double, and setup plans for the middle of the leg. It
reads the number back before it counts it, so a quiet number never changes
your score by accident. It learns your own hit rates from your own play.

Two voices for the coach, British and American, and you pick which one.

Everything runs on your phone. No account, no ads, no analytics.
```

Save and publish. The install-time asset pack arrives with it, so this is
also the first test of the 143 MB install over mobile data.

### Play App Signing

```
Test and release → App integrity → Play App Signing
```

Accept the upload key reset, so the key that is **not** the signing key of
record is the one in `dart-coach-keys/`. That is what makes the upload key
recoverable if it is ever lost.

The app signing key becomes the key of record. Back the keystore up anyway:
it is the only copy of the upload key, and Play has been known to take
several days to complete a key reset.

---

## Step 6 — The product

Only now, with a bundle in a track:

```
Monetize with Play → Products → One-time products → Create one-time product
```

### The product

| Field | Value |
|---|---|
| Product ID | `dartcoach_pro` |
| Name | `Darts Coach Pro` |
| Description | `The personalised coach: your own hit rates, the full out chart, the setup coaching and your statistics.` |

**The product ID cannot be changed once the product is created.** It has to
be `dartcoach_pro`, because that is what `BillingRepository.PRODUCT_ID` asks
Play for. A mismatch means the app cannot find the product and the purchase
silently fails.

### The purchase option

The product needs at least one purchase option, and this is what carries the
price:

| Field | Value |
|---|---|
| Purchase option ID | `buy` |
| Purchase type | **Buy** |
| Is it digital content or a service? | **Digital content** - it is a set of features in the app |
| Price | **$2.99** |
| Availability | All regions, or whichever ones are wanted |

Save the changes and **activate** the product. A product that is not active
does not appear in the purchase flow at all.

The app reads the price from Play rather than from a string in the code, so
the price shown to the player is right for their country. Changing it later
is a change in the console and no release.

### One thing to know about the old name

This used to be called an "in-app product". It is now a **one-time product**,
and the price lives in a **purchase option** inside it rather than on the
product. The old "In-app products" page still exists for apps that were
created before the change. The app is written against the current model: it
reads `oneTimePurchaseOfferDetailsList` from the product details, which is
the purchase option's price and offer token.

---

## Step 7 — Test the purchase

This is the step that proves the whole Pro tier, and the one that has never
been run.

1. Add the test account in Play Console, or use an account you own.
2. Install from **internal testing**, not by sideloading. A sideloaded APK
   cannot buy anything, because Play Billing needs the app to be known to
   Play.
3. Buy Pro. It is a one-time purchase, so it can be bought as many times as
   needed for testing.
4. Check that the Pro screens open and the paywall does not come back:
   - the out chart opens with the percentages and the routes onto each double
   - the setup chart opens
   - the statistics open
   - the missed-target correction works: say "I hit single eight instead"
   - the preferred double survives a restart
5. **Refund it** from the Play Console and check the other path: the
   entitlement goes away and the Pro screens lock again.

The purchase is acknowledged by the app. A non-consumable that is not
acknowledged is refunded by Play after three days and the entitlement is
taken away, so if the entitlement disappears by itself after three days,
that is the bug to look for.

---

## Step 8 — Production, and the part that takes the longest

If the developer account is a **personal account created after 13 November
2023**, the app cannot go to production straight from internal testing.

Google requires:

- a **closed** test with at least **12 testers** who have been opted in
  **continuously for at least 14 days**;
- then **Apply for production** on the Dashboard, answering questions about
  the closed test, the app, and production readiness;
- then a production release.

Things that catch people out:

- **Internal testing days do not count.** The 14 days have to be on a closed
  test.
- The counter tracks the **testers' opt-in**, not your builds. Shipping a new
  release to the closed track during the 14 days resets nothing.
- A tester who opts out and back in starts their 14 days again.
- Recruit more than 12. A dropout on day 13 costs another fortnight.

Budget **four to six weeks** for this in total, on top of the identity
verification. It is the longest pole in the whole release, and it is worth
starting the closed test as soon as the internal test is stable rather than
waiting for everything else to be finished.

---

## The order, as one list

1. Payments profile
2. Create the app entry
3. App content: privacy policy, ads, target audience, content rating, data safety, **sign-in details** (written now; the licence-tester purchase is verified after step 7)
4. Store listing: copy, icon, feature graphic, screenshots
5. Upload the signed bundle to **internal testing**
6. Play App Signing: accept the upload key reset
7. Create the **one-time product** `dartcoach_pro`, its purchase option, its price; **activate** it
8. Test the purchase, and test the refund
9. Closed test with 12 testers for 14 days
10. Apply for production, then create the production release

Steps 1 to 6 have to happen before step 7. Step 7 has to happen before
step 8, or there is nothing to sell.

---

## Sources

- [Create an in-app product](https://support.google.com/googleplay/android-developer/answer/1153481?hl=en)
- [Overview of one-time products](https://support.google.com/googleplay/android-developer/answer/16430488?hl=en)
- [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Set up an open, closed or internal test](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)
