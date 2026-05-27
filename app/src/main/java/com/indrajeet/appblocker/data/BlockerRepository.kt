package com.indrajeet.appblocker.data

import com.indrajeet.appblocker.blocking.RuleSnapshot
import com.indrajeet.appblocker.util.HostNormalizer
import com.indrajeet.appblocker.util.WhatsappCallWindowConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class ScheduleDraft(
    val label: String,
    val daysOfWeekMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long?
)

class PolicyViolationException(message: String) : IllegalArgumentException(message)

data class BucketDeletionResult(
    val deletedBuckets: List<BlockBucketEntity>,
    val missingIds: List<Long>,
    val missingNames: List<String>
)

class BlockerRepository(
    private val bucketDao: BlockBucketDao,
    private val targetDao: BlockTargetDao,
    private val scheduleDao: BlockScheduleDao,
    private val settingsGuardStore: SettingsGuardStore
) {
    fun observeBuckets(): Flow<List<BlockBucketEntity>> = bucketDao.observeAll()

    fun observeBucketDetails(): Flow<List<BucketDetails>> {
        return combine(
            observeBuckets(),
            targetDao.observeAll(),
            scheduleDao.observeAll()
        ) { buckets, targets, schedules ->
            buckets.map { bucket ->
                BucketDetails(
                    bucket = bucket,
                    targets = targets.filter { it.bucketId == bucket.id },
                    schedules = schedules.filter { it.bucketId == bucket.id }
                )
            }
        }
    }

    fun observeRuleSnapshot(): Flow<RuleSnapshot> {
        return observeBucketDetails().map { bucketDetails ->
            RuleSnapshot.fromBucketDetails(bucketDetails)
        }
    }

    fun observeSettingsGuardEnabled(): Flow<Boolean> = settingsGuardStore.observeSettingsBlocked()

    fun isSettingsGuardEnabled(): Boolean = settingsGuardStore.isSettingsBlocked()

    fun setSettingsGuardEnabled(enabled: Boolean) {
        settingsGuardStore.setSettingsBlocked(enabled)
    }

    fun observeWhatsappCallWindow(): Flow<WhatsappCallWindowConfig> {
        return settingsGuardStore.observeWhatsappCallWindow()
    }

    fun getWhatsappCallWindow(): WhatsappCallWindowConfig {
        return settingsGuardStore.getWhatsappCallWindow()
    }

    fun setWhatsappCallWindow(
        startMinute: Int,
        endMinute: Int
    ) {
        settingsGuardStore.setWhatsappCallWindow(
            startMinute = startMinute,
            endMinute = endMinute
        )
    }

    suspend fun addBucket(name: String): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Bucket name is required." }
        return bucketDao.insert(BlockBucketEntity(name = normalizedName))
    }

    suspend fun deleteBuckets(
        ids: List<Long>,
        names: List<String>
    ): BucketDeletionResult {
        val normalizedIds = ids.distinct().filter { it > 0L }
        val normalizedNames = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        require(normalizedIds.isNotEmpty() || normalizedNames.isNotEmpty()) {
            "Provide at least one bucket id or bucket name."
        }

        val matchedById = if (normalizedIds.isEmpty()) {
            emptyList()
        } else {
            bucketDao.getByIds(normalizedIds)
        }
        val matchedByName = if (normalizedNames.isEmpty()) {
            emptyList()
        } else {
            bucketDao.getByNames(normalizedNames)
        }
        val matchedBuckets = (matchedById + matchedByName)
            .associateBy { it.id }
            .values
            .sortedByDescending { it.createdAt }

        if (matchedBuckets.isNotEmpty()) {
            bucketDao.deleteByIds(matchedBuckets.map { it.id })
        }

        val foundIds = matchedById.map { it.id }.toSet()
        val foundNames = matchedByName.map { it.name }.toSet()
        return BucketDeletionResult(
            deletedBuckets = matchedBuckets,
            missingIds = normalizedIds.filterNot(foundIds::contains),
            missingNames = normalizedNames.filterNot(foundNames::contains)
        )
    }

    suspend fun addAppTarget(bucketId: Long, packageName: String, label: String): Boolean {
        val normalized = packageName.trim()
        require(normalized.isNotBlank()) { "Package name is required." }
        return targetDao.insert(
            BlockTargetEntity(
                bucketId = bucketId,
                normalizedValue = normalized,
                displayName = label.ifBlank { normalized },
                type = BlockTargetType.APP
            )
        ) != -1L
    }

    suspend fun addWebsiteTarget(bucketId: Long, rawHost: String): Boolean {
        val host = HostNormalizer.normalize(rawHost)
        return targetDao.insert(
            BlockTargetEntity(
                bucketId = bucketId,
                normalizedValue = host,
                displayName = host,
                type = BlockTargetType.WEBSITE
            )
        ) != -1L
    }

    suspend fun addSchedule(bucketId: Long, draft: ScheduleDraft) {
        validateDraft(draft)
        scheduleDao.insert(
            BlockScheduleEntity(
                bucketId = bucketId,
                label = draft.label.trim(),
                daysOfWeekMask = draft.daysOfWeekMask,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                startDateEpochDay = draft.startDateEpochDay,
                endDateEpochDay = draft.endDateEpochDay
            )
        )
    }

    suspend fun extendSchedule(id: Long, draft: ScheduleDraft) {
        validateDraft(draft)
        val existing = scheduleDao.getById(id) ?: throw PolicyViolationException("Schedule not found.")

        if (draft.daysOfWeekMask and existing.daysOfWeekMask != existing.daysOfWeekMask) {
            throw PolicyViolationException("Days can only be added, not removed.")
        }
        if (draft.startMinute > existing.startMinute) {
            throw PolicyViolationException("Start time can only move earlier or stay the same.")
        }
        if (draft.endMinute < existing.endMinute) {
            throw PolicyViolationException("End time can only move later or stay the same.")
        }
        if (draft.startDateEpochDay > existing.startDateEpochDay) {
            throw PolicyViolationException("Start date can only extend earlier or stay the same.")
        }
        when {
            existing.endDateEpochDay == null && draft.endDateEpochDay != null ->
                throw PolicyViolationException("A forever schedule cannot be shortened to a fixed end date.")
            existing.endDateEpochDay != null && draft.endDateEpochDay != null &&
                draft.endDateEpochDay < existing.endDateEpochDay ->
                throw PolicyViolationException("End date can only move later or stay the same.")
        }

        scheduleDao.update(
            existing.copy(
                label = draft.label.trim(),
                daysOfWeekMask = draft.daysOfWeekMask,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                startDateEpochDay = draft.startDateEpochDay,
                endDateEpochDay = draft.endDateEpochDay,
                lastExpandedAt = System.currentTimeMillis()
            )
        )
    }

    private fun validateDraft(draft: ScheduleDraft) {
        require(draft.label.isNotBlank()) { "Schedule label is required." }
        require(draft.daysOfWeekMask != 0) { "Pick at least one day." }
        require(draft.startMinute in 0..1439) { "Start time must be within the day." }
        require(draft.endMinute > draft.startMinute) { "End time must be after start time." }
        require(draft.endMinute <= draft.startMinute + 1440) {
            "Blocks can be at most 24 hours long."
        }
        require(
            draft.endDateEpochDay == null || draft.endDateEpochDay >= draft.startDateEpochDay
        ) { "End date must be on or after start date." }
    }
}
