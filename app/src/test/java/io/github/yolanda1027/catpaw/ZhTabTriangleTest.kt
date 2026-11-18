package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 上方列「中」右下角的小三角形：選注音時才有，選英文時沒有 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ZhTabTriangleTest {
    private fun render(chinese: Boolean): Pair<Bitmap, CatKeyboardView> {
        val ctx = RuntimeEnvironment.getApplication()
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.rows = if (chinese) Layouts.ZHUYIN else Layouts.ENGLISH
        kv.isChinese = chinese
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        return Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888).also { kv.draw(Canvas(it)) } to kv
    }

    @Test fun triangleOnlyInChinese() {
        val (zh, kv) = render(true)
        val (en, _) = render(false)
        val topH = 40 * kv.resources.displayMetrics.density
        val tabW = kv.width * 0.2f
        // 三角形裡面的一點（直角那個角附近）和三角形旁邊同一個分頁裡的空白處
        val x = (tabW / 2 + topH * 0.33f + topH * 0.07f).toInt()
        val y = (kv.floatH + topH / 2 + topH * 0.24f).toInt()
        val bgX = (tabW / 2 + topH * 0.33f + topH * 0.3f).toInt()
        assertNotEquals("注音：要有三角形", zh.getPixel(bgX, y), zh.getPixel(x, y))
        assertEquals("英文：不要有三角形", en.getPixel(bgX, y), en.getPixel(x, y))
    }
}
