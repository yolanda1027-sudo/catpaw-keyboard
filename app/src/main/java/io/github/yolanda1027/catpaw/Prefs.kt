package io.github.yolanda1027.catpaw

import android.content.Context
import android.content.SharedPreferences

/** 所有可以調的設定都在這裡 */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("catpaw", Context.MODE_PRIVATE)

    /** click / tick / custom / system / off */
    var hapticMode: String
        get() = sp.getString("haptic_mode", "click") ?: "click"
        set(v) = sp.edit().putString("haptic_mode", v).apply()

    /** 震動強度 1–100 */
    var strength: Int
        get() = sp.getInt("strength", 60)
        set(v) = sp.edit().putInt("strength", v).apply()

    /** 自訂模式的震動長度（毫秒） */
    var vibrateMs: Int
        get() = sp.getInt("vibrate_ms", 12)
        set(v) = sp.edit().putInt("vibrate_ms", v).apply()

    /** 每一排按鍵的高度（dp）。45 是照 Google 注音在 S24 Ultra 上量出來的 */
    var keyHeightDp: Int
        get() = sp.getInt("key_height_dp", 45)
        set(v) = sp.edit().putInt("key_height_dp", v).apply()

    /** classic / catpaw */
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

    /** 上次用的是中文還是英文 */
    var chinese: Boolean
        get() = sp.getBoolean("chinese", true)
        set(v) = sp.edit().putBoolean("chinese", v).apply()
}
