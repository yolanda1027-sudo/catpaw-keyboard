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
                Stickers.remove(prefs, slot)
                refreshThumb(slot)
                bumpStickers()
            }
        })
        root.addView(row)
    }

    private fun refreshThumb(slot: StickerSlot) {
        val f = Stickers.file(this, prefs, slot)
        stickerThumbs[slot]?.setImageBitmap(f?.let { android.graphics.BitmapFactory.decodeFile(it.path) })
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
                val bytes = content.toByteArray(Charsets.UTF_8)
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                android.widget.Toast.makeText(this, "主題已匯出（${mb(bytes.size.toLong())}），可以把檔案傳給別人了", android.widget.Toast.LENGTH_LONG).show()
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
        if (Stickers.import(this, prefs, slot, uri) != null) {
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

    /**
     * 調了自訂配色：
     * - 正在用「我的主題」：這個主題的底色改成自訂配色
     * - 正在用其他內建主題：換到內建的「自訂配色」主題（剛調的顏色帶過去）
     */
    private fun switchToCustom() {
        if (prefs.theme == "custom") return
        val active = ThemeStore.activeId(prefs)
        if (!ThemeStore.isBuiltIn(active)) {
            prefs.theme = "custom"
            return
        }
        val keep = listOf(prefs.customBg, prefs.customKey, prefs.customText, prefs.customSub, prefs.customAccent)
        ThemeStore.applyTheme(this, prefs, "custom")
        prefs.customBg = keep[0]; prefs.customKey = keep[1]; prefs.customText = keep[2]
        prefs.customSub = keep[3]; prefs.customAccent = keep[4]
    }

    /** 選色器視窗：大色塊＋彩虹條、新舊顏色對照、色碼欄 */
    private fun showColorPicker(label: String, initial: Int, onPick: (Int) -> Unit) =
        ColorDialog.show(this, label, initial, onPick)

    companion object {
        /** 隱私權政策（商店頁填的也是這個網址；HEAD = repo 的預設分支） */
        const val PRIVACY_URL = "https://github.com/yolanda1027-sudo/catpaw-keyboard/blob/HEAD/docs/privacy-policy.md"
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
        PageInfo("privacy", "🔒 隱私權", "打的字只留在手機裡：存了什麼、怎麼刪", false),
        PageInfo("licenses", "📜 開放原始碼授權", "注音詞庫、繁簡對照表、表情符號、字體的授權", false),
        // 從「外觀」的主題清單跳進來，不列在首頁
        PageInfo("color", "🖌 自訂配色", "自己調鍵盤的 5 個顏色", true, parent = "look", listed = false),
    )

    private fun parentOf(id: String) = pages.firstOrNull { it.id == id }?.parent ?: "home"
    private var scroll: ScrollView? = null

    internal var page = "home"
        private set

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

    /**
     * 返回鍵：在子頁面按返回，回上一層；在首頁按返回才離開。
     *
     * Android 13 以後用 OnBackInvokedCallback（Android 16 起 targetSdk 36 的 App
     * 不會再收到 onBackPressed）。只在子頁面註冊，首頁不註冊，
     * 系統才會播「縮回桌面」的預測返回動畫。Android 12 還是走 onBackPressed。
     */
    private val backCallback: Any? =
        if (android.os.Build.VERSION.SDK_INT >= 33) android.window.OnBackInvokedCallback { showPage(parentOf(page)) } else null
    internal var backRegistered = false
        private set

    private fun updateBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val cb = backCallback as android.window.OnBackInvokedCallback
        val want = page != "home"
        if (want == backRegistered) return
        if (want) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
        else onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb)
        backRegistered = want
    }

    /** 測試用：模擬按一下返回 */
    internal fun pressBack() {
        if (backRegistered) (backCallback as android.window.OnBackInvokedCallback).onBackInvoked() else finish()
    }

    @Deprecated("Android 12 的返回鍵處理；13 以後走 backCallback")
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
        Ui.fitSystemBars(outer)
        setContentView(outer)
        Ui.matchSystemBarIcons(this)
        updateBackCallback()
        actionBar?.title = info?.title?.substringAfter(' ') ?: getString(R.string.app_name)
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
            "licenses" -> pageLicenses()
            "privacy" -> pagePrivacy()
            else -> pageHome()
        }
        updateStatus()
    }

    private fun pageHome() {
        title("🐾 貓爪鍵盤")

        section("開始使用")
        status = note("")
        button("① 到系統設定啟用「${getString(R.string.ime_name)}」") {
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
            "custom" to "自訂長度（推薦：強度 70、12 毫秒）",
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

    private fun mb(bytes: Long) = String.format("%.1f MB", bytes / 1024.0 / 1024.0)

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

    /** 主題檔變成一個新的「我的主題」（不動到任何現有的主題），回傳 id 和警告 */
    internal fun importAsTheme(o: org.json.JSONObject, name: String): Pair<String, List<String>> {
        ThemeStore.syncActive(this, prefs)
        val (snap, warnings) = ThemeStore.withTemp(this, ThemeStore.factory(o.optString("theme"))) { tmp ->
            val w = ThemeIO.apply(this, tmp, o)
            ThemeStore.snapshot(tmp) to w
        }
        return ThemeStore.create(this, prefs, name, snap, originKind = "import") to warnings
    }

    private fun showExportDialog(themeId: String = ThemeStore.activeId(prefs)) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, (8 * dp).toInt(), p, 0)
        }
        val name = EditText(this).apply {
            hint = "主題名稱，例如：奶油黃貓爪"
            isSingleLine = true
            setText(ThemeStore.nameOf(this@SettingsActivity, themeId))
        }
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
                ThemeStore.syncActive(this, prefs)
                pendingExport = if (themeId == ThemeStore.activeId(prefs)) ThemeIO.export(this, prefs, n, parts)
                else ThemeStore.withTemp(this, ThemeStore.load(this, prefs, themeId)) { ThemeIO.export(this, it, n, parts) }
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
        fun fail(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        val size = try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
        } catch (_: Exception) { -1L }
        if (size > ThemeIO.MAX_BYTES) {
            return fail("主題檔太大（${mb(size)}），最多 ${mb(ThemeIO.MAX_BYTES.toLong())}。匯出時可以少勾一些圖片再試試")
        }
        val text = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > ThemeIO.MAX_BYTES) "" else String(bytes, Charsets.UTF_8)
            }
        } catch (_: OutOfMemoryError) {
            return fail("主題檔太大，手機記憶體不夠讀進來。匯出時可以少勾一些圖片再試試")
        } catch (e: Exception) {
            return fail("讀不到這個檔案：${e.message ?: e.javaClass.simpleName}")
        }
        if (text == null) return fail("讀不到這個檔案")
        if (text.isEmpty()) return fail("主題檔太大，最多 ${mb(ThemeIO.MAX_BYTES.toLong())}。匯出時可以少勾一些圖片再試試")
        val o = ThemeIO.parse(text)
        if (o == null) {
            fail("這不是貓爪鍵盤的主題檔")
            return
        }
        val (name, parts) = ThemeIO.describe(o)
        android.app.AlertDialog.Builder(this)
            .setTitle("匯入「$name」？")
            .setMessage("包含：" + parts.joinToString("、") + "\n\n會加進「我的主題」，原本的主題都不會動。")
            .setPositiveButton("加入") { _, _ ->
                val (id, warnings) = importAsTheme(o, name)
                showPage("look")
                val added = ThemeStore.nameOf(this, id)
                val msg = "已加入「$added」🎉" + if (warnings.isEmpty()) "" else "\n\n" + warnings.joinToString("\n")
                android.app.AlertDialog.Builder(this)
                    .setMessage("$msg\n\n要現在套用到鍵盤嗎？")
                    .setPositiveButton("套用") { _, _ ->
                        ThemeStore.applyTheme(this, prefs, id)
                        showPage("look")
                    }
                    .setNegativeButton("先不要", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pageTheme() {
        note("點一下套用。每個主題各自記得自己的樣子（配色、字體、按鍵高度、按鍵圖片、圖案…），換來換去不會互相影響。按「⋯」可以編輯、複製、匯出。")
        val active = ThemeStore.activeId(prefs)
        val all = ThemeStore.list(this, prefs)
        val radios = ArrayList<RadioButton>()
        fun row(t: ThemeStore.Info) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val rb = RadioButton(this).apply {
                text = t.name + if (t.modified) "（改過）" else ""
                tag = t.id
                isChecked = t.id == active
                setOnClickListener {
                    if (t.id != ThemeStore.activeId(prefs)) {
                        ThemeStore.applyTheme(this@SettingsActivity, prefs, t.id)
                        showPage("look")
                    }
                }
            }
            radios.add(rb)
            line.addView(rb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(Button(this).apply {
                text = "⋯"
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setOnClickListener { themeMenu(t) }
            }, LinearLayout.LayoutParams((52 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
            root.addView(line)
        }
        all.filter { it.builtIn }.forEach { row(it) }
        subTitle("我的主題")
        val mine = all.filter { !it.builtIn }
        if (mine.isEmpty()) note("還沒有。匯入主題檔、或在「⋯」選「複製一份」就會出現在這裡。")
        mine.forEach { row(it) }
        button("＋ 用目前的樣子新增一個主題") {
            askName("新增主題", ThemeStore.nameOf(this, active) + " 的副本") { name ->
                ThemeStore.syncActive(this, prefs)
                val id = ThemeStore.create(this, prefs, name, ThemeStore.load(this, prefs, active))
                ThemeStore.applyTheme(this, prefs, id)
                showPage("look")
            }
        }
        root.addView(Button(this).apply {
            text = "🖌 調整自訂配色 ›"
            isAllCaps = false
            setOnClickListener { showPage("color") }
        })
        subTitle("按鍵")
        slider("按鍵高度", 36, 64, prefs.keyHeightDp, " dp（一般手機鍵盤約 45）") { prefs.keyHeightDp = it }
        slider("長按時間", 200, 700, prefs.longPressMs, " 毫秒") { prefs.longPressMs = it }
        root.addView(Switch(this).apply {
            text = "按下時顯示放大的字"
            isChecked = prefs.preview
            setOnCheckedChangeListener { _, b -> prefs.preview = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        root.addView(Switch(this).apply {
            text = "最下面的導覽列也塗上鍵盤底色"
            isChecked = prefs.navBarKeyboardBg
            setOnCheckedChangeListener { _, b -> prefs.navBarKeyboardBg = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        note("關掉（預設）＝導覽列用系統原本的顏色，跟其他鍵盤一樣。")
    }

    /** 主題的「⋯」選單 */
    private fun themeMenu(t: ThemeStore.Info) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add("✏️ 在主題工作室編輯" to {
            ThemeStore.syncActive(this, prefs)
            startActivity(Intent(this, StudioActivity::class.java).putExtra(StudioActivity.EXTRA_THEME, t.id))
        })
        items.add("📄 複製一份" to {
            askName("複製主題", t.name + " 的副本") { name ->
                ThemeStore.syncActive(this, prefs)
                ThemeStore.create(this, prefs, name, ThemeStore.load(this, prefs, t.id))
                showPage("look")
            }
        })
        items.add("📤 匯出成主題檔" to { showExportDialog(t.id) })
        if (!t.builtIn) items.add("✍️ 重新命名" to {
            askName("重新命名", t.name) { name ->
                ThemeStore.rename(this, prefs, t.id, name)
                showPage("look")
            }
        })
        if (t.builtIn && t.modified) items.add("↩️ 恢復原廠" to {
            android.app.AlertDialog.Builder(this)
                .setTitle("「${t.name}」恢復原廠？")
                .setMessage("這個主題改過的地方（按鍵圖片、圖案、配色、字體…）會全部清掉。")
                .setPositiveButton("恢復原廠") { _, _ ->
                    ThemeStore.resetBuiltIn(this, prefs, t.id)
                    showPage("look")
                }
                .setNegativeButton("取消", null)
                .show()
        })
        if (!t.builtIn) {
            val origin = ThemeStore.origin(this, t.id)
            if (origin != null && ThemeStore.differsFromOrigin(ThemeStore.load(this, prefs, t.id), origin)) {
                val kind = if (ThemeStore.originKind(this, t.id) == "import") "匯入時" else "建立時"
                items.add("↩️ 恢復成${kind}的樣子" to {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("「${t.name}」恢復成${kind}的樣子？")
                        .setMessage("之後改過的地方（按鍵、圖案、背景、配色、字體…）都會變回${kind}的樣子。")
                        .setPositiveButton("恢復") { _, _ ->
                            ThemeStore.resetToOrigin(this, prefs, t.id)
                            showPage("look")
                        }
                        .setNegativeButton("取消", null)
                        .show()
                })
            }
        }
        if (!t.builtIn) items.add("🗑 刪除" to {
            android.app.AlertDialog.Builder(this)
                .setTitle("刪除「${t.name}」？")
                .setMessage("刪掉就救不回來了。想留著的話，可以先匯出成主題檔。")
                .setPositiveButton("刪除") { _, _ ->
                    ThemeStore.delete(this, prefs, t.id)
                    showPage("look")
                }
                .setNegativeButton("取消", null)
                .show()
        })
        android.app.AlertDialog.Builder(this)
            .setTitle(t.name)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .show()
    }

    /** 輸入名字的小視窗 */
    private fun askName(title: String, initial: String, onOk: (String) -> Unit) {
        val box = LinearLayout(this).apply {
            val p = (20 * dp).toInt()
            setPadding(p, (8 * dp).toInt(), p, 0)
        }
        val input = EditText(this).apply {
            setText(initial)
            isSingleLine = true
            selectAll()
        }
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("好") { _, _ -> onOk(input.text.toString().trim().ifEmpty { initial }) }
            .setNegativeButton("取消", null)
            .show()
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
            text = "注音直接顯示在輸入框裡（關掉＝顯示在鍵盤上方的注音小框框，選了字才送出）"
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
        root.addView(Switch(this).apply {
            text = "在表情鍵盤顯示「🖼️ 我的貼圖」分類"
            isChecked = prefs.myStickers
            setOnCheckedChangeListener { _, b -> prefs.myStickers = b }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        note("上傳的圖片會放在表情鍵盤的「🖼️」分類，點一下就送出去（GIF 動圖也可以）。關掉的話分類列就不會有它，上傳過的圖片會留著，之後再打開就回來了。")
        note("聊天 App 支援的話，圖片會直接放進去；不支援的話會先幫你複製好，長按輸入框選「貼上」就行。")
        note("在鍵盤上長按貼圖可以看放大版（放開就關掉，不會送出）。下面每張圖的 ◀ ▶ 可以調整排列順序，新上傳的會排在最前面。")
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
            // 往前、往後（排列順序）
            val arrows = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun arrow(label: String, dir: Int, enabled: Boolean) = arrows.addView(Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                minWidth = 0
                minimumWidth = 0
                // 一格只有螢幕的 1/4 寬：三星按鈕預設左右留白很多，箭頭會被擠到只剩一個點
                setPadding(0, paddingTop, 0, paddingBottom)
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.35f
                contentDescription = if (dir < 0) "往前" else "往後"
                setOnClickListener {
                    if (MyStickers.move(this@SettingsActivity, name, dir)) {
                        prefs.sp.edit().putLong("my_stickers_ver", System.currentTimeMillis()).apply()
                        showPage("mystickers")
                    }
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            arrow("◀", -1, i > 0)
            arrow("▶", +1, i < names.size - 1)
            cell.addView(arrows, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
        note("・不打聲調也可以：ㄋㄧㄏㄠ → 你好\n・選字列可以左右滑，按右邊的箭頭可以展開全部選項\n・分段選字：選好的字會先留在注音小框框，全部選完才送出；按刪除鍵會先把選好的字退回成注音\n・長按注音鍵會跳出選單，手指左右滑選大寫、注音符號或小寫／數字，放開就直接打出來\n・長按空白鍵可以切換到其他輸入法\n・按住刪除鍵會連續刪除\n・英文模式點兩下 Shift 會鎖定大寫\n・符號鍵盤可以左右滑換頁")
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

    override fun onPause() {
        super.onPause()
        // 外觀頁上改的東西（字體、按鍵高度、功能鍵貼圖…）算在正在用的主題裡：存回它的檔案
        ThemeStore.syncActive(this, prefs)
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
            else -> "還沒啟用，先按 ①，在清單裡打開「${getString(R.string.ime_name)}」"
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

    // ---------- 開放原始碼授權 ----------

    /** 隱私權：App 裡的簡短版，完整版在 docs/privacy-policy.md（商店頁填的也是那個網址） */
    private fun pagePrivacy() {
        note("🐾 貓爪鍵盤沒有網路權限，不能連上網路，所以你打的字、設定和圖片都只留在這支手機裡，不會傳給任何人（包括我們）。沒有廣告、沒有追蹤、不用註冊。")
        note("手機裡會存的東西：\n・選字記憶（你挑過的字）\n・剪貼簿紀錄（最多 20 筆，沒釘選的 1 小時後自動刪掉）\n・最近用過的符號、表情\n・設定、主題，還有你自己選的圖片和字體")
        note("特別不記的：\n・密碼欄位\n・無痕模式（例如瀏覽器的無痕分頁）\n・標成機密的剪貼簿內容（例如密碼管理 App 複製的）")
        note("Android 的系統備份可能會把設定、主題和選字記憶備份到你自己的 Google 帳號（我們看不到），剪貼簿紀錄不會備份。解除安裝貓爪鍵盤，所有資料都會一起刪掉。")
        button("清除學習紀錄…") { showPage("candidate") }
        button("剪貼簿紀錄開關、清除…") { showPage("clip") }
        button("看完整的隱私權政策（開啟網頁）") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL)))
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, PRIVACY_URL, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pageLicenses() {
        note("貓爪鍵盤用到下面這些開放原始碼的資料和字體，感謝每一位作者。點「看授權全文」可以看完整的授權條款。")
        for (e in OpenSource.ENTRIES) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = (14 * dp).toInt()
                setPadding(p, (12 * dp).toInt(), p, (12 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 14 * dp
                    setColor(0x0F888888)
                    setStroke((1 * dp).toInt(), 0x22888888)
                }
            }
            card.addView(TextView(this).apply { text = e.name; textSize = 16f; setTypeface(typeface, Typeface.BOLD) })
            card.addView(TextView(this).apply { text = e.usage; textSize = 13f; alpha = 0.75f; setPadding(0, (2 * dp).toInt(), 0, (6 * dp).toInt()) })
            card.addView(TextView(this).apply { text = e.license; textSize = 14f })
            card.addView(TextView(this).apply { text = e.copyright; textSize = 12f; alpha = 0.75f })
            card.addView(TextView(this).apply {
                text = e.url
                textSize = 12f
                setTextIsSelectable(true)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            })
            // 授權全文：點了才展開（不然整頁會很長）
            val full = TextView(this).apply {
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                visibility = View.GONE
                setPadding(0, (8 * dp).toInt(), 0, 0)
            }
            card.addView(Button(this).apply {
                text = "看授權全文"
                isAllCaps = false
                setOnClickListener {
                    if (full.visibility == View.VISIBLE) {
                        full.visibility = View.GONE
                        text = "看授權全文"
                    } else {
                        full.text = e.files.joinToString("\n\n────────\n\n") { f -> OpenSource.text(this@SettingsActivity, f)?.let { OpenSource.reflow(it) } ?: "（找不到 $f）" }
                        full.visibility = View.VISIBLE
                        text = "收起來"
                    }
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * dp).toInt() })
            card.addView(full)
            root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            })
        }
        subTitle("其他來源")
        note(OpenSource.OTHER_SOURCES)
    }

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
