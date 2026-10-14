import SwiftUI

private enum ChoreSheet: Identifiable {
    case templates
    case editor(Chore, isNew: Bool)
    case detail(String)

    var id: String {
        switch self {
        case .templates: "templates"
        case let .editor(c, isNew): "editor-\(c.id)-\(isNew)"
        case let .detail(id): "detail-\(id)"
        }
    }
}

func frequencyText(_ chore: Chore) -> String {
    switch chore.frequency {
    case .daily: "Daily"
    case .weekly: "Weekly · \(DateUtil.shortDay(chore.startEpochDay))"
    case .monthly: "Monthly · day \(DateUtil.dayOfMonth(chore.startEpochDay))"
    case .custom: "Every \(chore.intervalDays) days"
    }
}

struct ChoresView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt
    @State private var day: Int?
    @State private var showAll = false
    @State private var sheet: ChoreSheet?
    @State private var toggleTick = 0

    var body: some View {
        let data = store.data
        let stats = store.stats
        let today = stats.today
        let selectedDay = day ?? today
        let weekStart = DateUtil.weekStart(today)
        let listed = showAll ? data.chores : data.chores.filter { GameRules.isDue($0, day: selectedDay) }
        let doneIds = Set(data.completions.filter { $0.epochDay == selectedDay }.map(\.choreId))
        let grouped: [(Room, [Chore])] = data.rooms.compactMap { r in
            let list = listed.filter { $0.roomId == r.id }
            return list.isEmpty ? nil : (r, list)
        }

        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Chores").font(tt.display(30)).foregroundStyle(tt.c.ink).accessibilityAddTraits(.isHeader)
                    let doneToday = stats.todayChores.filter(\.isDone).count
                    Text(stats.todayChores.isEmpty ? "Nothing due today" : "\(doneToday) of \(stats.todayChores.count) done today · \(stats.waitingCount) still waiting")
                        .font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                }
                weekStrip(data: data, weekStart: weekStart, today: today, selected: selectedDay)
                HStack(spacing: 8) {
                    Chip(label: selectedDay == today ? "Today" : DateUtil.label(selectedDay), selected: !showAll) { showAll = false }
                    Chip(label: "All chores (\(data.chores.count))", selected: showAll) { showAll = true }
                }
                if !showAll && selectedDay > today {
                    Text("Ready when you are — these open up on \(DateUtil.shortDay(selectedDay)).").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                }
                if grouped.isEmpty {
                    SoftCard(color: tt.c.skySoft) {
                        Text(showAll ? "No chores yet" : "A free day!").font(tt.display(20)).foregroundStyle(tt.c.ink)
                        Text("Tap Add chore to pick from room templates or write your own.").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                    }
                }
                ForEach(grouped, id: \.0.id) { room, chores in
                    HStack(spacing: 8) {
                        Image(systemName: room.type.symbol).foregroundStyle(room.type.accent)
                        Text(room.name).font(tt.display(20)).foregroundStyle(tt.c.ink)
                        if data.floors > 1 { Text("· Floor \(room.floor + 1)").font(tt.body(12, 700)).foregroundStyle(tt.c.muted) }
                    }
                    .padding(.top, 8)
                    .accessibilityAddTraits(.isHeader)
                    ForEach(chores) { chore in
                        let done = doneIds.contains(chore.id) && !showAll
                        ChoreRow(
                            chore: chore, done: done, canComplete: !showAll && selectedDay == today,
                            onToggle: {
                                toggleTick += 1
                                if done { store.undoCompletion(chore.id) } else { store.completeChore(chore.id, memberId: stats.you?.id) }
                            },
                            onOpen: { sheet = .detail(chore.id) }
                        )
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 110)
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sensoryFeedback(.success, trigger: toggleTick)
        .overlay(alignment: .bottomTrailing) {
            Button { sheet = .templates } label: {
                Label("Add chore", systemImage: "plus").font(tt.body(16, 800)).foregroundStyle(.white)
                    .padding(.horizontal, 22).frame(minHeight: 58)
                    .background(tt.c.tangerine, in: .rect(cornerRadius: 24))
                    .shadow(color: tt.c.tangerine.opacity(0.35), radius: 12, y: 6)
            }
            .buttonStyle(PressScaleStyle())
            .padding(20)
        }
        .sheet(item: $sheet) { s in sheetContent(s) }
    }

    private func weekStrip(data: AppData, weekStart: Int, today: Int, selected: Int) -> some View {
        HStack(spacing: 6) {
            ForEach(0..<7, id: \.self) { i in
                let d = weekStart + i
                let on = d == selected && !showAll
                let due = data.chores.filter { GameRules.isDue($0, day: d) }.count
                let doneCount = data.completions.filter { $0.epochDay == d }.count
                Button {
                    day = d; showAll = false
                } label: {
                    VStack(spacing: 2) {
                        Text(DateUtil.shortDay(d)).font(tt.body(11, 700)).foregroundStyle(on ? .white : tt.c.muted)
                        Text("\(DateUtil.dayOfMonth(d))").font(tt.display(20))
                            .foregroundStyle(on ? .white : d == today ? tt.c.tangerine : tt.c.ink)
                            .monospacedDigit()
                        Circle()
                            .fill(due == 0 ? .clear : doneCount >= due && d <= today ? (on ? .white : tt.c.mint) : (on ? Color.white.opacity(0.6) : tt.c.outline))
                            .frame(width: 6, height: 6)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(on ? tt.c.tangerine : tt.c.surface, in: .rect(cornerRadius: 18))
                    .animation(tt.reducedMotion ? nil : .snappy, value: on)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(DateUtil.shortDay(d)) \(DateUtil.label(d))\(d == today ? ", today" : "")")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    @ViewBuilder private func sheetContent(_ s: ChoreSheet) -> some View {
        let data = store.data
        let you = store.stats.you?.id
        switch s {
        case .templates:
            TemplatePickerSheet(
                onPick: { t, room in sheet = .editor(store.fromTemplate(t, room: room, assignee: you), isNew: true) },
                onScratch: {
                    if let room = data.rooms.first {
                        sheet = .editor(Chore(id: GameRules.newId(), title: "", roomId: room.id, kind: .general, weight: 2, startEpochDay: store.stats.today, assigneeId: you), isNew: true)
                    }
                }
            )
        case let .editor(chore, isNew):
            ChoreEditorView(
                initial: chore, isNew: isNew,
                onSave: { store.saveChore($0); sheet = nil },
                onDelete: isNew ? nil : { store.deleteChore(chore.id); sheet = nil }
            )
        case let .detail(id):
            if let chore = data.chores.first(where: { $0.id == id }) {
                ChoreDetailSheet(chore: chore, onEdit: { sheet = .editor(chore, isNew: false) })
            }
        }
    }
}

private struct ChoreRow: View {
    let chore: Chore
    let done: Bool
    let canComplete: Bool
    let onToggle: () -> Void
    let onOpen: () -> Void
    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt

    var body: some View {
        let data = store.data
        let assignee = data.members.first { $0.id == chore.assigneeId }
        let reward = GameRules.reward(chore.weight)
        HStack(spacing: 12) {
            Button(action: onToggle) {
                Image(systemName: "checkmark").font(.system(size: 17, weight: .heavy))
                    .foregroundStyle(done ? .white : tt.c.tangerine.opacity(canComplete ? 1 : 0.4))
                    .frame(width: 44, height: 44)
                    .background(done ? tt.c.mint : tt.c.tangerineSoft, in: .circle)
                    .scaleEffect(done && !tt.reducedMotion ? 1.08 : 1)
                    .animation(.spring(response: 0.3, dampingFraction: 0.4), value: done)
            }
            .buttonStyle(.plain)
            .disabled(!canComplete)
            .accessibilityLabel(done ? "Done. Undo" : "Mark \(chore.title) done")

            Button(action: onOpen) {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(chore.title).font(tt.body(16, 800)).foregroundStyle(tt.c.ink).strikethrough(done).lineLimit(1)
                        HStack(spacing: 6) {
                            WeightDots(weight: chore.weight)
                            Text("+\(reward.xp) XP").font(tt.body(12, 700)).foregroundStyle(tt.c.sky)
                            if chore.rotate { Image(systemName: "arrow.triangle.2.circlepath").font(.system(size: 12)).foregroundStyle(tt.c.muted).accessibilityLabel("Rotates") }
                            if chore.timerMinutes != nil { Image(systemName: "timer").font(.system(size: 12)).foregroundStyle(tt.c.muted).accessibilityLabel("Has timer") }
                            if chore.needsConfirm { Image(systemName: "hand.thumbsup.fill").font(.system(size: 12)).foregroundStyle(tt.c.muted).accessibilityLabel("Needs confirmation") }
                        }
                        Text(frequencyText(chore) + (chore.checklist.isEmpty ? "" : " · \(chore.checklist.count) steps"))
                            .font(tt.body(11, 700)).foregroundStyle(tt.c.muted)
                        if chore.remindMinuteOfDay != nil, let next = ReminderService.shared.nextNudge(chore, data: data) {
                            Text("Next nudge \(next.formatted(date: .omitted, time: .shortened))")
                                .font(tt.body(11, 700)).foregroundStyle(tt.c.sky)
                        }
                    }
                    Spacer(minLength: 0)
                    if let assignee {
                        VStack(spacing: 2) {
                            MemberBadge(member: assignee, size: 34)
                            Text(assignee.name).font(tt.body(11, 700)).foregroundStyle(tt.c.muted).lineLimit(1)
                        }
                        .frame(maxWidth: 64)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens details")
        }
        .padding(12)
        .background(done ? tt.c.mintSoft : tt.c.surface, in: .rect(cornerRadius: 24))
    }
}

private struct TemplatePickerSheet: View {
    let onPick: (ChoreTemplate, Room) -> Void
    let onScratch: () -> Void
    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt
    @State private var roomIdx = 0

    var body: some View {
        let data = store.data
        let room = data.rooms.indices.contains(roomIdx) ? data.rooms[roomIdx] : nil
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Chore templates").font(tt.display(26)).foregroundStyle(tt.c.ink)
                Text("Pick a room, tap a template, tweak and save.").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                Button(action: onScratch) {
                    Label("Write my own chore", systemImage: "pencil")
                }
                .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 54))
                .padding(.top, 12)
                if data.rooms.isEmpty {
                    Text("Add rooms to your house first.").font(tt.body(14)).foregroundStyle(tt.c.muted).padding(.top, 14)
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(data.rooms.enumerated()), id: \.element.id) { i, r in
                                Button { roomIdx = i } label: {
                                    HStack(spacing: 5) {
                                        Image(systemName: r.type.symbol).font(.system(size: 13)).foregroundStyle(r.type.accent)
                                        Text(r.name).font(tt.body(13, 800)).foregroundStyle(tt.c.ink)
                                    }
                                    .padding(.horizontal, 12).frame(minHeight: 38)
                                    .background(i == roomIdx ? r.type.tint : tt.c.surface, in: .rect(cornerRadius: 14))
                                }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(i == roomIdx ? .isSelected : [])
                            }
                        }
                    }
                    .padding(.top, 14)
                    if let room {
                        let list = Catalog.templates(for: room.type).isEmpty ? Catalog.templates(for: .custom) : Catalog.templates(for: room.type)
                        let existing = Set(data.chores.filter { $0.roomId == room.id }.map(\.title))
                        VStack(spacing: 8) {
                            ForEach(list, id: \.title) { t in
                                let r = GameRules.reward(t.weight)
                                Button { onPick(t, room) } label: {
                                    HStack(spacing: 12) {
                                        IconTile(symbol: room.type.symbol, tint: room.type.accent, background: room.type.tint, size: 42)
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(t.title).font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                                            HStack(spacing: 4) {
                                                WeightDots(weight: t.weight)
                                                Text("· \(t.frequency.label) · +\(r.xp) XP").font(tt.body(11, 700)).foregroundStyle(tt.c.muted)
                                            }
                                        }
                                        Spacer()
                                        if existing.contains(t.title) {
                                            Text("Added").font(tt.body(12, 800)).foregroundStyle(tt.c.mint)
                                        } else {
                                            Image(systemName: "plus").font(.system(size: 17, weight: .bold)).foregroundStyle(tt.c.tangerine)
                                        }
                                    }
                                    .padding(14)
                                    .background(tt.c.surface, in: .rect(cornerRadius: 20))
                                }
                                .buttonStyle(PressScaleStyle())
                            }
                        }
                        .padding(.top, 10)
                    }
                }
            }
            .padding(20)
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .presentationDetents([.large])
    }
}

