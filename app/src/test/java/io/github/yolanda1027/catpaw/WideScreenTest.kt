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

/** 平板、手機橫放：按鍵區照手機的比例放中間，兩邊露出背景；設定可以關掉 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class WideScreenTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dp = 2.625f
    private val log = ArrayList<String>()

    private fun board(widthPx: Int, center: Boolean = true): CatKeyboardView {
        val p = Prefs(ctx)
        p.wideCenter = center
        p.theme = "magic_yellow"
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.rows = Layouts.ZHUYIN
        kv.quickItems = listOf("，", "。", "？", "！")
        kv.listener = object : CatKeyboardView.Listener {
            override fun onKey(key: Key, longPress: Boolean) { log += key.label }
            override fun onTabChinese() { log += "中" }
            override fun onTabEnglish() { log += "En" }
            override fun onHide() { log += "hide" }
            override fun onCandidate(index: Int) {}
            override fun onSymbolTab(index: Int) {}
            override fun onSymbolSwipe(direction: Int) {}
            override fun onClipboard() {}
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

    @Test fun tabletCentered() {
        val kv = board(2560)
        val cw = kv.contentWidth
        // 按鍵區大約是按鍵高度 45dp 的 11 倍，放中間
        assertEquals(45 * dp * 11f, cw, 1f)
        val pad = (2560 - cw) / 2
        // 按鍵只比手機寬兩成左右（手機上一格 108，不會變超寬）
        val (kw, _) = kv.keySizeOf(Layouts.ZHUYIN[1][0])!!
        assertTrue("注音鍵寬 $kw", kw < 108 * 1.25f)
        // 點按鍵區裡的「中」：有反應
        val topY = kv.floatH + 20 * dp
        tap(kv, pad + cw * 0.1f, topY)
        assertEquals(listOf("中"), log)
        // 點兩邊空白的地方：沒反應（不會變成「中」、不會收鍵盤）
        log.clear()
        tap(kv, pad / 2, topY)
        tap(kv, 2560 - pad / 2, topY)
        tap(kv, pad / 2, kv.measuredHeight * 0.6f)
        assertEquals(emptyList<String>(), log)
        // 快捷列第一顆：在按鍵區的左邊
        tap(kv, pad + cw * 0.1f, kv.floatH + 40 * dp + 20 * dp)
        assertEquals(listOf("quick ，"), log)
        val bmp = Bitmap.createBitmap(2560, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/wide-tablet.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun canTurnOff() {
        val kv = board(2560, center = false)
        assertEquals(2560f, kv.contentWidth, 0.5f)
        Prefs(ctx).wideCenter = true
    }
}
