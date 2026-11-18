package io.github.yolanda1027.catpaw

import org.json.JSONObject

/**
 * 按鍵上一個字（主字 ㄒ、小字 v…）自己的調整。
 * @param dx 往右移多少（按鍵寬度的幾分之幾）
 * @param dy 往下移多少（按鍵高度的幾分之幾）
 * @param scale 放大倍率
 * @param color 顏色（null = 照按鍵的文字顏色）
 */
data class LabelStyle(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val color: Int? = null,
    /** 透明度（1 = 不透明、0 = 完全透明） */
    val alpha: Float = 1f,
    /** 刪掉了（不畫、也選不到；在按鍵的面板可以恢復。按鍵照樣能打字） */
    val hidden: Boolean = false,
) {
    val isEmpty get() = dx == 0f && dy == 0f && scale == 1f && rotation == 0f && color == null && alpha == 1f && !hidden

    fun toJson(): JSONObject = JSONObject()
        .put("dx", dx.toDouble()).put("dy", dy.toDouble()).put("scale", scale.toDouble()).put("rotation", rotation.toDouble())
        .apply {
            color?.let { put("color", String.format("#%08X", it)) }
            if (alpha != 1f) put("alpha", alpha.toDouble())
            if (hidden) put("hidden", true)
        }

    companion object {
        fun fromJson(o: JSONObject): LabelStyle = LabelStyle(
            dx = o.optDouble("dx", 0.0).toFloat().coerceIn(-2f, 2f),
            dy = o.optDouble("dy", 0.0).toFloat().coerceIn(-2f, 2f),
            scale = o.optDouble("scale", 1.0).toFloat().coerceIn(0.1f, 6f),
            rotation = o.optDouble("rotation", 0.0).toFloat().coerceIn(-720f, 720f),
            color = o.optString("color").takeIf { it.isNotEmpty() }?.let { try { android.graphics.Color.parseColor(it) } catch (_: Exception) { null } },
            alpha = o.optDouble("alpha", 1.0).toFloat().let { if (it.isNaN()) 1f else it }.coerceIn(0f, 1f),
            hidden = o.optBoolean("hidden", false),
        )
    }
}

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
    /** 按鍵上每個字自己的位置、大小、顏色（"main" 主字、"sub" 小字） */
    val labels: Map<String, LabelStyle>? = null,
    /** 按鍵上所有東西（字和圖案）由下到上的順序：「label:main」「label:sub」或圖案的 id */
    val order: List<String>? = null,
    /** 底圖：鋪在底色上面、字下面，照按鍵的圓角裁掉（「@keyboard」用的話就是整個鍵盤的背景圖） */
    val bgImage: ImageFill? = null,
    /** 外框粗細（dp，沒設定＝2） */
    val borderWidth: Float? = null,
    /** 外框樣式：solid 實線｜dash 虛線｜dot 點點｜stitch 縫線（沒設定＝實線） */
    val borderStyle: String? = null,
    /** 縫線離按鍵邊緣多遠（dp，沒設定＝3） */
    val borderInset: Float? = null,
) {
    val isEmpty get() = bg == null && text == null && border == null && radius == null && hideLabel == null && effect == null &&
        labels.isNullOrEmpty() && order == null && bgImage == null && borderWidth == null && borderStyle == null && borderInset == null

    /** 有改到「長相」（形狀或文字顏色）：預覽泡泡、長按選單也跟著這顆鍵的樣子 */
    val customizedLook get() = isShaped || text != null

    /** 改了底色、圓角、外框或底圖：這顆鍵換成自訂的形狀 */
    val isShaped get() = bg != null || radius != null || border != null || bgImage != null ||
        borderWidth != null || borderStyle != null || borderInset != null

    /** 自己有設定的用自己的，沒設定的用 base（例如「所有注音鍵」的設定） */
    fun over(base: KeyStyle?): KeyStyle = if (base == null) this else KeyStyle(
        bg ?: base.bg, text ?: base.text, border ?: base.border, radius ?: base.radius, hideLabel ?: base.hideLabel,
        effect ?: base.effect,
        labels ?: base.labels,
        order ?: base.order,
        bgImage ?: base.bgImage,
        borderWidth ?: base.borderWidth,
        borderStyle ?: base.borderStyle,
        borderInset ?: base.borderInset,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        bg?.let { put("bg", hex(it)) }
        text?.let { put("text", hex(it)) }
        border?.let { put("border", hex(it)) }
        radius?.let { put("radius", it.toDouble()) }
        hideLabel?.let { put("hideLabel", it) }
        effect?.let { put("effect", it.toJson()) }
        labels?.takeIf { it.isNotEmpty() }?.let { m -> put("labels", JSONObject().apply { for ((k, v) in m) put(k, v.toJson()) }) }
        order?.let { put("order", org.json.JSONArray(it)) }
        bgImage?.let { put("bgImage", it.toJson()) }
        borderWidth?.let { put("borderWidth", it.toDouble()) }
        borderStyle?.let { put("borderStyle", it) }
        borderInset?.let { put("borderInset", it.toDouble()) }
    }

    companion object {
        private fun hex(c: Int) = String.format("#%08X", c)

        /** 按鍵外框的樣式（跟圖案的外框同一套） */
        val BORDER_STYLES = listOf("solid" to "實線", "dash" to "虛線", "dot" to "點點", "stitch" to "縫線")
        const val DEFAULT_BORDER_WIDTH = 2f
        const val DEFAULT_BORDER_INSET = 3f

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
                labels = o.optJSONObject("labels")?.let { lo ->
                    lo.keys().asSequence().mapNotNull { k -> lo.optJSONObject(k)?.let { k to LabelStyle.fromJson(it) } }
                        .filter { !it.second.isEmpty }.toMap().takeIf { it.isNotEmpty() }
                },
                order = o.optJSONArray("order")?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() } },
                bgImage = ImageFill.fromJson(o.optJSONObject("bgImage")),
                borderWidth = if (o.has("borderWidth")) o.optDouble("borderWidth").toFloat().takeIf { !it.isNaN() }?.coerceIn(0.5f, 8f) else null,
                borderStyle = o.optString("borderStyle").takeIf { s -> BORDER_STYLES.any { it.first == s } },
                borderInset = if (o.has("borderInset")) o.optDouble("borderInset").toFloat().takeIf { !it.isNaN() }?.coerceIn(0f, 12f) else null,
            )
        }
    }
}

