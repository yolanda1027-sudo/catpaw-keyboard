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

/** 按鍵上的字可以刪掉（之後能恢復）、可以調透明度 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class LabelDeleteTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val xKey = Layouts.ZHUYIN[3][3] // ㄒ（小字 v）

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    @Test fun jsonRoundTrip() {
        val ls = LabelStyle(alpha = 0.4f, hidden = true)
        assertEquals(ls, LabelStyle.fromJson(JSONObject(ls.toJson().toString())))
        assertFalse(ls.isEmpty)
        assertTrue(LabelStyle().isEmpty)
    }

    @Test fun deleteAndRestoreInStudio() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.editPicker!!.invoke(xKey)
        ShadowLooper.idleMainLooper()
        assertTrue(kv.keyElements(xKey).any { it.decal.id == "label:sub" })
        // 選到小字 v，按工具列的「刪除」
        val action = StudioActivity::class.java.declaredMethods.first { it.name == "decalAction" }.apply { isAccessible = true }
        action.invoke(a, "delete", Decal("label:sub", "zy:ㄒ", "label:sub"))
        ShadowLooper.idleMainLooper()
        assertTrue(KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map["zy:ㄒ"]!!.labels!!["sub"]!!.hidden)
        // 放大的按鍵裡選不到它了；主字還在
        assertFalse(kv.keyElements(xKey).any { it.decal.id == "label:sub" })
        assertTrue(kv.keyElements(xKey).any { it.decal.id == "label:main" })
        // 面板上有「被刪掉的字」可以恢復
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text == "小字「v」" })
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(b))
        File("build/renders").mkdirs()
        File("build/renders/label-deleted.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        (find(root) { it is Button && it.text == "恢復" } as Button).performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(kv.keyElements(xKey).any { it.decal.id == "label:sub" })
        assertTrue(KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map["zy:ㄒ"]?.labels.isNullOrEmpty())
    }

    /** 按鍵的字墨水有幾個點（跟底色差很多的點） */
    private fun inkIn(k: Key, p: Prefs): Int {
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        val r = kv.keyRectOf(k)!!
        val bg = b.getPixel((r.left + 12).toInt(), (kv.floatH + r.centerY()).toInt())
        var n = 0
        for (y in (kv.floatH + r.top + 12).toInt() until (kv.floatH + r.centerY()).toInt()) for (x in (r.left + 12).toInt() until (r.right - 12).toInt()) {
            val c = b.getPixel(x, y)
            if (Math.abs(Color.red(c) - Color.red(bg)) + Math.abs(Color.green(c) - Color.green(bg)) + Math.abs(Color.blue(c) - Color.blue(bg)) > 120) n++
        }
        return n
    }

    @Test fun transparentLabelDisappears() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        val bo = Layouts.ZHUYIN[0][0]
        val before = inkIn(bo, p)
        assertTrue(before > 20)
        KeyStyles.save(p, KeyStyles(mapOf("zy:ㄅ" to KeyStyle(labels = mapOf("main" to LabelStyle(alpha = 0f))))))
        assertEquals("主字完全透明", 0, inkIn(bo, p))
    }

    @Test fun screenshotLabelSelected() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.editPicker!!.invoke(xKey)
        ShadowLooper.idleMainLooper()
        StudioActivity::class.java.getDeclaredField("decalSel").apply { isAccessible = true }.set(a, "label:sub")
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text.startsWith("透明度") })
        assertNotNull(find(root) { it is Button && it.text.startsWith("🗑 刪除這個字") })
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(b))
        File("build/renders/label-selected.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
