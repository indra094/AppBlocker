import SwiftUI

struct ScheduleEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel

    let bucket: BlockBucket
    let existing: BlockSchedule?

    @State private var label: String
    @State private var weekdays: Set<Int>
    @State private var startTime: Date
    @State private var endTime: Date
    @State private var endsNextDay: Bool
    @State private var startDate: Date
    @State private var hasEndDate: Bool
    @State private var endDate: Date

    init(bucket: BlockBucket, existing: BlockSchedule? = nil) {
        self.bucket = bucket
        self.existing = existing

        let defaultStart = Self.timeDate(minutes: 390)
        let defaultEnd = Self.timeDate(minutes: 540)
        _label = State(initialValue: existing?.label ?? "")
        _weekdays = State(initialValue: existing?.weekdays ?? Set(WeekdayOption.allCases.map(\.rawValue)))
        _startTime = State(initialValue: existing.map { Self.timeDate(minutes: $0.startMinute) } ?? defaultStart)
        _endTime = State(initialValue: existing.map { Self.timeDate(minutes: $0.endMinute % 1_440) } ?? defaultEnd)
        _endsNextDay = State(initialValue: existing?.endsNextDay ?? false)
        _startDate = State(initialValue: existing?.startDate ?? Date())
        _hasEndDate = State(initialValue: existing?.endDate != nil)
        _endDate = State(initialValue: existing?.endDate ?? Date())
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Window") {
                    TextField("School morning", text: $label)
                    DatePicker("Start time", selection: $startTime, displayedComponents: .hourAndMinute)
                    DatePicker("End time", selection: $endTime, displayedComponents: .hourAndMinute)
                    Toggle("Ends next day", isOn: $endsNextDay)
                }

                Section("Days") {
                    weekdayGrid
                }

                Section("Date range") {
                    DatePicker("Start date", selection: $startDate, displayedComponents: .date)
                    Toggle("No end date", isOn: Binding(
                        get: { !hasEndDate },
                        set: { hasEndDate = !$0 }
                    ))
                    if hasEndDate {
                        DatePicker("End date", selection: $endDate, displayedComponents: .date)
                    }
                }

                if existing != nil {
                    Section {
                        Text("Existing windows are extend-only: you can add days, start earlier, end later, or extend the date range, but not weaken the rule.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle(existing == nil ? "Add window" : "Extend window")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(existing == nil ? "Add" : "Extend") {
                        save()
                    }
                }
            }
        }
    }

    private var weekdayGrid: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 64))], spacing: 8) {
            ForEach(WeekdayOption.allCases) { day in
                Button {
                    if weekdays.contains(day.rawValue) {
                        weekdays.remove(day.rawValue)
                    } else {
                        weekdays.insert(day.rawValue)
                    }
                } label: {
                    Text(day.shortTitle)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(weekdays.contains(day.rawValue) ? .accentColor : .secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func save() {
        var endMinute = Self.minuteOfDay(from: endTime)
        let startMinute = Self.minuteOfDay(from: startTime)
        if endsNextDay || endMinute <= startMinute {
            endMinute += 1_440
        }

        let draft = BlockSchedule(
            label: label,
            weekdays: weekdays,
            startMinute: startMinute,
            endMinute: endMinute,
            startDate: startDate,
            endDate: hasEndDate ? endDate : nil
        )

        do {
            if let existing {
                try SchedulePolicy.validateExtension(from: existing, to: draft)
                model.extendSchedule(bucketID: bucket.id, scheduleID: existing.id, draft: draft)
            } else {
                try SchedulePolicy.validateNew(draft)
                model.addSchedule(bucketID: bucket.id, schedule: draft)
            }
            dismiss()
        } catch {
            model.errorMessage = error.localizedDescription
        }
    }

    private static func minuteOfDay(from date: Date) -> Int {
        let calendar = Calendar.current
        return calendar.component(.hour, from: date) * 60 + calendar.component(.minute, from: date)
    }

    private static func timeDate(minutes: Int) -> Date {
        let calendar = Calendar.current
        return calendar.date(
            bySettingHour: (minutes % 1_440) / 60,
            minute: (minutes % 1_440) % 60,
            second: 0,
            of: Date()
        ) ?? Date()
    }
}
