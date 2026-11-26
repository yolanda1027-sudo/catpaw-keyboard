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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ChineseConvertTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Test fun convertsWithPhrasesFirst() {
        fun s(t: String) = ChineseConvert.toSimplified(ctx, t)
        assertEquals("头发很长", s("頭髮很長"))
        assertEquals("这里的面包", s("這裡的麵包"))
        assertEquals("于是我们出发了", s("於是我們出發了"))
        assertEquals("乾隆皇帝", s("乾隆皇帝")) // 詞表裡有：不能變成「干隆」
        assertEquals("饼干很干", s("餅乾很乾"))
        assertEquals("猫爪键盘🐾 ㄇㄠ abc", s("貓爪鍵盤🐾 ㄇㄠ abc")) // 注音、英文、表情不動
        assertEquals("", s(""))
    }

    @Test fun dedupeKeepsOriginalIndex() {
        ChineseConvert.ensure(ctx)
        val (shown, index) = ChineseConvert.dedupe(listOf("髮", "發", "法", "罰", "伐")) { ChineseConvert.toSimplified(it) }
        assertEquals(listOf("发", "法", "罚", "伐"), shown)
        assertEquals(listOf(0, 2, 3, 4), index)
    }

    @Test fun toggleKeyInExpandedPanel() {
        val p = Prefs(ctx)
        p.theme = "classic"
        p.simplified = true
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.candidates = listOf("头发", "头", "投", "透", "偷", "头发很长", "图", "土", "吐", "兔", "途", "突", "涂", "徒")
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val expanded = CatKeyboardView::class.java.getDeclaredField("expanded").apply { isAccessible = true }
        expanded.set(kv, true)
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/sc-toggle-panel.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        var toggled = 0
        var picked = -1
        kv.listener = object : TestListener() {
            override fun onCandidate(index: Int) { picked = index }
            override fun onToggleSimplified() { toggled++ }
        }
        val d = 2.625f
        fun tap(x: Float, y: Float) {
            val t = SystemClock.uptimeMillis()
            kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
            kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
        }
        // 左下角：面板最下面（下方留白 10dp 上面那一格）
        val bottomY = kv.measuredHeight - 10 * d - 45 * 0.95f * d / 2
        tap(40f, bottomY)
        assertEquals(1, toggled)
        assertEquals(-1, picked) // 不會選到字
        // 左邊那一欄的上面：沒作用
        tap(40f, 34 * d + 40 * d + 30 * d)
        assertEquals(1, toggled)
        // 點第一個候選字（左邊那欄右邊）
        tap(1080f / 7 + 60f, 34 * d + 40 * d + 30 * d)
        assertEquals(0, picked)

        // 設定裡關掉切換鍵：沒有左邊那一欄，同一個位置變成候選字
        p.showScToggle = false
        kv.reloadPrefs()
        kv.candidates = listOf("頭", "投")
        expanded.set(kv, true)
        tap(40f, 34 * d + 40 * d + 30 * d)
        assertEquals(1, toggled)
        assertEquals(0, picked)
    }
}

