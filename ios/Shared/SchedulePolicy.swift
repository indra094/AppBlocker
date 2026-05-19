import Foundation

enum SchedulePolicy {
    static func validateNew(_ schedule: BlockSchedule) throws {
        guard !schedule.label.isEmpty else {
            throw AppBlockerError.schedulePolicyViolation("Schedule label is required.")
        }
        guard !schedule.weekdays.isEmpty else {
            throw AppBlockerError.schedulePolicyViolation("Pick at least one day.")
        }
        guard schedule.startMinute >= 0, schedule.startMinute < 1_440 else {
            throw AppBlockerError.schedulePolicyViolation("Start time must be within the day.")
        }
        guard schedule.endMinute > schedule.startMinute else {
            throw AppBlockerError.schedulePolicyViolation("End time must be after start time.")
        }
        guard schedule.endMinute <= schedule.startMinute + 1_440 else {
            throw AppBlockerError.schedulePolicyViolation("Blocks can be at most 24 hours long.")
        }
        if let endDate = schedule.endDate,
           Calendar.current.startOfDay(for: endDate) < Calendar.current.startOfDay(for: schedule.startDate) {
            throw AppBlockerError.schedulePolicyViolation("End date must be on or after start date.")
        }
    }

    static func validateExtension(from existing: BlockSchedule, to draft: BlockSchedule) throws {
        try validateNew(draft)

        guard draft.weekdays.isSuperset(of: existing.weekdays) else {
            throw AppBlockerError.schedulePolicyViolation("Days can only be added, not removed.")
        }
        guard draft.startMinute <= existing.startMinute else {
            throw AppBlockerError.schedulePolicyViolation("Start time can only move earlier or stay the same.")
        }
        guard draft.endMinute >= existing.endMinute else {
            throw AppBlockerError.schedulePolicyViolation("End time can only move later or stay the same.")
        }

        let calendar = Calendar.current
        guard calendar.startOfDay(for: draft.startDate) <= calendar.startOfDay(for: existing.startDate) else {
            throw AppBlockerError.schedulePolicyViolation("Start date can only extend earlier or stay the same.")
        }

        switch (existing.endDate, draft.endDate) {
        case (nil, .some):
            throw AppBlockerError.schedulePolicyViolation("A forever schedule cannot be shortened to a fixed end date.")
        case let (.some(existingEnd), .some(draftEnd))
            where calendar.startOfDay(for: draftEnd) < calendar.startOfDay(for: existingEnd):
            throw AppBlockerError.schedulePolicyViolation("End date can only move later or stay the same.")
        default:
            break
        }
    }
}
