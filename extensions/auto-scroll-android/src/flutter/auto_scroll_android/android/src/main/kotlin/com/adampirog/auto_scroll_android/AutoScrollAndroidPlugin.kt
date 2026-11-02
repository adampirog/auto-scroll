package com.adampirog.auto_scroll_android

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.Locale
import kotlin.math.abs

class AutoScrollAndroidPlugin :
    FlutterPlugin,
    MethodChannel.MethodCallHandler {

    companion object {
        private const val CHANNEL = "auto_scroll_android/overlay"

        private val TEAL = Color.rgb(7, 150, 184)
        private val INK = Color.rgb(21, 34, 56)
        private val MUTED = Color.rgb(101, 117, 139)
        private val PALE = Color.rgb(233, 247, 251)
        private val SURFACE = Color.rgb(243, 247, 250)
        private val BORDER = Color.rgb(222, 231, 237)
    }

    private val handler = Handler(Looper.getMainLooper())

    private var context: Context? = null
    private var channel: MethodChannel? = null
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    private var playButton: GlyphButton? = null
    private var upButton: TextView? = null
    private var downButton: TextView? = null

    private var direction = "down"
    private var speed = 1.0
    private var playing = false
    private var pending = false
    private var commandVersion = 0
    private var receiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                pauseScrolling()
            }
        }
    }

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
        hideOverlay()
        channel?.setMethodCallHandler(null)
        channel = null
        context = null
    }

    private fun hasAccessibilityPermission(ctx: Context): Boolean {
        val manager = ctx.getSystemService(
            Context.ACCESSIBILITY_SERVICE
        ) as AccessibilityManager

        val serviceClass = AutoScrollAccessibilityService::class.java.name

        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val service = info.resolveInfo.serviceInfo

            service.packageName == ctx.packageName &&
                service.name == serviceClass
        }
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        val ctx = context ?: run {
            result.error("UNAVAILABLE", "Android context unavailable", null)
            return
        }

        try {
            when (call.method) {
                "is_enabled" -> {
                    result.success(hasAccessibilityPermission(ctx))
                }

                "request_permission" -> {
                    if (hasAccessibilityPermission(ctx)) {
                        result.success(true)
                    } else {
                        ctx.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        result.success(false)
                    }
                }

                "dispatch_swipe" -> {
                    val direction = call.argument<String>("direction")
                    val speed = call.argument<Double>("speed")

                    if (direction == null || speed == null) {
                        result.error("INVALID", "Invalid gesture", null)
                        return
                    }

                    AutoScrollAccessibilityService.swipe(
                        direction,
                        speed
                    ) { completed ->
                        result.success(completed)
                    }
                }

                "overlay_permission_granted" -> {
                    result.success(Settings.canDrawOverlays(ctx))
                }

                "request_overlay_permission" -> {
                    if (Settings.canDrawOverlays(ctx)) {
                        result.success(true)
                    } else {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        result.success(false)
                    }
                }

                "show_overlay" -> result.success(showOverlay(ctx))

                "hide_overlay" -> {
                    hideOverlay()
                    result.success(true)
                }

                "is_overlay_visible" -> {
                    result.success(overlay != null)
                }

                "scroll_finished" -> {
                    commandVersion++
                    pending = false
                    setPlaying(false)
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("ANDROID_ERROR", e.message, null)
        }
    }

    private fun showOverlay(ctx: Context): Boolean {
        if (overlay != null) return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        if (!Settings.canDrawOverlays(ctx)) {
            return false
        }

        val wm = ctx.getSystemService(
            Context.WINDOW_SERVICE
        ) as WindowManager

        val screenWidth = ctx.resources.displayMetrics.widthPixels
        val collapsedWidth = dp(ctx, 124)
        val expandedWidth = dp(ctx, 280)
            .coerceAtMost(
                (screenWidth - dp(ctx, 24)).coerceAtLeast(1)
            )

        val params = WindowManager.LayoutParams(
            collapsedWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (screenWidth - collapsedWidth - dp(ctx, 16))
                .coerceAtLeast(0)
            y = dp(ctx, 120)
        }

        val root = DragPanel(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(ctx, 8),
                dp(ctx, 8),
                dp(ctx, 8),
                dp(ctx, 8)
            )
            background = shape(ctx, Color.WHITE, 22f, BORDER)
            elevation = dp(ctx, 10).toFloat()
        }

        root.onDrag = { dx, dy ->
            val screen = ctx.resources.displayMetrics

            params.x = (params.x + dx.toInt()).coerceIn(
                0,
                (screen.widthPixels - root.width).coerceAtLeast(0)
            )
            params.y = (params.y + dy.toInt()).coerceIn(
                0,
                (screen.heightPixels - root.height).coerceAtLeast(0)
            )

            wm.updateViewLayout(root, params)
        }

        val titleRow = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(ctx, 6), 0, 0, dp(ctx, 6))
        }

        titleRow.addView(
            label(ctx, "AutoScroll", 15f, INK, true),
            LinearLayout.LayoutParams(0, dp(ctx, 38), 1f)
        )

        titleRow.addView(
            iconButton(ctx, Glyph.CLOSE, MUTED, SURFACE, 36).apply {
                contentDescription = "Close floating controls"
                setOnClickListener { hideOverlay() }
            }
        )

        val controlsRow = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        playButton = iconButton(
            ctx,
            Glyph.PLAY,
            Color.WHITE,
            TEAL,
            48
        ).apply {
            contentDescription = "Start scrolling"
            setOnClickListener { toggleScrolling() }
        }

        controlsRow.addView(playButton)

        controlsRow.addView(
            View(ctx),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        val expandButton = iconButton(
            ctx,
            Glyph.EXPAND,
            INK,
            PALE,
            44
        ).apply {
            contentDescription = "Expand floating controls"
        }

        controlsRow.addView(expandButton)

        val details = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(
                dp(ctx, 6),
                dp(ctx, 14),
                dp(ctx, 6),
                dp(ctx, 6)
            )
        }

        var expanded = false

        expandButton.setOnClickListener {
            expanded = !expanded

            titleRow.visibility = if (expanded) View.VISIBLE else View.GONE
            details.visibility = if (expanded) View.VISIBLE else View.GONE

            expandButton.glyph = if (expanded) {
                Glyph.COLLAPSE
            } else {
                Glyph.EXPAND
            }

            expandButton.contentDescription = if (expanded) {
                "Collapse floating controls"
            } else {
                "Expand floating controls"
            }

            params.width = if (expanded) {
                expandedWidth
            } else {
                collapsedWidth
            }

            params.x = params.x.coerceIn(
                0,
                (screenWidth - params.width).coerceAtLeast(0)
            )

            wm.updateViewLayout(root, params)
        }

        details.addView(
            label(ctx, "DIRECTION", 11f, MUTED, true)
        )

        val directionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(ctx, SURFACE, 12f)
            setPadding(
                dp(ctx, 4),
                dp(ctx, 4),
                dp(ctx, 4),
                dp(ctx, 4)
            )
        }

        upButton = directionButton(ctx, "↑  Up", "up")
        downButton = directionButton(ctx, "↓  Down", "down")

        directionRow.addView(
            upButton,
            LinearLayout.LayoutParams(0, dp(ctx, 42), 1f)
        )
        directionRow.addView(
            downButton,
            LinearLayout.LayoutParams(0, dp(ctx, 42), 1f)
        )

        details.addView(
            directionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(ctx, 8)
                bottomMargin = dp(ctx, 16)
            }
        )

        updateDirectionButtons()

        val speedHeader = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        speedHeader.addView(
            label(ctx, "SPEED", 11f, MUTED, true),
            LinearLayout.LayoutParams(0, dp(ctx, 28), 1f)
        )

        val speedValue = label(ctx, "1.0×", 14f, TEAL, true)
        speedHeader.addView(speedValue)
        details.addView(speedHeader)

        val slider = SeekBar(ctx).apply {
            max = 9
            progress = 1
            progressTintList = ColorStateList.valueOf(TEAL)
            thumbTintList = ColorStateList.valueOf(TEAL)
            progressBackgroundTintList = ColorStateList.valueOf(BORDER)
        }

        slider.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    speed = 0.5 + progress * 0.5
                    speedValue.text = String.format(
                        Locale.US,
                        "%.1f×",
                        speed
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    sendConfig()
                }
            }
        )

        details.addView(slider)

        val limits = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        limits.addView(
            label(ctx, "0.5×", 11f, MUTED),
            LinearLayout.LayoutParams(0, dp(ctx, 20), 1f)
        )
        limits.addView(label(ctx, "5.0×", 11f, MUTED))
        details.addView(limits)

        root.addView(titleRow)
        root.addView(controlsRow)
        root.addView(details)

        wm.addView(root, params)

        windowManager = wm
        overlay = root
        pending = false
        setPlaying(false)

        ctx.registerReceiver(
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF)
        )
        receiverRegistered = true

        return true
    }

    private fun toggleScrolling() {
        if (pending) return

        if (playing) {
            pauseScrolling()
            return
        }

        val nativeChannel = channel ?: return
        val version = ++commandVersion

        pending = true
        playButton?.alpha = 0.55f

        nativeChannel.invokeMethod(
            "scroll_start",
            config(),
            object : MethodChannel.Result {
                override fun success(result: Any?) {
                    handler.post {
                        if (version != commandVersion) return@post

                        pending = false
                        playButton?.alpha = 1f

                        if (result == true) {
                            setPlaying(true)
                        } else {
                            setPlaying(false)
                            toast("Accessibility is unavailable")
                        }
                    }
                }

                override fun error(
                    code: String,
                    message: String?,
                    details: Any?
                ) {
                    handler.post {
                        if (version != commandVersion) return@post

                        pending = false
                        playButton?.alpha = 1f
                        setPlaying(false)
                        toast(message ?: "Unable to start scrolling")
                    }
                }

                override fun notImplemented() {
                    handler.post {
                        if (version != commandVersion) return@post

                        pending = false
                        playButton?.alpha = 1f
                        setPlaying(false)
                    }
                }
            }
        )
    }

    private fun pauseScrolling() {
        commandVersion++
        pending = false
        setPlaying(false)
        channel?.invokeMethod("scroll_stop", null)
    }

    private fun setPlaying(value: Boolean) {
        playing = value
        playButton?.glyph = if (value) Glyph.PAUSE else Glyph.PLAY
        playButton?.alpha = 1f
        playButton?.contentDescription = if (value) {
            "Pause scrolling"
        } else {
            "Start scrolling"
        }
    }

    private fun sendConfig() {
        channel?.invokeMethod("scroll_config", config())
    }

    private fun config(): Map<String, Any> = mapOf(
        "speed" to speed,
        "direction" to direction
    )

    private fun directionButton(
        ctx: Context,
        text: String,
        value: String
    ): TextView {
        return label(ctx, text, 14f, INK, true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            contentDescription = "Scroll $value"
            setOnClickListener {
                direction = value
                updateDirectionButtons()
                sendConfig()
            }
        }
    }

    private fun updateDirectionButtons() {
        fun update(button: TextView?, selected: Boolean) {
            button ?: return

            button.setTextColor(if (selected) TEAL else MUTED)
            button.background = shape(
                context ?: return,
                if (selected) PALE else SURFACE,
                10f
            )
        }

        update(upButton, direction == "up")
        update(downButton, direction == "down")
    }

    private fun hideOverlay() {
        pauseScrolling()

        overlay?.let {
            windowManager?.removeView(it)
        }

        overlay = null
        windowManager = null
        playButton = null
        upButton = null
        downButton = null

        if (receiverRegistered) {
            context?.unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
    }

    private fun toast(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun label(
        ctx: Context,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(ctx).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL

            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
    }

    private fun iconButton(
        ctx: Context,
        glyph: Glyph,
        tint: Int,
        fill: Int,
        size: Int
    ): GlyphButton {
        return GlyphButton(ctx).apply {
            this.glyph = glyph
            this.tint = tint
            layoutParams = LinearLayout.LayoutParams(
                dp(ctx, size),
                dp(ctx, size)
            )
            background = shape(ctx, fill, size / 2f)
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.argb(28, 0, 0, 0)),
                null,
                shape(ctx, Color.WHITE, size / 2f)
            )
            isClickable = true
        }
    }

    private fun shape(
        ctx: Context,
        fill: Int,
        radiusDp: Float,
        stroke: Int? = null
    ): GradientDrawable {
        val density = ctx.resources.displayMetrics.density

        return GradientDrawable().apply {
            cornerRadius = radiusDp * density
            setColor(fill)

            if (stroke != null) {
                setStroke(
                    density.toInt().coerceAtLeast(1),
                    stroke
                )
            }
        }
    }

    private fun dp(ctx: Context, value: Int): Int {
        return (value * ctx.resources.displayMetrics.density).toInt()
    }

    private class DragPanel(ctx: Context) : LinearLayout(ctx) {
        var onDrag: ((Float, Float) -> Unit)? = null

        private val slop =
            ViewConfiguration.get(ctx).scaledTouchSlop

        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var lastY = 0f

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    lastX = startX
                    lastY = startY
                }

                MotionEvent.ACTION_MOVE -> {
                    if (
                        abs(event.rawX - startX) > slop ||
                        abs(event.rawY - startY) > slop
                    ) {
                        lastX = event.rawX
                        lastY = event.rawY
                        return true
                    }
                }
            }

            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    onDrag?.invoke(
                        event.rawX - lastX,
                        event.rawY - lastY
                    )
                    lastX = event.rawX
                    lastY = event.rawY
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> return true
            }

            return super.onTouchEvent(event)
        }
    }

    private enum class Glyph {
        PLAY, PAUSE, EXPAND, COLLAPSE, CLOSE
    }

    private class GlyphButton(ctx: Context) : View(ctx) {
        var glyph = Glyph.PLAY
            set(value) {
                field = value
                invalidate()
            }

        var tint = Color.WHITE
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val unit = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f

            paint.color = tint
            paint.strokeWidth = 2.5f * unit
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND

            when (glyph) {
                Glyph.PLAY -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawPath(
                        Path().apply {
                            moveTo(cx - 5 * unit, cy - 8 * unit)
                            lineTo(cx + 8 * unit, cy)
                            lineTo(cx - 5 * unit, cy + 8 * unit)
                            close()
                        },
                        paint
                    )
                }

                Glyph.PAUSE -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        cx - 7 * unit,
                        cy - 8 * unit,
                        cx - 2 * unit,
                        cy + 8 * unit,
                        1.5f * unit,
                        1.5f * unit,
                        paint
                    )
                    canvas.drawRoundRect(
                        cx + 2 * unit,
                        cy - 8 * unit,
                        cx + 7 * unit,
                        cy + 8 * unit,
                        1.5f * unit,
                        1.5f * unit,
                        paint
                    )
                }

                Glyph.EXPAND, Glyph.COLLAPSE -> {
                    paint.style = Paint.Style.STROKE
                    val sign = if (glyph == Glyph.EXPAND) 1f else -1f

                    canvas.drawPath(
                        Path().apply {
                            moveTo(cx - 7 * unit, cy - sign * 3 * unit)
                            lineTo(cx, cy + sign * 4 * unit)
                            lineTo(cx + 7 * unit, cy - sign * 3 * unit)
                        },
                        paint
                    )
                }

                Glyph.CLOSE -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(
                        cx - 5 * unit,
                        cy - 5 * unit,
                        cx + 5 * unit,
                        cy + 5 * unit,
                        paint
                    )
                    canvas.drawLine(
                        cx + 5 * unit,
                        cy - 5 * unit,
                        cx - 5 * unit,
                        cy + 5 * unit,
                        paint
                    )
                }
            }
        }
    }
}
