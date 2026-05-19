import FamilyControls
import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showingAddBucket = false

    var body: some View {
        NavigationStack {
            List {
                setupSection
                bucketSection
            }
            .navigationTitle("AppBlocker")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showingAddBucket = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add bucket")
                }
            }
            .sheet(isPresented: $showingAddBucket) {
                AddBucketView()
            }
            .alert(
                "AppBlocker",
                isPresented: Binding(
                    get: { model.errorMessage != nil },
                    set: { if !$0 { model.errorMessage = nil } }
                )
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(model.errorMessage ?? "")
            }
        }
    }

    private var setupSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                Label("Screen Time authorization", systemImage: "shield.lefthalf.filled")
                    .font(.headline)
                Text(statusMessage)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Button {
                    Task { await model.requestAuthorization() }
                } label: {
                    Text(model.authorizationStatus == .approved ? "Refresh authorization" : "Allow Screen Time access")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(.vertical, 6)
        } footer: {
            Text("iOS blocking uses Apple's Screen Time APIs. Users select opaque app, category, and web-domain tokens from the system picker.")
        }
    }

    private var bucketSection: some View {
        Section("Buckets") {
            if model.buckets.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Label("No buckets yet", systemImage: "tray")
                        .font(.headline)
                    Text("Add a bucket, choose apps or websites, then add blocking windows.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 8)
            } else {
                ForEach(model.buckets) { bucket in
                    BucketRow(bucket: bucket)
                }
            }
        }
    }

    private var statusMessage: String {
        switch model.authorizationStatus {
        case .approved:
            return "Approved. Blocking windows can shield selected apps and websites."
        case .denied:
            return "Denied. Open Screen Time authorization again or check Settings."
        case .notDetermined:
            return "Not requested yet. AppBlocker needs permission before it can apply shields."
        @unknown default:
            return "Unknown authorization state."
        }
    }
}

private struct BucketRow: View {
    let bucket: BlockBucket
    @State private var editingTargets = false
    @State private var addingSchedule = false
    @State private var editingSchedule: BlockSchedule?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(bucket.name)
                    .font(.headline)
                SelectionSummary(selection: bucket.selection)
            }

            HStack {
                Button("Edit targets") {
                    editingTargets = true
                }
                .buttonStyle(.bordered)

                Button("Add window") {
                    addingSchedule = true
                }
                .buttonStyle(.bordered)
            }

            if bucket.schedules.isEmpty {
                Text("No blocking windows yet.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(bucket.schedules) { schedule in
                    Button {
                        editingSchedule = schedule
                    } label: {
                        ScheduleSummary(schedule: schedule)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, 6)
        .sheet(isPresented: $editingTargets) {
            TargetSelectionView(bucket: bucket)
        }
        .sheet(isPresented: $addingSchedule) {
            ScheduleEditorView(bucket: bucket)
        }
        .sheet(item: $editingSchedule) { schedule in
            ScheduleEditorView(bucket: bucket, existing: schedule)
        }
    }
}

private struct SelectionSummary: View {
    let selection: FamilyActivitySelection

    var body: some View {
        Text("\(selection.applicationTokens.count) apps, \(selection.categoryTokens.count) categories, \(selection.webDomainTokens.count) websites selected")
            .font(.subheadline)
            .foregroundStyle(.secondary)
    }
}

private struct ScheduleSummary: View {
    let schedule: BlockSchedule

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(schedule.label)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(schedule.isActive(at: Date()) ? "Active now" : "Scheduled")
                    .font(.caption)
                    .foregroundStyle(schedule.isActive(at: Date()) ? .green : .secondary)
            }
            Text(scheduleDescription)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(10)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private var scheduleDescription: String {
        let days = WeekdayOption.allCases
            .filter { schedule.weekdays.contains($0.rawValue) }
            .map(\.shortTitle)
            .joined(separator: ", ")
        return "\(days) \(timeText(schedule.startMinute))-\(timeText(schedule.endMinute % 1_440))\(schedule.endsNextDay ? " next day" : "")"
    }

    private func timeText(_ minute: Int) -> String {
        String(format: "%02d:%02d", minute / 60, minute % 60)
    }
}
