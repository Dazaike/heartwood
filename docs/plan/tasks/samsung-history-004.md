---
id: samsung-history-004
scope: Samsung Health daily-history correctness
status: done
depends-on: [samsung-sdk-002]
---

## objective

Correct Samsung Health daily totals so prior calendar days use the SDK's daily grouped aggregate buckets, and display activity-summary distance in kilometers.

## defect

The current adapter runs one aggregate request per day and consumes only the first result. Samsung Health's grouped daily aggregation returns timestamped buckets; use those bucket dates and retain explicit zero-valued entries for dates with no bucket.

## path

- `app/src/main/java/dev/easonhuang/heartwood/samsung/SamsungHealthManager.kt`

## verification

Build and install the debug APK on the connected Android device. With Samsung Health Data SDK Developer Mode enabled and reads granted, refresh Heartwood and verify that prior-day step totals render from Samsung Health and distance is displayed in km.