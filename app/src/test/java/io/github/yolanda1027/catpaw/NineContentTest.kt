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

    /** 切得保守：很淡的陰影留著、最邊邊細細的描邊不會被切到；取消勾選就照整張圖切 */
    @Test fun conservativeAndCanBeTurnedOff() {
        // 大張的圖（會跳著看）：最外圈 2px 細描邊，再外面一圈很淡（約 4%）的陰影
        val big = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
        val c = Canvas(big)
        c.drawRect(RectF(97f, 97f, 1503f, 903f), Paint().apply { color = Color.argb(10, 0, 0, 0) }) // 淡淡的陰影
        c.drawRect(RectF(101f, 101f, 1499f, 899f), Paint().apply { color = Color.rgb(200, 150, 60) }) // 描邊
        c.drawRect(RectF(103f, 103f, 1497f, 897f), Paint().apply { color = Color.rgb(243, 227, 192) })
        val cr = NineFrame.contentRect(big)
        assertTrue("淡陰影和描邊都在範圍裡：$cr", cr.left <= 97 && cr.top <= 97 && cr.right >= 1503 && cr.bottom >= 903)
        // 取消勾選：照整張圖切（左邊會是透明的空白）
        val bmp = paddedParchment()
        val whole = NineFrame.contentRect(bmp, trim = false)
        assertEquals(0, whole.left)
        val out = Bitmap.createBitmap(230, 245, Bitmap.Config.ARGB_8888)
        NineFrame.draw(Canvas(out), bmp, 0.3f, 0.3f, RectF(0f, 0f, 230f, 245f), trim = false)
        assertEquals("不去空白：左邊是透明的", 0, Color.alpha(out.getPixel(4, 122)))
        // 存檔再讀回來，設定還在
        val f = ImageFill("a.png", nine = true, trim = false)
        assertEquals(false, ImageFill.fromJson(org.json.JSONObject(f.toJson().toString()))!!.trim)
        assertEquals(true, ImageFill.fromJson(org.json.JSONObject(ImageFill("a.png", nine = true).toJson().toString()))!!.trim)
    }
}
