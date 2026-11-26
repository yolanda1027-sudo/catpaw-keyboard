package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 分類列：放得下照舊平均分；放不下（顏文字）就左右滑，點一下才選 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TabScrollTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val picked = ArrayList<Int>()

    @Before fun load() {
        Emojis.load(File("src/main/assets/emoji.txt").readText())
        Symbols.loadKaomoji(File("src/main/assets/kaomoji.txt").readText())
    }

    private fun board(tabs: List<String?>, sel: Int = 2): CatKeyboardView {
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.rows = Symbols.rows(List(9) { "(^^)" }, true)
        kv.symbolTabs = tabs
        kv.symbolTab = sel
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        kv.listener = object : TestListener() {
            override fun onSymbolTab(index: Int) { picked += index }
        }
        draw(kv)
        return kv
    }

    private fun draw(kv: View): Bitmap {
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        return bmp
    }

    private val y = 20f * 2.625f // 分類列中間

    private fun tap(kv: View, x: Float) {
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun swipe(kv: View, x0: Float, dx: Float) {
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x0, y, 0))
        for (i in 1..10) kv.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, x0 + dx * i / 10, y, 0))
        for (i in 1..6) kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 160 + i * 30L, MotionEvent.ACTION_MOVE, x0 + dx, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 400, MotionEvent.ACTION_UP, x0 + dx, y, 0))
    }

    private val kaoTabs get() = listOf("◁", null) + Symbols.kaomojiGroups.map { it.first }

    @Test fun roomyRowsStayEven() {
        // 一般符號、表情符號的分類列放得下：跟以前一樣平均分、不能滑
        assertFalse(board(Symbols.categories.map { it.tab }).tabsScrollable())
        assertFalse(board(listOf<String?>(null, "🖼️") + Emojis.groups.map { it.first }).tabsScrollable())
        // 平均分的時候，點哪裡就是哪一格
        val kv = board(Symbols.categories.map { it.tab })
        val n = Symbols.categories.size
        tap(kv, 1080f / n * 2.5f)
        assertEquals(listOf(2), picked)
    }

    @Test fun kaomojiRowScrolls() {
        val kv = board(kaoTabs)
        assertTrue(kv.tabsScrollable())
        // 一開始最左邊：第一格是 ◁
        tap(kv, 10f)
        assertEquals(0, picked.last())
        val before = picked.size
        // 往左滑：只是捲動，不算選
        swipe(kv, 900f, -700f)
        assertEquals(before, picked.size)
        draw(kv)
        // 捲到最右邊之後，最右邊那格是「其他」
        swipe(kv, 900f, -2000f)
        tap(kv, 1075f)
        assertEquals(kaoTabs.size - 1, picked.last())
        // 選到最後一格：重畫時那格要看得到；換回別組分類列從頭開始
        kv.symbolTab = kaoTabs.size - 1
        draw(kv)
        tap(kv, 1075f)
        assertEquals(kaoTabs.size - 1, picked.last())
        kv.symbolTabs = Symbols.categories.map { it.tab }
        draw(kv)
        tap(kv, 10f)
        assertEquals(0, picked.last())
    }

    @Test fun renderKaomojiRow() {
        val kv = board(kaoTabs, sel = 2)
        File("build/renders").mkdirs()
        File("build/renders/kaomoji-tabs.png").outputStream().use { draw(kv).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
