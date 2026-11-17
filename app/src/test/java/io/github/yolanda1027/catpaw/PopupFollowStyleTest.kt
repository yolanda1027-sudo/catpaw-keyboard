package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** 預覽泡泡、長按選單、展開的選字面板跟著主題工作室的樣式 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PopupFollowStyleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val pink = Color.parseColor("#FF8FB1")
    private val wine = Color.parseColor("#8E2A4A")
    private val brown = Color.parseColor("#3A2A20")

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun near(a: Int, b: Int, tol: Int = 30) =
        abs(Color.red(a) - Color.red(b)) <= tol && abs(Color.green(a) - Color.green(b)) <= tol && abs(Color.blue(a) - Color.blue(b)) <= tol

    private fun kv(p: Prefs) = CatKeyboardView(ctx, p, Haptics(ctx)).apply {
        measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun draw(v: CatKeyboardView): Bitmap {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        return b
    }

    private fun field(v: CatKeyboardView, n: String) = CatKeyboardView::class.java.getDeclaredField(n).apply { isAccessible = true }

    /** 假裝手指按著這顆鍵 */
    private fun press(v: CatKeyboardView, k: Key, label: String) {
        val kr = (field(v, "keyRects").get(v) as List<*>).first { it!!.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(it) == k }
        field(v, "pressed").set(v, kr)
        field(v, "previewLabel").set(v, label)
    }

    /** 泡泡在按鍵上面：這顆鍵上緣往上 0.5 個鍵高的那一點 */
    private fun bubblePoint(v: CatKeyboardView, k: Key): Pair<Int, Int> {
        val r = v.keyRectOf(k)!!
        return (r.centerX() + r.width() * 0.4f).toInt() to (v.floatH + r.top - 45 * 2.625f * 0.45f).toInt()
    }

    @Test fun previewBubbleFollowsKey() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val bo = Layouts.ZHUYIN[1][1] // ㄊ（上面還有一排，泡泡不會被切掉）
        val other = Layouts.ZHUYIN[1][5]
        KeyStyles.save(p, KeyStyles(mapOf("zy:ㄊ" to KeyStyle(bg = pink, text = Color.WHITE, border = wine, borderStyle = "stitch", radius = 0.5f))))
        val v = kv(p)
        press(v, bo, "ㄊ")
        val b = draw(v)
        save(b, "popup-preview-styled")
        val (x, y) = bubblePoint(v, bo)
        assertTrue("改過的鍵：泡泡是粉紅色", near(b.getPixel(x, y), pink))
        // 沒改過的鍵：照主題原本的泡泡（羊皮紙，不是粉紅）
        press(v, other, "ㄗ")
        val b2 = draw(v)
        val (x2, y2) = bubblePoint(v, other)
        assertFalse(near(b2.getPixel(x2, y2), pink))
    }

    @Test fun longPressMenuFollowsKey() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val bo = Layouts.ZHUYIN[1][1]
        KeyStyles.save(p, KeyStyles(mapOf("zy:ㄊ" to KeyStyle(bg = pink, text = Color.WHITE, border = wine, radius = 0.5f))))
        val v = kv(p)
        press(v, bo, "ㄊ")
        (field(v, "longPressRunnable").get(v) as Runnable).run()
        val b = draw(v)
        save(b, "popup-longpress-styled")
        val r = field(v, "popupRect").get(v) as RectF
        val sel = field(v, "popupSel").getInt(v)
        val n = (field(v, "popup").get(v)!!.javaClass.getDeclaredField("items").apply { isAccessible = true }.get(field(v, "popup").get(v)) as List<*>).size
        val optW = r.width() / n
        // 沒選到的格子：粉紅；選到的那格：外框色（酒紅）
        val other = if (sel == 0) 1 else 0
        val y = (v.floatH + r.top + r.height() * 0.85f).toInt()
        assertTrue(near(b.getPixel((r.left + optW * (other + 0.5f)).toInt(), y), pink))
        assertTrue(near(b.getPixel((r.left + optW * (sel + 0.5f)).toInt(), y), wine))
    }

    @Test fun expandedPanelFollowsTopBar() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        KeyStyles.save(p, KeyStyles(topBar = TopBarStyle(bg = brown, text = Color.WHITE, textDim = Color.parseColor("#D8C3B0"), tab = pink)))
        val v = kv(p)
        v.candidates = listOf("你好", "妳好", "擬好", "你", "妳", "泥", "尼", "膩", "逆", "匿", "呢", "倪", "霓", "妮", "擬", "伲")
        field(v, "expanded").setBoolean(v, true)
        val b = draw(v)
        save(b, "popup-panel-topbar")
        // 面板中間：咖啡色底，有白字
        assertTrue(near(b.getPixel(b.width - 6, b.height - 6), brown, 10))
        var white = 0
        for (yy in (v.floatH + 40 * 2.625f).toInt() until (v.floatH + 40 * 2.625f + 120).toInt()) for (xx in 200 until 900 step 3) if (near(b.getPixel(xx, yy), Color.WHITE, 20)) white++
        assertTrue("面板上的字是白色", white > 20)
    }

    @Test fun shapedKeyBubbleIsTheImage() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val img = Bitmap.createBitmap(200, 210, Bitmap.Config.ARGB_8888)
        Canvas(img).drawCircle(100f, 105f, 95f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F3E3C3") })
        val name = Decals.storeBytes(ctx, Decals.toBytes(img), maxPx = Decals.FILL_MAX_PX)!!
        KeyStyles.save(p, KeyStyles(mapOf("@zhuyin" to KeyStyle(text = brown, bgImage = ImageFill(name, ImageFill.FIT, shape = true)))))
        val v = kv(p)
        val bo = Layouts.ZHUYIN[1][1]
        press(v, bo, "ㄊ")
        val b = draw(v)
        save(b, "popup-preview-shaped")
        val (x, y) = bubblePoint(v, bo)
        assertTrue("泡泡是那張圓形羊皮紙", near(b.getPixel((v.keyRectOf(bo)!!.centerX()).toInt(), y), Color.parseColor("#F3E3C3")))
        assertTrue(x > 0)
    }
}
