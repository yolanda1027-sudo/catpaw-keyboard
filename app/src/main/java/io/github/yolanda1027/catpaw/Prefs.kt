package io.github.yolanda1027.catpaw

import android.content.Context
import android.content.SharedPreferences

/**
 * 所有可以調的設定都在這裡。
 * @param file 存在哪個設定檔：平常是 "catpaw"（鍵盤真的在用的）；
 *   主題工作室編輯時用自己的工作副本（ThemeStore.WORK），按儲存才寫回主題。
 */
class Prefs(context: Context, val file: String = ThemeStore.LIVE) {
    private val app: Context = context.applicationContext
    val sp: SharedPreferences = app.getSharedPreferences(file, Context.MODE_PRIVATE)

    /** 功能鍵貼圖：哪個功能鍵用哪張圖（圖檔放在 Decals.dir，跟著主題走） */
    var funcStickers: Map<String, String>
        get() = try {
            val o = org.json.JSONObject(sp.getString("func_stickers", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.optString(it) }.filterValues { Decals.safeName(it) }
        } catch (_: Exception) { emptyMap() }
        set(v) = sp.edit().putString("func_stickers", org.json.JSONObject(v).toString()).apply()

    /** click / tick / custom / system / off */
    var hapticMode: String
        get() = sp.getString("haptic_mode", "custom") ?: "custom"
        set(v) = sp.edit().putString("haptic_mode", v).apply()

    /** 震動強度 1–100 */
    var strength: Int
        get() = sp.getInt("strength", 70)
        set(v) = sp.edit().putInt("strength", v).apply()

    /** 自訂模式的震動長度（毫秒） */
    var vibrateMs: Int
        get() = sp.getInt("vibrate_ms", 12)
        set(v) = sp.edit().putInt("vibrate_ms", v).apply()

    /** 每一排按鍵的高度（dp）。45 是照 Google 注音在 S24 Ultra 上量出來的 */
    var keyHeightDp: Int
        get() = sp.getInt("key_height_dp", 45)
        set(v) = sp.edit().putInt("key_height_dp", v).apply()

    /** classic / catpaw / stitch */
    var theme: String
        get() = sp.getString("theme", "classic") ?: "classic"
        set(v) = sp.edit().putString("theme", v).apply()

    var longPressMs: Int
        get() = sp.getInt("long_press_ms", 350)
        set(v) = sp.edit().putInt("long_press_ms", v).apply()

    /** 按下時跳出放大的字 */
    var preview: Boolean
        get() = sp.getBoolean("preview", true)
        set(v) = sp.edit().putBoolean("preview", v).apply()

    /**
     * 第 2 版重新照原版量了字的比例，按鍵高度回到 45dp 才會跟原版一樣高。
     * 舊版為了「按鍵看起來太小」而調高的設定，在這裡重設一次。
     */
    fun migrate() {
        if (sp.getInt("layout_version", 1) < 2) {
            sp.edit().putInt("key_height_dp", 45).putInt("layout_version", 2).apply()
        }
        // 測試（Robolectric）每次都是全新的設定，不用搬；搬家本身另外測（ThemeStoreTest）
        if (file == ThemeStore.LIVE && android.os.Build.FINGERPRINT != "robolectric") ThemeStore.migrate(app, this)
    }

    /** 中文打出來用簡體（選字還是用繁體詞庫，送出前才轉） */
    var simplified: Boolean
        get() = sp.getBoolean("simplified", false)
        set(v) = sp.edit().putBoolean("simplified", v).apply()

    /** 鍵盤上方要不要顯示「簡」切換鍵 */
    var showScToggle: Boolean
        get() = sp.getBoolean("show_sc_toggle", true)
        set(v) = sp.edit().putBoolean("show_sc_toggle", v).apply()

    /**
     * 主題圖片的存法：false（預設）＝ PNG，原圖畫質；true ＝ 省空間，WebP（大張的品質 95、小的無損）。
     * 影響之後上傳的圖和匯出的主題檔，已經存的圖不會動。
     */
    var imageCompact: Boolean
        get() = sp.getBoolean("image_compact", false)
        set(v) = sp.edit().putBoolean("image_compact", v).apply()

    /** 英文模式打字時，選字列顯示建議字（只建議，不自動改） */
    var enSuggest: Boolean
        get() = sp.getBoolean("en_suggest", true)
        set(v) = sp.edit().putBoolean("en_suggest", v).apply()

    /** 自訂按鍵動畫的設定（按鍵動畫選「custom」時用） */
    var customEffect: CustomEffect
        get() = try {
            CustomEffect.fromJson(sp.getString("custom_effect", null)?.let { org.json.JSONObject(it) }) ?: CustomEffect()
        } catch (_: Exception) { CustomEffect() }
        set(v) = sp.edit().putString("custom_effect", v.toJson().toString()).apply()

    /**
     * false（預設）＝像 Google 注音：注音顯示在鍵盤上方的小框框，選了字才送進輸入框
     * true ＝ 注音直接顯示在輸入框裡（帶底線），即時轉成中文
     */
    var inlineComposing: Boolean
        get() = sp.getBoolean("inline_composing", false)
        set(v) = sp.edit().putBoolean("inline_composing", v).apply()

    /** 最近用過的符號（用 \u0001 分隔） */
    var recentSymbols: String
        get() = sp.getString("recent_symbols", "") ?: ""
        set(v) = sp.edit().putString("recent_symbols", v).apply()

    /** 釘選的符號、表情（用 \u0001 分隔，照使用者排的順序）：放在「最近使用」最前面，不會被擠掉 */
    var pinnedSymbols: String
        get() = sp.getString("pinned_symbols", "") ?: ""
        set(v) = sp.edit().putString("pinned_symbols", v).apply()

    var pinnedEmoji: String
        get() = sp.getString("pinned_emoji", "") ?: ""
        set(v) = sp.edit().putString("pinned_emoji", v).apply()

    /** 最近用過的表情符號（用 \u0001 分隔） */
    var recentEmoji: String
        get() = sp.getString("recent_emoji", "") ?: ""
        set(v) = sp.edit().putString("recent_emoji", v).apply()

    /** 最近用過的顏文字（用 \u0001 分隔） */
    var recentKaomoji: String
        get() = sp.getString("recent_kaomoji", "") ?: ""
        set(v) = sp.edit().putString("recent_kaomoji", v).apply()

    /**
     * 最下面導覽列（三顆按鈕／手勢條）那一條要不要塗鍵盤的底色。
     * 預設不塗：塗系統原本的導覽列顏色，跟其他鍵盤一樣。
     */
    var navBarKeyboardBg: Boolean
        get() = sp.getBoolean("nav_bar_keyboard_bg", false)
        set(v) = sp.edit().putBoolean("nav_bar_keyboard_bg", v).apply()

    /**
     * 鍵盤最下面多留的空間（dp）。有些手機把手勢提示條關掉後，系統還是會在鍵盤底下畫
     * 「切換輸入法」「收起鍵盤」兩顆按鈕，卻沒告訴鍵盤要讓位：這時手動調高。
     */
    var bottomPadDp: Int
        get() = sp.getInt("bottom_pad_dp", 0)
        set(v) = sp.edit().putInt("bottom_pad_dp", v).apply()

    /** 記錄剪貼簿（點鍵盤上方的剪貼簿按鈕可以看到、貼上） */
    var clipboardHistory: Boolean
        get() = sp.getBoolean("clipboard_history", true)
        set(v) = sp.edit().putBoolean("clipboard_history", v).apply()

    // ---------- 第三階段：自訂配色、按鍵動畫 ----------

    private fun color(key: String, def: String) = sp.getInt(key, android.graphics.Color.parseColor(def))
    private fun setColor(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    var customBg: Int get() = color("c_bg", "#FDF0F5"); set(v) = setColor("c_bg", v)
    var customKey: Int get() = color("c_key", "#FFFFFF"); set(v) = setColor("c_key", v)
    var customText: Int get() = color("c_text", "#5B4B57"); set(v) = setColor("c_text", v)
    var customSub: Int get() = color("c_sub", "#B59AAB"); set(v) = setColor("c_sub", v)
    var customAccent: Int get() = color("c_accent", "#F49AB7"); set(v) = setColor("c_accent", v)

    /** none / bounce / heart / paw / star */
    var pressEffect: String
        get() = sp.getString("press_effect", "none") ?: "none"
        set(v) = sp.edit().putString("press_effect", v).apply()

    /** 按鍵上的字體：system / huninn / iansui / wenkai */
    var keyFont: String
        get() = sp.getString("key_font", "system") ?: "system"
        set(v) = sp.edit().putString("key_font", v).apply()

    /**
     * 字體分三組：中文、注音、英文和符號。
     * 舊版是「按鍵字體／選字字體」，沒設過新的就沿用：中文＝選字字體，注音、英文＝按鍵字體。
     */
    var zhFont: String
        get() = sp.getString("font_zh", null) ?: candFont
        set(v) = sp.edit().putString("font_zh", v).apply()

    var zhuyinFont: String
        get() = sp.getString("font_zhuyin", null) ?: keyFont
        set(v) = sp.edit().putString("font_zhuyin", v).apply()

    var enFont: String
        get() = sp.getString("font_en", null) ?: keyFont
        set(v) = sp.edit().putString("font_en", v).apply()

    /** 選字列、注音小框框的字體（舊版） */
    var candFont: String
        get() = sp.getString("cand_font", "system") ?: "system"
        set(v) = sp.edit().putString("cand_font", v).apply()

    /** 表情鍵盤要不要顯示「🖼️ 我的貼圖」分類 */
    var myStickers: Boolean
        get() = sp.getBoolean("my_stickers", true)
        set(v) = sp.edit().putBoolean("my_stickers", v).apply()

    /** 快捷符號列：開關、內容（用空白分開） */
    var quickBar: Boolean
        get() = sp.getBoolean("quick_bar", false)
        set(v) = sp.edit().putBoolean("quick_bar", v).apply()

    var quickItems: String
        get() = sp.getString("quick_items", DEFAULT_QUICK) ?: DEFAULT_QUICK
        set(v) = sp.edit().putString("quick_items", v).apply()

    /** 快捷符號列的一個按鈕：內容、要不要顯示 */
    data class QuickEntry(val text: String, val on: Boolean)

    /** 每個按鈕分開存（JSON）；舊版是一串用空白分開的文字，第一次讀的時候轉過來 */
    fun quickEntries(): List<QuickEntry> {
        val raw = sp.getString("quick_config", null)
            ?: return quickItems.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { QuickEntry(it, true) }
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                QuickEntry(o.optString("t"), o.optBoolean("on", true))
            }
        } catch (_: Exception) {
            defaultQuickEntries()
        }
    }

    fun setQuickEntries(list: List<QuickEntry>) {
        val arr = org.json.JSONArray()
        for (q in list.take(MAX_QUICK)) arr.put(org.json.JSONObject().put("t", q.text).put("on", q.on))
        sp.edit().putString("quick_config", arr.toString()).apply()
    }

    /** 鍵盤上實際顯示的按鈕（有勾選、不是空白的） */
    fun quickList(): List<String> = quickEntries().filter { it.on && it.text.isNotBlank() }.map { it.text.trim() }.take(MAX_QUICK)

    companion object {
        /** ⇤ 跳到最前、⇥ 跳到最後、◀ ▶ 游標左右移一格；其他是要打出的符號 */
        const val DEFAULT_QUICK = "⇤ () 「」 『』 【】 “” ⇥"
        const val MAX_QUICK = 7

        fun defaultQuickEntries() = DEFAULT_QUICK.split(" ").map { QuickEntry(it, true) }
    }

    /** 上次用的是中文還是英文 */
    var chinese: Boolean
        get() = sp.getBoolean("chinese", true)
        set(v) = sp.edit().putBoolean("chinese", v).apply()
}
