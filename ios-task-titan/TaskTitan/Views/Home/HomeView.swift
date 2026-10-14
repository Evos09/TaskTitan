import SwiftUI

struct HomeView: View {
    @Environment(AppStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.tt) private var tt

    @State private var floor = 0
    @State private var mode: CameraMode = .orbit
    @State private var labels = false
    @State private var showBell = false
    @State private var floorTick = 0

    var body: some View {
        let stats = store.stats
        let data = store.data
        let maxFloor = max(data.floors - 1, 0)
        let shownFloor = min(floor, maxFloor)

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header(confirmCount: data.completions.filter { !$0.confirmed }.count)
                HudStrip(stats: stats).padding(.horizontal, 12).padding(.top, 12)
                houseWindow(stats: stats, data: data, floor: shownFloor, maxFloor: maxFloor)
                    .padding(.horizontal, 12).padding(.top, 12)
                if stats.streak == 0 {
                    Text("Start a new streak today — any chore counts.")
                        .font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
                        .padding(.horizontal, 24).padding(.vertical, 6)
                } else {
                    Spacer().frame(height: 12)
                }
                goalCard(stats: stats, data: data).padding(.horizontal, 12)
                SectionTitle(title: "Today's chores") {
                    Button { router.tab = .chores } label: {
                        HStack(spacing: 2) {
                            Text("See all").font(tt.body(15, 800))
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold))
                        }
                        .foregroundStyle(tt.c.tangerine)
                        .frame(minHeight: 44)
                    }
                }
                .padding(.horizontal, 16).padding(.top, 18)
                questRow(stats: stats)
            }
            .padding(.bottom, 24)
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sensoryFeedback(.selection, trigger: floorTick)
        .onChange(of: store.avatarFocus?.at) { _, _ in
            if let f = store.avatarFocus, let room = data.rooms.first(where: { $0.id == f.roomId }) { floor = room.floor }
        }
        .sheet(isPresented: $showBell) { bellSheet.presentationDetents([.medium, .large]) }
    }

    private func header(confirmCount: Int) -> some View {
        HStack(spacing: 12) {
            BrandMark(size: 44)
            VStack(alignment: .leading, spacing: 0) {
                Text("Task Titan").font(tt.display(26)).foregroundStyle(tt.c.ink)
                Text("A happier home together").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
            }
            Spacer()
            Button { showBell = true } label: {
                Image(systemName: "bell.fill").font(.system(size: 19)).foregroundStyle(tt.c.ink)
                    .frame(width: 44, height: 44)
                    .overlay(alignment: .topTrailing) {
                        if confirmCount > 0 { Circle().fill(tt.c.bubblegum).frame(width: 9, height: 9).offset(x: -10, y: 10) }
                    }
            }
            .accessibilityLabel(confirmCount > 0 ? "Updates, \(confirmCount) waiting" : "Updates")
            NavigationLink(value: Route.settings) {
                Image(systemName: "gearshape.fill").font(.system(size: 19)).foregroundStyle(tt.c.ink).frame(width: 44, height: 44)
            }
            .accessibilityLabel("Settings")
        }
        .padding(.leading, 16).padding(.trailing, 6).padding(.top, 10)
    }

    private func status(_ stats: Stats) -> String {
        let name = stats.you?.name ?? "You"
        if let f = store.avatarFocus, Date().timeIntervalSince(f.at) < 4,
           let room = store.data.rooms.first(where: { $0.id == f.roomId }) {
            return "\(name) is celebrating in the \(room.name.lowercased())!"
        }
        if stats.houseCondition >= 85 { return "\(name) is relaxing in a sparkling home" }
        if stats.houseCondition >= 50 { return "\(name) is relaxing in a tidy home" }
        return "\(name) is resting — ready when you are"
    }

    private var skyGradient: LinearGradient {
        let hour = DateUtil.hourOfDay()
        let colors: [Color] = if hour < 6 || hour >= 20 {
            [Color(hex: 0x2C3A78), Color(hex: 0x5867B0), Color(hex: 0x8E8FD0)]
        } else if hour >= 17 {
            [Color(hex: 0xFFC9A3), Color(hex: 0xFFE3CC), Color(hex: 0xDDEFFF)]
        } else {
            [Color(hex: 0xCFEBFF), Color(hex: 0xE6F5FF), Color(hex: 0xF3FAFF)]
        }
        return LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)
    }

    private func houseWindow(stats: Stats, data: AppData, floor: Int, maxFloor: Int) -> some View {
        let props = stats.todayChores.map { PropSpec(choreId: $0.chore.id, roomId: $0.chore.roomId, kind: $0.chore.kind) }
        let pending = Set(stats.todayChores.filter { !$0.isDone }.map(\.chore.id))
        let statusText = status(stats)
        return ZStack {
            skyGradient
            House3DView(
                scene: HouseScene(
                    rooms: data.rooms, topFloor: floor, props: props, pendingIds: pending,
                    recentClean: store.recentClean, look: data.profile.look, tier: stats.tier,
                    decor: data.profile.equippedDecor, focus: store.avatarFocus, roomCondition: stats.roomCondition
                ),
                cameraMode: mode,
                showLabels: labels,
                description: "Your 3D home, floor \(floor + 1). \(statusText). House condition \(stats.houseCondition) percent."
            )
            VStack(spacing: 0) {
                HStack(alignment: .top, spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "house.fill").font(.system(size: 16)).foregroundStyle(tt.c.sky)
                        Text(statusText).font(tt.body(14, 700)).foregroundStyle(tt.c.ink).lineLimit(2)
                            .contentTransition(.opacity)
                            .animation(.easeInOut, value: statusText)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .background(Color.white.opacity(0.92), in: .rect(cornerRadius: 18))
                    Spacer(minLength: 0)
                    Button { labels.toggle() } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "sparkles").font(.system(size: 17))
                            VStack(alignment: .leading, spacing: 0) {
                                Text("House condition").font(tt.body(10, 700))
                                Text("\(GameRules.conditionLabel(stats.houseCondition)) \(stats.houseCondition)%").font(tt.body(14, 800)).monospacedDigit()
                            }
                        }
                        .foregroundStyle(labels ? .white : tt.c.ink)
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(labels ? tt.c.mint : tt.c.mintSoft, in: .rect(cornerRadius: 18))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("House condition \(stats.houseCondition) percent. Toggle room labels")
                    .accessibilityAddTraits(labels ? .isSelected : [])
                }
                .padding(12)
                Spacer()
                HStack(spacing: 6) {
                    ForEach(CameraMode.allCases) { m in
                        Button(m.rawValue) { mode = m }
                            .font(tt.body(13, 800))
                            .foregroundStyle(mode == m ? .white : tt.c.ink)
                            .padding(.horizontal, 11).frame(minHeight: 34)
                            .background(mode == m ? tt.c.sky : Color.white.opacity(0.9), in: .rect(cornerRadius: 14))
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(mode == m ? .isSelected : [])
                    }
                    Spacer(minLength: 0)
                    HStack(spacing: 2) {
                        Image(systemName: "flame.fill").font(.system(size: 15)).foregroundStyle(tt.c.tangerine)
                        Text(stats.streak > 0 ? "\(stats.streak)" : "New").font(tt.body(14, 800)).foregroundStyle(tt.c.ink).monospacedDigit()
                    }
                    .padding(.horizontal, 8).frame(minHeight: 34)
                    .background(Color.white.opacity(0.9), in: .rect(cornerRadius: 14))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(stats.streak > 0 ? "\(stats.streak) day streak" : "New streak")
                }
                .padding(10)
            }
            VStack(alignment: .trailing, spacing: 8) {
                ForEach((0...maxFloor).reversed(), id: \.self) { i in
                    Button("Floor \(i + 1)") { self.floor = i; floorTick += 1 }
                        .font(tt.body(14, 800))
                        .foregroundStyle(i == floor ? .white : tt.c.ink)
                        .padding(.horizontal, 14).frame(minHeight: 38)
                        .background(i == floor ? tt.c.tangerine : Color.white.opacity(0.9), in: .rect(cornerRadius: 16))
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(i == floor ? .isSelected : [])
                }
                Button { router.editingHouse = true } label: {
                    Label("Edit house", systemImage: "pencil").font(tt.body(14, 800)).foregroundStyle(tt.c.ink)
                        .padding(.horizontal, 12).frame(minHeight: 38)
                        .background(Color.white.opacity(0.9), in: .rect(cornerRadius: 16))
                }
                .buttonStyle(.plain)
            }
            .padding(.trailing, 10)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .frame(height: 420)
        .clipShape(.rect(cornerRadius: 30))
    }

    private func goalCard(stats: Stats, data: AppData) -> some View {
        SoftCard {
            HStack(spacing: 12) {
                IconTile(symbol: "person.3.fill", tint: tt.c.bubblegum, background: tt.c.bubblegumSoft, size: 52)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Household goal: \(stats.householdWeek) of \(data.weeklyGoal) chores").font(tt.body(16, 800)).foregroundStyle(tt.c.ink).monospacedDigit()
                    FatProgress(progress: Double(stats.householdWeek) / Double(max(data.weeklyGoal, 1)), color: tt.c.tangerine, track: tt.c.tangerineSoft, height: 14)
                }
            }
            HStack(spacing: 10) {
                HStack(spacing: -8) {
                    ForEach(data.members.prefix(4)) { MemberBadge(member: $0, size: 40) }
                }
                Text(contributorsLine(stats.contributors.map(\.name), total: data.members.count))
                    .font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                NavigationLink(value: Route.recap) {
                    HStack(spacing: 6) {
                        Image(systemName: "calendar").font(.system(size: 16, weight: .semibold))
                        Text("Weekly recap").font(tt.body(14, 800))
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold))
                    }
                    .foregroundStyle(tt.c.bubblegum)
                    .padding(.horizontal, 12).frame(minHeight: 44)
                    .background(tt.c.bubblegumSoft, in: .rect(cornerRadius: 18))
                }
            }
            .padding(.top, 12)
        }
    }

    @ViewBuilder private func questRow(stats: Stats) -> some View {
        let todo = stats.todayChores.filter { !$0.isDone }
        let done = stats.todayChores.filter(\.isDone)
        if stats.todayChores.isEmpty {
            SoftCard(color: tt.c.skySoft) {
                Text("Nothing scheduled today").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                Text("Add chores from the Chores tab — templates make it quick.").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
            }
            .padding(.horizontal, 12)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(Array(todo.prefix(6))) { s in
                        QuestCard(status: s) { store.completeChore(s.chore.id, memberId: stats.you?.id) }
                    }
                    ForEach(Array(done.prefix(4))) { s in QuestCard(status: s, onDone: nil) }
                    if !todo.isEmpty {
                        Button { router.tab = .chores } label: {
                            VStack(alignment: .leading) {
                                Image(systemName: "clock.fill").font(.system(size: 26)).foregroundStyle(tt.c.bubblegum)
                                Spacer()
                                Text("\(todo.count) chores still waiting").font(tt.body(14, 700)).foregroundStyle(tt.c.ink).multilineTextAlignment(.leading)
                            }
                            .padding(14)
                            .frame(width: 130, height: 132, alignment: .leading)
                            .background(tt.c.bubblegumSoft, in: .rect(cornerRadius: 24))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 4)
            }
            .contentMargins(.horizontal, 12, for: .scrollContent)
        }
    }

    private var bellSheet: some View {
        let data = store.data
        let waiting = data.completions.filter { !$0.confirmed }
        return ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Ready when you are").font(tt.display(22)).foregroundStyle(tt.c.ink)
                if waiting.isEmpty {
                    Text("No thumbs-ups waiting. \(store.stats.waitingCount) chores still open today.").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                }
                ForEach(waiting) { comp in
                    let chore = data.chores.first { $0.id == comp.choreId }
                    let who = data.members.first { $0.id == comp.memberId }?.name ?? "Someone"
                    SoftCard {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(chore?.title ?? "Chore").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                                Text("\(who) finished this · \(DateUtil.label(comp.epochDay))").font(tt.body(12)).foregroundStyle(tt.c.muted)
                            }
                            Spacer()
                            Button("Looks great") { store.confirmCompletion(comp.id) }
                                .font(tt.body(15, 800)).foregroundStyle(tt.c.tangerine).frame(minHeight: 44)
                        }
                    }
                }
            }
            .padding(20)
        }
        .background(tt.c.canvas.ignoresSafeArea())
    }
}

