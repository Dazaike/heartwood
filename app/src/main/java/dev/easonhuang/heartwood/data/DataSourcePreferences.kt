package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DataSource { HEALTH_CONNECT, SAMSUNG_HEALTH }

private val Context.dataSourceDataStore by preferencesDataStore(name = "heartwood_data_source")

class DataSourcePreferences(private val context: Context) {
    private val SOURCE_KEY = stringPreferencesKey("source")

    val source: Flow<DataSource> = context.dataSourceDataStore.data.map { prefs ->
        prefs[SOURCE_KEY]?.let { runCatching { DataSource.valueOf(it) }.getOrNull() }
            ?: DataSource.HEALTH_CONNECT
    }

    suspend fun setSource(value: DataSource) {
        context.dataSourceDataStore.edit { it[SOURCE_KEY] = value.name }
    }
}