/**
 * 上方列（中／En、選字列、符號分類列）的顏色。沒設定的（null）照主題原本的。
 * @param bg 整列的底色（透明＝看得到主題背景，鍵盤背景圖片也會鋪上來）
 * @param tab 選中的那一塊（「中」、第一個候選字）的底色（透明＝不畫）
 * @param text 選中的字、候選字
 * @param textDim 沒選中的字（例如「En」）
 * @param icon 剪貼簿、▽、展開箭頭
 */
data class TopBarStyle(
    val bg: Int? = null,
    val tab: Int? = null,
    val text: Int? = null,
    val textDim: Int? = null,
    val icon: Int? = null,
    /** 選中那塊的外框：顏色、粗細、樣式、縫線離邊距離（跟按鍵的外框同一套） */
    val tabBorder: Int? = null,
    val tabBorderWidth: Float? = null,
    val tabBorderStyle: String? = null,
    val tabBorderInset: Float? = null,
) {
    val isEmpty get() = bg == null && tab == null && text == null && textDim == null && icon == null &&
        tabBorder == null && tabBorderWidth == null && tabBorderStyle == null && tabBorderInset == null

    fun toJson(): JSONObject = JSONObject().apply {
        fun hex(c: Int) = String.format("#%08X", c)
        bg?.let { put("bg", hex(it)) }
        tab?.let { put("tab", hex(it)) }
        text?.let { put("text", hex(it)) }
        textDim?.let { put("textDim", hex(it)) }
        icon?.let { put("icon", hex(it)) }
        tabBorder?.let { put("tabBorder", hex(it)) }
        tabBorderWidth?.let { put("tabBorderWidth", it.toDouble()) }
        tabBorderStyle?.let { put("tabBorderStyle", it) }
        tabBorderInset?.let { put("tabBorderInset", it.toDouble()) }
    }

    companion object {
        fun fromJson(o: JSONObject?): TopBarStyle? {
            if (o == null) return null
            fun col(k: String): Int? = o.optString(k).takeIf { it.isNotEmpty() }?.let {
                try { android.graphics.Color.parseColor(it) } catch (_: Exception) { null }
            }
            fun num(k: String, lo: Float, hi: Float) = if (o.has(k)) o.optDouble(k).toFloat().takeIf { !it.isNaN() }?.coerceIn(lo, hi) else null
            return TopBarStyle(
                col("bg"), col("tab"), col("text"), col("textDim"), col("icon"),
                tabBorder = col("tabBorder"),
                tabBorderWidth = num("tabBorderWidth", 0.5f, 8f),
                tabBorderStyle = o.optString("tabBorderStyle").takeIf { s -> KeyStyle.BORDER_STYLES.any { it.first == s } },
                tabBorderInset = num("tabBorderInset", 0f, 12f),
            ).takeIf { !it.isEmpty }
        }
    }
}

