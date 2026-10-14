import SwiftUI

/// First launch: name, age group and COPPA/GDPR-style consent.
struct ConsentView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.tt) private var tt
    @State private var name = ""
    @State private var age = "adult"
    @State private var agreed = false
    @State private var parent = false
    @State private var style = "boy"
    @FocusState private var nameFocused: Bool

    private var needsParent: Bool { age == "child" }
    private var canGo: Bool { agreed && (!needsParent || parent) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                AvatarView(look: AvatarLook(style: style), tier: .thriving, pose: .celebrate, description: "A cheerful 3D avatar waving hello")
                    .frame(height: 230)
                    .frame(maxWidth: .infinity)
                Text("Task Titan").font(tt.display(34)).foregroundStyle(tt.c.ink)
                Text("Real chores, a happier 3D home. Your avatar only ever levels up.")
                    .font(tt.body(16)).foregroundStyle(tt.c.muted)
                    .padding(.top, 2)

                TextField("Your name or nickname", text: $name)
                    .font(tt.body(17))
                    .textInputAutocapitalization(.words)
                    .submitLabel(.done)
                    .focused($nameFocused)
                    .padding(.horizontal, 16)
                    .frame(minHeight: 56)
                    .background(tt.c.surface, in: .rect(cornerRadius: 18))
                    .onChange(of: name) { _, v in if v.count > 20 { name = String(v.prefix(20)) } }
                    .padding(.top, 20)

                Text("Your character").font(tt.body(16, 800)).foregroundStyle(tt.c.ink).padding(.top, 16)
                Picker("Your character", selection: $style) {
                    Text("Boy").tag("boy")
                    Text("Girl").tag("girl")
                }
                .pickerStyle(.segmented)
                .padding(.top, 8)

                Text("Who's using this device?").font(tt.body(16, 800)).foregroundStyle(tt.c.ink).padding(.top, 16)
                Picker("Age group", selection: $age) {
                    Text("18+").tag("adult")
                    Text("13–17").tag("teen")
                    Text("Under 13").tag("child")
                }
                .pickerStyle(.segmented)
                .padding(.top, 8)

                SoftCard {
                    Text("Our privacy promise").font(tt.display(20)).foregroundStyle(tt.c.ink)
                    VStack(alignment: .leading, spacing: 10) {
                        promise("location.slash.fill", "No address or location — ever")
                        promise("camera.badge.ellipsis", "No camera and no photo uploads")
                        promise("lock.fill", "Your data is encrypted on this device")
                        promise("eye.slash.fill", "Sharing with friends is off until you turn it on")
                    }
                    .padding(.top, 10)
                }
                .padding(.top, 16)

                consentRow($agreed, "I agree to the Terms and Privacy Policy, and understand I can export or delete my data anytime in Settings.")
                    .padding(.top, 12)
                if needsParent {
                    consentRow($parent, "I'm a parent or guardian and I approve this account. Social features stay off for kids.")
                }

                Button("Build my home") {
                    store.giveConsent(name: name, ageGroup: age, parentApproved: parent, style: style)
                    if needsParent {
                        store.updateSettings {
                            $0.shareActivity = false
                            $0.shareProgress = false
                            $0.interHouseholdChallenge = false
                        }
                    }
                }
                .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 58))
                .disabled(!canGo)
                .padding(.top, 16)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(LinearGradient(colors: [tt.c.tangerineSoft, tt.c.canvas, tt.c.canvas], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
    }

    private func promise(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: 12) {
            IconTile(symbol: symbol, tint: tt.c.sky, background: tt.c.skySoft, size: 34)
            Text(text).font(tt.body(15, 500)).foregroundStyle(tt.c.ink)
        }
    }

    private func consentRow(_ value: Binding<Bool>, _ text: String) -> some View {
        Button {
            value.wrappedValue.toggle()
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: value.wrappedValue ? "checkmark.square.fill" : "square")
                    .font(.system(size: 22))
                    .foregroundStyle(value.wrappedValue ? tt.c.tangerine : tt.c.muted)
                Text(text).font(tt.body(15, 500)).foregroundStyle(tt.c.ink).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: value.wrappedValue)
        .accessibilityAddTraits(value.wrappedValue ? .isSelected : [])
    }
}
