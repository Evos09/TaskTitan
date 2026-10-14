import SwiftUI

struct SettingsView: View {
    @Environment(AppStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.tt) private var tt
    @State private var confirmDelete = false
    @State private var renaming = false
    @State private var newName = ""

    var body: some View {
        let data = store.data
        let s = data.settings
        let kid = s.ageGroup == "child"

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                group("Home") {
                    navRow("house.fill", "Edit house & floors", "Up to 3 floors, as many rooms as you like") { router.editingHouse = true }
                    divider
                    navRow("pencil", "Household name", data.householdName) { newName = data.householdName; renaming = true }
                    divider
                    toggleRow("bell.fill", "Gentle reminders", "A friendly nudge at each chore's scheduled time", s.reminders) { store.setReminders($0) }
                    if s.reminders {
                        divider
                        if store.remindersPaused {
                            navRow("play.fill", "Resume reminders", "Paused until \(DateUtil.label(s.reminderPausedUntil)) — resumes automatically anyway") { store.resumeReminders() }
                        } else {
                            navRow("pause.fill", "Pause for 1 week", "No nudges until \(DateUtil.label(DateUtil.today() + 7)), then they're back") { store.pauseRemindersForWeek() }
                        }
                    }
                }
                group("Accessibility") {
                    toggleRow("circle.lefthalf.filled", "High contrast", "Stronger text and colors", s.highContrast) { v in store.updateSettings { $0.highContrast = v } }
                    divider
                    toggleRow("figure.walk.motion", "Reduce motion", "Pauses 3D animations and confetti", s.reducedMotion) { v in store.updateSettings { $0.reducedMotion = v } }
                    divider
                    toggleRow("textformat.size", "Larger text", "Bumps text size across the app", s.largeText) { v in store.updateSettings { $0.largeText = v } }
                }
                group("What friends can see") {
                    if kid {
                        Text("Sharing is locked for under-13 accounts.").font(tt.body(12)).foregroundStyle(tt.c.muted).padding(.horizontal, 14).padding(.vertical, 6)
                    }
                    toggleRow("person.crop.circle", "My avatar", nil, s.shareAvatar, enabled: !kid) { v in store.updateSettings { $0.shareAvatar = v } }
                    divider
                    toggleRow("house", "My 3D house", nil, s.shareHouse, enabled: !kid) { v in store.updateSettings { $0.shareHouse = v } }
                    divider
                    toggleRow("chart.line.uptrend.xyaxis", "My progress & level", nil, s.shareProgress, enabled: !kid) { v in store.updateSettings { $0.shareProgress = v } }
                    divider
                    toggleRow("person.3.fill", "My chore activity", nil, s.shareActivity, enabled: !kid) { v in store.updateSettings { $0.shareActivity = v } }
                }
                group("Privacy & data") {
                    HStack(spacing: 12) {
                        IconTile(symbol: "checkmark.shield.fill", tint: tt.c.sky, background: tt.c.skySoft, size: 36)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("No address, camera or location").font(tt.body(16)).foregroundStyle(tt.c.ink)
                            Text("Task Titan never asks for these permissions. Data is AES-encrypted on this device.").font(tt.body(12)).foregroundStyle(tt.c.muted)
                        }
                    }
                    .padding(12)
                    divider
                    ShareLink(item: store.exportJSON(), subject: Text("Task Titan data export")) {
                        rowLabel("square.and.arrow.down", "Export my data", "Share a copy as JSON", tint: nil)
                    }
                    .buttonStyle(.plain)
                    divider
                    navRow("trash.fill", "Delete all my data", "Removes everything from this device", tint: tt.c.bubblegum) { confirmDelete = true }
                }
                group("Plan") {
                    navRow("crown.fill", data.isPro ? "Task Titan Pro" : "Upgrade to Pro", "Core chores are always free") { router.showingPro = true }
                }
                Text("Task Titan · v1.0 · Consent recorded for \(s.ageGroup == "child" ? "under 13 (parent approved)" : s.ageGroup == "teen" ? "13–17" : "18+")")
                    .font(tt.body(11, 700)).foregroundStyle(tt.c.muted)
                    .padding(16)
            }
            .padding(.horizontal, 12)
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(tt.c.canvas, for: .navigationBar)
        .alert("Delete everything?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) {
                router.tab = .home
                store.deleteEverything()
            }
            Button("Keep my data", role: .cancel) {}
        } message: {
            Text("Your house, chores, avatar and progress will be permanently erased from this device. This can't be undone.")
        }
        .alert("Household name", isPresented: $renaming) {
            TextField("Household name", text: $newName)
            Button("Save") { store.renameHousehold(String(newName.prefix(24))) }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var divider: some View { Divider().overlay(tt.c.outline) }

    private func group<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(tt.body(16, 800)).foregroundStyle(tt.c.muted).padding(.leading, 8).accessibilityAddTraits(.isHeader)
            VStack(spacing: 0) { content() }
                .padding(4)
                .background(tt.c.surface, in: .rect(cornerRadius: 28))
        }
        .padding(.top, 16)
    }

    private func rowLabel(_ symbol: String, _ title: String, _ subtitle: String, tint: Color?) -> some View {
        HStack(spacing: 12) {
            IconTile(symbol: symbol, tint: tint ?? tt.c.tangerine, background: tint != nil ? tt.c.bubblegumSoft : tt.c.tangerineSoft, size: 36)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(tt.body(16)).foregroundStyle(tint ?? tt.c.ink)
                Text(subtitle).font(tt.body(12)).foregroundStyle(tt.c.muted)
            }
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(tt.c.muted)
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private func navRow(_ symbol: String, _ title: String, _ subtitle: String, tint: Color? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) { rowLabel(symbol, title, subtitle, tint: tint) }
            .buttonStyle(.plain)
    }

    private func toggleRow(_ symbol: String, _ title: String, _ subtitle: String?, _ value: Bool, enabled: Bool = true, onChange: @escaping (Bool) -> Void) -> some View {
        Toggle(isOn: Binding(get: { value }, set: onChange)) {
            HStack(spacing: 12) {
                IconTile(symbol: symbol, tint: tt.c.tangerine, background: tt.c.tangerineSoft, size: 36)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(tt.body(16)).foregroundStyle(tt.c.ink)
                    if let subtitle { Text(subtitle).font(tt.body(12)).foregroundStyle(tt.c.muted) }
                }
            }
        }
        .tint(tt.c.tangerine)
        .disabled(!enabled)
        .padding(.horizontal, 12).padding(.vertical, 10)
    }
}
