package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 「邊框」放法：去背圖四周有透明空白時，只切有畫東西的範圍（不然按鍵會變成細細一條） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NineContentTest {
    /** 羊皮紙：300 × 220 的圖，中間 160 × 200 有畫，左右各留 70 的透明 */
    private fun paddedParchment() = Bitmap.createBitmap(300, 220, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawRoundRect(RectF(70f, 10f, 230f, 210f), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(243, 227, 192) })
    }

    @Test fun fillsTheWholeKey() {
        val bmp = paddedParchment()
        val cr = NineFrame.contentRect(bmp)
        assertTrue("有畫的範圍：$cr", cr.left in 66..72 && cr.right in 228..236)
        for ((w, h) in listOf(230 to 245, 480 to 220)) { // 直向的注音鍵、橫向的寬鍵
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            NineFrame.draw(Canvas(out), bmp, 0.3f, 0.3f, RectF(0f, 0f, w.toFloat(), h.toFloat()))
            assertEquals("${w}×$h 左邊有羊皮紙", 255, Color.alpha(out.getPixel(4, h / 2)))
            assertEquals("${w}×$h 右邊有羊皮紙", 255, Color.alpha(out.getPixel(w - 5, h / 2)))
            assertEquals("${w}×$h 中間有羊皮紙", 255, Color.alpha(out.getPixel(w / 2, h / 2)))
        }
    }
}
