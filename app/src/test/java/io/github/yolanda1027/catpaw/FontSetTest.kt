package io.github.yolanda1027.catpaw

import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FontSetTest {
    private val zh = Typeface.SERIF
    private val zy = Typeface.MONOSPACE
    private val en = Typeface.SANS_SERIF
    private val set = FontSet(zh, zy, en)

    private fun names(s: String) = set.runs(s).map { (t, tf) ->
        t + "/" + when (tf) { zh -> "中"; zy -> "注"; else -> "英" }
    }

    @Test fun splitsByScript() {
        assertEquals(listOf("這/中", "ㄅㄧˇ ㄎㄜˇ/注"), names("這ㄅㄧˇ ㄎㄜˇ"))
        assertEquals(listOf("?123/英"), names("?123"))
        assertEquals(listOf("「好」/中", "Hi/英"), names("「好」Hi"))
    }

    @Test fun combiningMarksStayWithPreviousChar() {
        // 顏文字的附加符號（•́ 的那一撇）要跟著前面的字，不能自己變一段
        val runs = set.runs("(•́ㅂ•̀)")
        assertEquals("(•́ㅂ•̀)", runs.joinToString("") { it.first })
        runs.forEach { (t, _) -> assert(!Character.isLetterOrDigit(t[0]) || t[0] != '́') }
    }

    @Test fun emptyAndUniform() {
        assertEquals(1, set.runs("").size)
        assertEquals(1, FontSet(en, en, en).runs("這ㄅHi").size)
    }
}
