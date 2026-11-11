package io.github.yolanda1027.catpaw.engine

/** 注音符號的分類 */
object Bpmf {
    const val INITIALS = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ"
    const val MEDIALS = "ㄧㄨㄩ"
    const val FINALS = "ㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ"
    /** ˊ ˇ ˋ ˙（一聲沒有符號） */
    const val TONES = "ˊˇˋ˙"

    /** 0 = 聲母、1 = 介音、2 = 韻母、-1 = 不是注音 */
    fun slot(c: Char): Int = when (c) {
        in INITIALS -> 0
        in MEDIALS -> 1
        in FINALS -> 2
        else -> -1
    }

    fun isTone(c: Char) = c in TONES

    /** "ㄏㄠˇ" → ("ㄏㄠ", "ˇ")；"ㄇㄚ" → ("ㄇㄚ", "") */
    fun split(syllable: String): Pair<String, String> {
        val last = syllable.lastOrNull() ?: return "" to ""
        return if (isTone(last)) syllable.dropLast(1) to last.toString() else syllable to ""
    }
}

/**
 * 使用者打的一個音節。
 * @param body 注音本體（不含聲調），例如 "ㄏㄠ"
 * @param tone null = 沒打聲調（哪一聲都可以）；"" = 一聲；其他 = ˊˇˋ˙
 */
data class Pattern(val body: String, val tone: String?) {
    /**
     * 只打了聲母、沒打聲調，當作縮寫（ㄒ 可以代表 ㄒㄧㄝ、ㄒㄧㄠ…）。
     * 有打聲調的話（例如 ㄓˋ = 智）就是完整的音，不當縮寫。
     */
    val isAbbreviation: Boolean get() = tone == null && body.length == 1 && Bpmf.slot(body[0]) == 0

    fun matches(syllable: String): Boolean {
        val (b, t) = Bpmf.split(syllable)
        val bodyOk = if (isAbbreviation) b.startsWith(body) else b == body
        return bodyOk && (tone == null || tone == t)
    }

    /** 顯示用：沒打聲調的就只顯示本體 */
    override fun toString() = body + (tone ?: "")
}
