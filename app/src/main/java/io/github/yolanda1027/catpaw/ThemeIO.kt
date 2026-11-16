package io.github.yolanda1027.catpaw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 主題匯入／匯出（像酒館的主題檔）：把外觀相關的設定存成一個 JSON 檔，
 * 傳給別人匯入就能套用一模一樣的鍵盤。
 *
 * 檔案格式：
 * {
 *   "format": "catpaw-keyboard-theme", "version": 1, "name": "我的奶油黃",
 *   "theme": "macaron_butter",
 *   "colors": { "bg": "#FDF0F5", "key": ..., "text": ..., "sub": ..., "accent": ... },
 *   "keyHeight": 45,
 *   "fonts": { "zh": "wenkai", "zhuyin": "huninn", "en": "system" },   // 選擇性
 *   "effect": "spell",                                                  // 選擇性
 *   "stickers": { "enter": "<PNG base64>", ... },                       // 選擇性
 *   "quickBar": { "on": true, "items": [ { "t": "()", "on": true } ] }, // 選擇性
 *   "keyStyles": { "zy:ㄅ": { "bg": "#FFFFE08A", "radius": 0.5 }, "@enter": {...} } // 選擇性
 * }
 * 自己上傳的字體不會放進檔案（太大，也可能有授權問題），對方沒有的話會換成系統字體。
 */
object ThemeIO {
    const val FORMAT = "catpaw-keyboard-theme"
    const val VERSION = 1
    /** 匯入時最多讀多大的檔案 */
    const val MAX_BYTES = 10 * 1024 * 1024

    enum class Part(val title: String) {
        FONTS("字體"),
        EFFECT("按鍵動畫"),
        STICKERS("功能鍵貼圖"),
        QUICK("快捷符號列"),
        KEYS("單顆按鍵樣式（主題工作室）"),
        DECALS("貼上的圖案（主題工作室）"),
    }

    private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    // ---------- 匯出 ----------

