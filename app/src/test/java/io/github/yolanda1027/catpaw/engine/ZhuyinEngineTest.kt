package io.github.yolanda1027.catpaw.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class ZhuyinEngineTest {

    companion object {
        lateinit var dict: ZhuyinDict

        @BeforeClass
        @JvmStatic
        fun load() {
            val t = System.currentTimeMillis()
            dict = File("src/main/assets/zhuyin.dict").inputStream().use { ZhuyinDict.load(it) }
            println("dict loaded: ${dict.size} entries in ${System.currentTimeMillis() - t} ms")
        }
    }

    private fun engine() = ZhuyinEngine(dict, UserScores(null))

    /** 模擬打字：空白鍵 = 一聲 */
    private fun ZhuyinEngine.type(keys: String): ZhuyinEngine {
        for (c in keys) {
            when {
                c == ' ' -> typeTone("")
                Bpmf.isTone(c) -> typeTone(c.toString())
                else -> typeSymbol(c)
            }
        }
        return this
    }

    private fun convert(keys: String) = engine().type(keys).commitAll()

    @Test fun singleChar() = assertEquals("你", convert("ㄋㄧˇ"))

    @Test fun phrase() = assertEquals("你好", convert("ㄋㄧˇㄏㄠˇ"))

    @Test fun firstToneWithSpace() = assertEquals("今天", convert("ㄐㄧㄣ ㄊㄧㄢ "))

    @Test fun sentence() = assertEquals("今天天氣很好", convert("ㄐㄧㄣ ㄊㄧㄢ ㄊㄧㄢ ㄑㄧˋㄏㄣˇㄏㄠˇ"))

    @Test fun sentence2() = assertEquals("我們去看電影", convert("ㄨㄛˇㄇㄣ˙ㄑㄩˋㄎㄢˋㄉㄧㄢˋㄧㄥˇ"))

    @Test fun withoutTones() = assertEquals("你好", convert("ㄋㄧㄏㄠ"))

    @Test fun withoutTonesSentence() = assertEquals("我們去看電影", convert("ㄨㄛㄇㄣㄑㄩㄎㄢㄉㄧㄢㄧㄥ"))

    @Test fun abbreviation() {
        // ㄒㄒ 最常見的是「學校」，「謝謝」也要排在前面
        val c = engine().type("ㄒㄒ").candidates().map { it.text }
        assertTrue("謝謝 should be near the top: ${c.take(10)}", "謝謝" in c.take(10))
    }

    @Test fun composingShowsChineseAndRawCurrent() {
        val e = engine().type("ㄋㄧˇㄏㄠ")
        assertEquals("你ㄏㄠ", e.composingText())
    }

    @Test fun backspaceReopensLastSyllable() {
        val e = engine().type("ㄋㄧˇㄏㄠˇ")
        e.backspace() // 拿掉 ˇ
        assertEquals("你ㄏㄠ", e.composingText())
        e.backspace() // 拿掉 ㄠ
        assertEquals("你ㄏ", e.composingText())
    }

    @Test fun candidatesStartWithSentenceThenPhrases() {
        val c = engine().type("ㄐㄧㄣ ㄊㄧㄢ ㄊㄧㄢ ㄑㄧˋ").candidates().map { it.text }
        assertEquals("今天天氣", c[0])
        assertTrue("今天 should be offered: $c", "今天" in c.take(10))
        assertTrue("今 should be offered", "今" in c)
    }

    @Test fun selectingPartialKeepsTheRest() {
        val e = engine().type("ㄐㄧㄣ ㄊㄧㄢ ㄊㄧㄢ ㄑㄧˋ")
        val jintian = e.candidates().first { it.text == "今天" }
        assertEquals("今天", e.select(jintian))
        assertEquals("天氣", e.composingText())
    }

    @Test fun learningMovesPickedCandidateUp() {
        val e = engine().type("ㄋㄧˇ")
        val first = e.candidates()[0].text
        val other = e.candidates().first { it.text != first && it.text == "妳" }
        e.select(other)
        e.type("ㄋㄧˇ")
        e.select(e.candidates().first { it.text == "妳" })
        assertEquals("妳", engine().let { it }.let { e.type("ㄋㄧˇ"); e.candidates()[0].text })
    }

    @Test fun associated() {
        val a = dict.associated('天')
        assertTrue("天 → 氣/空/下 expected in $a", a.any { it == "氣" || it == "空" || it == "下" })
    }
}
