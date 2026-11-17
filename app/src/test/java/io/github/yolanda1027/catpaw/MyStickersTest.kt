package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import org.junit.Assert.assertArrayEquals
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
class MyStickersTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val gif: ByteArray get() = javaClass.classLoader!!.getResourceAsStream("anim.gif").readBytes()

    /** 假裝是相簿的照片：一隻大大的彩色圓臉 */
    private fun photo(color: Int, w: Int = 900, h: Int = 700): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawCircle(w / 2f, h / 2f, minOf(w, h) * 0.45f, p)
        p.color = Color.WHITE
        c.drawCircle(w * 0.4f, h * 0.45f, minOf(w, h) * 0.06f, p)
        c.drawCircle(w * 0.6f, h * 0.45f, minOf(w, h) * 0.06f, p)
        return Decals.toBytes(b)
    }

    @Test fun addListDelete() {
        val a = MyStickers.add(ctx, photo(Color.parseColor("#FF8FA9")))!!
        Thread.sleep(5)
        val g = MyStickers.add(ctx, gif)!!
        assertTrue(a.endsWith(".png"))
        assertTrue(g.endsWith(".gif"))
        assertArrayEquals(gif, MyStickers.file(ctx, g)!!.readBytes()) // GIF 照原檔（會動）
        // 縮過：最大邊 512
        val b = android.graphics.BitmapFactory.decodeFile(MyStickers.file(ctx, a)!!.path)
        assertTrue(maxOf(b.width, b.height) <= MyStickers.MAX_PX)
        assertEquals(listOf(g, a), MyStickers.list(ctx)) // 新的在前面
        assertNull(MyStickers.add(ctx, "not an image".toByteArray()))
        // 鍵盤：一頁 4 排各 5 個
        val rows = MyStickers.rows(ctx, 0)
        assertEquals(Act.STICKER, rows[0][0].act)
        assertEquals(g, rows[0][0].text)
        MyStickers.delete(ctx, a)
        MyStickers.delete(ctx, g)
        assertTrue(MyStickers.list(ctx).isEmpty())
        // 沒有貼圖：放一顆「去上傳」
        assertEquals(Act.OPEN_STICKERS, MyStickers.rows(ctx, 0)[0][0].act)
    }

    /** 別的 App 透過 content:// 讀得到圖片（只讀那個資料夾裡的） */
    @Test fun providerServesTheImage() {
        Robolectric.setupContentProvider(StickerProvider::class.java, ctx.packageName + ".stickers")
        val name = MyStickers.add(ctx, gif)!!
        val uri = StickerProvider.uri(ctx, name)
        assertEquals("image/gif", ctx.contentResolver.getType(uri))
        assertArrayEquals(gif, ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        // 亂給檔名：讀不到
        val bad = android.net.Uri.parse("content://${ctx.packageName}.stickers/s/..%2Fshared_prefs%2Fcatpaw.xml")
        assertTrue(runCatching { ctx.contentResolver.openInputStream(bad)!!.close() }.isFailure)
    }

    @Test fun renderStickerBoardAndSettings() {
        val colors = listOf("#FF8FA9", "#FFD166", "#7BC6A4", "#8FB8FF", "#C9A0FF", "#FF9F6E", "#6ED3E0")
        for (c in colors) { MyStickers.add(ctx, photo(Color.parseColor(c))); Thread.sleep(3) }
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        Emojis.load(File("src/main/assets/emoji.txt").readText())
        kv.rows = MyStickers.rows(ctx, 0)
        kv.symbolTabs = listOf<String?>(null, "🖼️") + Emojis.groups.map { it.first }
        kv.symbolTab = 1
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/my-stickers-board.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val a = Robolectric.buildActivity(SettingsActivity::class.java, android.content.Intent().putExtra("page", "mystickers")).setup().get()
        val v = a.window.decorView
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val s = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(s))
        File("build/renders/my-stickers-settings.png").outputStream().use { s.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
