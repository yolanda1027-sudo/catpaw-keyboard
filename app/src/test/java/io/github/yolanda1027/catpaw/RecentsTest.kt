package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RecentsTest {
    @Test fun pinReorderRemove() {
        var pins = emptyList<String>()
        var recent = listOf("😂", "🥰", "🐱", "👍")
        fun act(a: String, t: String) { Recents.apply(a, t, pins, recent, 32)!!.let { pins = it.first; recent = it.second } }
        act("pin", "🐱")
        act("pin", "👍")
        assertEquals(listOf("🐱", "👍", "😂", "🥰"), Recents.shown(pins, recent, 32)) // 釘選的在最前面
        act("left", "👍")
        assertEquals(listOf("👍", "🐱"), pins)
        act("right", "👍")
        assertEquals(listOf("🐱", "👍"), pins)
        // 最近用了別的：釘選的不會被擠下去
        recent = listOf("🎉") + recent
        assertEquals(listOf("🐱", "👍", "🎉", "😂"), Recents.shown(pins, recent, 32).take(4))
        act("unpin", "🐱")
        assertEquals(listOf("👍", "🐱", "🎉"), Recents.shown(pins, recent, 32).take(3)) // 回到最近使用最前面
        act("remove", "😂")
        assertEquals(false, "😂" in Recents.shown(pins, recent, 32))
        // 滿了不能再釘
        assertNull(Recents.apply("pin", "x", List(4) { "$it" }, emptyList(), 4))
        // 一頁放不下：釘選的優先
        assertEquals(listOf("a", "b", "c"), Recents.shown(listOf("a", "b"), listOf("c", "d"), 3))
    }

    /** 鍵盤上：長按跳出動作選單，直接放開＝第一個動作；釘選的畫小圖釘 */
    @Test fun longPressShowsActionMenu() {
        val ctx = RuntimeEnvironment.getApplication()
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        val items = listOf("🐱", "👍", "😂", "🥰", "🎉")
        kv.rows = Emojis.rows(items)
        kv.symbolTabs = listOf(null, "😀")
        kv.symbolTab = 0
        kv.itemActions = { k -> if (k.text == "🐱") listOf("unpin" to "解除釘選", "left" to "往前", "cancel" to "取消", "right" to "往後", "remove" to "刪除") else listOf("pin" to "釘選", "cancel" to "取消", "remove" to "刪除") }
        kv.isPinned = { k -> k.text == "🐱" }
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        var got: Pair<String, String>? = null
        var typed: String? = null
        kv.listener = object : CatKeyboardView.Listener {
            override fun onKey(key: Key, longPress: Boolean) { typed = key.text }
            override fun onTabChinese() {}
            override fun onTabEnglish() {}
            override fun onHide() {}
            override fun onCandidate(index: Int) {}
            override fun onSymbolTab(index: Int) {}
            override fun onSymbolSwipe(direction: Int) {}
            override fun onClipboard() {}
            override fun onQuick(item: String) {}
            override fun onItemAction(key: Key, action: String) { got = key.text to action }
        }
        val r = kv.keyRectOf(kv.rows[0][1])!! // 👍
        val y = r.centerY() + 34 * 2.625f
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.centerX(), y, 0))
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/recent-pin-menu.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 700, MotionEvent.ACTION_UP, r.centerX(), y, 0))
        assertEquals("直接放開＝取消", "👍" to "cancel", got)
        assertNull(typed) // 長按選單不會打字
    }
}
