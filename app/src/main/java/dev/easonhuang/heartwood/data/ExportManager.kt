package dev.easonhuang.heartwood.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportFormat(val label: String, val mime: String, val extension: String) {
    CSV("CSV", "text/csv", "csv"),
    JSON("JSON", "application/json", "json"),
    MARKDOWN("Markdown (for AI chat)", "text/markdown", "md"),
}

/**
 * Streams the data the app reads out to a user-chosen file via the Storage Access Framework.
 * Read-only and on-device, the file lands wherever the system picker points (Drive, local, etc.).
 */
class ExportManager(
    private val context: Context,
    private val dataSourceProvider: suspend () -> HealthDataSource,
    private val goalsRepo: GoalsRepository,
    private val unitPrefs: UnitPreferences,
) {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    suspend fun export(
        uri: Uri,
        metrics: List<Metric>,
        format: ExportFormat,
        days: Int = 365,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = dataSourceProvider()
            val system = unitPrefs.unitSystem.first()
            val sections = metrics.map { metric ->
                val t0 = System.currentTimeMillis()
                val rows = runCatching { manager.exportRows(metric, days, system) }.getOrDefault(emptyList())
                android.util.Log.d("HeartwoodExport", "${metric.key}: ${rows.size} rows in ${System.currentTimeMillis() - t0}ms")
                metric to if (format == ExportFormat.MARKDOWN) downsample(metric, rows) else rows
            }
            val total = sections.sumOf { it.second.size }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                when (format) {
                    ExportFormat.CSV -> writeCsv(writer, sections, system)
                    ExportFormat.JSON -> writeJson(writer, sections, system)
                    ExportFormat.MARKDOWN -> {
                        val goalMetrics = GoalCatalog.metrics.filter { it in metrics }
                        val goalStats = computeWeeklyStats(
                            goalMetrics.associateWith { manager.readDailySeries(it, 14, system) },
                            goalsRepo.goals.first(),
                            system,
                        )
                        val dashboard = runCatching { manager.readDashboard(system) }.getOrDefault(emptyList())
                            .filter { it.metric in metrics && it.granted }
                        writeMarkdown(writer, metrics, sections, dashboard, goalStats, days, system)
                    }
                }
            } ?: error("Could not open output file")
            total
        }
    }

    suspend fun exportMarkdownToCache(
        metrics: List<Metric>,
        days: Int = 365,
    ): Result<Pair<Uri, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = dataSourceProvider()
            val system = unitPrefs.unitSystem.first()
            val sections = metrics.map { metric ->
                val t0 = System.currentTimeMillis()
                val rows = runCatching { manager.exportRows(metric, days, system) }.getOrDefault(emptyList())
                android.util.Log.d("HeartwoodExport", "${metric.key}: ${rows.size} rows in ${System.currentTimeMillis() - t0}ms")
                metric to downsample(metric, rows)
            }
            val total = sections.sumOf { it.second.size }
            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(cacheDir, "heartwood-export.md")
            file.bufferedWriter().use { writer ->
                val goalMetrics = GoalCatalog.metrics.filter { it in metrics }
                val goalStats = computeWeeklyStats(
                    goalMetrics.associateWith { manager.readDailySeries(it, 14, system) },
                    goalsRepo.goals.first(),
                    system,
                )
                val dashboard = runCatching { manager.readDashboard(system) }.getOrDefault(emptyList())
                    .filter { it.metric in metrics && it.granted }
                writeMarkdown(writer, metrics, sections, dashboard, goalStats, days, system)
            }
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            contentUri to total
        }
    }

    /** Raw heart-rate samples can number in the hundreds of thousands; AI-chat Markdown gets 5-minute averages. */
    private fun downsample(metric: Metric, points: List<SeriesPoint>): List<SeriesPoint> {
        if (metric != Metric.HEART_RATE || points.size < 2000) return points
        return points.groupBy { it.time.epochSecond / 300 }
            .map { (bucket, list) ->
                SeriesPoint(Instant.ofEpochSecond(bucket * 300), list.map { it.value }.average().toFloat(), list.first().label)
            }
            .sortedBy { it.time }
    }

    private fun writeCsv(writer: Appendable, sections: List<Pair<Metric, List<SeriesPoint>>>, system: UnitSystem = UnitSystem.IMPERIAL) {
        writer.append("metric,title,unit,timestamp,value\n")
        sections.forEach { (metric, points) ->
            val unitLabel = metric.unit(system)
            points.forEach { p ->
                writer.append(metric.key).append(',')
                    .append(csv(metric.title)).append(',')
                    .append(unitLabel).append(',')
                    .append(iso.format(p.time)).append(',')
                    .append(p.value.toString()).append('\n')
            }
        }
    }

    private fun writeJson(writer: Appendable, sections: List<Pair<Metric, List<SeriesPoint>>>, system: UnitSystem = UnitSystem.IMPERIAL) {
        writer.append("{\"app\":\"Heartwood\",\"metrics\":[")
        sections.forEachIndexed { si, (metric, points) ->
            if (si > 0) writer.append(',')
            writer.append("{\"key\":\"").append(metric.key).append("\",")
                .append("\"title\":").append(jsonStr(metric.title)).append(',')
                .append("\"unit\":\"").append(metric.unit(system)).append("\",")
                .append("\"points\":[")
            points.forEachIndexed { pi, p ->
                if (pi > 0) writer.append(',')
                writer.append("{\"t\":\"").append(iso.format(p.time)).append("\",\"v\":")
                    .append(p.value.toString()).append('}')
            }
            writer.append("]}")
        }
        writer.append("]}")
    }

    private fun writeMarkdown(
        writer: Appendable,
        metrics: List<Metric>,
        sections: List<Pair<Metric, List<SeriesPoint>>>,
        dashboard: List<MetricSummary>,
        goalStats: List<WeeklyStat>,
        days: Int,
        system: UnitSystem = UnitSystem.IMPERIAL,
    ) {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val startDate = LocalDate.now().minusDays((days - 1).toLong())
        val endDate = LocalDate.now()
        val headerFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

        writer.append("# Heartwood Health Data Export\n\n")
        writer.append(
            "> **For AI assistants:** This file is a personal health data export generated by the " +
                "Heartwood app (an Android Health Connect viewer). It reflects the exporting user's own " +
                "recorded health and fitness data for the period below — treat every value as accurate, " +
                "recorded ground truth, not an estimate. Use it to answer the user's questions about " +
                "trends, habits, and progress toward goals. Do not offer medical diagnoses; for medical " +
                "concerns, suggest the user consult a healthcare professional. Each metric's unit is " +
                "given in its section heading.\n\n",
        )
        writer.append("**Generated:** ").append(headerFmt.format(now)).append('\n')
        writer.append("**Period covered:** ").append(headerFmt.format(startDate)).append(" - ")
            .append(headerFmt.format(endDate)).append(" (").append(days.toString()).append(" days)\n\n")
        if (goalStats.isNotEmpty()) {
            writer.append("## Goals progress (this week vs. last week)\n\n")
            writer.append("| Metric | This week avg | Last week avg | Goal | Progress | Change |\n")
            writer.append("|---|---|---|---|---|---|\n")
            goalStats.forEach { stat ->
                val delta = stat.deltaPercent?.let { "%+.1f%%".format(it) } ?: "n/a"
                writer.append("| ").append(mdEscape(stat.metric.title))
                    .append(" | ").append(stat.metric.formatValue(stat.thisWeekAvg)).append(' ').append(stat.unit)
                    .append(" | ").append(stat.metric.formatValue(stat.lastWeekAvg)).append(' ').append(stat.unit)
                    .append(" | ").append(stat.metric.formatValue(stat.goal)).append(' ').append(stat.unit)
                    .append(" | ").append((stat.progress * 100).toInt().toString()).append('%')
                    .append(" | ").append(delta).append(" |\n")
            }
            writer.append('\n')
        }

        if (dashboard.isNotEmpty()) {
            writer.append("## Current snapshot\n\n")
            writer.append("| Metric | Value | Detail |\n|---|---|---|\n")
            dashboard.forEach { summary ->
                val value = if (summary.hasData) "${summary.value} ${summary.unit}" else "No data"
                writer.append("| ").append(mdEscape(summary.metric.title)).append(" | ").append(mdEscape(value))
                    .append(" | ").append(mdEscape(summary.caption ?: "")).append(" |\n")
            }
            writer.append('\n')
        }

        sections.forEach { (metric, points) ->
            writer.append("## ").append(metric.title).append(" (").append(metric.unit(system)).append(")\n\n")
            if (points.isEmpty()) {
                writer.append("No data recorded in this period.\n\n")
                return@forEach
            }
            writer.append("| Time | Value |\n|---|---|\n")
            points.forEach { point ->
                writer.append("| ").append(iso.format(point.time)).append(" | ")
                    .append(metric.formatValue(point.value)).append(" |\n")
            }
            writer.append('\n')
        }
    }

    private fun csv(s: String): String =
        if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun jsonStr(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun mdEscape(s: String): String = s.replace("|", "\\|")
}
