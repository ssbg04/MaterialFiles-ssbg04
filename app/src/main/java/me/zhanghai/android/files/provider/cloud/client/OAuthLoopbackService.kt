/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.cloud.client

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.NotificationIds
import me.zhanghai.android.files.compat.stopForegroundCompat
import me.zhanghai.android.files.storage.EditCloudServerActivity
import me.zhanghai.android.files.util.NotificationChannelTemplate
import me.zhanghai.android.files.util.NotificationTemplate
import me.zhanghai.android.files.util.WakeWifiLock

val oauthLoopbackNotificationTemplate =
    NotificationTemplate(
        NotificationChannelTemplate(
            "oauth_loopback",
            R.string.notification_channel_oauth_loopback_name,
            NotificationManagerCompat.IMPORTANCE_LOW,
            descriptionRes = R.string.notification_channel_oauth_loopback_description,
            showBadge = false
        ),
        colorRes = R.color.color_primary,
        smallIcon = R.drawable.notification_icon,
        contentTitleRes = R.string.oauth_loopback_notification_title,
        ongoing = true,
        onlyAlertOnce = true,
        category = NotificationCompat.CATEGORY_SERVICE,
        priority = NotificationCompat.PRIORITY_LOW
    )

class OAuthLoopbackService : Service() {
    private lateinit var wakeWifiLock: WakeWifiLock

    override fun onCreate() {
        super.onCreate()
        wakeWifiLock = WakeWifiLock(OAuthLoopbackService::class.java.simpleName)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeWifiLock.isAcquired = true
        startForegroundNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeWifiLock.isInitialized) {
            wakeWifiLock.isAcquired = false
        }
        stopForegroundCompat(ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundNotification() {
        val contentIntent = Intent(this, EditCloudServerActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            NotificationIds.OAUTH_LOOPBACK,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = oauthLoopbackNotificationTemplate.createBuilder(this)
            .setContentText(getString(R.string.oauth_loopback_notification_text))
            .setContentIntent(contentPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationIds.OAUTH_LOOPBACK,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        } else {
            startForeground(NotificationIds.OAUTH_LOOPBACK, notification)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OAuthLoopbackService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, OAuthLoopbackService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