    fun export(ctx: Context, prefs: Prefs, name: String, parts: Set<Part>): String {
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("version", VERSION)
        o.put("name", name.ifBlank { "我的貓爪鍵盤" })
        o.put("theme", prefs.theme)
        o.put("colors", JSONObject()
            .put("bg", hex(prefs.customBg)).put("key", hex(prefs.customKey)).put("text", hex(prefs.customText))
            .put("sub", hex(prefs.customSub)).put("accent", hex(prefs.customAccent)))
        o.put("keyHeight", prefs.keyHeightDp)
        if (Part.FONTS in parts) {
            o.put("fonts", JSONObject().put("zh", prefs.zhFont).put("zhuyin", prefs.zhuyinFont).put("en", prefs.enFont))
        }
        if (Part.EFFECT in parts) o.put("effect", prefs.pressEffect)
        if (Part.STICKERS in parts) {
            val st = JSONObject()
            for (slot in StickerSlot.values()) {
                val f = Stickers.file(ctx, slot)
                if (f.exists()) st.put(slot.id, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
            if (st.length() > 0) o.put("stickers", st)
        }
        if (Part.QUICK in parts) {
            val items = JSONArray()
            for (q in prefs.quickEntries()) items.put(JSONObject().put("t", q.text).put("on", q.on))
            o.put("quickBar", JSONObject().put("on", prefs.quickBar).put("items", items))
        }
        if (Part.KEYS in parts) {
            val ks = KeyStyles.load(prefs)
            if (ks.map.isNotEmpty()) o.put("keyStyles", ks.toJson())
        }
        if (Part.DECALS in parts) {
            val list = Decals.load(prefs)
            if (list.isNotEmpty()) o.put("decals", Decals.exportJson(ctx, list))
        }
        return o.toString(2)
    }

    // ---------- 匯入 ----------

    /** 讀檔：不是貓爪鍵盤的主題檔就回傳 null */
    fun parse(text: String): JSONObject? = try {
        JSONObject(text).takeIf { it.optString("format") == FORMAT }
    } catch (_: Exception) {
        null
    }

    /** 給確認視窗看的：主題名稱、包含哪些東西 */
    fun describe(o: JSONObject): Pair<String, List<String>> {
        val parts = mutableListOf("主題與配色")
        if (o.has("fonts")) parts.add("字體")
        if (o.has("effect")) parts.add("按鍵動畫")
        o.optJSONObject("stickers")?.let { if (it.length() > 0) parts.add("功能鍵貼圖 ${it.length()} 張") }
        if (o.has("quickBar")) parts.add("快捷符號列")
        o.optJSONObject("keyStyles")?.let { if (it.length() > 0) parts.add("單顆按鍵樣式 ${it.length()} 組") }
        o.optJSONObject("decals")?.optJSONArray("items")?.let { if (it.length() > 0) parts.add("圖案 ${it.length()} 個") }
        return o.optString("name", "沒有名字的主題") to parts
    }

    private val knownThemes: Set<String> by lazy {
        setOf("classic", "stitch", "catpaw", "custom") +
            MacaronTheme.PALETTES.map { it.id } + MagicTheme.PALETTES.map { it.id }
    }

    /** 套用主題檔，回傳要提醒使用者的事（例如字體沒有） */
    fun apply(ctx: Context, prefs: Prefs, o: JSONObject): List<String> {
        val warnings = ArrayList<String>()

        o.optJSONObject("colors")?.let { c ->
            fun col(k: String, set: (Int) -> Unit) {
                val s = c.optString(k)
                try { if (s.isNotEmpty()) set(Color.parseColor(s)) } catch (_: Exception) {}
            }
            col("bg") { prefs.customBg = it }
            col("key") { prefs.customKey = it }
            col("text") { prefs.customText = it }
            col("sub") { prefs.customSub = it }
            col("accent") { prefs.customAccent = it }
        }
        val theme = o.optString("theme")
        if (theme in knownThemes) {
            prefs.theme = theme
        } else if (theme.isNotEmpty()) {
            prefs.theme = "custom"
            warnings.add("這個主題（$theme）你的版本還沒有，先用檔案裡的配色代替")
        }
        if (o.has("keyHeight")) prefs.keyHeightDp = o.optInt("keyHeight", prefs.keyHeightDp).coerceIn(36, 64)

        o.optJSONObject("fonts")?.let { f ->
            val available = Fonts.options(ctx).map { it.first }.toSet()
            fun font(k: String, label: String, set: (String) -> Unit) {
                val id = f.optString(k)
                if (id.isEmpty()) return
                if (id in available) set(id)
                else {
                    set("system")
                    warnings.add("${label}字體是對方自己上傳的，你這邊沒有，先用系統字體")
                }
            }
            font("zh", "中文") { prefs.zhFont = it }
            font("zhuyin", "注音") { prefs.zhuyinFont = it }
            font("en", "英文") { prefs.enFont = it }
        }

        if (o.has("effect")) {
            val e = o.optString("effect")
            if (PressEffects.ALL.any { it.first == e }) prefs.pressEffect = e
        }

        o.optJSONObject("stickers")?.let { st ->
            // 主題有帶貼圖：先清掉原本的，再換成檔案裡的
            for (slot in StickerSlot.values()) Stickers.remove(ctx, slot)
            for (slot in StickerSlot.values()) {
                val b64 = st.optString(slot.id)
                if (b64.isEmpty()) continue
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    // 重新解碼再存一次：確定真的是圖片，也順便限制大小
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp == null) { warnings.add("${slot.title}的貼圖壞掉了，略過"); continue }
                    val s = minOf(1f, 384f / maxOf(bmp.width, bmp.height))
                    val fixed = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
                    val out = ByteArrayOutputStream()
                    fixed.compress(Bitmap.CompressFormat.PNG, 100, out)
                    Stickers.file(ctx, slot).writeBytes(out.toByteArray())
                } catch (_: Exception) {
                    warnings.add("${slot.title}的貼圖讀不出來，略過")
                }
            }
            prefs.sp.edit().putLong("stickers_ver", System.currentTimeMillis()).apply()
        }

        o.optJSONObject("quickBar")?.let { q ->
            prefs.quickBar = q.optBoolean("on", prefs.quickBar)
            q.optJSONArray("items")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { Prefs.QuickEntry(it.optString("t").replace(" ", ""), it.optBoolean("on", true)) }
                }.filter { it.text.isNotEmpty() }
                if (list.isNotEmpty()) prefs.setQuickEntries(list)
            }
        }
        o.optJSONObject("keyStyles")?.let { KeyStyles.save(prefs, KeyStyles.fromJson(it)) }
        o.optJSONObject("decals")?.let {
            val (list, broken) = Decals.importJson(ctx, it)
            Decals.save(prefs, list)
            Decals.cleanup(ctx, list)
            if (broken > 0) warnings.add("有 $broken 個圖案的圖片壞掉了，略過")
        }
        return warnings
    }
}
