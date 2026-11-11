package io.github.yolanda1027.catpaw

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** 設定頁：啟用步驟、試打區、震動和外觀調整 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var haptics: Haptics
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var customMsRow: View
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        haptics = Haptics(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, p, p, p * 3)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        title("🐾 貓爪鍵盤")
        note("第一階段：手感原型。現在注音鍵只會打出注音符號（按空白送出），選字功能會在下一階段加上。")

        section("開始使用")
        status = note("")
        button("① 到系統設定啟用「貓爪鍵盤」") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        button("② 切換成貓爪鍵盤") {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }

        section("試打區")
        root.addView(EditText(this).apply {
            hint = "在這裡打字試手感…調整下面的設定後，再回來打打看"
            minLines = 3
            gravity = Gravity.TOP
        })

        section("震動手感")
        val modes = listOf(
            "click" to "清脆（推薦先試這個）",
            "tick" to "輕點（更短更輕）",
            "custom" to "自訂長度（傳統震動）",
            "system" to "跟隨系統設定",
            "off" to "關閉震動",
        )
        radios(modes, prefs.hapticMode) {
            prefs.hapticMode = it
            customMsRow.visibility = if (it == "custom") View.VISIBLE else View.GONE
            haptics.perform(root, prefs)
        }
        slider("強度", 1, 100, prefs.strength, "") { prefs.strength = it }
        customMsRow = slider("長度", 1, 60, prefs.vibrateMs, " 毫秒") { prefs.vibrateMs = it }
        customMsRow.visibility = if (prefs.hapticMode == "custom") View.VISIBLE else View.GONE
        button("試震一下") { haptics.perform(it, prefs) }

        section("外觀")
        radios(
            listOf("classic" to "經典深色（Google 注音風）", "catpaw" to "貓爪肉球（試做版）"),
            prefs.theme,
        ) { prefs.theme = it }
        slider("按鍵高度", 36, 64, prefs.keyHeightDp, " dp（Google 注音約 45）") { prefs.keyHeightDp = it }
        slider("長按時間", 200, 700, prefs.longPressMs, " 毫秒") { prefs.longPressMs = it }
        root.addView(Switch(this).apply {
            text = "按下時顯示放大的字"
            isChecked = prefs.preview
            setOnCheckedChangeListener { _, b -> prefs.preview = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })

        section("小提示")
        note("・長按注音鍵可以打出右下角的數字／英文\n・長按空白鍵可以切換到其他輸入法\n・按住刪除鍵會連續刪除\n・英文模式點兩下 Shift 會鎖定大寫")
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStatus() // 從輸入法選單回來時更新
    }

    private fun updateStatus() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        status.text = when {
            current -> "✅ 貓爪鍵盤使用中！"
            enabled -> "已啟用，再按 ② 切換過去就可以用了"
            else -> "還沒啟用，先按 ①，在清單裡打開「貓爪鍵盤」"
        }
    }

    // ---------- 小工具 ----------

    private fun title(s: String) = root.addView(TextView(this).apply {
        text = s
        textSize = 26f
        setTypeface(typeface, Typeface.BOLD)
    })

    private fun section(s: String) = root.addView(TextView(this).apply {
        text = s
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (24 * dp).toInt(), 0, (8 * dp).toInt())
    })

    private fun note(s: String): TextView = TextView(this).apply {
        text = s
        textSize = 14f
        setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        root.addView(this)
    }

    private fun button(s: String, onClick: (View) -> Unit) = root.addView(Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener(onClick)
    })

    private fun radios(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        val group = RadioGroup(this)
        options.forEachIndexed { i, (key, label) ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                tag = key
                isChecked = key == current
            })
        }
        group.setOnCheckedChangeListener { g, id -> onPick(g.findViewById<View>(id).tag as String) }
        root.addView(group)
    }

    private fun slider(label: String, min: Int, max: Int, value: Int, unit: String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        val tv = TextView(this).apply { text = "$label：$value$unit" }
        val sb = SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    tv.text = "$label：$p$unit"
                    if (fromUser) onChange(p)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {
                    haptics.perform(s, prefs) // 放開時震一下讓你感受
                }
            })
        }
        box.addView(tv)
        box.addView(sb)
        root.addView(box)
        return box
    }
}
