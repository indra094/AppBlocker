package com.indrajeet.appblocker.util

import com.indrajeet.appblocker.data.BlockScheduleEntity
import java.time.LocalDate

object ScheduleFormatter {
    fun describe(schedule: BlockScheduleEntity): String {
        val days = DayMask.toDays(schedule.daysOfWeekMask).joinToString { it.name.take(3) }
        val start = timeText(schedule.startMinute)
        val end = timeText(schedule.endMinute % 1440)
        val overnight = if (schedule.endMinute > 1440) " next day" else ""
        val range = if (schedule.endDateEpochDay == null) {
            "from ${dateText(schedule.startDateEpochDay)} onward"
        } else {
            "${dateText(schedule.startDateEpochDay)} to ${dateText(schedule.endDateEpochDay)}"
        }
        return "$days, $start to $end$overnight, $range"
    }

    fun timeText(minute: Int): String {
        val normalized = ((minute % 1440) + 1440) % 1440
        val hour = normalized / 60
        val minutePart = normalized % 60
        return "%02d:%02d".format(hour, minutePart)
    }

    fun parseClock(value: String): Int {
        val parts = value.trim().split(":")
        require(parts.size == 2) { "Use HH:MM time format." }
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        require(hour in 0..23 && minute in 0..59) { "Time must be a real 24-hour clock value." }
        return hour * 60 + minute
    }

    fun dateText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()
}

