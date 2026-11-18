package io.github.yolanda1027.catpaw

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 快捷列按鈕圖片（九宮格）：四個角不變形，按鈕變寬只拉長中間 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NineFrameTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val gold = Color.parseColor("#F0C23B")

    /** 雕花框：深咖啡底、金邊、四個角有金色菱形 */
    private fun ornate(w: Int = 300, h: Int = 120): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#3A2A20")
        c.drawRoundRect(RectF(6f, 6f, w - 6f, h - 6f), 22f, 22f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = gold
        c.drawRoundRect(RectF(9f, 9f, w - 9f, h - 9f), 20f, 20f, p)
        p.strokeWidth = 2f
        c.drawRoundRect(RectF(18f, 18f, w - 18f, h - 18f), 14f, 14f, p)
        p.style = Paint.Style.FILL
        for ((x, y) in listOf(22f to 22f, w - 22f to 22f, 22f to h - 22f, w - 22f to h - 22f)) {
            c.drawPath(Path().apply { moveTo(x, y - 14f); lineTo(x + 14f, y); lineTo(x, y + 14f); lineTo(x - 14f, y); close() }, p)
        }
        return b
    }

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    private fun render(v: View, name: String) {
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        save(b, name)
    }

    @Test fun cornersStayTheSame() {
        val img = ornate()
        val narrow = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888)
        val wide = Bitmap.createBitmap(700, 80, Bitmap.Config.ARGB_8888)
        NineFrame.draw(Canvas(narrow), img, 0.25f, 0.4f, RectF(0f, 0f, 200f, 80f))
        NineFrame.draw(Canvas(wide), img, 0.25f, 0.4f, RectF(0f, 0f, 700f, 80f))
        // 左上角那一塊（圖片 25% 寬 × 40% 高，縮放 80/120）一模一樣
        val cw = (300 * 0.25f * 80 / 120).toInt() - 1
        val ch = (120 * 0.4f * 80 / 120).toInt() - 1
        for (y in 0 until ch) for (x in 0 until cw) assertEquals("($x,$y)", narrow.getPixel(x, y), wide.getPixel(x, y))
        // 右上角也一樣（從右邊數）
        for (y in 0 until ch) for (x in 1..cw) assertEquals(narrow.getPixel(200 - x, y), wide.getPixel(700 - x, y))

        val f = NineFrame("decal_1.png", 0.2f, 0.35f)
        assertEquals(f, NineFrame.fromJson(JSONObject(f.toJson().toString())))
        assertNull(NineFrame.fromJson(JSONObject().put("img", "../x.png")))
        val q = QuickBarStyle(text = gold, frame = f)
        assertEquals(q, QuickBarStyle.fromJson(JSONObject(q.toJson().toString())))
    }

    @Test fun studioUploadAdjustExportAndCleanup() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        p.quickBar = true
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.onQuickBarPicked!!.invoke()
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text == "按鈕圖片" })
        (find(root) { it is Button && it.text == "📷 上傳圖片" } as Button).performClick()
        val req = shadowOf(a).nextStartedActivityForResult
        val f = File(ctx.cacheDir, "frame.png").apply { outputStream().use { ornate().compress(Bitmap.CompressFormat.PNG, 100, it) } }
        shadowOf(a).receiveResult(req.intent, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(f)))
        ShadowLooper.idleMainLooper()
        // 上傳完直接打開「調整邊角」
        val d = ShadowDialog.getLatestDialog() as Dialog
        val nv = find(d.window!!.decorView) { it is NineSliceView } as NineSliceView
        nv.frame = nv.frame.copy(sx = 0.2f, sy = 0.38f)
        render(d.window!!.decorView, "nine-dialog")
        (find(d.window!!.decorView) { it is Button && it.text == "完成" } as Button).performClick()
        ShadowLooper.idleMainLooper()
        val q = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).quick!!
        assertEquals(0.2f, q.frame!!.sx, 0.001f)
        // 按鈕底色、外框、圓角變灰
        assertNotNull(find(root) { it is TextView && it.text.startsWith("💡 按鈕底色、外框、圓角是灰的") })
        render(root, "nine-studio")

        // 鍵盤上的樣子
        StudioActivity::class.java.declaredMethods.first { it.name == "setQuick" && it.parameterCount == 2 }.apply { isAccessible = true }
            .invoke(a, { s: QuickBarStyle -> s.copy(text = gold) }, true)
        val k = CatKeyboardView(ctx, Prefs(ctx, ThemeStore.WORK), Haptics(ctx))
        k.quickItems = Prefs(ctx, ThemeStore.WORK).quickList()
        k.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        k.layout(0, 0, k.measuredWidth, k.measuredHeight)
        val b = Bitmap.createBitmap(k.measuredWidth, k.measuredHeight, Bitmap.Config.ARGB_8888)
        k.draw(Canvas(b))
        save(b, "nine-keyboard")
        // 第一顆按鈕裡有金色（框）
        var goldPx = 0
        val top = (k.floatH + 40 * 2.625f).toInt()
        for (y in top until top + 100) for (x in 0 until 150) {
            val c = b.getPixel(x, y)
            if (Color.red(c) > 220 && Color.green(c) > 170 && Color.blue(c) < 120) goldPx++
        }
        assertTrue("按鈕有金框：$goldPx", goldPx > 50)

        // 離開工作室時不會被清掉；主題匯出再匯入也在
        Decals.cleanup(ctx, Decals.load(Prefs(ctx, ThemeStore.WORK)))
        assertTrue(File(Decals.dir(ctx), q.frame.img).exists())
        val json = ThemeIO.export(ctx, Prefs(ctx, ThemeStore.WORK), "雕花快捷列", setOf(ThemeIO.Part.KEYS))
        KeyStyles.save(Prefs(ctx, ThemeStore.WORK), KeyStyles.EMPTY)
        Decals.dir(ctx).listFiles()!!.forEach { it.delete() }
        val warn = ThemeIO.apply(ctx, Prefs(ctx, ThemeStore.WORK), ThemeIO.parse(json)!!)
        assertTrue(warn.toString(), warn.isEmpty())
        val back = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).quick!!.frame!!
        assertTrue(File(Decals.dir(ctx), back.img).exists())
        assertEquals(0.38f, back.sy, 0.001f)
    }
}
