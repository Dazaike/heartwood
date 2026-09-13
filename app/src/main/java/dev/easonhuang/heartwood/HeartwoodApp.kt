package dev.easonhuang.heartwood

import android.app.Application
import dev.easonhuang.heartwood.data.DashboardLayoutRepository
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.DataSource
import dev.easonhuang.heartwood.data.DataSourcePreferences
import dev.easonhuang.heartwood.data.HealthDataSource
import dev.easonhuang.heartwood.mcp.McpServerPreferences
import dev.easonhuang.heartwood.data.WidgetPreferences
import dev.easonhuang.heartwood.data.UnitPreferences
import dev.easonhuang.heartwood.samsung.SamsungHealthManager
import dev.easonhuang.heartwood.widget.WidgetUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HeartwoodApp : Application() {
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(this) }
    val samsungHealth: SamsungHealthManager by lazy { SamsungHealthManager(this) }
    val dataSourcePrefs: DataSourcePreferences by lazy { DataSourcePreferences(this) }
    val goals: GoalsRepository by lazy { GoalsRepository(this) }
    val exporter: ExportManager by lazy { ExportManager(this, ::activeDataSource, goals, unitPrefs) }
    val dashboardLayout: DashboardLayoutRepository by lazy { DashboardLayoutRepository(this) }
    val widgetPrefs: WidgetPreferences by lazy { WidgetPreferences(this) }
    val mcpPrefs: McpServerPreferences by lazy { McpServerPreferences(this) }
    val unitPrefs: UnitPreferences by lazy { UnitPreferences(this) }

    suspend fun activeDataSource(): HealthDataSource =
        if (dataSourcePrefs.source.first() == DataSource.SAMSUNG_HEALTH) samsungHealth else healthConnect

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.Default).launch {
            WidgetUpdateScheduler.schedule(this@HeartwoodApp, widgetPrefs.refreshIntervalMinutes.first().toLong())
        }
    }
}
