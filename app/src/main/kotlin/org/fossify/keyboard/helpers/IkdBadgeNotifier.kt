package org.fossify.keyboard.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.fossify.keyboard.R
import org.fossify.keyboard.activities.DashboardActivity
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.helpers.IkdBadgeEvaluator.UnlockedBadge

/**
 * Phase 14 §7.5: posts a **local** notification for newly-unlocked
 * badges, in parallel with the in-app snackbar. Nothing leaves the
 * device — `NotificationManagerCompat.notify(...)` only, no network, no
 * `INTERNET` permission. The notification text is a static badge title /
 * description from `strings.xml`, never any captured data.
 *
 * Gating (Decision #6 / #11):
 *  - no-op when `Config.badgeNotificationsEnabled == false`,
 *  - no-op when the Android 13+ `POST_NOTIFICATIONS` runtime permission
 *    is not granted (the snackbar already covered the in-app case —
 *    graceful degradation).
 *
 * Tapping the notification opens `DashboardActivity` straight on the
 * Achievements tab via `EXTRA_OPEN_TAB` + `FLAG_IMMUTABLE`.
 */
object IkdBadgeNotifier {

    const val CHANNEL_ID = "moodscript_badges"
    private const val NOTIFICATION_ID = 47_014

    fun notify(context: Context, newlyUnlocked: List<UnlockedBadge>) {
        if (newlyUnlocked.isEmpty()) return
        if (!context.config.badgeNotificationsEnabled) return
        if (!hasPermission(context)) return

        ensureChannel(context)

        val firstDef = IkdBadgeCatalog.defFor(newlyUnlocked.first().key) ?: return
        val firstEmoji = firstDef.emoji
        val firstTitle = context.getString(firstDef.titleRes)
        val extraCount = newlyUnlocked.size - 1

        val title: String
        val text: String
        if (extraCount == 0) {
            title = context.getString(
                R.string.badge_notification_title_single, firstEmoji, firstTitle,
            )
            text = context.getString(firstDef.descRes)
        } else {
            title = context.getString(
                R.string.badge_notification_title_multi, newlyUnlocked.size,
            )
            text = context.getString(
                R.string.badge_notification_text_multi,
                firstEmoji, firstTitle, extraCount,
            )
        }

        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DashboardActivity.EXTRA_OPEN_TAB, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.badge_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }
}
