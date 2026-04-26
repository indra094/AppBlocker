package com.indrajeet.appblocker.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledApp(
    val label: String,
    val packageName: String
)

object InstalledAppScanner {
    fun scan(context: Context): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return apps
            .map {
                InstalledApp(
                    label = it.loadLabel(context.packageManager).toString(),
                    packageName = it.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

