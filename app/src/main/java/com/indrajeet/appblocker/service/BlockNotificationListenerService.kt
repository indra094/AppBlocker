package com.indrajeet.appblocker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.indrajeet.appblocker.AppBlockerApplication
import com.indrajeet.appblocker.blocking.RuleEvaluator
import com.indrajeet.appblocker.blocking.RuleSnapshot
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BlockNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observerStarted: Boolean = false

    @Volatile
    private var ruleSnapshot: RuleSnapshot = RuleSnapshot(emptyList())

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
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val packageName = notification.packageName
        if (packageName !in WHATSAPP_PACKAGES) {
            return
        }
        val snapshot = ruleSnapshot
        if (snapshot.buckets.isEmpty()) {
            return
        }
        val now = ZonedDateTime.now()
        if (RuleEvaluator.activeBucketForPackage(snapshot, packageName, now) != null) {
            runCatching { cancelNotification(notification.key) }
        }
    }

    override fun onDestroy() {
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

    companion object {
        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
    }
}
