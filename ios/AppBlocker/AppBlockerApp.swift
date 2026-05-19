import SwiftUI

@main
struct AppBlockerApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(model)
        }
    }
}
