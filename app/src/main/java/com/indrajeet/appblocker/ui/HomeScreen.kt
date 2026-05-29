package com.indrajeet.appblocker.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indrajeet.appblocker.data.BlockBucketEntity
import com.indrajeet.appblocker.data.BlockScheduleEntity
import com.indrajeet.appblocker.data.BucketDetails
import com.indrajeet.appblocker.data.ScheduleDraft
import com.indrajeet.appblocker.service.BlockAccessibilityService
import com.indrajeet.appblocker.service.BlockNotificationListenerService
import com.indrajeet.appblocker.util.DayMask
import com.indrajeet.appblocker.util.InstalledApp
import com.indrajeet.appblocker.util.ScreenTimeTracker
import com.indrajeet.appblocker.util.ScheduleFormatter
import com.indrajeet.appblocker.util.WhatsappCallWindow
import com.indrajeet.appblocker.util.WhatsappCallWindowConfig
import com.indrajeet.appblocker.util.WeeklyUsageSummary
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlinx.coroutines.launch

private enum class UsageGraphMode {
    Line,
    Bars
}

private data class MonthMarker(
    val index: Int,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    openAccessibilitySettings: () -> Unit,
    requestBatteryUnrestricted: () -> Unit,
    requestDeviceAdmin: () -> Unit,
    openNotificationAccessSettings: () -> Unit,
    openUsageAccessSettings: () -> Unit,
    openDeviceAdminSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accessibilityEnabled = context.isAccessibilityServiceEnabled()
    val batteryUnrestricted = context.isBatteryUnrestricted()
    val notificationAccessEnabled = context.isNotificationListenerEnabled()
    val usageAccessEnabled = context.isUsageAccessEnabled()

    var showBucketDialog by rememberSaveable { mutableStateOf(false) }
    var showManagementDialog by rememberSaveable { mutableStateOf(false) }
    var showReliabilityDialog by rememberSaveable { mutableStateOf(true) }
    var showWeeklyUsageDialog by rememberSaveable { mutableStateOf(false) }
    var showWhatsappCallWindowDialog by rememberSaveable { mutableStateOf(false) }
    var appBucket by remember { mutableStateOf<BlockBucketEntity?>(null) }
    var websiteBucket by remember { mutableStateOf<BlockBucketEntity?>(null) }
    var scheduleBucket by remember { mutableStateOf<BlockBucketEntity?>(null) }
    var editingSchedule by remember { mutableStateOf<BlockScheduleEntity?>(null) }

    val openAccessibilityForSetup: () -> Unit = openAccessibilitySettings

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AppBlocker") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PermissionCard(
                    accessibilityEnabled = accessibilityEnabled,
                    batteryUnrestricted = batteryUnrestricted,
                    notificationAccessEnabled = notificationAccessEnabled,
                    usageAccessEnabled = usageAccessEnabled,
                    openAccessibilitySettings = openAccessibilityForSetup,
                    requestBatteryUnrestricted = requestBatteryUnrestricted,
                    requestDeviceAdmin = requestDeviceAdmin,
                    openNotificationAccessSettings = openNotificationAccessSettings,
                    openUsageAccessSettings = openUsageAccessSettings,
                    onShowWeeklyUsage = { showWeeklyUsageDialog = true },
                    whatsappCallWindow = uiState.whatsappCallWindow,
                    onConfigureWhatsappCallWindow = { showWhatsappCallWindowDialog = true },
                    openManagementDialog = { showManagementDialog = true },
                    isDeviceAdminActive = uiState.isDeviceAdminActive,
                    isDeviceOwner = uiState.isDeviceOwner
                )
            }
            item {
                SectionHeader(
                    title = "Buckets",
                    action = {
                        IconButton(onClick = { showBucketDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add bucket")
                        }
                    }
                )
            }
            if (uiState.buckets.isEmpty()) {
                item {
                    EmptyCard("No buckets yet. Add one, then put apps, domains, and time windows inside it.")
                }
            } else {
                items(uiState.buckets, key = { "bucket-${it.bucket.id}" }) { bucketDetails ->
                    BucketCard(
                        bucketDetails = bucketDetails,
                        onAddApp = { appBucket = bucketDetails.bucket },
                        onAddWebsite = { websiteBucket = bucketDetails.bucket },
                        onAddSchedule = {
                            scheduleBucket = bucketDetails.bucket
                            editingSchedule = null
                        },
                        onExtendSchedule = { schedule ->
                            scheduleBucket = bucketDetails.bucket
                            editingSchedule = schedule
                        }
                    )
                }
            }
        }
    }

    if (showBucketDialog) {
        BucketEditorDialog(
            onDismiss = { showBucketDialog = false },
            onSave = { name ->
                scope.launch {
                    runCatching { viewModel.addBucket(name) }
                        .onSuccess {
                            showBucketDialog = false
                            snackbarHostState.showSnackbar(it)
                        }
                        .onFailure {
                            snackbarHostState.showSnackbar(viewModel.toUserMessage(it))
                        }
                }
            }
        )
    }

    appBucket?.let { bucket ->
        val bucketDetails = uiState.buckets.firstOrNull { it.bucket.id == bucket.id }
        AppPickerDialog(
            title = "Add apps to ${bucket.name}",
            installedApps = uiState.installedApps,
            alreadyBlocked = bucketDetails?.blockedApps?.map { it.normalizedValue }?.toSet().orEmpty(),
            onDismiss = { appBucket = null },
            onAdd = { app ->
                scope.launch {
                    runCatching { viewModel.addBlockedApp(bucket, app) }
                        .onSuccess {
                            appBucket = null
                            snackbarHostState.showSnackbar(it)
                        }
                        .onFailure { snackbarHostState.showSnackbar(viewModel.toUserMessage(it)) }
                }
            }
        )
    }

    websiteBucket?.let { bucket ->
        WebsiteDialog(
            bucketName = bucket.name,
            onDismiss = { websiteBucket = null },
            onSave = { host ->
                scope.launch {
                    runCatching { viewModel.addBlockedSite(bucket, host) }
                        .onSuccess {
                            websiteBucket = null
                            snackbarHostState.showSnackbar(it)
                        }
                        .onFailure { snackbarHostState.showSnackbar(viewModel.toUserMessage(it)) }
                }
            }
        )
    }

    scheduleBucket?.let { bucket ->
        ScheduleEditorDialog(
            existing = editingSchedule,
            bucketName = bucket.name,
            onDismiss = {
                scheduleBucket = null
                editingSchedule = null
            },
            onSave = { draft, existingId ->
                scope.launch {
                    val outcome = runCatching {
                        if (existingId == null) {
                            viewModel.addSchedule(bucket, draft)
                        } else {
                            viewModel.extendSchedule(existingId, draft)
                        }
                    }
                    outcome.onSuccess {
                        scheduleBucket = null
                        editingSchedule = null
                        snackbarHostState.showSnackbar(it)
                    }.onFailure {
                        snackbarHostState.showSnackbar(viewModel.toUserMessage(it))
                    }
                }
            }
        )
    }

    if (showManagementDialog) {
        ManagementSetupDialog(
            isDeviceAdminActive = uiState.isDeviceAdminActive,
            isDeviceOwner = uiState.isDeviceOwner,
            onDismiss = { showManagementDialog = false },
            requestDeviceAdmin = requestDeviceAdmin,
            openAccessibilitySettings = openAccessibilityForSetup,
            openDeviceAdminSettings = openDeviceAdminSettings
        )
    }

    if (showReliabilityDialog && (!accessibilityEnabled || !batteryUnrestricted)) {
        ReliabilitySetupDialog(
            accessibilityEnabled = accessibilityEnabled,
            batteryUnrestricted = batteryUnrestricted,
            onDismiss = { showReliabilityDialog = false },
            openAccessibilitySettings = openAccessibilityForSetup,
            requestBatteryUnrestricted = requestBatteryUnrestricted
        )
    }

    if (showWeeklyUsageDialog) {
        WeeklyUsageDialog(
            weeklyUsage = uiState.weeklyUsage,
            onDismiss = { showWeeklyUsageDialog = false }
        )
    }

    if (showWhatsappCallWindowDialog) {
        WhatsappCallWindowDialog(
            config = uiState.whatsappCallWindow,
            onDismiss = { showWhatsappCallWindowDialog = false },
            onSave = { startMinute, endMinute ->
                scope.launch {
                    runCatching {
                        viewModel.updateWhatsappCallWindow(
                            startMinute = startMinute,
                            endMinute = endMinute
                        )
                    }.onSuccess {
                        showWhatsappCallWindowDialog = false
                        snackbarHostState.showSnackbar(it)
                    }.onFailure {
                        snackbarHostState.showSnackbar(viewModel.toUserMessage(it))
                    }
                }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    accessibilityEnabled: Boolean,
    batteryUnrestricted: Boolean,
    notificationAccessEnabled: Boolean,
    usageAccessEnabled: Boolean,
    openAccessibilitySettings: () -> Unit,
    requestBatteryUnrestricted: () -> Unit,
    requestDeviceAdmin: () -> Unit,
    openNotificationAccessSettings: () -> Unit,
    openUsageAccessSettings: () -> Unit,
    onShowWeeklyUsage: () -> Unit,
    whatsappCallWindow: WhatsappCallWindowConfig,
    onConfigureWhatsappCallWindow: () -> Unit,
    openManagementDialog: () -> Unit,
    isDeviceAdminActive: Boolean,
    isDeviceOwner: Boolean
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text("Accessibility permission", fontWeight = FontWeight.Bold)
                    Text(
                        if (accessibilityEnabled) {
                            "Enabled. App and website blocking can run."
                        } else {
                            "Required for enforcing blocks. Turn it on before expecting rules to work."
                        }
                    )
                }
            }
            OutlinedButton(onClick = openAccessibilitySettings) {
                Text(if (accessibilityEnabled) "Open accessibility settings" else "Enable accessibility")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text("Battery optimization", fontWeight = FontWeight.Bold)
                    Text(
                        if (batteryUnrestricted) {
                            "Unrestricted mode is active."
                        } else {
                            "Set app battery to unrestricted for reliable blocking."
                        }
                    )
                }
            }
            OutlinedButton(onClick = requestBatteryUnrestricted) {
                Text(if (batteryUnrestricted) "Review battery settings" else "Set battery to unrestricted")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text("Notification access (WhatsApp silence)", fontWeight = FontWeight.Bold)
                    Text(
                        if (notificationAccessEnabled) {
                            "Enabled. WhatsApp notifications can be silenced during active WhatsApp block windows, and foreground WhatsApp calls are ended outside the allowed ${WhatsappCallWindow.description(whatsappCallWindow)} window."
                        } else {
                            "Enable notification access so AppBlocker can cancel WhatsApp notifications during active WhatsApp block windows. WhatsApp call ending outside the allowed ${WhatsappCallWindow.description(whatsappCallWindow)} window still depends on Accessibility."
                        }
                    )
                }
            }
            OutlinedButton(onClick = openNotificationAccessSettings) {
                Text(if (notificationAccessEnabled) "Review notification access" else "Enable notification access")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text("WhatsApp call blocking window", fontWeight = FontWeight.Bold)
                    Text(
                        "Foreground WhatsApp calls are allowed during ${WhatsappCallWindow.description(whatsappCallWindow)}. Outside that Pacific Time window, AppBlocker ends them."
                    )
                }
            }
            OutlinedButton(onClick = onConfigureWhatsappCallWindow) {
                Text("Configure WhatsApp call window")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text("Usage access (weekly screen time)", fontWeight = FontWeight.Bold)
                    Text(
                        if (usageAccessEnabled) {
                            "Enabled. Weekly screen-time totals can be shown inside AppBlocker."
                        } else {
                            "Enable usage access so AppBlocker can show weekly screen-time totals."
                        }
                    )
                }
            }
            OutlinedButton(onClick = openUsageAccessSettings) {
                Text(if (usageAccessEnabled) "Review usage access" else "Enable usage access")
            }
            if (usageAccessEnabled) {
                OutlinedButton(onClick = onShowWeeklyUsage) {
                    Text("View weekly screen time")
                }
            }
            Text("Management protection", fontWeight = FontWeight.Bold)
            Text(
                if (isDeviceAdminActive && accessibilityEnabled) {
                    "Active. AppBlocker automatically blocks settings screens that can disable AppBlocker admin or uninstall paths."
                } else {
                    "Pending. Enable both accessibility and device admin to activate automatic management protection."
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (!isDeviceAdminActive) {
                Button(onClick = requestDeviceAdmin) {
                    Text("Enable device admin")
                }
            } else {
                Text(
                    "Device admin is enabled and protected. Disabling it from settings is blocked by policy.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                if (isDeviceOwner) {
                    "Device owner mode is active. Removal should go through the laptop-managed supervised flow."
                } else if (isDeviceAdminActive) {
                    "Device admin is active. This makes removal more controlled, but full supervised uninstall protection still requires device-owner provisioning from the laptop script."
                } else {
                    "Device admin is not active yet. Enable it here for guided setup, then use the laptop script on an eligible device for stronger supervised management."
                },
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = openManagementDialog) {
                Text("How supervised setup works")
            }
        }
    }
}

