package com.indrajeet.appblocker.util

import java.time.LocalTime
import java.time.ZonedDateTime

object WhatsappCallWindow {
    val start: LocalTime = LocalTime.of(20, 30)
    val endExclusive: LocalTime = LocalTime.of(6, 30)

    fun isActive(now: ZonedDateTime): Boolean = isActive(now.toLocalTime())

    fun isActive(time: LocalTime): Boolean {
        return !time.isBefore(start) || time.isBefore(endExclusive)
    }
}
