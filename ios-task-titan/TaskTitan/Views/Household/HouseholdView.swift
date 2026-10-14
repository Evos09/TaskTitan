import SwiftUI

/// Flame-streak weeks that earn a confetti moment.
private let streakMilestones: Set<Int> = [4, 8, 12]

/// Cooperative household view: one shared goal, recap, events, memories. No rankings.
struct HouseholdView: View {
    @Environment(AppStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.tt) private var tt
    @State private var addingMember = false
    @State private var newMember = ""
    @State private var editingGoal = false
    @State private var goal = 40
    @State private var streakParty: Celebration?
    @State private var lastStreak: Int?

    var body: some View {
        let data = store.data
        let stats = store.stats
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    BrandMark(size: 44)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(data.householdName).font(tt.display(26)).foregroundStyle(tt.c.ink).lineLimit(1)
                        Text("A happier home together").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                    }
                    Spacer()
                    NavigationLink(value: Route.settings) {
                        Image(systemName: "gearshape.fill").font(.system(size: 19)).foregroundStyle(tt.c.ink).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Settings")
                }
                .padding(.horizontal, 4).padding(.top, 10)

                goalCard(data: data, stats: stats)
                recapCard(data: data, stats: stats)
                seasonCard(data: data, stats: stats)
                SectionTitle("House memories").padding(.horizontal, 4).padding(.top, 6)
                memories(data: data)
                SectionTitle(title: "Members") {
                    Button { newMember = ""; addingMember = true } label: {
                        Label("Add", systemImage: "person.badge.plus").font(tt.body(15, 800)).foregroundStyle(tt.c.tangerine).frame(minHeight: 44)
                    }
                }
                .padding(.horizontal, 4).padding(.top, 6)
                membersCard(data: data, stats: stats)
                challengeCard(data: data)
                if !data.isPro {
                    Button { router.showingPro = true } label: {
                        HStack(spacing: 10) {
                            Text("✦").font(.system(size: 22)).foregroundStyle(tt.c.sun)
                            Text("Manage more than one household with Pro").font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(tt.c.muted)
                        }
                        .padding(16)
                        .background(tt.c.surface, in: .rect(cornerRadius: 24))
                    }
                    .buttonStyle(PressScaleStyle())
                }
                ShareLink(item: progressText(data: data, stats: stats)) {
                    Label("Share progress card", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(CandyButtonStyle(fill: tt.c.bubblegumSoft, foreground: tt.c.bubblegum, height: 60))
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 28)
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .overlay {
            if let party = streakParty {
                CelebrationOverlay(celebration: party) { withAnimation { streakParty = nil } }
                    .transition(.opacity)
            }
        }
        .onAppear { if lastStreak == nil { lastStreak = stats.weekStreak } }
        .onChange(of: stats.weekStreak) { old, new in
            if streakMilestones.contains(new) && new > (lastStreak ?? old) {
                streakParty = Celebration(title: "\(new) weeks on fire!", message: "Your home hit its weekly goal \(new) weeks in a row. Amazing teamwork!")
            }
            lastStreak = new
        }
        .alert("Add a household member", isPresented: $addingMember) {
            TextField("Name", text: $newMember).textInputAutocapitalization(.words)
            Button("Add") { store.addMember(String(newMember.prefix(20))) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Great for shared devices and kids. Members only see the shared goal — never each other's stats.")
        }
        .sheet(isPresented: $editingGoal) {
            VStack(alignment: .leading, spacing: 16) {
                Text("Weekly household goal").font(tt.display(22)).foregroundStyle(tt.c.ink)
                Stepper("Chores per week: \(goal)", value: $goal, in: 5...300, step: 5)
                    .font(tt.body(16, 700)).foregroundStyle(tt.c.ink)
                Button("Save") { store.setWeeklyGoal(goal); editingGoal = false }
                    .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 54))
            }
            .padding(24)
            .presentationDetents([.height(240)])
            .background(tt.c.canvas.ignoresSafeArea())
        }
    }

    private func goalCard(data: AppData, stats: Stats) -> some View {
        let reward: String = if stats.householdWeek >= data.weeklyGoal {
            "Goal reached — the whole home earned it!"
        } else if data.weeklyGoal - stats.householdWeek <= 10 {
            "Almost at this week's goal"
        } else {
            "Every chore fills the bar for everyone"
        }
        return SoftCard(color: tt.c.skySoft) {
            HStack(spacing: 14) {
                ProgressRing(value: stats.householdWeek, goal: data.weeklyGoal).frame(width: 132, height: 132)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Our week: \(stats.householdWeek) of \(data.weeklyGoal) chores").font(tt.display(20)).foregroundStyle(tt.c.ink)
                    Text(reward).font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                    if stats.weekStreak > 0 {
                        HStack(spacing: 4) {
                            Image(systemName: "flame.fill").font(.system(size: 15)).symbolEffect(.pulse, isActive: !tt.reducedMotion)
                            Text("\(stats.weekStreak) week\(stats.weekStreak == 1 ? "" : "s") on fire").font(tt.body(12, 800))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(tt.c.sun, in: .rect(cornerRadius: 14))
                        .padding(.top, 8)
                        .accessibilityElement(children: .combine)
                    }
                    HStack(spacing: -6) {
                        ForEach(data.members.prefix(5)) { MemberBadge(member: $0, size: 44) }
                    }
                    .padding(.top, 10)
                    Button { goal = data.weeklyGoal; editingGoal = true } label: {
                        Label("Adjust goal", systemImage: "slider.horizontal.3").font(tt.body(12, 800)).foregroundStyle(tt.c.sky).frame(minHeight: 36)
                    }
                }
            }
        }
    }

    private func recapCard(data: AppData, stats: Stats) -> some View {
        NavigationLink(value: Route.recap) {
            SoftCard {
                HStack(spacing: 14) {
                    IconTile(symbol: "calendar", tint: tt.c.bubblegum, background: tt.c.bubblegumSoft, size: 64)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Weekly recap").font(tt.display(20)).foregroundStyle(tt.c.ink)
                        Label("Home stayed sparkling \(stats.sparklingDaysThisWeek) day\(stats.sparklingDaysThisWeek == 1 ? "" : "s")", systemImage: "sparkles")
                            .font(tt.body(14, 500)).foregroundStyle(tt.c.ink)
                        Label(stats.contributors.count >= data.members.count && data.members.count > 1 ? "Everyone pitched in" : "\(stats.contributors.count) helper\(stats.contributors.count == 1 ? "" : "s") so far", systemImage: "person.3.fill")
                            .font(tt.body(14, 500)).foregroundStyle(tt.c.ink)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(tt.c.muted)
                }
            }
        }
        .buttonStyle(PressScaleStyle())
    }

    private func seasonCard(data: AppData, stats: Stats) -> some View {
        let owned = data.profile.owned.contains(stats.season.decorId)
        return SoftCard(color: RGB(stats.season.tint).color) {
            HStack(spacing: 14) {
                Image(systemName: "leaf.fill").font(.system(size: 32)).foregroundStyle(tt.c.tangerine)
                    .frame(width: 64, height: 64).background(Color.white.opacity(0.6), in: .rect(cornerRadius: 20))
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(stats.season.name) event · \(stats.seasonDaysLeft) days left").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                    Text(stats.season.reward).font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                    FatProgress(progress: Double(stats.seasonProgress) / Double(stats.seasonGoal), color: tt.c.tangerine, track: Color.white.opacity(0.7), height: 10)
                        .padding(.top, 8)
                    Text("\(min(stats.seasonProgress, stats.seasonGoal)) of \(stats.seasonGoal) household chores").font(tt.body(11, 700)).foregroundStyle(tt.c.muted).padding(.top, 4)
                }
            }
            if stats.seasonUnlocked && !owned {
                Button("Claim \(Catalog.cosmetic(stats.season.decorId)?.name ?? "reward")") { store.claimSeason() }
                    .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 48))
                    .padding(.top, 10)
            } else if owned {
                Text("Unlocked — find it in Rewards › Decor").font(tt.body(12, 700)).foregroundStyle(tt.c.mint).padding(.top, 8)
            }
        }
    }

    @ViewBuilder private func memories(data: AppData) -> some View {
        if data.memories.isEmpty {
            SoftCard {
                Text("Finish every chore in a room to save a before & after memory here.").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
            }
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(Array(data.memories.prefix(8))) { m in
                        if let room = data.rooms.first(where: { $0.id == m.roomId }) {
                            MemoryCard(memory: m, room: room, before: true)
                            MemoryCard(memory: m, room: room, before: false)
                        }
                    }
                }
            }
        }
    }

    private func membersCard(data: AppData, stats: Stats) -> some View {
        SoftCard {
            ForEach(data.members) { m in
                HStack(spacing: 12) {
                    MemberBadge(member: m, size: 42)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(m.isYou ? "\(m.name) (you)" : m.name).font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                        Text(m.isYou ? "Your week: \(stats.yourWeek) chores · only you can see this" : "Contributing to the shared goal").font(tt.body(12)).foregroundStyle(tt.c.muted)
                    }
                    Spacer()
                    if !m.isYou {
                        Button { store.removeMember(m.id) } label: {
                            Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(tt.c.muted).frame(width: 44, height: 44)
                        }
                        .accessibilityLabel("Remove \(m.name)")
                    }
                }
                .padding(.vertical, 6)
            }
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Invite code").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
                    Text(data.inviteCode.isEmpty ? "—" : data.inviteCode).font(tt.display(26)).tracking(4).foregroundStyle(tt.c.ink)
                        .textSelection(.enabled)
                }
                Spacer()
                ShareLink(item: "Join our home on Task Titan! Use invite code \(data.inviteCode) — https://tasktitan.app/join/\(data.inviteCode)") {
                    Label("Share link", systemImage: "square.and.arrow.up").font(tt.body(14, 800)).foregroundStyle(.white)
                        .padding(.horizontal, 14).frame(minHeight: 44)
                        .background(tt.c.tangerine, in: .rect(cornerRadius: 18))
                }
            }
            .padding(12)
            .background(tt.c.tangerineSoft, in: .rect(cornerRadius: 18))
            .padding(.top, 8)
        }
    }

    private func challengeCard(data: AppData) -> some View {
        SoftCard {
            Toggle(isOn: Binding(get: { data.settings.interHouseholdChallenge }, set: { v in store.updateSettings { $0.interHouseholdChallenge = v } })) {
                HStack(spacing: 12) {
                    Image(systemName: "lock.fill").foregroundStyle(tt.c.sky)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Friendly challenges with other homes").font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                        Text("Opt-in only, never inside your own home. Off by default.").font(tt.body(12)).foregroundStyle(tt.c.muted)
                    }
                }
            }
            .tint(tt.c.tangerine)
            .disabled(data.settings.ageGroup == "child")
            if data.settings.interHouseholdChallenge {
                Text("You're opted in. Household-vs-household sprints will appear here once another home shares a challenge code with you.")
                    .font(tt.body(12)).foregroundStyle(tt.c.muted).padding(.top, 8)
            }
        }
    }

    private func progressText(data: AppData, stats: Stats) -> String {
        "🏠 \(data.householdName) on Task Titan\n" +
            "This week: \(stats.householdWeek) of \(data.weeklyGoal) chores together\n" +
            "Sparkling days: \(stats.sparklingDaysThisWeek) · \(stats.season.name): \(min(stats.seasonProgress, stats.seasonGoal))/\(stats.seasonGoal)\n" +
            (stats.weekStreak > 0 ? "🔥 \(stats.weekStreak) weeks on fire\n" : "") +
            "Real chores, a happier 3D home ✨"
    }
}

