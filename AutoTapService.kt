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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AutoTapService : AccessibilityService() {

    private class Marker(val view: TextView, val params: WindowManager.LayoutParams)

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val markers = mutableListOf<Marker>()
    private var running = false
    private var intervalMs = 500L

    private var panel: View? = null
    private var intervalInput: EditText? = null
    private var startButton: Button? = null
    private var statusText: TextView? = null

    private val black = Color.BLACK
    private val white = Color.WHITE
    private val grey = 0xFF8A8A8A.toInt()

    private val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private val markerSize: Int get() = dp(46)

    private val tapRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val builder = GestureDescription.Builder()
            val half = markerSize / 2
            markers.forEach { m ->
                val x = (m.params.x + half).toFloat()
                val y = (m.params.y + half).toFloat()
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x + 1f, y + 1f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0, 20))
            }
            if (markers.isNotEmpty()) {
                dispatchGesture(builder.build(), null, null)
            }
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
            markers.forEach { wm.removeView(it.view) }
            panel?.let { wm.removeView(it) }
        }
        super.onDestroy()
    }

    // ---------- Void-themed panel ----------

    private fun showPanel() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(logo())
            addView(TextView(this@AutoTapService).apply {
                text = "C L I C K E R"
                setTextColor(grey)
                textSize = 10f
                letterSpacing = 0.4f
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
            })
        }

        val intervalLabel = TextView(this).apply {
            text = "INTERVAL (MS)"
            setTextColor(grey)
            textSize = 10f
            letterSpacing = 0.2f
            typeface = Typeface.MONOSPACE
        }

        val interval = EditText(this).apply {
            setText("500")
            hint = "500"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(white)
            setHintTextColor(grey)
            typeface = Typeface.MONOSPACE
            textSize = 15f
            background = box(black, white, 1, dp(4))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        intervalInput = interval

        val status = TextView(this).apply {
            setTextColor(grey)
            textSize = 11f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
        }
        statusText = status

        val start = styledButton("START", filled = true) { toggleRunning() }
        startButton = start
        val addPoint = styledButton("+ POINT", filled = false) { addMarker() }
        val clear = styledButton("CLEAR POINTS", filled = false) { clearMarkers() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            background = box(black, white, 1, dp(6))
            addItem(header)
            addItem(intervalLabel, top = dp(18))
            addItem(interval, top = dp(6))
            addItem(status, top = dp(14))
            addItem(rowOf(start, addPoint), top = dp(14))
            addItem(clear, top = dp(10))
        }

        val params = WindowManager.LayoutParams(
            dp(280),
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

    private fun logo(): TextView = TextView(this).apply {
        text = "VO/D®"
        setTextColor(white)
        textSize = 28f
        letterSpacing = 0.12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    private fun styledButton(label: String, filled: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            typeface = Typeface.MONOSPACE
            textSize = 13f
            letterSpacing = 0.15f
            setTextColor(if (filled) black else white)
            background = if (filled) box(white, white, 1, dp(4)) else box(black, white, 1, dp(4))
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { onClick() }
        }

    private fun box(fill: Int, stroke: Int, strokeDp: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(strokeDp), stroke)
            cornerRadius = radiusPx.toFloat()
        }

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

    // ---------- Tap points ----------

    private fun addMarker() {
        if (running) {
            toast("Itigil muna bago mag-add ng point")
            return
        }
        if (markers.size >= 10) {
            toast("Maximum na 10 points")
            return
        }

        val size = markerSize
        val metrics = resources.displayMetrics

        val tv = TextView(this).apply {
            text = (markers.size + 1).toString()
            gravity = Gravity.CENTER
            setTextColor(white)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 15f
            background = box(black, white, 2, size / 2)
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
            y = metrics.heightPixels / 3 + markers.size * dp(56)
        }

        makeDraggable(tv, tv, params)
        wm.addView(tv, params)
        markers.add(Marker(tv, params))
        updateStatus()
    }

    private fun clearMarkers() {
        if (running) {
            toast("Itigil muna bago mag-clear")
            return
        }
        markers.forEach { wm.removeView(it.view) }
        markers.clear()
        updateStatus()
    }

    private fun setMarkersPassThrough(passThrough: Boolean) {
        markers.forEach { m ->
            m.params.flags = if (passThrough) {
                overlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                overlayFlags
            }
            m.view.alpha = if (passThrough) 0.45f else 1f
            wm.updateViewLayout(m.view, m.params)
        }
    }

    // ---------- Running ----------

    private fun toggleRunning() {
        if (running) stopTapping() else startTapping()
    }

    private fun startTapping() {
        if (markers.isEmpty()) {
            toast("Maglagay muna ng kahit isang point")
            return
        }
        intervalMs = intervalInput?.text?.toString()?.toLongOrNull()?.coerceAtLeast(50L) ?: 500L
        running = true
        setMarkersPassThrough(true)
        startButton?.text = "STOP"
        handler.post(tapRunnable)
        updateStatus()
    }

    private fun stopTapping() {
        if (!running) return
        running = false
        handler.removeCallbacks(tapRunnable)
        setMarkersPassThrough(false)
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
        statusText?.text = "$state  ·  ${markers.size} POINT"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
