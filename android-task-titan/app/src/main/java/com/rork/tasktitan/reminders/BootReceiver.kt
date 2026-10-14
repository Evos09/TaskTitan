package com.rork.tasktitan.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rork.tasktitan.data.SecureStore

/** Alarms don't survive reboots — rebuild every reminder from saved data. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                SecureStore(context).load()?.let { ReminderScheduler.sync(context, it) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
