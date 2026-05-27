import FamilyControls
import SwiftUI
import UniformTypeIdentifiers

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showingAddBucket = false
    @State private var exportDocument = BucketTransferDocument(data: Data("[]".utf8))
    @State private var showingExporter = false
    @State private var showingImporter = false

    var body: some View {
        NavigationStack {
            List {
                setupSection
                bucketSection
            }
            .navigationTitle("AppBlocker")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button("Export rules") {
                            exportRules()
                        }
                        Button("Import rules") {
                            showingImporter = true
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    .accessibilityLabel("Maintenance")
                }
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
            .fileExporter(
                isPresented: $showingExporter,
                document: exportDocument,
                contentType: .json,
                defaultFilename: "AppBlocker-buckets"
            ) { result in
                if case .failure(let error) = result {
                    model.errorMessage = error.localizedDescription
                }
            }
            .fileImporter(
                isPresented: $showingImporter,
                allowedContentTypes: [.json]
            ) { result in
                switch result {
                case .success(let url):
                    importRules(from: url)
                case .failure(let error):
                    model.errorMessage = error.localizedDescription
                }
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
                    BucketRow(
                        bucket: bucket,
                        onResetSchedules: {
                            model.resetSchedules(ids: Set([bucket.id]))
                        },
                        onDelete: {
                            model.deleteBuckets(ids: Set([bucket.id]))
                        }
                    )
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

    private func exportRules() {
        do {
            exportDocument = try model.exportDocument()
            showingExporter = true
        } catch {
            model.errorMessage = error.localizedDescription
        }
    }

    private func importRules(from url: URL) {
        let didAccessResource = url.startAccessingSecurityScopedResource()
        defer {
            if didAccessResource {
                url.stopAccessingSecurityScopedResource()
            }
        }

        do {
            let data = try Data(contentsOf: url)
            model.importBuckets(from: data)
        } catch {
            model.errorMessage = error.localizedDescription
        }
    }
}

private struct BucketRow: View {
    let bucket: BlockBucket
    let onResetSchedules: () -> Void
    let onDelete: () -> Void
    @State private var editingTargets = false
    @State private var addingSchedule = false
    @State private var editingSchedule: BlockSchedule?
    @State private var showingDeleteConfirmation = false
    @State private var showingResetConfirmation = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(bucket.name)
                        .font(.headline)
                    SelectionSummary(selection: bucket.selection)
                }
                Spacer()
                Menu {
                    Button("Reset windows", role: .destructive) {
                        showingResetConfirmation = true
                    }
                    Button("Delete bucket", role: .destructive) {
                        showingDeleteConfirmation = true
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .imageScale(.large)
                        .padding(.top, 2)
                }
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
        .confirmationDialog(
            "Reset all windows for \(bucket.name)?",
            isPresented: $showingResetConfirmation,
            titleVisibility: .visible
        ) {
            Button("Reset windows", role: .destructive) {
                onResetSchedules()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This clears every blocking window in the bucket but keeps the selected apps and websites.")
        }
        .confirmationDialog(
            "Delete \(bucket.name)?",
            isPresented: $showingDeleteConfirmation,
            titleVisibility: .visible
        ) {
            Button("Delete bucket", role: .destructive) {
                onDelete()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the bucket, its selected targets, and all of its windows.")
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
