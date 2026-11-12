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
        val k = key(e)
        val cur = bonus[k] ?: 0f
        val needed = topScore - e.score + MARGIN
        bonus[k] = maxOf(cur, needed).coerceAtMost(MAX_BONUS)
        save()
    }

    /** 記住一個自訂詞，回傳 true 代表是新的 */
    fun addPhrase(phrase: String, reading: String): Boolean {
        val k = "$phrase\t$reading"
        if (phrases.containsKey(k)) return false
        phrases[k] = phrase to reading
        while (phrases.size > MAX_PHRASES) phrases.remove(phrases.keys.first())
        save()
        return true
    }

    fun userPhrases(): List<Pair<String, String>> = phrases.values.toList()

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
