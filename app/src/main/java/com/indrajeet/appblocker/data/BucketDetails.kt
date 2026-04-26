package com.indrajeet.appblocker.data

data class BucketDetails(
    val bucket: BlockBucketEntity,
    val targets: List<BlockTargetEntity>,
    val schedules: List<BlockScheduleEntity>
) {
    val blockedApps: List<BlockTargetEntity>
        get() = targets.filter { it.type == BlockTargetType.APP }

    val blockedSites: List<BlockTargetEntity>
        get() = targets.filter { it.type == BlockTargetType.WEBSITE }
}