@Composable
private fun WhatsappCallWindowDialog(
    config: WhatsappCallWindowConfig,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var startText by rememberSaveable { mutableStateOf(ScheduleFormatter.timeText(config.startMinute)) }
    var endText by rememberSaveable { mutableStateOf(ScheduleFormatter.timeText(config.endMinute)) }
    val dialogScope = rememberCoroutineScope()
    val dialogSnackbarHostState = remember { SnackbarHostState() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    dialogScope.launch {
                        runCatching {
                            onSave(
                                ScheduleFormatter.parseClock(startText),
                                ScheduleFormatter.parseClock(endText)
                            )
                        }.onFailure {
                            dialogSnackbarHostState.showSnackbar(
                                it.message ?: "Invalid WhatsApp call window."
                            )
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("WhatsApp call blocking window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SnackbarHost(dialogSnackbarHostState)
                Text(
                    "Set the Pacific Time window when WhatsApp calls are allowed. Outside this window, AppBlocker ends foreground WhatsApp calls."
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Start time") },
                    placeholder = { Text("08:00") }
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("End time") },
                    placeholder = { Text("06:30") }
                )
                Text(
                    "Use 24-hour HH:MM input. If the end time is earlier than the start time, the allowed window continues past midnight."
                )
            }
        }
    )
}

