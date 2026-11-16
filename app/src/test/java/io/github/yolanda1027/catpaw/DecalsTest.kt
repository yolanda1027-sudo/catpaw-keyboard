package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class DecalsTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun render(v: View, name: String) {
        Ui.spaceButtons(v, (3 * v.resources.displayMetrics.density).toInt())
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 假裝是使用者從相簿選的圖：一個藍色圓點的 600×400 圖（會被縮到 512 以內） */
    private fun photo(): ByteArray {
        val b = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.TRANSPARENT)
        c.drawCircle(300f, 200f, 180f, android.graphics.Paint().apply { color = Color.parseColor("#4F8BFF") })
        return Decals.toBytes(b)
    }

    @Test fun jsonRoundTripAndSafety() {
        val d = Decal("a", "zy:ㄅ", "shape:heart", x = 0.3f, y = 0.7f, size = 0.6f, rotation = 15f, alpha = 0.8f, color = 0xFF112233.toInt())
        assertEquals(d, Decal.fromJson(JSONObject(d.toJson().toString())))
        // 不接受奇怪的路徑
        assertNull(Decal.fromJson(JSONObject().put("target", "@bg").put("img", "file:../../shared_prefs/x.png")))
        assertNull(Decal.fromJson(JSONObject().put("target", "@bg").put("img", "http://x")))
    }

    @Test fun storesImportedImageSmaller() {
        val name = Decals.storeBytes(ctx, photo())!!
        val b = android.graphics.BitmapFactory.decodeFile(File(Decals.dir(ctx), name).path)
        assertTrue(maxOf(b.width, b.height) <= Decals.MAX_PX)
        assertNull(Decals.storeBytes(ctx, "not an image".toByteArray()))
    }

    private fun sampleDecals(): List<Decal> {
        val img = Decals.storeBytes(ctx, photo())!!
        return listOf(
            Decal("bg1", Decal.BG, "shape:cat", x = 0.5f, y = 0.62f, size = 0.22f, color = 0xFFFFE08A.toInt()),
            Decal("k1", "zy:ㄅ", "shape:catline", x = 0.27f, y = 0.3f, size = 0.42f, color = 0xFFF2C75C.toInt()),
            Decal("k2", "enter", "shape:heart", x = 0.75f, y = 0.25f, size = 0.35f, rotation = 20f, color = 0xFFFF4F8B.toInt()),
            Decal("k3", "zy:ㄓ", "shape:bow", x = 0.5f, y = 0.18f, size = 0.4f),
            Decal("k4", "zy:ㄚ", "shape:star", x = 0.8f, y = 0.2f, size = 0.3f, color = 0xFFFFC94D.toInt()),
            Decal("k5", "zy:ㄇ", "shape:paw", x = 0.75f, y = 0.75f, size = 0.3f),
            Decal("k6", "zy:ㄙ", "shape:flower", x = 0.25f, y = 0.25f, size = 0.32f),
            Decal("k7", "zy:ㄦ", "shape:moon", x = 0.5f, y = 0.5f, size = 0.4f, color = 0xFFB39CF0.toInt()),
            Decal("k8", "space", "shape:sparkle", x = 0.85f, y = 0.5f, size = 0.35f, color = 0xFFFFC94D.toInt()),
            Decal("k9", "zy:ㄤ", Decal.FILE + img, x = 0.7f, y = 0.3f, size = 0.4f, alpha = 0.9f),
            Decal("bg2", Decal.BG, "shape:sparkle", x = 0.93f, y = 0.07f, size = 0.08f, front = true, color = 0xFFFFE08A.toInt()),
        )
    }

    @Test fun renderDecals() {
        val p = Prefs(ctx)
        p.theme = "classic"
        Decals.save(p, sampleDecals())
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        render(kv, "decals-classic")
        kv.decalEditId = "k1"
        render(kv, "decals-selected")
    }

    private fun ev(action: Int, vararg pts: Pair<Float, Float>): MotionEvent {
        val t = SystemClock.uptimeMillis()
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { i -> MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test fun dragAndPinchMoveTheSelectedDecal() {
        val p = Prefs(ctx)
        Decals.save(p, listOf(Decal("bg1", Decal.BG, "shape:cat", x = 0.5f, y = 0.5f, size = 0.2f)))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        var last: Decal? = null
        var finished = 0
        kv.editPicker = { }
        kv.onDecalChanged = { d, done -> last = d; if (done) finished++ }
        kv.decalEditId = "bg1"

        // 按住圖案中間往右拖 108px（鍵盤寬的 10%）
        val floatH = 34 * 2.625f
        val cx = 540f
        val cy = floatH + (kv.measuredHeight - floatH) * 0.5f
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, cx to cy))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, cx + 108f to cy))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, cx + 108f to cy))
        assertEquals(0.6f, last!!.x, 0.01f)
        assertEquals(1, finished)

        // 兩隻手指拉開成兩倍距離、再轉 90 度
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 648f to cy))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 548f to cy, 748f to cy))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, 648f to cy - 200f, 648f to cy + 200f))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 648f to cy - 200f, 648f to cy + 200f))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 648f to cy - 200f))
        assertEquals(0.4f, last!!.size, 0.01f)
        assertEquals(90f, last!!.rotation, 0.5f)
    }

    /** 放大的按鍵上，用 Canva 那種把手：拖角縮放、拖圓圈旋轉、小工具複製／翻轉／刪除 */
    @Test fun canvaStyleHandles() {
        val p = Prefs(ctx)
        Decals.save(p, listOf(Decal("k1", "zy:ㄅ", "shape:cat", x = 0.5f, y = 0.5f, size = 0.4f)))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val zoom = StudioActivity::class.java.getDeclaredField("zoom").apply { isAccessible = true }.get(a) as KeyZoomView
        kv.onDecalPicked!!.invoke(Decals.load(p).first())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        render(a.window.decorView, "studio-handles")
        fun now() = Decals.load(Prefs(ctx)).first { it.id == "k1" }
        fun drag(from: Pair<Float, Float>, to: Pair<Float, Float>) {
            zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, from))
            zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, to))
            zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, to))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            render(a.window.decorView, "studio-handles")
        }
        val box = KeyZoomView::class.java.getDeclaredMethod("keyBox").apply { isAccessible = true }.invoke(zoom) as android.graphics.RectF
        val d0 = now()
        val half = d0.size * box.height() / 2 + 5 * 2.625f
        val cx = box.centerX()
        val cy = box.centerY()
        // 右下角往外拖到兩倍距離 → 變兩倍大
        drag(cx + half to cy + half, cx + half * 2 to cy + half * 2)
        assertEquals(d0.size * 2, now().size, 0.02f)
        // 旋轉把手（框下面 30dp）拖到右邊 → 轉 -90 度（從正下方轉到正右方）
        val h2 = now().size * box.height() / 2 + 5 * 2.625f
        drag(cx to cy + h2 + 30 * 2.625f, cx + 300f to cy)
        assertEquals(-90f, now().rotation, 1f)

        // 小工具：位置從畫出來的框拿
        @Suppress("UNCHECKED_CAST")
        val tools = DecalHandles::class.java.getDeclaredField("toolRects").apply { isAccessible = true }.get(zoom.handles) as List<Pair<String, android.graphics.RectF>>
        fun tap(id: String) {
            val r = tools.first { it.first == id }.second
            zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, r.centerX() to r.centerY()))
            zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, r.centerX() to r.centerY()))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            render(a.window.decorView, "studio-handles")
        }
        tap("flip")
        assertTrue(now().flip)
        render(a.window.decorView, "studio-handles-flip")
        tap("copy")
        assertEquals(2, Decals.load(Prefs(ctx)).size)
        tap("delete") // 刪掉的是複製出來、現在選到的那個
        assertEquals(listOf("k1"), Decals.load(Prefs(ctx)).map { it.id })
    }

    @Test fun tapSelectsDecalOrKey() {
        val p = Prefs(ctx)
        Decals.save(p, listOf(Decal("k2", "enter", "shape:heart", x = 0.5f, y = 0.5f, size = 0.5f)))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        var picked: Decal? = null
        var key: Key? = null
        kv.editPicker = { key = it }
        kv.onDecalPicked = { picked = it }
        // Enter 在右下角：點正中間＝點到愛心
        val x = 1080f - 1080f * 1.5f / 10.5f / 2
        val y = kv.measuredHeight - 10 * 2.625f - 45 * 2.625f / 2
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, x to y))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, x to y))
        assertEquals("k2", picked?.id)
        // 點左上角的 ㄅ：選到按鍵
        val y0 = 34 * 2.625f + 40 * 2.625f + 6 * 2.625f + 20 * 2.625f
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 50f to y0))
        kv.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 50f to y0))
        assertEquals("ㄅ", key?.label)
    }

    @Test fun themeFileCarriesDecalsAndImages() {
        val p = Prefs(ctx)
        val list = sampleDecals()
        Decals.save(p, list)
        val json = ThemeIO.export(ctx, p, "圖案", setOf(ThemeIO.Part.DECALS))
        // 別人的手機：什麼都沒有
        p.sp.edit().clear().commit()
        Decals.cleanup(ctx, emptyList())
        val o = ThemeIO.parse(json)!!
        assertTrue(ThemeIO.describe(o).second.contains("圖案 ${list.size} 個"))
        val w = ThemeIO.apply(ctx, p, o)
        assertTrue(w.toString(), w.isEmpty())
        val back = Decals.load(Prefs(ctx))
        assertEquals(list.size, back.size)
        val img = back.first { !it.isShape }
        assertNotNull(Decals.pngBytes(ctx, img.fileName))
        assertEquals(list.map { it.copy(img = "") }, back.map { it.copy(img = "") })
    }

    @Test fun studioDecalPanel() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        Decals.save(p, sampleDecals())
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val f = StudioActivity::class.java.getDeclaredField("kv"); f.isAccessible = true
        val kv = f.get(a) as CatKeyboardView
        kv.editPicker!!.invoke(Layouts.ZHUYIN[0][0])
        render(a.window.decorView, "studio-key-decals")
        kv.onDecalPicked!!.invoke(Decals.load(p).first { it.id == "k1" })
        render(a.window.decorView, "studio-decal")

        // 放大的按鍵上拖曳圖案：往右拖 1/4 個按鍵寬
        val zf = StudioActivity::class.java.getDeclaredField("zoom"); zf.isAccessible = true
        val zoom = zf.get(a) as KeyZoomView
        val box = KeyZoomView::class.java.getDeclaredMethod("keyBox").apply { isAccessible = true }.invoke(zoom) as android.graphics.RectF
        val before = Decals.load(p).first { it.id == "k1" }
        val sx = box.left + before.x * box.width()
        val sy = box.top + before.y * box.height()
        zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, sx to sy))
        zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, sx + box.width() / 4 to sy))
        zoom.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, sx + box.width() / 4 to sy))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(before.x + 0.25f, Decals.load(Prefs(ctx)).first { it.id == "k1" }.x, 0.01f)
        render(a.window.decorView, "studio-zoom-moved")

        // 點另一個圖案（Enter 鍵上的愛心）：放大畫面換成 Enter
        kv.onDecalPicked!!.invoke(Decals.load(p).first { it.id == "k2" })
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        render(a.window.decorView, "studio-zoom-enter")
        // 鍵盤背景的面板（按鈕要分開，不能黏在一起）
        kv.editPicker!!.invoke(Layouts.ZHUYIN[0][0])
        val sel = StudioActivity::class.java.getDeclaredField("selected"); sel.isAccessible = true
        sel.set(a, null)
        val bg = StudioActivity::class.java.getDeclaredField("bgSelected"); bg.isAccessible = true
        bg.set(a, true)
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        render(a.window.decorView, "studio-bg-panel")
    }

    /** 恢復主題預設：要先確認；按了才清掉圖案和按鍵設定，整體動畫不動 */
    @Test fun resetToThemeDefault() {
        val p = Prefs(ctx)
        p.pressEffect = "spell"
        Decals.save(p, sampleDecals())
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED, effect = KeyEffect("snow"))))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        render(a.window.decorView, "studio-home-reset")
        val btn = findButton(a.window.decorView, "↺ 恢復主題預設")!!
        btn.performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        // 還沒按確認：什麼都沒清
        assertEquals(sampleDecals().size, Decals.load(Prefs(ctx)).size)
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(Decals.load(Prefs(ctx)).isEmpty())
        assertTrue(KeyStyles.load(Prefs(ctx)).map.isEmpty())
        assertTrue(Decals.dir(ctx).listFiles()!!.isEmpty())
        assertEquals("spell", Prefs(ctx).pressEffect)
    }

    private fun findButton(v: View, text: String): android.widget.Button? {
        if (v is android.widget.Button && v.text.toString() == text) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findButton(v.getChildAt(i), text)?.let { return it }
        return null
    }
}
