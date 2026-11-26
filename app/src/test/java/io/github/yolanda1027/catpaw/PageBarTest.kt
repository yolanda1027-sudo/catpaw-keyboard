package io.github.yolanda1027.catpaw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 頁數條：底線、目前那一段可以選顏色；也可以換成小圖示跟著頁數跑 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PageBarTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val green = Color.rgb(30, 200, 60)
    private val gold = Color.rgb(240, 190, 40)

    private fun png(c: Int): ByteArray {
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(c) }
        return java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun jsonAndExport() {
        val img = Decals.storeBytes(ctx, png(gold))!!
        val pb = PageBarStyle(track = green, bar = gold, thumb = img)
        val ks = KeyStyles(pageBar = pb)
        assertEquals(pb, KeyStyles.fromJson(JSONObject(ks.toJson().toString())).pageBar)
        assertTrue(img in ks.imageFiles)
        assertEquals(null, PageBarStyle.fromJson(JSONObject().put("thumb", "../x.png")))
        // 匯出、匯入：圖跟著走（換成新的檔名）
        val json = ThemeStore.withTemp(ctx, ThemeStore.factory("classic")) { t ->
            KeyStyles.save(t, ks)
            ThemeIO.export(ctx, t, "頁數條", ThemeIO.Part.values().toSet())
        }
        val back = ThemeStore.withTemp(ctx, ThemeStore.factory("classic")) { t ->
            ThemeIO.apply(ctx, t, ThemeIO.parse(json)!!)
            KeyStyles.load(t).pageBar
        }!!
        assertEquals(green, back.track)
        assertTrue(File(Decals.dir(ctx), back.thumb!!).exists())
    }

    private fun symbolBoard(p: Prefs): Pair<CatKeyboardView, Bitmap> {
        Symbols.loadKaomoji(File("src/main/assets/kaomoji.txt").readText())
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.rows = Symbols.rows(Symbols.categories[1].pages[0], false)
        kv.symbolTabs = Symbols.categories.map { it.tab }
        kv.symbolPageCount = 3
        kv.symbolPage = 0
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        return kv to b
    }

    /** 頁數條的 y（畫面座標）：最後一排上面 */
    private fun barY(kv: CatKeyboardView): Int {
        val last = kv.rows.last().first { it.act != Act.GAP }
        return (kv.keyRectOf(last)!!.top - 1.5f * 2.625f + kv.floatH).toInt()
    }

    @Test fun drawsColorsOrThumb() {
        val p = Prefs(ctx)
        // 只改顏色：第一頁那一段（左邊 1/3）是綠色
        KeyStyles.save(p, KeyStyles(pageBar = PageBarStyle(bar = green)))
        val (kv, b) = symbolBoard(p)
        assertEquals(green, b.getPixel(180, barY(kv)))
        // 小圖示：第一頁那一段的正中間（1080 ÷ 3 ÷ 2 = 180）畫圖示，旁邊有一塊金色
        val img = Decals.storeBytes(ctx, png(gold))!!
        KeyStyles.save(p, KeyStyles(pageBar = PageBarStyle(bar = green, thumb = img)))
        val (kv2, b2) = symbolBoard(p)
        File("build/renders").mkdirs()
        File("build/renders/pagebar-thumb.png").outputStream().use { b2.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val y = barY(kv2)
        assertEquals("圖示在中間", gold, b2.getPixel(180, y - 10))
        assertTrue("有圖示時不畫綠色那段", b2.getPixel(60, y) != green)
    }


    @Test fun studioUpload() {
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        (views(a.window.decorView).first { it is Button && it.text.toString().startsWith("📍 頁數條") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        assertNotNull(views(a.window.decorView).firstOrNull { it is TextView && it.text == "目前那一段" })
        (views(a.window.decorView).first { it is Button && it.text == "📷 上傳小圖示" } as Button).performClick()
        val req = shadowOf(a).nextStartedActivityForResult
        val f = File(ctx.cacheDir, "paw.png").apply { writeBytes(png(gold)) }
        shadowOf(a).receiveResult(req.intent, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(f)))
        ShadowLooper.idleMainLooper()
        val thumb = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).pageBar?.thumb
        assertNotNull(thumb)
        assertNotNull(views(a.window.decorView).firstOrNull { it is Button && it.text == "拿掉，改回橫線" })
    }
}
