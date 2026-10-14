import SwiftUI

@main
struct TaskTitanApp: App {
    @State private var store: AppStore
    @State private var router = AppRouter()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        AppFonts.register()
        ReminderService.shared.configure()
        _store = State(initialValue: AppStore())
    }

    var body: some Scene {
        WindowGroup {
            let s = store.data.settings
            ContentView()
                .environment(store)
                .environment(router)
                .environment(\.tt, Theme(c: s.highContrast ? .highContrast : .candy, reducedMotion: s.reducedMotion, largeText: s.largeText))
                .preferredColorScheme(.light)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { store.refreshDay() }
                }
        }
    }
}
