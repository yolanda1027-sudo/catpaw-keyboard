package io.github.yolanda1027.catpaw.engine

/** 選字列上的一個選項 */
class Candidate(
    val text: String,
    /** 選了它會用掉前面幾個音節 */
    val length: Int,
    /** 組成這個選項的詞（學習用） */
    val entries: List<Entry>,
)

/** 整句轉換的一段 */
class Segment(val text: String, val entry: Entry?)

/**
 * 注音轉中文的引擎（不依賴 Android，可以直接在電腦上測試）。
 *
 * 打字的規則：
 * - 按聲調鍵（ˊˇˋ˙）或空白（一聲）把目前這個字的注音結束
 * - 不打聲調也可以：打到下一個字的聲母時，會自動切開，前一個字當作「哪一聲都可以」
 * - 只打聲母也行（ㄒㄒ → 謝謝）
 */
class ZhuyinEngine(private val dict: ZhuyinDict, private val user: UserScores) {

    private val done = ArrayList<Pattern>()
    private val current = StringBuilder()
    private var currentSlot = -1
    private val cache = HashMap<String, List<Entry>>()

    val isEmpty: Boolean get() = done.isEmpty() && current.isEmpty()

    fun clear() {
        done.clear()
        current.setLength(0)
        currentSlot = -1
    }

    /** 打一個注音符號（聲母／介音／韻母） */
    fun typeSymbol(c: Char) {
        val slot = Bpmf.slot(c)
        if (slot < 0) return
        if (current.isNotEmpty() && slot <= currentSlot) {
            // 例如 ㄋㄧ 之後打 ㄏ：前一個字結束（沒打聲調）
            done.add(Pattern(current.toString(), null))
            current.setLength(0)
        }
        current.append(c)
        currentSlot = slot
    }

    /** 打聲調。一聲請傳 ""。回傳 false 代表沒有東西可以加聲調 */
    fun typeTone(tone: String): Boolean {
        if (current.isNotEmpty()) {
            done.add(Pattern(current.toString(), tone))
            current.setLength(0)
            currentSlot = -1
            return true
        }
        // 剛剛被自動切開、還沒打聲調的字，補上聲調
        val last = done.lastOrNull() ?: return false
        if (last.tone != null) return false
        done[done.size - 1] = last.copy(tone = tone)
        return true
    }

    /** 目前正在打、還沒結束的注音 */
    val hasPartial: Boolean get() = current.isNotEmpty()

    fun backspace(): Boolean {
        if (current.isNotEmpty()) {
            current.setLength(current.length - 1)
            currentSlot = current.lastOrNull()?.let { Bpmf.slot(it) } ?: -1
            return true
        }
        val last = done.removeLastOrNull() ?: return false
        // 把上一個字拿回來繼續編輯：有聲調就先拿掉聲調，沒有就刪掉最後一個符號
        val body = if (last.tone != null) last.body else last.body.dropLast(1)
        current.append(body)
        currentSlot = current.lastOrNull()?.let { Bpmf.slot(it) } ?: -1
        return true
    }

    private fun allPatterns(): List<Pattern> =
        if (current.isNotEmpty()) done + Pattern(current.toString(), null) else done.toList()

    private fun scoreOf(e: Entry) = e.score + user.bonus(e)

    private fun lookup(p: List<Pattern>): List<Entry> {
        val key = p.joinToString("|") { it.body + "/" + (it.tone ?: "*") }
        return cache.getOrPut(key) {
            if (cache.size > 2000) cache.clear()
            dict.lookup(p)
        }
    }

    private fun best(p: List<Pattern>): Entry? = lookup(p).maxByOrNull { scoreOf(it) }

    /** 整句轉換：找出分數加起來最高的切法 */
    fun convert(patterns: List<Pattern> = allPatterns()): List<Segment> {
        val n = patterns.size
        if (n == 0) return emptyList()
        val bestScore = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val backLen = IntArray(n + 1)
        val backEntry = arrayOfNulls<Entry>(n + 1)
        bestScore[0] = 0f
        for (i in 0 until n) {
            if (bestScore[i] == Float.NEGATIVE_INFINITY) continue
            for (len in 1..minOf(ZhuyinDict.MAX_PHRASE_LEN, n - i)) {
                val e = best(patterns.subList(i, i + len))
                val s = when {
                    e != null -> bestScore[i] + scoreOf(e)
                    len == 1 -> bestScore[i] - 20f // 查不到的音，就直接顯示注音
                    else -> continue
                }
                if (s > bestScore[i + len]) {
                    bestScore[i + len] = s
                    backLen[i + len] = len
                    backEntry[i + len] = e
                }
            }
        }
        val out = ArrayList<Segment>()
        var j = n
        while (j > 0) {
            val len = backLen[j]
            val e = backEntry[j]
            out.add(Segment(e?.phrase ?: patterns[j - 1].toString(), e))
            j -= len
        }
        out.reverse()
        return out
    }

    /** 編輯區要顯示的字：已經結束的字轉成中文，正在打的那個字顯示注音 */
    fun composingText(): String = convert(done).joinToString("") { it.text } + current

    /** 目前打的注音（顯示在鍵盤上方的小框框），音節之間空一格 */
    fun readingText(): String =
        (done.map { it.toString() } + if (current.isNotEmpty()) listOf(current.toString()) else emptyList())
            .joinToString(" ")

    /** 全部轉成中文（按 Enter 或空白送出時用） */
    fun commitAll(): String {
        val segs = convert()
        segs.forEach { s -> s.entry?.let { user.learn(it) } }
        clear()
        return segs.joinToString("") { it.text }
    }

    /**
     * 選字列：
     * 1. 整句轉換的結果
     * 2. 從第一個字開始、由長到短的詞（例如打了 3 個字：三字詞 → 兩字詞 → 單字）
     */
    fun candidates(): List<Candidate> {
        val p = allPatterns()
        if (p.isEmpty()) return emptyList()
        val out = ArrayList<Candidate>()
        val seen = HashSet<String>()
        if (p.size >= 2) {
            val segs = convert(p)
            if (segs.size > 1) {
                val text = segs.joinToString("") { it.text }
                out.add(Candidate(text, p.size, segs.mapNotNull { it.entry }))
                seen.add(text)
            }
        }
        for (len in minOf(ZhuyinDict.MAX_PHRASE_LEN, p.size) downTo 1) {
            for (e in lookup(p.subList(0, len)).sortedByDescending { scoreOf(it) }) {
                if (seen.add(e.phrase)) out.add(Candidate(e.phrase, len, listOf(e)))
            }
        }
        return out
    }

    /** 選了一個選項：回傳要送出的文字，剩下的音節繼續留著 */
    fun select(c: Candidate): String {
        c.entries.forEach { user.learn(it) }
        val total = done.size + if (current.isNotEmpty()) 1 else 0
        if (c.length >= total) {
            clear()
        } else {
            repeat(c.length) { done.removeAt(0) }
        }
        return c.text
    }
}
