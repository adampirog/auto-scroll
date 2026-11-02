package com.adampirog.autoscroll

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

/** Native replacement for the Flet settings screen. */
class MainActivity : Activity() {

    companion object {
        private val TEAL = Color.rgb(7, 150, 184)
        private val INK = Color.rgb(21, 34, 56)
        private val MUTED = Color.rgb(101, 117, 139)
        private val CANVAS = Color.rgb(245, 248, 251)
        private val PALE = Color.rgb(233, 247, 251)
    }

    private lateinit var client: ServiceClient
    private lateinit var accessStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var overlaySwitch: Switch
    private lateinit var speedValue: TextView
    private lateinit var speedSlider: SeekBar
    private lateinit var directionSummary: TextView
    private lateinit var upButton: Button
    private lateinit var downButton: Button

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
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildInterface()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(CANVAS)
        }
        // Android 15+ enforces edge-to-edge for target SDK 35.
        // Keep native controls clear of status and navigation bars.
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
        }
        scroll.addView(page)
        setContentView(scroll)

        // Header
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "⇵"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = background(TEAL, 15f)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(50), dp(50)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        heading.addView(label("Auto Scroll", 24f, INK, bold = true))
        heading.addView(label("Hands-free scrolling for long content", 12f, MUTED))
        header.addView(heading)
        page.addView(header, sectionParams())

        // Floating controls card
        val overlayCard = card()
        val overlayHeader = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val overlayHeading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overlayHeading.addView(label("Floating controls", 17f, INK, bold = true))
        overlayHeading.addView(label("Show controls over other apps", 12f, MUTED))
        overlayHeader.addView(overlayHeading, LinearLayout.LayoutParams(0, -2, 1f))
        overlaySwitch = Switch(this).apply {
            isEnabled = false
            thumbTintList = android.content.res.ColorStateList.valueOf(TEAL)
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                if (!suppressSwitch && ready) setOverlay(checked)
            }
        }
        overlayHeader.addView(overlaySwitch)
        overlayCard.addView(overlayHeader)
        overlayStatus = label("Overlay hidden", 12f, MUTED)
        overlayCard.addView(overlayStatus, spaced(dp(12)))
        overlayCard.addView(actionButton("Refresh status") { refresh() }, spaced(dp(10)))
        page.addView(overlayCard, sectionParams())

        // Accessibility card
        val accessCard = card()
        accessCard.addView(label("Accessibility", 16f, INK, bold = true))
        accessStatus = label("Checking accessibility...", 13f, MUTED)
        accessCard.addView(accessStatus, spaced(dp(10)))
        val accessButtons = row()
        accessButtons.addView(
            actionButton("Check access") { refresh() },
            weightedParams()
        )
        accessButtons.addView(
            actionButton("Settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            weightedParams(left = dp(10))
        )
        accessCard.addView(accessButtons, spaced(dp(12)))
        page.addView(accessCard, sectionParams())

        // Scrolling settings card
        val settingsCard = card()
        settingsCard.addView(label("Scrolling", 16f, INK, bold = true))
        directionSummary = label("1×  ·  Down", 13f, MUTED)
        settingsCard.addView(directionSummary, spaced(dp(5)))
        val speedRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        speedRow.addView(label("Speed", 14f, INK), LinearLayout.LayoutParams(0, -2, 1f))
        speedValue = label("1.0×", 20f, INK, bold = true)
        speedRow.addView(speedValue)
        settingsCard.addView(speedRow, spaced(dp(18)))
        speedSlider = SeekBar(this).apply {
            max = 9
            progress = 1
            isEnabled = false
            progressTintList = android.content.res.ColorStateList.valueOf(TEAL)
            thumbTintList = android.content.res.ColorStateList.valueOf(TEAL)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    if (fromUser) {
                        speed = 0.5 + progress * 0.5
                        syncSettings()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (ready) saveConfig()
                }
            })
        }
        settingsCard.addView(speedSlider, spaced(dp(7)))
        val limits = row()
        limits.addView(label("0.5×  Slower", 11f, MUTED), weightedParams())
        limits.addView(label("5×  Faster", 11f, MUTED))
        settingsCard.addView(limits)
        settingsCard.addView(label("Direction", 14f, INK), spaced(dp(20)))
        val directions = row()
        upButton = actionButton("↑  Up") { chooseDirection("up") }.apply { isEnabled = false }
        downButton = actionButton("↓  Down") { chooseDirection("down") }.apply { isEnabled = false }
        directions.addView(upButton, weightedParams())
        directions.addView(downButton, weightedParams(left = dp(10)))
        settingsCard.addView(directions, spaced(dp(10)))
        page.addView(settingsCard, sectionParams())
        syncSettings()
    }

    private fun refresh() {
        val token = ++requestNumber
        ready = false
        setControlsEnabled(false)
        if (!client.permissionEnabled()) {
            accessStatus.text = "Accessibility disabled"
            accessStatus.setTextColor(MUTED)
            setOverlayChecked(false)
            overlayStatus.text = "Overlay hidden"
            return
        }
        accessStatus.text = "Checking accessibility..."
        client.request("state") { success, state ->
            if (token != requestNumber || isFinishing || isDestroyed) return@request
            ready = success && state != null
            setControlsEnabled(ready)
            if (!ready) {
                accessStatus.text = "Accessibility enabled, service unavailable"
                accessStatus.setTextColor(MUTED)
                setOverlayChecked(false)
                overlayStatus.text = "Overlay unavailable"
                return@request
            }
            accessStatus.text = "Accessibility ready"
            accessStatus.setTextColor(TEAL)
            val visible = state!!.getBoolean("visible", false)
            setOverlayChecked(visible)
            overlayStatus.text = if (visible) "Overlay active" else "Overlay hidden"
            direction = state.getString("direction")?.takeIf { it == "up" || it == "down" }
                ?: "down"
            speed = state.getDouble("speed", 1.0).coerceIn(0.5, 5.0)
            speedSlider.progress = ((speed - 0.5) * 2).roundToInt().coerceIn(0, 9)
            syncSettings()
        }
    }

    private fun setOverlay(visible: Boolean) {
        setControlsEnabled(false)
        client.request(if (visible) "show" else "hide") { success, _ ->
            if (isFinishing || isDestroyed) return@request
            refresh()
            if (!success) overlayStatus.text = "Unable to change overlay state"
        }
    }

    private fun chooseDirection(value: String) {
        if (!ready) return
        direction = value
        syncSettings()
        saveConfig()
    }

    private fun saveConfig() {
        setControlsEnabled(false)
        client.request("configure", direction = direction, speed = speed) { success, _ ->
            if (isFinishing || isDestroyed) return@request
            refresh()
            if (!success) overlayStatus.text = "Unable to save settings"
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        overlaySwitch.isEnabled = enabled
        speedSlider.isEnabled = enabled
        upButton.isEnabled = enabled
        downButton.isEnabled = enabled
    }

    private fun setOverlayChecked(value: Boolean) {
        suppressSwitch = true
        overlaySwitch.isChecked = value
        suppressSwitch = false
    }

    private fun syncSettings() {
        speedValue.text = String.format(java.util.Locale.US, "%.1f×", speed)
        directionSummary.text = "${speed.toFloat()}×  ·  ${direction.replaceFirstChar { it.uppercase() }}"
        upButton.setTextColor(if (direction == "up") Color.WHITE else INK)
        upButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (direction == "up") TEAL else PALE
        )
        downButton.setTextColor(if (direction == "down") Color.WHITE else INK)
        downButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (direction == "down") TEAL else PALE
        )
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = background(Color.WHITE, 18f)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun actionButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            minHeight = dp(42)
            minimumHeight = dp(42)
            setPadding(dp(9), 0, dp(9), 0)
            setTextColor(INK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(PALE)
            setOnClickListener { onClick() }
        }

    private fun background(fill: Int, radius: Float) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius * resources.displayMetrics.density
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun sectionParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) }

    private fun spaced(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }

    private fun weightedParams(left: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = left }
}
