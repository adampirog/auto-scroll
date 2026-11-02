package com.adampirog.auto_scroll_android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating Accessibility overlay with draggable controls.
 */
internal class AutoScrollOverlay(
    private val service: AutoScrollAccessibilityService,
    private val onToggle: () -> Unit,
    private val onClose: () -> Unit,
    private val onDirection: (String) -> Unit,
    private val onSpeed: (Double) -> Unit
) {
    companion object {
        private const val COLLAPSED_DP = 116
        private const val EXPANDED_DP = 276

        private val TEAL = Color.rgb(7, 150, 184)
        private val INK = Color.rgb(21, 34, 56)
        private val MUTED = Color.rgb(101, 117, 139)
        private val PALE = Color.rgb(233, 247, 251)
        private val SURFACE = Color.rgb(243, 247, 250)
        private val BORDER = Color.rgb(222, 231, 237)

        private const val MIN_SPEED = 1
        private const val MAX_SPEED = 25
    }

    private val windowManager = service.getSystemService(
        Context.WINDOW_SERVICE
    ) as WindowManager

    private var root: DragPanel? = null
    private var params: WindowManager.LayoutParams? = null

    private var expanded = false

    private var playButton: GlyphView? = null
    private var expandButton: GlyphView? = null
    private var titleRow: LinearLayout? = null
    private var details: LinearLayout? = null
    private var upButton: TextView? = null
    private var downButton: TextView? = null
    private var speedSlider: SeekBar? = null
    private var speedLabel: TextView? = null

    fun show(
        direction: String,
        speed: Double,
        playing: Boolean
    ) {
        if (root != null) return

        val screenWidth =
            service.resources.displayMetrics.widthPixels

        val collapsedWidth = dp(COLLAPSED_DP)

        val layout = WindowManager.LayoutParams(
            collapsedWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT

            x = (
                screenWidth - collapsedWidth - dp(16)
            ).coerceAtLeast(0)

            y = dp(120)
        }

        val panel = DragPanel(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = shape(Color.WHITE, 20f, BORDER)
            elevation = dp(10).toFloat()
        }

        panel.onDrag = { dx, dy ->
            val screen = service.resources.displayMetrics

            layout.x = (layout.x + dx.roundToInt()).coerceIn(
                0,
                (screen.widthPixels - panel.width).coerceAtLeast(0)
            )

            layout.y = (layout.y + dy.roundToInt()).coerceIn(
                0,
                (screen.heightPixels - panel.height).coerceAtLeast(0)
            )

            windowManager.updateViewLayout(panel, layout)
        }

        val heading = LinearLayout(service).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(6), 0, 0, dp(8))
        }

        heading.addView(
            text("AutoScroll", 15f, INK, bold = true),
            LinearLayout.LayoutParams(0, dp(36), 1f)
        )

        heading.addView(
            icon(Glyph.CLOSE, MUTED, SURFACE, 36).apply {
                contentDescription = "Close AutoScroll"
                setOnClickListener { onClose() }
            }
        )

        titleRow = heading

        val controls = LinearLayout(service).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        playButton = icon(
            Glyph.PLAY,
            Color.WHITE,
            TEAL,
            46
        ).apply {
            contentDescription = "Start scrolling"
            setOnClickListener { onToggle() }
        }

        controls.addView(playButton)

        controls.addView(
            View(service),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        expandButton = icon(
            Glyph.EXPAND,
            INK,
            PALE,
            40
        ).apply {
            contentDescription = "Expand controls"
            setOnClickListener { toggleExpanded() }
        }

        controls.addView(expandButton)

        val advanced = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(5), dp(14), dp(5), dp(5))
        }

        advanced.addView(
            text("DIRECTION", 11f, MUTED, bold = true)
        )

        val directions = LinearLayout(service).apply {
            background = shape(SURFACE, 12f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        upButton = directionButton("↑  Up", "up")
        downButton = directionButton("↓  Down", "down")

        directions.addView(
            upButton,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        directions.addView(
            downButton,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        advanced.addView(
            directions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(16)
            }
        )

        val speedHeading = LinearLayout(service).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        speedHeading.addView(
            text("SPEED", 11f, MUTED, bold = true),
            LinearLayout.LayoutParams(0, dp(28), 1f)
        )

        speedLabel = text(
            "1×",
            15f,
            TEAL,
            bold = true
        )

        speedHeading.addView(speedLabel)
        advanced.addView(speedHeading)

        speedSlider = SeekBar(service).apply {
            max = MAX_SPEED - MIN_SPEED

            progressTintList = ColorStateList.valueOf(TEAL)
            thumbTintList = ColorStateList.valueOf(TEAL)
            progressBackgroundTintList =
                ColorStateList.valueOf(BORDER)

            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        view.parent.requestDisallowInterceptTouchEvent(
                            false
                        )
                    }
                }

                false
            }

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            onSpeed(
                                (MIN_SPEED + progress).toDouble()
                            )
                        }
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        advanced.addView(speedSlider)

        val speedLimits = LinearLayout(service).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        speedLimits.addView(
            text("1×", 11f, MUTED),
            LinearLayout.LayoutParams(0, dp(20), 1f)
        )

        speedLimits.addView(
            text("25×", 11f, MUTED)
        )

        advanced.addView(speedLimits)
        details = advanced

        panel.addView(heading)
        panel.addView(controls)
        panel.addView(advanced)

        windowManager.addView(panel, layout)

        root = panel
        params = layout
        expanded = false

        sync(direction, speed, playing)
    }

    fun sync(
        direction: String,
        speed: Double,
        playing: Boolean
    ) {
        playButton?.glyph = if (playing) {
            Glyph.PAUSE
        } else {
            Glyph.PLAY
        }

        playButton?.contentDescription = if (playing) {
            "Pause scrolling"
        } else {
            "Start scrolling"
        }

        fun style(
            button: TextView?,
            selected: Boolean
        ) {
            button ?: return

            button.setTextColor(
                if (selected) TEAL else MUTED
            )

            button.background = shape(
                if (selected) PALE else SURFACE,
                10f
            )
        }

        style(upButton, direction == "up")
        style(downButton, direction == "down")

        val progress = (
            speed.roundToInt() - MIN_SPEED
        ).coerceIn(0, MAX_SPEED - MIN_SPEED)

        if (speedSlider?.progress != progress) {
            speedSlider?.progress = progress
        }

        speedLabel?.text = String.format(
            Locale.US,
            "%d×",
            speed.roundToInt()
        )
    }

    fun refreshBounds() {
        val panel = root ?: return
        val layout = params ?: return

        val screen = service.resources.displayMetrics

        layout.width = targetWidth(expanded)

        layout.x = layout.x.coerceIn(
            0,
            (screen.widthPixels - layout.width).coerceAtLeast(0)
        )

        layout.y = layout.y.coerceIn(
            0,
            (screen.heightPixels - panel.height).coerceAtLeast(0)
        )

        windowManager.updateViewLayout(panel, layout)
    }

    fun hide() {
        val panel = root ?: return

        root = null
        params = null
        playButton = null
        expandButton = null
        titleRow = null
        details = null
        upButton = null
        downButton = null
        speedSlider = null
        speedLabel = null

        windowManager.removeView(panel)
    }

    private fun toggleExpanded() {
        val panel = root ?: return
        val layout = params ?: return

        expanded = !expanded

        titleRow?.visibility = if (expanded) {
            View.VISIBLE
        } else {
            View.GONE
        }

        details?.visibility = if (expanded) {
            View.VISIBLE
        } else {
            View.GONE
        }

        expandButton?.glyph = if (expanded) {
            Glyph.COLLAPSE
        } else {
            Glyph.EXPAND
        }

        expandButton?.contentDescription = if (expanded) {
            "Collapse controls"
        } else {
            "Expand controls"
        }

        layout.width = targetWidth(expanded)

        val screenWidth =
            service.resources.displayMetrics.widthPixels

        layout.x = layout.x.coerceIn(
            0,
            (screenWidth - layout.width).coerceAtLeast(0)
        )

        windowManager.updateViewLayout(panel, layout)
    }

    private fun targetWidth(expanded: Boolean): Int {
        val screenWidth =
            service.resources.displayMetrics.widthPixels

        val requested = if (expanded) {
            dp(EXPANDED_DP)
        } else {
            dp(COLLAPSED_DP)
        }

        return requested.coerceAtMost(
            screenWidth.coerceAtLeast(1)
        )
    }

    private fun directionButton(
        title: String,
        direction: String
    ): TextView =
        text(title, 14f, INK, bold = true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            contentDescription = "Scroll $direction"

            setOnClickListener {
                onDirection(direction)
            }
        }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView =
        TextView(service).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL

            if (bold) {
                setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
                )
            }
        }

    private fun icon(
        glyph: Glyph,
        tint: Int,
        fill: Int,
        size: Int
    ): GlyphView =
        GlyphView(service).apply {
            this.glyph = glyph
            this.tint = tint

            layoutParams = LinearLayout.LayoutParams(
                dp(size),
                dp(size)
            )

            background = shape(
                fill,
                size / 2f
            )

            foreground = RippleDrawable(
                ColorStateList.valueOf(
                    Color.argb(28, 0, 0, 0)
                ),
                null,
                shape(
                    Color.WHITE,
                    size / 2f
                )
            )

            isClickable = true
        }

    private fun shape(
        fill: Int,
        radiusDp: Float,
        stroke: Int? = null
    ): GradientDrawable {
        val density =
            service.resources.displayMetrics.density

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

    private fun dp(value: Int): Int =
        (
            value * service.resources.displayMetrics.density
        ).roundToInt()

    private class DragPanel(context: Context) :
        LinearLayout(context) {

        var onDrag: ((Float, Float) -> Unit)? = null

        private val slop =
            ViewConfiguration.get(context).scaledTouchSlop

        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var lastY = 0f

        override fun onInterceptTouchEvent(
            event: MotionEvent
        ): Boolean {
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

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {
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
                MotionEvent.ACTION_CANCEL -> {
                    return true
                }
            }

            return super.onTouchEvent(event)
        }
    }

    private enum class Glyph {
        PLAY,
        PAUSE,
        EXPAND,
        COLLAPSE,
        CLOSE
    }

    private class GlyphView(context: Context) : View(context) {
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
                            moveTo(
                                cx - 5 * unit,
                                cy - 8 * unit
                            )
                            lineTo(
                                cx + 8 * unit,
                                cy
                            )
                            lineTo(
                                cx - 5 * unit,
                                cy + 8 * unit
                            )
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

                    val sign = if (
                        glyph == Glyph.EXPAND
                    ) {
                        1f
                    } else {
                        -1f
                    }

                    canvas.drawPath(
                        Path().apply {
                            moveTo(
                                cx - 7 * unit,
                                cy - sign * 3 * unit
                            )
                            lineTo(
                                cx,
                                cy + sign * 4 * unit
                            )
                            lineTo(
                                cx + 7 * unit,
                                cy - sign * 3 * unit
                            )
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
