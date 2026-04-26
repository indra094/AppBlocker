package com.indrajeet.appblocker

import android.app.Application
import androidx.room.Room
import com.indrajeet.appblocker.data.BlockerDatabase
import com.indrajeet.appblocker.data.BlockerRepository
import com.indrajeet.appblocker.data.SettingsGuardStore

class AppBlockerApplication : Application() {
    val settingsGuardStore: SettingsGuardStore by lazy {
        SettingsGuardStore(this)
    }

    val database: BlockerDatabase by lazy {
        Room.databaseBuilder(
            this,
            BlockerDatabase::class.java,
            "app-blocker.db"
        ).fallbackToDestructiveMigration().build()
    }

    val repository: BlockerRepository by lazy {
        BlockerRepository(
            bucketDao = database.blockBucketDao(),
            targetDao = database.blockTargetDao(),
            scheduleDao = database.blockScheduleDao(),
            settingsGuardStore = settingsGuardStore
        )
    }
}
