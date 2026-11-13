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
    private lateinit var preview: CatKeyboardView
    private lateinit var themeGroup: RadioGroup
    private val stickerThumbs = HashMap<StickerSlot, android.widget.ImageView>()

    /** 設定一改，預覽就重畫（要存成欄位，不然會被系統回收掉） */
    private val previewListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        preview.reloadPrefs()
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(previewListener)
        super.onDestroy()
    }

    // ---------- 功能鍵貼圖 ----------

    private fun stickerRow(slot: StickerSlot) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val thumb = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0x11000000)
        }
        stickerThumbs[slot] = thumb
        refreshThumb(slot)
        row.addView(thumb, LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()))
        row.addView(TextView(this).apply {
            text = slot.title
            textSize = 15f
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "選圖片"
            isAllCaps = false
            setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_STICKER + slot.ordinal)
            }
        })
        row.addView(Button(this).apply {
            text = "移除"
            isAllCaps = false
            setOnClickListener {
                Stickers.remove(this@SettingsActivity, slot)
                refreshThumb(slot)
                bumpStickers()
            }
        })
        root.addView(row)
    }

    private fun refreshThumb(slot: StickerSlot) {
        val f = Stickers.file(this, slot)
        stickerThumbs[slot]?.setImageBitmap(if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path) else null)
    }

    /** 通知鍵盤重新讀貼圖 */
    private fun bumpStickers() = prefs.sp.edit().putLong("stickers_ver", System.currentTimeMillis()).apply()

    @Deprecated("startActivityForResult 在這裡夠用")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val slot = StickerSlot.values().getOrNull(requestCode - REQ_STICKER) ?: return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (Stickers.import(this, slot, uri) != null) {
            refreshThumb(slot)
            bumpStickers()
        } else {
            android.widget.Toast.makeText(this, "這張圖讀不出來，換一張試試", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 自訂配色 ----------

    private val palette = listOf(
        "#FFFFFF", "#FDF0F5", "#FFE4EC", "#F9C9D6", "#F49AB7", "#E0688E",
        "#FFF3E0", "#FFE0B2", "#FFF9C4", "#E8F5E9", "#C8E6C9", "#E0F7FA",
        "#B3E5FC", "#E1D5F5", "#C5B3E6", "#9E86C8", "#B59AAB", "#5B4B57",
        "#3E3A4F", "#273238", "#000000",
    )

    private fun swatch(color: Int, sizeDp: Int) = View(this).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setStroke((1 * dp).toInt(), 0x33000000)
        }
        layoutParams = LinearLayout.LayoutParams((sizeDp * dp).toInt(), (sizeDp * dp).toInt()).apply {
            setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
        }
    }

    private fun colorRow(label: String, get: () -> Int, set: (Int) -> Unit) {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * dp).toInt(), 0, 0)
        }
        val current = swatch(get(), 22)
        head.addView(current)
        head.addView(TextView(this).apply { text = label; textSize = 15f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        fun apply(c: Int) {
            set(c)
            (current.background as android.graphics.drawable.GradientDrawable).setColor(c)
            if (prefs.theme != "custom") {
                prefs.theme = "custom"
                themeGroup.findViewWithTag<RadioButton>("custom")?.isChecked = true
            }
        }
        head.addView(Button(this).apply {
            text = "色碼"
            isAllCaps = false
            setOnClickListener {
                val input = EditText(this@SettingsActivity).apply {
                    setText(String.format("#%06X", 0xFFFFFF and get()))
                }
                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(label)
                    .setView(input)
                    .setPositiveButton("確定") { _, _ ->
                        try { apply(android.graphics.Color.parseColor(input.text.toString().trim())) }
                        catch (_: Exception) {
                            android.widget.Toast.makeText(this@SettingsActivity, "色碼格式像 #F49AB7", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        })
        root.addView(head)
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (hex in palette) {
            val c = android.graphics.Color.parseColor(hex)
            strip.addView(swatch(c, 34).apply { setOnClickListener { apply(c) } })
        }
        root.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        })
    }

    companion object {
        private const val REQ_STICKER = 100
    }
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        prefs.migrate()
        haptics = Haptics(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, p, p, p * 3)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        title("🐾 貓爪鍵盤")
        note("第三階段：功能鍵貼圖、按鍵動畫、自訂配色都在「外觀」裡，上面有即時預覽可以直接按按看。")

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
            "click" to "清脆",
            "tick" to "輕點（更短更輕）",
            "custom" to "自訂長度（最像 Google 注音：強度 70、12 毫秒）",
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
        note("預覽（可以直接按按看動畫和震動）：")
        preview = CatKeyboardView(this, prefs, haptics)
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        prefs.sp.registerOnSharedPreferenceChangeListener(previewListener)

        themeGroup = radios(
            listOf(
                "classic" to "經典深色（Google 注音風）",
                "stitch" to "粉紅縫線",
                "catpaw" to "貓爪肉球",
                "custom" to "自訂配色（在下面調顏色）",
            ),
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

        section("按鍵動畫")
        radios(
            listOf(
                "none" to "不要動畫",
                "bounce" to "彈一下",
                "heart" to "冒小愛心 ♡",
                "paw" to "冒小貓爪 🐾",
                "star" to "冒小星星 ☆",
            ),
            prefs.pressEffect,
        ) { prefs.pressEffect = it }

        section("功能鍵貼圖")
        note("從相簿選圖片貼在功能鍵上（去背的 PNG 效果最好）。圖片會縮小後存在 App 裡，相簿的原圖刪掉也沒關係。")
        for (slot in StickerSlot.values()) stickerRow(slot)

        section("自訂配色")
        note("點色塊就會換成「自訂配色」主題，上面的預覽會跟著變。也可以按「色碼」輸入像 #F49AB7 這樣的顏色。")
        colorRow("背景", { prefs.customBg }) { prefs.customBg = it }
        colorRow("按鍵", { prefs.customKey }) { prefs.customKey = it }
        colorRow("文字", { prefs.customText }) { prefs.customText = it }
        colorRow("小字（數字、英文）", { prefs.customSub }) { prefs.customSub = it }
        colorRow("強調色（Enter、選取）", { prefs.customAccent }) { prefs.customAccent = it }

        section("選字")
        root.addView(Switch(this).apply {
            text = "注音直接顯示在輸入框裡（關掉＝像 Google 注音，顯示在鍵盤上方，選了字才送出）"
            isChecked = prefs.inlineComposing
            setOnCheckedChangeListener { _, b -> prefs.inlineComposing = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        note("記憶選字：你從選字列挑的字，下次會排第一個；同一次打字裡分段挑的字（例如先選「妳」再選「好」）會記成新詞「妳好」。直接按空白／Enter 接受預設的不算。只存在這支手機裡。")
        button("清除學習紀錄") {
            java.io.File(filesDir, "user_scores.txt").delete()
            prefs.sp.edit().putLong("clear_learning", System.currentTimeMillis()).apply()
            android.widget.Toast.makeText(this, "已清除", android.widget.Toast.LENGTH_SHORT).show()
        }

        section("剪貼簿")
        root.addView(Switch(this).apply {
            text = "記錄複製過的文字（鍵盤上方的剪貼簿按鈕）"
            isChecked = prefs.clipboardHistory
            setOnCheckedChangeListener { _, b -> prefs.clipboardHistory = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        note("只存在這支手機裡。沒釘選的 1 小時後會自動刪掉；密碼管理 App 複製的密碼不會被記錄。")
        button("清除剪貼簿紀錄（包含釘選的）") {
            java.io.File(filesDir, "clips.json").delete()
            prefs.sp.edit().putLong("clear_clips", System.currentTimeMillis()).apply()
            android.widget.Toast.makeText(this, "已清除", android.widget.Toast.LENGTH_SHORT).show()
        }

        section("小提示")
        note("・不打聲調也可以：ㄋㄧㄏㄠ → 你好\n・選字列可以左右滑，按右邊的箭頭可以展開全部選項\n・長按注音鍵會跳出選單（像 Google 注音），手指左右滑選大寫、注音符號或小寫／數字，放開就直接打出來\n・長按空白鍵可以切換到其他輸入法\n・按住刪除鍵會連續刪除\n・英文模式點兩下 Shift 會鎖定大寫")
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

    private fun radios(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit): RadioGroup {
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
        return group
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
