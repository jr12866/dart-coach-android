# Google Play store listing

The copy and disclosure answers for Darts Coach. Keep this file, the in-app
privacy text, and the Play Console Data safety answers in agreement.

## App name

**Darts Coach**

## Short description

> Voice-controlled 501 darts coach. Say your score; get the route to checkout.

64 characters.

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
> has no account, no ads, no analytics, and no internet permission. Your
> microphone audio is processed on the device and never leaves it.
>
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
| Contact email | Put the public support address here before submitting |
| Privacy contact | Put the public privacy address here before submitting |

The developer name shown in the Play listing must match the developer name in
the privacy policy.

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

Two captures already exist in `docs/store/screenshots/`: the main screen and
the privacy policy. The others need the app in the right state, which means
saying a score at the phone.

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
- Digital purchases: no
- The app is a darts training utility, not a game with gambling or wagering

## Ads

The app contains no ads.

## Data safety

Use the following answers. They are based on the app's actual manifest and
dependencies.

### Data collection

**No data is collected.**

Google's definition of collection is data that is retrieved from the device
and sent off it. Darts Coach accesses microphone audio and processes it only
in memory on the device. It has no `INTERNET` permission, so the audio cannot
leave the phone. Google explicitly says that data accessed only on the device
and not sent off it does not need to be disclosed as collected.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | **Not applicable** |
| Do you provide a way for users to request that their data is deleted? | **Not applicable** — the app has no account or server-side user data |

### Security practices

| Question | Answer |
|---|---|
| Does your app use encryption? | **No** — no data is transmitted |
| Does your app collect data from children? | **No** |
| Has your app been independently validated against a security standard? | **No** |
| Does your app contain third-party SDKs that collect or share user data? | **No** |
| Does your app use a recognized security standard? | **No** |

The app does include `piper-phonemize` and the sherpa-onnx Kotlin API, but
these are build-time/runtime libraries, not analytics SDKs. They do not collect
or transmit data.

### Permissions

| Permission | Reason |
|---|---|
| `RECORD_AUDIO` | Hear the player's spoken score and transcribe it on the device |
| Microphone | Required for the app's core function |
| No `INTERNET` permission | The app has no networking capability |

### Privacy policy

The public policy must be an active, public, non-geofenced web page, not a PDF
and not an editable document. The same policy text is included in the app via
`app/src/main/res/raw/privacy_policy.html`.

The privacy contact address must be real and monitored before submission.
