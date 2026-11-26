package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 平板、手機橫放：鍵盤跟手機直放一樣大、放中間，兩邊露出背景；平板照按鍵高度等比例放大 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class WideScreenTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dp = 2.625f
    private val log = ArrayList<String>()

    private fun board(widthPx: Int, keyHeightDp: Int = 45): CatKeyboardView {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        p.keyHeightDp = keyHeightDp
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.rows = Layouts.ZHUYIN
        kv.quickItems = listOf("，", "。", "？", "！")
        kv.listener = object : TestListener() {
            override fun onKey(key: Key, longPress: Boolean) { log += key.label }
            override fun onTabChinese() { log += "中" }
            override fun onTabEnglish() { log += "En" }
            override fun onHide() { log += "hide" }
            override fun onQuick(item: String) { log += "quick $item" }
        }
        kv.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, widthPx, kv.measuredHeight)
        return kv
    }

    private fun tap(kv: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test fun phoneUnchanged() {
        val kv = board(1080)
        assertEquals(1080f, kv.contentWidth, 0.5f)
    }

    @Test fun phoneLandscapeSameAsPortrait() {
        val portrait = board(1080)
        val kv = board(2340) // 同一支手機轉橫
        val cw = kv.contentWidth
        // 按鍵區跟直放一樣寬（這支手機直放的寬），每顆鍵大小幾乎一樣
        val shortSide = minOf(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels).toFloat()
        assertEquals(shortSide, cw, 0.5f)
        assertEquals(portrait.keySizeOf(Layouts.ZHUYIN[1][0])!!.first, kv.keySizeOf(Layouts.ZHUYIN[1][0])!!.first, 1f)
        assertEquals(portrait.keySizeOf(Layouts.ZHUYIN[1][0])!!.second, kv.keySizeOf(Layouts.ZHUYIN[1][0])!!.second, 0.01f)
        val pad = (2340 - cw) / 2
        // 點按鍵區裡的「中」：有反應
        val topY = kv.floatH + 20 * dp
        tap(kv, pad + cw * 0.1f, topY)
        assertEquals(listOf("中"), log)
        // 點兩邊空白的地方：沒反應（不會變成「中」、不會收鍵盤）
        log.clear()
        tap(kv, pad / 2, topY)
        tap(kv, 2340 - pad / 2, topY)
        tap(kv, pad / 2, kv.measuredHeight * 0.6f)
        assertEquals(emptyList<String>(), log)
        // 快捷列第一顆：在按鍵區的左邊
        tap(kv, pad + cw * 0.1f, kv.floatH + 40 * dp + 20 * dp)
        assertEquals(listOf("quick ，"), log)
        val bmp = Bitmap.createBitmap(2340, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/wide-landscape.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 平板（短邊 800dp）：照一般手機的比例；按鍵高度調高，整個鍵盤等比例放大 */
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletFollowsKeyHeight() {
        val d = 2f
        val kv = board(2560)
        assertEquals(45 * d * 411f / 45f, kv.contentWidth, 1f)
        val big = board(2560, keyHeightDp = 60)
        assertEquals(60 * d * 411f / 45f, big.contentWidth, 1f)
        // 等比例：寬高比跟預設一樣
        val (w1, h1) = kv.keySizeOf(Layouts.ZHUYIN[1][0])!!
        val (w2, h2) = big.keySizeOf(Layouts.ZHUYIN[1][0])!!
        assertEquals(w1 / h1, w2 / h2, 0.01f)
        Prefs(ctx).keyHeightDp = 45
    }
}
