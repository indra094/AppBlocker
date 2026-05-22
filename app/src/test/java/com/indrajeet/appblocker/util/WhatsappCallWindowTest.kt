package com.indrajeet.appblocker.util

import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsappCallWindowTest {
    @Test
    fun `window is inactive before evening start`() {
        assertFalse(WhatsappCallWindow.isActive(LocalTime.of(20, 29)))
    }

    @Test
    fun `window is active from evening start through midnight`() {
        assertTrue(WhatsappCallWindow.isActive(LocalTime.of(20, 30)))
        assertTrue(WhatsappCallWindow.isActive(LocalTime.of(23, 59)))
    }

    @Test
    fun `window stays active after midnight until morning cutoff`() {
        assertTrue(WhatsappCallWindow.isActive(LocalTime.MIDNIGHT))
        assertTrue(WhatsappCallWindow.isActive(LocalTime.of(6, 29)))
    }

    @Test
    fun `window turns off at morning cutoff`() {
        assertFalse(WhatsappCallWindow.isActive(LocalTime.of(6, 30)))
    }
}
