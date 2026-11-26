package io.github.yolanda1027.catpaw

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 主題工作室的對齊：對齊所在的框、多選彼此對齊、等距排開 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ElementAlignTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val frame = RectF(0f, 0f, 100f, 50f)
    private fun item(id: String, cx: Float, cy: Float, hw: Float = 5f, hh: Float = 5f) = ElementAlign.Item(id, cx, cy, hw, hh)

    @Test fun oneItemAlignsToFrame() {
        val a = item("a", 40f, 20f)
        assertEquals(5f to 20f, ElementAlign.apply(listOf(a), ElementAlign.Op.LEFT, frame)["a"])
        assertEquals(95f to 20f, ElementAlign.apply(listOf(a), ElementAlign.Op.RIGHT, frame)["a"])
        assertEquals(50f to 20f, ElementAlign.apply(listOf(a), ElementAlign.Op.HCENTER, frame)["a"])
        assertEquals(40f to 45f, ElementAlign.apply(listOf(a), ElementAlign.Op.BOTTOM, frame)["a"])
        assertEquals(40f to 25f, ElementAlign.apply(listOf(a), ElementAlign.Op.VCENTER, frame)["a"])
    }

    @Test fun rotatedUsesOuterBox() {
        // 寬 20、高 10 轉 90 度：外框變成寬 10、高 20
        val (hw, hh) = ElementAlign.halfExtents(20f, 10f, 90f)
        assertEquals(5f, hw, 0.01f)
        assertEquals(10f, hh, 0.01f)
    }

    @Test fun severalAlignToEachOther() {
        val items = listOf(item("a", 20f, 10f), item("b", 60f, 30f, hw = 10f), item("c", 80f, 20f))
        val left = ElementAlign.apply(items, ElementAlign.Op.LEFT, frame)
        assertEquals("左緣對齊最左邊的 15：中心在 20", 20f, left.getValue("a").first, 0.01f)
        assertEquals(25f, left.getValue("b").first, 0.01f) // 寬的那個：左緣也在 15
        val mid = ElementAlign.apply(items, ElementAlign.Op.VCENTER, frame)
        assertEquals("選到的範圍上 5 下 35 → 中間 20", 20f, mid.getValue("b").second, 0.01f)
        // 等距：最左和最右不動，中間的平均分開（間隔都一樣）
        val sp = ElementAlign.apply(items, ElementAlign.Op.HSPACE, frame)
        val gap1 = (sp.getValue("b").first - 10f) - (sp.getValue("a").first + 5f)
        val gap2 = (sp.getValue("c").first - 5f) - (sp.getValue("b").first + 10f)
        assertEquals(gap1, gap2, 0.01f)
        assertEquals(20f, sp.getValue("a").first, 0.01f)
        assertEquals(80f, sp.getValue("c").first, 0.01f)
    }


    @Test fun studioArrangeAndAlign() {
        Prefs(ctx).theme = "classic"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val cls = StudioActivity::class.java
        val kv = cls.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val root = a.window.decorView
        val key = kv.rows[2][3] // ㄑ
        val keyId = KeyStyles.idOf(key)
        val d1 = Decal("h1", keyId, Decal.SHAPE + "heart", x = 0.3f, y = 0.2f, size = 0.3f)
        val d2 = Decal("s1", keyId, Decal.SHAPE + "star", x = 0.7f, y = 0.8f, size = 0.4f)
        cls.getDeclaredMethod("saveDecals", List::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(a, listOf(d1, d2), false)
        kv.editPicker!!.invoke(key)
        ShadowLooper.idleMainLooper()
        fun check(name: String) { (find(root) { it is CheckBox && it.text.toString().contains(name) } as CheckBox).performClick(); ShadowLooper.idleMainLooper() }
        fun press(label: String) { (find(root) { it is Button && it.text == label } as Button).performClick(); ShadowLooper.idleMainLooper() }
        val hspace = find(root) { it is Button && it.text == "水平等距" } as Button
        assertFalse("沒勾的時候不能按", hspace.isEnabled)
        check("愛心")
        check("星星")
        // 兩個彼此靠左：左緣一樣
        press("靠左")
        val r = kv.keyRectOf(key)!!
        fun left(id: String): Float {
            val d = Decals.load(Prefs(ctx, ThemeStore.WORK)).first { it.id == id }
            val (w, _) = Decals.sizeOf(d, r.height(), null)
            return r.left + d.x * r.width() - w / 2
        }
        assertEquals(left("h1"), left("s1"), 0.5f)
        assertEquals("對齊的是比較左邊那個的左緣", r.left + 0.3f * r.width() - 0.3f * r.height() / 2, left("h1"), 0.5f)
        // 截圖：放大的鍵上兩個都有粉紅虛線框
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val shot = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(shot))
        File("build/renders").mkdirs()
        File("build/renders/align-arrange.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 面板捲到「排列」那一段
        val title = find(root) { it is android.widget.TextView && it.text == "排列" }!!
        val sv = generateSequence(title.parent as View?) { it.parent as? View }.first { it is android.widget.ScrollView } as android.widget.ScrollView
        sv.scrollTo(0, (title.parent as View).let { title.top } - 20)
        root.layout(0, 0, 1080, 2340)
        val shot2 = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(shot2))
        File("build/renders/align-arrange-panel.png").outputStream().use { shot2.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 只勾主字：水平置中＝對齊這顆鍵
        check("愛心")
        check("星星")
        check("主字")
        press("靠右")
        val ls = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map[keyId]!!.labels!!.getValue("main")
        assertTrue("主字往右移：dx=${ls.dx}", ls.dx > 0.1f)
        press("水平置中")
        // 置中剛好回到原本的位置：位置設定就清掉了（沒有 labels 也算 dx = 0）
        val dx2 = KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map[keyId]?.labels?.get("main")?.dx ?: 0f
        assertEquals("置中：回到正中間", 0f, dx2, 0.02f)
    }
}
