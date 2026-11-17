package io.github.yolanda1027.catpaw

/**
 * 「最近使用」的符號／表情：釘選的照使用者排的順序放最前面，不會被擠掉；其他照最近用過的順序。
 */
object Recents {
    /** 那一頁要放什麼 */
    fun shown(pins: List<String>, recent: List<String>, perPage: Int): List<String> =
        pins.take(perPage) + recent.filter { it !in pins }.take((perPage - pins.size).coerceAtLeast(0))

    /**
     * 長按選單的動作做完之後，釘選和最近使用變成什麼。
     * @return (新的釘選, 新的最近使用)；釘選滿了就回傳 null
     */
    fun apply(action: String, t: String, pins: List<String>, recent: List<String>, max: Int): Pair<List<String>, List<String>>? {
        val p = pins.toMutableList()
        var r = recent
        when (action) {
            "pin" -> {
                if (t in p) return p to r
                if (p.size >= max) return null
                p.add(t)
            }
            // 取消釘選：放回最近使用的最前面（不會一下子不見）
            "unpin" -> { p.remove(t); r = listOf(t) + r.filter { it != t } }
            "left", "right" -> {
                val i = p.indexOf(t)
                val j = i + if (action == "left") -1 else 1
                if (i >= 0 && j in p.indices) p[i] = p[j].also { p[j] = p[i] }
            }
            "remove" -> { p.remove(t); r = r.filter { it != t } }
        }
        return p to r
    }
}
