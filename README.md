# Pinnr

One-touch location pins for Android Auto. Make categories on your phone, then tap a category tile in the car to drop a pin at your current location. Export any category (or everything) as **CSV** or **GPX**.

## Install

1. Open **Releases** on this repo and download the latest `pinnr-N.apk` on your phone.
2. Open it and allow installing from your browser/files app when Android asks.
3. Open Pinnr once and **allow precise location**.

## Show it in Android Auto (sideloaded builds)

Android Auto hides apps that didn't come from the Play Store until you enable developer settings:

1. On your phone open **Settings → Apps → Android Auto → Additional settings in the app** (or open Android Auto from the launcher).
2. Scroll to the bottom and tap **Version** about 10 times until developer mode is enabled.
3. Open the **⋮ menu → Developer settings** and turn on **Unknown sources**.
4. Back in Android Auto settings, open **Customise launcher** and make sure **Pinnr** is ticked.
5. Connect to the car. Pinnr appears in the app launcher.

To test without a car, use the [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu).

## Using it

- **Phone:** create categories with **New category**, pick a colour, and use the car icon to choose which ones show in the car. Open a category to see pins, add notes, open a pin in your maps app, or drop a pin manually.
- **Car:** tap a tile to pin. A toast confirms it, and **Undo** appears in the top corner for the last pin.
- Each pin records location, time, GPS accuracy and a street address (looked up in the background).

## Getting pins into Google Maps

Google Maps can't import into its own saved lists, but **Google My Maps** can, and My Maps show up in the Google Maps app under **Saved → Maps**.

1. Export a category as **CSV** (category screen → ⋮ → Export as CSV) and save it to Drive or email it to yourself.
2. Go to [mymaps.google.com](https://mymaps.google.com) → **Create a new map** → **Import**.
3. Choose the CSV, pick **Latitude** and **Longitude** as the position columns and **Name** as the title.

GPX exports work with OsmAnd, Organic Maps, Garmin and most GPS apps.

## Building

Every push to `main` builds a debug APK with GitHub Actions and publishes it as a release. Locally: open in Android Studio and run, or `gradle assembleDebug`.

## Publishing later

The car side uses the Car App Library POI category and only templates, and release builds restrict which hosts may connect. Before publishing you'll need a release signing key and Play Console's Android Auto review.

## What's new in 0.2

- **Pin-behind (Android Auto):** tap the `Now` button in the top bar to cycle `-10s`, `-20s`, `-30s`, then tap a tag. The pin goes where you were that many seconds ago. Resets to `Now` after each pin.
- **Move pins between tags:** open a pin on the phone, tap `Tag:` and pick another tag, then Save.
- **KML export:** `Download KML` / `Share KML` in the export menus. Import into Google My Maps or Google Earth; each tag becomes its own folder with its colour.
