package io.github.yolanda1027.catpaw

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.TextView
import android.widget.Button
import android.widget.CheckBox
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 按鍵底圖「照圖片的形狀」：毛邊、雲朵形的鍵，透明的地方就是按鍵外面 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class KeyShapeImageTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val parchment = Color.parseColor("#F3E3C3")

    /** 毛邊羊皮紙（去背）：四周鋸齒，中間淡米色 */
    private fun deckled(w: Int = 300, h: Int = 320): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val rnd = java.util.Random(7)
        val path = Path()
        val n = 90
        for (i in 0 until n) {
            val t = 2 * Math.PI * i / n
            // 圓角方形的輪廓＋一點點亂數 → 毛邊
            val ex = cos(t).let { Math.signum(it) * Math.pow(abs(it), 0.35) }
            val ey = sin(t).let { Math.signum(it) * Math.pow(abs(it), 0.35) }
            val j = 0.9 + rnd.nextDouble() * 0.06
            val x = (w / 2 + ex * w / 2 * j).toFloat()
            val y = (h / 2 + ey * h / 2 * j).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = parchment
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = Color.parseColor("#B08A55")
        c.drawPath(path, p)
        return b
    }

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(kv: CatKeyboardView): Bitmap {
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        return b
    }

    private fun near(a: Int, b: Int, tol: Int = 12) =
        abs(Color.red(a) - Color.red(b)) <= tol && abs(Color.green(a) - Color.green(b)) <= tol && abs(Color.blue(a) - Color.blue(b)) <= tol

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


    @Test fun jsonKeepsShape() {
        val f = ImageFill("decal_1.png", ImageFill.FIT, shape = true)
        assertEquals(f, ImageFill.fromJson(JSONObject(f.toJson().toString())))
        assertEquals(false, ImageFill.fromJson(JSONObject(ImageFill("a.png").toJson().toString()))!!.shape)
    }

    @Test fun shapedKeysAndPress() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val name = Decals.storeBytes(ctx, Decals.toBytes(deckled()), maxPx = Decals.FILL_MAX_PX)!!
        val ink = Color.parseColor("#4A3420")
        KeyStyles.save(p, KeyStyles(mapOf("@zhuyin" to KeyStyle(text = ink, bgImage = ImageFill(name, ImageFill.FIT, shape = true)))))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        val b = render(kv)
        save(b, "keyshape-keyboard")

        // 對照：沒有任何設定時，同一個位置是主題原本的按鍵色
        KeyStyles.save(p, KeyStyles.EMPTY)
        val plain = render(CatKeyboardView(ctx, p, Haptics(ctx)))
        KeyStyles.save(p, KeyStyles(mapOf("@zhuyin" to KeyStyle(text = ink, bgImage = ImageFill(name, ImageFill.FIT, shape = true)))))

        val bo = Layouts.ZHUYIN[1][0] // ㄆ
        val r = kv.keyRectOf(bo)!!
        val top = kv.floatH
        val box = android.graphics.RectF(r.left + 3 * 2.625f, r.top + 4 * 2.625f + top, r.right - 3 * 2.625f, r.bottom - 4 * 2.625f + top)
        // 中間偏下（避開字）：羊皮紙
        val mid = b.getPixel(box.centerX().toInt(), (box.top + box.height() * 0.85f).toInt())
        assertTrue("中間應該是羊皮紙", near(mid, parchment, 20))
        // 四個角是透明的 → 露出鍵盤背景，不是主題原本的按鍵色、也不是羊皮紙
        val cx = (box.left + 2).toInt()
        val cy = (box.top + 2).toInt()
        val corner = b.getPixel(cx, cy)
        assertTrue("角落不應該是羊皮紙", !near(corner, parchment, 30))
        assertTrue("角落不應該是主題原本的鍵", !near(corner, plain.getPixel(box.centerX().toInt(), (box.top + box.height() * 0.85f).toInt()), 20))

        // 按下去：只有圖片變暗，角落不變
        val f = CatKeyboardView::class.java.getDeclaredField("keyRects").apply { isAccessible = true }
        val kr = (f.get(kv) as List<*>).first { it!!.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(it) == bo }
        CatKeyboardView::class.java.getDeclaredField("pressed").apply { isAccessible = true }.set(kv, kr)
        val bp = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bp))
        save(bp, "keyshape-pressed")
        val midP = bp.getPixel(box.centerX().toInt(), (box.top + box.height() * 0.85f).toInt())
        assertTrue("按下去中間要變暗", Color.red(midP) < Color.red(mid) - 15)
        assertTrue("按下去角落不能變（不能出現暗暗的方框）", near(bp.getPixel(cx, cy), corner, 3))
    }

    @Test fun studioCheckboxAndCropChecker() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        // 工作室打開時會先清掉沒在用的圖檔，所以進來之後才「上傳」
        val name = Decals.storeBytes(ctx, Decals.toBytes(deckled()), maxPx = Decals.FILL_MAX_PX)!!
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.editPicker!!.invoke(Layouts.ZHUYIN[1][0])
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        StudioActivity::class.java.getDeclaredMethod("back").apply { isAccessible = true }.invoke(a) // 回首頁
        ShadowLooper.idleMainLooper()
        (find(root) { it is android.widget.Button && it.text.contains("所有注音鍵") } as android.widget.Button).performClick()
        ShadowLooper.idleMainLooper()
        StudioActivity::class.java.declaredMethods.first { it.name == "setFill" }.apply { isAccessible = true }.invoke(a, "@zhuyin", ImageFill(name))
        ShadowLooper.idleMainLooper()
        val box = find(root) { it is android.widget.CheckBox && it.text.startsWith("按鍵外觀跟著圖片的形狀") } as android.widget.CheckBox
        box.performClick()
        ShadowLooper.idleMainLooper()
        val f = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map["@zhuyin"]!!.bgImage!!
        assertTrue(f.shape)
        assertTrue("勾了之後順便換成「整張都要看到」", f.fit)
        assertEquals(ImageFill.Mode.WHOLE, f.mode)
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val shot = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(shot))
        save(shot, "keyshape-studio")

        // 照片模式＋跟著圖片形狀（以前的「照圖片的形狀」）：裁切畫面是灰白格子底
        StudioActivity::class.java.declaredMethods.first { it.name == "setFill" }.apply { isAccessible = true }
            .invoke(a, "@zhuyin", ImageFill(name, shape = true, zoom = ImageFill.FIT))
        ShadowLooper.idleMainLooper()
        val cropBtn = find(root) { it is Button && it.text == "✂️ 裁切" } as Button
        cropBtn.performClick()
        ShadowLooper.idleMainLooper()
        val crop = ShadowDialog.getLatestDialog() as Dialog
        val cv = find(crop.window!!.decorView) { it is CropView } as CropView
        val v = crop.window!!.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val cb = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(cb))
        save(cb, "keyshape-crop")
        // 整張圖都在框裡（毛邊沒被裁掉）
        val d = cv.fill.destRect(cv.frame, cv.bmp.width, cv.bmp.height)
        assertTrue(d.left >= cv.frame.left - 1 && d.right <= cv.frame.right + 1 && d.top >= cv.frame.top - 1 && d.bottom <= cv.frame.bottom + 1)
        // 重設也是「整張放進框裡」
        cv.pinch(2f, cv.frame.centerX(), cv.frame.centerY())
        cv.reset()
        assertEquals(cv.minZoom, cv.fill.zoom, 0.001f)
    }
}
