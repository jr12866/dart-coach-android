# Store assets

The files in this directory are the publishable Play Store assets and the
public privacy-policy page.

| File | Purpose |
|---|---|
| `icon-512.png` | Play listing app icon, 512 x 512 |
| `feature-graphic.png` | Play feature graphic, 1024 x 500, no alpha |
| `privacy-policy.html` | Public privacy-policy page |
| `screenshots/` | Real device screenshots, 1080 x 1920 |

The copy and the exact Data safety answers are in `docs/STORE-LISTING.md`.

The privacy policy is published at
**`https://jr12866.github.io/dart-coach-android/`**, served by GitHub Pages
from `site/index.html`. All four copies of the policy - the markdown source,
this page, the copy bundled in the app and the published page - are generated
from `docs/PRIVACY-POLICY.md` by `tools/generate-privacy-policy.py`, so they
cannot drift apart. The published page updates itself when the policy changes,
because the GitHub Actions workflow in `.github/workflows/pages.yml` deploys
`site/` on every push.

Before publishing:

1. Upload the icon, feature graphic, and at least two phone screenshots.
2. Put the published policy URL in Play Console, under **Policy → App content
   → Privacy policy**.
