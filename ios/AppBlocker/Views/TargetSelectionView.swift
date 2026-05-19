import FamilyControls
import SwiftUI

struct TargetSelectionView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    let bucket: BlockBucket
    @State private var selection: FamilyActivitySelection
    @State private var pickerPresented = false

    init(bucket: BlockBucket) {
        self.bucket = bucket
        _selection = State(initialValue: bucket.selection)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(bucket.name) {
                    SelectionSummaryLine(selection: selection)
                    Button("Open Apple picker") {
                        pickerPresented = true
                    }
                }
            }
            .navigationTitle("Targets")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        model.updateSelection(bucketID: bucket.id, selection: selection)
                        dismiss()
                    }
                }
            }
            .familyActivityPicker(
                title: "Edit targets",
                headerText: "Changes apply to future active windows for this bucket.",
                footerText: "To stay privacy-safe, iOS does not reveal app names back to AppBlocker.",
                isPresented: $pickerPresented,
                selection: $selection
            )
        }
    }
}
