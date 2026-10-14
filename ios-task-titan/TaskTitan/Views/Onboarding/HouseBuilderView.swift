import SwiftUI

func floorName(_ i: Int) -> String { i == 0 ? "Ground" : "Floor \(i + 1)" }

/// Full-screen house designer: up to 3 floors, unlimited rooms, live 3D preview.
struct HouseBuilderView: View {
    let onboarding: Bool
    var onDone: () -> Void = {}
    var onClose: (() -> Void)?

    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt

    @State private var initialRooms: [Room] = []
    @State private var rooms: [Room] = []
    @State private var size: HomeSize = .two
    @State private var floors = 1
    @State private var floor = 0
    @State private var selected: String?
    @State private var pendingSize: HomeSize?
    @State private var confirmDelete: Room?
    @State private var toast: String?
    @State private var didLoad = false
    @State private var placeTick = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                quickStart
                floorTabs
                planCard
                previewCard
                templates
                actions
                SoftCard(color: tt.c.skySoft) {
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.shield.fill").font(.system(size: 28)).foregroundStyle(tt.c.sky)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("No address, camera or location needed").font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                            Text("Your home is a playful sketch that only you control.").font(tt.body(12)).foregroundStyle(tt.c.muted)
                        }
                    }
                }
                .padding(.top, 14)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 16)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(tt.c.canvas.ignoresSafeArea())
        .overlay(alignment: .bottom) { toastView }
        .sensoryFeedback(.impact(weight: .light), trigger: placeTick)
        .onAppear(perform: load)
        .alert("Start from \(pendingSize?.label ?? "")?", isPresented: Binding(get: { pendingSize != nil }, set: { if !$0 { pendingSize = nil } })) {
            Button("Use \(pendingSize?.label ?? "")") { if let s = pendingSize { applySize(s) } }
            Button("Keep mine", role: .cancel) {}
        } message: {
            Text("This replaces your current floor plan with a fresh layout. Chores in removed rooms will be cleared when you save.")
        }
        .alert("Remove \(confirmDelete?.name ?? "room")?", isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } })) {
            Button("Remove", role: .destructive) {
                if let r = confirmDelete { rooms.removeAll { $0.id == r.id }; selected = nil }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Chores in this room will be removed when you save your house.")
        }
    }

    private func load() {
        guard !didLoad else { return }
        didLoad = true
        let d = store.data
        initialRooms = d.rooms.isEmpty ? GameRules.layout(for: d.homeSize) : d.rooms
        rooms = initialRooms
        size = d.homeSize
        floors = max(d.floors, (initialRooms.map(\.floor).max() ?? 0) + 1)
    }

    // MARK: Sections

    private var header: some View {
        HStack(spacing: 12) {
            BrandMark(size: 44)
            VStack(alignment: .leading, spacing: 0) {
                Text("Build Your Home").font(tt.display(26)).foregroundStyle(tt.c.ink)
                Text("Design your space, your way").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
            }
            Spacer()
            if let onClose {
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.system(size: 16, weight: .bold)).foregroundStyle(tt.c.ink)
                        .frame(width: 44, height: 44).background(tt.c.surface, in: .circle)
                }
                .accessibilityLabel("Close")
            }
        }
        .padding(.top, 10)
    }

    private var quickStart: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Quick start").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
            Picker("Home size", selection: Binding(get: { size }, set: { s in
                if onboarding && rooms == initialRooms { applySize(s) } else { pendingSize = s }
            })) {
                ForEach(HomeSize.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
        }
        .padding(.top, 14)
    }

    private var floorTabs: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                ForEach(0..<floors, id: \.self) { i in
                    Button {
                        floor = i; selected = nil
                    } label: {
                        Text(floorName(i)).font(tt.body(15, 800))
                            .foregroundStyle(i == floor ? .white : tt.c.ink)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(i == floor ? tt.c.tangerine : .clear, in: .capsule)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(i == floor ? [.isSelected] : [])
                }
                if floors < GameRules.maxFloors {
                    Button {
                        floors += 1; floor = floors - 1; selected = nil; placeTick += 1
                    } label: {
                        Label("Add floor", systemImage: "plus.circle").font(tt.body(15, 800)).foregroundStyle(tt.c.sky)
                            .padding(.horizontal, 10).frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(5)
            .background(tt.c.surface, in: .rect(cornerRadius: 26))
            HStack {
                Text("\(floors) of \(GameRules.maxFloors) floors · \(rooms.count) rooms").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
                Spacer()
                if floor > 0 && floor == floors - 1 {
                    Button("Remove this floor") {
                        rooms.removeAll { $0.floor == floor }
                        floors -= 1; floor = floors - 1; selected = nil
                    }
                    .font(tt.body(12, 700)).foregroundStyle(tt.c.bubblegum)
                }
            }
            .padding(.horizontal, 6)
            .frame(minHeight: 32)
        }
        .padding(.top, 14)
    }

    private var planCard: some View {
        SoftCard(padding: 12) {
            Text("Drag to arrange, pull corners to resize").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
            FloorPlanEditor(
                rooms: rooms, floor: floor, selectedId: selected,
                onSelect: { selected = $0 },
                onChange: { r in
                    if let i = rooms.firstIndex(where: { $0.id == r.id }) { rooms[i] = r }
                    placeTick += 1
                }
            )
            .padding(.top, 8)
            if !rooms.contains(where: { $0.floor == floor }) {
                Text("This floor is empty — tap a room template below to add one.").font(tt.body(12)).foregroundStyle(tt.c.muted).padding(.top, 8)
            }
            if let sel = rooms.first(where: { $0.id == selected }) {
                selectedPanel(sel).padding(.top, 10)
            }
        }
        .padding(.top, 6)
    }

    private func selectedPanel(_ room: Room) -> some View {
        HStack(spacing: 6) {
            TextField("Room name", text: Binding(
                get: { rooms.first { $0.id == room.id }?.name ?? "" },
                set: { v in if let i = rooms.firstIndex(where: { $0.id == room.id }) { rooms[i].name = String(v.prefix(22)) } }
            ))
            .font(tt.body(16))
            .padding(.horizontal, 14)
            .frame(minHeight: 48)
            .background(tt.c.canvas, in: .rect(cornerRadius: 16))
            Menu {
                ForEach(RoomType.allCases, id: \.self) { t in
                    Button {
                        if let i = rooms.firstIndex(where: { $0.id == room.id }) {
                            if rooms[i].name == rooms[i].type.label { rooms[i].name = t.label }
                            rooms[i].type = t
                        }
                    } label: { Label(t.label, systemImage: t.symbol) }
                }
            } label: {
                IconTile(symbol: room.type.symbol, tint: room.type.accent, background: room.type.tint, size: 48)
            }
            .accessibilityLabel("Change room type")
            Button {
                if store.data.chores.contains(where: { $0.roomId == room.id }) { confirmDelete = room } else {
                    rooms.removeAll { $0.id == room.id }; selected = nil
                }
            } label: {
                Image(systemName: "trash").font(.system(size: 18, weight: .semibold)).foregroundStyle(tt.c.bubblegum)
                    .frame(width: 48, height: 48).background(tt.c.bubblegumSoft, in: .rect(cornerRadius: 15))
            }
            .accessibilityLabel("Remove room")
        }
    }

    private var previewCard: some View {
        ZStack(alignment: .topLeading) {
            Color(hex: 0xEFF7FF)
            House3DView(
                scene: HouseScene(rooms: rooms, topFloor: min(floor, floors - 1), look: store.data.profile.look),
                autoSpin: true,
                description: "3D preview of \(floorName(floor))"
            )
            VStack(alignment: .leading, spacing: 0) {
                Text("3D preview").font(tt.display(20)).foregroundStyle(tt.c.ink)
                Text("\(floors) floors · \(rooms.count) rooms").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
            }
            .padding(14)
            .allowsHitTesting(false)
            VStack(alignment: .trailing, spacing: 6) {
                ForEach((0..<floors).reversed(), id: \.self) { i in
                    Button(floorName(i)) { floor = i; selected = nil }
                        .font(tt.body(12, 800))
                        .foregroundStyle(i == floor ? .white : tt.c.ink)
                        .padding(.horizontal, 12).frame(minHeight: 30)
                        .background(i == floor ? tt.c.tangerine : Color.white.opacity(0.9), in: .capsule)
                        .buttonStyle(.plain)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .topTrailing)
        }
        .frame(height: 250)
        .clipShape(.rect(cornerRadius: 28))
        .padding(.top, 12)
    }

    private var templates: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Room templates").font(tt.display(22)).foregroundStyle(tt.c.ink)
                Spacer()
                Text("Tap to add to \(floorName(floor))").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(RoomType.allCases, id: \.self) { t in
                        Button { addRoom(t) } label: {
                            VStack(spacing: 6) {
                                Image(systemName: t.symbol).font(.system(size: 24, weight: .semibold)).foregroundStyle(t.accent)
                                Text(t.label).font(tt.body(12, 700)).foregroundStyle(t.accent).lineLimit(1).minimumScaleFactor(0.8)
                            }
                            .frame(width: 86, height: 84)
                            .background(t.tint, in: .rect(cornerRadius: 20))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Add \(t.label)")
                    }
                }
            }
            .contentMargins(.horizontal, 0)
        }
        .padding(.top, 16)
    }

    private var actions: some View {
        HStack(spacing: 12) {
            Button { addRoom(.custom) } label: {
                Label("Add room", systemImage: "plus.circle")
            }
            .buttonStyle(CandyButtonStyle(fill: tt.c.skySoft, foreground: tt.c.sky, height: 58))
            Button {
                guard !rooms.isEmpty else { show("Add at least one room first"); return }
                let used = (rooms.map(\.floor).max() ?? 0) + 1
                store.saveHouse(rooms: rooms, floors: min(max(used, 1), floors), size: size)
                onDone()
            } label: {
                Label(onboarding ? "Enter my home" : "Save house", systemImage: "arrow.right")
                    .labelStyle(TrailingIconLabelStyle())
            }
            .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 58))
            .layoutPriority(1)
        }
        .padding(.top, 16)
    }

    @ViewBuilder private var toastView: some View {
        if let toast {
            Text(toast).font(tt.body(14, 700)).foregroundStyle(.white)
                .padding(.horizontal, 18).padding(.vertical, 12)
                .background(tt.c.ink, in: .capsule)
                .padding(.bottom, 20)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    // MARK: Actions

    private func show(_ msg: String) {
        withAnimation { toast = msg }
        Task {
            try? await Task.sleep(for: .seconds(2.5))
            withAnimation { if toast == msg { toast = nil } }
        }
    }

    private func addRoom(_ type: RoomType) {
        let big = GameRules.nextFreeSpot(rooms, floor: floor, w: 3, h: 3)
        guard let spot = big ?? GameRules.nextFreeSpot(rooms, floor: floor, w: 2, h: 2) else {
            show(floors < GameRules.maxFloors ? "\(floorName(floor)) is full — add another floor or resize rooms" : "This floor is full — try resizing rooms")
            return
        }
        let w = big != nil ? 3 : 2
        let same = rooms.filter { $0.type == type }.count
        let room = Room(id: GameRules.newId(), name: same == 0 ? type.label : "\(type.label) \(same + 1)", type: type, floor: floor, x: spot.x, y: spot.y, w: w, h: w)
        rooms.append(room)
        selected = room.id
        placeTick += 1
    }

    private func applySize(_ s: HomeSize) {
        size = s
        rooms = GameRules.layout(for: s)
        floors = (rooms.map(\.floor).max() ?? 0) + 1
        floor = 0
        selected = nil
    }
}

struct TrailingIconLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 6) {
            configuration.title
            configuration.icon
        }
    }
}
