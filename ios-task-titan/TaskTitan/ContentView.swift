import SwiftUI

/// Root: consent → house setup → tabbed app, plus app-wide presentations.
struct ContentView: View {
    @Environment(AppStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.tt) private var tt

    var body: some View {
        @Bindable var router = router
        let s = store.data.settings
        ZStack {
            if !s.consentGiven {
                ConsentView().transition(.opacity)
            } else if !store.data.setupDone {
                HouseBuilderView(onboarding: true).transition(.opacity)
            } else {
                tabs
            }
            if let cel = store.celebration {
                CelebrationOverlay(celebration: cel) { withAnimation { store.celebration = nil } }
                    .id(cel.id)
                    .transition(.opacity)
                    .zIndex(10)
            }
        }
        .animation(tt.reducedMotion ? nil : .easeInOut(duration: 0.3), value: s.consentGiven)
        .animation(tt.reducedMotion ? nil : .easeInOut(duration: 0.3), value: store.data.setupDone)
        .fullScreenCover(isPresented: $router.editingHouse) {
            HouseBuilderView(onboarding: false, onDone: { router.editingHouse = false }, onClose: { router.editingHouse = false })
                .environment(\.tt, tt)
        }
        .sheet(isPresented: $router.showingPro) {
            ProView().environment(\.tt, tt)
        }
    }

    private var tabs: some View {
        @Bindable var router = router
        return TabView(selection: $router.tab) {
            Tab("Home", systemImage: "house.fill", value: AppTab.home) {
                stack { HomeView() }
            }
            Tab("Chores", systemImage: "checklist", value: AppTab.chores) {
                stack { ChoresView() }
            }
            Tab("Household", systemImage: "person.3.fill", value: AppTab.household) {
                stack { HouseholdView() }
            }
            Tab("Rewards", systemImage: "gift.fill", value: AppTab.rewards) {
                stack { RewardsView() }
            }
        }
        .tint(tt.c.tangerine)
        .sensoryFeedback(.selection, trigger: router.tab)
    }

    private func stack<V: View>(@ViewBuilder _ root: () -> V) -> some View {
        NavigationStack {
            root().navigationDestination(for: Route.self) { route in
                switch route {
                case .settings: SettingsView()
                case .recap: RecapView()
                }
            }
        }
    }
}
