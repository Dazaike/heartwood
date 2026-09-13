package dev.easonhuang.heartwood.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.error.ErrorCode
import com.samsung.android.sdk.health.data.error.HealthDataException
import kotlinx.coroutines.launch
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.easonhuang.heartwood.data.UnitPreferences
import dev.easonhuang.heartwood.data.DashboardLayoutRepository
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.DataSource
import dev.easonhuang.heartwood.data.DataSourcePreferences
import dev.easonhuang.heartwood.data.HealthDataSource
import dev.easonhuang.heartwood.mcp.McpServerPreferences
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.WidgetPreferences
import dev.easonhuang.heartwood.samsung.SamsungHealthManager
import dev.easonhuang.heartwood.ui.dashboard.CustomizeDashboardScreen
import dev.easonhuang.heartwood.ui.dashboard.DashboardScreen
import dev.easonhuang.heartwood.ui.detail.DetailScreen
import dev.easonhuang.heartwood.ui.onboarding.SamsungHealthErrorScreen
import dev.easonhuang.heartwood.ui.onboarding.LoadingScreen
import dev.easonhuang.heartwood.ui.onboarding.OnboardingScreen
import dev.easonhuang.heartwood.ui.onboarding.UnavailableScreen
import dev.easonhuang.heartwood.ui.settings.SettingsScreen
import dev.easonhuang.heartwood.ui.summary.SummaryScreen

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val ACTION_HC_SETTINGS = "androidx.health.connect.action.HEALTH_CONNECT_SETTINGS"
private const val ACTION_MANAGE_HEALTH_PERMISSIONS = "androidx.health.connect.action.MANAGE_HEALTH_PERMISSIONS"

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.Today),
    SUMMARY("summary", "Summary", Icons.Rounded.Insights),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
fun HeartwoodRoot(
    healthConnect: HealthConnectManager,
    samsungHealth: SamsungHealthManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    dashboardLayout: DashboardLayoutRepository,
    widgetPrefs: WidgetPreferences,
    mcpPrefs: McpServerPreferences,
    dataSourcePrefs: DataSourcePreferences,
    unitPrefs: UnitPreferences,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val source by dataSourcePrefs.source.collectAsStateWithLifecycle(initialValue = DataSource.HEALTH_CONNECT)
    when (source) {
        DataSource.HEALTH_CONNECT -> HealthConnectRoot(
            manager = healthConnect,
            goalsRepo = goalsRepo,
            exporter = exporter,
            dashboardLayout = dashboardLayout,
            widgetPrefs = widgetPrefs,
            mcpPrefs = mcpPrefs,
            dataSourcePrefs = dataSourcePrefs,
            unitPrefs = unitPrefs,
            deepLinkMetric = deepLinkMetric,
            onDeepLinkConsumed = onDeepLinkConsumed,
        )
        DataSource.SAMSUNG_HEALTH -> SamsungHealthRoot(
            manager = samsungHealth,
            goalsRepo = goalsRepo,
            exporter = exporter,
            dashboardLayout = dashboardLayout,
            widgetPrefs = widgetPrefs,
            mcpPrefs = mcpPrefs,
            dataSourcePrefs = dataSourcePrefs,
            unitPrefs = unitPrefs,
            deepLinkMetric = deepLinkMetric,
            onDeepLinkConsumed = onDeepLinkConsumed,
        )
    }
}

@Composable
private fun HealthConnectRoot(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    dashboardLayout: DashboardLayoutRepository,
    widgetPrefs: WidgetPreferences,
    mcpPrefs: McpServerPreferences,
    dataSourcePrefs: DataSourcePreferences,
    unitPrefs: UnitPreferences,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    if (!manager.isAvailable) {
        UnavailableScreen(onInstall = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                        .setPackage("com.android.vending")
                )
            }
        })
        return
    }

    var granted by remember { mutableStateOf<Set<String>?>(null) }
    // True while the initial setup is chaining its permission requests (data → background).
    var inSetup by remember { mutableStateOf(false) }
    var requestedExtras by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result }

    // Re-read grants every time we return to the app (e.g. after toggling in HC settings).
    LifecycleResumeEffect(Unit) {
        scope.launch { granted = manager.grantedPermissions() }
        onPauseOrDispose { }
    }

    val hasData = (granted ?: emptySet()).any { it in manager.metricPermissions }
    val hasBackground = granted?.contains(HealthConnectManager.PERMISSION_READ_IN_BACKGROUND) == true

    // Setup is one continuous flow: after data is granted, immediately chain the background +
    // history prompt (HC won't allow it in the same request), so widgets work straight away.
    LaunchedEffect(inSetup, granted) {
        if (!inSetup || granted == null) return@LaunchedEffect
        when {
            !hasData -> inSetup = false
            !hasBackground && !requestedExtras -> {
                requestedExtras = true
                permissionLauncher.launch(manager.extraPermissions)
            }
            else -> inSetup = false
        }
    }

    fun startSetup() {
        requestedExtras = false
        inSetup = true
        permissionLauncher.launch(manager.metricPermissions)
    }

    fun manageAccess() {
        val g = granted ?: emptySet()
        when {
            !manager.metricPermissions.any { it in g } -> startSetup()
            HealthConnectManager.PERMISSION_READ_IN_BACKGROUND !in g ->
                permissionLauncher.launch(manager.extraPermissions)
            else -> {
                val candidates = listOf(
                    Intent(ACTION_MANAGE_HEALTH_PERMISSIONS).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
                    Intent(ACTION_HC_SETTINGS),
                )
                for (intent in candidates) {
                    if (runCatching { context.startActivity(intent) }.isSuccess) return
                }
                permissionLauncher.launch(manager.permissions)
            }
        }
    }

    when {
        granted == null || inSetup -> LoadingScreen()
        !hasData -> OnboardingScreen(onConnect = ::startSetup)
        else -> MainNav(
            manager = manager,
            goalsRepo = goalsRepo,
            exporter = exporter,
            dashboardLayout = dashboardLayout,
            widgetPrefs = widgetPrefs,
            mcpPrefs = mcpPrefs,
            dataSourcePrefs = dataSourcePrefs,
            unitPrefs = unitPrefs,
            onManagePermissions = ::manageAccess,
            deepLinkMetric = deepLinkMetric,
            onDeepLinkConsumed = onDeepLinkConsumed,
        )
    }
}

