package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 長按選字 → 問「移除選字記憶？」→ 按移除／取消 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ForgetPromptTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val log = ArrayList<String>()

    private fun board(): CatKeyboardView {
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.candidates = listOf("賽的", "賽", "塞", "腮")
        kv.composing = "ㄙㄞˋㄉㄜ˙"
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        kv.listener = object : CatKeyboardView.Listener {
            override fun onKey(key: Key, longPress: Boolean) { log += "key" }
            override fun onTabChinese() {}
            override fun onTabEnglish() {}
            override fun onHide() {}
            override fun onCandidate(index: Int) { log += "pick $index" }
            override fun onSymbolTab(index: Int) {}
            override fun onSymbolSwipe(direction: Int) {}
            override fun onClipboard() {}
            override fun onQuick(item: String) {}
            override fun onCandidateLongPress(index: Int) { log += "long $index"; kv.forgetPrompt = kv.candidates[index] }
            override fun onForgetAnswer(remove: Boolean) { log += "answer $remove" }
        }
        return kv
    }

    private val topY = (34f + 20f) * 2.625f // 上面透明區 34dp，上方列中間

    private fun down(kv: View, x: Float, y: Float, t: Long) = kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
    private fun up(kv: View, x: Float, y: Float, t: Long) = kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0))

    @Test fun longPressAsksThenRemoves() {
        val kv = board()
        // 長按第一個選項：不會選字，會問要不要移除
        val t = SystemClock.uptimeMillis()
        down(kv, 60f, topY, t)
        ShadowLooper.idleMainLooper(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        up(kv, 60f, topY, t + 1500)
        assertEquals(listOf("long 0"), log)
        assertEquals("賽的", kv.forgetPrompt)
        // 畫面：上方列換成問句
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/forget-prompt.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 按「移除」（最右邊）
        val t2 = SystemClock.uptimeMillis()
        down(kv, 1080f - 40f * 2.625f, topY, t2)
        up(kv, 1080f - 40f * 2.625f, topY, t2 + 50)
        assertEquals(listOf("long 0", "answer true"), log)
        assertNull(kv.forgetPrompt)
    }

    @Test fun shortTapStillPicks() {
        val kv = board()
        val t = SystemClock.uptimeMillis()
        down(kv, 60f, topY, t)
        up(kv, 60f, topY, t + 80)
        assertEquals(listOf("pick 0"), log)
    }

    @Test fun cancelOrTapElsewhere() {
        val kv = board()
        kv.forgetPrompt = "賽的"
        // 按「取消」
        var t = SystemClock.uptimeMillis()
        down(kv, 1080f - 110f * 2.625f, topY, t)
        up(kv, 1080f - 110f * 2.625f, topY, t + 50)
        assertEquals(listOf("answer false"), log)
        assertNull(kv.forgetPrompt)
        // 再問一次，這次直接去打字：當作取消，字照常打
        kv.forgetPrompt = "賽的"
        log.clear()
        t = SystemClock.uptimeMillis()
        val y = kv.measuredHeight * 0.6f
        down(kv, 540f, y, t)
        up(kv, 540f, y, t + 50)
        assertTrue(log.toString(), log.first() == "answer false" && "key" in log)
        assertNull(kv.forgetPrompt)
    }
}
