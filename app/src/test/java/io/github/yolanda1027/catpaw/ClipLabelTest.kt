package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 剪貼簿那幾條借一般字鍵的外觀：字鍵的字挪過位置、刪掉、設成透明時，
 * 剪貼簿的字不能跟著跑掉（以前會整個不見）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClipLabelTest {
    private val charStyle = KeyStyle(
        bg = 0xFFF3DFB0.toInt(),
        text = 0x00000000, // 透明
        hideLabel = true,
        labels = mapOf("main" to LabelStyle(dx = 0.3f, hidden = true), "sub" to LabelStyle(dy = 0.2f)),
    )

    /** 每一頁分開設計：注音／英文頁的字鍵樣式不會套到剪貼簿；剪貼簿頁自己設計的才會 */
    @Test fun clipPageHasItsOwnDesign() {
        val clip = Layouts.clipRows(listOf(ClipHistory.Item("貓爪鍵盤測試", 0L, false)), 0).onPage("clip")[0][0]
        assertEquals("clip/@clip", KeyStyles.groupIdOf(clip))
        val ks = KeyStyles(mapOf(KeyStyles.Group.CHAR.id to charStyle))
        assertNull("注音／英文頁的字鍵樣式不借給剪貼簿", ks.resolve(clip))
        val mine = KeyStyle(bg = 0xFFCCE5FF.toInt(), text = 0xFF223344.toInt())
        val ks2 = ks.with("clip/@clip", mine)
        assertEquals(mine.bg, ks2.resolve(clip)!!.bg)
        // 一般字鍵本身不受影響
        assertEquals(true, ks2.resolve(Key("a", Act.TEXT))!!.hideLabel)
    }

    @Test fun renderClipPageWithTweakedCharStyle() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        KeyStyles.save(prefs, KeyStyles(mapOf(KeyStyles.Group.CHAR.id to charStyle)))
        val kv = CatKeyboardView(ctx, prefs, Haptics(ctx))
        kv.reloadPrefs()
        kv.pageScope = "clip"
        kv.rows = Layouts.clipRows(listOf(ClipHistory.Item("貓爪鍵盤測試", 0L, false), ClipHistory.Item("第二筆", 0L, true)), 0).onPage("clip")
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val bmp = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/clip-tweaked-char.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 第一條「裡面」（避開圓角外的深色背景）要有跟底色不一樣的像素＝字
        val kr = (CatKeyboardView::class.java.getDeclaredField("keyRects").apply { isAccessible = true }.get(kv) as List<*>).first()!!
        val r = kr.javaClass.getDeclaredField("rect").apply { isAccessible = true }.get(kr) as android.graphics.RectF
        val bg = bmp.getPixel((r.right - 30).toInt(), (r.centerY() + kv.floatH).toInt())
        var ink = 0
        for (y in (r.top + kv.floatH + 20).toInt() until (r.bottom + kv.floatH - 20).toInt() step 2)
            for (x in (r.left + 30).toInt() until (r.right - 30).toInt() step 2)
                if (bmp.getPixel(x, y) != bg) ink++
        assertTrue("剪貼簿條上要看得到字（字的像素 $ink）", ink > 100)
        KeyStyles.save(prefs, KeyStyles(emptyMap()))
    }
}
