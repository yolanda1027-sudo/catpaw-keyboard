package io.github.yolanda1027.catpaw

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 按鍵底圖「九宮格」：空白鍵這種長條鍵套雕花框，四個角不變形 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class KeyNineTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val gold = Color.parseColor("#F0C23B")
    private val dp = 2.625f

    private fun ornate(w: Int = 240, h: Int = 200): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#F3E3C3")
        c.drawRoundRect(RectF(6f, 6f, w - 6f, h - 6f), 26f, 26f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = gold
        c.drawRoundRect(RectF(10f, 10f, w - 10f, h - 10f), 22f, 22f, p)
        p.style = Paint.Style.FILL
        for ((x, y) in listOf(28f to 28f, w - 28f to 28f, 28f to h - 28f, w - 28f to h - 28f)) {
            c.drawPath(Path().apply { moveTo(x, y - 16f); lineTo(x + 16f, y); lineTo(x, y + 16f); lineTo(x - 16f, y); close() }, p)
        }
        return b
    }

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    private fun render(p: Prefs): Pair<CatKeyboardView, Bitmap> {
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        return kv to b
    }

    @Test fun jsonRoundTrip() {
        val f = ImageFill("decal_1.png", nine = true, sx = 0.22f, sy = 0.4f)
        assertEquals(f, ImageFill.fromJson(JSONObject(f.toJson().toString())))
        assertTrue(f.ownsShape)
        // 兩個都勾的舊資料：以九宮格為準
        val both = ImageFill.fromJson(JSONObject().put("img", "a.png").put("shape", true).put("nine", true))!!
        assertTrue(both.nine)
        assertFalse(both.shape)
    }

    @Test fun spaceBarCornersMatchNarrowKey() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val name = Decals.storeBytes(ctx, Decals.toBytes(ornate()), maxPx = Decals.FILL_MAX_PX)!!
        val fill = ImageFill(name, nine = true, sx = 0.3f, sy = 0.3f)
        KeyStyles.save(p, KeyStyles(mapOf(
            "space" to KeyStyle(text = Color.parseColor("#5A3E2B"), bgImage = fill),
            "zy:ㄦ" to KeyStyle(text = Color.parseColor("#5A3E2B"), bgImage = fill),
        )))
        val (kv, b) = render(p)
        save(b, "key-nine-keyboard")
        val space = kv.keyRectOf(Layouts.ZHUYIN[4][3])!!
        val er = kv.keyRectOf(Layouts.ZHUYIN[4][4])!!
        fun box(r: RectF) = RectF(r.left + 3 * dp, r.top + 4 * dp + kv.floatH, r.right - 3 * dp, r.bottom - 4 * dp + kv.floatH)
        val sb = box(space)
        val eb = box(er)
        // 兩顆鍵一樣高：左上角那一塊（角的大小）應該一模一樣
        val k = sb.height() / 200f
        val cw = (240 * 0.3f * minOf(k, eb.width() / (240 * 0.6f))).toInt() - 2
        val ch = (200 * 0.3f * k).toInt() - 2
        // 兩顆鍵開始的位置不一定剛好在整數像素上，邊緣顏色會差一點點，所以比「顏色差不多」
        fun near(a: Int, c: Int) = Math.abs(Color.red(a) - Color.red(c)) < 40 && Math.abs(Color.green(a) - Color.green(c)) < 40 && Math.abs(Color.blue(a) - Color.blue(c)) < 40
        var diff = 0
        for (y in 2 until ch) for (x in 2 until cw) {
            if (!near(b.getPixel((sb.left + x).toInt(), (sb.top + y).toInt()), b.getPixel((eb.left + x).toInt(), (eb.top + y).toInt()))) diff++
        }
        // 兩顆鍵的起點不在同一個小數像素上，菱形、圓角的邊緣會差幾個點（整數位置的完全一致在 NineFrameTest 驗過）
        assertTrue("角落差了 $diff 個點", diff < cw * ch / 20)
        // 不變形：兩個角裡的金色菱形面積差不多
        fun goldIn(r: RectF): Int {
            var n = 0
            for (y in 2 until ch) for (x in 2 until cw) {
                val c = b.getPixel((r.left + x).toInt(), (r.top + y).toInt())
                if (Color.red(c) > 220 && Color.green(c) > 170 && Color.blue(c) < 120) n++
            }
            return n
        }
        val gs = goldIn(sb)
        val ge = goldIn(eb)
        assertTrue("菱形面積 $gs vs $ge", Math.abs(gs - ge) <= maxOf(4, ge / 10))
        // 中間拉長的地方沒有細縫：空白鍵中間那一排，每一點都是羊皮紙色
        val yMid = (sb.top + sb.height() * 0.5f).toInt()
        val paper = b.getPixel(sb.centerX().toInt() + 40, yMid)
        for (x in (sb.left + sb.width() * 0.2f).toInt()..(sb.left + sb.width() * 0.32f).toInt()) {
            assertTrue("x=$x 有細縫", near(b.getPixel(x, yMid), paper))
        }
        // 空白鍵中間是羊皮紙（中間被拉長，沒有金色菱形）
        val mid = b.getPixel(sb.centerX().toInt(), (sb.top + sb.height() * 0.85f).toInt())
        assertTrue(Color.red(mid) > 220 && Color.blue(mid) > 160)
    }

    @Test fun studioRadioAndAdjust() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val name = Decals.storeBytes(ctx, Decals.toBytes(ornate()), maxPx = Decals.FILL_MAX_PX)!!
        kv.editPicker!!.invoke(Layouts.ZHUYIN[4][3]) // 空白鍵
        ShadowLooper.idleMainLooper()
        StudioActivity::class.java.declaredMethods.first { it.name == "setFill" }.apply { isAccessible = true }.invoke(a, "space", ImageFill(name))
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        pickMode(root, "邊框")
        // 切到九宮格：直接打開調整邊角，預覽用這顆鍵的大小
        val d = ShadowDialog.getLatestDialog() as Dialog
        assertNotNull(find(d.window!!.decorView) { it is TextView && it.text == "調整按鍵圖片的邊角" })
        val nv = find(d.window!!.decorView) { it is NineSliceView } as NineSliceView
        nv.frame = nv.frame.copy(sx = 0.25f, sy = 0.35f)
        val v = d.window!!.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val db = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(db))
        save(db, "key-nine-dialog")
        (find(v) { it is Button && it.text == "完成" } as Button).performClick()
        ShadowLooper.idleMainLooper()
        val f = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map["space"]!!.bgImage!!
        assertTrue(f.nine)
        assertEquals(0.25f, f.sx, 0.001f)
        // 裁切換成調整邊角；底色、外框、圓角變灰
        assertNotNull(find(root) { it is Button && it.text == "📐 調整邊角" })
        assertNotNull(find(root) { it is TextView && it.text.toString().contains("現在用「邊框」") })
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val sb = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(sb))
        save(sb, "key-nine-studio")

        // 預覽泡泡也套同一個框
        val k = CatKeyboardView(ctx, Prefs(ctx, ThemeStore.WORK), Haptics(ctx))
        k.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        k.layout(0, 0, k.measuredWidth, k.measuredHeight)
        val e = Layouts.ZHUYIN[3][4] // ㄖ（套給它看泡泡）
        KeyStyles.save(Prefs(ctx, ThemeStore.WORK), KeyStyles(mapOf("zy:ㄖ" to KeyStyle(bgImage = f))))
        k.reloadPrefs()
        k.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        k.layout(0, 0, k.measuredWidth, k.measuredHeight)
        fun fld(n: String) = CatKeyboardView::class.java.getDeclaredField(n).apply { isAccessible = true }
        val kr = (fld("keyRects").get(k) as List<*>).first { it!!.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(it) == e }
        fld("pressed").set(k, kr)
        fld("previewLabel").set(k, "ㄖ")
        val pb = Bitmap.createBitmap(k.width, k.height, Bitmap.Config.ARGB_8888)
        k.draw(Canvas(pb))
        save(pb, "key-nine-preview")
        val r = k.keyRectOf(e)!!
        var goldPx = 0
        for (y in (k.floatH + r.top - 45 * dp * 1.1f).toInt() until (k.floatH + r.top).toInt()) for (x in (r.left - 20).toInt() until (r.right + 20).toInt()) {
            val c = pb.getPixel(x, y)
            if (Color.red(c) > 220 && Color.green(c) > 170 && Color.blue(c) < 120) goldPx++
        }
        assertTrue("泡泡有金框：$goldPx", goldPx > 40)
    }
}
