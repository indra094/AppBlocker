package com.indrajeet.appblocker.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

data class WeeklyUsageSummary(
    val label: String,
    val totalTimeMs: Long
)

internal data class UsageTimelineEvent(
    val timestampMs: Long,
    val eventType: Int,
    val packageName: String? = null
)

internal data class UsageEventTotals(
    val foregroundUsageMs: Long,
    val screenInteractiveMs: Long,
    val hasForegroundEvents: Boolean,
    val hasScreenEvents: Boolean
) {
    val preferredUsageMs: Long?
        get() = when {
            hasForegroundEvents -> foregroundUsageMs
            hasScreenEvents -> screenInteractiveMs
            else -> null
        }
}

object ScreenTimeTracker {
    private const val WEEK_HISTORY_COUNT = 52L
    private const val EVENT_LOOKBACK_MS = 24L * 60L * 60L * 1_000L

    fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun loadWeeklyUsage(
        context: Context,
        now: ZonedDateTime = ZonedDateTime.now()
    ): List<WeeklyUsageSummary> {
        if (!hasUsageAccess(context)) {
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val currentWeekStart = now
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay(now.zone)

        val formatter = DateTimeFormatter.ofPattern("MMM d")
        return (0 until WEEK_HISTORY_COUNT)
            .map { index -> currentWeekStart.minusWeeks(index) }
            .map { weekStart ->
                val weekEnd = weekStart.plusDays(6)
                val intervalEnd = if (indexOfWeek(weekStart, currentWeekStart) == 0L) {
                    now
                } else {
                    weekStart.plusWeeks(1)
                }
                val intervalStartMs = weekStart.toInstant().toEpochMilli()
                val intervalEndMs = intervalEnd.toInstant().toEpochMilli()
                val eventUsageMs = usageStatsManager
                    .loadEventUsage(intervalStartMs, intervalEndMs)
                    ?.preferredUsageMs
                val totalMs = clampUsageToInterval(
                    totalMs = eventUsageMs
                        ?: usageStatsManager.loadAggregateUsage(intervalStartMs, intervalEndMs),
                    intervalStartMs = intervalStartMs,
                    intervalEndMs = intervalEndMs
                )
                val label = "${formatter.format(weekStart)} - ${formatter.format(weekEnd)}"
                WeeklyUsageSummary(
                    label = label,
                    totalTimeMs = totalMs
                )
            }
    }

    private fun indexOfWeek(
        weekStart: ZonedDateTime,
        currentWeekStart: ZonedDateTime
    ): Long {
        return java.time.temporal.ChronoUnit.WEEKS.between(weekStart, currentWeekStart)
    }

    private fun UsageStatsManager.loadEventUsage(
        intervalStartMs: Long,
        intervalEndMs: Long
    ): UsageEventTotals? {
        val lookbackStartMs = max(0L, intervalStartMs - EVENT_LOOKBACK_MS)
        val usageEvents = runCatching {
            queryEvents(lookbackStartMs, intervalEndMs)
        }.getOrNull() ?: return null

        val timelineEvents = mutableListOf<UsageTimelineEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            timelineEvents += UsageTimelineEvent(
                timestampMs = event.timeStamp,
                eventType = event.eventType,
                packageName = event.packageName
            )
        }

        return calculateUsageFromEvents(
            events = timelineEvents,
            intervalStartMs = intervalStartMs,
            intervalEndMs = intervalEndMs
        )
    }

    private fun UsageStatsManager.loadAggregateUsage(
        intervalStartMs: Long,
        intervalEndMs: Long
    ): Long {
        return runCatching {
            queryAndAggregateUsageStats(intervalStartMs, intervalEndMs)
                .values
                .sumOf { it.totalTimeInForeground }
        }.getOrDefault(0L)
    }

    internal fun calculateUsageFromEvents(
        events: Iterable<UsageTimelineEvent>,
        intervalStartMs: Long,
        intervalEndMs: Long
    ): UsageEventTotals {
        if (intervalEndMs <= intervalStartMs) {
            return UsageEventTotals(
                foregroundUsageMs = 0L,
                screenInteractiveMs = 0L,
                hasForegroundEvents = false,
                hasScreenEvents = false
            )
        }

        val foregroundPackages = mutableSetOf<String>()
        var foregroundScreenInteractive = true
        var screenInteractive = false
        var hasForegroundEvents = false
        var hasScreenEvents = false
        var foregroundUsageMs = 0L
        var screenInteractiveMs = 0L
        var lastTimestampMs = intervalStartMs

        events
            .filter { it.timestampMs <= intervalEndMs }
            .sortedBy { it.timestampMs }
            .forEach { event ->
                val eventTimestampMs = event.timestampMs.coerceIn(intervalStartMs, intervalEndMs)
                if (eventTimestampMs > lastTimestampMs) {
                    if (foregroundScreenInteractive && foregroundPackages.isNotEmpty()) {
                        foregroundUsageMs += eventTimestampMs - lastTimestampMs
                    }
                    if (screenInteractive) {
                        screenInteractiveMs += eventTimestampMs - lastTimestampMs
                    }
                    lastTimestampMs = eventTimestampMs
                }

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        hasForegroundEvents = true
                        val packageName = event.packageName
                        if (!packageName.isNullOrBlank()) {
                            foregroundPackages += packageName
                        }
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        hasForegroundEvents = true
                        val packageName = event.packageName
                        if (!packageName.isNullOrBlank()) {
                            foregroundPackages -= packageName
                        }
                    }

                    UsageEvents.Event.SCREEN_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        hasScreenEvents = true
                        foregroundScreenInteractive = true
                        screenInteractive = true
                    }

                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_SHOWN -> {
                        hasScreenEvents = true
                        foregroundScreenInteractive = false
                        screenInteractive = false
                    }
                }
            }

        if (lastTimestampMs < intervalEndMs) {
            if (foregroundScreenInteractive && foregroundPackages.isNotEmpty()) {
                foregroundUsageMs += intervalEndMs - lastTimestampMs
            }
            if (screenInteractive) {
                screenInteractiveMs += intervalEndMs - lastTimestampMs
            }
        }

        return UsageEventTotals(
            foregroundUsageMs = clampUsageToInterval(
                totalMs = foregroundUsageMs,
                intervalStartMs = intervalStartMs,
                intervalEndMs = intervalEndMs
            ),
            screenInteractiveMs = clampUsageToInterval(
                totalMs = screenInteractiveMs,
                intervalStartMs = intervalStartMs,
                intervalEndMs = intervalEndMs
            ),
            hasForegroundEvents = hasForegroundEvents,
            hasScreenEvents = hasScreenEvents
        )
    }

    internal fun clampUsageToInterval(
        totalMs: Long,
        intervalStartMs: Long,
        intervalEndMs: Long
    ): Long {
        val intervalDurationMs = (intervalEndMs - intervalStartMs).coerceAtLeast(0L)
        return totalMs.coerceIn(0L, intervalDurationMs)
    }
}
