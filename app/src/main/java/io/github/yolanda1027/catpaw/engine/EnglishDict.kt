package io.github.yolanda1027.catpaw.engine

import java.io.File
import java.io.InputStream

/**
 * 英文建議字：打到一半補完（hel → hello、help），也猜小錯字（helo → hello、dont → don't）。
 * 只建議，不會自動改掉你打的字。
 *
 * 單字表來自 AOSP 英文鍵盤（見 tools/build_en_dict.py），一行「單字<TAB>常用度 0~255」，照小寫排序。
 */
class EnglishDict private constructor(
    /** 小寫，排好序（查開頭用二分搜尋） */
    private val keys: Array<String>,
    private val freqs: ByteArray,
    /** 寫法跟小寫不一樣的字（I、iPhone、London…）：第幾個 → 原本的寫法 */
    private val display: Map<Int, String>,
) {
    val size get() = keys.size

    private fun freqAt(i: Int) = freqs[i].toInt() and 0xFF

    /** 第一個 >= key 的位置 */
    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun indexOf(key: String): Int {
        val i = lowerBound(key)
        return if (i < keys.size && keys[i] == key) i else -1
    }

    fun contains(word: String) = indexOf(word.lowercase()) >= 0

    /**
     * @param typed 游標前面正在打的那個字（只有英文字母和 '）
     * @param user 學過的字（可以是 null）
     * @return 建議字，最推薦的在前面；大小寫跟著你打的（Hel → Hello、HEL → HELLO）
     */
    fun suggest(typed: String, user: EnglishUser? = null, limit: Int = MAX): List<String> {
        val low = typed.lowercase()
        if (low.isEmpty()) return emptyList()
        // 小寫 → (原本寫法, 分數)
        val found = HashMap<String, Pair<String, Int>>()
        fun offer(key: String, shown: String, score: Int) {
            val old = found[key]
            if (old == null || old.second < score) found[key] = shown to score
        }

        // 1. 開頭一樣的字（補完）
        var i = lowerBound(low)
        var n = 0
        while (i < keys.size && n < SCAN_MAX && keys[i].startsWith(low)) {
            val k = keys[i]
            var s = freqAt(i) + (user?.bonus(k) ?: 0)
            if (k.length == low.length) s += EXACT_BONUS
            offer(k, display[i] ?: k, s)
            i++
            n++
        }
        // 2. 學過、但字典裡沒有的字（打過兩次以上才建議，免得打錯一次就一直出現）
        user?.startingWith(low)?.forEach { (k, shown) ->
            if (indexOf(k) < 0) offer(k, shown, USER_BASE + user.bonus(k))
        }
        // 3. 差一個字母的字（少打、多打、打錯、前後顛倒）
        if (low.length >= 2) {
            // 打得很短的時候（he、goo），比較可能是還沒打完，不是打錯：猜錯字多扣一點
            val short = if (low.length <= 3) SHORT_PENALTY else 0
            for ((v, penalty) in edits1(low)) {
                val j = indexOf(v)
                if (j >= 0) offer(v, display[j] ?: v, freqAt(j) - penalty - (if (penalty > 0) short else 0) + (user?.bonus(v) ?: 0))
            }
        }
        return found.entries
            .sortedWith(compareByDescending<Map.Entry<String, Pair<String, Int>>> { it.value.second }.thenBy { it.key })
            .take(limit)
            .map { matchCase(typed, it.value.first) }
    }

    companion object {
        const val MAX = 12
        /** 開頭一樣的字最多看幾個（只打一個字母時有上萬個，看最前面的就夠了） */
        private const val SCAN_MAX = 30000
        private const val EXACT_BONUS = 20
        private const val SHORT_PENALTY = 20
        private const val USER_BASE = 110
        private const val LETTERS = "abcdefghijklmnopqrstuvwxyz'"

        fun load(input: InputStream): EnglishDict {
            val keys = ArrayList<String>(140_000)
            val freqs = java.io.ByteArrayOutputStream(140_000)
            val display = HashMap<Int, String>()
            input.bufferedReader().forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEachLine
                val w = line.substring(0, tab)
                val f = line.substring(tab + 1).toIntOrNull() ?: return@forEachLine
                val k = w.lowercase()
                // 同一個小寫有兩種寫法（may / May）：只留比較常用的那個（檔案裡比較常用的排前面）
                if (keys.isNotEmpty() && keys.last() == k) return@forEachLine
                if (w != k) display[keys.size] = w
                keys.add(k)
                freqs.write(f.coerceIn(0, 255))
            }
            return EnglishDict(keys.toTypedArray(), freqs.toByteArray(), display)
        }

        /** 鍵盤上相鄰的字母（按歪了最常打錯成旁邊的字） */
        private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private fun near(a: Char, b: Char): Boolean {
            val ra = ROWS.indexOfFirst { a in it }
            val rb = ROWS.indexOfFirst { b in it }
            if (ra < 0 || rb < 0 || kotlin.math.abs(ra - rb) > 1) return false
            // 下一排往右偏半格：同一排差 1，上下排差 0 或 1
            val xa = ROWS[ra].indexOf(a) + ra * 0.5f
            val xb = ROWS[rb].indexOf(b) + rb * 0.5f
            return kotlin.math.abs(xa - xb) <= 1.01f
        }

        /**
         * 差一個字母的所有可能（Norvig 的拼字修正），和每一種錯法的扣分：
         * 少打 ' （dont → don't、im → I'm）最常見不扣分還加分；少打一個字母、前後顛倒扣一點；
         * 按到旁邊的鍵扣一點，按到很遠的鍵扣比較多；多打一個字母只在 4 個字母以上才猜（hel 不會變成 he）。
         */
        fun edits1(w: String): Map<String, Int> {
            val out = HashMap<String, Int>()
            fun add(v: String, p: Int) {
                if (v != w && (out[v] ?: Int.MAX_VALUE) > p) out[v] = p
            }
            for (i in 0..w.length) {
                val a = w.substring(0, i)
                val b = w.substring(i)
                if (b.isNotEmpty() && w.length >= 4) add(a + b.substring(1), 30) // 多打一個
                if (b.length > 1) add(a + b[1] + b[0] + b.substring(2), 15) // 前後顛倒
                for (c in LETTERS) {
                    if (b.isNotEmpty() && c != b[0] && c != '\'') add(a + c + b.substring(1), if (near(c, b[0])) 20 else 45) // 打錯一個
                    if (c == '\'') { if (i in 1 until w.length) add(a + c + b, -60) } // 少打 '
                    else add(a + c + b, 15) // 少打一個
                }
            }
            return out
        }

        /** 大小寫跟著打的字：Hel → Hello、HEL → HELLO；專有名詞（iPhone、London）照原本的寫法 */
        fun matchCase(typed: String, word: String): String {
            val letters = typed.filter { it.isLetter() }
            return when {
                letters.length >= 2 && letters.all { it.isUpperCase() } -> word.uppercase()
                word.any { it.isUpperCase() } -> word // 本來就有大寫的照原本寫法
                letters.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
                else -> word
            }
        }

        /** 游標前面正在打的那個英文字（字母和 '）；前面接著數字、@、網址那類符號的話不算（不在打一般的字） */
        fun currentWord(before: CharSequence?): String {
            if (before.isNullOrEmpty()) return ""
            var i = before.length
            while (i > 0 && isWordChar(before[i - 1])) i--
            var w = before.substring(i)
            if (i > 0 && before[i - 1].let { it.isLetterOrDigit() || it in "@/._-#:\\" }) return ""
            w = w.trimStart('\'')
            return w
        }

        fun isWordChar(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z') || c == '\''
    }
}

