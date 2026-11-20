package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 圖案框的下面（旋轉把手的對面）有移動把手：圖案太小、手指會蓋住時用它拖 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class MoveHandleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dp = 2.625f

    private fun ev(action: Int, x: Float, y: Float): MotionEvent {
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, x, y, 0)
    }

    /** 拖移動把手（在框下面：中心往下 半個框高＋5dp 留邊＋30dp） */
    private fun dragMoveHandle(rotation: Float): Pair<Decal, Decal> {
        val h = DecalHandles(dp)
        val target = RectF(0f, 0f, 1080f, 700f)
        val d = Decal("d1", Decal.BG, "shape:star", x = 0.5f, y = 0.6f, size = 0.05f, rotation = rotation)
        val g = DecalHandles.Geo(target, target.height(), null)
        // 先畫一次（工具列的位置要畫過才知道）
        h.draw(Canvas(Bitmap.createBitmap(1080, 700, Bitmap.Config.ARGB_8888)), d, g, 1080f, 700f)
        val (cx, cy) = Decals.centerOf(d, target)
        val (_, sh) = Decals.sizeOf(d, target.height(), null)
        val dist = sh / 2 + 5 * dp + 30 * dp
        val a = Math.toRadians(rotation.toDouble())
        // 圖案自己座標的 (0, dist) 轉到畫面上
        val mx = (cx - dist * Math.sin(a)).toFloat()
        val my = (cy + dist * Math.cos(a)).toFloat()
        var last: Decal? = null
        // 拖的過程中最後一次的樣子（放開時會再用 current() 拿「最新的」報一次；這裡 current 回傳舊的，所以不看那次）
        h.onChange = { nd, done -> if (!done) last = nd }
        val pick = { _: Float, _: Float -> null as Decal? }
        h.onTouch(ev(MotionEvent.ACTION_DOWN, mx, my), d, { g }, pick, { d })
        h.onTouch(ev(MotionEvent.ACTION_MOVE, mx + 108f, my - 70f), d, { g }, pick, { d })
        h.onTouch(ev(MotionEvent.ACTION_UP, mx + 108f, my - 70f), d, { g }, pick, { d })
        return d to last!!
    }

    @Test fun dragMovesTheDecal() {
        val (d, moved) = dragMoveHandle(0f)
        // 往右 108 / 1080 = 0.1；往上 70 / 700 = 0.1
        assertEquals(d.x + 0.1f, moved.x, 0.001f)
        assertEquals(d.y - 0.1f, moved.y, 0.001f)
        // 只有移動：大小、旋轉都沒變
        assertEquals(d.size, moved.size, 0.0001f)
        assertEquals(d.rotation, moved.rotation, 0.0001f)
    }

    @Test fun handleTurnsWithTheDecal() {
        // 轉 90 度：移動把手跑到右邊（旋轉把手在左邊），一樣拖得動
        val (d, moved) = dragMoveHandle(90f)
        assertEquals(d.x + 0.1f, moved.x, 0.001f)
        assertEquals(d.rotation, moved.rotation, 0.0001f)
    }

    @Test fun screenshotSmallDecal() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        Decals.save(p, listOf(Decal("d1", Decal.BG, "shape:star", x = 0.45f, y = 0.55f, size = 0.06f, front = true, color = 0xFFF0C23B.toInt())))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.onDecalPicked!!.invoke(Decals.load(work_p).first())
        ShadowLooper.idleMainLooper()
        assertNotNull(kv.decalEditId)
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        File("build/renders").mkdirs()
        File("build/renders/move-handle.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
