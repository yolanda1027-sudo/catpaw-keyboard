package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 回歸測試：鍵盤背景「有時候」變透明（透出後面 App 的白底）。
 * 原因：按鍵底色設成透明時，主題共用的畫筆被設成全透明、沒有改回來；
 * 下一張畫面的背景漸層（魔法、粉紅縫線主題用漸層）跟著變透明。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class BackgroundLeakTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun checkTheme(theme: String) {
        val p = Prefs(ctx)
        p.theme = theme
        // 有幾顆鍵的底色設成透明（像羊皮紙那種用圖片當鍵的主題常會這樣設）
        KeyStyles.save(p, KeyStyles(mapOf(
            // Enter 鍵換成印章圖案：底色透明、不顯示原本的圖示（截圖裡就是這樣設的）
            "enter" to KeyStyle(bg = Color.TRANSPARENT, hideLabel = true),
            "backspace" to KeyStyle(bg = Color.TRANSPARENT, border = Color.parseColor("#F0C23B")),
        )))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        // 連畫好幾張（每一張都畫在全新的透明畫布上，像系統每次重畫那樣）
        repeat(3) { i ->
            val b = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
            kv.draw(Canvas(b))
            // 按鍵區左下角的空隙：一定要是不透明的背景
            val px = b.getPixel(2, b.height - 3)
            assertEquals("$theme 第 ${i + 1} 張：鍵盤背景不能透明", 255, Color.alpha(px))
            val mid = b.getPixel(b.width / 2, (kv.floatH + 40 * 2.625f + 4).toInt())
            assertEquals("$theme 第 ${i + 1} 張：按鍵區上緣的背景不能透明", 255, Color.alpha(mid))
        }
    }

    @Test fun magicThemeBackgroundStaysOpaque() = checkTheme("magic_yellow")
    @Test fun stitchThemeBackgroundStaysOpaque() = checkTheme("stitch")

    /** 畫半透明的 GIF（圖案透明度、按鍵動畫淡出）之後，畫布要回到原本的狀態 */
    @Test fun drawAnimRestoresCanvas() {
        val gif = javaClass.classLoader!!.getResourceAsStream("anim.gif").readBytes()
        val f = java.io.File(ctx.cacheDir, "t.gif").apply { writeBytes(gif) }
        val d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(f))
        val c = Canvas(Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888))
        val before = c.saveCount
        Decals.drawAnim(c, d, android.graphics.RectF(10f, 10f, 110f, 110f), 128)
        assertEquals(before, c.saveCount)
        Decals.drawAnim(c, d, android.graphics.RectF(10f, 10f, 110f, 110f), 255)
        assertEquals(before, c.saveCount)
    }
}
