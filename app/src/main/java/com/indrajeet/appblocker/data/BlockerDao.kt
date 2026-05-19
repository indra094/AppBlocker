package com.indrajeet.appblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

class BlockerTypeConverters {
    @TypeConverter
    fun fromTargetType(type: BlockTargetType): String = type.name

    @TypeConverter
    fun toTargetType(value: String): BlockTargetType = BlockTargetType.valueOf(value)
}

@Dao
interface BlockBucketDao {
    @Query("SELECT * FROM block_buckets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockBucketEntity>>

    @Query("SELECT * FROM block_buckets WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<BlockBucketEntity>

    @Query("SELECT * FROM block_buckets WHERE name IN (:names)")
    suspend fun getByNames(names: List<String>): List<BlockBucketEntity>

    @Insert
    suspend fun insert(bucket: BlockBucketEntity): Long

    @Query("DELETE FROM block_buckets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int
}

@Dao
interface BlockTargetDao {
    @Query("SELECT * FROM block_targets ORDER BY displayName ASC")
    fun observeAll(): Flow<List<BlockTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(target: BlockTargetEntity): Long
}

@Dao
interface BlockScheduleDao {
    @Query("SELECT * FROM block_schedules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockScheduleEntity>>

    @Query("SELECT * FROM block_schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BlockScheduleEntity?

    @Insert
    suspend fun insert(schedule: BlockScheduleEntity): Long

    @Update
    suspend fun update(schedule: BlockScheduleEntity)
}
