package com.indrajeet.appblocker.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsappCallNotificationHeuristicsTest {
    @Test
    fun `call category is treated as a whatsapp call notification`() {
        assertTrue(
            WhatsappCallNotificationHeuristics.looksLikeCallNotification(
                category = "call",
                isOngoing = false,
                fields = listOf("WhatsApp", "John Doe")
            )
        )
    }

    @Test
    fun `ongoing calling notification is treated as a whatsapp call notification`() {
        assertTrue(
            WhatsappCallNotificationHeuristics.looksLikeCallNotification(
                category = null,
                isOngoing = true,
                fields = listOf("WhatsApp", "Calling...", "Return to call")
            )
        )
    }

    @Test
    fun `minimized ongoing video call notification is treated as a whatsapp call notification`() {
        assertTrue(
            WhatsappCallNotificationHeuristics.looksLikeCallNotification(
                category = null,
                isOngoing = true,
                fields = listOf("WhatsApp", "Ongoing video call", "Hang up", "Return to call")
            )
        )
    }

    @Test
    fun `missed call notification is ignored`() {
        assertFalse(
            WhatsappCallNotificationHeuristics.looksLikeCallNotification(
                category = null,
                isOngoing = false,
                fields = listOf("WhatsApp", "Missed call")
            )
        )
    }

    @Test
    fun `ordinary chat notification is ignored`() {
        assertFalse(
            WhatsappCallNotificationHeuristics.looksLikeCallNotification(
                category = "msg",
                isOngoing = false,
                fields = listOf("WhatsApp", "2 new messages", "Hey, are you free?")
            )
        )
    }
}
