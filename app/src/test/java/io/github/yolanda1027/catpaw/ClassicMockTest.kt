package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 經典深色改版的試做圖（A 夜貓粉、B 可可奶茶、C 午夜藍線框），挑好之後會換成正式的 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClassicMockTest {
    @Test fun render() {
        val ctx = RuntimeEnvironment.getApplication()
        for (t in listOf("classic", "classic_a", "classic_b", "classic_c")) for (zh in listOf(true, false)) {
            val prefs = Prefs(ctx)
            prefs.theme = t
            val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
            v.rows = if (zh) Layouts.ZHUYIN else Layouts.ENGLISH
            v.isChinese = zh
            if (!zh) v.candidates = listOf("hello", "help", "held", "helping")
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val b = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b))
            File("build/renders").mkdirs()
            File("build/renders/mock-$t-${if (zh) "zh" else "en"}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
