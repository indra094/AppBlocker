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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.indrajeet.appblocker.AppBlockerApplication
import com.indrajeet.appblocker.R
import com.indrajeet.appblocker.admin.BlockDeviceAdminReceiver
import com.indrajeet.appblocker.blocking.RuleEvaluator
import com.indrajeet.appblocker.blocking.RuleSnapshot
import com.indrajeet.appblocker.ui.BlockedActivity
import com.indrajeet.appblocker.util.HostNormalizer
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

    private val settingsPackages = mutableSetOf<String>()

    private var lastShownKey: String? = null
    private var lastShownAt: Long = 0L

    companion object {
        private const val SAFE_REDIRECT_URL = "https://github.com"
        private const val SAFE_REDIRECT_HOST = "github.com"
    }

    private val ticker = object : Runnable {
        override fun run() {
            evaluateCurrentContext()
            mainHandler.postDelayed(this, 5_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
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
        mainHandler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentEvent = event ?: return
        val eventType = currentEvent.eventType
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return
        }
        val packageName = currentEvent.packageName?.toString() ?: return
        val isWindowChangeEvent =
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val isBrowserContentEvent =
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                packageName in BrowserSupport.browserAddressBars.keys
        if (!isWindowChangeEvent && !isBrowserContentEvent) {
            return
        }
        if (packageName == this.packageName) {
            return
        }
        currentPackage = packageName
        evaluateCurrentContext()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(ticker)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun evaluateCurrentContext() {
        val activePackage = currentPackage ?: return
        if (activePackage in settingsPackages && isProtectedSelfManagementScreen()) {
            triggerBlock(
                reason = "Protected setting blocked",
                target = activePackage,
                forceHome = true,
                minIntervalMs = 0
            )
            return
        }
        val snapshot = ruleSnapshot
        if (snapshot.buckets.isEmpty()) {
            return
        }
        val now = ZonedDateTime.now()

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

    private fun isProtectedSelfManagementScreen(): Boolean {
        if (!isManagementProtectionActive()) {
            return false
        }
        val root = rootInActiveWindow ?: return false
        val haystack = StringBuilder(1024)
        appendNodeStrings(root, haystack, depth = 0, maxDepth = 6, maxChars = 4000)
        val normalized = haystack.toString().lowercase(Locale.US)

        val appMarkers = listOf(
            packageName.lowercase(Locale.US),
            getString(R.string.app_name).lowercase(Locale.US)
        )
        val dangerMarkers = listOf(
            "uninstall",
            "remove",
            "deactivate",
            "turn off",
            "device admin",
            "device administrators",
            "device admin apps",
            "app info",
            "app details",
            "special app access"
        )
        val hasAppMarker = appMarkers.any { normalized.contains(it) }
        val hasDangerMarker = dangerMarkers.any { normalized.contains(it) }
        return hasAppMarker && hasDangerMarker
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
            performGlobalAction(GLOBAL_ACTION_HOME)
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
        startActivity(intent)
    }
}
