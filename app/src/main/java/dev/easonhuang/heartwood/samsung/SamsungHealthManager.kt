package dev.easonhuang.heartwood.samsung

import android.app.Activity
import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.data.Field
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalDateGroup
import com.samsung.android.sdk.health.data.request.LocalDateGroupUnit
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.request.ReadDataRequest
import dev.easonhuang.heartwood.data.HealthDataSource
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricDetail
import dev.easonhuang.heartwood.data.MetricKind
import dev.easonhuang.heartwood.data.MetricSummary
import dev.easonhuang.heartwood.data.RecordRow
import dev.easonhuang.heartwood.data.SeriesPoint
import dev.easonhuang.heartwood.data.UnitSystem
import dev.easonhuang.heartwood.data.convertValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Read-only adapter for Samsung Health Data SDK. */
class SamsungHealthManager(private val context: Context) : HealthDataSource {
    private val store by lazy { HealthDataService.getStore(context.applicationContext) }
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dowFmt = DateTimeFormatter.ofPattern("EEE")
    private val dayFmt = DateTimeFormatter.ofPattern("MMM d")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    override val isAvailable: Boolean = true

    private val readPermissions = setOf(
        Permission.of(DataTypes.STEPS, AccessType.READ),
        Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ),
        Permission.of(DataTypes.FLOORS_CLIMBED, AccessType.READ),
        Permission.of(DataTypes.EXERCISE, AccessType.READ),
        Permission.of(DataTypes.WATER_INTAKE, AccessType.READ),
        Permission.of(DataTypes.HEART_RATE, AccessType.READ),
        Permission.of(DataTypes.SLEEP, AccessType.READ),
        Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
        Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ),
        Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.READ),
        Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
    )

    suspend fun requestPermissions(activity: Activity): Set<String> =
        grantedIdsOf(store.requestPermissions(readPermissions, activity))

    override suspend fun grantedPermissions(): Set<String> =
        grantedIdsOf(store.getGrantedPermissions(readPermissions))

    override fun permissionFor(metric: Metric): String = when (metric) {
        Metric.STEPS -> "samsung:steps"
        Metric.DISTANCE, Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES -> "samsung:activity_summary"
        Metric.FLOORS -> "samsung:floors_climbed"
        Metric.EXERCISE -> "samsung:exercise"
        Metric.HYDRATION -> "samsung:water_intake"
        Metric.HEART_RATE -> "samsung:heart_rate"
        Metric.SLEEP -> "samsung:sleep"
        Metric.OXYGEN -> "samsung:blood_oxygen"
        Metric.WEIGHT, Metric.HEIGHT -> "samsung:body_composition"
        Metric.BLOOD_PRESSURE -> "samsung:blood_pressure"
        Metric.BLOOD_GLUCOSE -> "samsung:blood_glucose"
        Metric.RESTING_HR, Metric.HRV, Metric.RESPIRATORY, Metric.VO2MAX -> UNSUPPORTED_PERMISSION
    }

    override suspend fun isGranted(metric: Metric): Boolean =
        !metric.isUnsupported() && permissionFor(metric) in runCatching { grantedPermissions() }.getOrDefault(emptySet())

    override suspend fun readDashboard(unitSystem: UnitSystem): List<MetricSummary> {
        val granted = runCatching { grantedPermissions() }.getOrDefault(emptySet())
        return Metric.entries.map { metric ->
            when {
                metric.isUnsupported() -> unsupportedSummary(metric, unitSystem)
                permissionFor(metric) !in granted -> MetricSummary(metric, "-", null, hasData = false, granted = false, unit = metric.unit(unitSystem))
                else -> runCatching { summarize(metric, unitSystem) }
                    .getOrElse { MetricSummary(metric, "-", "No data", hasData = false, granted = true, unit = metric.unit(unitSystem)) }
            }
        }
    }

    override suspend fun readDetail(metric: Metric, unitSystem: UnitSystem): MetricDetail {
        if (metric.isUnsupported()) return unsupportedDetail(metric, unitSystem)
        if (metric == Metric.HEART_RATE) return heartRateDetail()
        if (metric == Metric.BLOOD_PRESSURE) return bloodPressureDetail()

        val days = if (metric.kind == MetricKind.DAILY_TOTAL) 14 else 90
        val points = series(metric, days, unitSystem)
        val unitLabel = metric.unit(unitSystem)
        if (points.isEmpty()) return MetricDetail(metric, "-", "No data recorded", emptyList(), unit = unitLabel)
        val values = points.map { it.value }
        return if (metric.kind == MetricKind.DAILY_TOTAL) {
            MetricDetail(
                metric = metric,
                headline = formatValue(metric, points.last().value),
                caption = "Today, $unitLabel",
                points = points,
                stats = listOf(
                    "Daily avg" to "${formatValue(metric, values.average().toFloat())} $unitLabel",
                    "Best day" to "${formatValue(metric, values.max())} $unitLabel",
                    "14-day total" to "${formatValue(metric, values.sum())} $unitLabel",
                ),
                recent = recent(metric, points, unitSystem),
                unit = unitLabel,
            )
        } else {
            MetricDetail(
                metric = metric,
                headline = "${formatValue(metric, points.last().value)} $unitLabel",
                caption = "Latest, ${dayFmt.format(points.last().time.atZone(zone))}",
                points = points,
                stats = listOf(
                    "Average" to "${formatValue(metric, values.average().toFloat())} $unitLabel",
                    "Min" to "${formatValue(metric, values.min())} $unitLabel",
                    "Max" to "${formatValue(metric, values.max())} $unitLabel",
                ),
                recent = recent(metric, points, unitSystem),
                unit = unitLabel,
            )
        }
    }

    override suspend fun readDailySeries(metric: Metric, days: Int, unitSystem: UnitSystem): List<SeriesPoint> =
        if (metric.kind == MetricKind.DAILY_TOTAL && !metric.isUnsupported()) series(metric, days, unitSystem) else emptyList()

    override suspend fun exportRows(metric: Metric, days: Int, unitSystem: UnitSystem): List<SeriesPoint> = when {
        metric.isUnsupported() -> emptyList()
        metric == Metric.HEART_RATE -> heartRateExportRows(minOf(days, 30))
        else -> series(metric, days, unitSystem)
    }

    private suspend fun summarize(metric: Metric, unitSystem: UnitSystem): MetricSummary {
        if (metric == Metric.HEART_RATE) return heartRateSummary()
        if (metric == Metric.BLOOD_PRESSURE) return bloodPressureSummary()
        if (metric == Metric.SLEEP) return sleepSummary()
        val points = series(metric, days = 7, unitSystem)
        val unitLabel = metric.unit(unitSystem)
        if (points.isEmpty()) return MetricSummary(metric, "-", "No data", hasData = false, granted = true, unit = unitLabel)
        val spark = points.map { it.value }
        return when (metric.kind) {
            MetricKind.DAILY_TOTAL -> MetricSummary(
                metric = metric,
                value = formatValue(metric, points.last().value),
                caption = "7-day avg ${formatValue(metric, spark.average().toFloat())} $unitLabel",
                hasData = true,
                granted = true,
                spark = spark,
                unit = unitLabel,
            )
            MetricKind.LATEST -> MetricSummary(
                metric = metric,
                value = formatValue(metric, points.last().value),
                caption = "as of ${dayFmt.format(points.last().time.atZone(zone))}",
                hasData = true,
                granted = true,
                spark = spark,
                unit = unitLabel,
            )
        }
    }
    private suspend fun sleepSummary(): MetricSummary {
        val points = sleepAggregateSeries(days = 7)
        if (points.isEmpty()) return MetricSummary(Metric.SLEEP, "-", "No data", false, granted = true)
        return MetricSummary(
            metric = Metric.SLEEP,
            value = formatValue(Metric.SLEEP, points.last().value),
            caption = "as of ${dayFmt.format(points.last().time.atZone(zone))}",
            hasData = true,
            granted = true,
            spark = points.map { it.value },
        )
    }


    private suspend fun heartRateSummary(): MetricSummary {
        val rows = heartRateRows(7)
        if (rows.isEmpty()) return MetricSummary(Metric.HEART_RATE, "-", "No data", false, granted = true)
        val latest = rows.last()
        val today = LocalDate.now()
        val todays = rows.filter { it.timestamp().atZone(zone).toLocalDate() == today }
            .map { it.float(DataType.HeartRateType.HEART_RATE) }
        val caption = if (todays.isNotEmpty()) {
            "avg ${todays.average().toInt()}, ${todays.min().toInt()}-${todays.max().toInt()} bpm today"
        } else {
            "latest ${dayMonthTime(latest.timestamp())}"
        }
        return MetricSummary(
            metric = Metric.HEART_RATE,
            value = latest.float(DataType.HeartRateType.HEART_RATE).toInt().toString(),
            caption = caption,
            hasData = true,
            granted = true,
            spark = rows.map { it.float(DataType.HeartRateType.HEART_RATE) }.takeLast(60),
        )
    }

    private suspend fun bloodPressureSummary(): MetricSummary {
        val rows = bloodPressureRows(30)
        val latest = rows.lastOrNull()
            ?: return MetricSummary(Metric.BLOOD_PRESSURE, "-", "No data", false, granted = true)
        return MetricSummary(
            metric = Metric.BLOOD_PRESSURE,
            value = bloodPressureValue(latest),
            caption = "as of ${dayFmt.format(latest.timestamp().atZone(zone))}",
            hasData = true,
            granted = true,
            spark = rows.map { it.float(DataType.BloodPressureType.SYSTOLIC) },
        )
    }

    private suspend fun heartRateDetail(): MetricDetail {
        val points = heartRateRows(7).map { point ->
            SeriesPoint(point.timestamp(), point.float(DataType.HeartRateType.HEART_RATE), timeFmt.format(point.timestamp().atZone(zone)))
        }
        if (points.isEmpty()) return MetricDetail(Metric.HEART_RATE, "-", "No data", emptyList())
        val bpm = points.map { it.value }
        return MetricDetail(
            metric = Metric.HEART_RATE,
            headline = "${points.last().value.toInt()} bpm",
            caption = "Latest reading",
            points = points,
            stats = listOf(
                "Average" to "${bpm.average().toInt()} bpm",
                "Min" to "${bpm.min().toInt()} bpm",
                "Max" to "${bpm.max().toInt()} bpm",
            ),
            recent = points.takeLast(30).reversed().map { RecordRow("${it.value.toInt()} bpm", dayMonthTime(it.time)) },
        )
    }

    private suspend fun bloodPressureDetail(): MetricDetail {
        val rows = bloodPressureRows(90)
        if (rows.isEmpty()) return MetricDetail(Metric.BLOOD_PRESSURE, "-", "No data", emptyList())
        val points = rows.map { point ->
            SeriesPoint(point.timestamp(), point.float(DataType.BloodPressureType.SYSTOLIC), dayFmt.format(point.timestamp().atZone(zone)))
        }
        val latest = rows.last()
        return MetricDetail(
            metric = Metric.BLOOD_PRESSURE,
            headline = "${bloodPressureValue(latest)} mmHg",
            caption = "Latest, ${dayFmt.format(latest.timestamp().atZone(zone))}",
            points = points,
            stats = listOf(
                "Avg systolic" to "${rows.map { it.float(DataType.BloodPressureType.SYSTOLIC) }.average().toInt()} mmHg",
                "Avg diastolic" to "${rows.map { it.float(DataType.BloodPressureType.DIASTOLIC) }.average().toInt()} mmHg",
                "Readings" to rows.size.toString(),
            ),
            recent = rows.takeLast(20).reversed().map { RecordRow("${bloodPressureValue(it)} mmHg", dayMonthTime(it.timestamp())) },
        )
    }

    private suspend fun series(metric: Metric, days: Int, unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<SeriesPoint> {
        val raw = when (metric) {
            Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES,
            Metric.FLOORS, Metric.EXERCISE, Metric.HYDRATION -> dailyAggregateSeries(metric, days)
            Metric.SLEEP -> sleepRows(days).mapNotNull { point ->
                point.getValue(DataType.SleepType.DURATION)?.let { duration ->
                    SeriesPoint(point.timestamp(), duration.toMinutes() / 60f, dayFmt.format(point.timestamp().atZone(zone)))
                }
            }.sortedBy { it.time }
            Metric.OXYGEN -> latestRows(days, DataType.BloodOxygenType.OXYGEN_SATURATION, DataTypes.BLOOD_OXYGEN.readDataRequestBuilder)
            Metric.WEIGHT -> latestRows(days, DataType.BodyCompositionType.WEIGHT, DataTypes.BODY_COMPOSITION.readDataRequestBuilder)
            Metric.HEIGHT -> latestRows(days, DataType.BodyCompositionType.HEIGHT, DataTypes.BODY_COMPOSITION.readDataRequestBuilder)
            Metric.BLOOD_PRESSURE -> bloodPressureRows(days).map { point ->
                SeriesPoint(point.timestamp(), point.float(DataType.BloodPressureType.SYSTOLIC), dayFmt.format(point.timestamp().atZone(zone)))
            }
            Metric.BLOOD_GLUCOSE -> latestRows(
                days,
                DataType.BloodGlucoseType.GLUCOSE_LEVEL,
                DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder,
            ) { it * MMOL_L_TO_MG_DL }
            Metric.HEART_RATE -> heartRateRows(days).map { point ->
                SeriesPoint(point.timestamp(), point.float(DataType.HeartRateType.HEART_RATE), timeFmt.format(point.timestamp().atZone(zone)))
            }
            Metric.RESTING_HR, Metric.HRV, Metric.RESPIRATORY, Metric.VO2MAX -> emptyList()
        }
        return raw.map { it.copy(value = metric.convertValue(it.value, unitSystem)) }
    }

    private suspend fun dailyAggregateSeries(metric: Metric, days: Int): List<SeriesPoint> = when (metric) {
        Metric.STEPS -> dailyLocalTimeSeries(DataType.StepsType.TOTAL, days) { it.toFloat() }
        Metric.DISTANCE -> dailyLocalTimeSeries(DataType.ActivitySummaryType.TOTAL_DISTANCE, days) {
            it / METERS_PER_KILOMETER
        }
        Metric.ACTIVE_CALORIES -> dailyLocalTimeSeries(DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED, days) { it }
        Metric.TOTAL_CALORIES -> dailyLocalTimeSeries(DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED, days) { it }
        Metric.FLOORS -> dailyLocalTimeSeries(DataType.FloorsClimbedType.TOTAL, days) { it }
        Metric.EXERCISE -> dailyLocalDateSeries(DataType.ExerciseType.TOTAL_DURATION, days) {
            it.toMinutes().toFloat()
        }
        Metric.HYDRATION -> dailyDualTimeSeries(DataType.WaterIntakeType.TOTAL, days) {
            it / MILLILITERS_PER_LITER
        }
        else -> emptyList()
    }

    private suspend fun sleepAggregateSeries(days: Int): List<SeriesPoint> =
        dailyLocalDateSeries(DataType.SleepType.TOTAL_DURATION, days) { it.toMinutes() / 60f }
            .takeIf { points -> points.any { it.value != 0f } }
            ?: emptyList()

    private suspend fun <T : Any> dailyLocalTimeSeries(
        operation: AggregateOperation<T, AggregateRequest.LocalTimeBuilder<T>>,
        days: Int,
        transform: (T) -> Float,
    ): List<SeriesPoint> {
        val dates = dailyDates(days)
        if (dates.isEmpty()) return emptyList()
        return dailyGroupedSeries(
            dates,
            operation.requestBuilder.setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(dates.first().atStartOfDay(), dates.last().plusDays(1).atStartOfDay()),
                LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1),
            ).build(),
            transform,
        )
    }

    private suspend fun <T : Any> dailyDualTimeSeries(
        operation: AggregateOperation<T, AggregateRequest.DualTimeBuilder<T>>,
        days: Int,
        transform: (T) -> Float,
    ): List<SeriesPoint> {
        val dates = dailyDates(days)
        if (dates.isEmpty()) return emptyList()
        return dailyGroupedSeries(
            dates,
            operation.requestBuilder.setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(dates.first().atStartOfDay(), dates.last().plusDays(1).atStartOfDay()),
                LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1),
            ).build(),
            transform,
        )
    }

    private suspend fun <T : Any> dailyLocalDateSeries(
        operation: AggregateOperation<T, AggregateRequest.LocalDateBuilder<T>>,
        days: Int,
        transform: (T) -> Float,
    ): List<SeriesPoint> {
        val dates = dailyDates(days)
        if (dates.isEmpty()) return emptyList()
        return dailyGroupedSeries(
            dates,
            operation.requestBuilder.setLocalDateFilterWithGroup(
                LocalDateFilter.of(dates.first(), dates.last().plusDays(1)),
                LocalDateGroup.of(LocalDateGroupUnit.DAILY, 1),
            ).build(),
            transform,
        )
    }

    private suspend fun <T : Any> dailyGroupedSeries(
        dates: List<LocalDate>,
        request: AggregateRequest<T>,
        transform: (T) -> Float,
    ): List<SeriesPoint> {
        val valuesByDate = store.aggregateData(request).dataList.mapNotNull { data ->
            data.value?.let { value -> data.startTime.atZone(zone).toLocalDate() to transform(value) }
        }.toMap()
        return dates.map { date ->
            SeriesPoint(date.atStartOfDay(zone).toInstant(), valuesByDate[date] ?: 0f, dowFmt.format(date))
        }
    }

    private fun dailyDates(days: Int): List<LocalDate> {
        if (days <= 0) return emptyList()
        val today = LocalDate.now()
        return (0 until days).map { index -> today.minusDays((days - 1 - index).toLong()) }
    }

    private suspend fun heartRateRows(days: Int): List<HealthDataPoint> =
        rows(DataTypes.HEART_RATE.readDataRequestBuilder, days).sortedBy { it.timestamp() }
    private suspend fun sleepRows(days: Int): List<HealthDataPoint> =
        rows(DataTypes.SLEEP.readDataRequestBuilder, days).sortedBy { it.timestamp() }


    private suspend fun bloodPressureRows(days: Int): List<HealthDataPoint> =
        rows(DataTypes.BLOOD_PRESSURE.readDataRequestBuilder, days).sortedBy { it.timestamp() }

    private suspend fun latestRows(
        days: Int,
        field: Field<Float>,
        builder: ReadDataRequest.DualTimeBuilder<HealthDataPoint>,
        transform: (Float) -> Float = { it },
    ): List<SeriesPoint> = rows(builder, days).mapNotNull { point ->
        point.getValue(field)?.let { value ->
            SeriesPoint(point.timestamp(), transform(value), dayFmt.format(point.timestamp().atZone(zone)))
        }
    }.sortedBy { it.time }

    private suspend fun rows(
        builder: ReadDataRequest.DualTimeBuilder<HealthDataPoint>,
        days: Int,
    ): List<HealthDataPoint> {
        val start = LocalDate.now().minusDays(days.toLong()).atStartOfDay()
        return rowsBetween(builder, start, LocalDateTime.now())
    }

    private suspend fun rowsBetween(
        builder: ReadDataRequest.DualTimeBuilder<HealthDataPoint>,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<HealthDataPoint> = store.readData(
        builder.setLocalTimeFilter(LocalTimeFilter.of(start, end))
            .setOrdering(Ordering.DESC)
            .build(),
    ).dataList

    /**
     * Export-only heart rate: one read per day (a single multi-week read of per-second watch samples
     * is what made export hang), each day reduced to 5-minute averages as it arrives.
     */
    private suspend fun heartRateExportRows(days: Int): List<SeriesPoint> {
        val today = LocalDate.now()
        val out = ArrayList<SeriesPoint>()
        for (offset in days downTo 0) {
            val day = today.minusDays(offset.toLong())
            val end = if (offset == 0) LocalDateTime.now() else day.plusDays(1).atStartOfDay()
            val points = rowsBetween(DataTypes.HEART_RATE.readDataRequestBuilder, day.atStartOfDay(), end)
            points.groupBy { it.timestamp().epochSecond / HR_BUCKET_SECONDS }
                .toSortedMap()
                .forEach { (bucket, list) ->
                    val instant = Instant.ofEpochSecond(bucket * HR_BUCKET_SECONDS)
                    out += SeriesPoint(
                        instant,
                        list.map { it.float(DataType.HeartRateType.HEART_RATE) }.average().toFloat(),
                        timeFmt.format(instant.atZone(zone)),
                    )
                }
        }
        return out
    }

    private fun HealthDataPoint.timestamp(): Instant = requireNotNull(endTime ?: startTime ?: updateTime)

    private fun HealthDataPoint.float(field: Field<Float>): Float = requireNotNull(getValue(field))

    private fun bloodPressureValue(point: HealthDataPoint): String =
        "${point.float(DataType.BloodPressureType.SYSTOLIC).toInt()}/${point.float(DataType.BloodPressureType.DIASTOLIC).toInt()}"

    private fun recent(metric: Metric, points: List<SeriesPoint>, unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<RecordRow> {
        val unitLabel = metric.unit(unitSystem)
        return points.takeLast(20).reversed().map { RecordRow("${formatValue(metric, it.value)} $unitLabel", dayMonthTime(it.time)) }
    }
    private fun grantedIdsOf(granted: Set<Permission>): Set<String> = granted.mapNotNull { permission ->
        when (permission.dataType) {
            DataTypes.STEPS -> "samsung:steps"
            DataTypes.ACTIVITY_SUMMARY -> "samsung:activity_summary"
            DataTypes.FLOORS_CLIMBED -> "samsung:floors_climbed"
            DataTypes.EXERCISE -> "samsung:exercise"
            DataTypes.WATER_INTAKE -> "samsung:water_intake"
            DataTypes.HEART_RATE -> "samsung:heart_rate"
            DataTypes.SLEEP -> "samsung:sleep"
            DataTypes.BLOOD_OXYGEN -> "samsung:blood_oxygen"
            DataTypes.BODY_COMPOSITION -> "samsung:body_composition"
            DataTypes.BLOOD_PRESSURE -> "samsung:blood_pressure"
            DataTypes.BLOOD_GLUCOSE -> "samsung:blood_glucose"
            else -> null
        }
    }.toSet()

    private fun Metric.isUnsupported(): Boolean = this in UNSUPPORTED_METRICS

    private fun unsupportedSummary(metric: Metric, unitSystem: UnitSystem = UnitSystem.IMPERIAL) =
        MetricSummary(metric, "-", UNSUPPORTED_CAPTION, hasData = false, granted = false, unit = metric.unit(unitSystem))

    private fun unsupportedDetail(metric: Metric, unitSystem: UnitSystem = UnitSystem.IMPERIAL) =
        MetricDetail(metric, "-", UNSUPPORTED_CAPTION, emptyList(), unit = metric.unit(unitSystem))
    private fun formatValue(metric: Metric, value: Float): String = when (metric) {
        Metric.STEPS, Metric.FLOORS -> "%,d".format(value.toLong())
        Metric.DISTANCE, Metric.HYDRATION -> "%.2f".format(value)
        Metric.VO2MAX, Metric.WEIGHT, Metric.SLEEP -> "%.1f".format(value)
        else -> "%.0f".format(value)
    }

    private fun dayMonthTime(time: Instant): String {
        val zoned = time.atZone(zone)
        return "${dayFmt.format(zoned)}, ${timeFmt.format(zoned)}"
    }

    private companion object {
        const val HR_BUCKET_SECONDS = 300L
        const val UNSUPPORTED_PERMISSION = "samsung:unsupported"
        const val UNSUPPORTED_CAPTION = "Not available from Samsung Health"
        const val METERS_PER_KILOMETER = 1000f
        const val MILLILITERS_PER_LITER = 1000f
        const val MMOL_L_TO_MG_DL = 18.0182f
        val UNSUPPORTED_METRICS = setOf(Metric.RESTING_HR, Metric.HRV, Metric.RESPIRATORY, Metric.VO2MAX)
    }
}
