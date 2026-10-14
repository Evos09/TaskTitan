import SwiftUI

struct RecapView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt
    @State private var grown = false

    var body: some View {
        let data = store.data
        let stats = store.stats
        let weekStart = DateUtil.weekStart(stats.today)
        let confirmed = data.completions.filter { $0.confirmed && $0.epochDay >= weekStart && $0.epochDay <= stats.today }
        let perDay = (0..<7).map { i in confirmed.filter { $0.epochDay == weekStart + i }.count }
        let maxDay = max(perDay.max() ?? 0, 1)
        let choreRoom = Dictionary(data.chores.map { ($0.id, $0.roomId) }, uniquingKeysWith: { a, _ in a })
        let roomCounts = Dictionary(grouping: confirmed.compactMap { choreRoom[$0.choreId] }, by: { $0 }).mapValues(\.count)
        let topRooms: [(Room, Int)] = roomCounts.sorted { $0.value > $1.value }
            .compactMap { e in data.rooms.first { $0.id == e.key }.map { ($0, e.value) } }
            .prefix(3).map { $0 }
        let memories = data.memories.filter { $0.epochDay >= weekStart }.count
        let yourXp = confirmed.filter { $0.memberId == stats.you?.id }.reduce(0) { $0 + $1.xp }
        let upcoming = (1...7).reduce(0) { sum, d in sum + data.chores.filter { GameRules.isDue($0, day: stats.today + d) }.count }

        ScrollView {
            VStack(spacing: 12) {
                SoftCard(color: tt.c.skySoft) {
                    Text("Week of \(DateUtil.label(weekStart))").font(tt.body(15, 800)).foregroundStyle(tt.c.sky)
                    Text("\(stats.householdWeek) chores, together").font(tt.display(26)).foregroundStyle(tt.c.ink)
                    Text(stats.householdWeek >= data.weeklyGoal ? "You hit the household goal of \(data.weeklyGoal)! 🎉" : "\(data.weeklyGoal - stats.householdWeek) to go for the household goal — no pressure.")
                        .font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                    HStack(alignment: .bottom, spacing: 8) {
                        ForEach(0..<7, id: \.self) { i in
                            let n = perDay[i]
                            VStack(spacing: 4) {
                                Spacer(minLength: 0)
                                Text("\(n)").font(tt.body(11, 700)).foregroundStyle(tt.c.ink).monospacedDigit()
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(weekStart + i == stats.today ? tt.c.tangerine : tt.c.sky.opacity(0.6))
                                    .frame(height: max(4, 80 * (grown ? Double(n) / Double(maxDay) : 0)))
                                    .animation(tt.reducedMotion ? nil : .spring(response: 0.6, dampingFraction: 0.7).delay(Double(i) * 0.06), value: grown)
                                Text(String(DateUtil.shortDay(weekStart + i).prefix(1))).font(tt.body(11, 700)).foregroundStyle(tt.c.muted)
                            }
                            .frame(maxWidth: .infinity)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("\(DateUtil.shortDay(weekStart + i)): \(n) chores")
                        }
                    }
                    .frame(height: 130)
                    .padding(.top, 14)
                }
                HStack(spacing: 12) {
                    statTile("sparkles", "\(stats.sparklingDaysThisWeek)", "Sparkling days")
                    statTile("photo.on.rectangle.angled", "\(memories)", "New memories")
                }
                if !topRooms.isEmpty {
                    SoftCard {
                        Text("Most cared-for rooms").font(tt.display(20)).foregroundStyle(tt.c.ink)
                        ForEach(topRooms, id: \.0.id) { room, n in
                            HStack(spacing: 10) {
                                IconTile(symbol: room.type.symbol, tint: room.type.accent, background: room.type.tint, size: 38)
                                Text(room.name).font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                                Spacer()
                                Text("\(n) chores").font(tt.body(14, 800)).foregroundStyle(tt.c.muted)
                            }
                            .padding(.vertical, 5)
                        }
                        .padding(.top, 8)
                    }
                }
                SoftCard(color: tt.c.tangerineSoft) {
                    Label("Just for you — nobody else sees this", systemImage: "lock.fill").font(tt.body(14, 800)).foregroundStyle(tt.c.tangerine)
                    HStack(spacing: 10) {
                        miniStat("\(stats.yourWeek)", "chores", flame: false)
                        miniStat("+\(yourXp)", "XP", flame: false)
                        miniStat(stats.streak > 0 ? "\(stats.streak)" : "New", "streak", flame: true)
                    }
                    .padding(.top, 10)
                    if stats.streak == 0 {
                        Text("Start a new streak today — any chore counts.").font(tt.body(12)).foregroundStyle(tt.c.muted).padding(.top, 8)
                    }
                }
                SoftCard {
                    Label("Coming up", systemImage: "star.fill").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                    Text("\(upcoming) chores scheduled over the next 7 days. Ready when you are.").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 16)
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .navigationTitle("Weekly recap")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(tt.c.canvas, for: .navigationBar)
        .onAppear { grown = true }
    }

    private func statTile(_ symbol: String, _ value: String, _ label: String) -> some View {
        SoftCard {
            Image(systemName: symbol).foregroundStyle(tt.c.bubblegum)
            Text(value).font(tt.display(26)).foregroundStyle(tt.c.ink).padding(.top, 6)
            Text(label).font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
        }
        .accessibilityElement(children: .combine)
    }

    private func miniStat(_ value: String, _ label: String, flame: Bool) -> some View {
        VStack(spacing: 0) {
            if flame { Image(systemName: "flame.fill").font(.system(size: 15)).foregroundStyle(tt.c.tangerine) }
            Text(value).font(tt.display(20)).foregroundStyle(tt.c.ink).monospacedDigit()
            Text(label).font(tt.body(11, 700)).foregroundStyle(tt.c.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(tt.c.surface, in: .rect(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }
}
