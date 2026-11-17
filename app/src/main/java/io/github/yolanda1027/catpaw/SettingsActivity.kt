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
        if (Eyedropper.onResult(this, requestCode, resultCode, data)) return
        if (requestCode == REQ_MY_STICKER) {
            if (resultCode != RESULT_OK || data == null) return
            val uris = ArrayList<android.net.Uri>()
            data.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) }
            if (uris.isEmpty()) data.data?.let { uris.add(it) }
            var ok = 0
            var bad = 0
            for (u in uris) {
                val name = try {
                    contentResolver.openInputStream(u)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 40 * 1024 * 1024) null else MyStickers.add(this, bytes)
                    }
                } catch (_: Exception) { null }
                if (name != null) ok++ else bad++
            }
            prefs.sp.edit().putLong("my_stickers_ver", System.currentTimeMillis()).apply() // 讓鍵盤重新整理
            android.widget.Toast.makeText(this, "加了 $ok 張貼圖" + if (bad > 0) "（$bad 張讀不出來）" else "", android.widget.Toast.LENGTH_LONG).show()
            showPage("mystickers")
            return
        }
        if (requestCode == REQ_EXPORT) {
            val uri = data?.data
            val content = pendingExport
            pendingExport = null
            if (resultCode != RESULT_OK || uri == null || content == null) return
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                android.widget.Toast.makeText(this, "主題已匯出，可以把檔案傳給別人了", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "存檔失敗：${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == REQ_IMPORT) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) handleImport(uri)
            return
        }
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

    /** 色票：每一行是一個色系，由淺到深 */
    private val paletteFamilies = listOf(
        "粉" to listOf("#FFE4EC", "#F9C9D6", "#F48FB1", "#D8577E"),
        "橘" to listOf("#FFE8D9", "#FFC9A8", "#FFA06E", "#E07A45"),
        "黃" to listOf("#FFF7CC", "#FFE98A", "#FFD54F", "#E0B12E"),
        "綠" to listOf("#E3F5EA", "#B9E4C9", "#7FCFA0", "#3F9E6C"),
        "藍" to listOf("#E3F0FF", "#B8D6FA", "#7BAAF0", "#3F72C8"),
        "紫" to listOf("#EEE6FA", "#D5C4F2", "#B39CF0", "#7A5CC8"),
        "棕" to listOf("#F3E8DC", "#DCC3A8", "#B08B68", "#6B4F3A"),
        "灰" to listOf("#FFFFFF", "#D9D9DE", "#8E8E99", "#2E2E38"),
    )

    /** 整組配色：名稱、背景、按鍵、文字、小字、強調色 */
    private class ColorPreset(val name: String, val bg: String, val key: String, val text: String, val sub: String, val accent: String)

    private val colorPresets = listOf(
        ColorPreset("🌸 粉色", "#FFE8EF", "#FFFFFF", "#8C4A5E", "#D08AA0", "#F48FB1"),
        ColorPreset("🫐 藍色", "#E6F0FF", "#FFFFFF", "#3E5A86", "#8AA6CF", "#7BAAF0"),
        ColorPreset("🌿 綠色", "#E5F5EC", "#FFFFFF", "#3D6B55", "#84B89D", "#6CC59A"),
        ColorPreset("💜 紫色", "#EEE7FA", "#FFFFFF", "#5C4A85", "#A08CC8", "#B39CF0"),
        ColorPreset("🍑 蜜桃", "#FFEDE2", "#FFFFFF", "#8A5038", "#D2957A", "#FFA987"),
        ColorPreset("🍋 檸檬", "#FFF8D6", "#FFFFFF", "#7A6420", "#C2A64A", "#F5CF4A"),
        ColorPreset("🍫 奶茶", "#F1E6DA", "#FFFAF3", "#6B4F3A", "#B0937A", "#C9A27E"),
        ColorPreset("🌙 夜空", "#23243A", "#34365A", "#E8E6FF", "#9A98C8", "#A58CFF"),
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

    /** 一個可以調的顏色（背景、按鍵…）：目前的顏色、名稱、選色按鈕；點這一列＝選它當色票要套用的對象 */
    private class ColorSlot(val label: String, val get: () -> Int, val set: (Int) -> Unit) {
        lateinit var row: LinearLayout
        lateinit var chip: View
    }

    private fun switchToCustom() {
        if (prefs.theme != "custom") {
            prefs.theme = "custom"
            themeGroup?.findViewWithTag<RadioButton>("custom")?.isChecked = true
        }
    }

    /** 選色器視窗：大色塊＋彩虹條、新舊顏色對照、色碼欄 */
    private fun showColorPicker(label: String, initial: Int, onPick: (Int) -> Unit) =
        ColorDialog.show(this, label, initial, onPick)

    companion object {
        private const val REQ_STICKER = 100
        private const val REQ_FONT = 200
        private const val REQ_EXPORT = 300
        private const val REQ_IMPORT = 301
        private const val REQ_MY_STICKER = 400
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
        PageInfo("look", "🎨 外觀", "主題、按鍵大小、字體、功能鍵貼圖、主題匯入匯出", true),
        PageInfo("effect", "✨ 按鍵動畫", "彈一下、愛心、蝴蝶、魔法火花…", true),
        PageInfo("quick", "⌨ 快捷符號列", "一鍵打出成對符號、移動游標", true),
        PageInfo("candidate", "📝 選字與記憶", "組字方式、簡體／繁體、清除學習紀錄", false),
        PageInfo("clip", "📋 剪貼簿", "剪貼簿紀錄開關、清除", false),
        PageInfo("mystickers", "🖼️ 我的貼圖", "上傳自己的圖片，在表情鍵盤裡用", false),
        PageInfo("tips", "💡 小提示", "一些好用的小技巧", false),
        // 從「外觀」的主題清單跳進來，不列在首頁
        PageInfo("color", "🖌 自訂配色", "自己調鍵盤的 5 個顏色", true, parent = "look", listed = false),
    )

    private fun parentOf(id: String) = pages.firstOrNull { it.id == id }?.parent ?: "home"
    private var scroll: ScrollView? = null

    private var page = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.autoSpaceButtons(this)
        prefs = Prefs(this)
        prefs.migrate()
        haptics = Haptics(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(previewListener)
        showPage(savedInstanceState?.getString("page") ?: intent?.getStringExtra("page") ?: "home")
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
            "mystickers" -> pageMyStickers()
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

        section("自己設計")
        button("🖌️ 打開貓爪主題工作室（點按鍵改樣子）") {
            startActivity(Intent(this, StudioActivity::class.java))
        }
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
        anchor("匯入／匯出")
        pageShare()
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

    // ---------- 主題匯入／匯出 ----------

    /** 匯出時先把內容準備好，等使用者選好存檔位置再寫進去 */
    private var pendingExport: String? = null

    private fun pageShare() {
        note("把現在的鍵盤外觀存成一個主題檔（.json），傳給家人朋友，對方匯入就能套用一模一樣的樣子。自己上傳的字體不會放進檔案。")
        button("📤 匯出目前的主題") { showExportDialog() }
        button("📥 匯入主題檔") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // 從 LINE 存下來的 .json 常常不會被標成 application/json
            }
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_IMPORT)
        }
    }

    private fun showExportDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, (8 * dp).toInt(), p, 0)
        }
        val name = EditText(this).apply { hint = "主題名稱，例如：奶油黃貓爪"; isSingleLine = true }
        box.addView(name)
        box.addView(TextView(this).apply {
            text = "一定會包含主題和配色，另外要放："
            setPadding(0, (10 * dp).toInt(), 0, 0)
        })
        val checks = ThemeIO.Part.values().associateWith { part ->
            android.widget.CheckBox(this).apply { text = part.title; isChecked = true }.also { box.addView(it) }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("匯出主題")
            .setView(box)
            .setPositiveButton("下一步：選存檔位置") { _, _ ->
                val parts = checks.filterValues { it.isChecked }.keys
                val n = name.text.toString().trim().ifEmpty { "我的貓爪鍵盤" }
                pendingExport = ThemeIO.export(this, prefs, n, parts)
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "貓爪鍵盤主題_" + n.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".json")
                }
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_EXPORT)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleImport(uri: android.net.Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > ThemeIO.MAX_BYTES) null else String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) { null }
        val o = text?.let { ThemeIO.parse(it) }
        if (o == null) {
            android.widget.Toast.makeText(this, "這不是貓爪鍵盤的主題檔", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val (name, parts) = ThemeIO.describe(o)
        android.app.AlertDialog.Builder(this)
            .setTitle("匯入「$name」？")
            .setMessage("包含：" + parts.joinToString("、") + "\n\n套用後會取代你目前的這些設定。")
            .setPositiveButton("套用") { _, _ ->
                val warnings = ThemeIO.apply(this, prefs, o)
                showPage("look") // 重新整理畫面，讓選項跟著變
                val msg = if (warnings.isEmpty()) "已套用「$name」🎉" else "已套用「$name」\n\n" + warnings.joinToString("\n")
                android.app.AlertDialog.Builder(this).setMessage(msg).setPositiveButton("好", null).show()
            }
            .setNegativeButton("取消", null)
            .show()
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
        note("想自己決定動畫怎麼動、飛出什麼、什麼顏色，或只讓某幾顆鍵用特別的動畫，到主題工作室調：")
        button("🎨 打開主題工作室") { startActivity(Intent(this, StudioActivity::class.java)) }
    }

    private fun pageSticker() {
        note("從相簿選圖片貼在功能鍵上（去背的 PNG 效果最好）。圖片會縮小後存在 App 裡，相簿的原圖刪掉也沒關係。")
        for (slot in StickerSlot.values()) stickerRow(slot)
    }

    private fun pageColor() {
        val slots = listOf(
            ColorSlot("背景", { prefs.customBg }) { prefs.customBg = it },
            ColorSlot("按鍵", { prefs.customKey }) { prefs.customKey = it },
            ColorSlot("文字", { prefs.customText }) { prefs.customText = it },
            ColorSlot("小字（數字、英文）", { prefs.customSub }) { prefs.customSub = it },
            ColorSlot("強調色（Enter、選取）", { prefs.customAccent }) { prefs.customAccent = it },
        )
        var selected = slots[0]

        fun refreshChips() {
            for (sl in slots) {
                (sl.chip.background as android.graphics.drawable.GradientDrawable).setColor(sl.get())
                sl.row.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12 * dp
                    setColor(if (sl === selected) 0x22F48FB1 else 0x00000000)
                    if (sl === selected) setStroke((1.5f * dp).toInt(), 0x99F48FB1.toInt())
                }
            }
        }

        fun applyTo(sl: ColorSlot, c: Int) {
            sl.set(c)
            switchToCustom()
            refreshChips()
        }

        // 快速套用整組
        subTitle("快速套用整組配色")
        var line: LinearLayout? = null
        colorPresets.forEachIndexed { i, p ->
            if (i % 4 == 0) {
                line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                root.addView(line)
            }
            line!!.addView(TextView(this).apply {
                text = p.name
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.parseColor(p.text))
                val pad = (8 * dp).toInt()
                setPadding(pad / 2, pad, pad / 2, pad)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12 * dp
                    setColor(android.graphics.Color.parseColor(p.bg))
                    setStroke((2 * dp).toInt(), android.graphics.Color.parseColor(p.accent))
                }
                setOnClickListener {
                    prefs.customBg = android.graphics.Color.parseColor(p.bg)
                    prefs.customKey = android.graphics.Color.parseColor(p.key)
                    prefs.customText = android.graphics.Color.parseColor(p.text)
                    prefs.customSub = android.graphics.Color.parseColor(p.sub)
                    prefs.customAccent = android.graphics.Color.parseColor(p.accent)
                    switchToCustom()
                    refreshChips()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
            })
        }

        // 個別顏色
        subTitle("調整個別顏色")
        note("先點要改的部分，再點下面的色票；或按「選色」自由選。")
        for (sl in slots) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (6 * dp).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
                isClickable = true
                setOnClickListener { selected = sl; refreshChips() }
            }
            val chip = swatch(sl.get(), 26)
            chip.setOnClickListener { selected = sl; refreshChips(); showColorPicker(sl.label, sl.get()) { applyTo(sl, it) } }
            row.addView(chip)
            row.addView(TextView(this).apply {
                text = sl.label
                textSize = 15f
                setPadding((6 * dp).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "選色"
                isAllCaps = false
                setOnClickListener { selected = sl; refreshChips(); showColorPicker(sl.label, sl.get()) { applyTo(sl, it) } }
            })
            sl.row = row
            sl.chip = chip
            root.addView(row)
        }

        // 色票表：一直行一個色系，由上到下淺到深
        subTitle("色票")
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, _) in paletteFamilies) {
            header.addView(TextView(this).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                alpha = 0.7f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(header)
        for (shade in 0 until 4) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((_, colors) in paletteFamilies) {
                val c = android.graphics.Color.parseColor(colors[shade])
                val cell = LinearLayout(this).apply { gravity = Gravity.CENTER }
                cell.addView(swatch(c, 34).apply { setOnClickListener { applyTo(selected, c) } })
                r.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            root.addView(r)
        }
        refreshChips()
    }

    private fun pageCandidate() {
        root.addView(Switch(this).apply {
            text = "注音直接顯示在輸入框裡（關掉＝像 Google 注音，顯示在鍵盤上方，選了字才送出）"
            isChecked = prefs.inlineComposing
            setOnCheckedChangeListener { _, b -> prefs.inlineComposing = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        root.addView(Switch(this).apply {
            text = "選字面板左下角放「簡」切換鍵"
            isChecked = prefs.showScToggle
            setOnCheckedChangeListener { _, b ->
                prefs.showScToggle = b
                if (!b) prefs.simplified = false // 拿掉切換鍵就回到繁體，不會卡在簡體
            }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        note("打字時把選字面板展開（選字列右邊的箭頭），左下角的「簡」按一下就切換簡體／繁體，面板上的字會馬上換。選字一樣用繁體詞庫，只有顯示和打出去的字變簡體（對照表來自 OpenCC）。關掉這個開關會拿掉切換鍵，並回到繁體。")
        note("記憶選字：你從選字列挑的字，下次會排第一個；同一次打字裡分段挑的字（例如先選「妳」再選「好」）會記成新詞「妳好」。直接按空白／Enter 接受預設的不算。只存在這支手機裡。")
        button("清除學習紀錄") {
            java.io.File(filesDir, "user_scores.txt").delete()
            prefs.sp.edit().putLong("clear_learning", System.currentTimeMillis()).apply()
            android.widget.Toast.makeText(this, "已清除", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 從鍵盤上按「上傳貼圖」：直接跳到那一頁
        intent.getStringExtra("page")?.let { showPage(it) }
    }

    /** 我的貼圖：上傳、刪除。用的時候在表情鍵盤的 🖼️ 分類 */
    private fun pageMyStickers() {
        note("上傳的圖片會放在表情鍵盤的「🖼️」分類，點一下就送出去（GIF 動圖也可以）。")
        note("聊天 App 支援的話，圖片會直接放進去；不支援的話會先幫你複製好，長按輸入框選「貼上」就行。")
        button("＋ 上傳圖片（可以一次選好幾張）") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_MY_STICKER)
        }
        val names = MyStickers.list(this)
        subTitle(if (names.isEmpty()) "還沒有貼圖" else "目前有 ${names.size} 張")
        // 一排 4 張：縮圖＋刪除
        var row: LinearLayout? = null
        names.forEachIndexed { i, name ->
            if (i % 4 == 0) row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }.also { root.addView(it) }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
            }
            cell.addView(android.widget.ImageView(this).apply {
                setImageBitmap(MyStickers.bitmap(this@SettingsActivity, name))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 12 * dp; setColor(0x14888888) }
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (76 * dp).toInt()))
            cell.addView(Button(this).apply {
                text = "刪除"
                isAllCaps = false
                textSize = 13f
                setOnClickListener {
                    MyStickers.delete(this@SettingsActivity, name)
                    prefs.sp.edit().putLong("my_stickers_ver", System.currentTimeMillis()).apply()
                    showPage("mystickers")
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row!!.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        // 最後一排不滿 4 張：補空位，縮圖才不會被拉大
        val rest = names.size % 4
        if (rest != 0) repeat(4 - rest) { row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
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
