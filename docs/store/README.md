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

Before publishing:

1. Replace the example privacy email in `privacy-policy.html` and
   `docs/PRIVACY-POLICY.md` with a monitored address.
2. Replace the developer name if the Play listing uses a different name.
3. Publish `privacy-policy.html` at a stable public HTTPS URL and put that URL
   in both Play Console and the app.
4. Upload the icon, feature graphic, and at least two phone screenshots.