private struct ProgressRing: View {
    let value: Int
    let goal: Int
    @Environment(\.tt) private var tt
    @State private var shown: Double = 0

    var body: some View {
        let p = min(max(Double(value) / Double(max(goal, 1)), 0), 1)
        ZStack {
            Circle().trim(from: 0, to: 0.75)
                .stroke(Color(hex: 0xCFE2EE), style: StrokeStyle(lineWidth: 16, lineCap: .round))
                .rotationEffect(.degrees(135))
            Circle().trim(from: 0, to: 0.75 * shown)
                .stroke(tt.c.tangerine, style: StrokeStyle(lineWidth: 16, lineCap: .round))
                .rotationEffect(.degrees(135))
            VStack(spacing: 0) {
                HStack(alignment: .lastTextBaseline, spacing: 2) {
                    Text("\(value)").font(tt.display(34)).foregroundStyle(tt.c.tangerine).monospacedDigit()
                        .contentTransition(.numericText())
                    Text("/ \(goal)").font(tt.display(16)).foregroundStyle(tt.c.ink)
                }
                Text("chores").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
            }
        }
        .padding(8)
        .onAppear { withAnimation(tt.reducedMotion ? nil : .easeOut(duration: 0.9)) { shown = p } }
        .onChange(of: p) { _, new in withAnimation(tt.reducedMotion ? nil : .spring) { shown = new } }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(value) of \(goal) chores this week")
    }
}

