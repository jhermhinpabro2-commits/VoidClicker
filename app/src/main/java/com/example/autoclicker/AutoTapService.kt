package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import android.accessibilityservice.GestureDescription
import android.graphics.Path

class AutoTapService : AccessibilityService() {

    companion object {
        const val MODE_NONE = 0
        const val MODE_SINGLE = 1
        const val MODE_MULTI = 2
        private const val MAX_TARGETS = 10

        @Volatile
        var instance: AutoTapService? = null

        // set by MainActivity before it opens Accessibility settings,
        // so the service sends the user back to the home page once enabled
        @Volatile
        var returnHome = false
    }

    var mode = MODE_NONE
        private set

    private class Target(val view: TextView, val params: WindowManager.LayoutParams)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private val targets = mutableListOf<Target>()

    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var countView: TextView? = null
    private var statusView: TextView? = null
    private var startBtn: TextView? = null
    private var intervalInput: EditText? = null

    private var running = false
    private var clicks = 0
    private var intervalMs = 500L
    private var scale = 1f // one scale for everything, so boxes stay boxes

    private val cyan = Color.parseColor("#00E5FF")
    private val dim = Color.parseColor("#8A8F8C")
    private val red = Color.parseColor("#FF5252")

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        instance = this
        if (returnHome) {
            returnHome = false
            goHome()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        closeAll()
        mode = MODE_NONE
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        closeAll()
        mode = MODE_NONE
        instance = null
        super.onDestroy()
    }

    private fun goHome() {
        val i = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        try {
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // public API (used by MainActivity)
    // ------------------------------------------------------------------

    fun showMode(newMode: Int) {
        closeAll()
        mode = newMode
        clicks = 0
        panelParams = overlayParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpFixed(16f), dpFixed(80f)
        )
        buildPanel()
        addTarget()
    }

    /** Closes the GUI and the target boxes. The accessibility service stays ON. */
    fun hideAll() {
        closeAll()
        mode = MODE_NONE
    }

    // ------------------------------------------------------------------
    // sizes / helpers
    // ------------------------------------------------------------------

    private fun dpFixed(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun d(v: Float) = (v * scale * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun boxBg(fill: Int, radius: Float, strokeColor: Int? = null, strokeDp: Float = 1f) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = d(radius).toFloat()
            if (strokeColor != null) setStroke(max(1, d(strokeDp)), strokeColor)
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size * scale
            setTextColor(color)
            typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = 0.06f
            setSingleLine()
        }

    private fun styleBtn(tv: TextView, filled: Boolean, color: Int = cyan) {
        if (filled) {
            tv.setTextColor(Color.BLACK)
            tv.background = boxBg(color, 8f)
        } else {
            tv.setTextColor(Color.WHITE)
            tv.background = boxBg(Color.TRANSPARENT, 8f, Color.WHITE)
        }
    }

    private fun actionBtn(text: String, filled: Boolean, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 10f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.06f
            setSingleLine()
            styleBtn(this, filled)
            setOnClickListener { onClick() }
        }

    private fun squareBtn(text: String, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = boxBg(Color.TRANSPARENT, 8f, Color.WHITE)
            setOnClickListener { onClick() }
        }

    private fun addRow(parent: LinearLayout, a: View, b: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(a, LinearLayout.LayoutParams(0, d(40f), 1f).apply { rightMargin = d(3f) })
        row.addView(b, LinearLayout.LayoutParams(0, d(40f), 1f).apply { leftMargin = d(3f) })
        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = d(6f) }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(
        handle: View,
        params: WindowManager.LayoutParams,
        window: View,
        onClick: (() -> Unit)?
    ) {
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY
                    ox = params.x; oy = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    if (moved) {
                        params.x = ox + dx.toInt()
                        params.y = oy + dy.toInt()
                        try {
                            wm.updateViewLayout(window, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onClick?.invoke()
                    true
                }
                else -> true
            }
        }
    }

    private fun removeView(v: View?) {
        if (v == null) return
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // panel (the GUI)
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel() {
        removeView(panel)
        val p = panelParams ?: return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = d(236f)
            setPadding(d(12f), d(12f), d(12f), d(12f))
            background = boxBg(Color.parseColor("#F2080808"), 14f, Color.parseColor("#55FFFFFF"))
        }

        // header (drag handle)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.void_logo)
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(d(34f), d(34f))
        )
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label("VOID CLICKER", 12f, Color.WHITE, true))
        titleBlock.addView(
            label(if (mode == MODE_SINGLE) "SINGLE TARGET" else "MULTI TARGETS", 8.5f, dim)
        )
        header.addView(
            titleBlock,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = d(8f) }
        )
        header.addView(
            squareBtn("\u2212") { minimize() },
            LinearLayout.LayoutParams(d(30f), d(30f)).apply { leftMargin = d(6f) }
        )
        header.addView(
            squareBtn("\u00D7") { hideAll() },
            LinearLayout.LayoutParams(d(30f), d(30f)).apply { leftMargin = d(6f) }
        )
        root.addView(header)
        makeDraggable(header, p, root, null)

