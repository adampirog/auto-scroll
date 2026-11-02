package com.adampirog.auto_scroll_android

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class AutoScrollAndroidPlugin :
    FlutterPlugin,
    MethodChannel.MethodCallHandler {

    private var context: Context? = null
    private var channel: MethodChannel? = null
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    private var speed = 1.0

    override fun onAttachedToEngine(
        binding: FlutterPlugin.FlutterPluginBinding
    ) {
        context = binding.applicationContext
        channel = MethodChannel(
            binding.binaryMessenger,
            "auto_scroll_android/overlay"
        ).also {
            it.setMethodCallHandler(this)
        }
    }

    override fun onDetachedFromEngine(
        binding: FlutterPlugin.FlutterPluginBinding
    ) {
        hideOverlay()
        channel?.setMethodCallHandler(null)
        channel = null
        context = null
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        val ctx = context

        if (ctx == null) {
            result.error("UNAVAILABLE", "No Android context", null)
            return
        }

        try {
            when (call.method) {
                "overlay_permission_granted" -> {
                    result.success(Settings.canDrawOverlays(ctx))
                }

                "request_overlay_permission" -> {
                    if (Settings.canDrawOverlays(ctx)) {
                        result.success(true)
                    } else {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        ctx.startActivity(intent)
                        result.success(false)
                    }
                }

                "show_overlay" -> {
                    result.success(showOverlay(ctx))
                }

                "hide_overlay" -> {
                    hideOverlay()
                    result.success(true)
                }

                "is_overlay_visible" -> {
                    result.success(overlay != null)
                }

                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("OVERLAY_ERROR", e.message, null)
        }
    }

    private fun showOverlay(ctx: Context): Boolean {
        if (overlay != null) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        if (!Settings.canDrawOverlays(ctx)) return false

        val wm = ctx.getSystemService(
            Context.WINDOW_SERVICE
        ) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (ctx.resources.displayMetrics.widthPixels - dp(ctx, 240))
                .coerceAtLeast(0)
            y = dp(ctx, 120)
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(ctx, 14).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#DEE6EC"))
            }
            elevation = dp(ctx, 8).toFloat()
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        val speedRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        val dragHandle = button(ctx, "⋮⋮  AutoScroll") {}
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0

        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val width = ctx.resources.displayMetrics.widthPixels
                    val height = ctx.resources.displayMetrics.heightPixels

                    params.x = (
                        startX + (event.rawX - startRawX).toInt()
                    ).coerceIn(0, (width - root.width).coerceAtLeast(0))

                    params.y = (
                        startY + (event.rawY - startRawY).toInt()
                    ).coerceIn(0, (height - root.height).coerceAtLeast(0))

                    wm.updateViewLayout(root, params)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }

        val quickSwipe = button(ctx, "↓") {
            sendSwipe("down")
        }

        var expanded = false

        val expandButton = button(ctx, "⌄") {
            expanded = !expanded
            actions.visibility = if (expanded) View.VISIBLE else View.GONE
            speedRow.visibility = if (expanded) View.VISIBLE else View.GONE
            wm.updateViewLayout(root, params)
        }

        val closeButton = button(ctx, "×") {
            hideOverlay()
        }

        header.addView(dragHandle)
        header.addView(quickSwipe)
        header.addView(expandButton)
        header.addView(closeButton)

        actions.addView(button(ctx, "↑  Up") {
            sendSwipe("up")
        })

        actions.addView(button(ctx, "↓  Down") {
            sendSwipe("down")
        })

        val speedText = button(ctx, "1.0×") {}

        val speedSlider = SeekBar(ctx).apply {
            max = 10
            progress = 2
            layoutParams = LinearLayout.LayoutParams(
                dp(ctx, 140),
                dp(ctx, 45)
            )
        }

        speedSlider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    speed = 0.5 + progress * 0.25
                    speedText.text = String.format(
                        java.util.Locale.US,
                        "%.2f×",
                        speed
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        speedRow.addView(speedText)
        speedRow.addView(speedSlider)

        root.addView(header)
        root.addView(actions)
        root.addView(speedRow)

        wm.addView(root, params)

        windowManager = wm
        overlay = root

        return true
    }

    private fun sendSwipe(direction: String) {
        channel?.invokeMethod(
            "overlay_swipe",
            mapOf(
                "direction" to direction,
                "speed" to speed
            )
        )
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        windowManager?.removeView(view)
        overlay = null
        windowManager = null
    }

    private fun button(
        ctx: Context,
        text: String,
        action: () -> Unit
    ): TextView {
        return TextView(ctx).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#152238"))
            gravity = Gravity.CENTER
            setPadding(
                dp(ctx, 10),
                dp(ctx, 10),
                dp(ctx, 10),
                dp(ctx, 10)
            )
            setOnClickListener { action() }
        }
    }

    private fun dp(ctx: Context, value: Int): Int {
        return (value * ctx.resources.displayMetrics.density).toInt()
    }
}
