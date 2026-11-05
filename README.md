# Heartwood

Your health data is already on your phone. Heartwood lets you see it without gifting it to an account system, an ad network, or a venture-backed "wellness platform" that will eventually email you about mindfulness.

Heartwood is a private, **read-only** Android viewer for [Health Connect](https://developer.android.com/health-connect) and, if you insist on Samsung's ecosystem, Samsung Health. It reads the data already on your device, renders it properly, and sends precisely none of it anywhere.

> No accounts. No network permission. No trackers. Your data stays on your device. Revolutionary, apparently.

## What it does

- **Today**, Material You cards for steps, heart rate, sleep, energy, distance, exercise, SpO₂, weight, blood pressure, and the other numbers your body generates while corporate wellness apps harvest metadata.
- **Detail views**, charts, statistics, and recent records for every metric. Numbers, organized. A bold new concept.
- **Weekly summary**, daily averages against goals, progress rings, and week-over-week trends. You set the goals; the app declines to invent a lifestyle for you.
- **Home-screen widgets**, dynamically themed Glance + Material 3 widgets for the metrics worth seeing before you unlock your phone.
- **Export & AI Share**, write CSV or JSON through Android's file picker, or share formatted Markdown directly with your AI assistant through the system share sheet. No bespoke cloud relay because that would be stupid.
- **Unit system**, switch between Imperial (miles, lbs, in, fl oz) and Metric (km, kg, cm, L). Imperial is the default. Debate it with physics, not us.
- **Customizable dashboard**, reorder metric tiles and hide the noise. Your Today view, not a product manager's.
- **Samsung Health** *(optional)*, read directly from the Samsung Health app on-device instead of Health Connect.
- **Local AI server (MCP)** *(optional)*, an opt-in loopback Model Context Protocol server for local LLMs and tools. `localhost` only. The cloud does not need your resting heart rate.

## The boringly competent stack

- Kotlin, Jetpack Compose, Material 3, dynamic color
- `androidx.health.connect:connect-client` and Samsung Health Data SDK for on-device reads
- `androidx.glance` widgets and WorkManager refreshes
- DataStore for goals, dashboard layout, data source, and unit preferences
- NanoHTTPD for the optional loopback MCP server
- `minSdk 30`, `targetSdk 36`

No "AI-native health intelligence layer." Just native Android code reading local data like it was designed to.

## Build it

Clone the repository. Build the APK. Avoid downloading a random prebuilt binary from a forum run by a man named `APKMaster69`.

```sh
git clone https://github.com/Dazaike/heartwood
cd heartwood
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or use Nix, which pins the toolchain so your build remains reproducible instead of becoming an archaeological dig six months from now:

```sh
nix develop
./gradlew :app:assembleRelease
```

You need JDK 17 and the Android SDK (`platforms;android-36`, `build-tools;36.0.0`). Gradle pins the rest because manually reconstructing dependency versions is how people lose weekends. See [docs/REPRODUCIBLE.md](docs/REPRODUCIBLE.md) for the full reproducible-build setup and Nix notes.

### Sign a release, if you must

Create `keystore.properties` (already gitignored) or provide the equivalent `HEARTWOOD_*` environment variables:

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without signing credentials, `assembleRelease` emits an unsigned APK. That is fine for F-Droid; it signs its own builds because supply-chain hygiene is not optional.

## CI, because hope is not a test strategy

- **CI** (`.github/workflows/ci.yml`) builds and lints the debug APK on every push and pull request.
- **Release** (`.github/workflows/release.yml`) builds the release APK for every `v*` tag, signs it when secrets exist, and attaches it to the GitHub Release.

## F-Droid

Store descriptions and changelogs in `fastlane/metadata/android/en-US/`. The `fdroiddata` recipe template lives at [docs/fdroid-metadata-template.yml](docs/fdroid-metadata-template.yml). Follow it. F-Droid is not impressed by improvisation.

## Privacy: the entire point

Heartwood requests only **read** permissions. It never transmits data to an external server. You decide which types it can read and can revoke them whenever you like.

The optional Samsung Health integration reads directly from Samsung Health through its official on-device SDK. The optional AI server listens strictly on loopback (`localhost`). Your personal health record stays on your device, where it belongs. The robots have standards.

## License

[GPL-3.0-or-later](LICENSE) © Eason Huang
