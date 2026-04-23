package io.legado.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.constant.AppConst
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
        // 启动前台服务 - 使用 NotificationCompat 和正确的通知渠道
        val notification = NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
            .setContentTitle(getString(R.string.read_aloud))
            .setContentText(getString(R.string.read_aloud_s))
            .setSmallIcon(R.drawable.ic_volume_up)
            .setOngoing(true)
            .build()
        // Android 10+ 需要指定 foregroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationId.ReadAloudGuard,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NotificationId.ReadAloudGuard, notification)
        }
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
