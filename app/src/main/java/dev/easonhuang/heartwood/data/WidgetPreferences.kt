package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.widgetPrefsDataStore by preferencesDataStore(name = "heartwood_widget_prefs")

/** User-configurable refresh cadence for the periodic widget-update worker. */
class WidgetPreferences(private val context: Context) {

    private val INTERVAL_KEY = intPreferencesKey("refresh_interval_minutes")

    val refreshIntervalMinutes: Flow<Int> = context.widgetPrefsDataStore.data.map { it[INTERVAL_KEY] ?: 30 }

    suspend fun setRefreshIntervalMinutes(minutes: Int) {
        context.widgetPrefsDataStore.edit { it[INTERVAL_KEY] = minutes }
    }

    companion object {
        // Minutes; 15 is WorkManager's periodic-work floor.
        val OPTIONS = listOf(15, 30, 60, 180)
    }
}
