package dev.easonhuang.heartwood.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.heartwood.data.GoalCatalog
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthDataSource
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.SeriesPoint
import dev.easonhuang.heartwood.data.UnitPreferences
import dev.easonhuang.heartwood.data.UnitSystem
import dev.easonhuang.heartwood.data.WeeklyStat
import dev.easonhuang.heartwood.data.computeWeeklyStats
import dev.easonhuang.heartwood.data.toCanonicalValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SummaryState(
    val loading: Boolean = true,
    val stats: List<WeeklyStat> = emptyList(),
)

class SummaryViewModel(
    private val manager: HealthDataSource,
    private val goalsRepo: GoalsRepository,
    private val unitPrefs: UnitPreferences,
) : ViewModel() {

    // Raw daily series per metric (null until first load completes).
    private val series = MutableStateFlow<Map<Metric, List<SeriesPoint>>?>(null)

    val state = combine(series, goalsRepo.goals, unitPrefs.unitSystem) { seriesByMetric, goals, system ->
        if (seriesByMetric == null) {
            SummaryState(loading = true)
        } else {
            SummaryState(loading = false, stats = computeWeeklyStats(seriesByMetric, goals, system))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState())

    init {
        viewModelScope.launch {
            unitPrefs.unitSystem.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val system = unitPrefs.unitSystem.first()
            val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
            series.value = GoalCatalog.metrics
                .filter { manager.permissionFor(it) in granted }
                .associateWith { runCatching { manager.readDailySeries(it, 14, system) }.getOrDefault(emptyList()) }
        }
    }

    fun setGoal(metric: Metric, value: Float) {
        viewModelScope.launch {
            val system = unitPrefs.unitSystem.first()
            val canonical = metric.toCanonicalValue(value, system)
            goalsRepo.setGoal(metric, canonical)
        }
    }

    companion object {
        fun factory(
            manager: HealthDataSource,
            goalsRepo: GoalsRepository,
            unitPrefs: UnitPreferences,
        ) = viewModelFactory {
            initializer { SummaryViewModel(manager, goalsRepo, unitPrefs) }
        }
    }
}
