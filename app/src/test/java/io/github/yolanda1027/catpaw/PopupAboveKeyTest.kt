package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 長按選單整個浮在按鍵上面（像 Google 注音），手指不會擋到選項 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PopupAboveKeyTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun longPress(kv: CatKeyboardView, key: Key): RectF {
        val r = kv.keyRectOf(key)!!
        val t = SystemClock.uptimeMillis()
        // 觸控座標要加上最上面放注音小框框的那條
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY() + kv.floatH, 0))
        ShadowLooper.idleMainLooper(800, java.util.concurrent.TimeUnit.MILLISECONDS)
        val pr = CatKeyboardView::class.java.getDeclaredField("popupRect").apply { isAccessible = true }.get(kv) as RectF
        assertNotNull(CatKeyboardView::class.java.getDeclaredField("popup").apply { isAccessible = true }.get(kv))
        return pr
    }

    @Test fun popupSitsAboveTheKey() {
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val e = Layouts.ZHUYIN.flatten().first { it.label == "ㄜ" }
        val pr = longPress(kv, e)
        val key = kv.keyRectOf(e)!!
        assertTrue("選單底部在按鍵上面：${pr.bottom} < ${key.top}", pr.bottom < key.top)
        val b = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        File("build/renders").mkdirs()
        File("build/renders/popup-above-key.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 最上面一排也放得下（往上伸到注音小框框那條，不會被切掉）
        kv.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))
        val bo = Layouts.ZHUYIN[0][0]
        val top = longPress(kv, bo)
        assertTrue(top.bottom < kv.keyRectOf(bo)!!.top)
        assertTrue("沒有超出畫面", top.top >= -kv.floatH)
    }
}
