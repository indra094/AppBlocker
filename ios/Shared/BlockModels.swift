import FamilyControls
import Foundation

struct BlockBucket: Identifiable, Codable, Equatable {
    var id: UUID
    var name: String
    var selection: FamilyActivitySelection
    var schedules: [BlockSchedule]
    var createdAt: Date

    init(
        id: UUID = UUID(),
        name: String,
        selection: FamilyActivitySelection = FamilyActivitySelection(),
        schedules: [BlockSchedule] = [],
        createdAt: Date = Date()
    ) {
        self.id = id
        self.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        self.selection = selection
        self.schedules = schedules
        self.createdAt = createdAt
    }
}

struct BlockSchedule: Identifiable, Codable, Equatable {
    var id: UUID
    var label: String
    var weekdays: Set<Int>
    var startMinute: Int
    var endMinute: Int
    var startDate: Date
    var endDate: Date?
    var createdAt: Date
    var lastExpandedAt: Date

    init(
        id: UUID = UUID(),
        label: String,
        weekdays: Set<Int>,
        startMinute: Int,
        endMinute: Int,
        startDate: Date,
        endDate: Date?,
        createdAt: Date = Date(),
        lastExpandedAt: Date = Date()
    ) {
        self.id = id
        self.label = label.trimmingCharacters(in: .whitespacesAndNewlines)
        self.weekdays = weekdays
        self.startMinute = startMinute
        self.endMinute = endMinute
        self.startDate = startDate
        self.endDate = endDate
        self.createdAt = createdAt
        self.lastExpandedAt = lastExpandedAt
    }

    var endsNextDay: Bool {
        endMinute > 1_440
    }

    func isActive(at date: Date, calendar: Calendar = .current) -> Bool {
        let dayStart = calendar.startOfDay(for: date)
        let minuteOfDay = calendar.component(.hour, from: date) * 60 +
            calendar.component(.minute, from: date)

        if endMinute <= 1_440 {
            return dateIsInRange(dayStart, calendar: calendar) &&
                weekdays.contains(calendar.component(.weekday, from: date)) &&
                minuteOfDay >= startMinute &&
                minuteOfDay < endMinute
        }

        if minuteOfDay >= startMinute {
            return dateIsInRange(dayStart, calendar: calendar) &&
                weekdays.contains(calendar.component(.weekday, from: date))
        }

        let carryEndMinute = endMinute - 1_440
        guard minuteOfDay < carryEndMinute,
              let previousDay = calendar.date(byAdding: .day, value: -1, to: dayStart)
        else {
            return false
        }

        return dateIsInRange(previousDay, calendar: calendar) &&
            weekdays.contains(calendar.component(.weekday, from: previousDay))
    }

    private func dateIsInRange(_ dayStart: Date, calendar: Calendar) -> Bool {
        let normalizedStart = calendar.startOfDay(for: startDate)
        let normalizedEnd = endDate.map { calendar.startOfDay(for: $0) }

        return dayStart >= normalizedStart &&
            (normalizedEnd == nil || dayStart <= normalizedEnd!)
    }
}

enum WeekdayOption: Int, CaseIterable, Identifiable {
    case sunday = 1
    case monday = 2
    case tuesday = 3
    case wednesday = 4
    case thursday = 5
    case friday = 6
    case saturday = 7

    var id: Int { rawValue }

    var shortTitle: String {
        switch self {
        case .sunday: return "Sun"
        case .monday: return "Mon"
        case .tuesday: return "Tue"
        case .wednesday: return "Wed"
        case .thursday: return "Thu"
        case .friday: return "Fri"
        case .saturday: return "Sat"
        }
    }
}
