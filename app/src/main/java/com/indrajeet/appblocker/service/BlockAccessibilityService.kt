package com.indrajeet.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.indrajeet.appblocker.AppBlockerApplication
import com.indrajeet.appblocker.blocking.RuleEvaluator
import com.indrajeet.appblocker.blocking.RuleSnapshot
import com.indrajeet.appblocker.ui.BlockedActivity
import com.indrajeet.appblocker.util.HostNormalizer
import java.time.ZonedDateTime
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
    private var settingsGuardEnabled: Boolean = false

    private val settingsPackages = mutableSetOf<String>()

    private var lastShownKey: String? = null
    private var lastShownAt: Long = 0L

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
        serviceScope.launch {
            repository.observeSettingsGuardEnabled().collectLatest {
                settingsGuardEnabled = it
            }
        }
        mainHandler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
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
        if (settingsGuardEnabled && activePackage in settingsPackages) {
            triggerBlock("Device settings blocked", activePackage)
            return
        }
        val snapshot = ruleSnapshot
        if (snapshot.buckets.isEmpty()) {
            return
        }
        val now = ZonedDateTime.now()

        val appBucket = RuleEvaluator.activeBucketForPackage(snapshot, activePackage, now)
        if (appBucket != null) {
            triggerBlock("Blocked by ${appBucket.bucketName}", activePackage)
            return
        }

        if (activePackage in BrowserSupport.browserAddressBars.keys) {
            val host = readCurrentHost(activePackage) ?: return
            val hostMatch = RuleEvaluator.activeBucketForHost(snapshot, host, now) ?: return
            triggerBlock("Blocked by ${hostMatch.first.bucketName}", hostMatch.second)
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

    private fun triggerBlock(reason: String, target: String) {
        val now = System.currentTimeMillis()
        val key = "$reason::$target"
        if (key == lastShownKey && now - lastShownAt < 2_000) {
            return
        }
        lastShownKey = key
        lastShownAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockedActivity.EXTRA_REASON, reason)
            putExtra(BlockedActivity.EXTRA_TARGET, target)
        }
        startActivity(intent)
    }
}
