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
    private var status: TextView? = null
    private lateinit var customMsRow: View
    private var preview: CatKeyboardView? = null
    private var themeGroup: RadioGroup? = null
    private val stickerThumbs = HashMap<StickerSlot, android.widget.ImageView>()

    /** 設定一改，預覽就重畫（要存成欄位，不然會被系統回收掉） */
    private val previewListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        preview?.let {
            it.reloadPrefs()
            it.quickItems = if (prefs.quickBar) prefs.quickList() else emptyList()
        }
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
        if (requestCode == REQ_FONT) {
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) return
            val r = Fonts.import(this, uri)
            android.widget.Toast.makeText(this, r.message, android.widget.Toast.LENGTH_LONG).show()
            if (r.id != null) recreate() // 重新整理字體清單
            return
        }
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
                themeGroup?.findViewWithTag<RadioButton>("custom")?.isChecked = true
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
        private const val REQ_FONT = 200
    }
    private val dp get() = resources.displayMetrics.density

    // ---------- 分頁 ----------

    /**
     * 設定的分類：代號、標題、說明、要不要在上面放預覽鍵盤、
     * 按返回要回到哪一頁、要不要列在首頁
     */
    private class PageInfo(
        val id: String, val title: String, val desc: String, val preview: Boolean,
        val parent: String = "home", val listed: Boolean = true,
    )

    private val pages = listOf(
        PageInfo("haptic", "🎵 震動手感", "震動方式、強度、長度", false),
        PageInfo("look", "🎨 外觀", "主題、按鍵大小、字體、功能鍵貼圖", true),
        PageInfo("effect", "✨ 按鍵動畫", "彈一下、愛心、蝴蝶、魔法火花…", true),
        PageInfo("quick", "⌨ 快捷符號列", "一鍵打出成對符號、移動游標", true),
        PageInfo("candidate", "📝 選字與記憶", "組字方式、清除學習紀錄", false),
        PageInfo("clip", "📋 剪貼簿", "剪貼簿紀錄開關、清除", false),
        PageInfo("tips", "💡 小提示", "一些好用的小技巧", false),
        // 從「外觀」的主題清單跳進來，不列在首頁
        PageInfo("color", "🖌 自訂配色", "自己調鍵盤的 5 個顏色", true, parent = "look", listed = false),
    )

    private fun parentOf(id: String) = pages.firstOrNull { it.id == id }?.parent ?: "home"
    private var scroll: ScrollView? = null

    private var page = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        prefs.migrate()
        haptics = Haptics(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(previewListener)
        showPage(savedInstanceState?.getString("page") ?: "home")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("page", page) // 換字體會重新整理畫面，要記得停在哪一頁
    }

    @Deprecated("Activity 的返回鍵處理")
    override fun onBackPressed() {
        if (page != "home") showPage(parentOf(page))
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { showPage(parentOf(page)); return true }
        return super.onOptionsItemSelected(item)
    }

    /** 換頁：有需要的頁面，預覽鍵盤固定在最上面，下面的設定可以捲動 */
    private fun showPage(id: String) {
        page = id
        val info = pages.firstOrNull { it.id == id }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = null
        status = null
        themeGroup = null
        if (info?.preview == true) {
            val v = CatKeyboardView(this, prefs, haptics)
            // 預覽裡放幾個選字，才看得到「選字字體」
            v.reading = "ㄇㄠ ㄓㄠˇ"
            v.candidates = listOf("貓爪", "毛", "貓", "帽子", "冒")
            v.quickItems = if (prefs.quickBar) prefs.quickList() else emptyList()
            outer.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            preview = v
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, ((if (info?.preview == true) 8 else 20) * dp).toInt(), p, p * 3)
        }
        val sv = ScrollView(this).apply { addView(root) }
        scroll = sv
        outer.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(outer)
        actionBar?.title = info?.title?.substringAfter(' ') ?: "貓爪鍵盤"
        actionBar?.setDisplayHomeAsUpEnabled(info != null)
        when (id) {
            "haptic" -> pageHaptic()
            "look" -> pageLook()
            "effect" -> pageEffect()
            "color" -> pageColor()
            "quick" -> pageQuick()
            "candidate" -> pageCandidate()
            "clip" -> pageClip()
            "tips" -> pageTips()
            else -> pageHome()
        }
        updateStatus()
    }

    private fun pageHome() {
        title("🐾 貓爪鍵盤")

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
            hint = "在這裡打字試手感…"
            minLines = 2
            gravity = Gravity.TOP
        })

        section("設定")
        for (p in pages.filter { it.listed }) menuItem(p)
    }

    /** 首頁的分類按鈕：標題＋一行說明，點下去進到那一頁 */
    private fun menuItem(p: PageInfo) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (14 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(0x0F000000)
            }
            isClickable = true
            setOnClickListener { showPage(p.id) }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = p.title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = p.desc
            textSize = 13f
            alpha = 0.7f
        })
        box.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(TextView(this).apply { text = "›"; textSize = 24f; alpha = 0.5f })
        root.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (8 * dp).toInt()
        })
    }

    private fun pageHaptic() {
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
    }

    /** 外觀：主題、按鍵、字體、功能鍵貼圖，最上面有跳轉按鈕 */
    private fun pageLook() {
        val anchors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(anchors)
        val targets = LinkedHashMap<String, View>()
        fun anchor(name: String) {
            val v = TextView(this).apply {
                text = name
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, (22 * dp).toInt(), 0, (6 * dp).toInt())
            }
            root.addView(v)
            targets[name] = v
        }
        anchor("主題")
        pageTheme()
        anchor("字體")
        pageFont()
        anchor("功能鍵貼圖")
        pageSticker()
        // 跳轉按鈕：點了直接捲到那一段
        for ((name, v) in targets) {
            anchors.addView(Button(this).apply {
                text = name
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
                setOnClickListener { scroll?.smoothScrollTo(0, v.top) }
            })
        }
    }

    private fun pageTheme() {
        themeGroup = radios(
            listOf(
                "classic" to "經典深色（Google 注音風）",
                "stitch" to "粉紅縫線",
                "catpaw" to "貓爪肉球",
            ) + MacaronTheme.PALETTES.map { it.id to it.title } + MagicTheme.PALETTES.map { it.id to it.title } + listOf(
                "custom" to "自訂配色",
            ),
            prefs.theme,
        ) { prefs.theme = it }
        root.addView(Button(this).apply {
            text = "🖌 調整自訂配色 ›"
            isAllCaps = false
            setOnClickListener { showPage("color") }
        })
        subTitle("按鍵")
        slider("按鍵高度", 36, 64, prefs.keyHeightDp, " dp（Google 注音約 45）") { prefs.keyHeightDp = it }
        slider("長按時間", 200, 700, prefs.longPressMs, " 毫秒") { prefs.longPressMs = it }
        root.addView(Switch(this).apply {
            text = "按下時顯示放大的字"
            isChecked = prefs.preview
            setOnCheckedChangeListener { _, b -> prefs.preview = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
    }

    private fun pageFont() {
        note("照文字種類分三組，可以各用不同字體。例如注音鍵上的「ㄅ」用注音字體、下面的小「1」用英文字體。")
        subTitle("中文字體（選字列、中文字、全形標點「」，。）")
        radios(Fonts.options(this), prefs.zhFont) { prefs.zhFont = it }
        subTitle("注音字體（注音鍵、注音小框框、聲調）")
        radios(Fonts.options(this), prefs.zhuyinFont) { prefs.zhuyinFont = it }
        subTitle("英文和符號字體（英文、數字、半形符號）")
        radios(Fonts.options(this), prefs.enFont) { prefs.enFont = it }

        subTitle("我的字體")
        note("可以上傳自己的 .ttf / .otf 字體檔，上傳後會出現在上面三個清單裡。請使用你有權利使用的字體（例如免費授權或自己買的）。")
        button("上傳字體檔") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // 很多檔案管理 App 不會把字體檔標成 font/ttf，所以全部列出來
            }
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_FONT)
        }
        for (f in Fonts.userFonts(this)) {
            val id = "user:" + f.name
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = Fonts.displayName(f) + "（" + (f.length() / 1024 / 1024.0).let { String.format("%.1f", it) } + " MB）"
                textSize = 15f
                typeface = Fonts.get(this@SettingsActivity, id)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "刪除"
                isAllCaps = false
                setOnClickListener {
                    Fonts.delete(this@SettingsActivity, id)
                    if (prefs.zhFont == id) prefs.zhFont = "system"
                    if (prefs.zhuyinFont == id) prefs.zhuyinFont = "system"
                    if (prefs.enFont == id) prefs.enFont = "system"
                    recreate()
                }
            })
            root.addView(row)
        }
        note("字體授權：jf open 粉圓（justfont）、芫荽 Iansui、霞鶩文楷 TC（LXGW），都是 SIL Open Font License 1.1。芫荽和霞鶩文楷只保留常用字，比較少見的字會用系統字體顯示。")
    }

    private fun pageEffect() {
        note("選好之後，在上面的預覽鍵盤按按看。")
        radios(PressEffects.ALL, prefs.pressEffect) { prefs.pressEffect = it }
    }

    private fun pageSticker() {
        note("從相簿選圖片貼在功能鍵上（去背的 PNG 效果最好）。圖片會縮小後存在 App 裡，相簿的原圖刪掉也沒關係。")
        for (slot in StickerSlot.values()) stickerRow(slot)
    }

    private fun pageColor() {
        note("點色塊就會換成「自訂配色」主題，上面的預覽會跟著變。也可以按「色碼」輸入像 #F49AB7 這樣的顏色。")
        colorRow("背景", { prefs.customBg }) { prefs.customBg = it }
        colorRow("按鍵", { prefs.customKey }) { prefs.customKey = it }
        colorRow("文字", { prefs.customText }) { prefs.customText = it }
        colorRow("小字（數字、英文）", { prefs.customSub }) { prefs.customSub = it }
        colorRow("強調色（Enter、選取）", { prefs.customAccent }) { prefs.customAccent = it }
    }

    private fun pageCandidate() {
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
    }

    private fun pageClip() {
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
    }

    private fun pageTips() {
        note("・不打聲調也可以：ㄋㄧㄏㄠ → 你好\n・選字列可以左右滑，按右邊的箭頭可以展開全部選項\n・分段選字：選好的字會先留在注音小框框，全部選完才送出；按刪除鍵會先把選好的字退回成注音\n・長按注音鍵會跳出選單（像 Google 注音），手指左右滑選大寫、注音符號或小寫／數字，放開就直接打出來\n・長按空白鍵可以切換到其他輸入法\n・按住刪除鍵會連續刪除\n・英文模式點兩下 Shift 會鎖定大寫\n・符號鍵盤可以左右滑換頁")
    }

    // ---------- 快捷符號列（一個按鈕一行） ----------

    private fun pageQuick() {
        root.addView(Switch(this).apply {
            text = "在按鍵上面多一排快捷符號"
            isChecked = prefs.quickBar
            setOnCheckedChangeListener { _, b -> prefs.quickBar = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        note("每一行是一個按鈕，最多 ${Prefs.MAX_QUICK} 個：\n・兩個字（像 () 或 「」）：一次打出一組，游標停在中間；先選取文字再按會把它包起來\n・用 | 標游標位置，例如 **|**\n・⇤ 跳到最前面、⇥ 跳到最後面、◀ ▶ 游標左右移一格\n・取消勾選＝暫時不顯示，不用刪掉")

        val items = prefs.quickEntries().toMutableList()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        lateinit var addButton: Button
        lateinit var render: () -> Unit

        fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            setOnClickListener { onClick() }
        }

        render = {
            list.removeAllViews()
            items.forEachIndexed { i, q ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(android.widget.CheckBox(this).apply {
                    isChecked = q.on
                    setOnCheckedChangeListener { _, b -> items[i] = items[i].copy(on = b) }
                })
                row.addView(EditText(this).apply {
                    setText(q.text)
                    textSize = 18f
                    isSingleLine = true
                    hint = "例如 「」"
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            items[i] = items[i].copy(text = s?.toString()?.replace(" ", "") ?: "")
                        }
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(smallButton("↑") {
                    if (i > 0) { items.add(i - 1, items.removeAt(i)); render() }
                })
                row.addView(smallButton("↓") {
                    if (i < items.size - 1) { items.add(i + 1, items.removeAt(i)); render() }
                })
                row.addView(smallButton("刪除") { items.removeAt(i); render() })
                list.addView(row)
            }
            addButton.isEnabled = items.size < Prefs.MAX_QUICK
            addButton.text = if (items.size < Prefs.MAX_QUICK) "＋ 新增按鈕" else "最多 ${Prefs.MAX_QUICK} 個"
        }

        addButton = Button(this).apply {
            isAllCaps = false
            setOnClickListener { if (items.size < Prefs.MAX_QUICK) { items.add(Prefs.QuickEntry("", true)); render() } }
        }
        root.addView(addButton)

        subTitle("快速加入游標按鈕")
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((sym, desc) in listOf("⇤" to "最前", "⇥" to "最後", "◀" to "左移", "▶" to "右移")) {
            chips.addView(smallButton("$sym $desc") {
                if (items.size < Prefs.MAX_QUICK) { items.add(Prefs.QuickEntry(sym, true)); render() }
                else android.widget.Toast.makeText(this, "最多 ${Prefs.MAX_QUICK} 個", android.widget.Toast.LENGTH_SHORT).show()
            })
        }
        root.addView(chips)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }
        actions.addView(Button(this).apply {
            text = "儲存"
            isAllCaps = false
            setOnClickListener {
                val cleaned = items.filter { it.text.isNotBlank() }
                prefs.setQuickEntries(cleaned)
                items.clear(); items.addAll(cleaned); render()
                android.widget.Toast.makeText(this@SettingsActivity, "已儲存", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        actions.addView(Button(this).apply {
            text = "取消"
            isAllCaps = false
            setOnClickListener { items.clear(); items.addAll(prefs.quickEntries()); render() }
        })
        actions.addView(Button(this).apply {
            text = "恢復預設"
            isAllCaps = false
            setOnClickListener { items.clear(); items.addAll(Prefs.defaultQuickEntries()); render() }
        })
        root.addView(actions)
        render()
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
        val status = status ?: return
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

    private fun subTitle(s: String) = root.addView(TextView(this).apply {
        text = s
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (10 * dp).toInt(), 0, (2 * dp).toInt())
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