@Composable
private fun SamsungHealthRoot(
    manager: SamsungHealthManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    dashboardLayout: DashboardLayoutRepository,
    widgetPrefs: WidgetPreferences,
    mcpPrefs: McpServerPreferences,
    dataSourcePrefs: DataSourcePreferences,
    unitPrefs: UnitPreferences,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf<Set<String>?>(null) }
    var platformError by remember { mutableStateOf<String?>(null) }

    fun loadPermissions() {
        scope.launch {
            runCatching { manager.grantedPermissions() }
                .onSuccess {
                    granted = it
                    platformError = null
                }
                .onFailure { platformError = platformErrorMessage(it) }
        }
    }

    LifecycleResumeEffect(Unit) {
        loadPermissions()
        onPauseOrDispose { }
    }

    fun connect() {
        scope.launch {
            runCatching { manager.requestPermissions(context as Activity) }
                .onSuccess {
                    granted = it
                    platformError = null
                }
                .onFailure { platformError = platformErrorMessage(it) }
        }
    }

    when {
        platformError != null -> SamsungHealthErrorScreen(
            message = platformError!!,
            onRetry = {
                platformError = null
                granted = null
                loadPermissions()
            },
        )
        granted == null -> LoadingScreen()
        granted!!.isEmpty() -> OnboardingScreen(
            onConnect = ::connect,
            sourceName = "Samsung Health",
        )
        else -> MainNav(
            manager = manager,
            goalsRepo = goalsRepo,
            exporter = exporter,
            dashboardLayout = dashboardLayout,
            widgetPrefs = widgetPrefs,
            mcpPrefs = mcpPrefs,
            dataSourcePrefs = dataSourcePrefs,
            unitPrefs = unitPrefs,
            onManagePermissions = ::connect,
            deepLinkMetric = deepLinkMetric,
            onDeepLinkConsumed = onDeepLinkConsumed,
        )
    }
}

private fun platformErrorMessage(error: Throwable): String = when (error) {
    is AuthorizationException ->
        "Samsung Health has not authorized Heartwood. Confirm it is installed, then try again later."
    is HealthDataException -> when (error.errorCode) {
        ErrorCode.ERR_PLATFORM_NOT_INSTALLED ->
            "Samsung Health is not installed. Install it, then try again."
        ErrorCode.ERR_OLD_VERSION_PLATFORM ->
            "Samsung Health needs an update before Heartwood can connect."
        ErrorCode.ERR_PLATFORM_DISABLED ->
            "Samsung Health is disabled. Enable it, then try again."
        ErrorCode.ERR_PLATFORM_NOT_INITIALIZED ->
            "Samsung Health is still starting. Open it once, then try again."
        else -> error.errorMessage.ifBlank { "Samsung Health is unavailable. Please try again." }
    }
    else -> "Samsung Health is unavailable. Please try again."
}

@Composable
private fun MainNav(
    manager: HealthDataSource,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    dashboardLayout: DashboardLayoutRepository,
    widgetPrefs: WidgetPreferences,
    mcpPrefs: McpServerPreferences,
    dataSourcePrefs: DataSourcePreferences,
    unitPrefs: UnitPreferences,
    onManagePermissions: () -> Unit,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = remember { Dest.entries.toList() }
    val showBar = currentRoute in topLevel.map { it.route }

    // Open a metric's detail directly when launched from its widget.
    LaunchedEffect(deepLinkMetric) {
        val metric = deepLinkMetric?.let { Metric.fromKey(it) }
        if (metric != null) {
            navController.navigate("detail/${metric.key}")
            onDeepLinkConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        val bottomInset = inner.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Dest.TODAY.route) {
                DashboardScreen(
                    manager = manager,
                    layoutRepo = dashboardLayout,
                    unitPrefs = unitPrefs,
                    bottomInset = bottomInset,
                    onOpenMetric = { metric -> navController.navigate("detail/${metric.key}") },
                    onManagePermissions = onManagePermissions,
                    onCustomize = { navController.navigate("customize_dashboard") },
                )
            }
            composable("customize_dashboard") {
                CustomizeDashboardScreen(
                    layoutRepo = dashboardLayout,
                    bottomInset = bottomInset,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Dest.SUMMARY.route) {
                SummaryScreen(manager = manager, goalsRepo = goalsRepo, unitPrefs = unitPrefs, bottomInset = bottomInset)
            }
            composable(Dest.SETTINGS.route) {
                SettingsScreen(
                    manager = manager,
                    exporter = exporter,
                    widgetPrefs = widgetPrefs,
                    mcpPrefs = mcpPrefs,
                    dataSourcePrefs = dataSourcePrefs,
                    unitPrefs = unitPrefs,
                    bottomInset = bottomInset,
                    onManagePermissions = onManagePermissions,
                )
            }
            composable("detail/{key}") { entry ->
                val metric = entry.arguments?.getString("key")?.let { Metric.fromKey(it) }
                if (metric == null) {
                    navController.popBackStack()
                } else {
                    DetailScreen(
                        manager = manager,
                        metric = metric,
                        unitPrefs = unitPrefs,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
