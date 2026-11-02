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

class AutoTapService : AccessibilityService() {

    private class Target(val view: TextView, val params: WindowManager.LayoutParams)

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val targets = mutableListOf<Target>()
    private var running = false
    private var intervalMs = 500L
    private var totalClicks = 0L

    private var panel: View? = null
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
                builder.addStroke(GestureDescription.StrokeDescription(path, 0, 20))
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
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopTapping()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::wm.isInitialized) {
            targets.forEach { wm.removeView(it.view) }
            panel?.let { wm.removeView(it) }
        }
        super.onDestroy()
    }

    // ---------- Panel ----------

    private fun showPanel() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logoImage(), LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(LinearLayout(this@AutoTapService).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, 0)
                addView(TextView(this@AutoTapService).apply {
                    text = "VOID CLICKER"
                    setTextColor(white)
                    textSize = 17f
                    typeface = mono(bold = true)
                    letterSpacing = 0.18f
                })
                addView(TextView(this@AutoTapService).apply {
                    text = "AUTO TAP // v2.3"
                    setTextColor(dim)
                    textSize = 10f
                    typeface = mono()
                    letterSpacing = 0.15f
                })
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
            textSize = 40f
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = box(panelBg, white, 1, dp(4))
            addItem(header)
            addItem(sectionLabel("INTERVAL (MS)"), top = dp(24))
            addItem(interval, top = dp(8))
            addItem(sectionLabel("TOTAL CLICKS"), top = dp(20))
            addItem(counter, top = dp(4))
            addItem(status, top = dp(8))
            addItem(rowOf(btnStart, btnAdd), top = dp(24))
            addItem(rowOf(btnClear, btnReset), top = dp(12))
        }

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        makeDraggable(header, root, params)
        wm.addView(root, params)
        panel = root
        updateStatus()
    }

    private fun logoImage(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.void_logo)
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
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
            textSize = 13f
            letterSpacing = 0.15f
            setTextColor(if (filled) black else white)
            background = if (filled) box(accent, accent, 1, dp(2)) else box(black, dim, 1, dp(2))
            setPadding(0, dp(14), 0, dp(14))
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
                if (i > 0) leftMargin = dp(12)
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
        targets.forEach { wm.removeView(it.view) }
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
        handler.post(tapRunnable)
        updateStatus()
    }

    private fun stopTapping() {
        if (!running) return
        running = false
        handler.removeCallbacks(tapRunnable)
        setTargetsPassThrough(false)
        startButton?.text = "START"
        updateStatus()
    }

    // ---------- Helpers ----------

    private fun makeDraggable(handle: View, target: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    downX = params.x
                    downY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = downX + (e.rawX - downRawX).toInt()
                    params.y = downY + (e.rawY - downRawY).toInt()
                    wm.updateViewLayout(target, params)
                }
            }
            true
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
