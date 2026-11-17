package io.github.yolanda1027.catpaw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 設定裡可以把表情鍵盤的「🖼️ 我的貼圖」分類關掉 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class MyStickersToggleTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private class Ime(val s: CatImeService) {
        val kv = s.onCreateInputView() as CatKeyboardView
        private fun f(n: String) = CatImeService::class.java.getDeclaredField(n).apply { isAccessible = true }
        var cat: Int
            get() = f("emojiCat").getInt(s)
            set(v) = f("emojiCat").setInt(s, v)
        fun toEmoji() {
            val pageField = f("page")
            @Suppress("UNCHECKED_CAST")
            val emoji = (pageField.type.enumConstants as Array<Any>).first { it.toString() == "EMOJI" }
            pageField.set(s, emoji)
            refresh()
        }
        fun refresh() = CatImeService::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(s)
    }

    @Test fun tabComesAndGoes() {
        Emojis.load(File("src/main/assets/emoji.txt").readText())
        val p = Prefs(ctx)
        val ime = Ime(Robolectric.buildService(CatImeService::class.java).create().get())
        ime.cat = 0
        ime.toEmoji()
        // 預設打開：🕒、🖼️、😀…
        assertEquals("🖼️", ime.kv.symbolTabs!![1])
        ime.s.onSymbolTab(1)
        assertEquals(1, ime.cat)

        // 關掉：正在看貼圖的話跳到第一個表情分類，分類列上沒有 🖼️
        p.myStickers = false
        ime.refresh()
        assertFalse("🖼️" in ime.kv.symbolTabs!!)
        assertEquals(2, ime.cat)
        assertEquals(1, ime.kv.symbolTab) // 分類列上的第 2 格
        assertEquals(Emojis.groups[0].first, ime.kv.symbolTabs!![1])
        // 點分類列第 3 格 → 第二個表情分類
        ime.s.onSymbolTab(2)
        assertEquals(3, ime.cat)
        assertEquals(2, ime.kv.symbolTab)
        // 往回滑到第一個表情分類的開頭，再往回就停住（不會滑進貼圖）
        ime.s.onSymbolTab(1)
        ime.s.onSymbolSwipe(-1)
        assertEquals(2, ime.cat)
        assertFalse(ime.kv.morePagesBefore)
        // 🕒 最近使用還是第 1 格
        ime.s.onSymbolTab(0)
        assertEquals(0, ime.cat)

        // 再打開：回來了
        p.myStickers = true
        ime.refresh()
        assertTrue("🖼️" in ime.kv.symbolTabs!!)
    }
}
