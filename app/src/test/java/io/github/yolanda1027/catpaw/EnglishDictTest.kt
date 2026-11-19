package io.github.yolanda1027.catpaw

import io.github.yolanda1027.catpaw.engine.EnglishDict
import io.github.yolanda1027.catpaw.engine.EnglishUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 英文建議字：補完、猜錯字、大小寫、記住常打的字 */
class EnglishDictTest {
    companion object {
        val dict: EnglishDict by lazy { File("src/main/assets/en_words.txt").inputStream().use { EnglishDict.load(it) } }
    }

    private fun s(t: String, u: EnglishUser? = null) = dict.suggest(t, u)

    @Test fun printSamples() {
        for (t in listOf("h", "th", "hel", "helo", "teh", "dont", "im", "i", "Hel", "HEL", "beautifl", "recieve", "tomorow", "ip", "goo"))
            println("$t → ${s(t)}")
    }

    @Test fun loadsWholeList() {
        assertTrue("字典有十萬字以上，實際 ${dict.size}", dict.size > 100_000)
        assertTrue(dict.contains("hello"))
        assertTrue(dict.contains("Hello"))
        assertFalse(dict.contains("helo"))
    }

    @Test fun completesPrefix() {
        val r = s("hel")
        assertTrue(r.toString(), "hello" in r && "help" in r)
        assertEquals("最常用的排前面", "the", s("th").first())
    }

    @Test fun guessesTypos() {
        assertTrue(s("helo").toString(), "hello" in s("helo").take(3))
        assertTrue(s("teh").toString(), "the" in s("teh").take(3))
        assertTrue(s("dont").toString(), "don't" in s("dont").take(3))
        assertTrue(s("recieve").toString(), "receive" in s("recieve").take(3))
    }

    @Test fun followsCase() {
        assertTrue(s("Hel").toString(), "Hello" in s("Hel"))
        assertTrue(s("HEL").toString(), "HELLO" in s("HEL"))
        assertEquals("I", s("i").first())
        assertEquals(s("im").toString(), "I'm", s("im").first())
        assertTrue(s("ip").toString(), "iPhone" in s("ip"))
    }

    @Test fun learnsUserWords() {
        val f = File.createTempFile("en_user", ".txt").apply { delete() }
        val u = EnglishUser(f)
        u.learn("Catpaw")
        assertFalse("只打一次不建議", "catpaw" in s("catp", u))
        u.learn("catpaw")
        assertTrue(s("catp", u).toString(), "catpaw" in s("catp", u))
        assertTrue("句首大寫照打的", "Catpaw" in s("Catp", u))
        u.save()
        assertTrue("存起來，下次還記得", "catpaw" in s("catp", EnglishUser(f)))
        // 常打的字排前面
        val before = s("he").indexOf("hello")
        repeat(10) { u.learn("hello") }
        assertTrue(s("he", u).indexOf("hello") < before || before < 0)
    }

    @Test fun pausedLearnsNothing() {
        val f = File.createTempFile("en_user", ".txt").apply { delete() }
        val u = EnglishUser(f)
        u.paused = true
        u.learn("secretword")
        u.learn("secretword")
        u.save()
        assertFalse(f.exists())
        assertEquals(0, u.count("secretword"))
    }

    @Test fun currentWord() {
        assertEquals("hel", EnglishDict.currentWord("say hel"))
        assertEquals("don't", EnglishDict.currentWord("I don't"))
        assertEquals("", EnglishDict.currentWord("hello "))
        assertEquals("", EnglishDict.currentWord("mail me@gm"))
        assertEquals("", EnglishDict.currentWord("www.goo"))
        assertEquals("", EnglishDict.currentWord("abc123x"))
        assertEquals("你好", "你好" + EnglishDict.currentWord("你好"))
        assertEquals("ok", EnglishDict.currentWord("（ok"))
    }
}
