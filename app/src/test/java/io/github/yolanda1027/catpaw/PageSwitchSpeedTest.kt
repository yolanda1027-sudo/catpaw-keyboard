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

/**
 * 每顆鍵都有自訂樣式的主題：從剪貼簿跳回注音頁，第一張畫面不能慢
 * （以前換頁會把「量好的按鍵顏色」清掉，四十幾顆鍵全部重量一次，按鍵動畫卡在半空中）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PageSwitchSpeedTest {
    @Test fun switchingBackToStyledPageIsFast() {
        val ctx = RuntimeEnvironment.getApplication()
        val p = Prefs(ctx)
        p.theme = "magic_red"
        KeyStyles.save(p, KeyStyles.EMPTY
            .with("@zhuyin", KeyStyle(bg = 0xFFF3E3C0.toInt(), text = 0xFF5A3A22.toInt(), radius = 0.4f))
            .with("@func", KeyStyle(bg = 0xFF7A1F1F.toInt(), radius = 0.4f))
            .with("enter", KeyStyle(bg = 0xFFE0B040.toInt(), radius = 1f)))
        val v = CatKeyboardView(ctx, p, Haptics(ctx))
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val b = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight + 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        // 第一次畫注音頁：每顆鍵都要量一次顏色，也不能太慢
        val f0 = System.nanoTime()
        v.draw(c)
        val firstMs = (System.nanoTime() - f0) / 1e6
        println("PAGESWITCH very first frame ${"%.1f".format(firstMs)} ms（第一次要量每顆鍵的顏色，比較慢）")
        // 到剪貼簿頁
        v.pageScope = "clip"
        v.symbolTabs = listOf("剪貼簿")
        v.rows = Layouts.clipRows(listOf(ClipHistory.Item("一筆", 0L, false)), 0).onPage("clip")
        v.draw(c)
        // 貼上之後跳回注音頁：量第一張畫面
        v.pageScope = ""
        v.symbolTabs = null
        v.rows = Layouts.ZHUYIN
        val t0 = System.nanoTime()
        v.draw(c)
        val ms = (System.nanoTime() - t0) / 1e6
        println("PAGESWITCH first frame ${"%.1f".format(ms)} ms")
        assertTrue("跳回注音頁的第一張畫面花了 $ms ms", ms < 60)
    }
}
