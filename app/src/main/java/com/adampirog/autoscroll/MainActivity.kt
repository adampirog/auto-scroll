package com.adampirog.autoscroll

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.adampirog.auto_scroll_android.ScrollRules
import kotlin.math.roundToInt

class MainActivity : Activity() {

    companion object {
        private val TEAL = Color.rgb(7, 150, 184)
        private val TEAL_DARK = Color.rgb(4, 117, 147)
        private val INK = Color.rgb(21, 34, 56)
        private val MUTED = Color.rgb(101, 117, 139)
        private val CANVAS = Color.rgb(245, 248, 251)
        private val PALE = Color.rgb(233, 247, 251)
        private val BORDER = Color.rgb(227, 234, 240)
        private val SOFT = Color.rgb(247, 250, 252)
    }

    private lateinit var client: ServiceClient

    private lateinit var accessStatus: TextView
    private lateinit var accessBadge: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var overlayIndicator: View
    private lateinit var overlaySwitch: Switch

    private lateinit var speedValue: TextView
    private lateinit var speedSlider: SeekBar
    private lateinit var upButton: TextView
    private lateinit var downButton: TextView

    private var ready = false
    private var direction = "down"
    private var speed = ScrollRules.MIN_SPEED
    private var suppressSwitch = false
    private var requestNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        client = ServiceClient(applicationContext)

