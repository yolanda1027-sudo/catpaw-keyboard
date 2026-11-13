package io.github.yolanda1027.catpaw

import android.content.Context
import android.content.SharedPreferences

/** 所有可以調的設定都在這裡 */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("catpaw", Context.MODE_PRIVATE)

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
    }

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

    /** 最近用過的顏文字（用 \u0001 分隔） */
    var recentKaomoji: String
        get() = sp.getString("recent_kaomoji", "") ?: ""
        set(v) = sp.edit().putString("recent_kaomoji", v).apply()

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

    /** 上次用的是中文還是英文 */
    var chinese: Boolean
        get() = sp.getBoolean("chinese", true)
        set(v) = sp.edit().putBoolean("chinese", v).apply()
}
