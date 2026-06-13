package com.indrajeet.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.indrajeet.appblocker.AppBlockerApplication
import com.indrajeet.appblocker.R
import com.indrajeet.appblocker.admin.BlockDeviceAdminReceiver
import com.indrajeet.appblocker.blocking.RuleEvaluator
import com.indrajeet.appblocker.blocking.RuleSnapshot
import com.indrajeet.appblocker.ui.BlockedActivity
import com.indrajeet.appblocker.util.HostNormalizer
import com.indrajeet.appblocker.util.WhatsappCallWindow
import com.indrajeet.appblocker.util.WhatsappCallWindowConfig
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BlockAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var ruleSnapshot: RuleSnapshot = RuleSnapshot(emptyList())

    @Volatile
    private var currentPackage: String? = null

    @Volatile
    private var whatsappCallWindow: WhatsappCallWindowConfig = WhatsappCallWindow.defaultConfig

    private val settingsPackages = mutableSetOf<String>()

    private var lastShownKey: String? = null
    private var lastShownAt: Long = 0L

    companion object {
        private const val TAG = "BlockAccessibility"
        private const val SAFE_REDIRECT_URL = "https://github.com"
        private const val SAFE_REDIRECT_HOST = "github.com"
        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
        private val WHATSAPP_END_CALL_MARKERS = listOf(
            "end call",
            "hang up",
            "decline",
            "leave call"
        )
        private val WHATSAPP_END_CALL_VIEW_ID_MARKERS = listOf(
            "end_call",
            "hangup",
            "decline",
            "leave_call"
        )
        private val WHATSAPP_IN_CALL_MARKERS = listOf(
            "mute",
            "speaker",
            "switch camera",
            "turn off camera",
            "turn on camera",
            "calling",
            "ringing",
            "reconnecting"
        )
    }

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { evaluateCurrentContext() }
                .onFailure { Log.e(TAG, "Ticker evaluation failed", it) }
            mainHandler.postDelayed(this, 5_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }

            val repository = (application as AppBlockerApplication).repository
            settingsPackages.clear()
            settingsPackages.addAll(discoverSettingsPackages())
            serviceScope.launch {
                repository.observeRuleSnapshot().collectLatest {
                    ruleSnapshot = it
                }
            }
            serviceScope.launch {
                repository.observeWhatsappCallWindow().collectLatest {
                    whatsappCallWindow = it
                }
            }
            mainHandler.post(ticker)
        }
            .onFailure { Log.e(TAG, "Failed to initialize accessibility service", it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            val currentEvent = event ?: return
            val eventType = currentEvent.eventType
            if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                return
            }
            val packageName = currentEvent.packageName?.toString() ?: return
            if (packageName == this.packageName) {
                return
            }
            val foregroundPackage = activeWindowPackageName()
            if (foregroundPackage == this.packageName) {
                return
            }
            val isWindowChangeEvent =
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            val isBrowserContentEvent =
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    packageName in BrowserSupport.browserAddressBars.keys
            val isWhatsappActivityEvent =
                packageName in WHATSAPP_PACKAGES &&
                    (
                        isWindowChangeEvent ||
                            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
                        )
            if (!isWindowChangeEvent && !isBrowserContentEvent && !isWhatsappActivityEvent) {
                return
            }
            when {
                foregroundPackage != null -> currentPackage = foregroundPackage
                isWindowChangeEvent || isBrowserContentEvent -> currentPackage = packageName
            }
            if (isWhatsappActivityEvent) {
                handleWhatsappActivityEvent(currentEvent)
                if (packageName in WHATSAPP_PACKAGES && isOutsideWhatsappCallWindow()) {
                    return
                }
            }
            evaluateCurrentContext()
        }
            .onFailure { Log.e(TAG, "Accessibility event handling failed", it) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(ticker)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun evaluateCurrentContext() {
        val activePackage = activeWindowPackageName() ?: currentPackage ?: return
        currentPackage = activePackage
        if (activePackage in settingsPackages && isProtectedAccessibilityManagementScreen()) {
            triggerBlock(
                reason = "Protected accessibility setting blocked",
                target = activePackage,
                forceHome = true,
                minIntervalMs = 0,
                showBlockedScreen = false
            )
            return
        }
        val snapshot = ruleSnapshot
        val now = ZonedDateTime.now()
        if (disconnectWhatsappCallOutsideAllowedWindow(activePackage, now)) {
            return
        }
        if (snapshot.buckets.isEmpty()) {
            return
        }

        val appBucket = RuleEvaluator.activeBucketForPackage(snapshot, activePackage, now)
        if (appBucket != null) {
            triggerBlock(
                reason = "Blocked by ${appBucket.bucketName}",
                target = activePackage,
                forceHome = true,
                minIntervalMs = 0,
                showBlockedScreen = false
            )
            return
        }

        if (activePackage in BrowserSupport.browserAddressBars.keys) {
            val host = readCurrentHost(activePackage) ?: return
            val hostMatch = RuleEvaluator.activeBucketForHost(snapshot, host, now) ?: return
            if (hostMatch.second == SAFE_REDIRECT_HOST) {
                // Avoid redirect loops if safe URL itself is blocked.
                triggerBlock(
                    reason = "Blocked by ${hostMatch.first.bucketName}",
                    target = hostMatch.second,
                    forceHome = true,
                    minIntervalMs = 1_000
                )
                return
            }
            redirectBlockedWebsite(
                browserPackage = activePackage,
                blockedHost = hostMatch.second,
                bucketName = hostMatch.first.bucketName
            )
        }
    }

    private fun disconnectWhatsappCallOutsideAllowedWindow(
        activePackage: String,
        now: ZonedDateTime
    ): Boolean {
        val currentWindow = whatsappCallWindow
        if (activePackage !in WHATSAPP_PACKAGES || !WhatsappCallWindow.shouldDisconnect(now, currentWindow)) {
            return false
        }
        val root = rootInActiveWindow ?: return false
        if (!looksLikeWhatsappCall(root)) {
            return false
        }

        val disconnected = tapWhatsappEndCall(root)
        triggerBlock(
            reason = if (disconnected) {
                "WhatsApp call ended outside the allowed ${WhatsappCallWindow.description(currentWindow)} window"
            } else {
                "WhatsApp call detected outside the allowed ${WhatsappCallWindow.description(currentWindow)} window"
            },
            target = activePackage,
            forceHome = false,
            minIntervalMs = 5_000,
            showBlockedScreen = false
        )
        return true
    }

    private fun handleWhatsappActivityEvent(event: AccessibilityEvent) {
        if (!isOutsideWhatsappCallWindow()) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in WHATSAPP_PACKAGES) {
            return
        }
        val root = rootInActiveWindow ?: return
        val activePackage = root.packageName?.toString() ?: return
        if (activePackage !in WHATSAPP_PACKAGES) {
            return
        }
        if (looksLikeWhatsappCall(root)) {
            disconnectWhatsappCallOutsideAllowedWindow(activePackage, ZonedDateTime.now())
        }
    }

    private fun isOutsideWhatsappCallWindow(): Boolean {
        return WhatsappCallWindow.shouldDisconnect(ZonedDateTime.now(), whatsappCallWindow)
    }

    private fun isProtectedAccessibilityManagementScreen(): Boolean {
        if (!isManagementProtectionActive()) {
            return false
        }
        val root = rootInActiveWindow ?: return false
        val haystack = StringBuilder(1024)
        appendNodeStrings(root, haystack, depth = 0, maxDepth = 6, maxChars = 4000)
        val normalized = haystack.toString().lowercase(Locale.US)

        val appMarkers = listOf(
            packageName.lowercase(Locale.US),
            getString(R.string.app_name).lowercase(Locale.US),
            getString(R.string.service_label).lowercase(Locale.US)
        )
        val accessibilityMarkers = listOf(
            "accessibility",
            "accessibility service",
            "accessibility services",
            "downloaded apps"
        )
        val hasAppMarker = appMarkers.any { normalized.contains(it) }
        val hasAccessibilityMarker = accessibilityMarkers.any { normalized.contains(it) }
        return hasAppMarker && hasAccessibilityMarker
    }

    private fun isManagementProtectionActive(): Boolean {
        val manager = getSystemService(DevicePolicyManager::class.java) ?: return false
        val admin = ComponentName(this, BlockDeviceAdminReceiver::class.java)
        if (!manager.isAdminActive(admin) && !manager.isDeviceOwnerApp(packageName)) {
            return false
        }
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.contains(
            "$packageName/${BlockAccessibilityService::class.java.name}",
            ignoreCase = true
        )
    }

    private fun appendNodeStrings(
        node: AccessibilityNodeInfo?,
        accumulator: StringBuilder,
        depth: Int,
        maxDepth: Int,
        maxChars: Int
    ) {
        if (node == null || depth > maxDepth || accumulator.length >= maxChars) {
            return
        }
        node.viewIdResourceName?.let {
            accumulator.append(it).append('\n')
        }
        node.text?.toString()?.let {
            accumulator.append(it).append('\n')
        }
        node.contentDescription?.toString()?.let {
            accumulator.append(it).append('\n')
        }
        for (index in 0 until node.childCount) {
            appendNodeStrings(
                node = node.getChild(index),
                accumulator = accumulator,
                depth = depth + 1,
                maxDepth = maxDepth,
                maxChars = maxChars
            )
            if (accumulator.length >= maxChars) {
                return
            }
        }
    }

    private fun discoverSettingsPackages(): Set<String> {
        val discovered = mutableSetOf(
            "com.android.settings",
            "com.android.tv.settings"
        )
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        val activities = packageManager.queryIntentActivities(settingsIntent, 0)
        activities.forEach { info ->
            discovered.add(info.activityInfo.packageName)
        }
        return discovered
    }

    private fun activeWindowPackageName(): String? {
        return rootInActiveWindow
            ?.packageName
            ?.toString()
            ?.takeUnless { it == this.packageName }
    }

    private fun readCurrentHost(browserPackage: String): String? {
        val root = rootInActiveWindow ?: return null
        val ids = BrowserSupport.browserAddressBars[browserPackage].orEmpty()
        for (viewId in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            nodes.firstNotNullOfOrNull { node ->
                HostNormalizer.normalizeOrNull(node.text?.toString())
                    ?: HostNormalizer.normalizeOrNull(node.contentDescription?.toString())
            }?.let { return it }
        }
        return findUrlLikeText(root)
    }

    private fun findUrlLikeText(node: AccessibilityNodeInfo?): String? {
        if (node == null) {
            return null
        }
        HostNormalizer.normalizeOrNull(node.text?.toString())?.let { return it }
        HostNormalizer.normalizeOrNull(node.contentDescription?.toString())?.let { return it }
        for (index in 0 until node.childCount) {
            findUrlLikeText(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun looksLikeWhatsappCall(root: AccessibilityNodeInfo): Boolean {
        var callSignals = 0
        val foundEndControl = forEachNode(root) { node ->
            if (nodeMatchesAnyMarker(node, WHATSAPP_END_CALL_MARKERS, WHATSAPP_END_CALL_VIEW_ID_MARKERS)) {
                true
            } else {
                if (nodeMatchesAnyMarker(node, WHATSAPP_IN_CALL_MARKERS, emptyList())) {
                    callSignals += 1
                }
                false
            }
        }
        return foundEndControl || callSignals >= 2
    }

    private fun tapWhatsappEndCall(root: AccessibilityNodeInfo): Boolean {
        var clicked = false
        forEachNode(root) { node ->
            if (nodeMatchesAnyMarker(node, WHATSAPP_END_CALL_MARKERS, WHATSAPP_END_CALL_VIEW_ID_MARKERS)) {
                clicked = clickNodeOrParent(node)
            }
            clicked
        }
        return clicked
    }

    private fun nodeMatchesAnyMarker(
        node: AccessibilityNodeInfo,
        textMarkers: List<String>,
        viewIdMarkers: List<String>
    ): Boolean {
        val viewId = node.viewIdResourceName?.lowercase(Locale.US)
        if (viewId != null && viewIdMarkers.any(viewId::contains)) {
            return true
        }

        return sequenceOf(
            node.text?.toString(),
            node.contentDescription?.toString()
        )
            .filterNotNull()
            .map { it.lowercase(Locale.US) }
            .any { value -> textMarkers.any(value::contains) }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun forEachNode(
        node: AccessibilityNodeInfo?,
        visit: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (node == null) {
            return false
        }
        if (visit(node)) {
            return true
        }
        for (index in 0 until node.childCount) {
            if (forEachNode(node.getChild(index), visit)) {
                return true
            }
        }
        return false
    }

    private fun redirectBlockedWebsite(
        browserPackage: String,
        blockedHost: String,
        bucketName: String
    ) {
        val now = System.currentTimeMillis()
        val key = "redirect::$browserPackage::$blockedHost::$bucketName"
        if (key == lastShownKey && now - lastShownAt < 2_000) {
            return
        }
        lastShownKey = key
        lastShownAt = now

        val packageRedirect = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(SAFE_REDIRECT_URL)
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            setPackage(browserPackage)
        }

        runCatching {
            startActivity(packageRedirect)
        }.onFailure {
            val genericRedirect = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(SAFE_REDIRECT_URL)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(genericRedirect)
        }
    }

    private fun triggerBlock(
        reason: String,
        target: String,
        forceHome: Boolean = false,
        minIntervalMs: Long = 2_000,
        showBlockedScreen: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        val key = "$reason::$target"
        if (key == lastShownKey && now - lastShownAt < minIntervalMs) {
            return
        }
        lastShownKey = key
        lastShownAt = now

        if (forceHome) {
            runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
                .onFailure { Log.e(TAG, "Failed to send user home for $target", it) }
        }
        if (!showBlockedScreen) {
            return
        }

        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockedActivity.EXTRA_REASON, reason)
            putExtra(BlockedActivity.EXTRA_TARGET, target)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.e(TAG, "Failed to show blocked screen for $target", it) }
    }
}
