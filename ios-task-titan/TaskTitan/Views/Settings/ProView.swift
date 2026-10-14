import SwiftUI

/// Pro plan overview. Core chore features are never behind the paywall.
struct ProView: View {
    @Environment(\.tt) private var tt
    @Environment(\.dismiss) private var dismiss
    @State private var plan = 1
    @State private var notice = false

    private let plans: [(String, String, String)] = [("Monthly", "$5.99 / month", ""), ("Yearly", "$47.99 / year", "Just $3.99 a month")]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 16, weight: .bold)).foregroundStyle(tt.c.ink)
                            .frame(width: 44, height: 44).background(tt.c.surface.opacity(0.7), in: .circle)
                    }
                    .accessibilityLabel("Close")
                }
                Image(systemName: "crown.fill").font(.system(size: 42)).foregroundStyle(.white)
                    .frame(width: 84, height: 84).background(tt.c.tangerine, in: .rect(cornerRadius: 26))
                    .symbolEffect(.bounce, value: notice)
                Text("Task Titan Pro").font(tt.display(34)).foregroundStyle(tt.c.ink).padding(.top, 14)
                Text("More home, more style. Chores themselves are always free.").font(tt.body(16)).foregroundStyle(tt.c.muted)
                SoftCard {
                    ForEach(["Premium outfits, pets, auras & decor", "Multiple households (family + roommates)", "Advanced private stats & trends", "Early access to seasonal events"], id: \.self) { feature($0) }
                }
                .padding(.top, 18)
                SoftCard(color: tt.c.mintSoft) {
                    Text("Free forever").font(tt.body(16, 800)).foregroundStyle(tt.c.ink).padding(.bottom, 6)
                    ForEach(["Unlimited chores, reminders & checklists", "Your 3D avatar and 3D house", "Shared household goal & recaps"], id: \.self) { feature($0) }
                }
                .padding(.top, 12)
                VStack(spacing: 10) {
                    ForEach(Array(plans.enumerated()), id: \.offset) { i, p in
                        Button { plan = i } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(p.0).font(tt.body(16, 800)).foregroundStyle(tt.c.ink)
                                    if !p.2.isEmpty { Text(p.2).font(tt.body(12, 700)).foregroundStyle(tt.c.mint) }
                                }
                                Spacer()
                                Text(p.1).font(tt.body(14, 700)).foregroundStyle(tt.c.ink)
                            }
                            .padding(16)
                            .background(tt.c.surface, in: .rect(cornerRadius: 22))
                            .overlay { if plan == i { RoundedRectangle(cornerRadius: 22).strokeBorder(tt.c.tangerine, lineWidth: 2.5) } }
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(plan == i ? .isSelected : [])
                    }
                }
                .padding(.top, 16)
                Button("Start Pro") { notice.toggle() }
                    .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 58))
                    .padding(.top, 14)
                Text("Cancel anytime in your App Store account.").font(tt.body(11, 700)).foregroundStyle(tt.c.muted)
                    .frame(maxWidth: .infinity).padding(.top, 8).padding(.bottom, 24)
            }
            .padding(.horizontal, 20)
        }
        .background(LinearGradient(colors: [tt.c.tangerineSoft, tt.c.canvas], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        .alert("Coming soon", isPresented: $notice) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Subscriptions open once in-app purchases are connected.")
        }
    }

    private func feature(_ text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark").font(.system(size: 12, weight: .heavy)).foregroundStyle(.white)
                .frame(width: 24, height: 24).background(tt.c.tangerine, in: .circle)
            Text(text).font(tt.body(16)).foregroundStyle(tt.c.ink)
        }
        .padding(.vertical, 5)
    }
}
