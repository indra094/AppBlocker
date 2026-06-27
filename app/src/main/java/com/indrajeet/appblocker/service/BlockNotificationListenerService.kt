package com.indrajeet.appblocker.service

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.indrajeet.appblocker.AppBlockerApplication
import com.indrajeet.appblocker.blocking.RuleEvaluator
import com.indrajeet.appblocker.blocking.RuleSnapshot
import com.indrajeet.appblocker.util.WhatsappCallNotificationHeuristics
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

class BlockNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var observerStarted: Boolean = false

    @Volatile
    private var ruleSnapshot: RuleSnapshot = RuleSnapshot(emptyList())

    @Volatile
    private var whatsappCallWindow: WhatsappCallWindowConfig = WhatsappCallWindow.defaultConfig

    private var lastPromotedWhatsappCallKey: String? = null
    private var lastPromotedWhatsappCallAt: Long = 0L

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { evaluateActiveWhatsappNotifications() }
            mainHandler.postDelayed(this, WHATSAPP_CALL_TICK_MS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (observerStarted) {
            return
        }
        observerStarted = true
        val repository = (application as AppBlockerApplication).repository
        serviceScope.launch {
            repository.observeRuleSnapshot().collectLatest {
                ruleSnapshot = it
                cancelActiveBlockedWhatsappNotifications()
            }
        }
        serviceScope.launch {
            repository.observeWhatsappCallWindow().collectLatest {
                whatsappCallWindow = it
                evaluateActiveWhatsappNotifications()
            }
        }
        mainHandler.post(ticker)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val packageName = notification.packageName
        if (packageName !in WHATSAPP_PACKAGES) {
            return
        }
        val snapshot = ruleSnapshot
        if (snapshot.buckets.isNotEmpty()) {
            val now = ZonedDateTime.now()
            if (RuleEvaluator.activeBucketForPackage(snapshot, packageName, now) != null) {
                runCatching { cancelNotification(notification.key) }
            }
        }
        maybePromoteBlockedWhatsappCall(notification)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(ticker)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cancelActiveBlockedWhatsappNotifications() {
        val snapshot = ruleSnapshot
        if (snapshot.buckets.isEmpty()) {
            return
        }
        val now = ZonedDateTime.now()
        activeNotifications
            .orEmpty()
            .asSequence()
            .filter { it.packageName in WHATSAPP_PACKAGES }
            .filter { RuleEvaluator.activeBucketForPackage(snapshot, it.packageName, now) != null }
            .forEach { sbn ->
                runCatching { cancelNotification(sbn.key) }
            }
    }

    private fun evaluateActiveWhatsappNotifications() {
        cancelActiveBlockedWhatsappNotifications()
        activeNotifications
            .orEmpty()
            .asSequence()
            .filter { it.packageName in WHATSAPP_PACKAGES }
            .forEach(::maybePromoteBlockedWhatsappCall)
    }

    private fun maybePromoteBlockedWhatsappCall(notification: StatusBarNotification) {
        if (!isAccessibilityServiceEnabled()) {
            return
        }
        if (!WhatsappCallWindow.shouldDisconnect(ZonedDateTime.now(), whatsappCallWindow)) {
            return
        }
        if (!looksLikeWhatsappCallNotification(notification)) {
            return
        }
        if (!shouldPromoteWhatsappCallNotification(notification.key)) {
            return
        }
        if (sendCallEndAction(notification.notification)) {
            return
        }
        sendCallSurfaceIntent(notification.notification)
    }

    private fun looksLikeWhatsappCallNotification(notification: StatusBarNotification): Boolean {
        val payload = notification.notification
        val extras = payload.extras
        val actionTitles = payload.actions
            ?.map { action -> action.title?.toString() }
            .orEmpty()
        return WhatsappCallNotificationHeuristics.looksLikeCallNotification(
            category = payload.category,
            isOngoing = payload.flags and Notification.FLAG_ONGOING_EVENT != 0,
            fields = listOf(
                payload.tickerText?.toString(),
                extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
                extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ) + actionTitles
        )
    }

    private fun shouldPromoteWhatsappCallNotification(notificationKey: String): Boolean {
        val now = System.currentTimeMillis()
        if (
            notificationKey == lastPromotedWhatsappCallKey &&
            now - lastPromotedWhatsappCallAt < WHATSAPP_CALL_PROMOTION_COOLDOWN_MS
        ) {
            return false
        }
        lastPromotedWhatsappCallKey = notificationKey
        lastPromotedWhatsappCallAt = now
        return true
    }

    private fun sendCallEndAction(notification: Notification): Boolean {
        return notification.actions
            ?.asSequence()
            .orEmpty()
            .filter { action ->
                action.title
                    ?.toString()
                    ?.lowercase(Locale.US)
                    ?.let { title ->
                        WHATSAPP_CALL_END_ACTION_MARKERS.any(title::contains)
                    } == true
            }
            .any { action ->
                val pendingIntent = action.actionIntent ?: return@any false
                runCatching { pendingIntent.send() }.isSuccess
            }
    }

    private fun sendCallSurfaceIntent(notification: Notification) {
        val intents = listOfNotNull(notification.fullScreenIntent, notification.contentIntent)
        intents.firstOrNull { pendingIntent ->
            runCatching { pendingIntent.send() }.isSuccess
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.contains(
            "$packageName/${BlockAccessibilityService::class.java.name}",
            ignoreCase = true
        )
    }

    companion object {
        private const val WHATSAPP_CALL_TICK_MS = 5_000L
        private const val WHATSAPP_CALL_PROMOTION_COOLDOWN_MS = 10_000L
        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
        private val WHATSAPP_CALL_END_ACTION_MARKERS = listOf(
            "end call",
            "hang up",
            "hangup",
            "decline",
            "leave call"
        )
    }
}