/**
 * 快捷符號列的樣子。沒設定的（null）照主題原本的（按鈕跟功能鍵長得一樣、整列沒有底色）。
 * @param strip 整列的底色（透明＝沒有）
 * @param bg 按鈕底色（透明＝沒有按鈕底，只剩字）
 * @param radius 按鈕圓角 0–1
 */
data class QuickBarStyle(
    val strip: Int? = null,
    val bg: Int? = null,
    val text: Int? = null,
    val border: Int? = null,
    val radius: Float? = null,
    /** 外框粗細、樣式、縫線離邊距離（跟按鍵的外框同一套） */
    val borderWidth: Float? = null,
    val borderStyle: String? = null,
    val borderInset: Float? = null,
    /** 按鈕圖片（九宮格）：有的話按鈕就畫這張，底色、外框、圓角交給圖片 */
    val frame: NineFrame? = null,
) {
    val isEmpty get() = strip == null && bg == null && text == null && border == null && radius == null &&
        borderWidth == null && borderStyle == null && borderInset == null && frame == null
    /** 按鈕換成自訂的形狀（改了底色、外框或圓角） */
    val isShaped get() = bg != null || border != null || radius != null || borderWidth != null || borderStyle != null || borderInset != null

    /** 畫按鈕用的樣式（按鍵的畫法共用） */
    fun asKeyStyle() = KeyStyle(radius = radius, borderWidth = borderWidth, borderStyle = borderStyle, borderInset = borderInset)

    fun toJson(): JSONObject = JSONObject().apply {
        fun hex(c: Int) = String.format("#%08X", c)
        strip?.let { put("strip", hex(it)) }
        bg?.let { put("bg", hex(it)) }
        text?.let { put("text", hex(it)) }
        border?.let { put("border", hex(it)) }
        radius?.let { put("radius", it.toDouble()) }
        borderWidth?.let { put("borderWidth", it.toDouble()) }
        borderStyle?.let { put("borderStyle", it) }
        borderInset?.let { put("borderInset", it.toDouble()) }
        frame?.let { put("frame", it.toJson()) }
    }

    companion object {
        fun fromJson(o: JSONObject?): QuickBarStyle? {
            if (o == null) return null
            fun col(k: String): Int? = o.optString(k).takeIf { it.isNotEmpty() }?.let {
                try { android.graphics.Color.parseColor(it) } catch (_: Exception) { null }
            }
            val r = if (o.has("radius")) o.optDouble("radius").toFloat().takeIf { !it.isNaN() }?.coerceIn(0f, 1f) else null
            fun num(k: String, lo: Float, hi: Float) = if (o.has(k)) o.optDouble(k).toFloat().takeIf { !it.isNaN() }?.coerceIn(lo, hi) else null
            return QuickBarStyle(
                col("strip"), col("bg"), col("text"), col("border"), r,
                borderWidth = num("borderWidth", 0.5f, 8f),
                borderStyle = o.optString("borderStyle").takeIf { s -> KeyStyle.BORDER_STYLES.any { it.first == s } },
                borderInset = num("borderInset", 0f, 12f),
                frame = NineFrame.fromJson(o.optJSONObject("frame")),
            ).takeIf { !it.isEmpty }
        }
    }
}