private struct ChoreDetailSheet: View {
    let chore: Chore
    let onEdit: () -> Void
    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt
    @Environment(\.dismiss) private var dismiss
    @State private var ticked: Set<String> = []
    @State private var who: String?
    @State private var remaining = 0
    @State private var running = false
    @State private var timerDone = 0

    var body: some View {
        let data = store.data
        let today = store.stats.today
        let room = data.rooms.first { $0.id == chore.roomId }
        let completion = data.completions.first { $0.choreId == chore.id && $0.epochDay == today }
        let due = GameRules.isDue(chore, day: today)
        let reward = GameRules.reward(chore.weight)
        let type = room?.type ?? .custom

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    IconTile(symbol: type.symbol, tint: type.accent, background: type.tint, size: 52)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(chore.title).font(tt.display(22)).foregroundStyle(tt.c.ink)
                        Text("\(room?.name ?? "") · \(frequencyText(chore))").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                    }
                    Spacer()
                    Button(action: onEdit) {
                        Image(systemName: "pencil").font(.system(size: 18, weight: .bold)).foregroundStyle(tt.c.ink)
                            .frame(width: 44, height: 44).background(tt.c.surface, in: .circle)
                    }
                    .accessibilityLabel("Edit chore")
                }
                HStack {
                    WeightDots(weight: chore.weight)
                    Spacer()
                    Text("+\(reward.xp) XP · +\(reward.coins) coins · +\(reward.energy) energy").font(tt.body(13, 800)).foregroundStyle(tt.c.ink)
                }
                .padding(14)
                .background(tt.c.tangerineSoft, in: .rect(cornerRadius: 20))
                if !chore.notes.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text(chore.notes).font(tt.body(15, 500)).foregroundStyle(tt.c.ink)
                }
                if !chore.checklist.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("What done means").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                        VStack(spacing: 0) {
                            ForEach(chore.checklist) { item in
                                let on = ticked.contains(item.id) || completion != nil
                                Button {
                                    if ticked.contains(item.id) { ticked.remove(item.id) } else { ticked.insert(item.id) }
                                } label: {
                                    HStack(spacing: 10) {
                                        Image(systemName: on ? "checkmark.square.fill" : "square").font(.system(size: 20)).foregroundStyle(tt.c.tangerine)
                                        Text(item.text).font(tt.body(16)).foregroundStyle(tt.c.ink)
                                        Spacer()
                                    }
                                    .padding(.horizontal, 14).frame(minHeight: 48)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .disabled(completion != nil)
                                .sensoryFeedback(.selection, trigger: on)
                                .accessibilityAddTraits(on ? .isSelected : [])
                            }
                        }
                        .background(tt.c.surface, in: .rect(cornerRadius: 20))
                    }
                }
                if let minutes = chore.timerMinutes, completion == nil {
                    SoftCard(color: tt.c.skySoft) {
                        HStack {
                            VStack(alignment: .leading, spacing: 0) {
                                Text("Optional timer").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
                                Text(String(format: "%d:%02d", remaining / 60, remaining % 60)).font(tt.display(34)).foregroundStyle(tt.c.sky).monospacedDigit()
                                    .contentTransition(.numericText(countsDown: true))
                            }
                            Spacer()
                            Button {
                                if remaining == 0 { remaining = minutes * 60 }
                                running.toggle()
                            } label: {
                                Image(systemName: running ? "pause.fill" : "play.fill").font(.system(size: 26)).foregroundStyle(tt.c.sky)
                                    .frame(width: 56, height: 56).background(Color.white, in: .circle)
                            }
                            .accessibilityLabel(running ? "Pause timer" : "Start timer")
                        }
                        if remaining == 0 && timerDone > 0 {
                            Text("Time's up — nice focus!").font(tt.body(12, 700)).foregroundStyle(tt.c.mint)
                        }
                    }
                    .task(id: running) {
                        while running && remaining > 0 {
                            try? await Task.sleep(for: .seconds(1))
                            guard running else { break }
                            withAnimation { remaining -= 1 }
                        }
                        if remaining == 0 && running { running = false; timerDone += 1 }
                    }
                }
                if let completion {
                    let doer = data.members.first { $0.id == completion.memberId }?.name ?? "Someone"
                    SoftCard(color: tt.c.mintSoft) {
                        HStack(spacing: 10) {
                            Image(systemName: "sparkles").foregroundStyle(tt.c.mint)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Done by \(doer)").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                                Text(completion.confirmed ? "+\(completion.xp) XP earned" : "Waiting for a thumbs-up").font(tt.body(12)).foregroundStyle(tt.c.muted)
                            }
                            Spacer()
                            if !completion.confirmed && completion.memberId != store.stats.you?.id {
                                Button("Looks great") { store.confirmCompletion(completion.id) }
                                    .font(tt.body(15, 800)).foregroundStyle(tt.c.tangerine)
                            }
                        }
                    }
                    Button("Undo") { store.undoCompletion(chore.id) }
                        .font(tt.body(15, 700)).foregroundStyle(tt.c.muted)
                        .frame(maxWidth: .infinity, minHeight: 44)
                } else if due {
                    Text("Who did it?").font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(data.members) { m in
                                Button { who = m.id } label: {
                                    HStack(spacing: 6) {
                                        MemberBadge(member: m, size: 26)
                                        Text(m.isYou ? "\(m.name) (you)" : m.name).font(tt.body(14, 800)).foregroundStyle(tt.c.ink)
                                    }
                                    .padding(.horizontal, 10).frame(minHeight: 40)
                                    .background(who == m.id ? tt.c.tangerineSoft : tt.c.surface, in: .rect(cornerRadius: 14))
                                }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(who == m.id ? .isSelected : [])
                            }
                        }
                    }
                    Button {
                        store.completeChore(chore.id, memberId: who)
                        dismiss()
                    } label: {
                        Label("Mark done", systemImage: "checkmark")
                    }
                    .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 58))
                } else {
                    Text("Not due today — ready when you are on its next day.").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)
                }
            }
            .padding(20)
        }
        .background(tt.c.canvas.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .sensoryFeedback(.success, trigger: timerDone)
        .onAppear {
            who = store.stats.you?.id
            remaining = (chore.timerMinutes ?? 0) * 60
        }
    }
}
