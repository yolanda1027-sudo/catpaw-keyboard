package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 手機橫放：工作室改成左右並排，右邊的設定面板滑得動；左邊鍵盤縮小到放得下，點按鍵照樣選得到 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-420dpi")
class StudioLandscapeTest {
    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    @Test fun sideBySide() {
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val root = a.window.decorView
        val w = 2339; val h = 900 // 比較矮：扣掉狀態列、導覽列之後，鍵盤一定放不下
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        ShadowLooper.idleMainLooper()
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val panelScroll = views(root).filterIsInstance<ScrollView>().first { sv -> views(sv).any { it === StudioActivity::class.java.getDeclaredField("panel").apply { isAccessible = true }.get(a) } }
        assertTrue("設定面板有高度：${panelScroll.height}", panelScroll.height > 400)
        val stage = kv.parent as View
        val left = stage.parent as View
        assertTrue("鍵盤沒有被裁掉：stage ${stage.height} ≥ 鍵盤 ${kv.height}", stage.height >= kv.height)
        assertTrue("整個鍵盤縮小到放得下：${kv.height} × ${stage.scaleY} ≤ ${left.height}", kv.height * stage.scaleY <= left.height + 1)
        // 截圖
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/studio-landscape.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 點畫面上縮小後的「ㄅ」：選到ㄅ
        val bo = Layouts.ZHUYIN[0][0]
        val r = kv.keyRectOf(bo)!!
        val loc = IntArray(2).also { kv.getLocationInWindow(it) }
        val k = stage.scaleY
        // kv 在 stage 裡的座標 → 縮放（以 stage 上緣中間為中心）→ 畫面
        val stageLoc = IntArray(2).also { (left).getLocationInWindow(it) }
        val sx = stageLoc[0] + stage.pivotX + (kv.left + r.centerX() - stage.pivotX) * k
        val sy = stageLoc[1] + (kv.top + r.centerY() + kv.floatH) * k
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, sx, sy, 0))
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, sx, sy, 0))
        ShadowLooper.idleMainLooper()
        val selected = StudioActivity::class.java.getDeclaredField("selected").apply { isAccessible = true }.get(a) as Key?
        assertEquals(bo, selected)
    }
}
