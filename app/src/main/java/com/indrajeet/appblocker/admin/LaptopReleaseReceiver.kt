package com.indrajeet.appblocker.admin

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.indrajeet.appblocker.BuildConfig

class LaptopReleaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RELEASE_FOR_UNINSTALL) {
            return
        }

        val providedToken = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        if (providedToken != BuildConfig.LAPTOP_RELEASE_TOKEN) {
            Log.w(TAG, "Rejecting uninstall release request with invalid token.")
            return
        }

        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return
        val admin = ComponentName(context, BlockDeviceAdminReceiver::class.java)
        val packageName = context.packageName

        if (!manager.isAdminActive(admin)) {
            Log.i(TAG, "Device admin already inactive.")
            return
        }

        // First, remove hardening policies so uninstall can proceed.
        runCatching { manager.setUninstallBlocked(admin, packageName, false) }
        runCatching { manager.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL) }
        runCatching { manager.clearUserRestriction(admin, USER_RESTRICTION_NO_CONFIG_SETTINGS) }

        if (manager.isDeviceOwnerApp(packageName)) {
            runCatching { manager.clearDeviceOwnerApp(packageName) }
                .onFailure { Log.e(TAG, "Failed to clear device-owner state.", it) }
        }

        runCatching { manager.removeActiveAdmin(admin) }
            .onFailure { Log.e(TAG, "Failed to remove active admin.", it) }
    }

    companion object {
        const val ACTION_RELEASE_FOR_UNINSTALL =
            "com.indrajeet.appblocker.action.RELEASE_FOR_UNINSTALL"
        const val EXTRA_TOKEN = "token"

        private const val USER_RESTRICTION_NO_CONFIG_SETTINGS = "no_config_settings"
        private const val TAG = "LaptopReleaseReceiver"
    }
}
