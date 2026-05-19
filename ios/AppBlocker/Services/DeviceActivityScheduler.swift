import DeviceActivity
import Foundation

enum DeviceActivityScheduler {
    private static let center = DeviceActivityCenter()

    static func rescheduleMonitoring(for buckets: [BlockBucket]) throws {
        center.stopMonitoring()

        for bucket in buckets {
            for schedule in bucket.schedules {
                for weekday in schedule.weekdays.sorted() {
                    let name = DeviceActivityName(
                        "bucket-\(bucket.id.uuidString)-schedule-\(schedule.id.uuidString)-day-\(weekday)"
                    )
                    let deviceSchedule = DeviceActivitySchedule(
                        intervalStart: startComponents(for: schedule, weekday: weekday),
                        intervalEnd: endComponents(for: schedule, weekday: weekday),
                        repeats: true
                    )

                    try center.startMonitoring(name, during: deviceSchedule)
                }
            }
        }
    }

    private static func startComponents(for schedule: BlockSchedule, weekday: Int) -> DateComponents {
        DateComponents(
            calendar: Calendar.current,
            hour: schedule.startMinute / 60,
            minute: schedule.startMinute % 60,
            weekday: weekday
        )
    }

    private static func endComponents(for schedule: BlockSchedule, weekday: Int) -> DateComponents {
        let normalizedEnd = schedule.endMinute % 1_440
        let endWeekday = schedule.endMinute >= 1_440 ? nextWeekday(after: weekday) : weekday

        return DateComponents(
            calendar: Calendar.current,
            hour: normalizedEnd / 60,
            minute: normalizedEnd % 60,
            weekday: endWeekday
        )
    }

    private static func nextWeekday(after weekday: Int) -> Int {
        weekday == 7 ? 1 : weekday + 1
    }
}
