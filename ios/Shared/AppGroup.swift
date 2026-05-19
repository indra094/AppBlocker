import Foundation

enum AppGroup {
    static let identifier = "group.com.indrajeet.appblocker"
    static let bucketFileName = "buckets.json"

    static func bucketFileURL() throws -> URL {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: identifier
        ) else {
            throw AppBlockerError.appGroupUnavailable
        }

        return containerURL.appendingPathComponent(bucketFileName, isDirectory: false)
    }
}

enum AppBlockerError: LocalizedError {
    case appGroupUnavailable
    case schedulePolicyViolation(String)

    var errorDescription: String? {
        switch self {
        case .appGroupUnavailable:
            return "The shared App Group container is not available. Check the App Group entitlement."
        case .schedulePolicyViolation(let message):
            return message
        }
    }
}
