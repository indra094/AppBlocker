package com.indrajeet.appblocker.ui

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.indrajeet.appblocker.admin.BlockDeviceAdminReceiver
import com.indrajeet.appblocker.ui.theme.AppBlockerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refreshManagementState()
        viewModel.enforceSupervisedPolicies()
        setContent {
            AppBlockerTheme {
                HomeScreen(
                    viewModel = viewModel,
                    openAccessibilitySettings = {
                        launchSettingsSafely(
                            primary = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                            fallback = Intent(Settings.ACTION_SETTINGS),
                            failureMessage = "Couldn't open Accessibility settings on this device."
                        )
                    },
                    requestBatteryUnrestricted = {
                        val requestIntent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                        launchSettingsSafely(
                            primary = requestIntent,
                            fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                            failureMessage = "Couldn't open battery optimization settings."
                        )
                    },
                    requestDeviceAdmin = {
                        val admin = ComponentName(this, BlockDeviceAdminReceiver::class.java)
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "AppBlocker uses device admin as part of supervised setup. " +
                                    "For stronger uninstall protection, finish device-owner " +
                                    "provisioning from the laptop script on an eligible device."
                            )
                        }
                        launchSettingsSafely(
                            primary = intent,
                            fallback = Intent(Settings.ACTION_SECURITY_SETTINGS),
                            failureMessage = "Couldn't open device admin setup settings."
                        )
                    },
                    openDeviceAdminSettings = {
                        launchSettingsSafely(
                            primary = Intent(Settings.ACTION_SECURITY_SETTINGS),
                            fallback = Intent(Settings.ACTION_SETTINGS),
                            failureMessage = "Couldn't open security settings on this device."
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshManagementState()
        viewModel.enforceSupervisedPolicies()
    }

    private fun launchSettingsSafely(
        primary: Intent,
        fallback: Intent?,
        failureMessage: String
    ) {
        if (tryStartActivity(primary)) {
            return
        }
        if (fallback != null && tryStartActivity(fallback)) {
            return
        }
        Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show()
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}
