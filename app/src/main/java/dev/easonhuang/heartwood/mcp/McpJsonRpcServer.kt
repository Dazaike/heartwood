package dev.easonhuang.heartwood.mcp

import dev.easonhuang.heartwood.BuildConfig
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthDataSource
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Loopback-only (127.0.0.1) JSON-RPC 2.0 server exposing Heartwood's read-only [McpTools] as an
 * MCP-compatible tool server. Reachable only from the device itself or a PC via
 * `adb forward tcp:PORT tcp:PORT` — never the LAN. The Bearer token is the second safeguard.
 */
class McpJsonRpcServer(
    port: Int,
    private val token: String,
    private val dataSourceProvider: suspend () -> HealthDataSource,
    private val goalsRepo: GoalsRepository,
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.GET) {
            return newFixedLengthResponse(
                Response.Status.OK, "text/plain",
                "Heartwood MCP server. POST JSON-RPC 2.0 to this URL with header 'Authorization: Bearer <token>'.",
            )
        }
        if (session.method != Method.POST) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed")
        }

        val authHeader = session.headers["authorization"]
        if (authHeader != "Bearer $token") {
            return jsonResponse(Response.Status.UNAUTHORIZED, JSONObject().put("error", "unauthorized"))
        }

        val body = HashMap<String, String>()
        runCatching { session.parseBody(body) }
        val raw = body["postData"].orEmpty()

        val request = runCatching { JSONObject(raw) }.getOrNull()
            ?: return jsonResponse(Response.Status.OK, rpcError(null, -32700, "Parse error"))

        val id = if (request.has("id")) request.get("id") else null
        val method = request.optString("method")

        return when (method) {
            "initialize" -> jsonResponse(Response.Status.OK, rpcResult(id, initializeResult()))
            "notifications/initialized" -> newFixedLengthResponse(Response.Status.ACCEPTED, "application/json", "")
            "tools/list" -> jsonResponse(Response.Status.OK, rpcResult(id, JSONObject().put("tools", McpTools.toolDefinitions())))
            "tools/call" -> jsonResponse(Response.Status.OK, rpcResult(id, callTool(request.optJSONObject("params") ?: JSONObject())))
            else -> jsonResponse(Response.Status.OK, rpcError(id, -32601, "Method not found: $method"))
        }
    }

    private fun initializeResult(): JSONObject = JSONObject()
        .put("protocolVersion", "2024-11-05")
        .put("capabilities", JSONObject().put("tools", JSONObject()))
        .put(
            "serverInfo",
            JSONObject().put("name", "heartwood").put("version", BuildConfig.VERSION_NAME),
        )

    private fun callTool(params: JSONObject): JSONObject {
        val name = params.optString("name")
        val args = params.optJSONObject("arguments") ?: JSONObject()
        return runBlocking {
            runCatching {
                val manager = dataSourceProvider()
                val payload = when (name) {
                    "list_metrics" -> McpTools.listMetrics(manager)
                    "get_dashboard" -> McpTools.getDashboard(manager)
                    "get_metric_detail" -> McpTools.getMetricDetail(manager, args)
                    "get_metric_series" -> McpTools.getMetricSeries(manager, args)
                    "get_goals_progress" -> McpTools.getGoalsProgress(manager, goalsRepo)
                    else -> throw IllegalArgumentException("Unknown tool: $name")
                }
                JSONObject().put(
                    "content",
                    org.json.JSONArray().put(JSONObject().put("type", "text").put("text", payload.toString())),
                )
            }.getOrElse { e ->
                JSONObject()
                    .put(
                        "content",
                        org.json.JSONArray().put(JSONObject().put("type", "text").put("text", "Error: ${e.message}")),
                    )
                    .put("isError", true)
            }
        }
    }

    private fun rpcResult(id: Any?, result: JSONObject): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("result", result)

    private fun rpcError(id: Any?, code: Int, message: String): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL)
            .put("error", JSONObject().put("code", code).put("message", message))

    private fun jsonResponse(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString())
}
