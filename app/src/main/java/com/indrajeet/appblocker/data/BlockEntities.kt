package com.indrajeet.appblocker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BlockTargetType {
    APP,
    WEBSITE
}

@Entity(tableName = "block_buckets")
data class BlockBucketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "block_targets",
    foreignKeys = [
        ForeignKey(
            entity = BlockBucketEntity::class,
            parentColumns = ["id"],
            childColumns = ["bucketId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bucketId"]),
        Index(value = ["bucketId", "normalizedValue", "type"], unique = true)
    ]
)
data class BlockTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bucketId: Long,
    val normalizedValue: String,
    val displayName: String,
    val type: BlockTargetType,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "block_schedules",
    foreignKeys = [
        ForeignKey(
            entity = BlockBucketEntity::class,
            parentColumns = ["id"],
            childColumns = ["bucketId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bucketId"])]
)
data class BlockScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bucketId: Long,
    val label: String,
    val daysOfWeekMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long?,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExpandedAt: Long = System.currentTimeMillis()
)
