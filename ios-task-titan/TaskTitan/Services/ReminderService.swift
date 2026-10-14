import Foundation
import UserNotifications

/// Local "gentle reminder" notifications at each chore's scheduled time.
///
/// iOS can't run code when a notification fires, so instead of re-arming after each
/// fire (Android's approach) we pre-schedule the upcoming occurrences of every chore,
/// within the system's 64-pending-request limit, and re-sync whenever data changes or
/// the app becomes active. That makes the Settings toggle, the one-week pause (resumes
/// automatically because occurrences after the pause are already queued), "Skip today",
/// and completed-today suppression all work without the app being open.
@MainActor
final class ReminderService: NSObject {
    static let shared = ReminderService()

    static let categoryId = "CHORE_REMINDER"
    static let skipActionId = "SKIP_TODAY"
    private static let idPrefix = "chore."
    private static let maxPending = 60
    private static let perChore = 4
    private static let horizonDays = 21
    private let skipsKey = "reminder_skips"

    private let center = UNUserNotificationCenter.current()
    private var latest: AppData?

    func configure() {
        center.delegate = self
        let skip = UNNotificationAction(identifier: Self.skipActionId, title: "Skip today", options: [])
        let category = UNNotificationCategory(identifier: Self.categoryId, actions: [skip], intentIdentifiers: [], options: [])
        center.setNotificationCategories([category])
    }

    func requestPermission() {
        center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// Reminders run when the Settings toggle is on and no pause covers [day].
    static func active(_ data: AppData, on day: Int = DateUtil.today()) -> Bool {
        let s = data.settings
        return s.reminders && !(s.reminderPausedUntil > 0 && day <= s.reminderPausedUntil)
    }

    // MARK: Skips (chore id → the one epoch day whose nudge is skipped)

    private var skips: [String: Int] {
        get { (UserDefaults.standard.dictionary(forKey: skipsKey) as? [String: Int]) ?? [:] }
        set { UserDefaults.standard.set(newValue, forKey: skipsKey) }
    }

    /// Upcoming due days for a chore with a reminder, excluding done/skipped/paused days.
    static func upcoming(_ chore: Chore, data: AppData, skippedDay: Int?, limit: Int, now: Date = Date()) -> [(day: Int, date: Date)] {
        guard data.settings.reminders, let minute = chore.remindMinuteOfDay else { return [] }
        let done = Set(data.completions.filter { $0.choreId == chore.id }.map(\.epochDay))
        let today = DateUtil.today(now)
        var out: [(Int, Date)] = []
        for day in today...(today + horizonDays) where out.count < limit {
            guard GameRules.isDue(chore, day: day), !done.contains(day), day != skippedDay, active(data, on: day) else { continue }
            guard let date = DateUtil.localDate(day: day, minuteOfDay: minute), date > now else { continue }
            out.append((day, date))
        }
        return out
    }

    /// The next nudge time shown under chore rows; nil when silenced.
    func nextNudge(_ chore: Chore, data: AppData) -> Date? {
        Self.upcoming(chore, data: data, skippedDay: skips[chore.id], limit: 1).first?.date
    }

    /// Rebuild every pending reminder from [data].
    func sync(_ data: AppData) {
        latest = data
        let today = DateUtil.today()
        // Drop skips that are in the past.
        skips = skips.filter { $0.value >= today }
        let skipMap = skips

        var requests: [UNNotificationRequest] = []
        // Interleave by time so the soonest nudges always fit under the system cap.
        var all: [(chore: Chore, day: Int, date: Date)] = []
        for chore in data.chores {
            for occ in Self.upcoming(chore, data: data, skippedDay: skipMap[chore.id], limit: Self.perChore) {
                all.append((chore, occ.day, occ.date))
            }
        }
        all.sort { $0.date < $1.date }
        for item in all.prefix(Self.maxPending) {
            let content = UNMutableNotificationContent()
            content.title = item.chore.title
            let room = data.rooms.first { $0.id == item.chore.roomId }?.name
            content.body = room.map { "Ready when you are — it's waiting in the \($0)." } ?? "Ready when you are."
            content.sound = .default
            content.categoryIdentifier = Self.categoryId
            content.userInfo = ["choreId": item.chore.id, "day": item.day]
            content.threadIdentifier = item.chore.id
            let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: item.date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            requests.append(UNNotificationRequest(identifier: "\(Self.idPrefix)\(item.chore.id).\(item.day)", content: content, trigger: trigger))
        }

        let center = self.center
        center.getPendingNotificationRequests { pending in
            let ours = pending.map(\.identifier).filter { $0.hasPrefix(Self.idPrefix) }
            center.removePendingNotificationRequests(withIdentifiers: ours)
            requests.forEach { center.add($0) }
        }
        // Clear any delivered nudge for chores already finished today.
        let doneToday = data.completions.filter { $0.epochDay == today }.map { "\(Self.idPrefix)\($0.choreId).\(today)" }
        if !doneToday.isEmpty { center.removeDeliveredNotifications(withIdentifiers: doneToday) }
    }

    /// "Skip today": cancel only the chore's next occurrence; later nudges stay.
    func skipNext(choreId: String, firedDay: Int?) {
        guard let data = latest ?? SecureStore().load(),
              let chore = data.chores.first(where: { $0.id == choreId }) else { return }
        // If the nudge for today already showed, skip the following occurrence (matches Android).
        let after = firedDay ?? DateUtil.today()
        let done = Set(data.completions.filter { $0.choreId == chore.id }.map(\.epochDay))
        var next: Int?
        for day in (after + 1)...(after + 370) where GameRules.isDue(chore, day: day) && !done.contains(day) {
            next = day
            break
        }
        guard let next else { return }
        var s = skips
        s[choreId] = next
        skips = s
        sync(data)
    }

    func cancelAll() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
        UserDefaults.standard.removeObject(forKey: skipsKey)
    }
}

extension ReminderService: UNUserNotificationCenterDelegate {
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let choreId = info["choreId"] as? String
        let day = info["day"] as? Int
        let action = response.actionIdentifier
        Task { @MainActor in
            if action == ReminderService.skipActionId, let choreId {
                ReminderService.shared.skipNext(choreId: choreId, firedDay: day)
            }
            completionHandler()
        }
    }
}
