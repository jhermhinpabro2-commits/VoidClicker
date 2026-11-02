package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var openBtn: Button

    // ---- colors ----
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

        // ---- logo ----
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.void_logo)
            adjustViewBounds = true
            maxHeight = dp(110)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8); bottomMargin = dp(20) })

        // ---- card 1: STATUS ----
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
        statusCard.addView(statusRow, rowParams(bottom = 14))

        openBtn = Button(this).apply {
            text = "BUKSAN ACCESSIBILITY"
            isAllCaps = true
            textSize = 14f
            letterSpacing = 0.05f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, 12)
            stateListAnimator = null
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        statusCard.addView(openBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))
        root.addView(statusCard, cardParams())

        // ---- card 2: PAANO GAMITIN ----
        val stepsCard = newCard("PAANO GAMITIN")
        val steps = listOf(
            "Buksan ang Accessibility Settings",
            "I-enable ang \"Void Clicker\"",
            "Bumalik sa app at buksan ang laro",
            "Maglagay ng points at pindutin ang START"
        )
        steps.forEachIndexed { i, s ->
            stepsCard.addView(stepRow(i + 1, s), rowParams(bottom = if (i == steps.lastIndex) 0 else 14))
        }
        root.addView(stepsCard, cardParams())

        // ---- card 3: OTHERS ----
        val othersCard = newCard("OTHERS")
        othersCard.addView(infoRow("Version", "1.0"), rowParams(bottom = 0))
        root.addView(othersCard, cardParams())

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val on = isServiceEnabled()
        statusText.text = if (on) "Naka-enable ang Void Clicker" else "Hindi pa naka-enable"
        statusDot.background = rounded(if (on) green else red, 8)
        openBtn.text = if (on) "ACCESSIBILITY SETTINGS" else "BUKSAN ACCESSIBILITY"
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

    private fun rowParams(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom) }

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
        }, rowParams(bottom = 14))
        return c
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

    private fun infoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(textMain)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(textDim)
        })
        return row
    }
}
