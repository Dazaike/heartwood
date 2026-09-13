package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class UnitSystem(val label: String) {
    IMPERIAL("Imperial (miles, lbs, in)"),
    METRIC("Metric (km, kg, cm)"),
}

private val Context.unitPreferencesDataStore by preferencesDataStore(name = "heartwood_unit_system")

class UnitPreferences(private val context: Context) {
    private val UNIT_SYSTEM_KEY = stringPreferencesKey("unit_system")

    val unitSystem: Flow<UnitSystem> = context.unitPreferencesDataStore.data.map { prefs ->
        prefs[UNIT_SYSTEM_KEY]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
            ?: UnitSystem.IMPERIAL
    }

    suspend fun setUnitSystem(value: UnitSystem) {
        context.unitPreferencesDataStore.edit { it[UNIT_SYSTEM_KEY] = value.name }
    }
}
