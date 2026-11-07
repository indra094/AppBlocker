package com.indrajeet.appblocker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsGuardStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsBlockedState = MutableStateFlow(
        prefs.getBoolean(KEY_BLOCK_DEVICE_SETTINGS, false)
    )

    init {
        // Remove legacy call-only schedule preferences. Call enforcement now follows bucket rules.
        prefs.edit()
            .remove(KEY_WHATSAPP_CALL_WINDOW_START_MINUTE)
            .remove(KEY_WHATSAPP_CALL_WINDOW_END_MINUTE)
            .remove(KEY_WHATSAPP_CALL_WINDOW_CONFIGURED)
            .apply()
    }

    fun observeSettingsBlocked(): StateFlow<Boolean> = settingsBlockedState

    fun isSettingsBlocked(): Boolean = settingsBlockedState.value

    fun setSettingsBlocked(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_DEVICE_SETTINGS, enabled).apply()
        settingsBlockedState.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "appblocker_flags"
        private const val KEY_BLOCK_DEVICE_SETTINGS = "block_device_settings"
        private const val KEY_WHATSAPP_CALL_WINDOW_START_MINUTE =
            "whatsapp_call_window_start_minute"
        private const val KEY_WHATSAPP_CALL_WINDOW_END_MINUTE =
            "whatsapp_call_window_end_minute"
        private const val KEY_WHATSAPP_CALL_WINDOW_CONFIGURED =
            "whatsapp_call_window_configured"
    }
}
