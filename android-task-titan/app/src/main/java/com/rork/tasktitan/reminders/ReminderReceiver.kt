package com.rork.tasktitan.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rork.tasktitan.MainActivity
import com.rork.tasktitan.R
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.data.SecureStore

/**
 * Handles two intents: a reminder alarm firing, and the notification's "Skip today"
 * action. On fire it reads the encrypted data directly (fast prefs read), re-arms the
 * next occurrence, and never notifies when the chore was already finished, was
 * deleted, was skipped, or reminders are switched off. Skip cancels exactly the next
 * occurrence of that chore's reminder — everything after that still fires normally.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val choreId = intent.getStringExtra(ReminderScheduler.EXTRA_CHORE_ID)
        Thread {
            try {
                val data = SecureStore(context).load() ?: return@Thread
                val chore = data.chores.firstOrNull { it.id == choreId } ?: return@Thread
                if (intent.action == ReminderScheduler.ACTION_SKIP) {
                    skipNext(context, chore, data)
                    return@Thread
                }
                if (!ReminderScheduler.active(data)) return@Thread
                fire(context, chore, data, intent.getLongExtra(ReminderScheduler.EXTRA_DAY, DateUtil.today()))
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** A fired alarm: suppress if skipped or already done, otherwise notify and re-arm. */
    private fun fire(context: Context, chore: com.rork.tasktitan.data.Chore, data: com.rork.tasktitan.data.AppData, targetDay: Long) {
        when {
            ReminderScheduler.skippedDay(context, chore.id) == targetDay -> ReminderScheduler.clearSkip(context, chore.id)
            data.completions.any { it.choreId == chore.id && it.epochDay == targetDay } -> Unit
            else -> notify(context, chore.id, chore.title, data.rooms.firstOrNull { it.id == chore.roomId }?.name)
        }
        ReminderScheduler.sync(context, data)
    }

    /** Mark the chore's next occurrence as skipped, drop the current notification, re-arm. */
    private fun skipNext(context: Context, chore: com.rork.tasktitan.data.Chore, data: com.rork.tasktitan.data.AppData) {
        ReminderScheduler.nextDueDay(chore, data)?.let { ReminderScheduler.skipNext(context, chore.id, it) }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(chore.title.hashCode())
        ReminderScheduler.sync(context, data)
    }

    companion object {
        fun notify(context: Context, choreId: String, title: String, room: String?) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ReminderScheduler.ensureChannel(context)
            val tapIntent = PendingIntent.getActivity(
                context,
                title.hashCode(),
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val skipIntent = PendingIntent.getBroadcast(
                context,
                title.hashCode(),
                Intent(context, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_SKIP).putExtra(ReminderScheduler.EXTRA_CHORE_ID, choreId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val body = if (room == null) "Ready when you are." else "Ready when you are — it's waiting in the $room."
            val notification: Notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tapIntent)
                .addAction(0, "Skip today", skipIntent)
                .setAutoCancel(true)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(title.hashCode(), notification)
        }
    }
}