        window.statusBarColor = CANVAS
        window.navigationBarColor = CANVAS
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        buildInterface()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = true
            setBackgroundColor(CANVAS)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars()
                )

                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }

        val page = column().apply {
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }

        scroll.addView(page)
        setContentView(scroll)

        page.addView(
            buildHeader(),
            sectionParams(dp(20))
        )

        page.addView(
            buildOverlayCard(),
            sectionParams()
        )

        page.addView(
            buildAccessibilityCard(),
            sectionParams()
        )

        page.addView(buildScrollingCard())

        syncSettings()
    }

    private fun buildHeader(): LinearLayout {
        val header = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = label(
            "↕",
            26f,
            Color.WHITE,
            bold = true
        ).apply {
            gravity = Gravity.CENTER
            background = rounded(TEAL, 15f)
            elevation = dp(2).toFloat()
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        val heading = column().apply {
            setPadding(dp(14), 0, 0, 0)

            addView(
                label("Auto Scroll", 23f, INK, bold = true)
            )

            addView(
                label(
                    "Hands-free scrolling for long content",
                    12f,
                    MUTED
                ),
                spaced(dp(4))
            )
        }

        header.addView(heading, weightedParams())

        return header
    }

    private fun buildOverlayCard(): LinearLayout {
        val card = card()

        val header = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = column().apply {
            addView(
                label(
                    "Floating controls",
                    16f,
                    INK,
                    bold = true
                )
            )

            addView(
                label(
                    "Show controls over other apps",
                    12f,
                    MUTED
                ),
                spaced(dp(5))
            )
        }

        header.addView(title, weightedParams())

        overlaySwitch = Switch(this).apply {
            isEnabled = false

            thumbTintList = ColorStateList.valueOf(
                Color.WHITE
            )

            trackTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(TEAL, BORDER)
            )

            setOnCheckedChangeListener {
                _: CompoundButton, checked: Boolean ->
                if (!suppressSwitch && ready) {
                    setOverlay(checked)
                }
            }
        }

        header.addView(overlaySwitch)
        card.addView(header)

        val statusRow = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        overlayIndicator = View(this).apply {
            background = rounded(BORDER, 10f)
        }

        statusRow.addView(
            overlayIndicator,
            LinearLayout.LayoutParams(dp(7), dp(7))
        )

        overlayStatus = label(
            "Overlay hidden",
            12f,
            MUTED
        )

        statusRow.addView(
            overlayStatus,
            LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(9)
            }
        )

        card.addView(
            statusRow,
            spaced(dp(12))
        )

        card.addView(
            pillButton("Refresh status") {
                refresh()
            },
            spaced(dp(12))
        )

        return card
    }

    private fun buildAccessibilityCard(): LinearLayout {
        val card = card()

        val header = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            label(
                "Accessibility",
                16f,
                INK,
                bold = true
            ),
            weightedParams()
        )

        accessBadge = label(
            "CHECKING",
            10f,
            MUTED,
            bold = true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = rounded(SOFT, 50f)
        }

        header.addView(accessBadge)
        card.addView(header)

        accessStatus = label(
            "Checking accessibility...",
            12f,
            MUTED
        )

        card.addView(
            accessStatus,
            spaced(dp(12))
        )

        val actions = row()

        actions.addView(
            pillButton("Check access") {
                refresh()
            },
            weightedParams()
        )

        actions.addView(
            pillButton("Settings", filled = true) {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            },
            weightedParams(dp(10))
        )

        card.addView(
            actions,
            spaced(dp(14))
        )

        return card
    }

    private fun buildScrollingCard(): LinearLayout {
        val card = card()

        card.addView(
            label(
                "Scrolling",
                16f,
                INK,
                bold = true
            )
        )

        val speedHeader = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        speedHeader.addView(
            label("Speed", 14f, INK, bold = true),
            weightedParams()
        )

        speedValue = label(
            "1×",
            26f,
            TEAL,
            bold = true
        )

        speedHeader.addView(speedValue)

        card.addView(
            speedHeader,
            spaced(dp(16))
        )

        speedSlider = SeekBar(this).apply {
            max = (
                ScrollRules.MAX_SPEED - ScrollRules.MIN_SPEED
            ).toInt()

            progress = 0
            isEnabled = false

            progressTintList = ColorStateList.valueOf(TEAL)
            thumbTintList = ColorStateList.valueOf(TEAL)
            progressBackgroundTintList =
                ColorStateList.valueOf(BORDER)

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            speed = ScrollRules.MIN_SPEED + progress
                            syncSettings()
                        }
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                        if (ready) saveConfig()
                    }
                }
            )
        }

        card.addView(
            speedSlider,
            spaced(dp(8))
        )

        val limits = row()

        limits.addView(
            label(
                "${ScrollRules.MIN_SPEED.toInt()}×",
                11f,
                MUTED
            ),
            weightedParams()
        )

        limits.addView(
            label(
                "${ScrollRules.MAX_SPEED.toInt()}×",
                11f,
                MUTED
            )
        )

        card.addView(limits)

        card.addView(
            label(
                "Direction",
                14f,
                INK,
                bold = true
            ),
            spaced(dp(16))
        )

        val directionGroup = row().apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(SOFT, 15f)
        }

        upButton = pillButton("↑  Up") {
            chooseDirection("up")
        }.apply {
            isEnabled = false
        }

        downButton = pillButton("↓  Down") {
            chooseDirection("down")
        }.apply {
            isEnabled = false
        }

        directionGroup.addView(
            upButton,
            weightedParams()
        )

        directionGroup.addView(
            downButton,
            weightedParams(dp(4))
        )

        card.addView(
            directionGroup,
            spaced(dp(9))
        )

        return card
    }

    private fun refresh() {
        val token = ++requestNumber

        ready = false
        setControlsEnabled(false)

        if (!client.permissionEnabled()) {
            accessStatus.text = "Accessibility is disabled"
            accessStatus.setTextColor(MUTED)
            setAccessBadge("NOT ENABLED", false)
            setOverlayChecked(false)
            overlayStatus.text = "Overlay hidden"
            return
        }

        accessStatus.text = "Checking accessibility..."
        setAccessBadge("CHECKING", false)

        client.request("state") { success, state ->
            if (
                token != requestNumber ||
                isFinishing ||
                isDestroyed
            ) {
                return@request
            }

            ready = success && state != null
            setControlsEnabled(ready)

            if (!ready) {
                accessStatus.text =
                    "Accessibility enabled, service unavailable"
                accessStatus.setTextColor(MUTED)
                setAccessBadge("UNAVAILABLE", false)

                setOverlayChecked(false)
                overlayStatus.text = "Overlay unavailable"
                return@request
            }

            val currentState = state!!

            accessStatus.text = "Accessibility is ready"
            accessStatus.setTextColor(TEAL_DARK)
            setAccessBadge("READY", true)

            val visible = currentState.getBoolean(
                "visible",
                false
            )

            setOverlayChecked(visible)

            overlayStatus.text = if (visible) {
                "Overlay active"
            } else {
                "Overlay hidden"
            }

            direction = ScrollRules.restoreDirection(
                currentState.getString("direction")
            )

            speed = ScrollRules.normalizeSpeed(
                currentState.getDouble(
                    "speed",
                    ScrollRules.MIN_SPEED
                )
            )

            speedSlider.progress = (
                speed - ScrollRules.MIN_SPEED
            ).toInt()

            syncSettings()
        }
    }

    private fun setOverlay(visible: Boolean) {
        if (!ready) return

        setControlsEnabled(false)

        client.request(
            if (visible) "show" else "hide"
        ) { success, _ ->
            if (isFinishing || isDestroyed) return@request

            refresh()

            if (!success) {
                overlayStatus.text =
                    "Unable to change overlay state"
            }
        }
    }

    private fun chooseDirection(value: String) {
        if (!ready) return

        direction = value
        syncSettings()
        saveConfig()
    }

    private fun saveConfig() {
        if (!ready) return

        setControlsEnabled(false)

        client.request(
            "configure",
            direction = direction,
            speed = speed
        ) { success, _ ->
            if (isFinishing || isDestroyed) return@request

            refresh()

            if (!success) {
                overlayStatus.text = "Unable to save settings"
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        overlaySwitch.isEnabled = enabled
        speedSlider.isEnabled = enabled
        upButton.isEnabled = enabled
        downButton.isEnabled = enabled

        speedSlider.alpha = if (enabled) 1f else 0.5f
        upButton.alpha = if (enabled) 1f else 0.55f
        downButton.alpha = if (enabled) 1f else 0.55f
    }

    private fun setOverlayChecked(value: Boolean) {
        suppressSwitch = true
        overlaySwitch.isChecked = value
        suppressSwitch = false

        overlayIndicator.background = rounded(
            if (value) TEAL else BORDER,
            10f
        )
    }

    private fun syncSettings() {
        speedValue.text = "${speed.roundToInt()}×"

        updateDirectionButton(
            upButton,
            selected = direction == "up"
        )

        updateDirectionButton(
            downButton,
            selected = direction == "down"
        )
    }

    private fun updateDirectionButton(
        button: TextView,
        selected: Boolean
    ) {
        button.setTextColor(
            if (selected) Color.WHITE else MUTED
        )

        button.background = ripple(
            if (selected) TEAL else SOFT,
            13f
        )
    }

    private fun setAccessBadge(
        value: String,
        active: Boolean
    ) {
        accessBadge.text = value

        accessBadge.setTextColor(
            if (active) TEAL_DARK else MUTED
        )

        accessBadge.background = rounded(
            if (active) PALE else SOFT,
            50f
        )
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun card() = column().apply {
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = rounded(Color.WHITE, 20f, BORDER)
        elevation = dp(2).toFloat()
        clipToOutline = true
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false

        if (bold) {
            setTypeface(null, Typeface.BOLD)
        }
    }

    private fun pillButton(
        title: String,
        filled: Boolean = false,
        onClick: () -> Unit
    ) = label(
        title,
        13f,
        if (filled) Color.WHITE else TEAL_DARK,
        bold = true
    ).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)

        setPadding(
            dp(12),
            dp(9),
            dp(12),
            dp(9)
        )

        background = ripple(
            if (filled) TEAL else PALE,
            13f
        )

        isClickable = true
        isFocusable = true

        setOnClickListener { onClick() }
    }

    private fun rounded(
        fill: Int,
        radius: Float,
        stroke: Int? = null
    ) = GradientDrawable().apply {
        setColor(fill)

        cornerRadius =
            radius * resources.displayMetrics.density

        if (stroke != null) {
            setStroke(dp(1), stroke)
        }
    }

    private fun ripple(
        fill: Int,
        radius: Float
    ) = RippleDrawable(
        ColorStateList.valueOf(
            Color.argb(35, 0, 70, 100)
        ),
        rounded(fill, radius),
        rounded(Color.WHITE, radius)
    )

    private fun dp(value: Int): Int =
        (
            value * resources.displayMetrics.density
        ).roundToInt()

    private fun sectionParams(
        bottom: Int = dp(12)
    ) = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = bottom
    }

    private fun spaced(top: Int) =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = top
        }

    private fun weightedParams(
        left: Int = 0
    ) = LinearLayout.LayoutParams(0, -2, 1f).apply {
        leftMargin = left
    }
}
