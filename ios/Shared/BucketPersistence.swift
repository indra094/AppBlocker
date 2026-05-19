import Foundation

enum BucketPersistence {
    static func loadBuckets() throws -> [BlockBucket] {
        let url = try AppGroup.bucketFileURL()
        guard FileManager.default.fileExists(atPath: url.path) else {
            return []
        }

        let data = try Data(contentsOf: url)
        return try JSONDecoder.appBlocker.decode([BlockBucket].self, from: data)
    }

    static func saveBuckets(_ buckets: [BlockBucket]) throws {
        let url = try AppGroup.bucketFileURL()
        let data = try JSONEncoder.appBlocker.encode(buckets)
        try data.write(to: url, options: [.atomic, .completeFileProtectionUnlessOpen])
    }
}

extension JSONDecoder {
    static var appBlocker: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}

extension JSONEncoder {
    static var appBlocker: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }
}
