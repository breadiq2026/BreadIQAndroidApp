package com.BreadIQ.myapp.core

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.BreadIQ.myapp.R

/**
 * Fires when an `AlarmManager` alarm scheduled by
 * [BakeNotificationScheduler] goes off — the Android counterpart of
 * `UNCalendarNotificationTrigger` actually presenting the notification
 * that the source's `UNUserNotificationCenter.add(request)` call
 * scheduled ahead of time. Registered `exported="false"` in the
 * manifest — only this app's own `PendingIntent`s (built with an
 * explicit `Intent(context, BakeNotificationReceiver::class.java)`) ever
 * target it.
 *
 * Uses `ic_launcher_foreground` as the status-bar small icon — already a
 * single-color glyph (see that file's own doc comment), not a proper
 * dedicated monochrome notification icon. Good enough to ship; swap for
 * real notification-icon artwork as a later polish pass.
 */
class BakeNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(BakeNotificationScheduler.EXTRA_NOTIF_ID, -1)
        if (notifId == -1) return
        val title = intent.getStringExtra(BakeNotificationScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(BakeNotificationScheduler.EXTRA_BODY) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission was revoked between scheduling and firing —
            // nothing to post. Mirrors BakeNotificationScheduler.schedule's
            // own pre-check; belt and suspenders since an alarm, once
            // set, fires regardless of permission state at fire time.
            return
        }

        val notification = NotificationCompat.Builder(context, BakeNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
