package com.indrajeet.appblocker.util

import java.net.URI

object HostNormalizer {
    fun normalize(input: String): String {
        return normalizeOrNull(input) ?: throw IllegalArgumentException("Enter a valid hostname.")
    }

    fun normalizeOrNull(input: String?): String? {
        if (input.isNullOrBlank()) {
            return null
        }

        val trimmed = input.trim().lowercase()
        val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
        val host = runCatching { URI(candidate).host }.getOrNull() ?: return null
        return host.removePrefix("www.")
    }
}

