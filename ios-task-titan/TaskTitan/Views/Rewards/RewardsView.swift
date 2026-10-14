import SwiftUI

struct RewardsView: View {
    @Environment(AppStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.tt) private var tt
    @State private var tab = 0
    @State private var toast: String?
    @State private var buyTick = 0
    @State private var equipTick = 0

    private let tabs = ["Look"] + ItemCategory.allCases.map(\.label)
    private let grid = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        let data = store.data
        let stats = store.stats
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("Rewards").font(tt.display(30)).foregroundStyle(tt.c.ink).accessibilityAddTraits(.isHeader)
                    Spacer()
                    HStack(spacing: 6) {
                        Image(systemName: "dollarsign.circle.fill").foregroundStyle(tt.c.sun)
                        Text(stats.coins.formatted()).font(tt.display(18)).foregroundStyle(tt.c.ink).monospacedDigit()
                            .contentTransition(.numericText())
                            .animation(.snappy, value: stats.coins)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(tt.c.surface, in: .rect(cornerRadius: 18))
                    .accessibilityElement(children: .combine)
                }
                .padding(.horizontal, 16).padding(.vertical, 10)

                ZStack(alignment: .topLeading) {
                    LinearGradient(colors: [Color(hex: 0xFFE3F0), Color(hex: 0xFFF1E2), Color(hex: 0xFFF8EC)], startPoint: .top, endPoint: .bottom)
                    AvatarView(look: data.profile.look, tier: stats.tier, description: "Your avatar, \(stats.health.label.lowercased()), energy \(stats.tier.label)", health: stats.health)
                        .padding(.top, 24)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("\(stats.you?.name ?? "You") · Lv \(stats.level.level)").font(tt.display(20)).foregroundStyle(tt.c.ink)
                        Text("\(stats.health.label), energy \(stats.tier.label)").font(tt.body(15, 800)).foregroundStyle(stats.health == .healthy ? tt.c.mint : tt.c.bubblegum)
                    }
                    .padding(16)
                    .allowsHitTesting(false)
                    Text("Drag to spin").font(tt.body(11, 700)).foregroundStyle(tt.c.muted)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                        .padding(10)
                        .allowsHitTesting(false)
                }
                .frame(height: 300)
                .clipShape(.rect(cornerRadius: 30))
                .padding(.horizontal, 12)

                Text("Health follows today's chores: finish yours and you feel great again. Energy and levels only ever go up.")
                    .font(tt.body(12)).foregroundStyle(tt.c.muted)
                    .padding(.horizontal, 20).padding(.vertical, 8)

                SectionTitle("Daily quests").padding(.horizontal, 16)
                SoftCard {
                    ForEach(stats.quests) { q in
                        HStack(spacing: 10) {
                            Image(systemName: q.progress >= 1 ? "checkmark.circle.fill" : "circle.dashed").foregroundStyle(q.progress >= 1 ? tt.c.mint : tt.c.muted)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(q.title).font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                                FatProgress(progress: q.progress, color: tt.c.sky, track: tt.c.skySoft, height: 8)
                            }
                            if q.claimed {
                                Text("Claimed").font(tt.body(12, 800)).foregroundStyle(tt.c.mint)
                            } else if q.progress >= 1 {
                                Button("+\(q.coins)") { buyTick += 1; store.claimQuest(q.id) }
                                    .font(tt.body(14, 800)).foregroundStyle(.white)
                                    .padding(.horizontal, 14).frame(minHeight: 40)
                                    .background(tt.c.tangerine, in: .rect(cornerRadius: 14))
                            } else {
                                Text("+\(q.coins)").font(tt.body(14, 800)).foregroundStyle(tt.c.muted)
                            }
                        }
                        .padding(.vertical, 6)
                    }
                }
                .padding(.horizontal, 12).padding(.top, 8)

                SectionTitle("Closet").padding(.horizontal, 16).padding(.top, 16)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 4) {
                        ForEach(Array(tabs.enumerated()), id: \.offset) { i, t in
                            Button { withAnimation(.snappy) { tab = i } } label: {
                                VStack(spacing: 6) {
                                    Text(t).font(tt.body(15, 800)).foregroundStyle(tab == i ? tt.c.tangerine : tt.c.muted)
                                    Capsule().fill(tab == i ? tt.c.tangerine : .clear).frame(height: 3)
                                }
                                .padding(.horizontal, 10).frame(minHeight: 44)
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(tab == i ? .isSelected : [])
                        }
                    }
                }
                .contentMargins(.horizontal, 12, for: .scrollContent)
                .padding(.top, 4)

                Group {
                    if tab == 0 {
                        lookEditor(look: data.profile.look)
                    } else {
                        let cat = ItemCategory.allCases[tab - 1]
                        let items = Catalog.cosmetics.filter { $0.category == cat && (!$0.seasonal || data.profile.owned.contains($0.id)) }
                        VStack(alignment: .leading, spacing: 10) {
                            LazyVGrid(columns: grid, spacing: 10) {
                                ForEach(items) { item in
                                    ItemTile(item: item, data: data, level: stats.level.level) { tap(item, data: data) }
                                }
                            }
                            if cat == .decor {
                                Text("Decor shows up in your living room in 3D. Seasonal decor unlocks through household events.")
                                    .font(tt.body(12)).foregroundStyle(tt.c.muted)
                            }
                        }
                    }
                }
                .padding(.horizontal, 12).padding(.top, 10)

                SectionTitle("Milestones").padding(.horizontal, 16).padding(.top, 18)
                SoftCard {
                    ForEach(Catalog.achievements) { a in
                        let unlocked = stats.unlockedAchievements.contains(a.id)
                        let claimed = data.profile.claimedAchievements.contains(a.id)
                        HStack(spacing: 12) {
                            Image(systemName: "trophy.fill").foregroundStyle(unlocked ? tt.c.tangerine : tt.c.muted.opacity(0.5))
                                .frame(width: 40, height: 40)
                                .background(unlocked ? tt.c.tangerineSoft : Color(hex: 0xF1ECE4), in: .circle)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(a.title).font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                                Text(a.detail).font(tt.body(12)).foregroundStyle(tt.c.muted)
                            }
                            Spacer()
                            if claimed {
                                Image(systemName: "checkmark").font(.system(size: 16, weight: .bold)).foregroundStyle(tt.c.mint).accessibilityLabel("Claimed")
                            } else if unlocked {
                                Button("+\(a.coins)") { store.claimAchievement(a.id) }
                                    .font(tt.body(14, 800)).foregroundStyle(.white)
                                    .padding(.horizontal, 14).frame(minHeight: 40)
                                    .background(tt.c.tangerine, in: .rect(cornerRadius: 14))
                            } else {
                                Text("+\(a.coins)").font(tt.body(12, 800)).foregroundStyle(tt.c.muted)
                            }
                        }
                        .padding(.vertical, 7)
                    }
                }
                .padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 28)
            }
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sensoryFeedback(.success, trigger: buyTick)
        .sensoryFeedback(.selection, trigger: equipTick)
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast).font(tt.body(14, 700)).foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(tt.c.ink, in: .capsule)
                    .padding(.bottom, 16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func tap(_ item: Cosmetic, data: AppData) {
        if data.profile.owned.contains(item.id) {
            store.equip(item.id); equipTick += 1
        } else if item.pro && !data.isPro {
            router.showingPro = true
        } else if let err = store.buy(item.id) {
            withAnimation { toast = err }
            Task {
                try? await Task.sleep(for: .seconds(2.2))
                withAnimation { if toast == err { toast = nil } }
            }
        } else {
            buyTick += 1
        }
    }

    private func lookEditor(look: AvatarLook) -> some View {
        SoftCard {
            Text("Character").font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
            Picker("Character", selection: Binding(get: { look.style }, set: { var l = look; l.style = $0; store.setLook(l) })) {
                Text("Boy").tag("boy")
                Text("Girl").tag("girl")
            }
            .pickerStyle(.segmented)
            .padding(.top, 6)
            Text("Body type").font(tt.body(14, 700)).foregroundStyle(tt.c.ink).padding(.top, 14)
            Picker("Body type", selection: Binding(get: { look.body }, set: { var l = look; l.body = $0; store.setLook(l) })) {
                ForEach(Array(Catalog.bodyLabels.enumerated()), id: \.offset) { i, l in Text(l).tag(i) }
            }
            .pickerStyle(.segmented)
            .padding(.top, 6)
            Text("Skin tone").font(tt.body(14, 700)).foregroundStyle(tt.c.ink).padding(.top, 14)
            swatches(Catalog.skinTones, selected: look.skin, label: "Skin tone") { var l = look; l.skin = $0; store.setLook(l) }
                .padding(.top, 6)
            Text("Hair color").font(tt.body(14, 700)).foregroundStyle(tt.c.ink).padding(.top, 14)
            swatches(Catalog.hairColors, selected: look.hairColor, label: "Hair color") { var l = look; l.hairColor = $0; store.setLook(l) }
                .padding(.top, 6)
        }
    }

    private func swatches(_ colors: [UInt32], selected: Int, label: String, onPick: @escaping (Int) -> Void) -> some View {
        HStack(spacing: 10) {
            ForEach(Array(colors.enumerated()), id: \.offset) { i, col in
                Button { onPick(i); equipTick += 1 } label: {
                    Circle().fill(RGB(col).color)
                        .frame(width: 36, height: 36)
                        .padding(3)
                        .background(Circle().fill(i == selected ? tt.c.tangerine : .clear))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(label) \(i + 1)")
                .accessibilityAddTraits(i == selected ? .isSelected : [])
            }
        }
    }
}

private struct ItemTile: View {
    let item: Cosmetic
    let data: AppData
    let level: Int
    let onTap: () -> Void
    @Environment(\.tt) private var tt

    var body: some View {
        let owned = data.profile.owned.contains(item.id)
        let look = data.profile.look
        let equipped: Bool = switch item.category {
        case .hair: look.hair == item.id
        case .outfit: look.outfit == item.id
        case .shoes: look.shoes == item.id
        case .accessory: look.accessory == item.id
        case .pet: look.pet == item.id
        case .aura: look.aura == item.id
        case .decor: data.profile.equippedDecor.contains(item.id)
        }
        let locked = !owned && level < item.unlockLevel
        let proLocked = item.pro && !data.isPro && !owned
        let swatch = item.color != 0 ? RGB(item.color).color : RGB(Catalog.hairColors.indices.contains(look.hairColor) ? Catalog.hairColors[look.hairColor] : 0x6B3E1F).color
        let state: String = if equipped { "Equipped" } else if owned { "Owned" } else if proLocked { "Pro" } else if locked { "Lv \(item.unlockLevel)" } else { "\(item.price) coins" }
        let stateColor: Color = equipped ? tt.c.tangerine : owned ? tt.c.mint : proLocked ? tt.c.bubblegum : tt.c.muted

        Button(action: onTap) {
            VStack(spacing: 6) {
                swatch.opacity(0.18)
                    .aspectRatio(1.15, contentMode: .fit)
                    .overlay {
                        Group {
                            if item.category == .aura {
                                Circle().fill(swatch.opacity(locked ? 0.35 : 1))
                            } else {
                                RoundedRectangle(cornerRadius: 12).fill(swatch.opacity(locked ? 0.35 : 1))
                            }
                        }
                        .frame(width: 38, height: 38)
                    }
                    .overlay(alignment: .topTrailing) {
                        if locked || proLocked {
                            Image(systemName: "lock.fill").font(.system(size: 11)).foregroundStyle(tt.c.muted)
                                .frame(width: 22, height: 22).background(.white, in: .circle).padding(5)
                        }
                    }
                    .clipShape(.rect(cornerRadius: 16))
                Text(item.name).font(tt.body(12, 700)).foregroundStyle(tt.c.ink).lineLimit(1).minimumScaleFactor(0.8)
                Text(state).font(tt.body(11, 700)).foregroundStyle(stateColor)
            }
            .padding(10)
            .background(tt.c.surface, in: .rect(cornerRadius: 22))
            .overlay {
                if equipped { RoundedRectangle(cornerRadius: 22).strokeBorder(tt.c.tangerine, lineWidth: 2.5) }
            }
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel("\(item.name), \(state)")
    }
}
