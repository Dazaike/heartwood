package dev.easonhuang.heartwood.data

/** Common read-only surface both Health Connect and Samsung Health managers implement. */
interface HealthDataSource {
    val isAvailable: Boolean
    fun permissionFor(metric: Metric): String
    suspend fun grantedPermissions(): Set<String>
    suspend fun isGranted(metric: Metric): Boolean
    suspend fun readDashboard(unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<MetricSummary>
    suspend fun readDetail(metric: Metric, unitSystem: UnitSystem = UnitSystem.IMPERIAL): MetricDetail
    suspend fun readDailySeries(metric: Metric, days: Int, unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<SeriesPoint>
    suspend fun exportRows(metric: Metric, days: Int, unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<SeriesPoint>
}
