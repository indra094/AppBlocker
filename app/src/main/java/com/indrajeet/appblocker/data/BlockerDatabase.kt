package com.indrajeet.appblocker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BlockBucketEntity::class, BlockTargetEntity::class, BlockScheduleEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(BlockerTypeConverters::class)
abstract class BlockerDatabase : RoomDatabase() {
    abstract fun blockBucketDao(): BlockBucketDao
    abstract fun blockTargetDao(): BlockTargetDao
    abstract fun blockScheduleDao(): BlockScheduleDao
}
