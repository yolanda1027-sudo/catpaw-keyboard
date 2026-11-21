package io.github.yolanda1027.catpaw

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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

/** 剪貼簿：上下滑換頁；沒釘選的留多久可以在設定裡選 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClipScrollKeepTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val hour = 60 * 60 * 1000L

    private fun clipBoard(): Pair<CatKeyboardView, IntArray> {
        val items = List(10) { ClipHistory.Item("紀錄 $it", 0L, false) }
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.rows = Layouts.clipRows(items, 0)
        kv.symbolTabs = listOf("剪貼簿")
        kv.symbolPageCount = 3
        kv.symbolPage = 0
        kv.verticalPaging = true
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val swiped = intArrayOf(0)
        kv.listener = object : CatKeyboardView.Listener {
            override fun onKey(key: Key, longPress: Boolean) {}
            override fun onTabChinese() {}
            override fun onTabEnglish() {}
            override fun onHide() {}
            override fun onCandidate(index: Int) {}
            override fun onSymbolTab(index: Int) {}
            override fun onSymbolSwipe(direction: Int) { swiped[0] = direction }
            override fun onClipboard() {}
            override fun onQuick(item: String) {}
        }
        return kv to swiped
    }

    private fun drag(kv: CatKeyboardView, x0: Float, y0: Float, dx: Float, dy: Float) {
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x0, y0, 0))
        for (i in 1..10) kv.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, x0 + dx * i / 10, y0 + dy * i / 10, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, x0 + dx, y0 + dy, 0))
    }

    @Test fun swipeUpForNextPage() {
        val (kv, swiped) = clipBoard()
        val y = kv.measuredHeight * 0.55f
        // 左右滑：剪貼簿不換頁
        drag(kv, 800f, y, -500f, 0f)
        assertEquals(0, swiped[0])
        // 往上滑：下一頁（比較舊的）
        drag(kv, 540f, y, 0f, -260f)
        assertEquals(1, swiped[0])
        // 往下滑：上一頁
        kv.symbolPage = 1
        swiped[0] = 0
        drag(kv, 540f, kv.measuredHeight * 0.4f, 0f, 260f)
        assertEquals(-1, swiped[0])
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

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    @Test fun settingsChoices() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "clip")).setup().get()
        val radios = views(a.window.decorView).filterIsInstance<RadioButton>().map { it.text.toString() }
        assertEquals(listOf("1 小時（預設）", "8 小時", "12 小時", "1 天", "3 天", "7 天", "14 天"), radios)
        (views(a.window.decorView).first { it is RadioButton && it.text == "3 天" } as RadioButton).performClick()
        assertEquals(72, Prefs(ctx).clipKeepHours)
    }
}
