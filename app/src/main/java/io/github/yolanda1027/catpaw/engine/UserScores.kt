package io.github.yolanda1027.catpaw.engine

import java.io.File

/**
 * 記憶選字。存在手機裡的一個小檔案，不會上傳到任何地方。
 *
 * 1. 選字記憶：你「自己從選字列點選」的字詞，下次會排到第一個
 *    （直接按空白／Enter 接受預設的不算，免得常用字一直被加分、你挑的反而不突出）
 * 2. 自訂詞：同一次打字裡分段選的字（例如先選「妳」再選「好」），
 *    會被記成一個新詞「妳好」，之後整句轉換也會用到
 *
 * 檔案格式（一行一筆）：
 *   B<TAB>詞<TAB>讀音<TAB>加分
 *   P<TAB>詞<TAB>讀音
 * 舊版的「詞<TAB>讀音<TAB>加分」也讀得懂。
 */
class UserScores(private val file: File?) {
    private val bonus = HashMap<String, Float>()
    private val phrases = LinkedHashMap<String, Pair<String, String>>() // key → (詞, 讀音)

    /**
     * 暫停學習：輸入框要求「不要記住打的字」時（例如瀏覽器的無痕模式）打開。
     * 已經學過的字照樣會用來排選字，只是不再記新的。
     */
    var paused = false

    init {
        file?.takeIf { it.exists() }?.forEachLine { line ->
            val f = line.split('\t')
            when {
                f.size == 4 && f[0] == "B" -> f[3].toFloatOrNull()?.let { bonus[f[1] + "\t" + f[2]] = it }
                f.size == 3 && f[0] == "P" -> phrases[f[1] + "\t" + f[2]] = f[1] to f[2]
                f.size == 3 -> f[2].toFloatOrNull()?.let { bonus[f[0] + "\t" + f[1]] = minOf(it, 1f) } // 舊格式
            }
        }
    }

    private fun key(e: Entry) = e.phrase + "\t" + e.reading

    fun bonus(e: Entry): Float = bonus[key(e)] ?: 0f

    /**
     * 你從選字列挑了 e：讓它的分數比目前第一名高一點點。
     * @param topScore 同一組讀音裡，目前第一名的分數（已含加分）
     */
    fun pick(e: Entry, topScore: Float) {
        if (paused) return
        val k = key(e)
        val cur = bonus[k] ?: 0f
        val needed = topScore - e.score + MARGIN
        bonus[k] = maxOf(cur, needed).coerceAtMost(MAX_BONUS)
        save()
    }

    /** 記住一個自訂詞，回傳 true 代表是新的 */
    fun addPhrase(phrase: String, reading: String): Boolean {
        if (paused) return false
        val k = "$phrase\t$reading"
        if (phrases.containsKey(k)) return false
        phrases[k] = phrase to reading
        while (phrases.size > MAX_PHRASES) phrases.remove(phrases.keys.first())
        save()
        return true
    }

    fun userPhrases(): List<Pair<String, String>> = phrases.values.toList()

    /** 這個詞有沒有被記過（選過、或是自己組出來的詞） */
    fun remembers(e: Entry): Boolean = (bonus[key(e)] ?: 0f) > 0f || phrases.containsKey(key(e))

    /** 記過的自訂詞裡有沒有這幾個字（不管讀音） */
    fun hasPhraseText(text: String): Boolean = phrases.values.any { it.first == text }

    /**
     * 忘掉：這幾個詞的選字記憶、自訂詞，還有跟 text 一樣的自訂詞（不管讀音）。
     * 無痕模式也可以刪。回傳 true 代表真的有刪到東西。
     */
    fun forget(entries: List<Entry>, text: String): Boolean {
        var changed = false
        // 要忘掉的詞（詞, 讀音）：選項本身的詞，加上跟選項同字的自訂詞
        val targets = entries.map { it.phrase to it.reading }.toMutableList()
        targets += phrases.values.filter { it.first == text }
        for ((p, r) in targets) {
            if (bonus.remove("$p\t$r") != null) changed = true
            if (phrases.remove("$p\t$r") != null) changed = true
            // 組成這個詞的那幾段選過的字也一起忘掉（不然「僿」「的」各自的加分又會拼回「僿的」）
            val syl = r.split(' ')
            val parts = bonus.keys.filter { k ->
                val (bp, br) = k.split('\t').let { it[0] to it.getOrElse(1) { "" } }
                val bs = br.split(' ')
                bp.isNotEmpty() && bp.length == bs.size && bp.length < p.length &&
                    (0..p.length - bp.length).any { i -> p.startsWith(bp, i) && syl.subList(i, i + bs.size) == bs }
            }
            if (parts.isNotEmpty()) { parts.forEach { bonus.remove(it) }; changed = true }
        }
        if (changed) save()
        return changed
    }

    fun clearAll() {
        bonus.clear()
        phrases.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        try {
            val sb = StringBuilder()
            for ((k, v) in bonus) sb.append("B\t").append(k).append('\t').append(v).append('\n')
            for ((p, r) in phrases.values) sb.append("P\t").append(p).append('\t').append(r).append('\n')
            f.writeText(sb.toString())
        } catch (_: Exception) {
        }
    }

    companion object {
        /** 比第一名多多少（log10，0.3 ≈ 2 倍） */
        private const val MARGIN = 0.3f
        private const val MAX_BONUS = 12f
        private const val MAX_PHRASES = 2000
        /** 自訂詞的分數：比一般常用詞還高，整句轉換時會優先用 */
        const val USER_PHRASE_SCORE = -2.0f
    }
}
