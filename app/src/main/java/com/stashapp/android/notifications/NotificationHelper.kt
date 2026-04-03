package com.stashapp.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stashapp.android.MainActivity
import com.stashapp.android.R

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "expiration_alerts"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showExpirationSummary(itemNames: List<String>) {
        if (itemNames.isEmpty()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SCREEN", "expiring_soon")
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.label_expiring_soon)
        val contentText = if (itemNames.size == 1) {
            context.getString(R.string.notification_summary_single, itemNames[0])
        } else {
            val itemsString = itemNames.take(3).joinToString(", ")
            val moreCount = if (itemNames.size > 3) itemNames.size - 3 else 0
            if (moreCount > 0) {
                "$itemsString, ..." // Simplified for now, could use plural resources
            } else {
                itemsString
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground) // Use app icon from mipmap
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (itemNames.size > 1) {
            val bigText = itemNames.joinToString("\n")
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        with(NotificationManagerCompat.from(context)) {
            // Check for permission in modern Android versions if needed, 
            // but for now we assume it's handled or user will be prompted.
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}
