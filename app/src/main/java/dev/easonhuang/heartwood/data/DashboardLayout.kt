package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dashboardLayoutDataStore by preferencesDataStore(name = "heartwood_dashboard_layout")

/**
 * User-customizable ordering and visibility of the Today dashboard's metric cards. Metrics never
 * explicitly ordered are appended at the end in their [Metric] enum-declaration order, so newly
 * added metrics show up automatically without a migration.
 */
class DashboardLayoutRepository(private val context: Context) {

    private val ORDER_KEY = stringPreferencesKey("order")
    private val HIDDEN_KEY = stringSetPreferencesKey("hidden")

    private fun orderedMetrics(orderCsv: String?): List<Metric> {
        val explicit = orderCsv?.split(",")?.mapNotNull { Metric.fromKey(it) }.orEmpty()
        val remaining = Metric.entries.filter { it !in explicit }
        return explicit + remaining
    }

    /** Visible metrics in display order. */
    val layout: Flow<List<Metric>> = context.dashboardLayoutDataStore.data.map { prefs ->
        val hidden = prefs[HIDDEN_KEY].orEmpty()
        orderedMetrics(prefs[ORDER_KEY]).filter { it.key !in hidden }
    }

    /** Every metric (visible + hidden) in display order, paired with its hidden flag, for the customize screen. */
    val editableOrder: Flow<List<Pair<Metric, Boolean>>> = context.dashboardLayoutDataStore.data.map { prefs ->
        val hidden = prefs[HIDDEN_KEY].orEmpty()
        orderedMetrics(prefs[ORDER_KEY]).map { it to (it.key in hidden) }
    }

    suspend fun setOrder(order: List<Metric>) {
        context.dashboardLayoutDataStore.edit { prefs ->
            prefs[ORDER_KEY] = order.joinToString(",") { it.key }
        }
    }

    suspend fun setHidden(metric: Metric, hidden: Boolean) {
        context.dashboardLayoutDataStore.edit { prefs ->
            val current = prefs[HIDDEN_KEY].orEmpty()
            prefs[HIDDEN_KEY] = if (hidden) current + metric.key else current - metric.key
        }
    }
}
