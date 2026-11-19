package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 剪貼簿頁有很長的一筆（最多 5000 字）：畫一張畫面不能慢（按鍵動畫每一幀都要重畫） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClipDrawSpeedTest {
    @Test fun longClipsDrawFast() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        prefs.theme = "classic"
        val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
        val long = "one single white quill feather pen, slanted diagonally with a soft glow ".repeat(70).take(ClipHistory.MAX_LEN)
        val zh = "塞德里克推開玻璃木門帶她走進去，挑了最裡頭一張靠著壁爐的桌子。".repeat(150).take(ClipHistory.MAX_LEN)
        v.rows = Layouts.clipRows(listOf(
            ClipHistory.Item(long, 0L, true), ClipHistory.Item(zh, 0L, false),
            ClipHistory.Item(long + "x", 0L, false), ClipHistory.Item(zh + "y", 0L, false),
        ), 0)
        v.symbolTabs = listOf("剪貼簿")
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val b = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        v.draw(c) // 第一次（字型載入之類）不算
        val t0 = System.nanoTime()
        repeat(10) { v.draw(c) }
        val ms = (System.nanoTime() - t0) / 1e6 / 10
        println("CLIPDRAW ${"%.1f".format(ms)} ms per frame")
        assertTrue("畫一張剪貼簿畫面花了 $ms ms", ms < 50)
    }

    @Test fun cutsCleanly() {
        val t = ClassicTheme(2.625f)
        val s = t.fitOneLine("貓貓🐱".repeat(200), 30f, 400f)
        assertTrue(s, s.endsWith("…") && !Character.isHighSurrogate(s[s.length - 2]))
        assertTrue("剛好放得下", t.measure(s, 30f) <= 400f && t.measure(s.dropLast(1) + "貓…", 30f) > 380f)
        assertTrue("短的不截", t.fitOneLine("短短的", 30f, 400f) == "短短的")
    }
}
