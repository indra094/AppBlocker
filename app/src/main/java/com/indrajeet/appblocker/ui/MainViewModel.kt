package com.indrajeet.appblocker.ui

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.indrajeet.appblocker.AppBlockerApplication
import com.indrajeet.appblocker.admin.BlockDeviceAdminReceiver
import com.indrajeet.appblocker.data.BlockBucketEntity
import com.indrajeet.appblocker.data.BlockScheduleEntity
import com.indrajeet.appblocker.data.BucketDetails
import com.indrajeet.appblocker.data.PolicyViolationException
import com.indrajeet.appblocker.data.ScheduleDraft
import com.indrajeet.appblocker.util.InstalledApp
import com.indrajeet.appblocker.util.InstalledAppScanner
import com.indrajeet.appblocker.util.ScreenTimeTracker
import com.indrajeet.appblocker.util.WeeklyUsageSummary
import com.indrajeet.appblocker.util.WhatsappCallWindow
import com.indrajeet.appblocker.util.WhatsappCallWindowConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val buckets: List<BucketDetails> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val weeklyUsage: List<WeeklyUsageSummary> = emptyList(),
    val isDeviceAdminActive: Boolean = false,
    val isDeviceOwner: Boolean = false,
    val whatsappCallWindow: WhatsappCallWindowConfig = WhatsappCallWindow.defaultConfig
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AppBlockerApplication).repository
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val weeklyUsageState = MutableStateFlow<List<WeeklyUsageSummary>>(emptyList())
    private val deviceAdminState = MutableStateFlow(isCurrentAppDeviceAdmin(application))
    private val deviceOwnerState = MutableStateFlow(isCurrentAppDeviceOwner(application))

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeBucketDetails(),
        installedApps,
        weeklyUsageState,
        deviceAdminState,
        deviceOwnerState
    ) { buckets, apps, weeklyUsage, isDeviceAdminActive, isDeviceOwner ->
        HomeUiState(
            buckets = buckets,
            installedApps = apps,
            weeklyUsage = weeklyUsage,
            isDeviceAdminActive = isDeviceAdminActive,
            isDeviceOwner = isDeviceOwner
        )
    }.combine(repository.observeWhatsappCallWindow()) { state, whatsappCallWindow ->
        state.copy(whatsappCallWindow = whatsappCallWindow)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            installedApps.value = InstalledAppScanner.scan(getApplication())
            refreshWeeklyUsage()
        }
    }

    suspend fun addBucket(name: String): String {
        repository.addBucket(name)
        return "Bucket added."
    }

    suspend fun addBlockedApp(bucket: BlockBucketEntity, app: InstalledApp): String {
        val added = repository.addAppTarget(bucket.id, app.packageName, app.label)
        return if (added) {
            "${app.label} added to ${bucket.name}."
        } else {
            "${app.label} was already in ${bucket.name}."
        }
    }

    suspend fun addBlockedSite(bucket: BlockBucketEntity, host: String): String {
        val added = repository.addWebsiteTarget(bucket.id, host)
        return if (added) {
            "Website added to ${bucket.name}."
        } else {
            "That website was already in ${bucket.name}."
        }
    }

    suspend fun addSchedule(bucket: BlockBucketEntity, draft: ScheduleDraft): String {
        repository.addSchedule(bucket.id, draft)
        return "Blocking window added to ${bucket.name}."
    }

    suspend fun extendSchedule(id: Long, draft: ScheduleDraft): String {
        repository.extendSchedule(id, draft)
        return "Blocking window extended."
    }

    fun updateWhatsappCallWindow(
        startMinute: Int,
        endMinute: Int
    ): String {
        repository.setWhatsappCallWindow(
            startMinute = startMinute,
            endMinute = endMinute
        )
        return "WhatsApp call blocking window updated."
    }

    fun toUserMessage(error: Throwable): String {
        return when (error) {
            is PolicyViolationException -> error.message ?: "That change is not allowed."
            is IllegalArgumentException -> error.message ?: "Invalid input."
            else -> "Something went wrong."
        }
    }

    fun refreshManagementState() {
        val application = getApplication<Application>()
        deviceAdminState.value = isCurrentAppDeviceAdmin(application)
        deviceOwnerState.value = isCurrentAppDeviceOwner(application)
    }

    fun refreshWeeklyUsage() {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            weeklyUsageState.value = ScreenTimeTracker.loadWeeklyUsage(context = application)
        }
    }

    fun enforceSupervisedPolicies() {
        val application = getApplication<Application>()
        val manager = application.getSystemService(DevicePolicyManager::class.java) ?: return
        val admin = ComponentName(application, BlockDeviceAdminReceiver::class.java)
        if (!manager.isAdminActive(admin) || !manager.isDeviceOwnerApp(application.packageName)) {
            return
        }

        // Best-effort hardening when running as official device owner.
        runCatching { manager.setUninstallBlocked(admin, application.packageName, true) }
        runCatching { manager.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL) }
        runCatching { manager.clearUserRestriction(admin, USER_RESTRICTION_NO_CONFIG_SETTINGS) }
    }

    companion object {
        private const val USER_RESTRICTION_NO_CONFIG_SETTINGS = "no_config_settings"
    }

    private fun isCurrentAppDeviceAdmin(application: Application): Boolean {
        val manager = application.getSystemService(DevicePolicyManager::class.java) ?: return false
        val admin = ComponentName(application, BlockDeviceAdminReceiver::class.java)
        return manager.isAdminActive(admin)
    }

    private fun isCurrentAppDeviceOwner(application: Application): Boolean {
        val manager = application.getSystemService(DevicePolicyManager::class.java) ?: return false
        val admin = ComponentName(application, BlockDeviceAdminReceiver::class.java)
        return manager.isAdminActive(admin) && manager.isDeviceOwnerApp(application.packageName)
    }
}
