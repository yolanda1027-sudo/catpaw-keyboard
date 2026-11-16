package io.github.yolanda1027.catpaw

import org.json.JSONObject

/**
 * 單顆按鍵的樣式（主題工作室做的）。沒設定的欄位（null）就照主題原本的樣子。
 *
 * @param radius 圓角程度 0–1（0 = 直角、1 = 最圓）
 */
data class KeyStyle(
    val bg: Int? = null,
    val text: Int? = null,
    val border: Int? = null,
    val radius: Float? = null,
    val hideLabel: Boolean? = null,
    /** 這顆鍵自己的按鍵動畫（null = 跟全部按鍵一樣） */
    val effect: KeyEffect? = null,
) {
    val isEmpty get() = bg == null && text == null && border == null && radius == null && hideLabel == null && effect == null

    /** 自己有設定的用自己的，沒設定的用 base（例如「所有注音鍵」的設定） */
    fun over(base: KeyStyle?): KeyStyle = if (base == null) this else KeyStyle(
        bg ?: base.bg, text ?: base.text, border ?: base.border, radius ?: base.radius, hideLabel ?: base.hideLabel,
        effect ?: base.effect,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        bg?.let { put("bg", hex(it)) }
        text?.let { put("text", hex(it)) }
        border?.let { put("border", hex(it)) }
        radius?.let { put("radius", it.toDouble()) }
        hideLabel?.let { put("hideLabel", it) }
        effect?.let { put("effect", it.toJson()) }
    }

    companion object {
        private fun hex(c: Int) = String.format("#%08X", c)

        fun fromJson(o: JSONObject): KeyStyle {
            fun col(k: String): Int? = o.optString(k).takeIf { it.isNotEmpty() }?.let {
                try { android.graphics.Color.parseColor(it) } catch (_: Exception) { null }
            }
            return KeyStyle(
                bg = col("bg"),
                text = col("text"),
                border = col("border"),
                radius = if (o.has("radius")) o.optDouble("radius").toFloat().coerceIn(0f, 1f) else null,
                hideLabel = if (o.has("hideLabel")) o.optBoolean("hideLabel") else null,
                effect = KeyEffect.fromJson(o.optJSONObject("effect")),
            )
        }
    }
}

/**
 * 所有按鍵樣式。用「代號」對應：
 * - 單顆鍵：注音鍵 zy:ㄅ、其他字鍵 t:q、功能鍵 enter / backspace / space …
 * - 一整類：@zhuyin（所有注音鍵）、@char、@func、@space、@enter
 * 單顆的設定會蓋過整類的設定。
 */
class KeyStyles(val map: Map<String, KeyStyle> = emptyMap()) {

    enum class Group(val id: String, val title: String) {
        ZHUYIN("@zhuyin", "所有注音鍵"),
        CHAR("@char", "所有英文、數字、符號鍵"),
        FUNC("@func", "所有功能鍵"),
        SPACE("@space", "空白鍵"),
        ENTER("@enter", "Enter 鍵"),
    }

    /** 這顆鍵最後要長怎樣（沒有任何設定就是 null） */
    fun resolve(key: Key): KeyStyle? {
        if (map.isEmpty()) return null
        val own = map[idOf(key)]
        val group = map[groupOf(key).id]
        return when {
            own == null -> group
            else -> own.over(group)
        }?.takeIf { !it.isEmpty }
    }

    fun with(id: String, style: KeyStyle?): KeyStyles {
        val m = LinkedHashMap(map)
        if (style == null || style.isEmpty) m.remove(id) else m[id] = style
        return KeyStyles(m)
    }

    fun toJson(): JSONObject = JSONObject().apply { for ((k, v) in map) put(k, v.toJson()) }

    companion object {
        val EMPTY = KeyStyles()

        fun idOf(key: Key): String = when (key.act) {
            Act.ZHUYIN -> "zy:" + key.label
            Act.TEXT -> "t:" + key.label
            else -> key.act.name.lowercase()
        }

        fun groupOf(key: Key): Group = when {
            key.act == Act.ZHUYIN -> Group.ZHUYIN
            key.style == Style.SPACE -> Group.SPACE
            key.style == Style.ENTER -> Group.ENTER
            key.style == Style.FUNC -> Group.FUNC
            else -> Group.CHAR
        }

        /** 給人看的名字：「ㄅ」鍵、刪除鍵… */
        fun nameOf(key: Key): String = when (key.act) {
            Act.BACKSPACE -> "刪除鍵"
            Act.ENTER -> "Enter 鍵"
            Act.SPACE -> "空白鍵"
            Act.SHIFT -> "Shift 鍵"
            Act.TO_SYMBOL -> "?123 鍵"
            Act.TO_EMOJI -> "表情符號鍵"
            Act.TO_MAIN -> "返回鍵"
            Act.TO_NUMPAD -> "數字鍵盤鍵"
            else -> "「${key.label}」鍵"
        }

        fun fromJson(o: JSONObject?): KeyStyles {
            if (o == null) return EMPTY
            val m = LinkedHashMap<String, KeyStyle>()
            for (k in o.keys()) {
                val s = o.optJSONObject(k)?.let { KeyStyle.fromJson(it) } ?: continue
                if (!s.isEmpty) m[k] = s
            }
            return KeyStyles(m)
        }

        fun load(prefs: Prefs): KeyStyles = try {
            fromJson(prefs.sp.getString("key_styles", null)?.let { JSONObject(it) })
        } catch (_: Exception) {
            EMPTY
        }

        fun save(prefs: Prefs, s: KeyStyles) {
            prefs.sp.edit().putString("key_styles", s.toJson().toString()).apply()
        }
    }
}
