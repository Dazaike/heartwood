package dev.easonhuang.heartwood.mcp

import dev.easonhuang.heartwood.data.GoalCatalog
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthDataSource
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricKind
import dev.easonhuang.heartwood.data.WeeklyStat
import dev.easonhuang.heartwood.data.computeWeeklyStats
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only MCP tool implementations. Each function builds a plain [JSONObject]/[JSONArray]
 * payload from the existing [HealthDataSource]/[GoalsRepository] APIs; no new data-layer
 * reads are introduced here.
 */
object McpTools {

    /** Thrown when a tool call references an unknown metric key; mapped to JSON-RPC -32602. */
    class UnknownMetricException(key: String) : Exception("Unknown metric key: $key")

    suspend fun listMetrics(manager: HealthDataSource): JSONArray {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        val out = JSONArray()
        Metric.entries.forEach { metric ->
            out.put(
                JSONObject()
                    .put("key", metric.key)
                    .put("title", metric.title)
                    .put("unit", metric.unit)
                    .put("kind", if (metric.kind == MetricKind.DAILY_TOTAL) "daily_total" else "latest")
                    .put("granted", manager.permissionFor(metric) in granted)
            )
        }
        return out
    }

    suspend fun getDashboard(manager: HealthDataSource): JSONArray {
        val out = JSONArray()
        manager.readDashboard().forEach { summary ->
            out.put(
                JSONObject()
                    .put("metric", summary.metric.key)
                    .put("title", summary.metric.title)
                    .put("unit", summary.metric.unit)
                    .put("value", summary.value)
                    .put("caption", summary.caption)
                    .put("hasData", summary.hasData)
                    .put("granted", summary.granted)
                    .put("spark", JSONArray(summary.spark))
            )
        }
        return out
    }

    suspend fun getMetricDetail(manager: HealthDataSource, args: JSONObject): JSONObject {
        val key = args.optString("metric")
        val metric = Metric.fromKey(key) ?: throw UnknownMetricException(key)
        val detail = manager.readDetail(metric)
        val points = JSONArray()
        detail.points.forEach { p ->
            points.put(JSONObject().put("time", ISO.format(p.time)).put("value", p.value).put("label", p.label))
        }
        val stats = JSONArray()
        detail.stats.forEach { (label, value) -> stats.put(JSONObject().put("label", label).put("value", value)) }
        val recent = JSONArray()
        detail.recent.forEach { row -> recent.put(JSONObject().put("primary", row.primary).put("secondary", row.secondary)) }
        return JSONObject()
            .put("metric", metric.key)
            .put("headline", detail.headline)
            .put("caption", detail.caption)
            .put("points", points)
            .put("stats", stats)
            .put("recent", recent)
    }

    suspend fun getMetricSeries(manager: HealthDataSource, args: JSONObject): JSONArray {
        val key = args.optString("metric")
        val metric = Metric.fromKey(key) ?: throw UnknownMetricException(key)
        val days = (if (args.has("days")) args.optInt("days", 30) else 30).coerceIn(1, 365)
        val out = JSONArray()
        manager.exportRows(metric, days).forEach { p ->
            out.put(JSONObject().put("time", ISO.format(p.time)).put("value", p.value).put("label", p.label))
        }
        return out
    }

    suspend fun getGoalsProgress(manager: HealthDataSource, goalsRepo: GoalsRepository): JSONArray {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        val seriesByMetric = GoalCatalog.metrics
            .filter { manager.permissionFor(it) in granted }
            .associateWith { runCatching { manager.readDailySeries(it, 14) }.getOrDefault(emptyList()) }
        val stats: List<WeeklyStat> = computeWeeklyStats(seriesByMetric, goalsRepo.goals.first())
        val out = JSONArray()
        stats.forEach { s ->
            out.put(
                JSONObject()
                    .put("metric", s.metric.key)
                    .put("title", s.metric.title)
                    .put("unit", s.metric.unit)
                    .put("thisWeekAvg", s.thisWeekAvg)
                    .put("lastWeekAvg", s.lastWeekAvg)
                    .put("goal", s.goal)
                    .put("progress", s.progress)
                    .put("deltaPercent", s.deltaPercent)
            )
        }
        return out
    }

    /** MCP `tools/list` payload describing every tool above. */
    fun toolDefinitions(): JSONArray {
        fun schema(properties: JSONObject, required: List<String> = emptyList()): JSONObject =
            JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray(required))

        val metricProp = JSONObject().put("metric", JSONObject().put("type", "string").put("description", "Metric key, e.g. \"steps\""))
        val daysProp = JSONObject().put("days", JSONObject().put("type", "integer").put("description", "Days of history, 1-365 (default 30)"))

        return JSONArray()
            .put(
                JSONObject()
                    .put("name", "list_metrics")
                    .put("description", "List every Health Connect metric Heartwood surfaces, with its grant status.")
                    .put("inputSchema", schema(JSONObject()))
            )
            .put(
                JSONObject()
                    .put("name", "get_dashboard")
                    .put("description", "Get the current Today dashboard: headline value and sparkline per metric.")
                    .put("inputSchema", schema(JSONObject()))
            )
            .put(
                JSONObject()
                    .put("name", "get_metric_detail")
                    .put("description", "Get full detail for one metric: chart points, stats, and recent records.")
                    .put("inputSchema", schema(metricProp, listOf("metric")))
            )
            .put(
                JSONObject()
                    .put("name", "get_metric_series")
                    .put("description", "Get a flat time series for one metric over a given number of days.")
                    .put(
                        "inputSchema",
                        schema(
                            JSONObject().put("metric", metricProp.get("metric")).put("days", daysProp.get("days")),
                            listOf("metric"),
                        ),
                    )
            )
            .put(
                JSONObject()
                    .put("name", "get_goals_progress")
                    .put("description", "Get this-week vs last-week averages against goals for every goal-capable metric.")
                    .put("inputSchema", schema(JSONObject()))
            )
    }

    private val ISO = java.time.format.DateTimeFormatter.ISO_INSTANT
}