@Composable
private fun WeeklyUsageDialog(
    weeklyUsage: List<WeeklyUsageSummary>,
    onDismiss: () -> Unit
) {
    val listUsage = weeklyUsage
    val graphUsage = weeklyUsage.asReversed()
    val maxMs = weeklyUsage.maxOfOrNull { it.totalTimeMs } ?: 0L
    var graphMode by rememberSaveable { mutableStateOf(UsageGraphMode.Line.name) }

    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Past 52 Weeks Screen Time",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "See weekly usage as a compact graph. The chart runs oldest to newest from left to right, and the exact totals are listed below.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (listUsage.isEmpty()) {
                        item {
                            Text(
                                "No screen-time usage recorded yet.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = graphMode == UsageGraphMode.Line.name,
                                    onClick = { graphMode = UsageGraphMode.Line.name },
                                    label = { Text("Line") }
                                )
                                FilterChip(
                                    selected = graphMode == UsageGraphMode.Bars.name,
                                    onClick = { graphMode = UsageGraphMode.Bars.name },
                                    label = { Text("Bars") }
                                )
                            }
                        }
                        item {
                            WeeklyUsageGraph(
                                weeklyUsage = graphUsage,
                                maxMs = maxMs,
                                mode = if (graphMode == UsageGraphMode.Bars.name) {
                                    UsageGraphMode.Bars
                                } else {
                                    UsageGraphMode.Line
                                }
                            )
                        }
                        item {
                            MonthMarkerStrip(weeklyUsage = graphUsage)
                        }
                        item {
                            Text(
                                "Exact weekly totals (most recent first)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(listUsage, key = { it.label }) { entry ->
                            WeeklyUsageListRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyUsageGraph(
    weeklyUsage: List<WeeklyUsageSummary>,
    maxMs: Long,
    mode: UsageGraphMode
) {
    val monthMarkers = remember(weeklyUsage) { buildMonthMarkers(weeklyUsage) }
    val strokeColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (weeklyUsage.isEmpty()) {
            return@Canvas
        }

        val chartWidth = size.width
        val chartHeight = size.height
        val denominator = max(1, weeklyUsage.lastIndex)

        monthMarkers.forEach { marker ->
            val x = chartWidth * (marker.index.toFloat() / denominator.toFloat())
            drawLine(
                color = markerColor,
                start = Offset(x, 0f),
                end = Offset(x, chartHeight),
                strokeWidth = 1.dp.toPx()
            )
        }

        drawLine(
            color = markerColor,
            start = Offset(0f, chartHeight),
            end = Offset(chartWidth, chartHeight),
            strokeWidth = 1.dp.toPx()
        )

        if (mode == UsageGraphMode.Bars) {
            val step = chartWidth / weeklyUsage.size.toFloat()
            val barWidth = step * 0.72f
            weeklyUsage.forEachIndexed { index, entry ->
                val fraction = if (maxMs > 0L) {
                    (entry.totalTimeMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val barHeight = chartHeight * fraction
                val left = (index * step) + ((step - barWidth) / 2f)
                drawRect(
                    color = strokeColor,
                    topLeft = Offset(left, chartHeight - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        } else {
            val path = Path()
            weeklyUsage.forEachIndexed { index, entry ->
                val fraction = if (maxMs > 0L) {
                    (entry.totalTimeMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val x = chartWidth * (index.toFloat() / denominator.toFloat())
                val y = chartHeight - (chartHeight * fraction)
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            weeklyUsage.forEachIndexed { index, entry ->
                val fraction = if (maxMs > 0L) {
                    (entry.totalTimeMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val x = chartWidth * (index.toFloat() / denominator.toFloat())
                val y = chartHeight - (chartHeight * fraction)
                drawCircle(
                    color = strokeColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun MonthMarkerStrip(
    weeklyUsage: List<WeeklyUsageSummary>
) {
    val monthMarkers = remember(weeklyUsage) { buildMonthMarkers(weeklyUsage) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
    ) {
        val width = maxWidth
        val denominator = max(1, weeklyUsage.lastIndex)
        monthMarkers.forEach { marker ->
            val rawOffset = width * (marker.index.toFloat() / denominator.toFloat())
            val x = clampMarkerOffset(rawOffset, width)
            Text(
                text = marker.label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = x),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WeeklyUsageListRow(
    entry: WeeklyUsageSummary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            entry.label,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold
        )
        Text(formatHours(entry.totalTimeMs), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun buildMonthMarkers(weeklyUsage: List<WeeklyUsageSummary>): List<MonthMarker> {
    var previousMonth: String? = null
    return weeklyUsage.mapIndexedNotNull { index, entry ->
        val currentMonth = entry.label.substringBefore(' ')
        if (currentMonth == previousMonth) {
            null
        } else {
            previousMonth = currentMonth
            MonthMarker(index = index, label = currentMonth)
        }
    }
}

private fun clampMarkerOffset(rawOffset: Dp, width: Dp): Dp {
    val labelHalfWidth = 12.dp
    val maxOffset = if (width > 28.dp) width - 28.dp else 0.dp
    return when {
        rawOffset < labelHalfWidth -> 0.dp
        rawOffset > maxOffset -> maxOffset
        else -> rawOffset - labelHalfWidth
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BucketCard(
    bucketDetails: BucketDetails,
    onAddApp: () -> Unit,
    onAddWebsite: () -> Unit,
    onAddSchedule: () -> Unit,
    onExtendSchedule: (BlockScheduleEntity) -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(bucketDetails.bucket.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onAddApp, label = { Text("Add app") })
                AssistChip(onClick = onAddWebsite, label = { Text("Add domain") })
                AssistChip(onClick = onAddSchedule, label = { Text("Add window") })
            }
            BucketSection(
                title = "Apps on this bucket",
                values = bucketDetails.blockedApps.map { "${it.displayName} (${it.normalizedValue})" },
                emptyMessage = "No apps yet."
            )
            BucketSection(
                title = "Domains on this bucket",
                values = bucketDetails.blockedSites.map { it.normalizedValue },
                emptyMessage = "No domains yet."
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Time windows", fontWeight = FontWeight.Bold)
                if (bucketDetails.schedules.isEmpty()) {
                    Text("No time windows yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    bucketDetails.schedules.forEach { schedule ->
                        ScheduleCard(schedule = schedule, onExtend = { onExtendSchedule(schedule) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketSection(
    title: String,
    values: List<String>,
    emptyMessage: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
        } else {
            values.forEach { value ->
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ManagementSetupDialog(
    isDeviceAdminActive: Boolean,
    isDeviceOwner: Boolean,
    onDismiss: () -> Unit,
    requestDeviceAdmin: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    openDeviceAdminSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Supervised setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "AppBlocker can guide the user to enable Android permissions in-app, but the strongest supported uninstall protection uses official device-owner provisioning from the laptop script."
                )
                Text(
                    when {
                        isDeviceOwner -> "Current status: device owner is active."
                        isDeviceAdminActive -> "Current status: device admin is active, but device owner is not."
                        else -> "Current status: neither device admin nor device owner is active."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isDeviceAdminActive) {
                    Button(onClick = requestDeviceAdmin, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable device admin")
                    }
                }
                if (isDeviceAdminActive) {
                    Text(
                        "Device admin disable screens are blocked while protection is active.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    OutlinedButton(
                        onClick = openDeviceAdminSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open security settings")
                    }
                }
                OutlinedButton(
                    onClick = openAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open accessibility settings")
                }
                Text(
                    "For dedicated supervised devices, finish setup from the laptop with scripts\\provision-device-owner.ps1. That path depends on Android's device-owner rules, which usually require a freshly reset device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
private fun ReliabilitySetupDialog(
    accessibilityEnabled: Boolean,
    batteryUnrestricted: Boolean,
    onDismiss: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    requestBatteryUnrestricted: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Enable blocking reliability") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "For domain and app blocking to stay reliable, keep accessibility enabled and battery mode unrestricted."
                )
                Text(
                    "Accessibility: ${if (accessibilityEnabled) "Enabled" else "Not enabled"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Battery: ${if (batteryUnrestricted) "Unrestricted" else "Restricted/optimized"}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!accessibilityEnabled) {
                    Button(
                        onClick = openAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable accessibility")
                    }
                }
                if (!batteryUnrestricted) {
                    Button(
                        onClick = requestBatteryUnrestricted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set battery to unrestricted")
                    }
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(
    title: String,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        action?.invoke()
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SimpleValueCard(title: String, subtitle: String) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: BlockScheduleEntity,
    onExtend: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(schedule.label, fontWeight = FontWeight.Bold)
            Text(ScheduleFormatter.describe(schedule))
            AssistChip(onClick = onExtend, label = { Text("Extend only") })
        }
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    installedApps: List<InstalledApp>,
    alreadyBlocked: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (InstalledApp) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleApps = remember(installedApps, alreadyBlocked, query) {
        installedApps
            .asSequence()
            .filterNot { it.packageName in alreadyBlocked }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleApps.take(30), key = { it.packageName }) { app ->
                        ElevatedCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { onAdd(app) }) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun BucketEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Add bucket") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bucket name") },
                placeholder = { Text("Morning focus") }
            )
        }
    )
}

@Composable
private fun WebsiteDialog(
    bucketName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var host by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(host) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Add domain to $bucketName") },
        text = {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hostname or URL") },
                placeholder = { Text("example.com or https://example.com") }
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditorDialog(
    existing: BlockScheduleEntity?,
    bucketName: String,
    onDismiss: () -> Unit,
    onSave: (ScheduleDraft, Long?) -> Unit
) {
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var selectedDayMask by rememberSaveable {
        mutableIntStateOf(
            existing?.daysOfWeekMask ?: DayMask.fromDays(DayOfWeek.values().toSet())
        )
    }
    var startText by rememberSaveable {
        mutableStateOf(existing?.let { ScheduleFormatter.timeText(it.startMinute) } ?: "06:30")
    }
    var endText by rememberSaveable {
        mutableStateOf(
            existing?.let {
                ScheduleFormatter.timeText(it.endMinute % 1440)
            } ?: "09:00"
        )
    }
    var nextDay by rememberSaveable { mutableStateOf(existing?.endMinute?.let { it > 1440 } ?: false) }
    var startDateText by rememberSaveable {
        mutableStateOf(existing?.let { ScheduleFormatter.dateText(it.startDateEpochDay) } ?: LocalDate.now().toString())
    }
    var endDateText by rememberSaveable {
        mutableStateOf(existing?.endDateEpochDay?.let { ScheduleFormatter.dateText(it) } ?: "")
    }
    var forever by rememberSaveable { mutableStateOf(existing?.endDateEpochDay == null) }
    val dialogScope = rememberCoroutineScope()
    val dialogSnackbarHostState = remember { SnackbarHostState() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    dialogScope.launch {
                        runCatching {
                            val startMinute = ScheduleFormatter.parseClock(startText)
                            val endBaseMinute = ScheduleFormatter.parseClock(endText)
                            val startDate = LocalDate.parse(startDateText).toEpochDay()
                            val endDate = if (forever) null else LocalDate.parse(endDateText).toEpochDay()
                            val normalizedEnd = if (nextDay || endBaseMinute <= startMinute) {
                                endBaseMinute + 1440
                            } else {
                                endBaseMinute
                            }
                            ScheduleDraft(
                                label = label,
                                daysOfWeekMask = selectedDayMask,
                                startMinute = startMinute,
                                endMinute = normalizedEnd,
                                startDateEpochDay = startDate,
                                endDateEpochDay = endDate
                            )
                        }.onSuccess {
                            onSave(it, existing?.id)
                        }.onFailure {
                            dialogSnackbarHostState.showSnackbar(
                                it.message ?: "Invalid schedule input."
                            )
                        }
                    }
                }
            ) {
                Text(if (existing == null) "Add" else "Extend")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = {
            Text(
                if (existing == null) {
                    "Add blocking window to $bucketName"
                } else {
                    "Extend blocking window in $bucketName"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SnackbarHost(dialogSnackbarHostState)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Days")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.values().forEach { day ->
                        val dayBit = 1 shl (day.value - 1)
                        FilterChip(
                            selected = selectedDayMask and dayBit != 0,
                            onClick = {
                                selectedDayMask = if (selectedDayMask and dayBit != 0) {
                                    selectedDayMask and dayBit.inv()
                                } else {
                                    selectedDayMask or dayBit
                                }
                            },
                            label = { Text(day.name.take(3)) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Start HH:MM") }
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("End HH:MM") }
                    )
                }
                FilterChip(
                    selected = nextDay,
                    onClick = { nextDay = !nextDay },
                    label = { Text("Ends next day") }
                )
                OutlinedTextField(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    label = { Text("Start date YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = forever,
                    onClick = { forever = !forever },
                    label = { Text("No end date") }
                )
                if (!forever) {
                    OutlinedTextField(
                        value = endDateText,
                        onValueChange = { endDateText = it },
                        label = { Text("End date YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "When editing an existing window, the validator only allows a stronger rule: earlier starts, later ends, more days, or a later end date.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

private fun Context.isAccessibilityServiceEnabled(): Boolean {
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    return enabledServices.contains(
        "$packageName/${BlockAccessibilityService::class.java.name}",
        ignoreCase = true
    )
}

private fun Context.isBatteryUnrestricted(): Boolean {
    val powerManager = getSystemService(PowerManager::class.java) ?: return true
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.isNotificationListenerEnabled(): Boolean {
    val enabledListeners = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners"
    ).orEmpty()
    return enabledListeners.contains(
        "$packageName/${BlockNotificationListenerService::class.java.name}",
        ignoreCase = true
    )
}

private fun Context.isUsageAccessEnabled(): Boolean {
    return ScreenTimeTracker.hasUsageAccess(this)
}

private fun formatHours(totalMs: Long): String {
    val hours = totalMs / 3_600_000.0
    return String.format("%.1f h", hours)
}
