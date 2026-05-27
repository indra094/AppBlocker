import FamilyControls
import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var buckets: [BlockBucket] = []
    @Published private(set) var authorizationStatus: AuthorizationStatus
    @Published var errorMessage: String?

    private let authorizationCenter = AuthorizationCenter.shared

    init() {
        authorizationStatus = authorizationCenter.authorizationStatus
        reload()
        ShieldApplicator.applyActiveShields()
    }

    func requestAuthorization() async {
        do {
            try await authorizationCenter.requestAuthorization(for: .individual)
            authorizationStatus = authorizationCenter.authorizationStatus
            try rescheduleAndRefreshShields()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func reload() {
        do {
            buckets = try BucketPersistence.loadBuckets().sorted { $0.createdAt > $1.createdAt }
        } catch {
            errorMessage = error.localizedDescription
            buckets = []
        }
    }

    func addBucket(name: String, selection: FamilyActivitySelection) {
        do {
            let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmedName.isEmpty else {
                throw AppBlockerError.schedulePolicyViolation("Bucket name is required.")
            }

            buckets.insert(BlockBucket(name: trimmedName, selection: selection), at: 0)
            try persistAndReschedule()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func updateSelection(bucketID: UUID, selection: FamilyActivitySelection) {
        guard let index = buckets.firstIndex(where: { $0.id == bucketID }) else {
            return
        }

        do {
            buckets[index].selection = selection
            try persistAndReschedule()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func addSchedule(bucketID: UUID, schedule: BlockSchedule) {
        do {
            try SchedulePolicy.validateNew(schedule)
            guard let index = buckets.firstIndex(where: { $0.id == bucketID }) else {
                return
            }

            buckets[index].schedules.append(schedule)
            try persistAndReschedule()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func extendSchedule(bucketID: UUID, scheduleID: UUID, draft: BlockSchedule) {
        do {
            guard let bucketIndex = buckets.firstIndex(where: { $0.id == bucketID }),
                  let scheduleIndex = buckets[bucketIndex].schedules.firstIndex(where: { $0.id == scheduleID })
            else {
                return
            }

            let existing = buckets[bucketIndex].schedules[scheduleIndex]
            try SchedulePolicy.validateExtension(from: existing, to: draft)
            buckets[bucketIndex].schedules[scheduleIndex] = BlockSchedule(
                id: existing.id,
                label: draft.label,
                weekdays: draft.weekdays,
                startMinute: draft.startMinute,
                endMinute: draft.endMinute,
                startDate: draft.startDate,
                endDate: draft.endDate,
                createdAt: existing.createdAt,
                lastExpandedAt: Date()
            )
            try persistAndReschedule()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteBuckets(ids: Set<UUID> = [], names: Set<String> = []) {
        let normalizedNames = Set(
            names
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        )
        guard !ids.isEmpty || !normalizedNames.isEmpty else {
            errorMessage = "Pick at least one bucket id or name."
            return
        }

        do {
            let updatedBuckets = buckets.filter { bucket in
                !ids.contains(bucket.id) && !normalizedNames.contains(bucket.name)
            }
            try replaceBuckets(with: updatedBuckets)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func resetSchedules(ids: Set<UUID> = [], names: Set<String> = []) {
        let normalizedNames = Set(
            names
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        )
        guard !ids.isEmpty || !normalizedNames.isEmpty else {
            errorMessage = "Pick at least one bucket id or name."
            return
        }

        do {
            let updatedBuckets = buckets.map { bucket in
                guard ids.contains(bucket.id) || normalizedNames.contains(bucket.name) else {
                    return bucket
                }

                var updatedBucket = bucket
                updatedBucket.schedules = []
                return updatedBucket
            }
            try replaceBuckets(with: updatedBuckets)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func exportDocument() throws -> BucketTransferDocument {
        let data = try JSONEncoder.appBlocker.encode(buckets)
        return BucketTransferDocument(data: data)
    }

    func importBuckets(from data: Data) {
        do {
            let importedBuckets = try JSONDecoder.appBlocker.decode([BlockBucket].self, from: data)
            try replaceBuckets(with: importedBuckets)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func persistAndReschedule() throws {
        try BucketPersistence.saveBuckets(buckets)
        try rescheduleAndRefreshShields()
    }

    private func replaceBuckets(with updatedBuckets: [BlockBucket]) throws {
        buckets = updatedBuckets.sorted { $0.createdAt > $1.createdAt }
        try persistAndReschedule()
    }

    private func rescheduleAndRefreshShields() throws {
        try DeviceActivityScheduler.rescheduleMonitoring(for: buckets)
        ShieldApplicator.applyActiveShields()
    }
}
