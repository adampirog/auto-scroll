package com.adampirog.autoscroll

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.accessibility.AccessibilityManager
import com.adampirog.auto_scroll_android.AutoScrollAccessibilityService as ScrollService
import java.util.concurrent.atomic.AtomicBoolean

/** Small one-shot cross-process client; there is no polling and no background worker. */
internal class ServiceClient(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())

    fun permissionEnabled(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val service = info.resolveInfo.serviceInfo
            service.packageName == context.packageName &&
                service.name == ScrollService::class.java.name
        }
    }

    fun request(
        command: String,
        direction: String? = null,
        speed: Double? = null,
        reply: (Boolean, Bundle?) -> Unit
    ) {
        val finished = AtomicBoolean(false)
        val timeout = Runnable {
            if (finished.compareAndSet(false, true)) reply(false, null)
        }
        val receiver = object : ResultReceiver(handler) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (finished.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)
                    reply(resultCode == ScrollService.RESULT_OK, resultData)
                }
            }
        }
        val intent = Intent(ScrollService.ACTION_COMMAND).apply {
            setPackage(context.packageName)
            putExtra(ScrollService.EXTRA_COMMAND, command)
            putExtra(ScrollService.EXTRA_REPLY, receiver)
            if (direction != null) putExtra(ScrollService.EXTRA_DIRECTION, direction)
            if (speed != null) putExtra(ScrollService.EXTRA_SPEED, speed)
        }
        handler.postDelayed(timeout, 2000L)
        try {
            context.sendBroadcast(intent)
        } catch (_: Exception) {
            handler.removeCallbacks(timeout)
            if (finished.compareAndSet(false, true)) reply(false, null)
        }
    }
}
