package io.github.yolanda1027.catpaw

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 鍵盤背景、按鍵底色可以直接用圖片（放大、縮小、裁切、GIF） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ImageFillTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val gif: ByteArray get() = javaClass.classLoader!!.getResourceAsStream("anim.gif").readBytes()

    /** 假照片：天空漸層＋太陽＋山 */
    private fun photo(w: Int = 1200, h: Int = 800): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#8FD3FF"), Color.parseColor("#FFD6E7"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.parseColor("#FFC93C")
        c.drawCircle(w * 0.72f, h * 0.3f, h * 0.13f, p)
        p.color = Color.parseColor("#6FBF8E")
        val m = android.graphics.Path().apply { moveTo(0f, h.toFloat()); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.55f, h * 0.8f); lineTo(w * 0.8f, h * 0.45f); lineTo(w.toFloat(), h * 0.75f); lineTo(w.toFloat(), h.toFloat()); close() }
        c.drawPath(m, p)
        return b
    }

    private fun photoFile(name: String): File = File(ctx.cacheDir, name).also { f -> f.outputStream().use { photo().compress(Bitmap.CompressFormat.PNG, 100, it) } }

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(v: View, name: String) {
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        save(bmp, name)
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }
    private fun button(v: View, t: String) = (find(v) { it is Button && it.text.toString() == t } as? Button) ?: error("找不到按鈕：$t")

    @Test fun coverZoomAndClamp() {
        val frame = RectF(0f, 0f, 100f, 50f)
        // 200×200 的圖鋪滿 100×50：縮成 100×100，上下各裁掉 25
        assertEquals(RectF(0f, -25f, 100f, 75f), ImageFill("a.png").destRect(frame, 200, 200))
        // 想露出最上面：最多只能拉到上緣對齊框
        assertEquals(RectF(0f, 0f, 100f, 100f), ImageFill("a.png", cy = 0f).destRect(frame, 200, 200))
        // 放大兩倍
        assertEquals(200f, ImageFill("a.png", zoom = 2f).destRect(frame, 200, 200).width(), 0.01f)
        // 最小：整張剛好放進框裡（50×50，置中）
        assertEquals(0.5f, ImageFill.minZoom(frame, 200, 200), 0.001f)
        assertEquals(RectF(25f, 0f, 75f, 50f), ImageFill("a.png", zoom = 0.1f).destRect(frame, 200, 200))
        // 整理過的中心點不會超出範圍
        val n = ImageFill("a.png", zoom = 1f, cx = 0f, cy = 1f).normalized(frame, 200, 200)
        assertEquals(0.5f, n.cx, 0.001f)
        assertEquals(0.75f, n.cy, 0.001f)
        // JSON
        val f = ImageFill("decal_1.gif", 1.7f, 0.3f, 0.6f)
        assertEquals(f, ImageFill.fromJson(JSONObject(f.toJson().toString())))
        assertNull(ImageFill.fromJson(JSONObject().put("img", "../shared_prefs/x.png")))
        val st = KeyStyle(bgImage = f)
        assertEquals(st, KeyStyle.fromJson(JSONObject(st.toJson().toString())))
        assertTrue(st.isShaped)
    }

    @Test fun cropGestures() {
        val v = CropView(ctx, photo(), 1.2f, 0.15f, ImageFill("a.png"))
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 1500)
        // 圖片比框寬：可以左右拖。往右拖 → 露出左邊（中心往左）
        v.pan(100f, 0f)
        assertTrue(v.fill.cx < 0.5f)
        // 兩指張開兩倍 → 放大兩倍
        val z = v.fill.zoom
        v.pinch(2f, v.frame.centerX(), v.frame.centerY())
        assertEquals(z * 2, v.fill.zoom, 0.01f)
        // 縮到最小：整張圖都在框裡
        v.setZoom(0.01f)
        assertEquals(v.minZoom, v.fill.zoom, 0.001f)
        val d = v.fill.destRect(v.frame, 1200, 800)
        assertTrue(d.left >= v.frame.left - 0.5f && d.right <= v.frame.right + 0.5f)
        v.reset()
        assertEquals(ImageFill("a.png"), v.fill)
    }

    @Test fun studioUploadCropAndGif() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val root = a.window.decorView

        // ① 點 ㄅ → 改「所有注音鍵」→ 上傳照片當底圖
        val bo = Layouts.ZHUYIN[0][0]
        kv.editPicker!!.invoke(bo)
        ShadowLooper.idleMainLooper()
        (find(root) { it is android.widget.RadioButton && it.text == "所有注音鍵" } as android.widget.RadioButton).performClick()
        ShadowLooper.idleMainLooper()
        button(root, "📷 上傳圖片").performClick()
        var req = shadowOf(a).nextStartedActivityForResult
        shadowOf(a).receiveResult(req.intent, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(photoFile("p.png"))))
        ShadowLooper.idleMainLooper()
        // 上傳完直接進裁切畫面
        var crop = ShadowDialog.getLatestDialog() as Dialog
        val cv = find(crop.window!!.decorView) { it is CropView } as CropView
        render(crop.window!!.decorView, "fill-crop-key")
        cv.pinch(1.6f, cv.frame.centerX(), cv.frame.centerY())
        cv.pan(-cv.frame.width() * 0.4f, 0f) // 往左拖：露出右邊的太陽
        render(crop.window!!.decorView, "fill-crop-key-zoomed")
        button(crop.window!!.decorView, "完成").performClick()
        ShadowLooper.idleMainLooper()
        val zy = KeyStyles.load(Prefs(ctx)).map["@zhuyin"]!!.bgImage!!
        assertEquals(cv.fill, zy)
        assertTrue(zy.zoom > 1.5f)
        // 存的圖夠大，放大才不會糊
        val stored = android.graphics.BitmapFactory.decodeFile(File(Decals.dir(ctx), zy.img).path)
        assertEquals(1200, stored.width)

        // ② 鍵盤背景：上傳 GIF（會動）
        val bgGif = File(ctx.cacheDir, "bg.gif").apply { writeBytes(gif) }
        a.runOnUiThread {}
        StudioActivity::class.java.getDeclaredField("selected").apply { isAccessible = true }.set(a, null)
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        button(root, "🖼️ 鍵盤背景（背景圖片、貼圖案）").performClick()
        ShadowLooper.idleMainLooper()
        render(root, "fill-studio-bg-empty")
        button(root, "📷 上傳圖片").performClick()
        req = shadowOf(a).nextStartedActivityForResult
        shadowOf(a).receiveResult(req.intent, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(bgGif)))
        ShadowLooper.idleMainLooper()
        crop = ShadowDialog.getLatestDialog() as Dialog
        render(crop.window!!.decorView, "fill-crop-bg")
        button(crop.window!!.decorView, "完成").performClick()
        ShadowLooper.idleMainLooper()
        val bg = KeyStyles.load(Prefs(ctx)).keyboardImage!!
        assertTrue(bg.isGif)
        assertArrayEquals(gif, File(Decals.dir(ctx), bg.img).readBytes()) // GIF 照原檔（會動）
        render(root, "fill-studio-bg")

        // 離開工作室時，底圖不會被當成沒用的圖刪掉
        Decals.cleanup(ctx, Decals.load(Prefs(ctx)))
        assertTrue(File(Decals.dir(ctx), zy.img).exists())
        assertTrue(File(Decals.dir(ctx), bg.img).exists())

        // 鍵盤本身
        val k = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        k.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        k.layout(0, 0, k.measuredWidth, k.measuredHeight)
        val bmp = Bitmap.createBitmap(k.measuredWidth, k.measuredHeight, Bitmap.Config.ARGB_8888)
        k.draw(Canvas(bmp))
        save(bmp, "fill-keyboard")
        // 注音鍵中間是照片（不是主題原本的奶油色鍵）
        val r = k.keyRectOf(bo)!!
        val px = bmp.getPixel(r.centerX().toInt(), r.centerY().toInt() + 30)
        assertTrue(Color.blue(px) > 150 || Color.green(px) > 150)

        // 照片當鍵盤背景（按鍵維持主題原本的樣子）：給人看的截圖
        run {
            val pp = Prefs(ctx)
            val photoName = Decals.storeBytes(ctx, photoFile("bg.png").readBytes(), maxPx = Decals.FILL_MAX_PX)!!
            val saved = KeyStyles.load(pp)
            KeyStyles.save(pp, KeyStyles(mapOf(KeyStyles.KEYBOARD to KeyStyle(bgImage = ImageFill(photoName, zoom = 1.2f, cy = 0.55f)))))
            val k2 = CatKeyboardView(ctx, pp, Haptics(ctx))
            k2.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            k2.layout(0, 0, k2.measuredWidth, k2.measuredHeight)
            val b2 = Bitmap.createBitmap(k2.measuredWidth, k2.measuredHeight, Bitmap.Config.ARGB_8888)
            k2.draw(Canvas(b2))
            save(b2, "fill-keyboard-photo-bg")
            KeyStyles.save(pp, saved)
        }

        // ③ 匯出再匯入：底圖跟著走
        val json = ThemeIO.export(ctx, Prefs(ctx), "照片鍵盤", ThemeIO.Part.values().toSet())
        KeyStyles.save(Prefs(ctx), KeyStyles.EMPTY)
        Decals.dir(ctx).listFiles()!!.forEach { it.delete() }
        val warn = ThemeIO.apply(ctx, Prefs(ctx), ThemeIO.parse(json)!!)
        assertTrue(warn.toString(), warn.isEmpty())
        val ks = KeyStyles.load(Prefs(ctx))
        assertArrayEquals(gif, File(Decals.dir(ctx), ks.keyboardImage!!.img).readBytes())
        assertNotNull(File(Decals.dir(ctx), ks.map["@zhuyin"]!!.bgImage!!.img).takeIf { it.exists() })
        assertEquals(zy.zoom, ks.map["@zhuyin"]!!.bgImage!!.zoom, 0.0001f)
    }
}
