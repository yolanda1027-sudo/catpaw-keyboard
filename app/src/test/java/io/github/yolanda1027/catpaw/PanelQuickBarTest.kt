package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 展開選字面板時，快捷符號列不能疊在上面 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PanelQuickBarTest {
    @Test fun expandedPanelHidesQuickBar() {
        val ctx = RuntimeEnvironment.getApplication()
        val p = Prefs(ctx)
        p.theme = "classic"
        fun draw(withQuick: Boolean): Bitmap {
            val kv = CatKeyboardView(ctx, p, Haptics(ctx))
            kv.quickItems = if (withQuick) listOf("()", "「」", "⇥") else emptyList()
            kv.candidates = listOf("玉", "浴", "遇", "育", "預", "域", "愈", "欲", "籲", "慾", "譽", "裕")
            kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
            CatKeyboardView::class.java.getDeclaredField("expanded").apply { isAccessible = true }.set(kv, true)
            val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
            kv.draw(Canvas(bmp))
            return bmp
        }
        val b = draw(true)
        File("build/renders").mkdirs()
        File("build/renders/panel-with-quickbar.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 展開時，有快捷列和沒有快捷列畫出來要一模一樣（快捷列那一條不能有東西冒出來）
        val d = 2.625f
        val y = ((34 + 40 + 20) * d).toInt() // 透明區 + 上方列 + 快捷列的一半
        val a = draw(false)
        var diff = 0
        for (x in 0 until b.width step 3) if (a.getPixel(x, y) != b.getPixel(x, y)) diff++
        assertEquals(0, diff)
    }
}
