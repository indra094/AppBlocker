package com.indrajeet.appblocker.data

import android.content.Context
import com.indrajeet.appblocker.util.WhatsappCallWindow
import com.indrajeet.appblocker.util.WhatsappCallWindowConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsGuardStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsBlockedState = MutableStateFlow(
        prefs.getBoolean(KEY_BLOCK_DEVICE_SETTINGS, false)
    )
    private val whatsappCallWindowState = MutableStateFlow(readWhatsappCallWindow())

    fun observeSettingsBlocked(): StateFlow<Boolean> = settingsBlockedState

    fun isSettingsBlocked(): Boolean = settingsBlockedState.value

    fun setSettingsBlocked(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_DEVICE_SETTINGS, enabled).apply()
        settingsBlockedState.value = enabled
    }

    fun observeWhatsappCallWindow(): StateFlow<WhatsappCallWindowConfig> = whatsappCallWindowState

    fun getWhatsappCallWindow(): WhatsappCallWindowConfig = whatsappCallWindowState.value

    fun setWhatsappCallWindow(
        startMinute: Int,
        endMinute: Int
    ) {
        val config = WhatsappCallWindow.sanitize(
            startMinute = startMinute,
            endMinute = endMinute
        )
        prefs.edit()
            .putInt(KEY_WHATSAPP_CALL_WINDOW_START_MINUTE, config.startMinute)
            .putInt(KEY_WHATSAPP_CALL_WINDOW_END_MINUTE, config.endMinute)
            .apply()
        whatsappCallWindowState.value = config
    }

    private fun readWhatsappCallWindow(): WhatsappCallWindowConfig {
        return WhatsappCallWindow.sanitize(
            startMinute = prefs.getInt(
                KEY_WHATSAPP_CALL_WINDOW_START_MINUTE,
                WhatsappCallWindow.DEFAULT_START_MINUTE
            ),
            endMinute = prefs.getInt(
                KEY_WHATSAPP_CALL_WINDOW_END_MINUTE,
                WhatsappCallWindow.DEFAULT_END_MINUTE
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "appblocker_flags"
        private const val KEY_BLOCK_DEVICE_SETTINGS = "block_device_settings"
        private const val KEY_WHATSAPP_CALL_WINDOW_START_MINUTE =
            "whatsapp_call_window_start_minute"
        private const val KEY_WHATSAPP_CALL_WINDOW_END_MINUTE =
            "whatsapp_call_window_end_minute"
    }
}
