package io.legado.app.base

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.CoroutineContext

abstract class BaseService : LifecycleService() {

    private val simpleName = this::class.simpleName.toString()
    private var isForeground = false

    /**
     * 是否在任务移除时停止服务
     * 子类可重写此属性，如朗读服务需要保持后台运行时应返回 false
     */
    protected open val stopOnTaskRemoved: Boolean
        get() = true

    /**
     * 是否在服务被系统杀死后自动重启
     * 子类可重写此属性
     */
    protected open val autoRestartOnKill: Boolean
        get() = false

    fun <T> execute(
        scope: CoroutineScope = lifecycleScope,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
        semaphore: Semaphore? = null,
        block: suspend CoroutineScope.() -> T
    ) = Coroutine.async(scope, context, start, executeContext, semaphore, block)

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        LifecycleHelp.onServiceCreate(this)
        if (!AppConfig.permissionChecked) {
            AppConfig.permissionChecked = true
            checkPermission()
        }
    }

    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.d(simpleName) {
            "onStartCommand $intent ${intent?.toUri(0)}"
        }
        if (!isForeground) {
            startForegroundNotification()
            isForeground = true
        }
        // 返回 START_STICKY 确保服务被系统杀死后能重启
        return Service.START_STICKY
    }

    @CallSuper
    override fun onTaskRemoved(rootIntent: Intent?) {
        LogUtils.d(simpleName, "onTaskRemoved, stopOnTaskRemoved=$stopOnTaskRemoved")
        super.onTaskRemoved(rootIntent)
        // 对于需要后台驻留的服务（如朗读服务），不停止服务
        if (stopOnTaskRemoved) {
            stopSelf()
        } else if (autoRestartOnKill) {
            // 卓易通等环境可能需要重启服务
            AppLog.put("服务被移除，准备重启: $simpleName")
            val intent = Intent(applicationContext, this::class.java)
            applicationContext.startService(intent)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        LifecycleHelp.onServiceDestroy(this)
    }

    @CallSuper
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        LogUtils.d(simpleName, "onTimeout startId:$startId fgsType:$fgsType")
        stopSelf()
    }

    /**
     * 开启前台服务并发送通知
     */
    open fun startForegroundNotification() {

    }

    /**
     * 检测通知权限和后台权限
     */
    private fun checkPermission() {
        PermissionsCompat.Builder()
            .addPermissions(Permissions.POST_NOTIFICATIONS)
            .rationale(R.string.notification_permission_rationale)
            .onGranted {
                if (lifecycleScope.isActive) {
                    startForegroundNotification()
                }
            }
            .request()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionsCompat.Builder()
                .addPermissions(Permissions.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .rationale(R.string.ignore_battery_permission_rationale)
                .request()
        }
    }
}
