package io.github.yolanda1027.catpaw.engine

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/** 詞庫裡的一筆：詞、讀音（音節用空白分隔，一聲沒有符號）、分數（log10 機率） */
class Entry(val phrase: String, val reading: String, val score: Float, val user: Boolean = false) {
    val syllables: List<String> by lazy(LazyThreadSafetyMode.NONE) { reading.split(' ') }
}

/**
 * 注音詞庫。
 *
 * 用「每個音節的第一個符號」當索引，例如「你好 ㄋㄧˇ ㄏㄠˇ」的索引是「ㄋㄏ」。
 * 這樣不管是完整注音、沒打聲調、還是只打聲母的縮寫（ㄒㄒ → 謝謝），
 * 都能用同一個索引找到，再逐筆比對。
 */
class ZhuyinDict private constructor(
    private val index: HashMap<String, ArrayList<Entry>>,
    private val assoc: HashMap<Char, List<String>>,
) {
    /** 找出符合這串音節的所有詞，分數高的在前 */
    fun lookup(patterns: List<Pattern>): List<Entry> {
        val sig = String(CharArray(patterns.size) { patterns[it].body[0] })
        val bucket = index[sig] ?: return emptyList()
        return bucket.filter { e ->
            val syl = e.syllables
            syl.size == patterns.size && patterns.indices.all { patterns[it].matches(syl[it]) }
        }
    }

    private val userEntries = ArrayList<Entry>()

    /** 換上使用者自訂詞（先拿掉舊的，再加新的） */
    fun setUserPhrases(list: List<Pair<String, String>>) {
        for (e in userEntries) index[signatureOf(e.reading)]?.remove(e)
        userEntries.clear()
        for ((p, r) in list) addUserPhrase(p, r)
    }

    fun addUserPhrase(phrase: String, reading: String) {
        val sig = signatureOf(reading)
        if (sig.length > MAX_PHRASE_LEN) return
        val e = Entry(phrase, reading, UserScores.USER_PHRASE_SCORE, user = true)
        val bucket = index.getOrPut(sig) { ArrayList() }
        bucket.add(e)
        bucket.sortByDescending { it.score }
        userEntries.add(e)
    }

    /** 聯想詞：以這個字開頭的常用詞，去掉開頭那個字 */
    fun associated(c: Char): List<String> = assoc[c] ?: emptyList()

    val size: Int get() = index.values.sumOf { it.size }

    companion object {
        const val MAX_PHRASE_LEN = 8

        /** 讀加分表（assets/boost.txt）：「詞 加分」，加分省略時是 1.0 */
        fun parseBoosts(text: String): Map<String, Float> {
            val m = HashMap<String, Float>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(Regex("\\s+"))
                m[parts[0]] = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
            }
            return m
        }

        fun load(input: InputStream, boosts: Map<String, Float> = emptyMap()): ZhuyinDict {
            val index = HashMap<String, ArrayList<Entry>>(60000)
            val assocBuild = HashMap<Char, ArrayList<Pair<String, Float>>>()
            BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.isEmpty() || line[0] == '#') continue
                    val t1 = line.indexOf('\t')
                    val t2 = line.indexOf('\t', t1 + 1)
                    if (t1 <= 0 || t2 <= t1) continue
                    val phrase = line.substring(0, t1)
                    val reading = line.substring(t1 + 1, t2)
                    val score = (line.substring(t2 + 1).toFloatOrNull() ?: continue) + (boosts[phrase] ?: 0f)
                    val sig = signatureOf(reading)
                    if (sig.length > MAX_PHRASE_LEN) continue
                    index.getOrPut(sig) { ArrayList(4) }.add(Entry(phrase, reading, score))
                    if (phrase.length in 2..4 && score > -6.5f) {
                        assocBuild.getOrPut(phrase[0]) { ArrayList() }.add(phrase.substring(1) to score)
                    }
                }
            }
            for (list in index.values) list.sortByDescending { it.score }
            val assoc = HashMap<Char, List<String>>(assocBuild.size)
            for ((c, list) in assocBuild) {
                assoc[c] = list.sortedByDescending { it.second }.map { it.first }.distinct().take(30)
            }
            return ZhuyinDict(index, assoc)
        }

        internal fun signatureOf(reading: String): String {
            val sb = StringBuilder()
            var atStart = true
            for (ch in reading) {
                if (ch == ' ') atStart = true
                else if (atStart) { sb.append(ch); atStart = false }
            }
            return sb.toString()
        }
    }
}
