package com.indrajeet.appblocker.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    requestBatteryUnrestricted = {
                        val requestIntent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                        runCatching { startActivity(requestIntent) }
                            .onFailure {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
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
                        startActivity(intent)
                    },
                    openDeviceAdminSettings = {
                        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
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
}