/**
 * 所有按鍵樣式。用「代號」對應：
 * - 單顆鍵：注音鍵 zy:ㄅ、其他字鍵 t:q、功能鍵 enter / backspace / space …
 * - 一整類：@zhuyin（所有注音鍵）、@char、@func、@space、@enter
 * 單顆的設定會蓋過整類的設定。
 */
class KeyStyles(val map: Map<String, KeyStyle> = emptyMap(), val topBar: TopBarStyle? = null, val quick: QuickBarStyle? = null) {

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
        val st = when {
            own == null -> group
            else -> own.over(group)
        }
        // 剪貼簿那幾條：借一般字鍵的外觀（底圖、顏色、外框），但字的位置、隱藏、透明不借——
        // 位置是照按鍵寬度算的，剪貼簿條是字鍵的 10 倍寬，字會跑出框外或整個不見
        val fixed = if (key.act == Act.CLIP && st != null) st.copy(
            hideLabel = null, labels = null, order = null,
            text = st.text?.takeIf { android.graphics.Color.alpha(it) > 0 },
        ) else st
        return fixed?.takeIf { !it.isEmpty }
    }

    fun with(id: String, style: KeyStyle?): KeyStyles {
        val m = LinkedHashMap(map)
        if (style == null || style.isEmpty) m.remove(id) else m[id] = style
        return KeyStyles(m, topBar, quick)
    }

    fun withTopBar(t: TopBarStyle?): KeyStyles = KeyStyles(map, t?.takeIf { !it.isEmpty }, quick)
    fun withQuick(q: QuickBarStyle?): KeyStyles = KeyStyles(map, topBar, q?.takeIf { !it.isEmpty })
    /** 只換按鍵的部分（上方列、快捷列的顏色留著） */
    fun withMap(m: Map<String, KeyStyle>): KeyStyles = KeyStyles(m, topBar, quick)

    /** 一樣的設定（上一步用來比對有沒有改） */
    override fun equals(other: Any?) = other is KeyStyles && other.map == map && other.topBar == topBar && other.quick == quick
    override fun hashCode() = (map.hashCode() * 31 + (topBar?.hashCode() ?: 0)) * 31 + (quick?.hashCode() ?: 0)

    fun toJson(): JSONObject = JSONObject().apply {
        for ((k, v) in map) put(k, v.toJson())
        topBar?.let { put(TOP_BAR, it.toJson()) }
        quick?.let { put(QUICK, it.toJson()) }
    }

    /** 整個鍵盤的背景圖（不是按鍵；跟按鍵樣式存在一起，上一步、匯出、恢復預設都會一起處理） */
    val keyboardImage: ImageFill? get() = map[KEYBOARD]?.bgImage

    /** 真的是按鍵的設定（不算鍵盤背景圖） */
    val keyEntries: Map<String, KeyStyle> get() = map.filterKeys { it != KEYBOARD }

    /** 所有用到的圖檔名（按鍵底圖、鍵盤背景圖、快捷列按鈕圖片） */
    val imageFiles: Set<String> get() = map.values.mapNotNull { it.bgImage?.img }.toSet() + listOfNotNull(quick?.frame?.img)

    companion object {
        val EMPTY = KeyStyles()
        /** 鍵盤背景圖的代號 */
        const val KEYBOARD = "@keyboard"
        /** 上方列顏色（存檔裡的代號） */
        const val TOP_BAR = "@topbar"
        /** 快捷符號列的顏色（存檔裡的代號） */
        const val QUICK = "@quick"

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
                if (k == TOP_BAR || k == QUICK) continue
                val s = o.optJSONObject(k)?.let { KeyStyle.fromJson(it) } ?: continue
                if (!s.isEmpty) m[k] = s
            }
            return KeyStyles(m, TopBarStyle.fromJson(o.optJSONObject(TOP_BAR)), QuickBarStyle.fromJson(o.optJSONObject(QUICK)))
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
