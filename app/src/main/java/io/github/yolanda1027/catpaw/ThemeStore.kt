package io.github.yolanda1027.catpaw

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 主題庫：每個主題一個檔案（files/themes/<id>.json）。
 *
 * - 內建主題（經典深色、粉紅縫線…）：沒改過就沒有檔案（＝原廠的樣子）；改過才存一份，可以恢復原本的樣子。
 * - 我的主題：自己新增、匯入、另存的，id 是 "u_數字"。
 * 清單上兩種一視同仁：都可以改名、刪除。內建主題刪掉＝藏起來（[HIDDEN]），可以再加回來；
 * 改的名字記在 [NAMES]。這兩個是整支手機的設定，不跟著主題換。
 *
 * 一個主題 = 下面 [LOOK_KEYS] 這些設定的一份快照（底色主題、配色、字體、按鍵高度、按鍵動畫、
 * 按鍵樣式、圖案、功能鍵貼圖）。圖片本身放在 Decals.dir，主題裡只記檔名。
 *
 * 「正在用的主題」的內容就是鍵盤真正在用的設定（Prefs "catpaw"）；
 * 換主題前會先把它存回自己的檔案，再把新主題的快照套上去，所以主題之間不會互相殘留。
 * 主題工作室用另一份工作副本（[WORK]）編輯，按儲存才寫回主題。
 */
object ThemeStore {
    /** 鍵盤真正在用的設定檔 */
    const val LIVE = "catpaw"
    /** 主題工作室的工作副本 */
    const val WORK = "studio_work"
    /** 算圖檔、匯出、匯入時臨時用的 */
    private const val TMP = "theme_tmp"

    /** 屬於「主題」的設定（換主題時一起換） */
    val LOOK_KEYS = listOf(
        "theme", "c_bg", "c_key", "c_text", "c_sub", "c_accent",
        "key_height_dp", "font_zh", "font_zhuyin", "font_en", "cand_font", "key_font",
        "press_effect", "press_effect_color", "custom_effect", "key_styles", "decals", "func_stickers",
    )

    data class Info(val id: String, val name: String, val builtIn: Boolean, val modified: Boolean)

    val BUILT_IN: List<Pair<String, String>>
        get() = listOf("classic" to "經典深色", "stitch" to "粉紅縫線", "catpaw" to "貓爪肉球") +
            MacaronTheme.PALETTES.map { it.id to it.title } + MagicTheme.PALETTES.map { it.id to it.title } +
            listOf("custom" to "自訂配色")

    fun isBuiltIn(id: String) = BUILT_IN.any { it.first == id }

    /** 刪掉的內建主題（存在鍵盤的設定檔，不屬於任何主題） */
    private const val HIDDEN = "hidden_builtin"
    /** 內建主題改過的名字：{id: 名字} */
    private const val NAMES = "builtin_names"

    fun hiddenBuiltIns(live: Prefs): Set<String> = live.sp.getStringSet(HIDDEN, emptySet()).orEmpty()

