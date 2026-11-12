package io.github.yolanda1027.catpaw

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 剪貼簿紀錄：最近複製過的文字。
 * - 釘選的會一直留著
 * - 沒釘選的超過 1 小時自動刪掉（跟 Gboard 一樣，避免密碼之類的東西一直留著）
 * 只存在這支手機裡（App 的私人資料夾），不會上傳。
 */
class ClipHistory(private val file: File) {

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

    /** 釘選的在前，其他照時間新到舊 */
    fun all(now: Long = System.currentTimeMillis()): List<Item> {
        if (items.removeAll { !it.pinned && now - it.time > EXPIRE_MS }) save()
        return items.filter { it.pinned } + items.filter { !it.pinned }
    }

    fun add(text: String, now: Long = System.currentTimeMillis()) {
        val t = text.take(MAX_LEN)
        if (t.isBlank()) return
        val old = items.firstOrNull { it.text == t }
        if (old != null && items.firstOrNull() == old) return
        items.remove(old)
        items.add(0, Item(t, now, old?.pinned ?: false))
        // 太多的話，從最舊的、沒釘選的開始刪
        while (items.count { !it.pinned } > MAX_ITEMS) {
            items.removeAt(items.indexOfLast { !it.pinned })
        }
        save()
    }

    fun togglePin(text: String) {
        val i = items.indexOfFirst { it.text == text }
        if (i < 0) return
        items[i] = items[i].copy(pinned = !items[i].pinned, time = System.currentTimeMillis())
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
        const val MAX_ITEMS = 20
        const val MAX_LEN = 5000
    }
}
