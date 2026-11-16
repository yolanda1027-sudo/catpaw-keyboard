package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** 主題匯出 → 清空 → 匯入，要回到一模一樣 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeIOTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun roundTripEverything() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        p.customBg = 0xFFFDF0F5.toInt()
        p.customAccent = 0xFFFF77AA.toInt()
        p.keyHeightDp = 50
        p.zhFont = "wenkai"; p.zhuyinFont = "huninn"; p.enFont = "iansui"
        p.pressEffect = "spell"
        p.quickBar = true
        p.setQuickEntries(listOf(Prefs.QuickEntry("()", true), Prefs.QuickEntry("⇥", false)))
        Stickers.file(ctx, StickerSlot.ENTER).writeBytes(png(Color.RED))

        val json = ThemeIO.export(ctx, p, "測試主題", ThemeIO.Part.values().toSet())

        // 換成別人的手機：全部設定都不一樣
        p.sp.edit().clear().commit()
        for (s in StickerSlot.values()) Stickers.remove(ctx, s)
        Stickers.file(ctx, StickerSlot.SPACE).writeBytes(png(Color.BLUE)) // 這張要被清掉

        val o = ThemeIO.parse(json)
        assertNotNull(o)
        val (name, parts) = ThemeIO.describe(o!!)
        assertEquals("測試主題", name)
        assertEquals(listOf("主題與配色", "字體", "按鍵動畫", "功能鍵貼圖 1 張", "快捷符號列"), parts)

        val warnings = ThemeIO.apply(ctx, p, o)
        assertTrue(warnings.toString(), warnings.isEmpty())
        val q = Prefs(ctx)
        assertEquals("macaron_butter", q.theme)
        assertEquals(0xFFFDF0F5.toInt(), q.customBg)
        assertEquals(0xFFFF77AA.toInt(), q.customAccent)
        assertEquals(50, q.keyHeightDp)
        assertEquals("wenkai", q.zhFont); assertEquals("huninn", q.zhuyinFont); assertEquals("iansui", q.enFont)
        assertEquals("spell", q.pressEffect)
        assertTrue(q.quickBar)
        assertEquals(listOf(Prefs.QuickEntry("()", true), Prefs.QuickEntry("⇥", false)), q.quickEntries())
        val enter = BitmapFactory.decodeFile(Stickers.file(ctx, StickerSlot.ENTER).path)
        assertEquals(Color.RED, enter.getPixel(5, 5))
        assertFalse(Stickers.file(ctx, StickerSlot.SPACE).exists())
    }

    @Test fun onlyThemeWhenNothingChecked() {
        val p = Prefs(ctx)
        p.pressEffect = "snow"
        val o = ThemeIO.parse(ThemeIO.export(ctx, p, "", emptySet()))!!
        assertEquals(listOf("主題與配色"), ThemeIO.describe(o).second)
        assertFalse(o.has("fonts") || o.has("effect") || o.has("stickers") || o.has("quickBar"))
        p.pressEffect = "hearts"
        ThemeIO.apply(ctx, p, o)
        assertEquals("hearts", Prefs(ctx).pressEffect) // 沒勾的不會動到
    }

    @Test fun rejectsOtherFiles() {
        assertNull(ThemeIO.parse("hello"))
        assertNull(ThemeIO.parse("{\"name\":\"x\"}"))
        assertNull(ThemeIO.parse("[1,2,3]"))
    }

    @Test fun missingUserFontAndUnknownThemeFallBack() {
        val p = Prefs(ctx)
        val o = ThemeIO.parse("""{"format":"catpaw-keyboard-theme","version":1,"name":"x","theme":"future_theme",
            "colors":{"bg":"#123456","key":"nope"},"keyHeight":999,
            "fonts":{"zh":"user:font_1.ttf","zhuyin":"huninn"}}""")!!
        val w = ThemeIO.apply(ctx, p, o)
        assertEquals(2, w.size)
        val q = Prefs(ctx)
        assertEquals("custom", q.theme)
        assertEquals(0xFF123456.toInt(), q.customBg)
        assertEquals(64, q.keyHeightDp)
        assertEquals("system", q.zhFont)
        assertEquals("huninn", q.zhuyinFont)
    }
}
