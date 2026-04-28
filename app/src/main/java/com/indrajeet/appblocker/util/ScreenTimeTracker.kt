package com.indrajeet.appblocker.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class WeeklyUsageSummary(
    val label: String,
    val totalTimeMs: Long
)

object ScreenTimeTracker {
    private const val WEEK_HISTORY_COUNT = 52L

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
                val totalMs = usageStatsManager
                    .queryAndAggregateUsageStats(
                        weekStart.toInstant().toEpochMilli(),
                        intervalEnd.toInstant().toEpochMilli()
                    )
                    .values
                    .sumOf { it.totalTimeInForeground }
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
}
