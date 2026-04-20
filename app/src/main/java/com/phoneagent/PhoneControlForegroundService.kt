package com.phoneagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 前台服务：保持 APP 在后台不被杀
 * 显示常驻通知「Phone Agent 运行中」
 */
class PhoneControlForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "phone_agent_service"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // 启动 WebSocket Server
        PhoneControlWebSocketServer.start(19876)
    }

    override fun onDestroy() {
        super.onDestroy()
        PhoneControlWebSocketServer.stop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 被杀后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 创建通知渠道（Android 8+） */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_notification_channel),
            NotificationManager.IMPORTANCE_LOW // 低优先级，不发出声音
        ).apply {
            description = getString(R.string.foreground_notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /** 构建常驻通知 */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 不可滑动关闭
            .build()
    }
}
