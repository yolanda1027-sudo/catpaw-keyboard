package io.github.yolanda1027.catpaw

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** 工作室「微調」：← ↑ ↓ → 按一下移一點點，按住一直移；放開才存檔 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NudgeTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    private fun press(b: View, holdMs: Long) {
        val t = SystemClock.uptimeMillis()
        b.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        ShadowLooper.idleMainLooper(holdMs, TimeUnit.MILLISECONDS)
        b.dispatchTouchEvent(MotionEvent.obtain(t, t + holdMs, MotionEvent.ACTION_UP, 5f, 5f, 0))
        ShadowLooper.idleMainLooper()
    }

    @Test fun arrowsMoveTheDecal() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        Decals.save(p, listOf(Decal("d1", Decal.BG, "shape:star", x = 0.5f, y = 0.5f, size = 0.1f)))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work = Prefs(ctx, ThemeStore.WORK)
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.onDecalPicked!!.invoke(Decals.load(work).first())
        ShadowLooper.idleMainLooper()
        fun arrow(desc: String) = views(a.window.decorView).first { it is Button && it.contentDescription == desc }
        fun cur() = Decals.load(Prefs(ctx, ThemeStore.WORK)).first { it.id == "d1" }
        val x0 = cur().x
        val y0 = cur().y
        // 按一下往右：移一點點（1dp），而且存檔了
        press(arrow("往右移"), 50)
        val step = cur().x - x0
        assertTrue("一下移了 $step", step > 0f && step < 0.01f)
        assertEquals(y0, cur().y, 0.0001f)
        // 按一下往上：只有 y 變小
        press(arrow("往上移"), 50)
        assertTrue(cur().y < y0)
        assertEquals(x0 + step, cur().x, 0.0001f)
        // 按住往左 1 秒：一直移，移得比按一下多很多
        val before = cur().x
        press(arrow("往左移"), 1000)
        assertTrue("按住移了 ${before - cur().x}", before - cur().x > step * 10)
    }
}
