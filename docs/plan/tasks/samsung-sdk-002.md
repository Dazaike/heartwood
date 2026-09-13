---
id: samsung-sdk-002
scope: Samsung Health SDK adapter
status: done
depends-on: [health-source-001]
---

## objective

Implement `SamsungHealthManager` and its vendored build dependency exactly as specified by the integration plan.

## context

- `docs/INDEX.md`
- `docs/plan/analysis/samsung-health-direct.md`
- `C:/Users/kingm/Downloads/samsung-health-integration-plan.md` (steps 1 and 4)

## path

- `app/libs/samsung-health-data-api.aar` (vendored Samsung Health Data SDK 1.1.0 binary)
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/gradle.lockfile`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/dev/easonhuang/heartwood/samsung/SamsungHealthManager.kt` (new)

## SDK source

The required official SDK archive is available locally at `C:/Users/kingm/Downloads/samsung-health-data-sdk-1.1.0.zip`; its AAR is already vendored at the declared library path. Inspect the AAR and these official documentation entry points before implementing aggregate or point reads:

- `C:/Users/kingm/Downloads/samsung-health-data-sdk-1.1.0.zip:1.1.0/docs/programming-guide.html`
- `C:/Users/kingm/Downloads/samsung-health-data-sdk-1.1.0.zip:1.1.0/docs/api-reference.html`
- `C:/Users/kingm/Downloads/samsung-health-data-sdk-1.1.0.zip:1.1.0/sample-code/health-diary-sample-app.html`

## verification

Regenerate locks with `./gradlew :app:dependencies --write-locks` and run `./gradlew :app:assembleDebug`.
