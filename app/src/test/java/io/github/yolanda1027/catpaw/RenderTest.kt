package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 把鍵盤畫成 PNG（存到 app/build/renders/），不用手機也能檢查長相。
 * 螢幕設定成跟 S24 Ultra 預設解析度一樣：寬 1080px。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w384dp-h832dp-450dpi")
class RenderTest {

    private fun render(theme: String, layout: List<List<Key>>, name: String, pressAt: Pair<Float, Float>? = null, composing: String = "") {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        prefs.theme = theme
        val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
        v.rows = layout
        v.isChinese = layout === Layouts.ZHUYIN
        v.composing = composing
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        if (pressAt != null) {
            val t = SystemClock.uptimeMillis()
            v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pressAt.first, pressAt.second, 0))
        }
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = File("build/renders").apply { mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(f.length() > 0)
    }

    @Test
    fun renderAll() {
        for (t in listOf("classic", "catpaw")) {
            render(t, Layouts.ZHUYIN, "$t-zhuyin")
            render(t, Layouts.ZHUYIN, "$t-zhuyin-pressed", pressAt = 270f to 330f, composing = "ㄇㄠ")
            render(t, Layouts.ENGLISH, "$t-english")
            render(t, Layouts.SYMBOLS, "$t-symbols")
            render(t, Layouts.EMOJI, "$t-emoji")
        }
    }
}
