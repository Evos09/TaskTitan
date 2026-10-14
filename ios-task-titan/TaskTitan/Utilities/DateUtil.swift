import Foundation

/// Epoch-day helpers (local calendar days since 1970-01-01), mirroring the Android app.
nonisolated enum DateUtil {
    private static let dayMs: Int64 = 86_400_000
    private static let shortDays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    private static let months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

    private static var utc: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC") ?? .current
        return cal
    }()

    static func today(_ now: Date = Date()) -> Int {
        let ms = Int64(now.timeIntervalSince1970 * 1000)
        let offset = Int64(TimeZone.current.secondsFromGMT(for: now)) * 1000
        return Int((ms + offset) / dayMs)
    }

    private static func date(_ day: Int) -> Date { Date(timeIntervalSince1970: Double(day) * 86_400) }

    static func dayOfMonth(_ day: Int) -> Int { utc.component(.day, from: date(day)) }

    static func lengthOfMonth(_ day: Int) -> Int { utc.range(of: .day, in: .month, for: date(day))?.count ?? 30 }

    /// 0 = Sunday … 6 = Saturday
    static func dayOfWeek(_ day: Int) -> Int { utc.component(.weekday, from: date(day)) - 1 }

    static func month(_ day: Int) -> Int { utc.component(.month, from: date(day)) - 1 }

    static func shortDay(_ day: Int) -> String { shortDays[dayOfWeek(day)] }

    static func label(_ day: Int) -> String { "\(months[month(day)]) \(dayOfMonth(day))" }

    static func weekStart(_ day: Int) -> Int { day - dayOfWeek(day) }

    static func hourOfDay() -> Int { Calendar.current.component(.hour, from: Date()) }

    /// Local `Date` for a given epoch day at a minute-of-day (DST-safe via Calendar).
    static func localDate(day: Int, minuteOfDay: Int) -> Date? {
        var comps = utc.dateComponents([.year, .month, .day], from: date(day))
        comps.hour = minuteOfDay / 60
        comps.minute = minuteOfDay % 60
        comps.timeZone = .current
        return Calendar.current.date(from: comps)
    }

    /// 540 → "9:00 AM"
    static func formatMinute(_ minuteOfDay: Int) -> String {
        let h = min(max(minuteOfDay / 60, 0), 23)
        let m = minuteOfDay % 60
        let amPm = h < 12 ? "AM" : "PM"
        let h12 = h % 12 == 0 ? 12 : h % 12
        return "\(h12):\(String(format: "%02d", m)) \(amPm)"
    }
}
