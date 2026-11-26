package io.github.yolanda1027.catpaw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 我的貼圖：長按看放大版（放開不送出）；設定裡可以排順序 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StickerZoomOrderTest {
    private val ctx = RuntimeEnvironment.getApplication()

    /** 一張有字、有臉的「表情包」 */
    private fun sticker(color: Int, text: String): ByteArray {
        val b = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawCircle(240f, 270f, 170f, p)
        p.color = Color.WHITE
        c.drawCircle(185f, 250f, 22f, p)
        c.drawCircle(295f, 250f, 22f, p)
        p.color = Color.parseColor("#3A2A20")
        p.textSize = 54f
        p.textAlign = Paint.Align.CENTER
        c.drawText(text, 240f, 70f, p)
        return Decals.toBytes(b)
    }

    private fun add(color: Int, text: String): String {
        val n = MyStickers.add(ctx, sticker(color, text))!!
        Thread.sleep(3)
        return n
    }

    @Test fun orderMoveAddDelete() {
        val a = add(Color.parseColor("#FF8FA9"), "A")
        val b = add(Color.parseColor("#8FB8FF"), "B")
        val c = add(Color.parseColor("#7BC6A4"), "C")
        // 新的在前
        assertEquals(listOf(c, b, a), MyStickers.list(ctx))
        // a 往前一格
        assertTrue(MyStickers.move(ctx, a, -1))
        assertEquals(listOf(c, a, b), MyStickers.list(ctx))
        // 最前面不能再往前、最後面不能再往後
        assertFalse(MyStickers.move(ctx, c, -1))
        assertFalse(MyStickers.move(ctx, b, +1))
        // 新上傳的排最前面，原本排好的順序不變
        val d = add(Color.parseColor("#FFD166"), "D")
        assertEquals(listOf(d, c, a, b), MyStickers.list(ctx))
        // 刪掉一張：其他順序不變
        MyStickers.delete(ctx, a)
        assertEquals(listOf(d, c, b), MyStickers.list(ctx))
        // 順序檔不會被當成貼圖
        assertTrue(MyStickers.list(ctx).none { it.endsWith(".txt") })
    }

    private fun f(n: String) = CatKeyboardView::class.java.getDeclaredField(n).apply { isAccessible = true }

    @Test fun longPressShowsZoomReleaseDoesNotSend() {
        val name = add(Color.parseColor("#C9A0FF"), "我沒惹任何人")
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        val sent = ArrayList<Key>()
        kv.listener = object : TestListener() {
            override fun onKey(key: Key, longPress: Boolean) { sent.add(key) }
        }
        kv.rows = MyStickers.rows(ctx, 0)
        kv.symbolTabs = listOf<String?>(null, "🖼️", "😀")
        kv.symbolTab = 1
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val kr = (f("keyRects").get(kv) as List<*>).first {
            (it!!.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(it) as Key).text == name
        }
        // 真的按住不放（以前漏了：貼圖按下去根本沒開始算長按）
        val r = kr!!.javaClass.getDeclaredField("rect").apply { isAccessible = true }.get(kr) as android.graphics.RectF
        val t0 = android.os.SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, r.centerX(), r.centerY() + kv.floatH, 0))
        org.robolectric.shadows.ShadowLooper.idleMainLooper(Prefs(ctx).longPressMs + 50L, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("按住不放要跳出放大版", name, kv.stickerZoom)
        val b = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        File("build/renders").mkdirs()
        File("build/renders/sticker-zoom.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 放大的圖在正中間：那裡是貼圖的紫色臉
        val mid = b.getPixel(b.width / 2, (kv.floatH + (b.height - kv.floatH) * 0.55f).toInt())
        assertTrue("中間是放大的貼圖", Color.red(mid) in 170..230 && Color.blue(mid) > 220)
        // 放開：關掉，不送出
        CatKeyboardView::class.java.getDeclaredMethod("release", Boolean::class.java).apply { isAccessible = true }.invoke(kv, false)
        assertNull(kv.stickerZoom)
        assertTrue("長按放開不會送出", sent.isEmpty())
        // 一般點一下：照樣送出
        f("longFired").setBoolean(kv, false)
        f("pressed").set(kv, kr)
        CatKeyboardView::class.java.getDeclaredMethod("release", Boolean::class.java).apply { isAccessible = true }.invoke(kv, false)
        assertEquals(name, sent.single().text)
    }


    @Test fun settingsArrowsReorder() {
        val a = add(Color.parseColor("#FF8FA9"), "A")
        val b = add(Color.parseColor("#8FB8FF"), "B")
        val act = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "mystickers")).setup().get()
        val root = act.window.decorView
        // 第一張（b）的 ◀ 是灰的；按第一個能按的 ▶ → b 跟 a 交換
        val arrows = ArrayList<Button>()
        fun collect(v: View) { if (v is Button && (v.text == "◀" || v.text == "▶")) arrows.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i)) }
        collect(root)
        assertEquals(4, arrows.size)
        assertFalse(arrows[0].isEnabled) // b 的 ◀
        assertFalse(arrows[3].isEnabled) // a 的 ▶
        arrows[1].performClick() // b 的 ▶
        assertEquals(listOf(a, b), MyStickers.list(ctx))
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/renders/sticker-order-settings.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertNotNull(find(root) { it is Button && it.text == "刪除" })
    }
}
