package com.indrajeet.appblocker.blocking

import com.indrajeet.appblocker.data.BlockScheduleEntity
import java.time.ZonedDateTime

object RuleEvaluator {
    fun activeBucketForPackage(
        snapshot: RuleSnapshot,
        packageName: String,
        now: ZonedDateTime
    ): BucketRule? {
        return snapshot.buckets.firstOrNull { bucket ->
            packageName in bucket.blockedPackages && isBlockingActive(bucket.schedules, now)
        }
    }

    fun activeBucketForHost(
        snapshot: RuleSnapshot,
        host: String,
        now: ZonedDateTime
    ): Pair<BucketRule, String>? {
        val bucket = snapshot.buckets.firstOrNull { candidate ->
            candidate.blockedHosts.any { hostMatches(it, host) } && isBlockingActive(candidate.schedules, now)
        } ?: return null

        val matchedHost = bucket.blockedHosts.first { hostMatches(it, host) }
        return bucket to matchedHost
    }

    fun isBlockingActive(schedules: List<BlockScheduleEntity>, now: ZonedDateTime): Boolean {
        return schedules.any { isScheduleActive(it, now) }
    }

    fun isScheduleActive(schedule: BlockScheduleEntity, now: ZonedDateTime): Boolean {
        val today = now.toLocalDate()
        val minuteOfDay = now.hour * 60 + now.minute
        val carryEndMinute = schedule.endMinute - 1440

        if (schedule.endMinute <= 1440) {
            return isAllowedDay(schedule.daysOfWeekMask, now.dayOfWeek.value) &&
                today.toEpochDay() >= schedule.startDateEpochDay &&
                (schedule.endDateEpochDay == null || today.toEpochDay() <= schedule.endDateEpochDay) &&
                minuteOfDay in schedule.startMinute until schedule.endMinute
        }

        if (minuteOfDay >= schedule.startMinute) {
            return isAllowedDay(schedule.daysOfWeekMask, now.dayOfWeek.value) &&
                today.toEpochDay() >= schedule.startDateEpochDay &&
                (schedule.endDateEpochDay == null || today.toEpochDay() <= schedule.endDateEpochDay)
        }

        val previousDay = today.minusDays(1)
        return minuteOfDay < carryEndMinute &&
            isAllowedDay(schedule.daysOfWeekMask, previousDay.dayOfWeek.value) &&
            previousDay.toEpochDay() >= schedule.startDateEpochDay &&
            (schedule.endDateEpochDay == null || previousDay.toEpochDay() <= schedule.endDateEpochDay)
    }

    fun hostMatches(blockedHost: String, currentHost: String): Boolean {
        return currentHost == blockedHost || currentHost.endsWith(".$blockedHost")
    }

    private fun isAllowedDay(mask: Int, dayOfWeekValue: Int): Boolean {
        val bit = 1 shl (dayOfWeekValue - 1)
        return mask and bit != 0
    }
}
