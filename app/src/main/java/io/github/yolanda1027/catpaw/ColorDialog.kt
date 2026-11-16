package io.github.yolanda1027.catpaw

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 選顏色的小視窗（設定頁、主題工作室共用） */
object ColorDialog {
    fun show(a: Activity, label: String, initial: Int, onPick: (Int) -> Unit) {
        val dp = a.resources.displayMetrics.density
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * dp).toInt()
            setPadding(p, p / 2, p, 0)
        }
        val picker = ColorPickerView(a, initial)
        box.addView(picker, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        fun chip(c: Int) = View(a).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(c)
                setStroke((1 * dp).toInt(), 0x33000000)
            }
        }
        // 原本 ｜ 新的　＃色碼
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        val oldChip = chip(initial)
        val newChip = chip(initial)
        val size = LinearLayout.LayoutParams((52 * dp).toInt(), (40 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() }
        row.addView(TextView(a).apply { text = "原本"; textSize = 12f; setPadding(0, 0, (4 * dp).toInt(), 0) })
        row.addView(oldChip, size)
        row.addView(TextView(a).apply { text = "新的"; textSize = 12f; setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0) })
        row.addView(newChip, LinearLayout.LayoutParams(size))
        val hex = EditText(a).apply {
            textSize = 16f
            isSingleLine = true
            setText(String.format("#%06X", 0xFFFFFF and initial))
        }
        row.addView(hex, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)

        var syncing = false
        fun showNew(c: Int) = (newChip.background as android.graphics.drawable.GradientDrawable).setColor(c)
        picker.onColorChanged = { c ->
            showNew(c)
            syncing = true
            hex.setText(String.format("#%06X", 0xFFFFFF and c))
            syncing = false
        }
        // 直接輸入色碼也可以
        hex.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                if (syncing) return
                val t = e?.toString()?.trim() ?: return
                val c = try { android.graphics.Color.parseColor(if (t.startsWith("#")) t else "#$t") } catch (_: Exception) { return }
                picker.setColor(c)
                showNew(c)
            }
        })

        android.app.AlertDialog.Builder(a)
            .setTitle(label)
            .setView(box)
            .setPositiveButton("確定") { _, _ -> onPick(picker.color) }
            .setNegativeButton("取消", null)
            .show()
    }
}
