import Observation
import SwiftUI

enum AppTab: Hashable { case home, chores, household, rewards }

/// Pushed destinations inside each tab's navigation stack.
enum Route: Hashable { case settings, recap }

/// Cross-screen navigation state: selected tab and full-screen presentations.
@Observable
final class AppRouter {
    var tab: AppTab = .home
    var editingHouse = false
    var showingPro = false
}
