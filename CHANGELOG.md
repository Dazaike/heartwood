# Changelog

## [v1.0.1] - 2026-11-05

### Added
- Samsung Health: pulling to refresh (or tapping refresh) on Today and detail screens first wakes the Samsung Health app so data is current (`SamsungHealthWake`).
- Export: a "Preparing export…" progress dialog while CSV/JSON/Markdown is generated.
- Export: per-metric timing log (`HeartwoodExport` logcat tag).

### Changed
- README rewritten with a new intro and tone.
- Samsung Health heart-rate export reads one day at a time and reduces samples to 5-minute averages (all export formats); Markdown export reduces Health Connect heart rate the same way when there are 2000+ samples.

### Fixed
- Export no longer hangs on large heart-rate data from Samsung Health (previously a single 30-day read of per-second watch samples). Not yet confirmed on device at release time.
- Health Connect reads now page through all results instead of stopping at the first page of 1000 records.
- Refresh spinner no longer sticks if a refresh throws.

### Included commits since v1.0.0
- `d600323` feat: Samsung Health direct integration, Markdown AI share export, and unit system support
- `3b9fe94` docs: update README with AI share, unit system, dashboard customization, and fork repo

## [v1.0.0]
- Initial release.
