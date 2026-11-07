package com.indrajeet.appblocker.util

import java.util.Locale

object CallNotificationHeuristics {
    private val positiveMarkers = listOf(
        "incoming voice call",
        "incoming video call",
        "voice call",
        "video call",
        "ongoing call",
        "ongoing video call",
        "ongoing voice call",
        "return to call",
        "return to video call",
        "return to voice call",
        "google meet",
        "ongoing meeting",
        "return to meeting",
        "in a meeting",
        "meeting",
        "ringing",
        "calling",
        "reconnecting"
    )

    private val negativeMarkers = listOf(
        "missed call",
        "call ended",
        "call declined",
        "meeting ended"
    )

    fun looksLikeCallNotification(
        category: String?,
        isOngoing: Boolean,
        fields: List<String?>
    ): Boolean {
        val normalizedFields = fields
            .filterNotNull()
            .map { it.lowercase(Locale.US) }

        if (normalizedFields.any { value -> negativeMarkers.any(value::contains) }) {
            return false
        }
        if (category.equals("call", ignoreCase = true)) {
            return true
        }

        val markerCount = positiveMarkers.count { marker ->
            normalizedFields.any { value -> value.contains(marker) }
        }
        return markerCount >= 2 || (isOngoing && markerCount >= 1)
    }
}
