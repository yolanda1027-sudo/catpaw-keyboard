package io.github.yolanda1027.catpaw

import android.content.Context
import android.graphics.Typeface

/**
 * 可以選的字體（都附在 App 裡，所有手機看起來都一樣）。
 * 授權都是 SIL Open Font License 1.1，授權全文在 third_party/fonts/。
 * - 粉圓：jf open 粉圓 2.1（justfont），原檔未修改
 * - 芫荽：Iansui，只保留常用字（約 7,900 字），其他字會自動用系統字體顯示
 * - 霞鶩文楷：LXGW WenKai TC，同上
 */
object Fonts {
    val OPTIONS = listOf(
        "system" to "系統預設",
        "huninn" to "粉圓（圓圓的、很可愛）",
        "iansui" to "芫荽（手寫感）",
        "wenkai" to "霞鶩文楷（楷書、書卷感）",
    )

    private val files = mapOf(
        "huninn" to "fonts/jf-openhuninn.ttf",
        "iansui" to "fonts/iansui.ttf",
        "wenkai" to "fonts/lxgw-wenkai-tc.ttf",
    )

    private val cache = HashMap<String, Typeface>()

    fun get(ctx: Context, id: String): Typeface {
        val file = files[id] ?: return Typeface.DEFAULT
        return cache.getOrPut(id) {
            try {
                Typeface.createFromAsset(ctx.assets, file)
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
    }
}
