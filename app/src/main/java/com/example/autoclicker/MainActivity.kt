package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var openBtn: TextView
    private lateinit var singleBtn: TextView
    private lateinit var multiBtn: TextView

    private val bg = Color.parseColor("#0B0B0B")
    private val card = Color.parseColor("#1A1C1B")
    private val stroke = Color.parseColor("#2A2D2B")
    private val textMain = Color.WHITE
    private val textDim = Color.parseColor("#9AA09C")
    private val green = Color.parseColor("#3DDC84")
    private val red = Color.parseColor("#FF5252")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // logo
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.void_logo)
            adjustViewBounds = true
            maxHeight = dp(110)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8); bottomMargin = dp(20) })

        // STATUS
        val statusCard = newCard("STATUS")
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this).apply { background = rounded(red, 8) }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)))
        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(textMain)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusRow.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(12) })
        statusCard.addView(statusRow)

        openBtn = bigButton("OPEN ACCESSIBILITY", true).apply {
            setOnClickListener { openAccessibility() }
        }
        statusCard.addView(openBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(14) })
        root.addView(statusCard, cardParams())

        // SINGLE TARGET MODE
        val singleCard = newCard("SINGLE TARGET MODE")
        singleCard.addView(desc("One target box. Drag it onto the spot you want to tap."))
        singleBtn = bigButton("ENABLE", true).apply { setOnClickListener { onMode(AutoTapService.MODE_SINGLE) } }
        singleCard.addView(singleBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(14) })
        root.addView(singleCard, cardParams())

        // MULTI TARGETS MODE
        val multiCard = newCard("MULTI TARGETS MODE")
        multiCard.addView(desc("Up to 10 target boxes, all tapped every cycle."))
        multiBtn = bigButton("ENABLE", true).apply { setOnClickListener { onMode(AutoTapService.MODE_MULTI) } }
        multiCard.addView(multiBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(14) })
        root.addView(multiCard, cardParams())

        // HOW TO USE
        val stepsCard = newCard("HOW TO USE")
        val steps = listOf(
            "Enable \"Void Clicker\" in Accessibility",
            "Pick Single or Multi target and press ENABLE",
            "Open your game",
            "Drag the boxes to the spots and press START"
        )
        steps.forEachIndexed { i, s ->
            stepsCard.addView(stepRow(i + 1, s), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = if (i == steps.lastIndex) 0 else dp(14) })
        }
        root.addView(stepsCard, cardParams())

        root.addView(TextView(this).apply {
            text = "Ver 1.0"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(textDim)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val on = isServiceEnabled()
        statusText.text = if (on) "Void Clicker is enabled" else "Not enabled yet"
        statusDot.background = rounded(if (on) green else red, 8)
        openBtn.visibility = if (on) View.GONE else View.VISIBLE

        val svc = if (on) AutoTapService.instance else null
        setModeButton(singleBtn, svc?.mode == AutoTapService.MODE_SINGLE)
        setModeButton(multiBtn, svc?.mode == AutoTapService.MODE_MULTI)
    }

    private fun setModeButton(b: TextView, active: Boolean) {
        b.text = if (active) "DISABLE" else "ENABLE"
        if (active) {
            b.setTextColor(Color.WHITE)
            b.background = rounded(Color.TRANSPARENT, 12, Color.WHITE)
        } else {
            b.setTextColor(Color.BLACK)
            b.background = rounded(Color.WHITE, 12)
        }
    }

    private fun onMode(mode: Int) {
        val svc = AutoTapService.instance
        if (!isServiceEnabled() || svc == null) {
            Toast.makeText(this, "Enable \"Void Clicker\" in Accessibility first", Toast.LENGTH_LONG).show()
            openAccessibility()
            return
        }
        if (svc.mode == mode) {
            svc.hideAll()
            refreshStatus()
        } else {
            svc.showMode(mode)
            refreshStatus()
            moveTaskToBack(true) // go to the game, the GUI stays on top
        }
    }

    private fun openAccessibility() {
        AutoTapService.returnHome = true // service brings us back here once enabled
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.equals("$packageName/$packageName.AutoTapService", true) ||
                it.equals("$packageName/.AutoTapService", true)
        }
    }

    // ---- UI helpers ----

    private fun cardParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(14) }

    private fun newCard(title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 16, stroke)
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }
        c.addView(TextView(this).apply {
            text = title
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textDim)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
        return c
    }

    private fun desc(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(textMain)
        setLineSpacing(0f, 1.1f)
    }

    private fun bigButton(t: String, filled: Boolean) = TextView(this).apply {
        text = t
        gravity = Gravity.CENTER
        textSize = 14f
        letterSpacing = 0.05f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (filled) Color.BLACK else Color.WHITE)
        background = rounded(if (filled) Color.WHITE else Color.TRANSPARENT, 12, if (filled) null else Color.WHITE)
    }

    private fun stepRow(n: Int, text: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            this.text = n.toString()
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, 14)
        }, LinearLayout.LayoutParams(dp(28), dp(28)))
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(textMain)
            setLineSpacing(0f, 1.1f)
        }, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { leftMargin = dp(14) })
        return row
    }
}
