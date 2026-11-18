# Google Play store listing

The copy and disclosure answers for Darts Coach. Keep this file, the in-app
privacy text, and the Play Console Data safety answers in agreement.

## App name

**Darts Coach**

## Short description

> Voice-controlled 501 darts coach. Say your score; get the route to checkout.

76 characters, against Play's limit of 80.

## Full description

> Your phone is your darts coach.
>
> Say the score after your visit and Darts Coach works out what to throw next.
> It tracks the leg, chooses a checkout route, and talks you through the
> shots. Keep your eyes on the board.
>
> **A coach that thinks like a darts player**
>
> - Finds checkout routes from 501 to the double
> - Plans the visit, not just the finish
> - Knows the bogey numbers and the splits to avoid
> - Handles doubles, including the double-out finish
>
> **Built for the oche**
>
> - Say your score in plain English: "one forty", "treble twenty", "double six"
> - The coach reads it back before counting it, so a quiet number never changes
>   your score by accident
> - The screen stays awake during a leg
> - Undo a throw with one tap
>
> **Private by design**
>
> Speech recognition and the coach's voice both run on your phone. Darts Coach
> has no account, no ads, and no analytics. Your microphone audio is processed
> on the device and never leaves it. The only network connection is to Google
> Play, to take payment for the optional Pro upgrade.
>
> **Darts Coach Pro - one purchase, no subscription**
>
> Pro is $2.99, once. It works out your own hit rates from your own play, and
> gives you the routes that suit you rather than the ones that suit everyone.
> It adds the full out chart for every score from 2 to 170, the setup coaching
> for the middle of the leg, and your own statistics. The live coach keeps
> working either way.
> A large first install is expected: the speech models and the voice are
> included in the app so it works offline and does not need a download step.
>
> Suitable for Android phones with a microphone. Headphones are recommended
> when other people are nearby.
>
> Darts Coach is an independent training aid. It is not affiliated with the
> PDPA, WDF, PDC, or any other darts organisation.

## Category and contact

| | |
|---|---|
| App type | Application |
| Category | Sports |
| Tags | Darts, coach, scorekeeping, 501, training |
| Developer name | `Joe Rutsky` - the same string as in the privacy policy |
| Contact email | `jr12866@outlook.com` |
| Privacy contact | `jr12866@outlook.com` |
| Privacy policy URL | `https://jr12866.github.io/dart-coach-android/` |

The developer name shown in the Play listing must match the developer name in
the privacy policy. Set the Play Console developer name to `Joe Rutsky`, so the
two agree.

## Feature graphic

- 1024 x 500 pixels
- JPEG or 24-bit PNG, no alpha channel
- Use the same colours as the app: near-black background, green rings, amber
  bullseye
- Keep the dartboard and title in the centre, away from the edge cutoff zones
- Suggested wording: **"Say the score. Get the route."**

## App icon

- 512 x 512 pixels
- 32-bit PNG with alpha
- Maximum 1024 KB
- Full square; Google Play applies the rounded mask and shadow
- Use the dartboard artwork from `play/icon-512.png`

## Screenshots

At least two phone screenshots are required. Use 1080 x 1920 PNG or JPEG, with
no alpha. Suggested sequence:

1. The score and suggestion: **"401"**, **"Treble twenty, treble twenty and
   treble twenty"**
2. The read-back confirmation: **"You said one hundred and forty. Right?"**
3. The checkout plan: **"That leaves 221"**
4. The privacy promise: **"Everything happens on your phone"**

Take the real screenshots from the app rather than mock-ups, and remove the
Android status/navigation bars if the capture tool allows it. Do not add
device frames or promotional badges.

**Four captures exist now**, all in `docs/store/screenshots/`, all 1080 x 1920
PNG with no alpha:

| File | Shows |
|---|---|
| `01-main.png` | The scoreboard |
| `02-suggestion.png` | **401**, "Treble twenty, treble twenty and treble twenty", T20 T20 T20, leaving 221 |
| `03-privacy.png` | The privacy promise |
| `04-readback.png` | **"You said one hundred and forty. Right?"** |

The two that needed the app to hear a score were taken with the self-hearing
test, which plays the phrase through the speaker with the microphone gate
left open. `docs/store/screenshots/README.md` has the command.

