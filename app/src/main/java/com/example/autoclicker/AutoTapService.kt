package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class AutoTapService : AccessibilityService() {

    private class Target(val view: TextView, val params: WindowManager.LayoutParams)

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val targets = mutableListOf<Target>()
    private var running = false
    private var intervalMs = 500L
    private var totalClicks = 0L

    private val widthOptions = intArrayOf(220, 260, 300, 340)
    private var widthIndex = 2

    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubble: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var intervalInput: EditText? = null
    private var startButton: Button? = null
    private var statusText: TextView? = null
    private var counterText: TextView? = null

    private val black = Color.BLACK
    private val white = Color.WHITE
    private val accent = 0xFF00E5FF.toInt()
    private val dim = 0xFF6E6E6E.toInt()
    private val panelBg = 0xF2060606.toInt()

    private val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private val targetSize: Int get() = dp(48)

    private val tapRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (targets.isEmpty()) {
                stopTapping()
                return
            }
            val builder = GestureDescription.Builder()
            val half = targetSize / 2
            targets.forEach { t ->
                val x = (t.params.x + half).toFloat()
                val y = (t.params.y + half).toFloat()
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x + 1f, y + 1f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            }
            dispatchGesture(builder.build(), null, null)
            totalClicks += targets.size
            counterText?.text = totalClicks.toString()
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildPanel()
        buildBubble()
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopTapping()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        targets.forEach { safeRemove(it.view) }
        targets.clear()
        panel?.let { safeRemove(it) }
        bubble?.let { safeRemove(it) }
        panel = null
        bubble = null
        super.onDestroy()
    }

    // ---------- Panel and bubble ----------

    private fun buildPanel() {
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.void_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@AutoTapService).apply {
                text = "VOID CLICKER"
                setTextColor(white)
                textSize = 16f
                typeface = mono(bold = true)
                letterSpacing = 0.15f
            })
            addView(TextView(this@AutoTapService).apply {
                text = "AUTO TAP // v2.3"
                setTextColor(dim)
                textSize = 10f
                typeface = mono()
                letterSpacing = 0.15f
            })
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton("−") { minimize() }, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(iconButton("×") { closeAll() }, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                leftMargin = dp(8)
            })
        }

        val interval = EditText(this).apply {
            setText("500")
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(white)
            setHintTextColor(dim)
            typeface = mono()
            textSize = 16f
            background = box(black, dim, 1, dp(2))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        intervalInput = interval

        val counter = TextView(this).apply {
            text = "0"
            setTextColor(accent)
            textSize = 36f
            typeface = mono(bold = true)
            gravity = Gravity.CENTER
        }
        counterText = counter

        val status = TextView(this).apply {
            setTextColor(dim)
            textSize = 11f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            typeface = mono()
        }
        statusText = status

        val btnStart = styledButton("START", filled = true) { toggleRunning() }
        startButton = btnStart
        val btnAdd = styledButton("ADD TARGET", filled = false) { addTarget() }
        val btnClear = styledButton("CLEAR TARGETS", filled = false) { clearTargets() }
        val btnReset = styledButton("RESET COUNT", filled = false) { resetCount() }
        val btnSmaller = styledButton("SIZE −", filled = false) { resizePanel(-1) }
        val btnBigger = styledButton("SIZE +", filled = false) { resizePanel(1) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = box(panelBg, white, 1, dp(4))
            addItem(header)
            addItem(sectionLabel("INTERVAL (MS)"), top = dp(18))
            addItem(interval, top = dp(6))
            addItem(sectionLabel("TOTAL CLICKS"), top = dp(14))
            addItem(counter, top = dp(2))
            addItem(status, top = dp(4))
            addItem(rowOf(btnStart, btnAdd), top = dp(18))
            addItem(rowOf(btnClear, btnReset), top = dp(10))
            addItem(rowOf(btnSmaller, btnBigger), top = dp(10))
        }

        val params = WindowManager.LayoutParams(
            dp(widthOptions[widthIndex]),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(120)
        }

        makeDraggable(header, root, params)
        wm.addView(root, params)
        panel = root
        panelParams = params
        updateStatus()
    }

    private fun buildBubble() {
        val view = ImageView(this).apply {
            setImageResource(R.drawable.void_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = box(black, white, 2, dp(40))
        }
        val params = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(200)
        }
        makeDraggable(view, view, params) { togglePanel() }
        wm.addView(view, params)
        bubble = view
        bubbleParams = params
        view.visibility = View.GONE
    }

    private fun showPanel() {
        panel?.visibility = View.VISIBLE
        bubble?.visibility = View.GONE
    }

    private fun minimize() {
        panel?.visibility = View.GONE
        bubble?.visibility = View.VISIBLE
    }

    private fun togglePanel() {
        if (panel?.visibility == View.VISIBLE) minimize() else showPanel()
    }

    private fun closeAll() {
        stopTapping()
        targets.forEach { safeRemove(it.view) }
        targets.clear()
        panel?.let { safeRemove(it) }
        bubble?.let { safeRemove(it) }
        panel = null
        bubble = null
        disableSelf()
    }

    private fun resizePanel(delta: Int) {
        widthIndex = (widthIndex + delta).coerceIn(0, widthOptions.size - 1)
        val p = panelParams ?: return
        p.width = dp(widthOptions[widthIndex])
        panel?.let { wm.updateViewLayout(it, p) }
    }

    private fun iconButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(white)
            textSize = 18f
            typeface = mono(bold = true)
            background = box(black, white, 1, dp(4))
            setOnClickListener { onClick() }
        }

    private fun logoImage(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.void_logo)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun sectionLabel(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(dim)
        textSize = 10f
        letterSpacing = 0.2f
        typeface = mono()
    }

    private fun styledButton(value: String, filled: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = value
            typeface = mono(bold = true)
            textSize = 12f
            letterSpacing = 0.1f
            setTextColor(if (filled) black else white)
            background = if (filled) box(accent, accent, 1, dp(2)) else box(black, dim, 1, dp(2))
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { onClick() }
        }

    private fun box(fill: Int, stroke: Int, strokeDp: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(strokeDp), stroke)
            cornerRadius = radiusPx.toFloat()
        }

    private fun mono(bold: Boolean = false): Typeface =
        Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)

    private fun LinearLayout.addItem(view: View, top: Int = 0) {
        addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = top })
    }

    private fun rowOf(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) leftMargin = dp(10)
            })
        }
    }

    // ---------- Targets ----------

    private fun addTarget() {
        if (running) {
            toast("Stop first before adding targets")
            return
        }
        if (targets.size >= 10) {
            toast("Maximum of 10 targets")
            return
        }

        val size = targetSize
        val metrics = resources.displayMetrics

        val tv = TextView(this).apply {
            text = (targets.size + 1).toString()
            gravity = Gravity.CENTER
            setTextColor(accent)
            typeface = mono(bold = true)
            textSize = 15f
            background = box(black, accent, 2, size / 2)
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels / 2 - size / 2
            y = metrics.heightPixels / 3 + targets.size * dp(60)
        }

        makeDraggable(tv, tv, params)
        wm.addView(tv, params)
        targets.add(Target(tv, params))
        updateStatus()
    }

    private fun clearTargets() {
        if (running) {
            toast("Stop first before clearing")
            return
        }
        targets.forEach { safeRemove(it.view) }
        targets.clear()
        updateStatus()
    }

    private fun resetCount() {
        if (running) {
            toast("Stop first before resetting")
            return
        }
        totalClicks = 0
        counterText?.text = "0"
    }

    private fun setTargetsPassThrough(passThrough: Boolean) {
        targets.forEach { t ->
            t.params.flags = if (passThrough) {
                overlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                overlayFlags
            }
            t.view.alpha = if (passThrough) 0.4f else 1f
            wm.updateViewLayout(t.view, t.params)
        }
    }

    // ---------- Running ----------

    private fun toggleRunning() {
        if (running) stopTapping() else startTapping()
    }

    private fun startTapping() {
        if (targets.isEmpty()) {
            toast("Add at least one target")
            return
        }
        intervalMs = intervalInput?.text?.toString()?.toLongOrNull()?.coerceAtLeast(50L) ?: 500L
        running = true
        setTargetsPassThrough(true)
        startButton?.text = "STOP"
        minimize()
        bubble?.background = box(black, accent, 2, dp(40))
        handler.post(tapRunnable)
        updateStatus()
    }

    private fun stopTapping() {
        if (!running) return
        running = false
        handler.removeCallbacks(tapRunnable)
        setTargetsPassThrough(false)
        startButton?.text = "START"
        bubble?.background = box(black, white, 2, dp(40))
        updateStatus()
    }

    // ---------- Helpers ----------

    private fun makeDraggable(
        handle: View,
        target: View,
        params: WindowManager.LayoutParams,
        onTap: (() -> Unit)? = null
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    downX = params.x
                    downY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = downX + dx.toInt()
                    params.y = downY + dy.toInt()
                    wm.updateViewLayout(target, params)
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap?.invoke()
                }
            }
            true
        }
    }

    private fun safeRemove(view: View) {
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun updateStatus() {
        val state = if (running) "RUNNING" else "STANDBY"
        statusText?.text = "$state  //  ${targets.size} TARGET(S)"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
