---
id: markdown-export-001
scope: AI-shareable local health-data export
status: done
depends-on: []
---

## objective

Implement the Markdown AI-chat export described in `C:/Users/kingm/Downloads/MARKDOWN_AI_EXPORT_PLAN.md`, preserving the existing local MCP server and byte-identical CSV/JSON writers.

## context

- `docs/INDEX.md`
- `docs/plan/analysis/markdown-ai-export.md`
- `C:/Users/kingm/Downloads/MARKDOWN_AI_EXPORT_PLAN.md`

## path

- `app/src/main/java/dev/easonhuang/heartwood/data/ExportManager.kt`
- `app/src/main/java/dev/easonhuang/heartwood/ui/settings/ExportDialog.kt`
- `app/src/main/java/dev/easonhuang/heartwood/ui/settings/SettingsScreen.kt`
- `app/src/main/java/dev/easonhuang/heartwood/HeartwoodApp.kt`

## change

1. Add `MARKDOWN("Markdown (for AI chat)", "text/markdown", "md")` to `ExportFormat`.
2. Inject `GoalsRepository` into `ExportManager`; for Markdown only, stream the plan-specified preamble, generation and covered-period metadata, goal progress, current granted dashboard snapshot, and per-metric range tables/no-data messages. Use the existing ISO instant formatter and `Metric.formatValue`; use a fixed 14-day goal-comparison lookback. Keep `writeCsv` and `writeJson` unchanged.
3. In the dialog, Markdown suppresses metric checkboxes, shows the plan-specified explanation, stays exportable, and passes all granted metrics. CSV/JSON selection behavior remains unchanged.
4. Add the Markdown SAF launcher and exhaustive three-format dispatch using Markdown's MIME type and `heartwood-export.md`.
5. Construct `ExportManager` with the existing application `goals` repository.

## verification

Run `./gradlew :app:assembleDebug`. Do not run formatters, linters, or project-wide test suites. Device UI/export and chatbot-upload verification require a connected device and user data; document them as unperformed if unavailable.
