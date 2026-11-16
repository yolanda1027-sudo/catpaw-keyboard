package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

/** 主題工作室：按鍵上的字也能選、拖、換色、換上下層；圖案可以只拉寬或只拉高 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StudioElementsTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val xKey = Layouts.ZHUYIN[3][3] // ㄒ（長按 v）

    private fun render(v: View, name: String) {
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun ev(action: Int, vararg pts: Pair<Float, Float>): MotionEvent {
        val t = SystemClock.uptimeMillis()
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { i -> MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test fun labelStyleJsonRoundTrip() {
        val st = KeyStyle(
            labels = mapOf("main" to LabelStyle(0.1f, -0.2f, 1.5f, 15f, Color.RED), "sub" to LabelStyle(color = Color.BLUE)),
            order = listOf("label:sub", "d1", "label:main"),
        )
        assertEquals(st, KeyStyle.fromJson(JSONObject(st.toJson().toString())))
        assertTrue(!st.isEmpty)
    }

    private class Studio(val a: StudioActivity) {
        val kv = field("kv") as CatKeyboardView
        val zoom = field("zoom") as KeyZoomView
        fun field(n: String): Any = StudioActivity::class.java.getDeclaredField(n).apply { isAccessible = true }.get(a)!!
        fun call(n: String, vararg args: Any?) {
            val m = StudioActivity::class.java.declaredMethods.first { it.name == n && it.parameterCount == args.size }
            m.isAccessible = true
            m.invoke(a, *args)
            ShadowLooper.idleMainLooper()
        }
        fun box() = KeyZoomView::class.java.getDeclaredMethod("keyBox").apply { isAccessible = true }.invoke(zoom) as RectF
    }

    @Test fun dragRecolorAndReorderLabels() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        Decals.save(p, listOf(Decal("d1", "zy:ㄒ", "shape:heart", x = 0.5f, y = 0.32f, size = 0.45f, color = 0xFFFF6FA3.toInt())))
        val s = Studio(Robolectric.buildActivity(StudioActivity::class.java).setup().get())
        s.kv.editPicker!!.invoke(xKey)
        ShadowLooper.idleMainLooper()

        // 預設順序：主字、小字在下面，愛心在最上面
        assertEquals(listOf("label:main", "label:sub", "d1"), s.kv.elementOrder(xKey))

        // 點小字 v（在按鍵下面那一段）→ 選到小字
        val box = s.box()
        val sub = s.kv.keyElements(xKey).first { it.decal.id == "label:sub" }.decal
        val sx = box.left + sub.x * box.width()
        val sy = box.top + sub.y * box.height()
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, sx to sy))
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, sx to sy))
        ShadowLooper.idleMainLooper()
        assertEquals("label:sub", s.field("decalSel"))
        render(s.a.window.decorView, "elements-sub-selected")

        // 把小字往右下拖 1/4 個按鍵
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, sx to sy))
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, sx + box.width() / 4 to sy + box.height() / 8))
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, sx + box.width() / 4 to sy + box.height() / 8))
        ShadowLooper.idleMainLooper()
        val ls = KeyStyles.load(Prefs(ctx)).map["zy:ㄒ"]!!.labels!!["sub"]!!
        assertEquals(0.25f, ls.dx, 0.01f)
        assertEquals(0.125f, ls.dy, 0.01f)

        // 換顏色、把主字移到愛心上面
        s.call("setLabelStyle", xKey, "sub", ls.copy(color = 0xFF4F8BFF.toInt()), true)
        s.call("moveElement", xKey, "label:main", 1)
        s.call("moveElement", xKey, "label:main", 1)
        assertEquals(listOf("label:sub", "d1", "label:main"), s.kv.elementOrder(xKey))
        assertEquals(0xFF4F8BFF.toInt(), s.kv.labelColorOf(xKey, "sub"))
        render(s.a.window.decorView, "elements-reordered")

        // 鍵盤本身也照這樣畫
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders/elements-keyboard.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 重設：小字回到原本的位置和顏色
        s.call("setLabelStyle", xKey, "sub", null, true)
        assertTrue(KeyStyles.load(Prefs(ctx)).map["zy:ㄒ"]?.labels.isNullOrEmpty())
    }

    @Test fun sideHandlesStretchOnlyOneDirection() {
        val p = Prefs(ctx)
        Decals.save(p, listOf(Decal("d1", "zy:ㄒ", "shape:cat", x = 0.5f, y = 0.5f, size = 0.4f)))
        val s = Studio(Robolectric.buildActivity(StudioActivity::class.java).setup().get())
        s.kv.onDecalPicked!!.invoke(Decals.load(p).first())
        ShadowLooper.idleMainLooper()
        val box = s.box()
        fun now() = Decals.load(Prefs(ctx)).first()
        fun size(d: Decal) = Decals.sizeOf(d, box.height(), null)
        val (w0, h0) = size(now())
        val pad = 5 * 2.625f
        // 右邊的拉把往右拖：寬變兩倍，高不變
        val rx = box.centerX() + w0 / 2 + pad
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, rx to box.centerY()))
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, rx + w0 / 2 to box.centerY()))
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, rx + w0 / 2 to box.centerY()))
        ShadowLooper.idleMainLooper()
        val (w1, h1) = size(now())
        assertEquals(w0 * 2, w1, 2f)
        assertEquals(h0, h1, 2f)
        // 下面的拉把往上推：高變一半，寬不變
        val by = box.centerY() + h1 / 2 + pad
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, box.centerX() to by))
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, box.centerX() to by - h1 / 4))
        s.zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, box.centerX() to by - h1 / 4))
        ShadowLooper.idleMainLooper()
        val (w2, h2) = size(now())
        assertEquals(w1, w2, 2f)
        assertEquals(h1 / 2, h2, 2f)
        render(s.a.window.decorView, "elements-stretched")
        assertNotNull(now())
    }
}
