package io.github.yolanda1027.catpaw

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
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

    @Test fun arrowsMoveKeyLabel() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val xKey = Layouts.ZHUYIN[3][3] // ㄒ（小字 v）
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.editPicker!!.invoke(xKey)
        ShadowLooper.idleMainLooper()
        // 選到小字 v
        StudioActivity::class.java.getDeclaredField("decalSel").apply { isAccessible = true }.set(a, "label:sub")
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        fun arrow(desc: String) = views(a.window.decorView).first { it is Button && it.contentDescription == desc }
        fun cur() = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map["zy:ㄒ"]?.labels?.get("sub") ?: LabelStyle()
        fun subX() = kv.keyElements(xKey).first { it.decal.id == "label:sub" }.decal.x
        val x0 = subX()
        press(arrow("往右移"), 50)
        assertTrue("dx = ${cur().dx}", cur().dx > 0f && cur().dx < 0.05f)
        assertEquals(0f, cur().dy, 0.0001f)
        assertTrue(subX() > x0)
        // 主字沒動
        assertTrue(KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map["zy:ㄒ"]?.labels?.get("main") == null)
        // 按住往下：一直移
        press(arrow("往下移"), 1000)
        assertTrue("dy = ${cur().dy}", cur().dy > 0.1f)
        // 面板還在編輯這個小字
        assertTrue(views(a.window.decorView).any { it is android.widget.TextView && it.text.toString().startsWith("正在編輯：小字") })
    }

    @Test fun arrowsMoveWholeGroupLabels() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val xKey = Layouts.ZHUYIN[3][3]
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.editPicker!!.invoke(xKey)
        ShadowLooper.idleMainLooper()
        // 切到「整類一起改」
        StudioActivity::class.java.getDeclaredField("groupScope").apply { isAccessible = true }.set(a, true)
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        val gid = KeyStyles.groupIdOf(xKey)
        fun label(n: String) = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map[gid]?.labels?.get(n) ?: LabelStyle()
        fun arrows(desc: String) = views(a.window.decorView).filter { it is Button && it.contentDescription == desc }
        // 主字、小字各有一組方向鍵
        assertEquals(2, arrows("往右移").size)
        press(arrows("往右移")[0], 50)
        assertTrue("主字 dx = ${label("main").dx}", label("main").dx > 0f && label("main").dx < 0.05f)
        assertEquals(0f, label("sub").dx, 0.0001f)
        // 小字往上按住：一直移，最多到 -50%
        press(arrows("往上移")[1], 3000)
        assertTrue("小字 dy = ${label("sub").dy}", label("sub").dy < -0.1f && label("sub").dy >= -0.5f)
        // 上面的滑桿也跟著更新
        assertTrue(views(a.window.decorView).any { it is android.widget.TextView && it.text.toString().startsWith("左右（往右是 +）：+") })
    }
}
