# Samsung Health direct integration analysis

## Source

`C:/Users/kingm/Downloads/samsung-health-integration-plan.md` is the product and implementation contract.

## External delivery gate

The official Samsung Health Data SDK 1.1.0 archive is now available at `C:/Users/kingm/Downloads/samsung-health-data-sdk-1.1.0.zip`; its 489.9 KB AAR has been vendored as `app/libs/samsung-health-data-api.aar`. Its linked official programming guide, API reference, and HealthDiary sample provide the required exact API source. The SDK adapter may proceed after the data-source foundation's compile gate is revalidated.

## Modules

| Module | Inputs | Outputs | Depends on |
|---|---|---|---|
| Data-source foundation | Existing metrics/models and Android Context | `HealthDataSource`; persisted `DataSource` selection; unchanged Health Connect behavior | none |
| Samsung SDK adapter | Vendored Samsung AAR and exact aggregate API | `SamsungHealthManager`, permissions, Samsung-to-Heartwood mappings | data-source foundation, AAR |
| Application integration | Both concrete managers and preferences | selection UI, source-specific onboarding, widgets/MCP active source routing | adapter |

## Integration chains

1. `MainActivity` creates app dependencies → `HeartwoodRoot` selects the source → source-specific onboarding invokes the matching permission flow → `MainNav` and all screens consume `HealthDataSource`.
2. `SettingsScreen` persists a `DataSource` → `HeartwoodRoot` observes it and remounts the active flow.
3. Widget workers and `McpServerService` call `HeartwoodApp.activeDataSource()` → the selected manager supplies dashboard/export reads.
4. `SamsungHealthManager` maps the 14 supported metrics to Samsung data types; the four unsupported metrics return the fixed locked caption without querying the SDK.
5. `ExportManager` resolves the active source when an export begins, rather than retaining the startup `HealthConnectManager`; toggling data source must change exports as well as UI, widgets, and MCP.

## Delivery order

1. `health-source-001`: compile-safe foundation that preserves Health Connect.
2. `samsung-sdk-002`: SDK adapter after the AAR is provided and exact SDK API is inspected.
3. `samsung-ui-003`: application wiring, onboarding, settings, widget/MCP routing, locked-card text, and README after the adapter exists.

The final physical-device authorization and data-read validation must use Samsung Health 6.30.2+ on a physical device in Developer Mode until Partner Apps approval is granted.
