package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import org.json.JSONObject
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
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ShapeStyleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val pink = 0xFFF7A8C0.toInt()

    @Test fun jsonRoundTrip() {
        val styled = Decal("a", "zy:ㄅ", "shape:roundrect").withStyle(ShapeStyle(Color.TRANSPARENT, Color.WHITE, 0.08f, "stitch"))
        assertEquals(styled, Decal.fromJson(JSONObject(styled.toJson().toString())))
        // 舊的圖案（沒調過樣式）讀回來一樣是舊的樣子
        val old = Decal("b", "zy:ㄅ", "shape:catline", color = Color.RED)
        val back = Decal.fromJson(JSONObject(old.toJson().toString()))!!
        assertEquals(old, back)
        assertEquals(ShapeStyle(Color.TRANSPARENT, Color.RED, 0.045f), back.shapeStyle())
        assertNull(Decal("c", "zy:ㄅ", "shape:heart").shapeStyle().outline)
        assertEquals(15, Decals.SHAPES.size)
    }

    /** 15 種圖案＋幾種樣式排成一張圖 */
    @Test fun renderGallery() {
        val cell = 130f
        val styles = listOf(
            ShapeStyle(pink, null),
            ShapeStyle(Color.TRANSPARENT, 0xFFE0688E.toInt(), 0.07f, "solid"),
            ShapeStyle(0xFFFFE08A.toInt(), 0xFF8C6BD1.toInt(), 0.06f, "dash"),
            ShapeStyle(0xFFB9E4C9.toInt(), 0xFF3D6B55.toInt(), 0.07f, "dot"),
            ShapeStyle(pink, Color.WHITE, 0.05f, "stitch"),
        )
        val bmp = Bitmap.createBitmap((cell * 15).toInt(), (cell * styles.size).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFDF0F5.toInt())
        Decals.SHAPES.forEachIndexed { i, (name, _) ->
            styles.forEachIndexed { j, st ->
                val r = RectF(i * cell + 15, j * cell + 15, (i + 1) * cell - 15, (j + 1) * cell - 15)
                DecalShapes.draw(c, name, r, st, 1f)
            }
        }
        File("build/renders").mkdirs()
        File("build/renders/shape-gallery.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 用新功能在經典主題上做出「粉紅縫線」的感覺：每顆注音鍵貼一個圓角方形（粉紅底＋白色縫線），拉成按鍵大小，壓在字下面 */
    @Test fun makeStitchKeysYourself() {
        val p = Prefs(ctx)
        p.theme = "classic"
        val keys = Layouts.ZHUYIN.take(4).flatten()
        val list = keys.map { k ->
            Decal("s_" + KeyStyles.idOf(k), KeyStyles.idOf(k), "shape:roundrect", x = 0.5f, y = 0.5f, size = 0.86f, stretch = 1.05f)
                .withStyle(ShapeStyle(pink, Color.WHITE, 0.045f, "stitch"))
        }
        Decals.save(p, list)
        // 圖案壓到字下面、字改成深粉紅
        var styles = KeyStyles.EMPTY.with("@zhuyin", KeyStyle(text = 0xFFB0406A.toInt()))
        for (k in keys) styles = styles.with(KeyStyles.idOf(k), KeyStyle(order = listOf("s_" + KeyStyles.idOf(k), "label:main", "label:sub")))
        KeyStyles.save(p, styles)
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders/diy-stitch.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun studioShapePanel() {
        val p = Prefs(ctx)
        p.theme = "classic"
        Decals.save(p, listOf(Decal("r", "zy:ㄅ", "shape:roundrect", size = 0.86f).withStyle(ShapeStyle(pink, Color.WHITE, 0.05f, "stitch"))))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.onDecalPicked!!.invoke(Decals.load(p).first())
        ShadowLooper.idleMainLooper()
        val v = a.window.decorView
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(4200, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 4200)
        val bmp = Bitmap.createBitmap(1080, 4200, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders/studio-shape-style.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 「複製到所有注音鍵」：每顆注音鍵都有一份；再按一次是更新不是重複；上下層跟著原本的；可以一次移除 */
    @Test fun copyToAllZhuyinKeys() {
        val p = Prefs(ctx)
        p.theme = "classic"
        val b = Layouts.ZHUYIN[0][0]
        Decals.save(p, listOf(Decal("r1", "zy:ㄅ", "shape:roundrect", size = 0.86f).withStyle(ShapeStyle(pink, Color.WHITE, 0.045f, "stitch"))))
        KeyStyles.save(p, KeyStyles.EMPTY.with("zy:ㄅ", KeyStyle(order = listOf("r1", "label:main", "label:sub"))))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val cls = StudioActivity::class.java
        val kv = cls.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.onDecalPicked!!.invoke(Decals.load(p).first())
        ShadowLooper.idleMainLooper()
        val copy = cls.getDeclaredMethod("copyToGroup", Decal::class.java, Key::class.java, KeyStyles.Group::class.java).apply { isAccessible = true }
        fun now() = Decals.load(Prefs(ctx))
        val zhuyinKeys = Layouts.ZHUYIN.flatten().count { it.act == Act.ZHUYIN }

        copy.invoke(a, now().first { it.id == "r1" }, b, KeyStyles.Group.ZHUYIN)
        ShadowLooper.idleMainLooper()
        assertEquals(zhuyinKeys, now().size) // 每顆注音鍵一個（含原本那顆）
        // 原本壓在字底下 → 複製出去的也在字底下
        val x = Layouts.ZHUYIN[3][3] // ㄒ
        assertEquals("label:main", kv.elementOrder(x)[1])

        // 改原本那個的顏色，再按一次：更新，不會變兩倍
        Decals.save(Prefs(ctx), now().map { if (it.id == "r1") it.withStyle(it.shapeStyle().copy(fill = 0xFFB9E4C9.toInt())) else it })
        cls.getDeclaredField("decals").apply { isAccessible = true }.set(a, Decals.load(Prefs(ctx)))
        copy.invoke(a, now().first { it.id == "r1" }, b, KeyStyles.Group.ZHUYIN)
        ShadowLooper.idleMainLooper()
        assertEquals(zhuyinKeys, now().size)
        assertTrue(now().all { it.color == 0xFFB9E4C9.toInt() })
        render(a, "studio-copy-group")

        // 上一步：回到第一次複製後的樣子（粉紅）
        cls.getDeclaredMethod("undo").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        assertEquals(zhuyinKeys, now().size)
        assertTrue(now().filter { it.id != "r1" }.all { it.color == pink })
    }

    private fun render(a: StudioActivity, name: String) {
        val v = a.window.decorView
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
