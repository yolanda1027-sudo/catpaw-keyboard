package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.RectF
import android.view.View
import android.view.WindowInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Android 15 以後鍵盤也是滿版：最下排按鍵要讓出導覽列（三顆按鈕／手勢條）的高度 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NavBarInsetTest {
    private fun layout(kv: CatKeyboardView): Bitmap {
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        return Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888).also { kv.draw(Canvas(it)) }
    }

    private fun lowestKeyBottom(kv: CatKeyboardView): Float {
        val f = CatKeyboardView::class.java.getDeclaredField("keyRects").apply { isAccessible = true }
        return (f.get(kv) as List<*>).maxOf { r -> (r!!.javaClass.getDeclaredField("rect").apply { isAccessible = true }.get(r) as RectF).bottom }
    }

    @Test fun keysMoveUpAboveNavBar() {
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        layout(kv)
        val h0 = kv.height
        val bottom0 = lowestKeyBottom(kv)

        // 系統告訴鍵盤：下面有 126px 的導覽列
        kv.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 126))
                .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 126))
                .build(),
        )
        assertEquals(126, kv.navInset)
        val bmp = layout(kv)
        assertEquals("鍵盤多出導覽列那一段", h0 + 126, kv.height)
        assertEquals("按鍵位置不變（離鍵盤頂端一樣遠），所以整個往上讓出 126px", bottom0, lowestKeyBottom(kv), 0.5f)
        // 預設：導覽列那一段塗系統原本的導覽列顏色（淺色模式是灰白色，不是系統那層灰遮罩）
        val nav = s.systemNavBarColor()
        assertEquals(nav, kv.systemNavColor)
        assertEquals(255, android.graphics.Color.alpha(nav))
        org.junit.Assert.assertTrue("淺色模式要是淺的", android.graphics.Color.luminance(nav) > 0.5f)
        assertEquals("預設導覽列底下是系統導覽列的顏色", nav, bmp.getPixel(kv.width / 2, kv.height - 10))
        assertNotEquals("鍵盤本體照樣有背景", 0, android.graphics.Color.alpha(bmp.getPixel(kv.width / 2, kv.height - 126 - 5)))
        File("build/renders").mkdirs()
        File("build/renders/navbar-inset.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 設定打開「導覽列也塗鍵盤底色」
        Prefs(kv.context).navBarKeyboardBg = true
        kv.reloadPrefs()
        val painted = layout(kv)
        assertNotEquals("導覽列底下要有鍵盤背景", 0, android.graphics.Color.alpha(painted.getPixel(kv.width / 2, kv.height - 10)))
        Prefs(kv.context).navBarKeyboardBg = false

        // 導覽列只是一閃而過地藏起來（還佔著位置）：按鍵不能掉到它後面
        kv.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 126))
                .setVisible(WindowInsets.Type.navigationBars(), false)
                .build(),
        )
        assertEquals(126, kv.navInset)
        // 根本沒有導覽列：恢復原本高度
        kv.dispatchApplyWindowInsets(WindowInsets.Builder().build())
        layout(kv)
        assertEquals(h0, kv.height)
    }

    /** 手勢提示條關掉時：系統在鍵盤底下畫「切換輸入法」「收起鍵盤」，回報成可以點的區域 */
    @Test fun leavesRoomForImeButtonsWhenGestureHintHidden() {
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        Prefs(kv.context).bottomPadDp = 0
        // 導覽列 0（手勢提示條關掉），但下面有 84px 可以點的系統按鈕
        kv.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.tappableElement(), Insets.of(0, 0, 0, 84))
                .build(),
        )
        assertEquals("讓出系統按鈕的高度", 84, kv.navInset)
        // 手勢提示條開著：導覽列 42、沒有可以點的按鈕 → 跟以前一樣讓 42
        kv.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 42))
                .build(),
        )
        assertEquals(42, kv.navInset)
        // 設定裡手動多留 20dp
        Prefs(kv.context).bottomPadDp = 20
        val density = kv.context.resources.displayMetrics.density
        kv.dispatchApplyWindowInsets(WindowInsets.Builder().build())
        assertEquals((20 * density).toInt(), kv.navInset)
        Prefs(kv.context).bottomPadDp = 0
    }
}
