package com.indrajeet.appblocker.util

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsappCallWindowTest {
    private val defaultConfig = WhatsappCallWindow.defaultConfig

    @Test
    fun `window is inactive before pacific morning start`() {
        assertFalse(WhatsappCallWindow.isActive(LocalTime.of(7, 59), defaultConfig))
    }

    @Test
    fun `window is active from pacific morning start through midnight`() {
        assertTrue(WhatsappCallWindow.isActive(LocalTime.of(8, 0), defaultConfig))
        assertTrue(WhatsappCallWindow.isActive(LocalTime.of(23, 59), defaultConfig))
    }

    @Test
    fun `window stays active after midnight until pacific morning cutoff`() {
        assertTrue(WhatsappCallWindow.isActive(LocalTime.MIDNIGHT, defaultConfig))
        assertTrue(WhatsappCallWindow.isActive(LocalTime.of(6, 29), defaultConfig))
    }

    @Test
    fun `window turns off at pacific morning cutoff`() {
        assertFalse(WhatsappCallWindow.isActive(LocalTime.of(6, 30), defaultConfig))
    }

    @Test
    fun `window can be configured for a same-day range`() {
        val config = WhatsappCallWindow.sanitize(
            startMinute = 9 * 60,
            endMinute = 17 * 60
        )

        assertFalse(WhatsappCallWindow.isActive(LocalTime.of(8, 59), config))
        assertTrue(WhatsappCallWindow.isActive(LocalTime.NOON, config))
        assertFalse(WhatsappCallWindow.isActive(LocalTime.of(17, 0), config))
    }

    @Test
    fun `zoned time is evaluated in pacific time`() {
        val kolkataTime = ZonedDateTime.of(
            2026,
            1,
            16,
            4,
            30,
            0,
            0,
            ZoneId.of("Asia/Kolkata")
        )

        assertTrue(WhatsappCallWindow.isActive(kolkataTime, defaultConfig))

        val kolkataAllowedTime = ZonedDateTime.of(
            2026,
            1,
            16,
            20,
            45,
            0,
            0,
            ZoneId.of("Asia/Kolkata")
        )

        assertFalse(WhatsappCallWindow.isActive(kolkataAllowedTime, defaultConfig))
    }
}
