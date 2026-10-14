package com.rork.tasktitan.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rork.tasktitan.data.AppData
import com.rork.tasktitan.data.Chore
import com.rork.tasktitan.data.DateUtil
import java.util.Calendar
import java.util.TimeZone

/**
 * Schedules one local alarm per chore that has a reminder time, always for the
 * next due occurrence only. The receiver re-arms the next occurrence after each
 * fire, so at most a handful of alarms exist at any time. Fully local — no push,
 * no network. Nothing is scheduled when the Settings toggle is off or while the
 * one-week pause is active; both resume automatically.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "gentle_reminders"
    const val EXTRA_CHORE_ID = "choreId"
    const val EXTRA_DAY = "epochDay"
    const val ACTION_FIRE = "com.rork.tasktitan.FIRE_REMINDER"
    const val ACTION_SKIP = "com.rork.tasktitan.SKIP_REMINDER"
    private const val PREFS = "task_titan_reminders"
    private const val KEY_SCHEDULED = "scheduled_ids"
    private const val DAY_MS = 86_400_000L

    fun ensureChannel(context: Context) {
        // Notification channels arrived in API 26; older versions post without one.
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gentle reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Friendly nudges at a chore's scheduled time"
                },
            )
        }
    }

    private fun skipPrefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether reminders are currently active — the Settings toggle AND not paused. */
    fun active(data: AppData): Boolean {
        val paused = data.settings.reminderPausedUntil > 0L && DateUtil.today() <= data.settings.reminderPausedUntil
        return data.settings.reminders && !paused
    }

    /** Reconcile alarms with [data]: cancels stale ones, schedules the next occurrence for each. */
    fun sync(context: Context, data: AppData) {
        val prefs = skipPrefs(context)
        val previouslyScheduled = prefs.getStringSet(KEY_SCHEDULED, emptySet()) ?: emptySet()
        val active = data.chores.map { it.id }.toSet()
        (previouslyScheduled - active).forEach { cancel(context, it) }

        val nowScheduled = mutableSetOf<String>()
        val on = active(data)
        data.chores.forEach { chore ->
            val next = if (on) nextOccurrence(chore, data) else null
            if (next != null) {
                schedule(context, chore, next.first, next.second)
                nowScheduled.add(chore.id)
            } else {
                cancel(context, chore.id)
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, nowScheduled).apply()
    }

    fun cancel(context: Context, choreId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(fireIntent(context, choreId))
    }

    private fun fireIntent(context: Context, choreId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            choreId.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_CHORE_ID, choreId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(context: Context, chore: Chore, triggerAtMillis: Long, epochDay: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = fireIntent(context, chore.id)
        val canBeExact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        if (canBeExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            // Exact alarms not granted: fire within a window instead. Still respects the scheduled time approximately.
            manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60_000L, pi)
        }
        // Remember the intended day so a skip for that occurrence can be recognized on fire.
        skipPrefs(context).edit().putLong("day_${chore.id}", epochDay).apply()
    }

    /** The epoch day a pending alarm is for, or -1 when none is recorded. */
    fun pendingDay(context: Context, choreId: String): Long =
        skipPrefs(context).getLong("day_$choreId", -1L)

    fun skipNext(context: Context, choreId: String, nextDay: Long) {
        skipPrefs(context).edit().putLong("skip_$choreId", nextDay).apply()
    }

    fun skippedDay(context: Context, choreId: String): Long =
        skipPrefs(context).getLong("skip_$choreId", -1L)

    fun clearSkip(context: Context, choreId: String) {
        skipPrefs(context).edit().remove("skip_$choreId").apply()
    }

    /** Next epoch day (strictly after [after]) on which this chore is due and not already done. */
    fun nextDueDay(chore: Chore, data: AppData, after: Long = DateUtil.today()): Long? {
        val doneDays = data.completions.filter { it.choreId == chore.id }.map { it.epochDay }.toSet()
        for (offset in 1..370) {
            val day = after + offset
            if (com.rork.tasktitan.data.GameRules.isDue(chore, day) && day !in doneDays) return day
        }
        return null
    }

    /**
     * The next time this chore is due at its reminder time and not already done.
     * Returns null when the chore has no reminder time or nothing is schedulable.
     */
    fun nextOccurrence(chore: Chore, data: AppData, nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        val minute = chore.remindMinuteOfDay ?: return null
        val doneDays = data.completions.filter { it.choreId == chore.id }.map { it.epochDay }.toSet()
        val today = DateUtil.today()
        val zone = TimeZone.getDefault()
        // Search forward for the next due day; long custom intervals and month boundaries mean
        // the reminder can be more than a day away.
        for (offset in 0..370) {
            val day = today + offset
            if (!com.rork.tasktitan.data.GameRules.isDue(chore, day)) continue
            if (day in doneDays) continue
            // Local midnight of the target epoch-day (noon-UTC offset is the date's offset,
            // so DST transition dates resolve correctly).
            val utcNoon = day * DAY_MS
            val cal = Calendar.getInstance().apply {
                timeInMillis = utcNoon - zone.getOffset(utcNoon)
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val trigger = cal.timeInMillis
            if (trigger > nowMillis) return trigger to day
        }
        return null
    }
}
