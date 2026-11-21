package io.github.yolanda1027.catpaw

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
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** 英文字母長按：@ a A 三個選項，選哪個就照原樣打哪個（不管大寫有沒有開） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class EnglishLongPressTest {

    private fun longPressA(shift: Int, slideToX: Float?): Key? {
        val ctx = RuntimeEnvironment.getApplication()
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.rows = Layouts.ENGLISH
        kv.isChinese = false
        kv.shiftState = shift
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        var got: Key? = null
        kv.listener = object : CatKeyboardView.Listener {
            override fun onKey(key: Key, longPress: Boolean) { got = key }
            override fun onTabChinese() {}
            override fun onTabEnglish() {}
            override fun onHide() {}
            override fun onCandidate(index: Int) {}
            override fun onSymbolTab(index: Int) {}
            override fun onSymbolSwipe(direction: Int) {}
            override fun onClipboard() {}
            override fun onQuick(item: String) {}
        }
        // a：第二排第一顆（前面空半格），中心 x = 108
        val rowEn = 45f * 5 / 4 * 2.625f
        val y = 89f + 105f + 16f + rowEn * 1.5f
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 108f, y, 0))
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        val x = slideToX ?: 108f
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 700, MotionEvent.ACTION_MOVE, x, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 750, MotionEvent.ACTION_UP, x, y, 0))
        return got
    }

    @Test fun releaseRightAwayTypesTheSameLetter() {
        // 長按直接放開：打原本的字（小寫是小寫、開 Shift 是大寫）
        assertEquals("a", longPressA(0, null)?.text)
        assertEquals("A", longPressA(1, null)?.text)
    }

    @Test fun slideToUppercase() {
        // 選項：@（0–108）、a（108–216）、A（216–324）；a 在最左邊，選單被推回螢幕內
        val k = longPressA(0, 270f)!!
        assertEquals("A", k.text)
        assertTrue(k.exact)
    }

    @Test fun slideLeftToSymbol() {
        assertEquals("@", longPressA(0, 54f)?.text)
    }

    @Test fun lowercaseStaysLowercaseEvenWithShift() {
        // 開大寫時選項變成 @ A a；選最右邊的 a，打出來就是小寫（exact = true，服務不會再轉大寫）
        val k = longPressA(1, 270f)!!
        assertEquals("a", k.text)
        assertTrue(k.exact)
    }
}
