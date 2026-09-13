package dev.easonhuang.heartwood.data

/** This-week vs last-week comparison for a goal-capable metric. */
data class WeeklyStat(
    val metric: Metric,
    val perDay: List<SeriesPoint>,   // last 7 days, oldest → newest
    val thisWeekAvg: Float,
    val lastWeekAvg: Float,
    val goal: Float,
    val unit: String = metric.unit,
) {
    /** 0f..1f progress of this week's daily average toward the goal. */
    val progress: Float get() = if (goal > 0f) (thisWeekAvg / goal).coerceIn(0f, 1f) else 0f

    /** Week-over-week change as a percentage, or null when there's no prior-week baseline. */
    val deltaPercent: Float? get() =
        if (lastWeekAvg > 0f) (thisWeekAvg - lastWeekAvg) / lastWeekAvg * 100f else null
}

/** Computes this-week vs last-week averages against goals for every goal-capable metric with data. */
fun computeWeeklyStats(
    seriesByMetric: Map<Metric, List<SeriesPoint>>,
    goals: Map<Metric, Float>,
    unitSystem: UnitSystem = UnitSystem.IMPERIAL,
): List<WeeklyStat> =
    GoalCatalog.metrics.mapNotNull { metric ->
        val pts = seriesByMetric[metric]
        if (pts.isNullOrEmpty()) return@mapNotNull null
        val thisWeek = pts.takeLast(7)
        val lastWeek = pts.dropLast(7).takeLast(7)
        val canonicalGoal = goals[metric] ?: GoalCatalog.defaults.getValue(metric)
        val displayGoal = metric.convertValue(canonicalGoal, unitSystem)
        WeeklyStat(
            metric = metric,
            perDay = thisWeek,
            thisWeekAvg = thisWeek.map { it.value }.average().toFloat(),
            lastWeekAvg = if (lastWeek.isEmpty()) 0f else lastWeek.map { it.value }.average().toFloat(),
            goal = displayGoal,
            unit = metric.unit(unitSystem),
        )
    }
