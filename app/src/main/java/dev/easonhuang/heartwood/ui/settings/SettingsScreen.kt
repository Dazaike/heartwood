package dev.easonhuang.heartwood.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.easonhuang.heartwood.BuildConfig
import dev.easonhuang.heartwood.data.ExportFormat
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.HealthDataSource
import dev.easonhuang.heartwood.data.DataSource
import dev.easonhuang.heartwood.data.DataSourcePreferences
import dev.easonhuang.heartwood.data.UnitPreferences
import dev.easonhuang.heartwood.data.UnitSystem
import dev.easonhuang.heartwood.mcp.McpServerPreferences
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.WidgetPreferences
import dev.easonhuang.heartwood.mcp.McpServerService
import dev.easonhuang.heartwood.widget.WidgetUpdateScheduler
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/GuyOnWifi/heartwood"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    manager: HealthDataSource,
    exporter: ExportManager,
    widgetPrefs: WidgetPreferences,
    mcpPrefs: McpServerPreferences,
    dataSourcePrefs: DataSourcePreferences,
    unitPrefs: UnitPreferences,
    bottomInset: Dp,
    onManagePermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showExportDialog by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var grantedMetrics by remember { mutableStateOf<List<Metric>>(emptyList()) }

    var pendingMetrics by remember { mutableStateOf<List<Metric>>(emptyList()) }
    var pendingDays by remember { mutableStateOf(365) }

    suspend fun runExport(uri: Uri, format: ExportFormat, metrics: List<Metric>, days: Int) {
        if (metrics.isEmpty()) {
            snackbar.showSnackbar("No accessible data to export"); return
        }
        exporting = true
        val result = try { exporter.export(uri, metrics, format, days) } finally { exporting = false }
        result.fold(
            onSuccess = { count -> snackbar.showSnackbar("Exported $count records as ${format.label}") },
            onFailure = { snackbar.showSnackbar("Export failed: ${it.message}") },
        )
    }

    suspend fun runShareMarkdown(metrics: List<Metric>, days: Int) {
        if (metrics.isEmpty()) {
            snackbar.showSnackbar("No accessible data to export"); return
        }
        exporting = true
        val result = try { exporter.exportMarkdownToCache(metrics, days) } finally { exporting = false }
        result.fold(
            onSuccess = { (contentUri, count) ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = ExportFormat.MARKDOWN.mime
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Heartwood Health Data Export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share health data export").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    context.startActivity(chooser)
                }.onFailure {
                    snackbar.showSnackbar("Could not open share dialog: ${it.message}")
                }
            },
            onFailure = { snackbar.showSnackbar("Export failed: ${it.message}") },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.CSV, pendingMetrics, pendingDays) } } }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.JSON, pendingMetrics, pendingDays) } } }
    val mcpEnabled by mcpPrefs.enabled.collectAsStateWithLifecycle(initialValue = false)
    val mcpToken by mcpPrefs.token.collectAsStateWithLifecycle(initialValue = null)
    val widgetInterval by widgetPrefs.refreshIntervalMinutes.collectAsStateWithLifecycle(initialValue = 30)
    val currentSource by dataSourcePrefs.source.collectAsStateWithLifecycle(initialValue = DataSource.HEALTH_CONNECT)

    val currentUnitSystem by unitPrefs.unitSystem.collectAsStateWithLifecycle(initialValue = UnitSystem.IMPERIAL)
    fun startMcpServer() {
        scope.launch {
            mcpPrefs.setEnabled(true)
            ContextCompat.startForegroundService(context, Intent(context, McpServerService::class.java))
        }
    }

    fun stopMcpServer() {
        scope.launch { mcpPrefs.setEnabled(false) }
        context.stopService(Intent(context, McpServerService::class.java))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMcpServer()
        } else {
            scope.launch { snackbar.showSnackbar("Notification permission is required to run the local AI server.") }
        }
    }

    fun onMcpToggle(enable: Boolean) {
        if (!enable) {
            stopMcpServer()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMcpServer()
        }
    }

    LaunchedEffect(showExportDialog) {
        if (showExportDialog) {
            val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
            grantedMetrics = Metric.entries.filter { manager.permissionFor(it) in granted }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionLabel("Data") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        title = "Manage data access",
                        subtitle = "Choose which health data Heartwood can read",
                        onClick = onManagePermissions,
                    )
                    SettingRow(
                        icon = Icons.Rounded.Download,
                        title = "Export data",
                        subtitle = "Choose metrics, range and format to save to a file",
                        onClick = { showExportDialog = true },
                    )
                }
            }
            item { SectionLabel("Data source") }
            item {
                SettingsCard {
                    listOf(
                        DataSource.HEALTH_CONNECT to ("Health Connect" to "Read via Android's Health Connect (default)"),
                        DataSource.SAMSUNG_HEALTH to ("Samsung Health (direct)" to "Reads straight from the Samsung Health app; bypasses Health Connect"),
                    ).forEach { (source, labels) ->
                        val (title, subtitle) = labels
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = source == currentSource,
                                    onClick = { scope.launch { dataSourcePrefs.setSource(source) } },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = source == currentSource, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Units") }
            item {
                SettingsCard {
                    UnitSystem.entries.forEach { system ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = system == currentUnitSystem,
                                    onClick = { scope.launch { unitPrefs.setUnitSystem(system) } },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = system == currentUnitSystem, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(system.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item { SectionLabel("Widgets") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.Widgets,
                        title = "Widget refresh interval",
                        subtitle = "Every ${widgetIntervalLabel(widgetInterval)}",
                        onClick = { showIntervalDialog = true },
                    )
                }
            }
            item { SectionLabel("AI access") }
            item {
                SettingsCard {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.SmartToy, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(9.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Local MCP server", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (mcpEnabled) "On — 127.0.0.1:${McpServerService.PORT}" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = mcpEnabled, onCheckedChange = ::onMcpToggle)
                    }
                    if (mcpEnabled) {
                        val clipboard = LocalClipboardManager.current
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Pairing token", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    mcpToken ?: "generating…",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { mcpToken?.let { clipboard.setText(AnnotatedString(it)) } }) {
                                    Text("Copy")
                                }
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "adb forward tcp:${McpServerService.PORT} tcp:${McpServerService.PORT}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Anyone with local device or ADB access to this token can read your health data. " +
                                    "Turn off when you're not pairing an AI client.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    mcpPrefs.regenerateToken()
                                    context.stopService(Intent(context, McpServerService::class.java))
                                    ContextCompat.startForegroundService(context, Intent(context, McpServerService::class.java))
                                }
                            }) { Text("Regenerate token") }
                        }
                    }
                }
            }
            item { SectionLabel("About") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = "Heartwood ${BuildConfig.VERSION_NAME}",
                        subtitle = "Private, read-only Health Connect viewer",
                        onClick = {},
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        title = "Open source",
                        subtitle = "GPL-3.0-or-later, view the code",
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            }
                        },
                    )
                }
            }
            item {
                Text(
                    "Heartwood never sends your data anywhere unless you turn on the local AI server above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (exporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text("Preparing export…", Modifier.padding(start = 16.dp))
                }
            },
        )
    }

    if (showExportDialog) {
        ExportDialog(
            grantedMetrics = grantedMetrics,
            onDismiss = { showExportDialog = false },
            onExport = { metrics, days, format ->
                showExportDialog = false
                pendingMetrics = metrics
                pendingDays = days
                when (format) {
                    ExportFormat.CSV -> csvLauncher.launch("heartwood-export.csv")
                    ExportFormat.JSON -> jsonLauncher.launch("heartwood-export.json")
                    ExportFormat.MARKDOWN -> scope.launch { runShareMarkdown(pendingMetrics, pendingDays) }
                }
            },
        )
    }

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Widget refresh interval") },
            text = {
                Column {
                    WidgetPreferences.OPTIONS.forEach { minutes ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = minutes == widgetInterval, onClick = {
                                    showIntervalDialog = false
                                    scope.launch {
                                        widgetPrefs.setRefreshIntervalMinutes(minutes)
                                        WidgetUpdateScheduler.schedule(context, minutes.toLong())
                                    }
                                }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = minutes == widgetInterval, onClick = null)
                            Text(widgetIntervalLabel(minutes), modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIntervalDialog = false }) { Text("Close") } },
        )
    }
}

private fun widgetIntervalLabel(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""}"
    else -> "$minutes minutes"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(9.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
