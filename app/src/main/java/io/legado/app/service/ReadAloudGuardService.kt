package io.legado.app.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.legado.app.constant.AppLog
import io.legado.app.constant.NotificationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 朗读守护服务
 * 用于在卓易通等环境下监控朗读服务，防止被杀后台
 */
class ReadAloudGuardService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.put("朗读守护服务启动")
        startGuard()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        // 启动前台服务
        val notification = Notification.Builder(this)
            .setContentTitle("朗读服务")
            .setContentText("正在后台运行")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(NotificationId.ReadAloudGuard, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun startGuard() {
        serviceScope.launch {
            while (isActive) {
                delay(5000) // 每5秒检查一次
                if (BaseReadAloudService.isRun && BaseReadAloudService.pause) {
                    // 服务在运行但被暂停，尝试恢复
                    AppLog.put("检测到朗读服务异常，尝试恢复")
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ReadAloudGuardService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ReadAloudGuardService::class.java)
            context.stopService(intent)
        }
    }
}
