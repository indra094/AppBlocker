import FamilyControls
import Foundation
import ManagedSettings

enum ShieldApplicator {
    private static let store = ManagedSettingsStore(named: ManagedSettingsStore.Name("AppBlocker"))

    static func applyActiveShields(now: Date = Date(), calendar: Calendar = .current) {
        let buckets = (try? BucketPersistence.loadBuckets()) ?? []
        let activeBuckets = buckets.filter { bucket in
            bucket.schedules.contains { $0.isActive(at: now, calendar: calendar) }
        }

        applyShields(for: activeBuckets)
    }

    static func clearShields() {
        store.clearAllSettings()
    }

    private static func applyShields(for buckets: [BlockBucket]) {
        let selections = buckets.map(\.selection)
        let applicationTokens = selections.reduce(into: Set<ApplicationToken>()) {
            $0.formUnion($1.applicationTokens)
        }
        let categoryTokens = selections.reduce(into: Set<ActivityCategoryToken>()) {
            $0.formUnion($1.categoryTokens)
        }
        let webDomainTokens = selections.reduce(into: Set<WebDomainToken>()) {
            $0.formUnion($1.webDomainTokens)
        }

        store.shield.applications = applicationTokens.isEmpty ? nil : applicationTokens
        store.shield.applicationCategories = categoryTokens.isEmpty ? nil : .specific(categoryTokens)
        store.shield.webDomains = webDomainTokens.isEmpty ? nil : webDomainTokens
    }
}