Two more would be worth having when there is time: the checkout plan on its
own ("That leaves 221" as a spoken plan), and the out chart.

## Alt text

- Icon: `Darts Coach app icon: a green and amber dartboard on a dark
  background.`
- Feature graphic: `Darts Coach app icon and the words Say the score. Get the
  route.`

## Content rating

The questionnaire is entered in Play Console. The expected answers are:

- Violence, sexuality, language, controlled substances: none
- User-generated content: none
- Users can communicate with each other: no
- Digital purchases: **yes** - Darts Coach Pro, a one-time purchase of $2.99,
  handled by Google Play. No subscription.
- The app is a darts training utility, not a game with gambling or wagering

## Ads

The app contains no ads.

## In-app purchases

| | |
|---|---|
| Name | Darts Coach Pro |
| Type | One-time purchase, not a subscription |
| Price | $2.99 (or the local equivalent, set in Play Console) |
| What it unlocks | Personalised hit rates, the routes onto each of your doubles, the full out chart, the setup coaching, persistent preferences, and the analysis of where your misses go |

The product is a **one-time product** in the Play Console, with a purchase
option inside it that carries the price. The product id is `dartcoach_pro`
and it cannot be changed once the product is created. `docs/CONSOLE-SETUP.md`
has the exact steps.

The free tier is a working coach. The live suggestion, the scorekeeping, the
read-back and the undo all work without paying, and the player can set any
hit rate by hand, which makes the coach better for them.

The free tier does not show the percentages, because it is not able to
personalise them. It gives the plan - which dart to throw - which is what a
player needs at the oche.

## Data safety

The answers below are based on the app's actual manifest and dependencies.

### Data collection

**Purchase data is collected. Nothing else is.**

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** - purchases only |
| Which type? | **Purchase history** - the Google Play Billing Library reports what the account has bought |
| Is all of the user data collected by your app encrypted in transit? | **Yes** - the purchase is handled by Google Play, over Play's own encrypted connection |
| Do you provide a way for users to request that their data is deleted? | **Not applicable** - the app has no account and no server-side user data of its own. The purchase is deleted through Google Play. |

The microphone is the one that looks like it should be declared and is not.

Google defines "collected" as data that is retrieved from the device and sent
off it. The app's microphone audio is processed only in memory on the device
and is never sent anywhere - no code in the app reads the audio buffer and
writes it to a socket. The app's `INTERNET` permission is used for exactly one
thing, talking to Google Play about the purchase.

The purchase data is sent, so it is declared. The microphone audio is not
sent, so it is not.

### Security practices

| Question | Answer |
|---|---|
| Does your app use encryption? | **Yes** - for the purchase, handled by Google Play |
| Does your app collect data from children? | **No** |
| Has your app been independently validated against a security standard? | **No** |
| Does your app contain third-party SDKs that collect or share user data? | **Yes** - the Google Play Billing Library, which handles the purchase. No advertising, analytics, or crash-reporting SDKs. |
| Does your app use a recognized security standard? | **No** |

The app does include `piper-phonemize` and the sherpa-onnx Kotlin API, but
these are build-time/runtime libraries, not analytics SDKs. They do not collect
or transmit data.

### Permissions

| Permission | Reason |
|---|---|
| `RECORD_AUDIO` | Hear the player's spoken score and transcribe it on the device |
| Microphone | Required for the app's core function |
| `INTERNET` | Talk to Google Play to take the payment for Darts Coach Pro. Nothing else uses it. |
| `com.android.vending.BILLING` | Added by the Play Billing Library, for the Pro purchase |
| `ACCESS_NETWORK_STATE` | Added by the Play Billing Library, to check whether Play is reachable |

The last two are ordinary permissions that arrive with the billing library.
Neither of them sends anything anywhere on its own, and neither is a
"collected data type" on the Data safety form.

### Privacy policy

The public policy must be an active, public, non-geofenced web page, not a PDF
and not an editable document. The same policy text is included in the app via
`app/src/main/res/raw/privacy_policy.html`.

The published page is **`https://jr12866.github.io/dart-coach-android/`**. It
is served by GitHub Pages from `site/index.html` in this repository, and that
file is generated from `docs/PRIVACY-POLICY.md` by
`tools/generate-privacy-policy.py`, so the page, the store copy and the copy
inside the app cannot drift apart.

The privacy contact address must be real and monitored before submission.
