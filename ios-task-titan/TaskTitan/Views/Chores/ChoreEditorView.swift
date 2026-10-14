import SwiftUI

/// Create / edit a chore: weight, room, checklist ("what done means"), rotation, reminder and options.
struct ChoreEditorView: View {
    let initial: Chore
    let isNew: Bool
    let onSave: (Chore) -> Void
    let onDelete: (() -> Void)?

    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt
    @State private var title = ""
    @State private var roomId = ""
    @State private var weight = 2
    @State private var custom = false
    @State private var freq: Frequency = .daily
    @State private var interval = 2
    @State private var assignee: String?
    @State private var rotate = false
    @State private var auto = false
    @State private var timerOn = false
    @State private var timerMin = 10
    @State private var confirm = false
    @State private var remindOn = false
    @State private var remindTime = Date()
    @State private var steps: [ChecklistItem] = []
    @State private var newStep = ""
    @State private var notes = ""
    @State private var loaded = false

    var body: some View {
        let data = store.data
        let reward = GameRules.reward(weight)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(isNew ? "New Chore" : "Edit Chore").font(tt.display(26)).foregroundStyle(tt.c.ink)
                Text(isNew ? "Create a chore for your household" : "Changes apply from today on").font(tt.body(14, 500)).foregroundStyle(tt.c.muted)

                label("Chore name").padding(.top, 14)
                HStack {
                    TextField("e.g. Vacuum living room", text: $title)
                        .font(tt.body(17))
                        .textInputAutocapitalization(.sentences)
                        .onChange(of: title) { _, v in if v.count > 40 { title = String(v.prefix(40)) } }
                    if !title.isEmpty {
                        Button { title = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(tt.c.muted) }
                            .accessibilityLabel("Clear")
                    }
                }
                .padding(.horizontal, 16).frame(minHeight: 54)
                .background(tt.c.surface, in: .rect(cornerRadius: 18))

                label("Room").padding(.top, 12)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(data.rooms) { r in
                            let on = r.id == roomId
                            Button { roomId = r.id } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: r.type.symbol).foregroundStyle(r.type.accent)
                                    Text(r.name + (data.floors > 1 ? " · F\(r.floor + 1)" : "")).font(tt.body(14, 800))
                                        .foregroundStyle(on ? r.type.accent : tt.c.ink)
                                }
                                .padding(.horizontal, 14).frame(minHeight: 44)
                                .background(on ? r.type.tint : tt.c.surface, in: .rect(cornerRadius: 18))
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                }

                label("Effort level").padding(.top, 12)
                Picker("Effort level", selection: Binding(
                    get: { custom ? 4 : weight },
                    set: { v in
                        if v == 4 { custom = true; if weight < 4 { weight = 4 } } else { custom = false; weight = v }
                    }
                )) {
                    Text("Light").tag(1)
                    Text("Normal").tag(2)
                    Text("Heavy").tag(3)
                    Text("Custom").tag(4)
                }
                .pickerStyle(.segmented)
                HStack(spacing: 8) {
                    Text("Weight").font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
                    ForEach(1...5, id: \.self) { i in
                        Button { weight = i; custom = i > 3 } label: {
                            Text("\(i)").font(tt.body(15, 800)).foregroundStyle(i <= weight ? .white : tt.c.muted)
                                .frame(width: 40, height: 40)
                                .background(i <= weight ? tt.c.tangerine : tt.c.surface, in: .circle)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Weight \(i) of 5")
                        .accessibilityAddTraits(i == weight ? .isSelected : [])
                    }
                }
                .padding(.top, 8)
                .sensoryFeedback(.selection, trigger: weight)
                HStack(spacing: 10) {
                    Image(systemName: "star.fill").font(.system(size: 26)).foregroundStyle(tt.c.sun)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(GameRules.weightLabel(weight)) = +\(reward.xp) XP · +\(reward.coins) coins · +\(reward.energy) energy")
                            .font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                            .contentTransition(.numericText())
                            .animation(.snappy, value: weight)
                        Text("Bigger effort, bigger rewards!").font(tt.body(12)).foregroundStyle(tt.c.muted)
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(tt.c.tangerineSoft, in: .rect(cornerRadius: 20))
                .padding(.top, 10)

                label("How often?").padding(.top, 14)
                Picker("How often", selection: $freq) {
                    ForEach(Frequency.allCases, id: \.self) { f in
                        Text(f == .custom ? "Every \(interval) d" : f.label).tag(f)
                    }
                }
                .pickerStyle(.segmented)
                if freq == .custom {
                    Stepper("Repeat every \(interval) days", value: $interval, in: 2...30)
                        .font(tt.body(15, 500)).foregroundStyle(tt.c.ink).padding(.top, 6)
                }
                if freq == .weekly {
                    Text("Repeats every \(DateUtil.shortDay(initial.startEpochDay))").font(tt.body(12, 700)).foregroundStyle(tt.c.muted).padding(.top, 6)
                }

                label("Who's on it?").padding(.top, 14)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(data.members) { m in
                            Button { assignee = m.id } label: {
                                HStack(spacing: 6) {
                                    MemberBadge(member: m, size: 26)
                                    Text(m.isYou ? "\(m.name) (you)" : m.name).font(tt.body(14, 800)).foregroundStyle(tt.c.ink)
                                }
                                .padding(.horizontal, 10).frame(minHeight: 42)
                                .background(assignee == m.id ? tt.c.tangerineSoft : tt.c.surface, in: .rect(cornerRadius: 16))
                                .opacity(auto ? 0.45 : 1)
                            }
                            .buttonStyle(.plain)
                            .disabled(auto)
                            .accessibilityAddTraits(assignee == m.id ? .isSelected : [])
                        }
                    }
                }

                label("What does done mean?").padding(.top, 14)
                VStack(spacing: 0) {
                    ForEach(steps) { step in
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark.square.fill").foregroundStyle(tt.c.tangerine)
                            Text(step.text).font(tt.body(16)).foregroundStyle(tt.c.ink)
                            Spacer()
                            Button { steps.removeAll { $0.id == step.id } } label: {
                                Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(tt.c.muted).frame(width: 44, height: 44)
                            }
                            .accessibilityLabel("Remove step")
                        }
                        .padding(.leading, 12)
                        Divider().overlay(tt.c.outline)
                    }
                    HStack(spacing: 6) {
                        Button(action: addStep) {
                            Image(systemName: "plus.square.fill").font(.system(size: 22)).foregroundStyle(tt.c.tangerine).frame(width: 44, height: 44)
                        }
                        .accessibilityLabel("Add step")
                        TextField("Add a step", text: $newStep)
                            .font(tt.body(16))
                            .submitLabel(.done)
                            .onSubmit(addStep)
                    }
                    .padding(.horizontal, 6)
                }
                .background(tt.c.surface, in: .rect(cornerRadius: 20))

                label("Options").padding(.top, 14)
                VStack(spacing: 0) {
                    optionRow("arrow.triangle.2.circlepath", "Rotate between members", "Passes to the next person after each finish", Binding(get: { rotate }, set: { rotate = $0; if $0 { auto = false } }))
                    Divider().overlay(tt.c.outline)
                    optionRow("person.fill.checkmark", "Auto-assign by availability", "Gives it to whoever has the lightest load", Binding(get: { auto }, set: { auto = $0; if $0 { rotate = false } }))
                    Divider().overlay(tt.c.outline)
                    optionRow("timer", "Optional timer", timerOn ? "\(timerMin) minute focus timer" : "Off", $timerOn)
                    if timerOn {
                        Stepper("Timer · \(timerMin) min", value: $timerMin, in: 5...90, step: 5)
                            .font(tt.body(15, 500)).foregroundStyle(tt.c.ink)
                            .padding(.horizontal, 14).padding(.bottom, 8)
                    }
                    Divider().overlay(tt.c.outline)
                    optionRow("hand.thumbsup.fill", "Ask a member to confirm", "Rewards land after a thumbs-up", $confirm)
                    Divider().overlay(tt.c.outline)
                    optionRow("bell.badge.fill", "Gentle reminder", remindOn ? "A friendly nudge at \(remindTime.formatted(date: .omitted, time: .shortened))" : "A friendly nudge at a set time", $remindOn)
                    if remindOn {
                        DatePicker("Remind at", selection: $remindTime, displayedComponents: .hourAndMinute)
                            .font(tt.body(15, 500)).foregroundStyle(tt.c.muted)
                            .tint(tt.c.tangerine)
                            .padding(.horizontal, 14).padding(.bottom, 8)
                    }
                }
                .background(tt.c.surface, in: .rect(cornerRadius: 20))

                TextField("Notes (optional)", text: $notes, axis: .vertical)
                    .font(tt.body(16))
                    .lineLimit(1...4)
                    .onChange(of: notes) { _, v in if v.count > 200 { notes = String(v.prefix(200)) } }
                    .padding(16)
                    .background(tt.c.surface, in: .rect(cornerRadius: 18))
                    .padding(.top, 12)

                Button("Save chore", action: save)
                    .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 58))
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty || !data.rooms.contains { $0.id == roomId })
                    .padding(.top, 16)
                if let onDelete {
                    Button("Delete chore", role: .destructive, action: onDelete)
                        .font(tt.body(15, 800)).foregroundStyle(tt.c.bubblegum)
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
            }
            .padding(20)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(tt.c.canvas.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        title = initial.title
        roomId = initial.roomId
        weight = initial.weight
        custom = initial.weight > 3
        freq = initial.frequency
        interval = max(initial.intervalDays, 2)
        assignee = initial.assigneeId
        rotate = initial.rotate
        auto = initial.autoAssign
        timerOn = initial.timerMinutes != nil
        timerMin = initial.timerMinutes ?? 10
        confirm = initial.needsConfirm
        remindOn = initial.remindMinuteOfDay != nil
        let m = initial.remindMinuteOfDay ?? 9 * 60
        remindTime = Calendar.current.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: Date()) ?? Date()
        steps = initial.checklist
        notes = initial.notes
    }

    private func addStep() {
        let t = newStep.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty else { return }
        steps.append(ChecklistItem(id: GameRules.newId(), text: String(t.prefix(50))))
        newStep = ""
    }

    private func save() {
        let data = store.data
        let room = data.rooms.first { $0.id == roomId }
        let trimmed = title.trimmingCharacters(in: .whitespaces)
        let comps = Calendar.current.dateComponents([.hour, .minute], from: remindTime)
        var c = initial
        c.title = trimmed
        c.roomId = roomId
        c.kind = trimmed == initial.title ? initial.kind : Catalog.guessKind(trimmed, roomType: room?.type)
        c.weight = weight
        c.frequency = freq
        c.intervalDays = freq == .custom ? interval : 1
        c.assigneeId = assignee ?? data.members.first?.id
        c.rotate = rotate
        c.autoAssign = auto
        c.timerMinutes = timerOn ? timerMin : nil
        c.needsConfirm = confirm
        c.checklist = steps
        c.notes = notes
        c.remindMinuteOfDay = remindOn ? (comps.hour ?? 9) * 60 + (comps.minute ?? 0) : nil
        if remindOn { ReminderService.shared.requestPermission() }
        onSave(c)
    }

    private func label(_ text: String) -> some View {
        Text(text).font(tt.body(14, 700)).foregroundStyle(tt.c.ink).padding(.bottom, 6)
    }

    private func optionRow(_ symbol: String, _ title: String, _ subtitle: String, _ isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: 12) {
                Image(systemName: symbol).foregroundStyle(tt.c.ink.opacity(0.7)).frame(width: 24)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(tt.body(16)).foregroundStyle(tt.c.ink)
                    Text(subtitle).font(tt.body(12)).foregroundStyle(tt.c.muted)
                }
            }
        }
        .tint(tt.c.tangerine)
        .padding(.horizontal, 14).padding(.vertical, 10)
    }
}