/**
 * 記住你常打的英文字（只存在這支手機）：常打的字排前面；字典裡沒有的字（名字、自創字）打過兩次以上也會出現。
 * 檔案格式一行一筆：小寫<TAB>寫法<TAB>次數
 */
class EnglishUser(private val file: File?) {
    private val words = HashMap<String, Pair<String, Int>>()
    var paused = false
    var dirty = false
        private set

    init {
        file?.takeIf { it.exists() }?.forEachLine { line ->
            val f = line.split('\t')
            if (f.size == 3) f[2].toIntOrNull()?.let { words[f[0]] = f[1] to it }
        }
    }

    fun count(key: String) = words[key]?.second ?: 0

    /** 排名加分：打越多次加越多（最多加到 10 次） */
    fun bonus(key: String): Int = minOf(count(key), 10) * 6

    /** 開頭一樣、打過兩次以上的字 */
    fun startingWith(low: String): List<Pair<String, String>> =
        words.entries.filter { it.key.startsWith(low) && it.value.second >= 2 }.map { it.key to it.value.first }

    /** 打完一個字（按空白、標點，或從建議點選）時記一次 */
    fun learn(word: String) {
        if (paused) return
        val w = word.trim('\'')
        if (w.length < 2 || w.length > 40 || !w.all { EnglishDict.isWordChar(it) }) return
        val k = w.lowercase()
        val old = words[k]
        // 寫法：只有開頭大寫（句首、名字）記成小寫，打的時候再照你打的大小寫；iPhone、NASA 這種照著記
        val shown = if (w.drop(1).any { it.isUpperCase() }) w else old?.first ?: k
        words[k] = shown to ((old?.second ?: 0) + 1)
        if (words.size > LIMIT) {
            // 太多了：丟掉最少打的
            words.entries.sortedBy { it.value.second }.take(words.size - LIMIT).forEach { words.remove(it.key) }
        }
        dirty = true
    }

    fun save() {
        val f = file ?: return
        if (!dirty) return
        dirty = false
        try {
            f.writeText(words.entries.joinToString("") { "${it.key}\t${it.value.first}\t${it.value.second}\n" })
        } catch (_: Exception) {
        }
    }

    companion object {
        const val LIMIT = 3000
    }
}
