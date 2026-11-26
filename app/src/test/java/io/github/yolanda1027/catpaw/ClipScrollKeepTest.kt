package io.github.yolanda1027.catpaw

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.RadioButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 剪貼簿：整份清單順順的上下捲（不分頁）；沒釘選的留多久可以在設定裡選 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClipScrollKeepTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val hour = 60 * 60 * 1000L

    private fun clipBoard(): Triple<CatKeyboardView, IntArray, MutableList<String>> {
        val items = List(10) { ClipHistory.Item("紀錄 $it", 0L, false) }
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.scrollRows = Layouts.CLIPS_PER_PAGE
        kv.rows = Layouts.clipRows(items)
        kv.symbolTabs = listOf("剪貼簿")
        kv.symbolPageCount = 1
        kv.symbolPage = 0
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val swiped = intArrayOf(0)
        val typed = ArrayList<String>()
        kv.listener = object : TestListener() {
            override fun onKey(key: Key, longPress: Boolean) { typed += key.text.ifEmpty { key.label } }
            override fun onSymbolSwipe(direction: Int) { swiped[0] = direction }
        }
        return Triple(kv, swiped, typed)
    }

    /** 拖一段，最後停住一下再放開（不會繼續滑） */
    private fun drag(kv: CatKeyboardView, x0: Float, y0: Float, dx: Float, dy: Float) {
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x0, y0, 0))
        for (i in 1..10) kv.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, x0 + dx * i / 10, y0 + dy * i / 10, 0))
        for (i in 1..10) kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 160 + i * 30L, MotionEvent.ACTION_MOVE, x0 + dx, y0 + dy, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 480, MotionEvent.ACTION_UP, x0 + dx, y0 + dy, 0))
    }

    private fun tap(kv: CatKeyboardView, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test fun smoothScrollNoPages() {
        val (kv, swiped, typed) = clipBoard()
        val h = kv.measuredHeight.toFloat()
        // 鍵盤高度跟一般頁一樣（10 筆不會把鍵盤撐高）
        val plain = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx)).apply {
            measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        }
        assertEquals(plain.measuredHeight, kv.measuredHeight)
        val rowH = h * 0.12f
        val firstRow = h * 0.35f
        // 沒捲：第一筆
        tap(kv, 540f, firstRow)
        assertEquals("紀錄 0", typed.last())
        // 左右滑：不動
        drag(kv, 800f, firstRow, -500f, 0f)
        assertEquals(0f, kv.gridScroll, 0.01f)
        // 往上拖一小段：跟著手指捲一小段（不是一次跳一整頁），也不會換頁
        drag(kv, 540f, h * 0.55f, 0f, -rowH)
        assertTrue("捲了 ${kv.gridScroll}", kv.gridScroll > rowH * 0.3f && kv.gridScroll < rowH * 1.2f)
        assertEquals(0, swiped[0])
        // 拖到底：最後幾筆
        drag(kv, 540f, h * 0.55f, 0f, -h * 3)
        val max = kv.gridScroll
        assertTrue(max > 0f)
        tap(kv, 540f, h * 0.55f - 1f)
        val atBottom = typed.last()
        assertTrue("拖到底點到的是 $atBottom", atBottom in listOf("紀錄 8", "紀錄 9"))
        // 最下面那排（返回之類）還在原本的位置、按得到，不會被捲上來的紀錄擋住
        typed.clear()
        tap(kv, 60f, h - 20f)
        assertTrue(typed.isNotEmpty() && !typed.last().startsWith("紀錄"))
        // 拖回最上面
        drag(kv, 540f, firstRow, 0f, h * 3)
        assertEquals(0f, kv.gridScroll, 0.01f)
        tap(kv, 540f, firstRow)
        assertEquals("紀錄 0", typed.last())
    }

    @Test fun keepTimeFromSettings() {
        val f = File(ctx.filesDir, "clips.json").apply { delete() }
        val now = 1_000_000_000L
        ClipHistory(f).apply { add("兩小時前", now - 2 * hour); add("剛剛", now) }
        // 預設 1 小時：兩小時前的被刪掉
        assertEquals(listOf("剛剛"), ClipHistory(f).all(now).map { it.text })
        ClipHistory(f).apply { add("兩小時前", now - 2 * hour) }
        // 設成 8 小時：都還在
        val p = Prefs(ctx)
        p.clipKeepHours = 8
        assertEquals(2, ClipHistory.open(ctx).all(now).size)
        // 3 天前的：8 小時刪掉，7 天留著
        ClipHistory.open(ctx).add("三天前", now - 72 * hour)
        p.clipKeepHours = 168
        assertEquals(3, ClipHistory.open(ctx).all(now).size)
        p.clipKeepHours = 8
        assertEquals(2, ClipHistory.open(ctx).all(now).size)
        // 奇怪的值：回到 1 小時
        p.sp.edit().putInt("clip_keep_hours", 5).commit()
        assertEquals(1, p.clipKeepHours)
    }


    @Test fun settingsChoices() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "clip")).setup().get()
        val radios = views(a.window.decorView).filterIsInstance<RadioButton>().map { it.text.toString() }
        assertEquals(listOf("1 小時（預設）", "8 小時", "12 小時", "1 天", "3 天", "7 天", "14 天"), radios)
        (views(a.window.decorView).first { it is RadioButton && it.text == "3 天" } as RadioButton).performClick()
        assertEquals(72, Prefs(ctx).clipKeepHours)
    }
}
