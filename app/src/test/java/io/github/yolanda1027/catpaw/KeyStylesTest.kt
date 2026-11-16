package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class KeyStylesTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val b = Layouts.ZHUYIN[0][0] // ㄅ
    private val d = Layouts.ZHUYIN[0][1] // ㄉ
    private val enter = Layouts.ZHUYIN[4].last()

    @Test fun idsAndGroups() {
        assertEquals("zy:ㄅ", KeyStyles.idOf(b))
        assertEquals("enter", KeyStyles.idOf(enter))
        assertEquals(KeyStyles.Group.ZHUYIN, KeyStyles.groupOf(b))
        assertEquals(KeyStyles.Group.ENTER, KeyStyles.groupOf(enter))
        assertEquals(KeyStyles.Group.CHAR, KeyStyles.groupOf(Layouts.ENGLISH[1][0]))
        assertEquals("「ㄅ」鍵", KeyStyles.nameOf(b))
    }

    @Test fun ownStyleWinsOverGroup() {
        val s = KeyStyles.EMPTY
            .with("@zhuyin", KeyStyle(bg = Color.YELLOW, radius = 0.5f))
            .with("zy:ㄅ", KeyStyle(bg = Color.RED, hideLabel = true))
        assertEquals(KeyStyle(bg = Color.RED, radius = 0.5f, hideLabel = true), s.resolve(b))
        assertEquals(KeyStyle(bg = Color.YELLOW, radius = 0.5f), s.resolve(d))
        assertNull(s.resolve(enter))
        // 清掉（空的樣式）就移除
        assertTrue(s.with("zy:ㄅ", KeyStyle()).map.keys == setOf("@zhuyin"))
    }

    @Test fun savesAndLoads() {
        val p = Prefs(ctx)
        val s = KeyStyles.EMPTY
            .with("@zhuyin", KeyStyle(bg = 0xFFFFE08A.toInt(), text = 0xFF6B4F2A.toInt(), radius = 0.42f))
            .with("enter", KeyStyle(border = 0xFFFF8FA9.toInt(), hideLabel = false))
        KeyStyles.save(p, s)
        val back = KeyStyles.load(Prefs(ctx))
        assertEquals(s.map, back.map)
    }

    private fun render(v: View, name: String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun renderStyledKeyboard() {
        val p = Prefs(ctx)
        p.theme = "classic"
        KeyStyles.save(p, KeyStyles.EMPTY
            .with("@zhuyin", KeyStyle(bg = 0xFF37474F.toInt(), radius = 0.35f))
            .with("zy:ㄅ", KeyStyle(bg = 0xFFFFE08A.toInt(), text = 0xFF6B4F2A.toInt(), border = 0xFFFF8FA9.toInt(), radius = 1f))
            .with("zy:ㄆ", KeyStyle(hideLabel = true, border = 0xFF80CBC4.toInt()))
            .with("enter", KeyStyle(bg = 0xFFFF8FA9.toInt(), text = Color.WHITE, radius = 0.6f))
            .with("backspace", KeyStyle(text = 0xFFFFB3C4.toInt())))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.editHighlight = { KeyStyles.idOf(it) == "zy:ㄅ" }
        render(kv, "keystyles-classic")

        p.theme = "macaron_butter"
        kv.reloadPrefs()
        kv.editHighlight = null
        render(kv, "keystyles-butter")
    }

    @Test fun samplesCurrentColors() {
        val p = Prefs(ctx)
        p.theme = "classic"
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val c = kv.sampleColors(b)!!
        assertEquals(Color.parseColor("#273238"), c.bg)
        assertEquals(Color.parseColor("#D3D7DA"), c.text)
        // 自訂過的顏色，量到的就是自訂的顏色
        KeyStyles.save(p, KeyStyles.EMPTY.with("@zhuyin", KeyStyle(bg = 0xFFFFE08A.toInt(), text = 0xFF6B4F2A.toInt())))
        kv.reloadPrefs()
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val c2 = kv.sampleColors(b)!!
        assertEquals(0xFFFFE08A.toInt(), c2.bg)
        assertEquals(0xFF6B4F2A.toInt(), c2.text)
    }

    @Test fun studioScreen() {
        val p = Prefs(ctx)
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = 0xFFFF8FA9.toInt(), radius = 0.6f)))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val f = StudioActivity::class.java.getDeclaredField("kv"); f.isAccessible = true
        val kv = f.get(a) as CatKeyboardView
        render(a.window.decorView.also { it.measure(0, 0) }.let { a.window.decorView }, "studio-empty")
        kv.editPicker!!.invoke(enter)
        render(a.window.decorView, "studio-enter")
        kv.editPicker!!.invoke(b)
        render(a.window.decorView, "studio-zhuyin")
        // 魔法學院主題：選色器的起點要是羊皮紙的顏色
        Prefs(ctx).theme = "magic_yellow"
        kv.reloadPrefs()
        kv.editPicker!!.invoke(b)
        render(a.window.decorView, "studio-magic")
        // 桌面圖示
        val d = ctx.getDrawable(R.drawable.ic_studio_fg)!!
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFF3C4.toInt())
        d.setBounds(0, 0, 432, 432)
        d.draw(c)
        File("build/renders/studio-icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
