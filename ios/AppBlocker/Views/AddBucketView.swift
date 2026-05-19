import FamilyControls
import SwiftUI

struct AddBucketView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    @State private var name = ""
    @State private var selection = FamilyActivitySelection()
    @State private var pickerPresented = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Bucket") {
                    TextField("Morning focus", text: $name)
                }

                Section("Targets") {
                    SelectionSummaryLine(selection: selection)
                    Button("Choose apps, categories, and websites") {
                        pickerPresented = true
                    }
                }
            }
            .navigationTitle("Add bucket")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        model.addBucket(name: name, selection: selection)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .familyActivityPicker(
                title: "Choose targets",
                headerText: "Select the apps, categories, and websites this bucket should block.",
                footerText: "Apple keeps selected targets private by exposing only opaque Screen Time tokens.",
                isPresented: $pickerPresented,
                selection: $selection
            )
        }
    }
}

struct SelectionSummaryLine: View {
    let selection: FamilyActivitySelection

    var body: some View {
        Text("\(selection.applicationTokens.count) apps, \(selection.categoryTokens.count) categories, \(selection.webDomainTokens.count) websites")
            .foregroundStyle(.secondary)
    }
}
