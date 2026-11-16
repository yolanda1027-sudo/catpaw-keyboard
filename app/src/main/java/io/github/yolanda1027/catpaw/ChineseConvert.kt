package io.github.yolanda1027.catpaw

import android.content.Context

/**
 * 繁體 → 簡體（打字照樣用繁體詞庫選字，送出去之前才轉成簡體）。
 *
 * 對照表是 OpenCC 的 TSPhrases.txt（詞）和 TSCharacters.txt（單字），Apache-2.0。
 * 轉換方式：先找最長的詞（例如「乾隆」不能轉成「干隆」），找不到再一個字一個字轉。
 * 一個字有好幾種簡體寫法時，用第一個（OpenCC 把最常用的排在最前面）。
 */
object ChineseConvert {
    private var chars: Map<Int, String>? = null
    private var phrases: Map<String, String> = emptyMap()
    private var maxPhrase = 1

    /** 讀對照表（第一次用的時候讀一次，之後都用記住的） */
    @Synchronized
    fun ensure(ctx: Context) {
        if (chars != null) return
        fun read(name: String) = ctx.assets.open("opencc/$name").bufferedReader().use { it.readText() }
        load(read("TSCharacters.txt"), read("TSPhrases.txt"))
    }

    @Synchronized
    fun load(charText: String, phraseText: String) {
        val c = HashMap<Int, String>()
        for ((k, v) in parse(charText)) if (k.codePointCount(0, k.length) == 1) c[k.codePointAt(0)] = v
        val p = HashMap<String, String>()
        for ((k, v) in parse(phraseText)) if (k.length > 1) p[k] = v
        chars = c
        phrases = p
        maxPhrase = p.keys.maxOfOrNull { it.length } ?: 1
    }

    /** 每行「繁<Tab>簡1 簡2…」，# 開頭是註解 */
    private fun parse(text: String): List<Pair<String, String>> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1).trim().substringBefore(' ')
        }
        .filter { it.second.isNotEmpty() }
        .toList()

    /**
     * 選字列用：每個都轉成簡體，轉完一樣的只留第一個（例如「髮」「發」都是「发」）。
     * @return (要顯示的字, 每一個是原本清單的第幾個)
     */
    fun dedupe(list: List<String>, conv: (String) -> String): Pair<List<String>, List<Int>> {
        val seen = LinkedHashMap<String, Int>()
        list.forEachIndexed { i, t -> seen.putIfAbsent(conv(t), i) }
        return seen.keys.toList() to seen.values.toList()
    }

    fun toSimplified(ctx: Context, s: String): String {
        ensure(ctx)
        return toSimplified(s)
    }

    fun toSimplified(s: String): String {
        val c = chars ?: return s
        if (s.isEmpty()) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            var matched = false
            // 先找詞（從最長的開始試）
            var len = minOf(maxPhrase, s.length - i)
            while (len >= 2) {
                val hit = phrases[s.substring(i, i + len)]
                if (hit != null) {
                    out.append(hit)
                    i += len
                    matched = true
                    break
                }
                len--
            }
            if (matched) continue
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            out.append(c[cp] ?: s.substring(i, i + n))
            i += n
        }
        return out.toString()
    }
}
