package io.github.yolanda1027.catpaw

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import android.widget.Button
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 背景圖片（和按鍵底圖）可以只拉寬或只拉高；「剛好塞滿框」整張圖都看得到 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ImageStretchTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val gold = Color.parseColor("#E0A030")

    @Test fun math() {
        val frame = RectF(0f, 0f, 1080f, 738f)
        // 4:3 的圖，剛好塞滿 1080×738：整張貼齊框
        val f = ImageFill("a.png").fitFrame(frame, 400, 300)
        assertEquals(1080f / 738f / (4f / 3f), f.stretch, 0.001f)
        val d = f.destRect(frame, 400, 300)
        assertEquals(frame.left, d.left, 0.5f)
        assertEquals(frame.right, d.right, 0.5f)
        assertEquals(frame.top, d.top, 0.5f)
        assertEquals(frame.bottom, d.bottom, 0.5f)
        // 只拉寬兩倍：寬高比變兩倍
        val w = ImageFill("a.png", stretch = 2f).destRect(frame, 400, 300)
        assertEquals(2f * 4f / 3f, w.width() / w.height(), 0.01f)
        // JSON：沒拉就不寫，有拉就留著
        assertFalse(ImageFill("a.png").toJson().has("stretch"))
        val st = ImageFill("a.png", zoom = 1.3f, stretch = 1.25f)
        assertEquals(st, ImageFill.fromJson(JSONObject(st.toJson().toString())))
        assertEquals(ImageFill.MAX_STRETCH, ImageFill.fromJson(JSONObject().put("img", "a.png").put("stretch", 99))!!.stretch)
    }

    /** 像使用者那張：比框窄一點、四周有金框的圖 */
    private fun framedPicture(w: Int = 760, h: Int = 560): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.parseColor("#2E241C"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 18f; color = gold }
        c.drawRoundRect(RectF(12f, 12f, w - 12f, h - 12f), 40f, 40f, p)
        return b
    }

    /** 「這張圖要怎麼放？」／放法：點某一種（找到名稱那行字，點它那一整列） */
    private fun pickMode(v: View, title: String) {
        val t = find(v) { it is TextView && (it.text.toString() == title || it.text.toString() == "✓ $title") } ?: error("找不到放法：$title")
        (t.parent.parent as View).performClick()
        ShadowLooper.idleMainLooper()
        // 上傳後問「這張圖要怎麼放？」：點選項只是選起來，要按「確認」才套用
        val d = org.robolectric.shadows.ShadowDialog.getShownDialogs().filterIsInstance<android.app.AlertDialog>()
            .lastOrNull { it.window?.decorView === v.rootView }
        val ok = d?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        if (d != null && d.isShowing && ok != null && ok.text.toString() == "確認") {
            ok.performClick()
            ShadowLooper.idleMainLooper()
        }
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    private fun shot(v: View, name: String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun isGold(c: Int) = Color.red(c) > 180 && Color.green(c) in 110..190 && Color.blue(c) < 100

    @Test fun keyboardBackgroundFitFrame() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val root = a.window.decorView
        (find(root) { it is Button && it.text.toString().startsWith("🖼️ 鍵盤背景") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        (find(root) { it is Button && it.text == "📷 上傳圖片" } as Button).performClick()
        val req = shadowOf(a).nextStartedActivityForResult
        val f = File(ctx.cacheDir, "framed.png").apply { outputStream().use { framedPicture().compress(Bitmap.CompressFormat.PNG, 100, it) } }
        shadowOf(a).receiveResult(req.intent, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(f)))
        ShadowLooper.idleMainLooper()
        // 上傳完問要怎麼放：選「整張都要看到」（不用裁切）
        val d = ShadowDialog.getLatestDialog() as Dialog
        shot(d.window!!.decorView, "stretch-before")
        pickMode(d.window!!.decorView, "整張都要看到")
        val saved = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).keyboardImage!!
        assertTrue(saved.fit)
        assertTrue("舊版 App 看的拉寬也算好了：${saved.stretch}", saved.stretch > 1.05f)

        // 鍵盤上：背景圖的金框四邊都看得到（左右沒有被裁掉、也沒有空白）
        val kv = CatKeyboardView(ctx, Prefs(ctx, ThemeStore.WORK), Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        File("build/renders/stretch-keyboard.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val fr = kv.keyboardImageFrame()
        val top = kv.floatH
        // 左邊的金框（在框的最左邊附近）
        val midY = (top + fr.top + fr.height() * 0.5f).toInt()
        assertTrue("左邊有金框", (0..30).any { isGold(b.getPixel(it, midY)) })
        assertTrue("右邊有金框", (1049..1079).any { isGold(b.getPixel(it, midY)) })

        // 換到框比較高的手機（開了快捷列）：每台照自己的框重新塞滿，金框四邊還是都在
        kv.quickItems = listOf("()", "「」")
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b2 = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b2))
        val fr2 = kv.keyboardImageFrame()
        val mid2 = (top + fr2.top + fr2.height() * 0.5f).toInt()
        val cx = 540
        assertTrue("左邊有金框", (0..30).any { isGold(b2.getPixel(it, mid2)) })
        assertTrue("右邊有金框", (1049..1079).any { isGold(b2.getPixel(it, mid2)) })
        assertTrue("上面有金框", ((top + fr2.top).toInt()..(top + fr2.top + 30).toInt()).any { isGold(b2.getPixel(cx, it)) })
        assertTrue("下面有金框", ((top + fr2.bottom - 30).toInt() until (top + fr2.bottom).toInt()).any { isGold(b2.getPixel(cx, it)) })
        File("build/renders/stretch-keyboard-quickbar.png").outputStream().use { b2.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 裁切畫面：拉寬、拉高分開兩條，可以只拉一邊，也可以兩邊都拉 */
    @Test fun widthAndHeightSeparately() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val v = CropView(ctx, framedPicture(), 1080f / 738f, 0f, ImageFill("a.png"))
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 1400)
        val (w0, h0) = v.sizeRel()
        assertEquals(1f, w0, 0.01f)
        assertEquals(1f, h0, 0.01f)
        // 只拉寬：高度不變
        v.setSizeRel(1.5f, 1f)
        assertEquals(1.5f, v.sizeRel().first, 0.01f)
        assertEquals(1f, v.sizeRel().second, 0.01f)
        // 再拉高：寬度維持 1.5，兩邊都變大
        v.setSizeRel(1.5f, 2f)
        assertEquals(1.5f, v.sizeRel().first, 0.01f)
        assertEquals(2f, v.sizeRel().second, 0.01f)
        // 畫面上看到的寬高比也真的變了
        val d = v.fill.destRect(v.frame, v.bmp.width, v.bmp.height)
        assertEquals((1.5f / 2f) * v.bmp.width / v.bmp.height, d.width() / d.height(), 0.01f)
    }

    /**
     * 使用者回報：去背的羊皮紙按鍵（整張畫布是橫的、羊皮紙在中間、左右透明），
     * 按「剛好塞滿框」羊皮紙被擠成瘦長的。應該是羊皮紙本身剛好貼齊框。
     */
    @Test fun fitFrameUsesVisiblePartOfTransparentPng() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val bmp = Bitmap.createBitmap(1536, 1024, Bitmap.Config.ARGB_8888) // 透明畫布
        Canvas(bmp).drawRect(318f, 62f, 1218f, 962f, Paint().apply { color = gold }) // 中間 900 × 900 的方塊
        assertEquals(RectF(318f, 62f, 1218f, 962f).width(), opaqueBounds(bmp).width(), 6f)
        val v = CropView(ctx, bmp, 92f / 97f, 0.2f, ImageFill("a.png", shape = true, zoom = ImageFill.FIT))
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 1400)
        v.fitFrame()
        val d = v.fill.destRect(v.frame, bmp.width, bmp.height)
        // 方塊在畫面上的位置
        val sx = d.width() / bmp.width
        val sy = d.height() / bmp.height
        val box = RectF(d.left + 318 * sx, d.top + 62 * sy, d.left + 1218 * sx, d.top + 962 * sy)
        assertEquals("方塊左邊貼齊框", v.frame.left, box.left, 3f)
        assertEquals("方塊右邊貼齊框", v.frame.right, box.right, 3f)
        assertEquals("方塊上面貼齊框", v.frame.top, box.top, 3f)
        assertEquals("方塊下面貼齊框", v.frame.bottom, box.bottom, 3f)
        // 方塊幾乎是正方形、框也幾乎是正方形：只會微調一點點，不會變瘦長
        assertTrue("拉的倍數 ${v.fill.stretch}", v.fill.stretch in 0.9f..1.0f)
    }
}