private struct MemoryCard: View {
    let memory: Memory
    let room: Room
    let before: Bool
    @Environment(\.tt) private var tt

    var body: some View {
        var solo = room
        solo.floor = 0; solo.x = 0; solo.y = 0
        let props = memory.messKinds.enumerated().map { PropSpec(choreId: "\(memory.id)_\($0.offset)", roomId: solo.id, kind: $0.element) }
        return VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                before ? Color(hex: 0xF4EDE1) : Color(hex: 0xE6F5FF)
                House3DView(
                    scene: HouseScene(rooms: [solo], topFloor: 0, props: props, pendingIds: before ? Set(props.map(\.choreId)) : [],
                                      roomCondition: before ? [:] : [solo.id: 100]),
                    interactive: false, animated: false,
                    description: "\(room.name) \(before ? "before" : "after")"
                )
                if !before {
                    Text("✦").font(.system(size: 12)).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(tt.c.mint, in: .capsule).padding(8)
                }
            }
            .frame(height: 120)
            Text(before ? "\(memory.roomName): Before" : "After · \(DateUtil.label(memory.epochDay))")
                .font(tt.body(14, 800)).foregroundStyle(tt.c.ink).lineLimit(1)
                .padding(.horizontal, 12).padding(.vertical, 10)
        }
        .frame(width: 150)
        .background(tt.c.surface)
        .clipShape(.rect(cornerRadius: 22))
    }
}
