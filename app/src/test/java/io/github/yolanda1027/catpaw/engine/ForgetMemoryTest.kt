package io.github.yolanda1027.catpaw.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** 長按選字 → 移除選字記憶：誤按學到的詞可以刪掉，排序回到原本的樣子 */
class ForgetMemoryTest {
    companion object {
        lateinit var dict: ZhuyinDict

        @BeforeClass
        @JvmStatic
        fun load() {
            val boosts = ZhuyinDict.parseBoosts(File("src/main/assets/boost.txt").readText())
            dict = File("src/main/assets/zhuyin.dict").inputStream().use { ZhuyinDict.load(it, boosts) }
        }
    }

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

    @Test fun misPickedWordCanBeForgotten() {
        val file = File.createTempFile("user", ".txt").apply { delete() }
        val e = ZhuyinEngine(dict, UserScores(file))
        // 原本的樣子
        val before = e.type("ㄙㄞˋㄉㄜ˙").candidates().map { it.text }
        e.clear()
        val singlesBefore = e.type("ㄙㄞˋ").candidates().map { it.text }
        e.clear()
        // 誤按：先挑一個排在後面的字，再挑「的」 → 學成一個詞
        val odd = singlesBefore[3]
        e.type("ㄙㄞˋㄉㄜ˙")
        e.select(e.candidates().first { it.text == odd && it.length == 1 })
        e.select(e.candidates().first { it.text == "的" })
        val word = odd + "的"
        val learned = e.type("ㄙㄞˋㄉㄜ˙").candidates()
        assertEquals(word, learned[0].text)
        assertTrue(e.hasMemory(learned[0]))
        // 沒記憶的選項：不能刪
        assertFalse(e.hasMemory(learned.first { it.text == singlesBefore[1] }))
        // 刪掉
        assertTrue(e.forget(learned[0]))
        e.clear()
        val after = e.type("ㄙㄞˋㄉㄜ˙").candidates().map { it.text }
        assertNotEquals(word, after[0])
        assertEquals(before.take(5), after.take(5))
        e.clear()
        // 單字的排序也回到原本（那個字的加分一起拿掉）
        assertEquals(singlesBefore.take(5), e.type("ㄙㄞˋ").candidates().map { it.text }.take(5))
        e.clear()
        // 存檔也刪掉了：重新開也不會回來
        val e2 = ZhuyinEngine(dict, UserScores(file))
        assertEquals(before.take(5), e2.type("ㄙㄞˋㄉㄜ˙").candidates().map { it.text }.take(5))
        file.delete()
    }
}
