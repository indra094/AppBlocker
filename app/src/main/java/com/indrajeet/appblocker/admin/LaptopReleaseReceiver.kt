package com.indrajeet.appblocker.admin

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.indrajeet.appblocker.AppBlockerApplication
import com.indrajeet.appblocker.BuildConfig
import kotlinx.coroutines.runBlocking

class LaptopReleaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != ACTION_RELEASE_FOR_UNINSTALL &&
            action != ACTION_DELETE_BUCKETS &&
            action != ACTION_RESET_BUCKET_SCHEDULES
        ) {
            return
        }

        val providedToken = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        if (providedToken != BuildConfig.LAPTOP_RELEASE_TOKEN) {
            Log.w(TAG, "Rejecting laptop action with invalid token.")
            resultCode = RESULT_INVALID_TOKEN
            resultData = "Invalid token."
            return
        }

        when (action) {
            ACTION_RELEASE_FOR_UNINSTALL -> releaseForUninstall(context)
            ACTION_DELETE_BUCKETS -> deleteBuckets(context, intent)
            ACTION_RESET_BUCKET_SCHEDULES -> resetBucketSchedules(context, intent)
        }
    }

    private fun releaseForUninstall(context: Context) {
        resultCode = RESULT_OK
        resultData = "Uninstall release processed."

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

    private fun deleteBuckets(context: Context, intent: Intent) {
        val ids = parseIds(intent.getStringExtra(EXTRA_BUCKET_IDS))
        val names = parseDelimited(intent.getStringExtra(EXTRA_BUCKET_NAMES))
        if (ids.isEmpty() && names.isEmpty()) {
            resultCode = RESULT_INVALID_REQUEST
            resultData = "Provide at least one bucket id or exact bucket name."
            return
        }

        val repository = (context.applicationContext as AppBlockerApplication).repository
        runCatching {
            runBlocking {
                repository.deleteBuckets(ids = ids, names = names)
            }
        }.onSuccess { outcome ->
            val deletedSummary = if (outcome.deletedBuckets.isEmpty()) {
                "Deleted 0 buckets."
            } else {
                val labels = outcome.deletedBuckets.joinToString(", ") { "${it.name} (#${it.id})" }
                "Deleted ${outcome.deletedBuckets.size} bucket(s): $labels."
            }
            val missingIdSummary = if (outcome.missingIds.isEmpty()) {
                ""
            } else {
                " Missing ids: ${outcome.missingIds.joinToString(", ")}."
            }
            val missingNameSummary = if (outcome.missingNames.isEmpty()) {
                ""
            } else {
                " Missing exact names: ${outcome.missingNames.joinToString(", ")}."
            }
            resultCode = RESULT_OK
            resultData = deletedSummary + missingIdSummary + missingNameSummary
        }.onFailure {
            Log.e(TAG, "Failed to delete buckets from laptop action.", it)
            resultCode = RESULT_FAILURE
            resultData = it.message ?: "Bucket deletion failed."
        }
    }

    private fun resetBucketSchedules(context: Context, intent: Intent) {
        val ids = parseIds(intent.getStringExtra(EXTRA_BUCKET_IDS))
        val names = parseDelimited(intent.getStringExtra(EXTRA_BUCKET_NAMES))
        if (ids.isEmpty() && names.isEmpty()) {
            resultCode = RESULT_INVALID_REQUEST
            resultData = "Provide at least one bucket id or exact bucket name."
            return
        }

        val repository = (context.applicationContext as AppBlockerApplication).repository
        runCatching {
            runBlocking {
                repository.resetSchedules(ids = ids, names = names)
            }
        }.onSuccess { outcome ->
            val resetSummary = if (outcome.resetBuckets.isEmpty()) {
                "Reset timings for 0 buckets."
            } else {
                val labels = outcome.resetBuckets.joinToString(", ") {
                    "${it.bucket.name} (#${it.bucket.id}, removed ${it.deletedScheduleCount} schedule(s))"
                }
                "Reset timings for ${outcome.resetBuckets.size} bucket(s): $labels."
            }
            val missingIdSummary = if (outcome.missingIds.isEmpty()) {
                ""
            } else {
                " Missing ids: ${outcome.missingIds.joinToString(", ")}."
            }
            val missingNameSummary = if (outcome.missingNames.isEmpty()) {
                ""
            } else {
                " Missing exact names: ${outcome.missingNames.joinToString(", ")}."
            }
            resultCode = RESULT_OK
            resultData = resetSummary + missingIdSummary + missingNameSummary
        }.onFailure {
            Log.e(TAG, "Failed to reset bucket schedules from laptop action.", it)
            resultCode = RESULT_FAILURE
            resultData = it.message ?: "Bucket timing reset failed."
        }
    }

    private fun parseIds(value: String?): List<Long> {
        return parseDelimited(value)
            .mapNotNull { it.toLongOrNull() }
            .filter { it > 0L }
            .distinct()
    }

    private fun parseDelimited(value: String?): List<String> {
        return value
            .orEmpty()
            .split(EXTRA_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    companion object {
        const val ACTION_RELEASE_FOR_UNINSTALL =
            "com.indrajeet.appblocker.action.RELEASE_FOR_UNINSTALL"
        const val ACTION_DELETE_BUCKETS =
            "com.indrajeet.appblocker.action.DELETE_BUCKETS"
        const val ACTION_RESET_BUCKET_SCHEDULES =
            "com.indrajeet.appblocker.action.RESET_BUCKET_SCHEDULES"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_BUCKET_IDS = "bucket_ids"
        const val EXTRA_BUCKET_NAMES = "bucket_names"

        private const val EXTRA_DELIMITER = "|||"
        private const val USER_RESTRICTION_NO_CONFIG_SETTINGS = "no_config_settings"
        private const val RESULT_OK = 0
        private const val RESULT_INVALID_TOKEN = 1
        private const val RESULT_INVALID_REQUEST = 2
        private const val RESULT_FAILURE = 3
        private const val TAG = "LaptopReleaseReceiver"
    }
}
