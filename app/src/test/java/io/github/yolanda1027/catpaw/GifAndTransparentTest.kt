package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class GifAndTransparentTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val gif: ByteArray get() = javaClass.classLoader!!.getResourceAsStream("anim.gif").readBytes()

    @Test fun gifKeptForEffectsButNotForDecals() {
        assertTrue(Decals.isGif(gif))
        val forEffect = Decals.storeBytes(ctx, gif, keepGif = true)!!
        assertTrue(forEffect, forEffect.endsWith(".gif"))
        assertArrayEquals(gif, Decals.fileBytes(ctx, forEffect)) // 原檔照存，才會動
        val forDecal = Decals.storeBytes(ctx, gif)!!
        assertTrue(forDecal.endsWith(".png"))
        // 縮圖（清單、選圖案的地方）看得到第一格
        val bmp = Decals.bitmaps(ctx, listOf(Decal("x", Decal.BG, Decal.FILE + forEffect)))[forEffect]
        assertNotNull(bmp)
        assertEquals(48, bmp!!.width)
        // 檔名規則接受 .gif
        assertEquals(Decal.FILE + forEffect, CustomEffect.fromJson(CustomEffect(particle = Decal.FILE + forEffect).toJson())!!.particle)
    }

    @Test fun themeFileKeepsGif() {
        val p = Prefs(ctx)
        val name = Decals.storeBytes(ctx, gif, keepGif = true)!!
        p.pressEffect = "custom"
        p.customEffect = CustomEffect("burst", Decal.FILE + name)
        val json = ThemeIO.export(ctx, p, "gif", setOf(ThemeIO.Part.EFFECT))
        p.sp.edit().clear().commit()
        Decals.cleanup(ctx, emptyList())
        ThemeIO.apply(ctx, p, ThemeIO.parse(json)!!)
        val back = Prefs(ctx).customEffect
        assertTrue(back.fileName.endsWith(".gif"))
        assertArrayEquals(gif, Decals.fileBytes(ctx, back.fileName))
    }

    @Test fun gifPlaysInPressEffect() {
        val p = Prefs(ctx)
        val name = Decals.storeBytes(ctx, gif, keepGif = true)!!
        p.pressEffect = "custom"
        p.customEffect = CustomEffect("pop", Decal.FILE + name, count = 1, size = 20f)
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        val m = CatKeyboardView::class.java.getDeclaredMethod("effectAnim", String::class.java).apply { isAccessible = true }
        val d = m.invoke(kv, name)
        // 兩格的 GIF 要讀成會動的圖
        assertTrue("讀到的是 $d", d is android.graphics.drawable.AnimatedImageDrawable)
    }

    @Test fun transparentBodyAndBorder() {
        val st = KeyStyle(bg = Color.TRANSPARENT, border = Color.TRANSPARENT)
        assertEquals(st, KeyStyle.fromJson(JSONObject(st.toJson().toString())))
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        KeyStyles.save(p, KeyStyles.EMPTY
            .with("zy:ㄍ", KeyStyle(border = Color.TRANSPARENT))
            .with("zy:ㄐ", KeyStyle(bg = Color.TRANSPARENT, border = Color.TRANSPARENT))
            .with("backspace", KeyStyle(bg = Color.TRANSPARENT, border = Color.TRANSPARENT)))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/transparent-keys.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // ㄐ 沒有底框：按鍵中間偏左上（沒有字的地方）要是鍵盤背景的顏色
        val r = kv.keyRectOf(Layouts.ZHUYIN[1][3])!!
        val px = bmp.getPixel((r.left + r.width() * 0.15f).toInt(), (r.top + r.height() * 0.5f + 34 * 2.625f).toInt())
        val bg = bmp.getPixel((r.left - 1).toInt().coerceAtLeast(0), (r.top + 34 * 2.625f - 2).toInt())
        assertTrue(Color.red(px) < 120) // 黃黑主題的背景是深色（羊皮紙底是淺色）
        assertTrue(bg != 0)
    }
}
