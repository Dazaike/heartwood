---
id: samsung-ui-003
scope: Samsung Health application integration
status: done
depends-on: [samsung-sdk-002]
---

## objective

Wire the active data source through Compose, widgets, MCP, settings, manifest, and README.

## integration correction

`HeartwoodApp` currently constructs its singleton `ExportManager` with `healthConnect`. Merely widening that constructor parameter to `HealthDataSource` would retain Health Connect after a user switches sources. Preserve the plan's selected-source contract by making exports resolve `activeDataSource()` at export time (or an equivalent source-switch-safe construction); do not leave a long-lived export manager pinned to `healthConnect`.

## context

- `docs/INDEX.md`
- `docs/plan/analysis/samsung-health-direct.md`
- `C:/Users/kingm/Downloads/samsung-health-integration-plan.md` (steps 5–9)

## path

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/dev/easonhuang/heartwood/HeartwoodApp.kt`
- `app/src/main/java/dev/easonhuang/heartwood/MainActivity.kt`
- `app/src/main/java/dev/easonhuang/heartwood/ui/**`
- `app/src/main/java/dev/easonhuang/heartwood/mcp/**`
- `app/src/main/java/dev/easonhuang/heartwood/widget/HeartwoodWidget.kt`
- `app/src/main/java/dev/easonhuang/heartwood/widget/MetricWidget.kt`
- `app/src/main/java/dev/easonhuang/heartwood/data/ExportManager.kt`
- `README.md`

## verification

Build the debug APK, then run the plan's physical-device source-switch, Samsung permission, widget, MCP, supported/unsupported metrics, Health Connect return, and unavailable-platform smoke passes.
