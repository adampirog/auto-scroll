package com.adampirog.auto_scroll_android

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.atomic.AtomicBoolean

class AutoScrollAndroidPlugin :
    FlutterPlugin,
    MethodChannel.MethodCallHandler {

    companion object {
        private const val CHANNEL = "auto_scroll_android/overlay"
        private const val TIMEOUT_MS = 1500L
    }

    private val handler = Handler(Looper.getMainLooper())

    private var context: Context? = null
    private var channel: MethodChannel? = null

    override fun onAttachedToEngine(
        binding: FlutterPlugin.FlutterPluginBinding
    ) {
        context = binding.applicationContext

        channel = MethodChannel(
            binding.binaryMessenger,
            CHANNEL
        ).also {
            it.setMethodCallHandler(this)
        }
    }

    override fun onDetachedFromEngine(
        binding: FlutterPlugin.FlutterPluginBinding
    ) {
        channel?.setMethodCallHandler(null)
        channel = null
        context = null
    }

    private fun isPermissionGranted(ctx: Context): Boolean {
        val manager = ctx.getSystemService(
            Context.ACCESSIBILITY_SERVICE
        ) as AccessibilityManager

        val serviceName =
            AutoScrollAccessibilityService::class.java.name

        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val service = info.resolveInfo.serviceInfo

            service.packageName == ctx.packageName &&
                service.name == serviceName
        }
    }

    private fun sendCommand(
        ctx: Context,
        command: String,
        direction: String? = null,
        speed: Double? = null,
        callback: (Boolean, Bundle?) -> Unit
    ) {
        val completed = AtomicBoolean(false)

        val timeout = Runnable {
            if (completed.compareAndSet(false, true)) {
                callback(false, null)
            }
        }

        val receiver = object : ResultReceiver(handler) {
            override fun onReceiveResult(
                resultCode: Int,
                resultData: Bundle?
            ) {
                if (completed.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)

                    callback(
                        resultCode ==
                            AutoScrollAccessibilityService.RESULT_OK,
                        resultData
                    )
                }
            }
        }

        val intent = Intent(
            AutoScrollAccessibilityService.ACTION_COMMAND
        ).apply {
            setPackage(ctx.packageName)

            putExtra(
                AutoScrollAccessibilityService.EXTRA_COMMAND,
                command
            )

            putExtra(
                AutoScrollAccessibilityService.EXTRA_REPLY,
                receiver
            )

            if (direction != null) {
                putExtra(
                    AutoScrollAccessibilityService.EXTRA_DIRECTION,
                    direction
                )
            }

            if (speed != null) {
                putExtra(
                    AutoScrollAccessibilityService.EXTRA_SPEED,
                    speed
                )
            }
        }

        handler.postDelayed(timeout, TIMEOUT_MS)

        try {
            ctx.sendBroadcast(intent)
        } catch (_: Exception) {
            handler.removeCallbacks(timeout)

            if (completed.compareAndSet(false, true)) {
                callback(false, null)
            }
        }
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        val ctx = context ?: run {
            result.error(
                "UNAVAILABLE",
                "Android context unavailable",
                null
            )
            return
        }

        try {
            when (call.method) {
                "get_state" -> {
                    val granted = isPermissionGranted(ctx)

                    if (!granted) {
                        result.success(
                            mapOf(
                                "permission_enabled" to false,
                                "service_ready" to false,
                                "overlay_visible" to false,
                                "playing" to false,
                                "direction" to "down",
                                "speed" to 1.0
                            )
                        )
                    } else {
                        sendCommand(
                            ctx = ctx,
                            command = "state"
                        ) { ready, data ->
                            result.success(
                                mapOf(
                                    "permission_enabled" to true,
                                    "service_ready" to ready,
                                    "overlay_visible" to (
                                        ready &&
                                            data?.getBoolean("visible") == true
                                    ),
                                    "playing" to (
                                        ready &&
                                            data?.getBoolean("playing") == true
                                    ),
                                    "direction" to (
                                        if (ready) {
                                            data?.getString("direction")
                                                ?: "down"
                                        } else {
                                            "down"
                                        }
                                    ),
                                    "speed" to (
                                        if (ready) {
                                            data?.getDouble("speed", 1.0)
                                                ?: 1.0
                                        } else {
                                            1.0
                                        }
                                    )
                                )
                            )
                        }
                    }
                }

                "request_permission" -> {
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    result.success(true)
                }

                "show_overlay" -> {
                    sendCommand(ctx, "show") { success, _ ->
                        result.success(success)
                    }
                }

                "hide_overlay" -> {
                    sendCommand(ctx, "hide") { success, _ ->
                        result.success(success)
                    }
                }

                "set_config" -> {
                    val direction =
                        call.argument<String>("direction")

                    val speed =
                        call.argument<Number>("speed")?.toDouble()

                    if (
                        direction !in listOf("up", "down") ||
                        speed == null ||
                        !speed.isFinite() ||
                        speed !in 0.5..5.0
                    ) {
                        result.error(
                            "INVALID_CONFIG",
                            "Direction or speed is invalid",
                            null
                        )
                        return
                    }

                    sendCommand(
                        ctx = ctx,
                        command = "configure",
                        direction = direction,
                        speed = speed
                    ) { success, _ ->
                        result.success(success)
                    }
                }

                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error(
                "ANDROID_ERROR",
                e.message,
                null
            )
        }
    }
}
