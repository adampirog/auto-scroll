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
import java.util.Locale
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

        private const val MIN_SPEED = 1
        private const val MAX_SPEED = 25
    }

    private lateinit var client: ServiceClient

    private lateinit var accessStatus: TextView
    private lateinit var accessBadge: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var overlaySwitch: Switch
    private lateinit var speedValue: TextView
    private lateinit var speedSlider: SeekBar
    private lateinit var directionSummary: TextView
    private lateinit var upButton: TextView
    private lateinit var downButton: TextView

    private var ready = false
    private var direction = "down"
    private var speed = 1.0
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
            clipToPadding = false
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

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        scroll.addView(page)
        setContentView(scroll)

        // Header
        val header = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = label("↕", 30f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(TEAL, 17f)
            elevation = dp(3).toFloat()
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(56), dp(56))
        )

        val heading = column().apply {
            setPadding(dp(15), 0, 0, 0)
        }

        heading.addView(
            label("Auto Scroll", 25f, INK, bold = true)
        )
        heading.addView(
            label(
                "Hands-free scrolling for long content",
                12f,
                MUTED
            ),
            spaced(dp(5))
        )

        header.addView(
            heading,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        page.addView(
            header,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(30)
            }
        )

        // Floating controls
        val overlayCard = card()

        val overlayHeader = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val overlayHeading = column()

        overlayHeading.addView(
            label("Floating controls", 17f, INK, bold = true)
        )
        overlayHeading.addView(
            label("Show controls over other apps", 12f, MUTED),
            spaced(dp(6))
        )

        overlayHeader.addView(
            overlayHeading,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        overlaySwitch = Switch(this).apply {
            isEnabled = false

            thumbTintList = ColorStateList.valueOf(Color.WHITE)

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

        overlayHeader.addView(overlaySwitch)
        overlayCard.addView(overlayHeader)

        val overlayStateRow = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(19), 0, dp(4))
        }

        val indicator = View(this).apply {
            background = rounded(TEAL, 10f)
        }

        overlayStateRow.addView(
            indicator,
            LinearLayout.LayoutParams(dp(7), dp(7))
        )

        overlayStatus = label("Overlay hidden", 12f, MUTED)

        overlayStateRow.addView(
            overlayStatus,
            LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(9)
            }
        )

        overlayCard.addView(overlayStateRow)

        overlayCard.addView(
            pillButton("Refresh status") { refresh() },
            spaced(dp(15))
        )

        page.addView(overlayCard, sectionParams())

        // Accessibility
        val accessCard = card()

        val accessHeader = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        accessHeader.addView(
            label("Accessibility", 17f, INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        accessBadge = label(
            "CHECKING",
            10f,
            MUTED,
            bold = true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(SOFT, 50f)
        }

        accessHeader.addView(accessBadge)
        accessCard.addView(accessHeader)

        accessStatus = label(
            "Checking accessibility...",
            13f,
            MUTED
        )

        accessCard.addView(
            accessStatus,
            spaced(dp(15))
        )

        val accessActions = row()

        accessActions.addView(
            pillButton("Check access") { refresh() },
            weightedParams()
        )

        accessActions.addView(
            pillButton("Settings", filled = true) {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            },
            weightedParams(left = dp(10))
        )

        accessCard.addView(
            accessActions,
            spaced(dp(18))
        )

        page.addView(accessCard, sectionParams())

        // Scrolling
        val scrollingCard = card()

        scrollingCard.addView(
            label("Scrolling", 17f, INK, bold = true)
        )

        directionSummary = label(
            "1×  ·  Down",
            13f,
            MUTED
        )

        scrollingCard.addView(
            directionSummary,
            spaced(dp(6))
        )

        val speedHeader = row().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val speedHeading = column()
        speedHeading.addView(
            label("Speed", 14f, INK, bold = true)
        )
        speedHeading.addView(
            label("Adjust scrolling intensity", 12f, MUTED),
            spaced(dp(5))
        )

        speedHeader.addView(
            speedHeading,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        speedValue = label(
            "1×",
            29f,
            TEAL,
            bold = true
        )

        speedHeader.addView(speedValue)

        scrollingCard.addView(
            speedHeader,
            spaced(dp(25))
        )

        speedSlider = SeekBar(this).apply {
            max = MAX_SPEED - MIN_SPEED
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
                            speed = (progress + MIN_SPEED).toDouble()
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

        scrollingCard.addView(
            speedSlider,
            spaced(dp(12))
        )

        val limits = row()

        limits.addView(
            label("1×  Gentle", 11f, MUTED),
            weightedParams()
        )
        limits.addView(
            label("25×  Fast", 11f, MUTED)
        )

        scrollingCard.addView(limits)

        scrollingCard.addView(
            label("Direction", 14f, INK, bold = true),
            spaced(dp(27))
        )

        val directionGroup = row().apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(SOFT, 16f)
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
            weightedParams(left = dp(4))
        )

        scrollingCard.addView(
            directionGroup,
            spaced(dp(12))
        )

        scrollingCard.addView(
            label(
                "Higher speeds use faster, longer swipe gestures.",
                11f,
                MUTED
            ),
            spaced(dp(19))
        )

        page.addView(scrollingCard, sectionParams())

        page.addView(
            label(
                "Auto Scroll · Native Android",
                11f,
                MUTED
            ).apply {
                gravity = Gravity.CENTER
            },
            spaced(dp(8))
        )

        syncSettings()
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

            accessStatus.text = "Accessibility is ready"
            accessStatus.setTextColor(TEAL_DARK)
            setAccessBadge("READY", true)

            val currentState = state!!
            val visible = currentState.getBoolean("visible", false)

            setOverlayChecked(visible)
            overlayStatus.text = if (visible) {
                "Overlay active"
            } else {
                "Overlay hidden"
            }

            direction = currentState.getString("direction")
                ?.takeIf { it == "up" || it == "down" }
                ?: "down"

            speed = currentState
                .getDouble("speed", 1.0)
                .roundToInt()
                .coerceIn(MIN_SPEED, MAX_SPEED)
                .toDouble()

            speedSlider.progress = speed.toInt() - MIN_SPEED
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
    }

    private fun syncSettings() {
        val value = speed.roundToInt()
        speedValue.text = String.format(Locale.US, "%d×", value)

        val name = if (direction == "up") "Up" else "Down"
        directionSummary.text = "$value×  ·  $name"

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

    private fun row(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

    private fun column(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

    private fun card(): LinearLayout =
        column().apply {
            setPadding(
                dp(20),
                dp(21),
                dp(20),
                dp(21)
            )
            background = rounded(Color.WHITE, 21f, BORDER)
            elevation = dp(2).toFloat()
            clipToOutline = true
        }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView =
        TextView(this).apply {
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
    ): TextView =
        label(
            title,
            13f,
            if (filled) Color.WHITE else TEAL_DARK,
            bold = true
        ).apply {
            gravity = Gravity.CENTER
            minHeight = dp(46)
            setPadding(dp(12), dp(10), dp(12), dp(10))

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
    ): GradientDrawable =
        GradientDrawable().apply {
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
    ): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(
                Color.argb(35, 0, 70, 100)
            ),
            rounded(fill, radius),
            rounded(Color.WHITE, radius)
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun sectionParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(18)
        }

    private fun spaced(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = top
        }

    private fun weightedParams(
        left: Int = 0
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = left
        }
}
