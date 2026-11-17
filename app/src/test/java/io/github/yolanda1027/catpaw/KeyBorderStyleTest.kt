package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONObject
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
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.abs

/** 按鍵外框：粗細、實線／虛線／點點／縫線、縫線離邊距離 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class KeyBorderStyleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dp = 2.625f
    private val body = Color.parseColor("#FFE3EC")
    private val line = Color.parseColor("#E0607E")

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(p: Prefs): Pair<CatKeyboardView, Bitmap> {
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        return kv to b
    }

    private fun near(a: Int, b: Int, tol: Int = 40) =
        abs(Color.red(a) - Color.red(b)) <= tol && abs(Color.green(a) - Color.green(b)) <= tol && abs(Color.blue(a) - Color.blue(b)) <= tol

    /** 這顆鍵自訂外框鋪的框（畫面座標） */
    private fun box(kv: CatKeyboardView, k: Key): RectF {
        val r = kv.keyRectOf(k)!!
        return RectF(r.left + 3 * dp, r.top + 4 * dp + kv.floatH, r.right - 3 * dp, r.bottom - 4 * dp + kv.floatH)
    }

    /** 上邊中間那一段，往下 y 個像素的那一排有沒有外框色 */
    private fun lineAt(b: Bitmap, bx: RectF, y: Float): Boolean {
        val yy = (bx.top + y).toInt()
        for (x in (bx.left + bx.width() * 0.3f).toInt()..(bx.left + bx.width() * 0.7f).toInt()) if (near(b.getPixel(x, yy), line)) return true
        return false
    }

    @Test fun jsonRoundTrip() {
        val st = KeyStyle(border = line, borderWidth = 3.5f, borderStyle = "stitch", borderInset = 4f)
        assertEquals(st, KeyStyle.fromJson(JSONObject(st.toJson().toString())))
        assertNull(KeyStyle.fromJson(JSONObject().put("borderStyle", "zigzag")).borderStyle)
        assertTrue(KeyStyle(borderStyle = "dash").isShaped)
    }

    @Test fun drawsStylesAndStitchStaysPut() {
        val p = Prefs(ctx)
        p.theme = "macaron_strawberry"
        val zy = Layouts.ZHUYIN
        fun styles(stitchWidth: Float) = KeyStyles(mapOf(
            "@zhuyin" to KeyStyle(bg = body, border = line, radius = 0.35f),
            "zy:ㄅ" to KeyStyle(borderStyle = "solid", borderWidth = 4f),
            "zy:ㄆ" to KeyStyle(borderStyle = "dash", borderWidth = 2.5f),
            "zy:ㄇ" to KeyStyle(borderStyle = "dot", borderWidth = 3f),
            "zy:ㄈ" to KeyStyle(borderStyle = "stitch", borderWidth = stitchWidth, borderInset = 4f),
        ))
        KeyStyles.save(p, styles(2f))
        val (kv, b) = render(p)
        save(b, "border-styles")

        // 粗實線：貼著邊
        val solid = box(kv, zy[0][0])
        assertTrue(lineAt(b, solid, 1f))
        // 縫線：邊緣是底色，往內 4dp 才有線
        val st = box(kv, zy[3][0])
        assertFalse(lineAt(b, st, 1f))
        assertTrue(lineAt(b, st, 4 * dp))
        // 加粗縫線：還是在同一個地方（不會往內跑）
        KeyStyles.save(p, styles(5f))
        val (kv2, b2) = render(p)
        val st2 = box(kv2, zy[3][0])
        assertTrue(lineAt(b2, st2, 4 * dp))
        assertFalse(lineAt(b2, st2, 1f))
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    /** 這個 view 自己或上面幾層有沒有被變灰 */
    private fun greyed(v: View): Boolean {
        var cur: View? = v
        while (cur != null) {
            if (cur.alpha < 0.5f) return true
            cur = cur.parent as? View
        }
        return false
    }

    @Test fun studioControlsAndGreyOut() {
        val p = Prefs(ctx)
        p.theme = "macaron_strawberry"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.editPicker!!.invoke(Layouts.ZHUYIN[0][0])
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text == "外框樣式" })
        assertNull(find(root) { it is TextView && it.text.startsWith("縫線離邊距離") })
        // 選縫線 → 多一個「縫線離邊距離」
        (find(root) { it is Button && it.text.toString().endsWith("縫線") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals("stitch", KeyStyles.load(Prefs(ctx)).map["zy:ㄅ"]!!.borderStyle)
        assertNotNull(find(root) { it is TextView && it.text.startsWith("縫線離邊距離") })
        val shot = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        root.draw(Canvas(shot))
        save(shot, "border-studio")

        // 照圖片的形狀 → 底色、外框、圓角變灰；文字顏色不受影響
        val name = Decals.storeBitmap(ctx, Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) })
        StudioActivity::class.java.declaredMethods.first { it.name == "setFill" }.apply { isAccessible = true }
            .invoke(a, "zy:ㄅ", ImageFill(name, ImageFill.FIT, shape = true))
        ShadowLooper.idleMainLooper()
        val bgLabel = find(root) { it is TextView && it.text == "底色" }!!
        val borderLabel = find(root) { it is TextView && it.text == "外框顏色" }!!
        val textLabel = find(root) { it is TextView && it.text == "文字顏色" }!!
        assertTrue(greyed(bgLabel))
        val bgRow = bgLabel.parent.parent as ViewGroup
        for (i in 0 until bgRow.childCount) {
            val c = bgRow.getChildAt(i)
            if (c is ViewGroup) continue
            assertTrue("底色那一列的每個東西都要變淡：$c", greyed(c))
            assertFalse(c.isEnabled)
        }
        assertTrue(greyed(borderLabel))
        assertTrue(greyed(find(root) { it is TextView && it.text == "外框樣式" }!!))
        assertFalse(greyed(textLabel))
        val seek = find(root) { it is android.widget.SeekBar && greyed(it) }
        assertNotNull("圓角、粗細滑桿也要變灰", seek)
        assertFalse(seek!!.isEnabled)
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val shot2 = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(shot2))
        save(shot2, "border-studio-greyed")
    }
}
