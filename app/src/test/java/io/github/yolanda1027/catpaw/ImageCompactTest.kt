package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** 設定「圖片存法」：原圖畫質（PNG）／省空間（WebP，大張有損、小張無損） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageCompactTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        Prefs(ctx).imageCompact = false
    }

    /** 像照片的圖：漸層加雜點（PNG 很難壓） */
    private fun photo(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        c.drawPaint(Paint().apply { shader = RadialGradient(w / 3f, h / 3f, w.toFloat(), Color.rgb(255, 200, 120), Color.rgb(60, 30, 90), Shader.TileMode.CLAMP) })
        val r = java.util.Random(1)
        val p = Paint()
        repeat(w * h / 6) { p.color = Color.argb(60, r.nextInt(256), r.nextInt(256), r.nextInt(256)); c.drawPoint(r.nextInt(w).toFloat(), r.nextInt(h).toFloat(), p) }
    }

    /** 去背的小圖案：粉紅圓形，四周透明 */
    private fun sticker(size: Int) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
        Canvas(b).drawCircle(size / 2f, size / 2f, size * 0.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 120, 160) })
    }

    private fun bytesOf(name: String) = File(Decals.dir(ctx), name).readBytes()

    @Test fun defaultStaysPng() {
        val n = Decals.storeBitmap(ctx, photo(1200, 800), Decals.FILL_MAX_PX)
        assertTrue(n, n.endsWith(".png"))
    }

    @Test fun compactUsesWebp() {
        Prefs(ctx).imageCompact = true
        val big = photo(1200, 800)
        val png = ByteArrayOutputStream().also { big.compress(Bitmap.CompressFormat.PNG, 100, it) }.size()
        val n = Decals.storeBitmap(ctx, big, Decals.FILL_MAX_PX)
        assertTrue(n, n.endsWith(".webp") && Decals.safeName(n))
        val webp = bytesOf(n)
        assertTrue("大張的照片小很多：PNG $png → WebP ${webp.size}", webp.size * 3 < png)
        assertEquals(1200, BitmapFactory.decodeByteArray(webp, 0, webp.size).width)

        // 小圖案：無損，每個像素都一樣，透明的地方還是透明
        val small = sticker(300)
        val m = Decals.storeBitmap(ctx, small)
        val back = BitmapFactory.decodeFile(File(Decals.dir(ctx), m).path)
        assertTrue(m.endsWith(".webp"))
        assertEquals(0, Color.alpha(back.getPixel(3, 3)))
        for ((x, y) in listOf(150 to 150, 150 to 32, 40 to 150)) assertEquals("($x,$y) 不變", small.getPixel(x, y), back.getPixel(x, y))
    }

    @Test fun webpFromThemeFileIsNotCompressedAgain() {
        Prefs(ctx).imageCompact = true
        val b = ByteArrayOutputStream().also { photo(900, 600).compress(Bitmap.CompressFormat.WEBP_LOSSY, 95, it) }.toByteArray()
        val n = Decals.storeBytes(ctx, b, keepGif = true, maxPx = Decals.FILL_MAX_PX)!!
        assertTrue(bytesOf(n).contentEquals(b))
    }

    @Test fun compactExportShrinksAndOldNamesStillWork() {
        val prefs = Prefs(ctx)
        // 以前（原圖畫質）存的大張背景圖案
        prefs.imageCompact = false
        val name = Decals.storeBitmap(ctx, photo(1200, 800), Decals.FILL_MAX_PX)
        Decals.save(prefs, listOf(Decal("d1", "@bg", Decal.FILE + name)))
        val pngSize = bytesOf(name).size
        // 改成省空間再匯出：檔案裡的圖變 WebP，手機裡的原檔不動
        prefs.imageCompact = true
        val text = ThemeIO.export(ctx, prefs, "測試", ThemeIO.Part.values().toSet())
        assertTrue(bytesOf(name).size == pngSize)
        val o = ThemeIO.parse(text)!!
        val img = o.getJSONObject("decals").getJSONObject("images").getString(name)
        val webp = Base64.decode(img, Base64.DEFAULT)
        assertTrue(Decals.isWebp(webp))
        assertTrue("匯出的圖變小：$pngSize → ${webp.size}", webp.size * 3 < pngSize)

        // 手機裡本來就是 WebP 的圖：主題檔裡改叫 .png（舊版 App 才不會丟掉），匯入回來照樣讀得到
        val w = Decals.storeBitmap(ctx, sticker(300))
        assertTrue(w.endsWith(".webp"))
        Decals.save(prefs, listOf(Decal("d2", "@bg", Decal.FILE + w)))
        val text2 = ThemeIO.export(ctx, prefs, "測試2", ThemeIO.Part.values().toSet())
        assertFalse("主題檔裡沒有 .webp 的名字", text2.contains(".webp"))
        Decals.save(prefs, emptyList())
        ThemeIO.apply(ctx, prefs, ThemeIO.parse(text2)!!)
        val got = Decals.load(prefs).single()
        val f = File(Decals.dir(ctx), got.fileName)
        assertTrue(f.exists())
        assertEquals(300, BitmapFactory.decodeFile(f.path).width)
    }
}
