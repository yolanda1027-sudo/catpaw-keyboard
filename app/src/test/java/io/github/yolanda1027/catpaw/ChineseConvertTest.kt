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

    @Test fun toggleKeyOnTopBar() {
        val p = Prefs(ctx)
        p.theme = "classic"
        p.simplified = true
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/sc-toggle.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        var toggled = 0
        kv.listener = object : CatKeyboardView.Listener {
            override fun onKey(key: Key, longPress: Boolean) {}
            override fun onTabChinese() {}
            override fun onTabEnglish() {}
            override fun onHide() {}
            override fun onCandidate(index: Int) {}
            override fun onSymbolTab(index: Int) {}
            override fun onSymbolSwipe(direction: Int) {}
            override fun onClipboard() {}
            override fun onQuick(item: String) {}
            override fun onToggleSimplified() { toggled++ }
        }
        // 「中」「En」各一格寬，第三格是「簡」；上方列在透明區（34dp）下面
        val tabW = 1080f * 0.2f // 「中」「En」一格是鍵盤寬的 1/5
        val y = 34 * 2.625f + 20 * 2.625f
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, tabW * 2.5f, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, tabW * 2.5f, y, 0))
        assertEquals(1, toggled)

        // 設定裡關掉切換鍵：按同一個地方沒反應
        p.showScToggle = false
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 100, MotionEvent.ACTION_DOWN, tabW * 2.5f, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 150, MotionEvent.ACTION_UP, tabW * 2.5f, y, 0))
        assertEquals(1, toggled)
        assertTrue(tabW > 0)
    }
}

