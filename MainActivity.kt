package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logo = TextView(this).apply {
            text = "VO/D®"
            setTextColor(Color.WHITE)
            textSize = 36f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "C L I C K E R"
            setTextColor(0xFF8A8A8A.toInt())
            textSize = 11f
            letterSpacing = 0.4f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        val steps = TextView(this).apply {
            text = "01  Buksan ang Accessibility Settings\n" +
                    "02  I-enable ang \"Void Clicker\"\n" +
                    "03  Bumalik sa app at buksan ang laro\n" +
                    "04  Maglagay ng points at pindutin ang START"
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.5f)
        }

        val enable = Button(this).apply {
            text = "BUKSAN ACCESSIBILITY"
            isAllCaps = false
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.15f
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(4).toFloat()
            }
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(28), dp(56), dp(28), dp(28))
            addView(logo)
            addView(subtitle)
            addView(steps, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(40) })
            addView(enable, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(32) })
        }

        setContentView(content)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
