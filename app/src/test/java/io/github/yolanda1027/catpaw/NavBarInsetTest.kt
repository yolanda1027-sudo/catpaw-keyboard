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
            WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 126)).build(),
        )
        assertEquals(126, kv.navInset)
        val bmp = layout(kv)
        assertEquals("鍵盤多出導覽列那一段", h0 + 126, kv.height)
        assertEquals("按鍵位置不變（離鍵盤頂端一樣遠），所以整個往上讓出 126px", bottom0, lowestKeyBottom(kv), 0.5f)
        // 預設：導覽列那一段不畫，讓系統照手機主題畫導覽列
        assertEquals("預設導覽列底下是透明的", 0, android.graphics.Color.alpha(bmp.getPixel(kv.width / 2, kv.height - 10)))
        assertNotEquals("鍵盤本體照樣有背景", 0, android.graphics.Color.alpha(bmp.getPixel(kv.width / 2, kv.height - 126 - 5)))
        File("build/renders").mkdirs()
        File("build/renders/navbar-inset.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 設定打開「導覽列也塗鍵盤底色」
        Prefs(kv.context).navBarKeyboardBg = true
        kv.reloadPrefs()
        val painted = layout(kv)
        assertNotEquals("導覽列底下要有鍵盤背景", 0, android.graphics.Color.alpha(painted.getPixel(kv.width / 2, kv.height - 10)))
        Prefs(kv.context).navBarKeyboardBg = false

        // 導覽列藏起來（例如全螢幕遊戲）：恢復原本高度
        kv.dispatchApplyWindowInsets(WindowInsets.Builder().build())
        layout(kv)
        assertEquals(h0, kv.height)
    }
}
