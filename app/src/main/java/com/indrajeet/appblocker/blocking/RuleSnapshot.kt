package com.indrajeet.appblocker.blocking

import com.indrajeet.appblocker.data.BlockScheduleEntity
import com.indrajeet.appblocker.data.BucketDetails
import com.indrajeet.appblocker.data.BlockTargetType

data class BucketRule(
    val bucketId: Long,
    val bucketName: String,
    val blockedPackages: Set<String>,
    val blockedHosts: Set<String>,
    val schedules: List<BlockScheduleEntity>
)

data class RuleSnapshot(val buckets: List<BucketRule>) {
    companion object {
        fun fromBucketDetails(bucketDetails: List<BucketDetails>): RuleSnapshot {
            return RuleSnapshot(
                buckets = bucketDetails.map { detail ->
                    BucketRule(
                        bucketId = detail.bucket.id,
                        bucketName = detail.bucket.name,
                        blockedPackages = detail.targets
                            .asSequence()
                            .filter { it.type == BlockTargetType.APP }
                            .map { it.normalizedValue }
                            .toSet(),
                        blockedHosts = detail.targets
                            .asSequence()
                            .filter { it.type == BlockTargetType.WEBSITE }
                            .map { it.normalizedValue }
                            .toSet(),
                        schedules = detail.schedules
                    )
                }
            )
        }
    }
}