    private fun builtInNames(live: Prefs): JSONObject =
        try { JSONObject(live.sp.getString(NAMES, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    /** 刪掉的內建主題加回來（原本的樣子） */
    fun restoreBuiltIn(ctx: Context, live: Prefs, id: String) {
        if (!isBuiltIn(id)) return
        file(ctx, id)?.delete()
        live.sp.edit().putStringSet(HIDDEN, hiddenBuiltIns(live) - id).commit()
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "themes").apply { mkdirs() }
    private fun safeId(id: String) = id.matches(Regex("[a-z0-9_]{1,40}"))
    private fun file(ctx: Context, id: String): File? = if (safeId(id)) File(dir(ctx), "$id.json") else null

    // ---------- 快照 ----------

    /** 把這份設定裡屬於主題的部分拿出來 */
    fun snapshot(p: Prefs): JSONObject {
        val all = p.sp.all
        val o = JSONObject()
        for (k in LOOK_KEYS) all[k]?.let { o.put(k, it) }
        return o
    }

    /** 把快照套到這份設定上（屬於主題的設定全部換掉，其他設定不動） */
    fun applyTo(p: Prefs, snap: JSONObject) {
        val e = p.sp.edit()
        for (k in LOOK_KEYS) e.remove(k)
        for (k in LOOK_KEYS) {
            if (!snap.has(k)) continue
            when (val v = snap.get(k)) {
                is Int -> e.putInt(k, v)
                is Long -> e.putInt(k, v.toInt())
                is Boolean -> e.putBoolean(k, v)
                is Number -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
            }
        }
        e.commit()
    }

    /** 原廠的樣子：只有底色主題 */
    fun factory(id: String): JSONObject = JSONObject().put("theme", if (isBuiltIn(id)) id else "classic")

    /** 兩份快照一不一樣（照 LOOK_KEYS 一個一個比） */
    fun same(a: JSONObject, b: JSONObject) = LOOK_KEYS.all { k ->
        a.has(k) == b.has(k) && (!a.has(k) || a.get(k).toString() == b.get(k).toString())
    }

    // ---------- 主題清單 ----------

    fun activeId(live: Prefs): String = live.sp.getString("active_theme", null) ?: live.theme

    private fun stored(ctx: Context, id: String): JSONObject? = try {
        file(ctx, id)?.takeIf { it.exists() }?.readText()?.let { JSONObject(it) }
    } catch (_: Exception) { null }

    /** 這個主題的內容（正在用的主題＝鍵盤現在的設定） */
    fun load(ctx: Context, live: Prefs, id: String): JSONObject =
        if (id == activeId(live)) snapshot(live) else stored(ctx, id)?.optJSONObject("snap") ?: factory(id)

    fun nameOf(ctx: Context, id: String): String {
        BUILT_IN.firstOrNull { it.first == id }?.let { (_, def) ->
            return builtInNames(Prefs(ctx)).optString(id).takeIf { it.isNotBlank() } ?: def
        }
        return stored(ctx, id)?.optString("name")?.takeIf { it.isNotBlank() } ?: "我的主題"
    }

    /** 內建主題原本的名字（「加回內建主題」用） */
    fun builtInTitle(id: String) = BUILT_IN.firstOrNull { it.first == id }?.second ?: id

    fun list(ctx: Context, live: Prefs): List<Info> {
        val active = activeId(live)
        val hidden = hiddenBuiltIns(live)
        val builtIn = BUILT_IN.filter { it.first !in hidden }.map { (id, _) ->
            val name = nameOf(ctx, id)
            val modified = if (id == active) !same(snapshot(live), factory(id)) else file(ctx, id)?.exists() == true
            Info(id, name, true, modified)
        }
        val mine = dir(ctx).listFiles { f -> f.name.startsWith("u_") && f.name.endsWith(".json") }.orEmpty()
            .map { it.name.removeSuffix(".json") }
            .sortedBy { it.removePrefix("u_").toLongOrNull() ?: 0L }
            .map { Info(it, nameOf(ctx, it), false, false) }
        return builtIn + mine
    }

    fun exists(ctx: Context, id: String) =
        if (isBuiltIn(id)) id !in hiddenBuiltIns(Prefs(ctx)) else file(ctx, id)?.exists() == true

    /**
     * 存一個主題（內建主題存成跟原廠一樣的話，就把檔案刪掉＝沒改過）。
     * 「我的主題」另外記著原本的樣子（[origin]），之後改了還能恢復；重存時原本的樣子不會被蓋掉。
     */
    fun write(ctx: Context, id: String, snap: JSONObject, name: String? = null, origin: JSONObject? = null, originKind: String? = null) {
        val f = file(ctx, id) ?: return
        if (isBuiltIn(id) && same(snap, factory(id))) { f.delete(); return }
        val old = stored(ctx, id)
        val o = JSONObject().put("name", name ?: nameOf(ctx, id)).put("snap", snap)
        (origin ?: old?.optJSONObject("origin"))?.let { o.put("origin", it) }
        (originKind ?: old?.optString("originKind")?.takeIf { it.isNotEmpty() })?.let { o.put("originKind", it) }
        f.writeText(o.toString())
    }

    /** 主題工作室改得到的設定（工作室的「恢復」只動這些） */
    val STUDIO_KEYS = listOf("key_styles", "decals", "press_effect", "custom_effect")

    /** 「我的主題」原本的樣子（匯入或建立時）；內建主題是原廠的樣子；舊版建立、沒記錄的是 null */
    fun origin(ctx: Context, id: String): JSONObject? =
        if (isBuiltIn(id)) factory(id) else stored(ctx, id)?.optJSONObject("origin")

    /** 原本的樣子是怎麼來的："import" 匯入、"copy" 新增／複製／另存 */
    fun originKind(ctx: Context, id: String): String? = stored(ctx, id)?.optString("originKind")?.takeIf { it.isNotEmpty() }

    /** 只把某幾個設定換成 from 裡的（from 沒有的就拿掉＝預設） */
    fun restoreParts(p: Prefs, from: JSONObject, keys: List<String>) {
        val part = JSONObject()
        for (k in keys) if (from.has(k)) part.put(k, from.get(k))
        val e = p.sp.edit()
        for (k in keys) e.remove(k)
        for (k in keys) if (part.has(k)) when (val v = part.get(k)) {
            is Int -> e.putInt(k, v)
            is String -> e.putString(k, v)
            is Boolean -> e.putBoolean(k, v)
        }
        e.commit()
    }

    /** 跟原本的樣子比，這幾個設定有沒有改過 */
    fun differsFromOrigin(current: JSONObject, origin: JSONObject, keys: List<String> = LOOK_KEYS) = keys.any { k ->
        current.has(k) != origin.has(k) || (current.has(k) && current.get(k).toString() != origin.get(k).toString())
    }

    /** 「我的主題」整個恢復成原本的樣子（正在用的話鍵盤也跟著換） */
    fun resetToOrigin(ctx: Context, live: Prefs, id: String) {
        val o = origin(ctx, id) ?: return
        if (isBuiltIn(id)) { resetBuiltIn(ctx, live, id); return }
        if (activeId(live) == id) {
            applyTo(live, o)
            live.sp.edit().putLong("stickers_ver", System.currentTimeMillis()).commit()
        }
        write(ctx, id, o)
        cleanupImages(ctx)
    }

    /** 把正在用的主題（鍵盤現在的設定）存回它自己的檔案 */
    fun syncActive(ctx: Context, live: Prefs) {
        val id = activeId(live)
        if (exists(ctx, id)) write(ctx, id, snapshot(live))
    }

    /** 換主題：先把現在的存回去，再套上新的 */
    fun applyTheme(ctx: Context, live: Prefs, id: String) {
        syncActive(ctx, live)
        val snap = stored(ctx, id)?.optJSONObject("snap") ?: factory(id)
        applyTo(live, snap)
        live.sp.edit().putString("active_theme", id).putLong("stickers_ver", System.currentTimeMillis()).commit()
    }

    /** 名字重複的話後面加 (2)、(3)… */
    fun uniqueName(ctx: Context, live: Prefs, base: String): String {
        val names = list(ctx, live).map { it.name }.toSet()
        val b = base.trim().ifEmpty { "我的主題" }.take(30)
        if (b !in names) return b
        var n = 2
        while ("$b ($n)" in names) n++
        return "$b ($n)"
    }

    /**
     * 新增一個「我的主題」，回傳 id。現在的樣子同時記成「原本的樣子」，之後可以恢復。
     * @param originKind "import" 匯入、"copy" 新增／複製／另存
     */
    fun create(ctx: Context, live: Prefs, name: String, snap: JSONObject, originKind: String = "copy"): String {
        var t = System.currentTimeMillis()
        while (file(ctx, "u_$t")?.exists() == true) t++
        val id = "u_$t"
        write(ctx, id, snap, uniqueName(ctx, live, name), origin = snap, originKind = originKind)
        return id
    }

    fun rename(ctx: Context, live: Prefs, id: String, name: String) {
        if (isBuiltIn(id)) {
            val n = name.trim().take(30)
            val o = builtInNames(live)
            if (n.isEmpty() || n == builtInTitle(id)) o.remove(id)
            else o.put(id, if (n == nameOf(ctx, id)) n else uniqueName(ctx, live, n))
            live.sp.edit().putString(NAMES, o.toString()).commit()
            return
        }
        val snap = load(ctx, live, id)
        write(ctx, id, snap, uniqueName(ctx, live, name).takeIf { name.trim() != nameOf(ctx, id) } ?: name.trim())
    }

    /** 能不能刪：至少要留一個主題 */
    fun canDelete(ctx: Context, live: Prefs) = list(ctx, live).size > 1

    /**
     * 刪掉一個主題（正在用的話先換到清單上的第一個）。
     * 內建主題＝藏起來、改過的地方清掉，之後可以加回來（原本的樣子）；我的主題＝檔案刪掉。
     */
    fun delete(ctx: Context, live: Prefs, id: String) {
        val rest = list(ctx, live).filter { it.id != id }
        if (rest.isEmpty()) return
        if (activeId(live) == id) {
            // 清單上的第一個（自己做的、匯入的排在前面）
            val next = (rest.firstOrNull { !it.builtIn } ?: rest.first()).id
            live.sp.edit().putString("active_theme", next).putLong("stickers_ver", System.currentTimeMillis()).commit()
            applyTo(live, stored(ctx, next)?.optJSONObject("snap") ?: factory(next))
        }
        file(ctx, id)?.delete()
        if (isBuiltIn(id)) {
            val o = builtInNames(live).apply { remove(id) }
            live.sp.edit().putStringSet(HIDDEN, hiddenBuiltIns(live) + id).putString(NAMES, o.toString()).commit()
        }
        cleanupImages(ctx)
    }

    /** 內建主題恢復原廠 */
    fun resetBuiltIn(ctx: Context, live: Prefs, id: String) {
        if (!isBuiltIn(id)) return
        file(ctx, id)?.delete()
        if (activeId(live) == id) {
            applyTo(live, factory(id))
            live.sp.edit().putLong("stickers_ver", System.currentTimeMillis()).commit()
        }
        cleanupImages(ctx)
    }

    /** 在一份臨時設定上做事（例如匯出某個不是正在用的主題、算它用到哪些圖） */
    fun <T> withTemp(ctx: Context, snap: JSONObject, block: (Prefs) -> T): T {
        val p = Prefs(ctx, TMP)
        p.sp.edit().clear().commit()
        applyTo(p, snap)
        return block(p)
    }

    // ---------- 主題工作室 ----------

    /** 開工作室：工作副本＝鍵盤現在的所有設定，再換上要編輯的主題 */
    fun openWork(ctx: Context, live: Prefs, id: String): Prefs {
        syncActive(ctx, live)
        val work = Prefs(ctx, WORK)
        val e = work.sp.edit().clear()
        for ((k, v) in live.sp.all) when (v) {
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Boolean -> e.putBoolean(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
            is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
        }
        e.commit()
        applyTo(work, load(ctx, live, id))
        markSaved(work)
        return work
    }

    /** 記下「存過檔的樣子」，用來判斷有沒有沒存的修改 */
    fun markSaved(work: Prefs) {
        work.sp.edit().putString("studio_saved", snapshot(work).toString()).commit()
    }

    fun isDirty(work: Prefs): Boolean {
        val saved = work.sp.getString("studio_saved", null)?.let { JSONObject(it) } ?: return false
        return !same(saved, snapshot(work))
    }

    /** 工作室按儲存：寫回主題；正在用的主題就直接套到鍵盤上 */
    fun saveWork(ctx: Context, live: Prefs, id: String, work: Prefs) {
        val snap = snapshot(work)
        if (id == activeId(live)) {
            applyTo(live, snap)
            live.sp.edit().putLong("stickers_ver", System.currentTimeMillis()).commit()
        }
        write(ctx, id, snap)
        markSaved(work)
    }

    fun closeWork(ctx: Context) {
        Prefs(ctx, WORK).sp.edit().clear().commit()
        cleanupImages(ctx)
    }

    // ---------- 圖檔 ----------

    /** 這份設定用到的圖檔 */
    fun imagesOf(p: Prefs): Set<String> {
        val out = HashSet<String>()
        Decals.load(p).filter { !it.isShape }.forEach { out.add(it.fileName) }
        out += Decals.effectFiles(p)
        out += p.funcStickers.values
        return out
    }

    /** 所有主題（加上鍵盤現在的設定、工作室的工作副本）用到的圖檔 */
    fun allUsedImages(ctx: Context): Set<String> {
        val used = HashSet<String>()
        used += imagesOf(Prefs(ctx, LIVE))
        used += imagesOf(Prefs(ctx, WORK))
        dir(ctx).listFiles { f -> f.name.endsWith(".json") }.orEmpty().forEach { f ->
            val o = try { JSONObject(f.readText()) } catch (_: Exception) { null } ?: return@forEach
            // 現在的樣子和原本的樣子（恢復時要用）都算在用
            for (k in listOf("snap", "origin")) o.optJSONObject(k)?.let { snap -> used += withTemp(ctx, snap) { imagesOf(it) } }
        }
        return used
    }

    /** 刪掉沒有任何主題在用的圖檔 */
    fun cleanupImages(ctx: Context, extra: Collection<String> = emptyList()) {
        val used = allUsedImages(ctx) + extra
        Decals.dir(ctx).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    // ---------- 第一次升到「每個主題一個檔案」 ----------

    /**
     * 以前所有自訂（按鍵樣式、圖案、功能鍵貼圖…）是全部主題共用一份；
     * 照使用者的選擇：這些清掉，內建主題回到原廠的樣子（之後再自己匯入主題檔）。
     */
    fun migrate(ctx: Context, live: Prefs) {
        if (live.sp.getInt("theme_store", 0) < 1) {
            val base = live.theme
            val e = live.sp.edit()
            for (k in LOOK_KEYS) if (k != "theme") e.remove(k)
            e.putString("active_theme", if (isBuiltIn(base)) base else "classic")
            e.putInt("theme_store", 1)
            e.commit()
            // 舊版的功能鍵貼圖（files/sticker_enter.png 這種）
            for (slot in StickerSlot.values()) File(ctx.filesDir, "sticker_${slot.id}.png").delete()
            cleanupImages(ctx)
        }
        if (live.sp.getInt("theme_store", 0) < 2) {
            splitEnglishEverywhere(ctx, live)
            live.sp.edit().putInt("theme_store", 2).commit()
        }
    }

    // ---------- 注音、英文拆成兩頁 ----------

    /**
     * 以前注音和英文是同一頁（共用按鍵樣式、背景、上方列、圖案），現在各改各的。
     * 把英文鍵盤上用得到的設計複製一份到英文頁（en/…），英文鍵盤的樣子才不會變。
     * 英文頁已經有自己的設計就不動。
     */
    fun splitEnglish(p: Prefs) {
        val ks = KeyStyles.load(p)
        val ds = Decals.load(p)
        if (ks.map.keys.any { KeyStyles.pageOf(it) == "en" } || ks.pageTops.containsKey("en") || ds.any { KeyStyles.pageOf(it.target) == "en" }) return
        // 英文鍵盤上有的代號（單顆、整類）＋背景圖
        val enIds = Layouts.ENGLISH.flatten().filter { it.act != Act.GAP }
            .flatMap { listOf(KeyStyles.idOf(it), KeyStyles.groupIdOf(it)) }.toSet() + KeyStyles.KEYBOARD
        fun en(id: String) = KeyStyles.scoped("en", id)
        // 圖案：背景上的、英文鍵盤有的鍵上的，各複製一份（新的 id）
        val idMaps = HashMap<String, MutableMap<String, String>>()
        val copies = ArrayList<Decal>()
        for (d in ds) {
            val target = when {
                d.target == Decal.BG -> Decal.bgFor("en")
                KeyStyles.pageOf(d.target).isEmpty() && d.target in enIds -> en(d.target)
                else -> null
            } ?: continue
            val n = d.copy(id = Decals.newId(), target = target)
            idMaps.getOrPut(target) { HashMap() }[d.id] = n.id
            copies.add(n)
        }
        val m = LinkedHashMap(ks.map)
        for ((id, st) in ks.map) {
            if (KeyStyles.pageOf(id).isNotEmpty() || id !in enIds) continue
            val target = en(id)
            // 按鍵上字和圖案的上下順序：換成英文頁那份圖案的 id
            val map = idMaps[target]
            m[target] = if (map != null && st.order != null) st.copy(order = st.order.map { map[it] ?: it }) else st
        }
        var out = KeyStyles(m, ks.topBar, ks.quick, ks.pageTops, ks.popup, ks.pageBar)
        ks.topBar?.let { out = out.withTopBarFor("en", it) }
        KeyStyles.save(p, out)
        if (copies.isNotEmpty()) Decals.save(p, ds + copies)
        p.sp.edit().commit()
    }

    /** 手機裡所有主題（現在用的、存起來的、原本的樣子）都拆一次 */
    private fun splitEnglishEverywhere(ctx: Context, live: Prefs) {
        splitEnglish(live)
        dir(ctx).listFiles { f -> f.name.endsWith(".json") }.orEmpty().forEach { f ->
            val o = try { JSONObject(f.readText()) } catch (_: Exception) { null } ?: return@forEach
            for (k in listOf("snap", "origin")) {
                val snap = o.optJSONObject(k) ?: continue
                o.put(k, withTemp(ctx, snap) { t -> splitEnglish(t); snapshot(t) })
            }
            f.writeText(o.toString())
        }
    }
}
