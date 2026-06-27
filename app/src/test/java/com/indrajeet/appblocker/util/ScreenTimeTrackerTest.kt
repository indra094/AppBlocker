package com.indrajeet.appblocker.util

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeTrackerTest {
    @Test
    fun `foreground usage counts one active session`() {
        val totals = ScreenTimeTracker.calculateUsageFromEvents(
            events = listOf(
                event(1_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.example.one"),
                event(3_500L, UsageEvents.Event.ACTIVITY_PAUSED, "com.example.one")
            ),
            intervalStartMs = 0L,
            intervalEndMs = 5_000L
        )

        assertEquals(2_500L, totals.foregroundUsageMs)
    }

    @Test
    fun `foreground usage does not double count overlapping apps`() {
        val totals = ScreenTimeTracker.calculateUsageFromEvents(
            events = listOf(
                event(1_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.example.one"),
                event(2_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.example.two"),
                event(4_000L, UsageEvents.Event.ACTIVITY_PAUSED, "com.example.one"),
                event(5_000L, UsageEvents.Event.ACTIVITY_PAUSED, "com.example.two")
            ),
            intervalStartMs = 0L,
            intervalEndMs = 6_000L
        )

        assertEquals(4_000L, totals.foregroundUsageMs)
    }

    @Test
    fun `foreground usage pauses while screen is non interactive`() {
        val totals = ScreenTimeTracker.calculateUsageFromEvents(
            events = listOf(
                event(1_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.example.one"),
                event(2_500L, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
                event(4_000L, UsageEvents.Event.SCREEN_INTERACTIVE),
                event(5_000L, UsageEvents.Event.ACTIVITY_PAUSED, "com.example.one")
            ),
            intervalStartMs = 0L,
            intervalEndMs = 6_000L
        )

        assertEquals(2_500L, totals.foregroundUsageMs)
    }

    @Test
    fun `foreground state can be carried in from lookback events`() {
        val totals = ScreenTimeTracker.calculateUsageFromEvents(
            events = listOf(
                event(-1_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.example.one"),
                event(2_000L, UsageEvents.Event.ACTIVITY_PAUSED, "com.example.one")
            ),
            intervalStartMs = 0L,
            intervalEndMs = 5_000L
        )

        assertEquals(2_000L, totals.foregroundUsageMs)
    }

    @Test
    fun `aggregate fallback is clamped to interval duration`() {
        assertEquals(
            10_000L,
            ScreenTimeTracker.clampUsageToInterval(
                totalMs = 50_000L,
                intervalStartMs = 0L,
                intervalEndMs = 10_000L
            )
        )
        assertEquals(
            0L,
            ScreenTimeTracker.clampUsageToInterval(
                totalMs = -1L,
                intervalStartMs = 0L,
                intervalEndMs = 10_000L
            )
        )
    }

    private fun event(
        timestampMs: Long,
        eventType: Int,
        packageName: String? = null
    ): UsageTimelineEvent {
        return UsageTimelineEvent(
            timestampMs = timestampMs,
            eventType = eventType,
            packageName = packageName
        )
    }
}
