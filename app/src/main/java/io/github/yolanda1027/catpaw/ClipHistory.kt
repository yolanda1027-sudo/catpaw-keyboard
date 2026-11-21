package io.github.yolanda1027.catpaw

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 剪貼簿紀錄：最近複製過的文字。
 * - 釘選的會一直留著
 * - 沒釘選的超過留存時間（設定裡選，預設 1 小時，跟 Gboard 一樣）自動刪掉，避免密碼之類的東西一直留著
 * 只存在這支手機裡（App 的私人資料夾），不會上傳。
 */
class ClipHistory(private val file: File, private val expireMs: Long = EXPIRE_MS) {

    data class Item(val text: String, val time: Long, val pinned: Boolean)

    private val items = ArrayList<Item>()

    init {
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(Item(o.getString("t"), o.getLong("at"), o.optBoolean("pin")))
                }
            }
        } catch (_: Exception) {
        }
    }

    /** 釘選的在前（照使用者排的順序），其他照時間新到舊 */
    fun all(now: Long = System.currentTimeMillis()): List<Item> {
        if (items.removeAll { !it.pinned && now - it.time > expireMs }) save()
        return items.filter { it.pinned } + items.filter { !it.pinned }
    }

    fun add(text: String, now: Long = System.currentTimeMillis()) {
        val t = text.take(MAX_LEN)
        if (t.isBlank()) return
        val old = items.firstOrNull { it.text == t }
        if (old != null && items.firstOrNull() == old) return
        // 釘選的再複製一次：留在原本排好的位置
        if (old?.pinned == true) return
        items.remove(old)
        items.add(0, Item(t, now, old?.pinned ?: false))
        // 太多的話，從最舊的、沒釘選的開始刪
        while (items.count { !it.pinned } > MAX_ITEMS) {
            items.removeAt(items.indexOfLast { !it.pinned })
        }
        save()
    }

    fun togglePin(text: String) {
        val it = items.firstOrNull { it.text == text } ?: return
        action(if (it.pinned) "unpin" else "pin", text)
    }

    /**
     * 長按選單的動作：
     * - pin 釘選（排到釘選的最後面）
     * - unpin 取消釘選（放回沒釘選的最前面，不會一下子不見）
     * - up / down 釘選的往前、往後一格（設定的「剪貼簿」頁用 ↑ ↓ 排）
     * - remove 刪除
     */
    fun action(action: String, text: String, now: Long = System.currentTimeMillis()) {
        val i = items.indexOfFirst { it.text == text }
        if (i < 0) return
        val item = items[i]
        when (action) {
            "pin" -> {
                if (item.pinned) return
                items.removeAt(i)
                val lastPinned = items.indexOfLast { it.pinned }
                items.add(lastPinned + 1, item.copy(pinned = true, time = now))
            }
            "unpin" -> {
                if (!item.pinned) return
                items.removeAt(i)
                items.add(0, item.copy(pinned = false, time = now))
            }
            "up", "down" -> {
                if (!item.pinned) return
                val pinnedIdx = items.indices.filter { items[it].pinned }
                val k = pinnedIdx.indexOf(i)
                val other = pinnedIdx.getOrNull(k + if (action == "up") -1 else 1) ?: return
                items[i] = items[other].also { items[other] = items[i] }
            }
            "remove" -> items.removeAt(i)
            else -> return
        }
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        save()
    }

    private fun save() {
        try {
            val arr = JSONArray()
            for (it in items) arr.put(JSONObject().put("t", it.text).put("at", it.time).put("pin", it.pinned))
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXPIRE_MS = 60 * 60 * 1000L
        /** 設定裡可以選的留存時間（小時） */
        val KEEP_HOURS = listOf(1, 8, 12, 24, 72, 168, 336)

        fun keepLabel(h: Int) = if (h < 24) "$h 小時" else "${h / 24} 天"

        /** 照設定的留存時間打開剪貼簿紀錄（鍵盤、設定頁都要用這個，不然會照預設的 1 小時刪掉） */
        fun open(ctx: android.content.Context): ClipHistory =
            ClipHistory(File(ctx.filesDir, "clips.json"), Prefs(ctx).clipKeepHours * 60 * 60 * 1000L)
        const val MAX_ITEMS = 20
        const val MAX_LEN = 5000
    }
}
