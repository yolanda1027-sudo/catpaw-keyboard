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

        // 💧 汲色：從畫面 ｜ 從圖片
        val dropRow = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        val fromScreen = android.widget.Button(a).apply { text = "💧 從畫面汲色"; isAllCaps = false }
        val fromImage = android.widget.Button(a).apply { text = "🖼️ 從圖片汲色"; isAllCaps = false }
        // 兩顆都自己設好邊距（同一個高度、中間留空）
        val m = (3 * dp).toInt()
        dropRow.addView(fromScreen, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, m, m, m) })
        dropRow.addView(fromImage, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(m, m, 0, m) })
        box.addView(dropRow)

        var syncing = false
        fun showNew(c: Int) = (newChip.background as android.graphics.drawable.GradientDrawable).setColor(c)
        picker.onColorChanged = { c ->
            showNew(c)
            syncing = true
            hex.setText(String.format("#%06X", 0xFFFFFF and c))
            syncing = false
        }
        fun setFromDropper(c: Int) {
            picker.setColor(c)
            picker.onColorChanged?.invoke(c)
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

        // 字放大的手機上視窗可能比螢幕高，包一層可以捲
        val scroll = android.widget.ScrollView(a).apply { addView(box) }
        val dlg = android.app.AlertDialog.Builder(a)
            .setTitle(label)
            .setView(scroll)
            .setPositiveButton("確定") { _, _ -> onPick(picker.color) }
            .setNegativeButton("取消", null)
            .show()

        /** 汲色畫面關掉後回到選色視窗 */
        fun dropper(bmp: android.graphics.Bitmap?, hint: String) {
            if (bmp == null) {
                android.widget.Toast.makeText(a, "讀不到圖片 QAQ", android.widget.Toast.LENGTH_SHORT).show()
                dlg.show()
                return
            }
            Eyedropper.show(a, bmp, hint) { c ->
                if (c != null) setFromDropper(c)
                dlg.show()
                bmp.recycle() // 用完就丟，不存起來
            }
        }
        fromScreen.setOnClickListener {
            dlg.hide()
            Eyedropper.captureScreen(a) { dropper(it, "手指拖到想要的顏色，再按「用這個顏色」") }
        }
        fromImage.setOnClickListener {
            dlg.hide()
            Eyedropper.pickImage(a) { dropper(it, "手指拖到想要的顏色（這張圖不會被存起來）") }
        }
    }
}
