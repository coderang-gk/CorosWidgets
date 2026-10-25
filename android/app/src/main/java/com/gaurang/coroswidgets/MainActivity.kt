package com.gaurang.coroswidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaurang.coroswidgets.data.CorosWidgetPayload
import com.gaurang.coroswidgets.data.ExpressiveTileType
import com.gaurang.coroswidgets.data.LastRunData
import com.gaurang.coroswidgets.data.MissedWorkoutData
import com.gaurang.coroswidgets.data.NextWorkoutData
import com.gaurang.coroswidgets.data.RaceCountdownData
import com.gaurang.coroswidgets.data.WidgetRepository
import com.gaurang.coroswidgets.notifications.CorosNotificationHelper
import com.gaurang.coroswidgets.ui.ExpressiveTheme
import com.gaurang.coroswidgets.ui.ExpressiveWidgetRenderer
import com.gaurang.coroswidgets.widgets.*
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object PlanRaceHub : Screen("plan_race_hub/{tab}") {
        fun createRoute(tab: Int = 0) = "plan_race_hub/$tab"
    }
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_RUN_DETAIL = "EXTRA_OPEN_RUN_DETAIL"
    }

    data class WidgetCatalogItem(
        val styleIndex: Int,
        val title: String,
        val subtitle: String,
        val spanLabel: String,
        val category: String, // "SCULPTURAL" or "CARDS"
        val slot: ExpressiveTileType
    )

    private val catalog = listOf(
        WidgetCatalogItem(0, "Capsule Stack", "Mileage, Load, Z2 & Race", "2×2", "CARDS", ExpressiveTileType.WEEKLY_MILEAGE),
        WidgetCatalogItem(1, "EvoLab Ring", "Concentric load & readiness", "2×2", "SCULPTURAL", ExpressiveTileType.TRAINING_LOAD),
        WidgetCatalogItem(2, "Contrast Pods", "Circle + squircle volume pods", "2×2", "SCULPTURAL", ExpressiveTileType.NEXT_WORKOUT),
        WidgetCatalogItem(3, "80/20 Split Bar", "Aerobic vs Threshold HR balance", "2×2", "CARDS", ExpressiveTileType.ZONE_SPECTRUM),
        WidgetCatalogItem(4, "Cookie-12 Seal", "Pune HM countdown scallop", "2×2", "SCULPTURAL", ExpressiveTileType.RACE_COUNTDOWN),
        WidgetCatalogItem(5, "Horizon Split", "Weekly progress + Cookie-9", "2×2", "CARDS", ExpressiveTileType.COMMAND_CENTER),
        WidgetCatalogItem(6, "Nested Plan", "Asymmetric 3-run stack", "2×2", "CARDS", ExpressiveTileType.NEXT_WORKOUT),
        WidgetCatalogItem(7, "Slanted Pill", "Tilted capsule + satellites", "2×2", "SCULPTURAL", ExpressiveTileType.WEEKLY_MILEAGE),
        WidgetCatalogItem(8, "Tonal Ellipses", "Layered threshold & goal ovals", "2×2", "SCULPTURAL", ExpressiveTileType.RACE_COUNTDOWN),
        WidgetCatalogItem(9, "Flower Dial", "Cookie-12 readiness + week dots", "2×2", "SCULPTURAL", ExpressiveTileType.TRAINING_LOAD),
        WidgetCatalogItem(10, "Display Split", "Tall condensed race countdown", "2×2", "CARDS", ExpressiveTileType.RACE_COUNTDOWN),
        WidgetCatalogItem(11, "EvoLab Splits", "4-row pace & HR table", "2×2", "CARDS", ExpressiveTileType.COMMAND_CENTER)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CorosNotificationHelper.createNotificationChannel(this)

        val shouldOpenRunDetailFromIntent = intent?.getBooleanExtra(EXTRA_OPEN_RUN_DETAIL, false) ?: false

        setContent {
            val context = LocalContext.current
            val colorScheme = ExpressiveTheme.getComposeColorScheme(context)

            var payload by remember { mutableStateOf(WidgetRepository.getCachedPayload(context)) }
            var isSyncing by remember { mutableStateOf(false) }
            var selectedSection by remember { mutableIntStateOf(0) } // 0 = Training Hub, 1 = Widgets
            var selectedFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Sculptural, 2 = Cards
            var showServerDialog by remember { mutableStateOf(false) }
            var isResolvingMissed by remember { mutableStateOf(false) }
            var selectedRunForDetail by remember {
                mutableStateOf(if (shouldOpenRunDetailFromIntent) payload.lastRun else null)
            }
            var showWorkoutDetail by remember { mutableStateOf(false) }

            val navController = rememberNavController()

            // Notification permission launcher for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(Unit) {
                isSyncing = true
                CorosWidgetUpdateHelper.refreshAllWidgetsAsync(context, forceServerRefresh = false) {
                    runOnUiThread {
                        payload = WidgetRepository.getCachedPayload(context)
                        isSyncing = false
                    }
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Main.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Main.route) {
                        MainAppScaffold(
                            payload = payload,
                            isSyncing = isSyncing,
                            selectedSection = selectedSection,
                            onSectionSelected = { selectedSection = it },
                            selectedFilter = selectedFilter,
                            onFilterSelected = { selectedFilter = it },
                            catalog = catalog,
                            onRunClicked = { run -> selectedRunForDetail = run },
                            onWorkoutClicked = { showWorkoutDetail = true },
                            onRaceClicked = { navController.navigate(Screen.PlanRaceHub.createRoute(1)) },
                            onCalendarClicked = { navController.navigate(Screen.PlanRaceHub.createRoute(0)) },
                            onResolveMissedWorkout = { action, targetDate ->
                                val missed = payload.missedWorkout ?: return@MainAppScaffold
                                isResolvingMissed = true
                                Thread {
                                    val res = WidgetRepository.resolveMissedWorkout(context, missed.eventId, action, targetDate)
                                    runOnUiThread {
                                        isResolvingMissed = false
                                        res.onSuccess { fresh ->
                                            payload = fresh
                                            CorosWidgetUpdateHelper.renderAllFromCache(context)
                                            val msg = if (action == "SHIFT") "Shifted to $targetDate in Google Calendar" else "Workout marked [SKIPPED] in Google Calendar"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }.start()
                            },
                            isResolvingMissed = isResolvingMissed,
                            onSyncClicked = {
                                isSyncing = true
                                CorosWidgetUpdateHelper.refreshAllWidgetsAsync(context, forceServerRefresh = true) { res ->
                                    runOnUiThread {
                                        payload = WidgetRepository.getCachedPayload(context)
                                        isSyncing = false
                                        res.onSuccess {
                                            Toast.makeText(context, "Synced with COROS & Google Calendar", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Using cached data", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onSettingsClicked = { showServerDialog = true },
                            onPinWidget = { item ->
                                WidgetRepository.setStyleForTile(context, item.slot, item.styleIndex)
                                CorosWidgetUpdateHelper.renderAllFromCache(context)
                                pinWidgetToLauncher(item)
                            }
                        )
                    }

                    composable(
                        route = Screen.PlanRaceHub.route,
                        arguments = listOf(
                            navArgument("tab") {
                                type = NavType.IntType
                                defaultValue = 0
                            }
                        )
                    ) { backStackEntry ->
                        val tab = backStackEntry.arguments?.getInt("tab") ?: 0
                        PlanRaceHubScreen(
                            payload = payload,
                            initialTab = tab,
                            onNavigateBack = { navController.navigateUp() }
                        )
                    }
                }

                if (showWorkoutDetail) {
                    NextWorkoutDetailModalBottomSheet(
                        workout = payload.nextWorkout,
                        raceCountdown = payload.raceCountdown,
                        onDismiss = { showWorkoutDetail = false },
                        onViewFullPlan = {
                            showWorkoutDetail = false
                            navController.navigate(Screen.PlanRaceHub.createRoute(0))
                        }
                    )
                }

                selectedRunForDetail?.let { run ->
                    RunDetailModalBottomSheet(
                        run = run,
                        onDismiss = { selectedRunForDetail = null }
                    )
                }

                if (showServerDialog) {
                    ServerUrlConfigDialog(
                        initialUrl = WidgetRepository.getServerUrl(context),
                        onDismiss = { showServerDialog = false },
                        onSave = { newUrl ->
                            WidgetRepository.setServerUrl(context, newUrl)
                            showServerDialog = false
                            isSyncing = true
                            CorosWidgetUpdateHelper.refreshAllWidgetsAsync(context, forceServerRefresh = true) {
                                runOnUiThread {
                                    payload = WidgetRepository.getCachedPayload(context)
                                    isSyncing = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun pinWidgetToLauncher(item: WidgetCatalogItem) {
        val manager = AppWidgetManager.getInstance(this)
        val providerClass = when (item.slot) {
            ExpressiveTileType.WEEKLY_MILEAGE -> WeeklyMileageWidgetProvider::class.java
            ExpressiveTileType.TRAINING_LOAD -> TrainingLoadWidgetProvider::class.java
            ExpressiveTileType.NEXT_WORKOUT -> NextWorkoutWidgetProvider::class.java
            ExpressiveTileType.ZONE_SPECTRUM -> ZoneSpectrumWidgetProvider::class.java
            ExpressiveTileType.RACE_COUNTDOWN -> RaceCountdownWidgetProvider::class.java
            ExpressiveTileType.COMMAND_CENTER -> CommandCenterWidgetProvider::class.java
        }
        val provider = ComponentName(this, providerClass)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        }
        Toast.makeText(
            this,
            "Applied '${item.title}' to ${item.slot.title}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold(
    payload: CorosWidgetPayload,
    isSyncing: Boolean,
    selectedSection: Int,
    onSectionSelected: (Int) -> Unit,
    selectedFilter: Int,
    onFilterSelected: (Int) -> Unit,
    catalog: List<MainActivity.WidgetCatalogItem>,
    onRunClicked: (LastRunData) -> Unit,
    onWorkoutClicked: () -> Unit = {},
    onRaceClicked: () -> Unit = {},
    onCalendarClicked: () -> Unit = onRaceClicked,
    onResolveMissedWorkout: (String, String) -> Unit = { _, _ -> },
    isResolvingMissed: Boolean = false,
    onSyncClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onPinWidget: (MainActivity.WidgetCatalogItem) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedSection == 0) "Training Hub" else "Widgets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                actions = {
                    IconButton(
                        onClick = onCalendarClicked,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = rememberPlanVector(),
                            contentDescription = "Plan & Race Hub"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onSyncClicked,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = rememberRefreshVector(),
                            contentDescription = "Sync COROS"
                        )
                    }
                    IconButton(
                        onClick = onSettingsClicked,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = rememberTuneVector(),
                            contentDescription = "Server Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBarItem(
                    selected = selectedSection == 0,
                    onClick = { onSectionSelected(0) },
                    icon = {
                        Icon(
                            imageVector = rememberHubVector(),
                            contentDescription = "Training Hub"
                        )
                    },
                    label = {
                        Text(
                            text = "Training Hub",
                            fontWeight = if (selectedSection == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                NavigationBarItem(
                    selected = selectedSection == 1,
                    onClick = { onSectionSelected(1) },
                    icon = {
                        Icon(
                            imageVector = rememberWidgetsVector(),
                            contentDescription = "Widgets"
                        )
                    },
                    label = {
                        Text(
                            text = "Widgets",
                            fontWeight = if (selectedSection == 1) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        if (selectedSection == 0) {
            TrainingHubSection(
                payload = payload,
                isSyncing = isSyncing,
                contentPadding = innerPadding,
                onRunClicked = onRunClicked,
                onWorkoutClicked = onWorkoutClicked,
                onRaceClicked = onRaceClicked,
                onResolveMissedWorkout = onResolveMissedWorkout,
                isResolvingMissed = isResolvingMissed
            )
        } else {
            WidgetsGallerySection(
                payload = payload,
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
                catalog = catalog,
                contentPadding = innerPadding,
                onPinWidget = onPinWidget
            )
        }
    }
}

@Composable
private fun TrainingHubSection(
    payload: CorosWidgetPayload,
    isSyncing: Boolean,
    contentPadding: PaddingValues,
    onRunClicked: (LastRunData) -> Unit,
    onWorkoutClicked: () -> Unit = {},
    onRaceClicked: () -> Unit = {},
    onResolveMissedWorkout: (String, String) -> Unit = { _, _ -> },
    isResolvingMissed: Boolean = false
) {
    val runs = payload.recentRuns.ifEmpty { listOf(payload.lastRun) }
    val latest = runs.first()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Missed Workout Resolution Card (if a past scheduled session wasn't logged)
        if (payload.missedWorkout != null) {
            item {
                MissedWorkoutCoachCard(
                    missed = payload.missedWorkout,
                    isResolving = isResolvingMissed,
                    onResolve = onResolveMissedWorkout
                )
            }
        }

        // 1. Athlete Overview & Next Workout Card
        item {
            AthleteSummaryHeroCard(
                payload = payload,
                isSyncing = isSyncing,
                onWorkoutClicked = onWorkoutClicked,
                onRaceClicked = onRaceClicked
            )
        }

        // 2. Weekly Mileage Bar & 7-Day M-S Schedule Card
        item {
            WeeklyMileageProgressCard(
                payload = payload,
                onPlanClicked = onRaceClicked
            )
        }

        // 3. Tappable Latest Run Analysis Card (opens detailed modal on tap)
        item {
            LatestRunAnalysisCard(
                run = latest,
                onClick = { onRunClicked(latest) }
            )
        }

        // 4. Recent Runs History (each item also tappable for full analysis)
        if (runs.size > 1) {
            item {
                Text(
                    text = "RECENT WORKOUT LOG",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                )
            }
            items(runs.drop(1), key = { it.date + it.name }) { run ->
                RecentRunRowCard(
                    run = run,
                    onClick = { onRunClicked(run) }
                )
            }
        }
    }
}

@Composable
private fun WidgetsGallerySection(
    payload: CorosWidgetPayload,
    selectedFilter: Int,
    onFilterSelected: (Int) -> Unit,
    catalog: List<MainActivity.WidgetCatalogItem>,
    contentPadding: PaddingValues,
    onPinWidget: (MainActivity.WidgetCatalogItem) -> Unit
) {
    val filteredItems = remember(selectedFilter, catalog) {
        when (selectedFilter) {
            1 -> catalog.filter { it.category == "SCULPTURAL" }
            2 -> catalog.filter { it.category == "CARDS" }
            else -> catalog
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tap any widget to apply & pin",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Colors automatically adapt to your device wallpaper & dark/light theme.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            val options = listOf("All (12)", "Sculptural (6)", "Data Cards (6)")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
            ) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedFilter == index,
                        onClick = { onFilterSelected(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedFilter == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        gridItems(filteredItems, key = { it.styleIndex }) { item ->
            StockPixelWidgetCard(
                item = item,
                payload = payload,
                onClick = { onPinWidget(item) }
            )
        }
    }
}

@Composable
private fun WeeklyMileageProgressCard(
    payload: CorosWidgetPayload,
    onPlanClicked: (() -> Unit)? = null
) {
    val wm = payload.weeklyMileage
    val weekProgress = (wm.completionPct / 100f).coerceIn(0f, 1f)
    val expectedProgress = (wm.expectedCompletionPct / 100f).coerceIn(0f, 1f)
    // Only show active/scheduled training days (hide rest days)
    val trainingDays = remember(wm.days) {
        wm.days.filter { it.plannedKm > 0.0 || it.actualKm > 0.0 }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WEEKLY MILEAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", wm.actualKm),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "/ ${String.format(Locale.US, "%.0f", wm.plannedKm)} km",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${wm.completionPct}% Done",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", wm.remainingKm)} km remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Weekly Capsule Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(weekProgress.coerceAtLeast(0.06f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Training Days Only (No Rest Days)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                trainingDays.forEach { day ->
                    val isCompleted = day.actualKm > 0
                    val dayPct = if (day.plannedKm > 0) {
                        ((day.actualKm / day.plannedKm) * 100.0 + 0.5).toInt()
                    } else if (isCompleted) 100 else 0
                    val barFrac = (dayPct / 100f).coerceIn(0f, 1f)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (day.isToday) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = day.dayName.uppercase(Locale.US),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCompleted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = day.workoutType,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = if (isCompleted) {
                                        "${String.format(Locale.US, "%.1f", day.actualKm)} / ${String.format(Locale.US, "%.0f", day.plannedKm)}K · $dayPct%"
                                    } else {
                                        "Upcoming · ${String.format(Locale.US, "%.0f", day.plannedKm)}K"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                if (isCompleted) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(barFrac.coerceAtLeast(0.05f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer row linking to full Plan & Race Hub
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onPlanClicked != null) { onPlanClicked?.invoke() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rc = payload.raceCountdown
                val raceLabel = rc.raceName.ifBlank { "Half Marathon" }
                    .split(" ")
                    .joinToString(" ") { word -> word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) } }
                Text(
                    text = "Week ${rc.currentWeek} of ${rc.totalWeeks} · $raceLabel Plan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "View Plan →",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LatestRunAnalysisCard(
    run: LastRunData,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "LATEST RUN · ${run.dayLabel.uppercase(Locale.US)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = run.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", run.distanceKm)} km  ·  ${run.avgPace}  ·  ${run.avgHr} bpm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = run.verdictBadge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Coach Verdict & Executive Summary Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = run.aiVerdictHeadline,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = run.aiExecutiveSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                    )
                }
            }

            if (run.aiTips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "TOP COACH TIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = run.aiTips.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = "View AI Coach Analysis & Tips",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentRunRowCard(
    run: LastRunData,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run.dayLabel.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = run.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = run.verdictBadge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = run.aiVerdictHeadline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tap for AI Coach verdict & tips →",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunDetailModalBottomSheet(
    run: LastRunData,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI COACH RUN ANALYZER · ${run.dayLabel.uppercase(Locale.US)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = run.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", run.distanceKm)} km  ·  ${run.avgPace}  ·  ${run.avgHr} bpm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = run.verdictBadge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. AI Coach Executive Verdict Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "COACH VERDICT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = run.aiVerdictHeadline,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = run.aiExecutiveSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Deep AI Analyzer Findings
            Text(
                text = "WHAT THE AI ANALYZER FOUND",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                run.aiInsights.forEachIndexed { idx, insight ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "0${idx + 1}",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = insight,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Actionable Coach Tips & Next Workout Prescription
            Text(
                text = "ACTIONABLE COACH TIPS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                run.aiTips.forEachIndexed { idx, tip ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = "TIP ${idx + 1}",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissedWorkoutCoachCard(
    missed: MissedWorkoutData,
    isResolving: Boolean,
    onResolve: (action: String, targetDate: String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "!",
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
                Column {
                    Text(
                        text = "MISSED WORKOUT DETECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${missed.title} (${missed.targetDistanceKm.toInt()}K)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = missed.coachAdvice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (missed.options.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    missed.options.forEach { opt ->
                        if (opt.isRecommended) {
                            Button(
                                onClick = { onResolve(opt.action, opt.targetDate) },
                                enabled = !isResolving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = opt.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                text = "AI RECOMMENDED",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    if (opt.sublabel.isNotBlank()) {
                                        Text(
                                            text = opt.sublabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onResolve(opt.action, opt.targetDate) },
                                enabled = !isResolving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = opt.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (opt.sublabel.isNotBlank()) {
                                        Text(
                                            text = opt.sublabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onResolve("DROP", "") },
                        enabled = !isResolving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (isResolving) "Updating..." else "Drop & Move On",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AthleteSummaryHeroCard(
    payload: CorosWidgetPayload,
    isSyncing: Boolean,
    onWorkoutClicked: (() -> Unit)? = null,
    onRaceClicked: (() -> Unit)? = null
) {
    val wm = payload.weeklyMileage
    val tl = payload.trainingLoad
    val rc = payload.raceCountdown
    val nw = payload.nextWorkout

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSyncing) "Syncing COROS…" else "Updated ${payload.updatedLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = onRaceClicked != null) { onRaceClicked?.invoke() }
                ) {
                    val statusText = if (rc.daysLeft > 0) {
                        "🏁 ${rc.daysLeft}d to Race"
                    } else {
                        "COROS · ${tl.statusBadge}"
                    }
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Column M3 Tonal Metric Pods
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPod(
                    modifier = Modifier.weight(1f),
                    label = "Week ${rc.currentWeek}",
                    value = String.format(Locale.US, "%.1fK", wm.actualKm),
                    sub = "/ ${wm.plannedKm.toInt()}K (${wm.completionPct}%)",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 10.dp, bottomEnd = 10.dp)
                )
                MetricPod(
                    modifier = Modifier.weight(1f),
                    label = "Acute Load",
                    value = "${tl.acuteTl}",
                    sub = tl.statusBadge.lowercase().replaceFirstChar { it.uppercase() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                )
                MetricPod(
                    modifier = Modifier.weight(1f),
                    label = "Threshold",
                    value = rc.currentLtsp.replace(" /km", ""),
                    sub = "${tl.lthr} bpm",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 22.dp, bottomEnd = 22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Next Workout Strip (Clickable -> Opens Detailed Workout Inspection)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = onWorkoutClicked != null) { onWorkoutClicked?.invoke() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val cleanDayBadge = remember(nw.dayBadge) {
                            if (nw.dayBadge.isBlank()) "Next"
                            else nw.dayBadge.split(" ").joinToString(" ") { word ->
                                word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
                            }
                        }
                        Text(
                            text = "NEXT WORKOUT · $cleanDayBadge",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val cleanTitle = remember(nw.title) {
                            if (nw.title.contains("5x1.2K", ignoreCase = true)) "5x1.2K Intervals"
                            else nw.title
                        }
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        val resolvedPace = remember(nw.title, nw.targetPace) {
                            if (nw.title.contains("Interval", ignoreCase = true) || nw.title.contains("5x1.2K", ignoreCase = true)) {
                                "4:35–4:40 /km"
                            } else nw.targetPace
                        }
                        val resolvedHr = remember(nw.title, nw.targetHrZone) {
                            if (nw.title.contains("Interval", ignoreCase = true) || nw.title.contains("5x1.2K", ignoreCase = true)) {
                                "Zone 5 (173–185 bpm)"
                            } else nw.targetHrZone
                        }
                        Text(
                            text = "$resolvedPace  ·  $resolvedHr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = if (nw.targetDistanceKm > 0) "${nw.targetDistanceKm.toInt()} km" else "Planned",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Target Race Strip (Clickable -> Opens Full Plan & Race Hub)
            if (rc.daysLeft > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = onRaceClicked != null) { onRaceClicked?.invoke() },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TARGET RACE · ${rc.daysLeft} DAYS AWAY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val raceDisplayName = remember(rc.raceName) {
                                rc.raceName.ifBlank { "Half Marathon" }
                                    .split(" ")
                                    .joinToString(" ") { word -> word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) } }
                            }
                            Text(
                                text = raceDisplayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val cleanPhase = remember(rc.phase) {
                                rc.phase
                                    .replace("+", "&")
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ") { word ->
                                        if (word == "&") "&"
                                        else word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
                                    }
                            }
                            Text(
                                text = "Week ${rc.currentWeek} of ${rc.totalWeeks} · $cleanPhase",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${rc.daysLeft}d",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatChip(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String = ""
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MetricPod(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    containerColor: Color,
    contentColor: Color,
    shape: RoundedCornerShape
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StockPixelWidgetCard(
    item: MainActivity.WidgetCatalogItem,
    payload: CorosWidgetPayload,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val bitmap = remember(item.styleIndex, payload, isDark) {
        ExpressiveWidgetRenderer.renderTileBitmap(
            context = context,
            tileType = item.slot,
            payload = payload,
            widthPx = 520,
            heightPx = 520,
            styleIndexOverride = item.styleIndex
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Widget Preview Well
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Metadata Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = item.spanLabel,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ServerUrlConfigDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget Server Endpoint") },
        text = {
            Column {
                Text(
                    text = "Automatically tries home Wi-Fi LAN (192.168.1.3) and Tailscale (100.95.186.84).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(urlText) }) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun rememberHubVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "Hub",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(3.5f, 18.49f)
            lineToRelative(6.0f, -6.01f)
            lineToRelative(4.0f, 4.0f)
            lineTo(22.0f, 6.92f)
            lineToRelative(-1.41f, -1.41f)
            lineToRelative(-7.09f, 7.97f)
            lineToRelative(-4.0f, -4.0f)
            lineTo(2.0f, 16.99f)
            close()
        }
    }.build()
}

@Composable
private fun rememberWidgetsVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "Widgets",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(13.0f, 13.0f)
            verticalLineToRelative(8.0f)
            horizontalLineToRelative(8.0f)
            verticalLineToRelative(-8.0f)
            horizontalLineToRelative(-8.0f)
            close()
            moveTo(3.0f, 21.0f)
            horizontalLineToRelative(8.0f)
            verticalLineToRelative(-8.0f)
            horizontalLineTo(3.0f)
            verticalLineToRelative(8.0f)
            close()
            moveTo(3.0f, 3.0f)
            verticalLineToRelative(8.0f)
            horizontalLineToRelative(8.0f)
            verticalLineTo(3.0f)
            horizontalLineTo(3.0f)
            close()
            moveTo(16.66f, 1.69f)
            lineTo(11.0f, 7.34f)
            lineTo(16.66f, 13.0f)
            lineToRelative(5.66f, -5.66f)
            lineToRelative(-5.66f, -5.65f)
            close()
        }
    }.build()
}

@Composable
private fun rememberRefreshVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "Refresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
            curveTo(7.58f, 4.0f, 4.01f, 7.58f, 4.01f, 12.0f)
            reflectiveCurveTo(7.58f, 20.0f, 12.0f, 20.0f)
            curveTo(15.73f, 20.0f, 18.84f, 17.45f, 19.73f, 14.0f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18.0f, 12.0f, 18.0f)
            curveTo(8.69f, 18.0f, 6.0f, 15.31f, 6.0f, 12.0f)
            reflectiveCurveTo(8.69f, 6.0f, 12.0f, 6.0f)
            curveTo(13.66f, 6.0f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13.0f, 11.0f)
            horizontalLineTo(20.0f)
            verticalLineTo(4.0f)
            lineTo(17.65f, 6.35f)
            close()
        }
    }.build()
}

@Composable
private fun rememberTuneVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "Tune",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(3.0f, 17.0f)
            verticalLineTo(19.0f)
            horizontalLineTo(9.0f)
            verticalLineTo(17.0f)
            horizontalLineTo(3.0f)
            close()
            moveTo(3.0f, 5.0f)
            verticalLineTo(7.0f)
            horizontalLineTo(13.0f)
            verticalLineTo(5.0f)
            horizontalLineTo(3.0f)
            close()
            moveTo(13.0f, 21.0f)
            verticalLineTo(19.0f)
            horizontalLineTo(21.0f)
            verticalLineTo(17.0f)
            horizontalLineTo(13.0f)
            verticalLineTo(15.0f)
            horizontalLineTo(11.0f)
            verticalLineTo(21.0f)
            horizontalLineTo(13.0f)
            close()
            moveTo(7.0f, 9.0f)
            verticalLineTo(11.0f)
            horizontalLineTo(3.0f)
            verticalLineTo(13.0f)
            horizontalLineTo(7.0f)
            verticalLineTo(15.0f)
            horizontalLineTo(9.0f)
            verticalLineTo(9.0f)
            horizontalLineTo(7.0f)
            close()
            moveTo(21.0f, 13.0f)
            verticalLineTo(11.0f)
            horizontalLineTo(11.0f)
            verticalLineTo(13.0f)
            horizontalLineTo(21.0f)
            close()
            moveTo(15.0f, 9.0f)
            horizontalLineTo(17.0f)
            verticalLineTo(7.0f)
            horizontalLineTo(21.0f)
            verticalLineTo(5.0f)
            horizontalLineTo(17.0f)
            verticalLineTo(3.0f)
            horizontalLineTo(15.0f)
            verticalLineTo(9.0f)
            close()
        }
    }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NextWorkoutDetailModalBottomSheet(
    workout: NextWorkoutData,
    raceCountdown: RaceCountdownData,
    onDismiss: () -> Unit,
    onViewFullPlan: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val detailStructure = remember(workout) {
        com.gaurang.coroswidgets.calendar.CalendarWorkoutHelper.resolveWorkoutStructure(context, workout)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val cleanBadge = remember(workout.dayBadge) {
                        workout.dayBadge.split(" ").joinToString(" ") { word ->
                            word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
                        }
                    }
                    Text(
                        text = "NEXT SCHEDULED WORKOUT · $cleanBadge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detailStructure.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (workout.targetDistanceKm > 0) "${workout.targetDistanceKm.toInt()} km" else "Planned",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Pods (Pace & HR Zone)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPod(
                    modifier = Modifier.weight(1f),
                    label = "Target Pace",
                    value = detailStructure.targetPace,
                    sub = if (detailStructure.targetPace.contains("4:35") || detailStructure.title.contains("Interval", ignoreCase = true)) "Repeat Interval Pace" else "Target Cruising Pace",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(14.dp)
                )
                MetricPod(
                    modifier = Modifier.weight(1f),
                    label = "Target HR",
                    value = detailStructure.targetHrZone,
                    sub = if (detailStructure.targetHrZone.contains("Zone 5")) "Quality Zone" else "Target Zone",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Workout Structure & Breakdown Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXECUTION STRUCTURE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (detailStructure.isFromCalendar) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Google Calendar Sync",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    for ((step, detail) in detailStructure.steps) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = step,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Coach Physiology Guidance Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = detailStructure.coachGuidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CTA Button: View Full 10-Week Plan & Race Hub
            Button(
                onClick = {
                    onDismiss()
                    onViewFullPlan()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "View 10-Week Plan & Race Strategy →",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanRaceHubScreen(
    payload: CorosWidgetPayload,
    initialTab: Int = 0,
    onNavigateBack: () -> Unit
) {
    val rc = payload.raceCountdown
    val nw = payload.nextWorkout
    val athlete = payload.athlete
    val totalWeeks = if (rc.totalWeeks > 0) rc.totalWeeks else 10
    val currentWeek = if (rc.currentWeek > 0) rc.currentWeek.coerceIn(1, totalWeeks) else 1
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val expandedWeeks = remember { mutableStateMapOf<Int, Boolean>().apply { put(currentWeek, true) } }

    Scaffold(
        topBar = {
            val cleanRaceName = remember(rc.raceName) {
                if (rc.raceName.isBlank()) "Half Marathon"
                else rc.raceName.split(" ").joinToString(" ") { word ->
                    word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
                }
            }
            TopAppBar(
                title = {
                    Text(
                        text = "$cleanRaceName Plan",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = rememberArrowBackVector(),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Schedule", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Race Strategy", fontWeight = FontWeight.Bold) }
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(
                            initialOffsetX = { it / 3 },
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(280)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { -it / 3 },
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(200))
                            )
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(280)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { it / 3 },
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(200))
                            )
                    }
                },
                label = "PlanHubTabTransition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                0 -> {
                    // Schedule Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Hero Next Scheduled Workout
                        if (nw.targetDistanceKm > 0 || (nw.title.isNotBlank() && nw.title != "No Scheduled Workout")) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val cleanDay = remember(nw.dayBadge) {
                                                nw.dayBadge.split(" ").joinToString(" ") { word ->
                                                    word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
                                                }
                                            }
                                            Text(
                                                text = "NEXT SCHEDULED WORKOUT · $cleanDay",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = "Planned",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = nw.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val heroPace = if (nw.title.contains("Interval", ignoreCase = true) || nw.title.contains("5x1.2K", ignoreCase = true)) "4:35–4:40 /km" else nw.targetPace
                                        Text(
                                            text = "Target: $heroPace  ·  HR Zone: ${nw.targetHrZone}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Schedule Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$totalWeeks-WEEK CALENDAR SCHEDULE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tap week to expand",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Collapsible Weeks
                        items(totalWeeks) { index ->
                            val week = index + 1
                            val isCurrentWeek = week == currentWeek
                            val isExpanded = expandedWeeks[week] == true
                            val isRaceWeek = week == totalWeeks
                            val isPeakWeek = week == (totalWeeks * 0.7).toInt()
                            val isTaperWeek = week == totalWeeks - 1

                            val phaseLabel = when {
                                isRaceWeek -> "RACE WEEK 🏁"
                                isTaperWeek -> "TAPER"
                                isPeakWeek -> "PEAK"
                                week < (totalWeeks * 0.4).toInt() -> "BASE"
                                else -> "BUILD"
                            }

                            val baseVolume = 28.0 + (week * 1.5).coerceAtMost(16.0)
                            val weeklyKm = if (isRaceWeek) 21.1 + 10.0 else if (isTaperWeek) baseVolume * 0.75 else baseVolume
                            val longRunKm = if (isRaceWeek) 21.1 else (12.0 + (week * 1.0)).coerceIn(10.0, 22.0)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        expandedWeeks[week] = !isExpanded
                                    },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isCurrentWeek) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isCurrentWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = "W$week",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isCurrentWeek) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = "Week $week of $totalWeeks",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${String.format(Locale.US, "%.1f", weeklyKm)} km planned · 4 runs",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isRaceWeek) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = phaseLabel,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isRaceWeek) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = if (isExpanded) rememberArrowUpVector() else rememberArrowDownVector(),
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            when (week) {
                                                3 -> {
                                                    PlanWorkoutRow(
                                                        title = "W3 Tue · Threshold 2x3K",
                                                        desc = "Lactate threshold stimulus. Target: ${athlete.ltspPace} at Zone 4 (${athlete.lthr} bpm).",
                                                        distanceKm = 8.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W3 Wed · Easy 6K",
                                                        desc = "Aerobic base recovery. Conversational effort (<152 bpm).",
                                                        distanceKm = 6.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W3 Fri · Easy 6K + Strides",
                                                        desc = "Aerobic volume with 4x20s neuromuscular strides on flats.",
                                                        distanceKm = 6.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W3 Sun · Long Run 20K",
                                                        desc = "Completed 15 km · Target 5:45–6:15/km Zone 2.",
                                                        distanceKm = 20.0
                                                    )
                                                }
                                                4 -> {
                                                    PlanWorkoutRow(
                                                        title = "W4 Tue · 5x1.2K Intervals",
                                                        desc = "Main: 5 × 1.2 km @ 4:35–4:40/km with 2 min jog recovery. Total ~10 km.",
                                                        distanceKm = 10.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W4 Wed · Easy 8K",
                                                        desc = "Aerobic base recovery @ 5:55–6:25/km (<152 bpm).",
                                                        distanceKm = 8.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W4 Fri · Easy 7K + Strides",
                                                        desc = "Easy run @ 5:55–6:25/km + 6 × 20s relaxed strides.",
                                                        distanceKm = 7.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W4 Sun · 20K + 2x4K HM Pace",
                                                        desc = "Long run with 2 × 4 km @ HM Goal Pace (4:58–5:02/km).",
                                                        distanceKm = 20.0
                                                    )
                                                }
                                                5 -> {
                                                    PlanWorkoutRow(
                                                        title = "W5 Tue · 6K Tempo",
                                                        desc = "Main: 6 km continuous @ 4:52–4:58/km. Total ~10 km.",
                                                        distanceKm = 10.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W5 Wed · Easy 8K",
                                                        desc = "Aerobic recovery run @ 5:55–6:25/km (<152 bpm).",
                                                        distanceKm = 8.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W5 Fri · Easy 6K + Strides",
                                                        desc = "Easy aerobic run + 6 × 20s neuromuscular strides.",
                                                        distanceKm = 6.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W5 Sun · Long Run 21.1K",
                                                        desc = "Endurance long run @ 5:45–6:15/km Zone 2.",
                                                        distanceKm = 21.1
                                                    )
                                                }
                                                else -> {
                                                    val tTitle = if (week < 4) "Threshold 2x3K" else if (week < 8) "Threshold 2x4K" else "Tempo 8K"
                                                    val tKm = if (week < 4) 8.0 else if (week < 8) 10.0 else 11.0
                                                    PlanWorkoutRow(
                                                        title = "W$week Tue · $tTitle",
                                                        desc = "Lactate threshold stimulus. Target: ${athlete.ltspPace} at Zone 4 (${athlete.lthr} bpm).",
                                                        distanceKm = tKm
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W$week Wed · Easy 6K",
                                                        desc = "Aerobic base recovery. Conversational effort (<152 bpm).",
                                                        distanceKm = 6.0
                                                    )
                                                    PlanWorkoutRow(
                                                        title = "W$week Fri · Easy 6K + 4 Strides",
                                                        desc = "Aerobic volume with 4x20s neuromuscular strides on flats.",
                                                        distanceKm = 6.0
                                                    )
                                                    if (isRaceWeek) {
                                                        PlanWorkoutRow(
                                                            title = "🏁 W$week Sun · ${rc.raceName.ifBlank { "Half Marathon" }}",
                                                            desc = "Target Finish: ${rc.goalTime} · Cruising Pace: ${rc.goalPace}. Execute fueling strategy!",
                                                            distanceKm = 21.1,
                                                            isRace = true
                                                        )
                                                    } else {
                                                        PlanWorkoutRow(
                                                            title = "W$week Sun · Long Run ${longRunKm.toInt()}K",
                                                            desc = "Aerobic endurance builder. Keep heart rate strictly inside Zone 2 (<155 bpm).",
                                                            distanceKm = longRunKm
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Race Strategy Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "TARGET RACE ROADMAP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = rc.raceName.ifBlank { "HALF MARATHON" },
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Target Date: ${rc.raceDate.ifBlank { "Upcoming" }}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        MetricPod(
                                            modifier = Modifier.weight(1f),
                                            label = "Days Left",
                                            value = "${rc.daysLeft}",
                                            sub = if (rc.daysLeft > 0) "Countdown" else "Race Day",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        MetricPod(
                                            modifier = Modifier.weight(1f),
                                            label = "Readiness",
                                            value = if (rc.readinessTag.contains("ON TRACK")) "ON TRACK" else "BUILDING",
                                            sub = "COROS EvoLab Status",
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Target Pace vs Current Fitness
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "TARGET PACE VS CURRENT FITNESS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Goal Finish Time",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = rc.goalTime,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Goal Target Pace",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = rc.goalPace,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Current Threshold Pace",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = rc.currentLtsp,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Lactate Threshold HR",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${rc.currentLthr} bpm",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Periodization Macrocycle
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "PERIODIZATION MACROCYCLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Current: ${rc.phase}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MacrocyclePhaseCard(
                                            phase = "Phase 1 · Aerobic Base Foundation",
                                            detail = "Builds mitochondrial density, capillary network, and joint durability at Zone 2."
                                        )
                                        MacrocyclePhaseCard(
                                            phase = "Phase 2 · Threshold & Tempo Overload",
                                            detail = "Raises lactate clearing ceiling and locks in race-specific cruising speed."
                                        )
                                        MacrocyclePhaseCard(
                                            phase = "Phase 3 · Peak Volume & Race Taper",
                                            detail = "Supercompensation phase shedding accumulated fatigue while keeping fast-twitch recruitment sharp."
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun PlanWorkoutRow(
    title: String,
    desc: String,
    distanceKm: Double,
    isRace: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isRace) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRace) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRace) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isRace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "${distanceKm.toInt()} km",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRace) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MacrocyclePhaseCard(phase: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = phase,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun rememberPlanVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "Plan",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(19.0f, 4.0f)
            horizontalLineToRelative(-1.0f)
            verticalLineTo(2.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(2.0f)
            horizontalLineTo(8.0f)
            verticalLineTo(2.0f)
            horizontalLineTo(6.0f)
            verticalLineToRelative(2.0f)
            horizontalLineTo(5.0f)
            curveTo(3.89f, 4.0f, 3.01f, 4.9f, 3.01f, 6.0f)
            lineTo(3.0f, 20.0f)
            curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(14.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            verticalLineTo(6.0f)
            curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
            moveTo(19.0f, 20.0f)
            horizontalLineTo(5.0f)
            verticalLineTo(10.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(10.0f)
            close()
            moveTo(19.0f, 8.0f)
            horizontalLineTo(5.0f)
            verticalLineTo(6.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(2.0f)
            close()
        }
    }.build()
}

@Composable
private fun rememberArrowBackVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(20.0f, 11.0f)
            lineTo(7.83f, 11.0f)
            lineTo(13.42f, 5.41f)
            lineTo(12.0f, 4.0f)
            lineTo(4.0f, 12.0f)
            lineTo(12.0f, 20.0f)
            lineTo(13.41f, 18.59f)
            lineTo(7.83f, 13.0f)
            lineTo(20.0f, 13.0f)
            close()
        }
    }.build()
}

@Composable
private fun rememberArrowDownVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "ArrowDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(7.41f, 8.59f)
            lineTo(12.0f, 13.17f)
            lineTo(16.59f, 8.59f)
            lineTo(18.0f, 10.0f)
            lineTo(12.0f, 16.0f)
            lineTo(6.0f, 10.0f)
            close()
        }
    }.build()
}

@Composable
private fun rememberArrowUpVector(): ImageVector = remember {
    ImageVector.Builder(
        name = "ArrowUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(7.41f, 15.41f)
            lineTo(12.0f, 10.83f)
            lineTo(16.59f, 15.41f)
            lineTo(18.0f, 14.0f)
            lineTo(12.0f, 8.0f)
            lineTo(6.0f, 14.0f)
            close()
        }
    }.build()
}
