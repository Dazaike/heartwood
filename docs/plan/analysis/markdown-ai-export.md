# Markdown AI Export Delivery Analysis

## Scope

Add `MARKDOWN` as a third local-file export format. Existing Health Connect reads, local MCP server behavior, and CSV/JSON bytes remain unchanged.

## Module decomposition

| Module | Inputs | Output | Dependencies |
|---|---|---|---|
| `data/ExportManager` | granted metrics, range, `ExportFormat`, Health Connect series/dashboard, goals | streamed CSV, JSON, or AI-oriented Markdown | `HealthConnectManager`, `GoalsRepository`, `GoalCatalog`, formatting helpers |
| `ui/settings/ExportDialog` | granted metrics, range, selected format | effective metrics/range/format | `ExportFormat` |
| `ui/settings/SettingsScreen` | export selection | SAF `CreateDocument` launch and export invocation | `ExportDialog`, `ExportManager` |
| `HeartwoodApp` | application dependencies | fully constructed exporter | `GoalsRepository` |

## Integration enumeration

1. `HeartwoodApp` creates `ExportManager` with `HealthConnectManager` and `GoalsRepository`.
2. `SettingsScreen` receives that exporter and invokes `ExportManager.export` after the corresponding SAF launcher returns a URI.
3. Markdown selection sends all granted metrics rather than the dialog's checkbox state, and uses a Markdown MIME type and `.md` filename.
4. `ExportManager` reads the selected-range per-metric export rows, a fixed 14-day goal comparison, and the current granted dashboard snapshot; it streams those into the selected URI.
5. CSV and JSON retain their existing writer calls and output behavior.

## User-visible contract

```text
Export data dialog
├─ Metrics
│  ├─ CSV / JSON: selectable granted-metric checkboxes
│  └─ Markdown: static notice; every granted metric is included
├─ Range: existing choices
├─ Format: CSV | JSON | Markdown (for AI chat)
└─ Export → system file picker → selected output file
```

Markdown begins with the plan-specified AI-use preamble, includes generation/coverage metadata, optional goal and dashboard tables, then one heading/table or no-data message for every granted metric. It must use existing metric formatting and the existing ISO instant timestamp formatter.

## Verification

Compile the app with `./gradlew :app:assembleDebug`. The device/chatbot and byte-for-byte historical comparison checks require a connected device and pre-change output fixtures, neither of which is available in-repository; report them as manual follow-up rather than fabricating evidence.
