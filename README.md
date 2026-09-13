# Heartwood

A private, **read-only** Android health-data viewer for [Health Connect](https://developer.android.com/health-connect)
or, optionally, the Samsung Health app. Heartwood shows the health and fitness data already on
your device, beautifully, and without sending a single byte anywhere.

> No accounts. No network permission. No trackers. Just your data, on your device.

## Features

- **Today**, Material You cards for steps, heart rate, sleep, energy, distance, exercise,
  SpO₂, weight, blood pressure and more, each with a sparkline.
- **Detail views**, full charts, statistics and recent records per metric.
- **Weekly summary**, daily averages vs. goals you set, with progress rings and
  week-over-week trends.
- **Home-screen widgets**, key metrics, dynamically themed (Glance + Material 3).
- **Export & AI Share**, export data to CSV/JSON via file picker, or export formatted Markdown directly to your AI assistant of choice via the Android system share sheet.
- **Unit System**, toggle between Imperial (miles, lbs, in, fl oz) and Metric (km, kg, cm, L) in Settings (defaults to Imperial).
- **Customizable Dashboard**, reorder and show/hide metric tiles to personalize your Today view.
- **Samsung Health (optional)**, choose to read directly from the Samsung Health app on-device instead of Health Connect, in Settings.
- **Local AI Server (MCP, optional)**, opt-in loopback Model Context Protocol server to allow local LLMs/tools to inspect your health stats without cloud access.
## Tech

- Kotlin, Jetpack Compose, Material 3 (dynamic color)
- `androidx.health.connect:connect-client` and Samsung Health Data SDK for on-device reads
- `androidx.glance` widgets, WorkManager for periodic refresh
- DataStore for goals, layout, data source, and unit preferences
- NanoHTTPD for optional local loopback MCP server
- `minSdk 30`, `targetSdk 36`

## Build

```sh
git clone https://github.com/Dazaike/heartwood
cd heartwood
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or with Nix (pins the whole toolchain):

```sh
nix develop
./gradlew :app:assembleRelease
```

Requires JDK 17 and the Android SDK (`platforms;android-36`, `build-tools;36.0.0`). The
Gradle wrapper pins everything else. See [docs/REPRODUCIBLE.md](docs/REPRODUCIBLE.md) for the
full reproducible-build toolchain and Nix notes.

### Signing a release (optional)

Create `keystore.properties` (gitignored) or set the matching `HEARTWOOD_*` env vars:

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, `assembleRelease` produces an unsigned APK, which is fine for F-Droid, since it
signs its own builds.

## CI

- **CI** (`.github/workflows/ci.yml`), builds + lints the debug APK on every push/PR.
- **Release** (`.github/workflows/release.yml`), on a `v*` tag, builds the release APK
  (signed if secrets are present) and attaches it to a GitHub Release.

## F-Droid

Descriptions and changelogs live under `fastlane/metadata/android/en-US/`. A recipe template
for the `fdroiddata` repo is in [docs/fdroid-metadata-template.yml](docs/fdroid-metadata-template.yml).

## Privacy

Heartwood requests only **read** permissions and never transmits data to any external server.
You control which data types it can read at any time. The optional Samsung Health integration
reads directly from the Samsung Health app on-device via its official SDK. The optional local AI
server operates strictly over loopback (`localhost`), keeping all personal health records entirely on your device.
## License

[GPL-3.0-or-later](LICENSE) © Eason Huang
