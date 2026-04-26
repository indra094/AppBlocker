package com.indrajeet.appblocker.util

import java.time.DayOfWeek

object DayMask {
    fun fromDays(days: Set<DayOfWeek>): Int {
        return days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }
    }

    fun toDays(mask: Int): List<DayOfWeek> {
        return DayOfWeek.values().filter { day ->
            mask and (1 shl (day.value - 1)) != 0
        }
    }
}

