---
id: health-source-001
scope: data source abstraction
status: done
depends-on: []
---

## objective

Introduce the compile-safe, behavior-preserving source abstraction required for Samsung Health. Health Connect remains the default and its behavior must not change.

## context

- `docs/INDEX.md`
- `docs/plan/analysis/samsung-health-direct.md`
- `C:/Users/kingm/Downloads/samsung-health-integration-plan.md` (steps 2–3)

## path

- `app/src/main/java/dev/easonhuang/heartwood/data/HealthDataSource.kt` (new)
- `app/src/main/java/dev/easonhuang/heartwood/data/DataSourcePreferences.kt` (new)
- `app/src/main/java/dev/easonhuang/heartwood/data/HealthConnectManager.kt`

## change

1. Define `HealthDataSource` exactly as the eight-member read-only interface in the plan.
2. Make `HealthConnectManager` implement it by adding only the required `override` modifiers; retain all Health-Connect-specific onboarding members.
3. Add `DataSource { HEALTH_CONNECT, SAMSUNG_HEALTH }` and a preference-backed `DataSourcePreferences` using the existing `preferencesDataStore` convention. Invalid or absent persisted values must resolve to `HEALTH_CONNECT` as explicitly authorized by the plan.

## verification

Run `./gradlew :app:assembleDebug`. No Samsung SDK dependency may be added in this task.
