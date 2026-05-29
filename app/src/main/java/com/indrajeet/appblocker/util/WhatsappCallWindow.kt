package com.indrajeet.appblocker.util

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WhatsappCallWindowConfig(
    val startMinute: Int,
    val endMinute: Int
)

object WhatsappCallWindow {
    val pacificZoneId: ZoneId = ZoneId.of("America/Los_Angeles")
    private val displayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    const val DEFAULT_START_MINUTE: Int = 8 * 60
    const val DEFAULT_END_MINUTE: Int = 6 * 60 + 30

    val defaultConfig: WhatsappCallWindowConfig =
        WhatsappCallWindowConfig(
            startMinute = DEFAULT_START_MINUTE,
            endMinute = DEFAULT_END_MINUTE
        )

    fun isActive(now: ZonedDateTime): Boolean = isActive(now, defaultConfig)

    fun isActive(now: ZonedDateTime, config: WhatsappCallWindowConfig): Boolean {
        return isActive(now.withZoneSameInstant(pacificZoneId).toLocalTime(), config)
    }

    fun isActive(time: LocalTime): Boolean = isActive(time, defaultConfig)

    fun isActive(time: LocalTime, config: WhatsappCallWindowConfig): Boolean {
        val minuteOfDay = time.hour * 60 + time.minute
        return isActive(minuteOfDay, config)
    }

    fun shouldDisconnect(now: ZonedDateTime, config: WhatsappCallWindowConfig): Boolean {
        return !isActive(now, config)
    }

    fun shouldDisconnect(time: LocalTime, config: WhatsappCallWindowConfig): Boolean {
        return !isActive(time, config)
    }

    fun description(config: WhatsappCallWindowConfig): String {
        val daySuffix = if (crossesMidnight(config)) " the next day" else ""
        return "${displayTime(config.startMinute)} Pacific Time until ${displayTime(config.endMinute)} Pacific Time$daySuffix"
    }

    fun sanitize(
        startMinute: Int,
        endMinute: Int
    ): WhatsappCallWindowConfig {
        require(startMinute in 0 until MINUTES_PER_DAY) {
            "Start time must be between 00:00 and 23:59."
        }
        require(endMinute in 0 until MINUTES_PER_DAY) {
            "End time must be between 00:00 and 23:59."
        }
        require(startMinute != endMinute) {
            "Start and end time cannot be the same."
        }
        return WhatsappCallWindowConfig(
            startMinute = startMinute,
            endMinute = endMinute
        )
    }

    private fun isActive(
        minuteOfDay: Int,
        config: WhatsappCallWindowConfig
    ): Boolean {
        return if (crossesMidnight(config)) {
            minuteOfDay >= config.startMinute || minuteOfDay < config.endMinute
        } else {
            minuteOfDay in config.startMinute until config.endMinute
        }
    }

    private fun crossesMidnight(config: WhatsappCallWindowConfig): Boolean {
        return config.endMinute <= config.startMinute
    }

    private fun displayTime(minute: Int): String {
        val normalizedMinute = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return LocalTime.of(normalizedMinute / 60, normalizedMinute % 60).format(displayFormatter)
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