        // interval
        root.addView(
            label("INTERVAL (MS)", 9f, dim),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = d(12f) }
        )
        val input = EditText(this).apply {
            setText(intervalMs.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f * scale
            setPadding(d(10f), 0, d(10f), 0)
            background = boxBg(Color.TRANSPARENT, 8f, Color.parseColor("#88FFFFFF"))
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    setPanelFocusable(true)
                    handler.postDelayed({
                        intervalInput?.let {
                            it.requestFocus()
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }, 150)
                }
                false
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitInterval()
                    true
                } else false
            }
        }
        intervalInput = input
        root.addView(
            input,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(44f))
                .apply { topMargin = d(4f) }
        )

        // counter
        root.addView(
            label("TOTAL CLICKS", 9f, dim),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = d(10f) }
        )
        val count = label("0", 34f, cyan, true).apply { gravity = Gravity.CENTER }
        countView = count
        root.addView(
            count,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val status = label("", 9.5f, dim).apply { gravity = Gravity.CENTER }
        statusView = status
        root.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // buttons
        val start = actionBtn("START", true) { toggleRun() }
        startBtn = start
        val reset = actionBtn("RESET COUNT", false) { clicks = 0; refreshUi() }
        if (mode == MODE_MULTI) {
            addRow(root, start, actionBtn("ADD TARGET", false) { addTarget() })
            addRow(root, actionBtn("CLEAR TARGETS", false) { clearTargets() }, reset)
        } else {
            addRow(root, start, reset)
        }
        addRow(
            root,
            actionBtn("SIZE \u2212", false) { changeSize(-0.15f) },
            actionBtn("SIZE +", false) { changeSize(0.15f) }
        )

        try {
            wm.addView(root, p)
        } catch (_: Exception) {
        }
        panel = root
        refreshUi()
    }

    private fun setPanelFocusable(on: Boolean) {
        val p = panelParams ?: return
        val v = panel ?: return
        p.flags = if (on) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            wm.updateViewLayout(v, p)
        } catch (_: Exception) {
        }
    }

    private fun commitInterval() {
        val input = intervalInput ?: return
        val v = input.text.toString().toLongOrNull() ?: 500L
        intervalMs = max(50L, v)
        input.setText(intervalMs.toString())
        input.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        setPanelFocusable(false)
    }

    private fun refreshUi() {
        countView?.text = clicks.toString()
        statusView?.text =
            (if (running) "RUNNING" else "STANDBY") + " // " + targets.size + " TARGET(S)"
        startBtn?.let {
            it.text = if (running) "STOP" else "START"
            styleBtn(it, true, if (running) red else cyan)
        }
    }

    // ------------------------------------------------------------------
    // minimize / restore
    // ------------------------------------------------------------------

    private fun minimize() {
        val p = panelParams ?: return
        removeView(panel)
        panel = null
        val size = d(46f)
        val bp = overlayParams(size, size, p.x, p.y)
        bubbleParams = bp
        val b = ImageView(this).apply {
            setImageResource(R.drawable.void_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(d(4f), d(4f), d(4f), d(4f))
            background = boxBg(Color.parseColor("#F2080808"), 12f, Color.parseColor("#88FFFFFF"))
        }
        makeDraggable(b, bp, b) { restore() }
        try {
            wm.addView(b, bp)
        } catch (_: Exception) {
        }
        bubble = b
    }

    private fun restore() {
        val bp = bubbleParams
        val p = panelParams
        if (bp != null && p != null) {
            p.x = bp.x
            p.y = bp.y
        }
        removeView(bubble)
        bubble = null
        buildPanel()
    }

    // ------------------------------------------------------------------
    // targets (always square boxes)
    // ------------------------------------------------------------------

    private fun targetBg() = boxBg(Color.parseColor("#B000E5FF"), 8f, Color.WHITE, 2f)

    private fun addTarget() {
        if (mode == MODE_SINGLE && targets.isNotEmpty()) return
        if (targets.size >= MAX_TARGETS) {
            toast("Max $MAX_TARGETS targets")
            return
        }
        val size = d(52f)
        val tv = TextView(this).apply {
            text = (targets.size + 1).toString()
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 14f * scale
            background = targetBg()
        }
        val dm = resources.displayMetrics
        val offset = targets.size * dpFixed(24f)
        val p = overlayParams(
            size, size,
            dm.widthPixels / 2 - size / 2 + offset,
            dm.heightPixels / 2 - size / 2 + offset
        )
        if (running) p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        makeDraggable(tv, p, tv, null)
        try {
            wm.addView(tv, p)
        } catch (_: Exception) {
        }
        targets.add(Target(tv, p))
        refreshUi()
    }

    private val tapRunnable = object : Runnable {
    override fun run() {
        if (!running) return
        // dito ang actual na pag-tap, halimbawa:
        targets.forEach { performTap(it) }
        clicks++
        refreshUi()
        handler.postDelayed(this, intervalMs)
    }
}

    private fun performTap(t: Target) {
    val x = t.params.x + t.params.width / 2f
    val y = t.params.y + t.params.height / 2f
    val path = Path().apply { moveTo(x, y) }
    val stroke = GestureDescription.StrokeDescription(path, 0, 50)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    dispatchGesture(gesture, null, null)
    }
    
private fun toggleRun() {
    if (running) stopClicking() else startClicking()
}

private fun startClicking() {
    if (targets.isEmpty() && mode == MODE_MULTI) return
    commitInterval()
    running = true
    setTargetsPassThrough(true)
    refreshUi()
    handler.post(tapRunnable)
}

private fun stopClicking() {
    running = false
    handler.removeCallbacks(tapRunnable)
    setTargetsPassThrough(false)
    refreshUi()
}

    private fun clearTargets() {
        stopClicking()
        targets.forEach { removeView(it.view) }
        targets.clear()
        refreshUi()
    }

    private fun setTargetsPassThrough(on: Boolean) {
        targets.forEach { t ->
            t.params.flags = if (on) {
                t.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                t.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            try {
                wm.updateViewLayout(t.view, t.params)
            } catch (_: Exception) {
            }
        }
    }

    /** Same scale for panel and target boxes: width == height always. */
    private fun changeSize(delta: Float) {
        val newScale = (scale + delta).coerceIn(0.6f, 1.6f)
        if (newScale == scale) return
        val oldSize = d(52f)
        scale = newScale
        val newSize = d(52f)
        targets.forEach { t ->
            t.params.x += (oldSize - newSize) / 2 // shrink/grow around the center
            t.params.y += (oldSize - newSize) / 2
            t.params.width = newSize
            t.params.height = newSize
            t.view.textSize = 14f * scale
            try {
                wm.updateViewLayout(t.view, t.params)
            } catch (_: Exception) {
            }
        }
        if (panel != null) buildPanel()
        if (bubble != null) {
            val bp = bubbleParams
            val b = bubble
            if (bp != null && b != null) {
                bp.width = d(46f)
                bp.height = d(46f)
                try {
                    wm.updateViewLayout(b, bp)
                } catch (_: Exception) {
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // cleanup
    // ------------------------------------------------------------------

    private fun closeAll() {
        running = false
        handler.removeCallbacks(tapRunnable)
        if (!::wm.isInitialized) return
        removeView(panel)
        removeView(bubble)
        targets.forEach { removeView(it.view) }
        targets.clear()
        panel = null
        bubble = null
        countView = null
        statusView = null
        startBtn = null
        intervalInput = null
    }
}
