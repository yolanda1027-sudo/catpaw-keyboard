package io.github.yolanda1027.catpaw

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * 貓爪主題工作室：在鍵盤預覽上直接點按鍵來改樣子。
 * 改了就馬上存起來，鍵盤也會跟著變（不用另外按套用）。
 */
class StudioActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var kv: CatKeyboardView
    private lateinit var panel: LinearLayout
    private lateinit var tabZhuyin: Button
    private lateinit var tabEnglish: Button

    private var styles = KeyStyles.EMPTY
    private var selected: Key? = null
    /** true = 改「同一類的全部按鍵」，false = 只改選到的這顆 */
    private var groupScope = false
    private var english = false

    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        styles = KeyStyles.load(prefs)
        english = savedInstanceState?.getBoolean("english") ?: false
        actionBar?.title = "貓爪主題工作室"

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 要編輯哪一種鍵盤
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(12f), px(4f), px(12f), px(4f))
        }
        tabZhuyin = tabButton("注音鍵盤") { switchLayout(false) }
        tabEnglish = tabButton("英文鍵盤") { switchLayout(true) }
        tabs.addView(tabZhuyin, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(tabEnglish, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(tabs)

        kv = CatKeyboardView(this, prefs, Haptics(this)).apply {
            quickItems = emptyList()
            editPicker = { key ->
                if (selected?.let { KeyStyles.idOf(it) } != KeyStyles.idOf(key)) groupScope = false
                selected = key
                refresh()
            }
        }
        outer.addView(kv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(8f), px(20f), px(48f))
        }
        outer.addView(ScrollView(this).apply { addView(panel) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(outer)
        switchLayout(english)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("english", english)
    }

    private fun tabButton(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun switchLayout(en: Boolean) {
        english = en
        kv.rows = if (en) Layouts.ENGLISH else Layouts.ZHUYIN
        kv.isChinese = !en
        tabZhuyin.alpha = if (en) 0.5f else 1f
        tabEnglish.alpha = if (en) 1f else 0.5f
        selected = null
        groupScope = false
        refresh()
    }

    /** 目前在改的是哪個代號（單顆鍵或一整類） */
    private fun currentId(): String? {
        val key = selected ?: return null
        return if (groupScope) KeyStyles.groupOf(key).id else KeyStyles.idOf(key)
    }

    private fun update(change: (KeyStyle) -> KeyStyle?, rebuild: Boolean = true) {
        val id = currentId() ?: return
        styles = styles.with(id, change(styles.map[id] ?: KeyStyle()))
        KeyStyles.save(prefs, styles)
        kv.reloadPrefs()
        if (rebuild) refresh()
    }

    // ---------- 下面的編輯面板 ----------

    private fun refresh() {
        panel.removeAllViews()
        val key = selected
        if (key == null) {
            kv.editHighlight = null
            text("👆 點一下上面鍵盤的任何一顆按鍵，就可以開始改它的樣子", 16f).setPadding(0, px(12f), 0, px(8f))
            text("改好會直接套用到鍵盤上。想換整體主題、字體，到「貓爪鍵盤」設定的外觀頁。", 13f, dim = true)
            if (styles.map.isNotEmpty()) {
                text("目前有 ${styles.map.size} 組按鍵設定", 14f).setPadding(0, px(20f), 0, 0)
                clearAllButton()
            }
            return
        }
        val group = KeyStyles.groupOf(key)
        val selId = KeyStyles.idOf(key)
        kv.editHighlight = if (groupScope) { k -> KeyStyles.groupOf(k) == group } else { k -> KeyStyles.idOf(k) == selId }

        text("正在編輯：" + KeyStyles.nameOf(key), 18f, bold = true).setPadding(0, px(10f), 0, px(4f))

        // 空白鍵、Enter 本來就只有一顆，不用選範圍
        if (group == KeyStyles.Group.ZHUYIN || group == KeyStyles.Group.CHAR || group == KeyStyles.Group.FUNC) {
            val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
            val one = RadioButton(this).apply { id = View.generateViewId(); text = "只改這一顆"; isChecked = !groupScope }
            val all = RadioButton(this).apply { id = View.generateViewId(); text = group.title; isChecked = groupScope }
            rg.addView(one)
            rg.addView(all)
            rg.setOnCheckedChangeListener { _, id ->
                groupScope = id == all.id
                refresh()
            }
            panel.addView(rg)
        }

        val cur = styles.map[currentId()] ?: KeyStyle()
        // 沒自訂的顏色：用這顆鍵現在畫面上的顏色
        val now = kv.sampleColors(key)
        colorRow("底色", cur.bg, now?.bg ?: Color.WHITE) { v -> update({ it.copy(bg = v) }) }
        colorRow("文字顏色", cur.text, now?.text ?: Color.BLACK) { v -> update({ it.copy(text = v) }) }
        colorRow("外框顏色", cur.border, now?.border ?: Color.GRAY) { v -> update({ it.copy(border = v) }) }

        // 圓角
        val radius = ((cur.radius ?: 0.3f) * 100).toInt()
        val radiusLabel = text("圓角：$radius%", 15f)
        radiusLabel.setPadding(0, px(14f), 0, 0)
        panel.addView(SeekBar(this).apply {
            max = 100
            progress = radius
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    radiusLabel.text = "圓角：$p%"
                    update({ it.copy(radius = p / 100f) }, rebuild = false)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        text("0% 是直角，100% 是膠囊形。改了底色、外框或圓角，這顆鍵會換成自訂的形狀（羊皮紙這類紋理會變成接近的單色）。", 12f, dim = true)

        panel.addView(CheckBox(this).apply {
            text = "不顯示文字（只留底色和外框）"
            isChecked = cur.hideLabel == true
            setPadding(0, px(6f), 0, px(6f))
            setOnCheckedChangeListener { _, on -> update({ it.copy(hideLabel = if (on) true else null) }) }
        })

        if (groupScope && styles.map.containsKey(selId)) {
            text("💡 這顆鍵自己還有另外的設定，會優先用它自己的。", 13f, dim = true)
        }

        val what = if (groupScope) group.title else "這顆鍵"
        button("清除${what}的設定") { update({ null }) }
        clearAllButton()
    }

    private fun clearAllButton() {
        button("清除所有按鍵設定") {
            AlertDialog.Builder(this)
                .setMessage("要把所有按鍵都恢復成主題原本的樣子嗎？")
                .setPositiveButton("全部清除") { _, _ ->
                    styles = KeyStyles.EMPTY
                    KeyStyles.save(prefs, styles)
                    kv.reloadPrefs()
                    refresh()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 一列：名稱（下面小字寫是不是自訂的）、色塊（顯示現在的顏色，點了選顏色）、還原
     * @param now 沒自訂時，這顆鍵現在畫面上的顏色
     */
    private fun colorRow(label: String, value: Int?, now: Int, onPick: (Int?) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6f), 0, px(6f))
        }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(TextView(this).apply { text = label; textSize = 15f })
        names.addView(TextView(this).apply {
            text = if (value == null) "主題原本的顏色" else "已自訂"
            textSize = 12f
            alpha = 0.65f
        })
        row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val shown = value ?: now
        val chip = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(shown)
                setStroke(px(1.5f), 0x33000000)
            }
            contentDescription = "$label：" + String.format("#%06X", 0xFFFFFF and shown)
            setOnClickListener { ColorDialog.show(this@StudioActivity, label, shown) { onPick(it) } }
        }
        row.addView(chip, LinearLayout.LayoutParams(px(72f), px(40f)))
        val reset = Button(this).apply {
            text = "改回原色"
            isAllCaps = false
            visibility = if (value == null) View.INVISIBLE else View.VISIBLE
            setOnClickListener { onPick(null) }
        }
        row.addView(reset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = px(8f) })
        panel.addView(row)
    }

    private fun text(s: String, size: Float, bold: Boolean = false, dim: Boolean = false): TextView = TextView(this).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (dim) alpha = 0.7f
        panel.addView(this)
    }

    private fun button(s: String, onClick: () -> Unit) = panel.addView(Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    })
}
