package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

/** 快捷符號列可以改顏色：整列底色、按鈕底色、文字、外框、圓角 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class QuickBarStyleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val style = QuickBarStyle(
        strip = Color.parseColor("#FFE3EC"), bg = Color.parseColor("#FF8FB1"),
        text = Color.WHITE, border = Color.parseColor("#E0607E"), radius = 1f,
    )

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    @Test fun jsonRoundTrip() {
        val ks = KeyStyles(mapOf("zy:ㄅ" to KeyStyle(bg = Color.RED)), TopBarStyle(text = Color.BLUE), style)
        val back = KeyStyles.fromJson(JSONObject(ks.toJson().toString()))
        assertEquals(ks, back)
        assertEquals(setOf("zy:ㄅ"), back.map.keys)
        assertEquals(style, back.with("zy:ㄆ", KeyStyle(text = Color.BLUE)).withTopBar(null).quick)
        assertNull(back.withQuick(QuickBarStyle()).quick)
    }

    @Test fun keyboardDrawsQuickBar() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        p.quickBar = true
        KeyStyles.save(p, KeyStyles(quick = style))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.quickItems = p.quickList()
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        save(b, "quickbar-colors")
        val top = (kv.floatH + 40 * 2.625f).toInt()
        val n = p.quickList().size
        val w = 1080 / n
        // 按鈕中間偏下（避開字）是粉紅色；兩顆按鈕中間的空隙是整列的淡粉色
        val btn = b.getPixel(w / 2, top + (40 * 2.625f * 0.8f).toInt())
        assertTrue(Color.red(btn) > 240 && Color.green(btn) in 120..160)
        assertEquals(style.strip, b.getPixel(w, top + (40 * 2.625f / 2).toInt()))
    }

    @Test fun studioPanelUndoAndExport() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        p.quickBar = false // 設定裡關著也可以改
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        assertTrue(kv.quickItems.isEmpty())
        kv.onQuickBarPicked!!.invoke()
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text == "正在編輯：快捷符號列" })
        assertTrue(kv.quickItems.isNotEmpty()) // 先顯示出來看
        assertTrue(kv.editQuickBar)
        val set = StudioActivity::class.java.declaredMethods.first { it.name == "setQuick" && it.parameterCount == 2 }.apply { isAccessible = true }
        set.invoke(a, { _: QuickBarStyle -> style }, true)
        ShadowLooper.idleMainLooper()
        assertEquals(style, KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).quick)
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        save(bmp, "quickbar-studio")

        // 匯出再匯入
        val json = ThemeIO.export(ctx, Prefs(ctx, ThemeStore.WORK), "粉紅快捷列", setOf(ThemeIO.Part.KEYS))
        KeyStyles.save(Prefs(ctx, ThemeStore.WORK), KeyStyles.EMPTY)
        ThemeIO.apply(ctx, Prefs(ctx, ThemeStore.WORK), ThemeIO.parse(json)!!)
        assertEquals(style, KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).quick)

        // 離開這頁：快捷列關著的話就收起來
        StudioActivity::class.java.getDeclaredField("quickSelected").apply { isAccessible = true }.setBoolean(a, false)
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        assertTrue(kv.quickItems.isEmpty())

        // 上一步
        StudioActivity::class.java.getDeclaredMethod("undo").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        assertNull(KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).quick)
    }

    @Test fun stitchBorderOnQuickBar() {
        val st = style.copy(bg = Color.parseColor("#FF8FB1"), border = Color.parseColor("#FFF6E8"), radius = 0.4f,
            borderStyle = "stitch", borderWidth = 2f, borderInset = 4f)
        assertEquals(st, QuickBarStyle.fromJson(JSONObject(st.toJson().toString())))
        assertNull(QuickBarStyle.fromJson(JSONObject().put("borderStyle", "zigzag")))

        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        p.quickBar = true
        KeyStyles.save(p, KeyStyles(quick = st))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.quickItems = p.quickList()
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        save(b, "quickbar-stitch")
        // 第一顆按鈕：上緣是底色，往內 4dp 才有縫線（在按鈕上緣中間那一段找）
        val dp = 2.625f
        val n = p.quickList().size
        val w = 1080f / n
        val top = kv.floatH + 40 * dp + 3 * dp // 上方列下面、按鈕框開始
        val boxTop = top + 4 * dp
        fun hasLine(y: Float): Boolean {
            for (x in (w * 0.35f).toInt()..(w * 0.65f).toInt()) {
                val c = b.getPixel(x, y.toInt())
                if (Color.red(c) > 235 && Color.green(c) > 220 && Color.blue(c) > 205) return true // 米白縫線
            }
            return false
        }
        assertFalse(hasLine(boxTop + 1))
        assertTrue(hasLine(boxTop + 4 * dp))

        // 工作室：快捷列頁有外框樣式，選縫線多一個離邊距離
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val skv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        KeyStyles.save(Prefs(ctx, ThemeStore.WORK), KeyStyles.EMPTY)
        StudioActivity::class.java.getDeclaredField("styles").apply { isAccessible = true }.set(a, KeyStyles.EMPTY)
        skv.onQuickBarPicked!!.invoke()
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text == "外框樣式" })
        assertNull(find(root) { it is TextView && it.text.startsWith("縫線離邊距離") })
        (find(root) { it is Button && it.text.toString().endsWith("縫線") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals("stitch", KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).quick!!.borderStyle)
        assertNotNull(find(root) { it is TextView && it.text.startsWith("縫線離邊距離") })
    }
}