func contributorsLine(_ names: [String], total: Int) -> String {
    if names.isEmpty && total <= 1 { return "Invite your household from the Household tab" }
    if names.isEmpty { return "Everyone's ready to pitch in this week" }
    if names.count == 1 { return "\(names[0]) is contributing this week" }
    return names.dropLast().joined(separator: ", ") + " and \(names.last ?? "") are all contributing"
}

/// Horizontal quest card for a chore with tap-to-complete.
struct QuestCard: View {
    let status: ChoreStatus
    let onDone: (() -> Void)?
    @Environment(\.tt) private var tt
    @State private var doneTick = 0

    var body: some View {
        let type = status.room?.type ?? .custom
        let content = VStack(alignment: .leading) {
            HStack(alignment: .top) {
                IconTile(symbol: type.symbol, tint: type.accent, background: type.tint, size: 46)
                Spacer()
                Image(systemName: "checkmark").font(.system(size: 15, weight: .heavy))
                    .foregroundStyle(status.isDone ? .white : tt.c.tangerine)
                    .frame(width: 32, height: 32)
                    .background(status.isDone ? tt.c.mint : tt.c.tangerineSoft, in: .circle)
                    .symbolEffect(.bounce, value: status.isDone)
            }
            Spacer(minLength: 6)
            Text(status.chore.title).font(tt.body(16, 800)).foregroundStyle(tt.c.ink).lineLimit(1)
            Group {
                if status.awaitingConfirm {
                    Text("Waiting for a thumbs-up").font(tt.body(12, 700)).foregroundStyle(tt.c.sky)
                } else if status.isDone {
                    Text("Done · +\(status.completion?.xp ?? 0) XP").font(tt.body(12, 700)).foregroundStyle(tt.c.mint)
                } else {
                    WeightDots(weight: status.chore.weight)
                }
            }
            .padding(.top, 2)
        }
        .padding(14)
        .frame(width: 180, height: 132)
        .background(status.isDone ? tt.c.mintSoft : tt.c.surface, in: .rect(cornerRadius: 24))

        let label = "\(status.chore.title), \(status.room?.name ?? ""), \(GameRules.weightLabel(status.chore.weight))" + (status.isDone ? ", done" : "")
        if let onDone {
            Button {
                doneTick += 1
                onDone()
            } label: { content }
                .buttonStyle(PressScaleStyle())
                .sensoryFeedback(.success, trigger: doneTick)
                .accessibilityLabel(label)
                .accessibilityHint("Marks the chore done")
        } else {
            content.accessibilityElement(children: .ignore).accessibilityLabel(label)
        }
    }
}

/// Squishy press scale for card-like buttons.
struct PressScaleStyle: ButtonStyle {
    @Environment(\.tt) private var tt
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !tt.reducedMotion ? 0.95 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.5), value: configuration.isPressed)
    }
}
