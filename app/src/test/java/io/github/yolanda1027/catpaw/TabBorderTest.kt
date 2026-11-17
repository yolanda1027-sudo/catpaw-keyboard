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
import kotlin.math.abs

/** 上方列「選中那塊」（中、第一個候選字、目前的分類）可以加外框：實線／虛線／點點／縫線 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TabBorderTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dp = 2.625f
    private val gold = Color.parseColor("#F0C23B")
    private val brown = Color.parseColor("#3A2A20")

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(kv: CatKeyboardView): Bitmap {
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        return b
    }

    private fun near(a: Int, b: Int, tol: Int = 40) =
        abs(Color.red(a) - Color.red(b)) <= tol && abs(Color.green(a) - Color.green(b)) <= tol && abs(Color.blue(a) - Color.blue(b)) <= tol

    /** 「中」那塊的上緣往下 y 個像素，在中間那一段有沒有金色 */
    private fun goldAt(b: Bitmap, kv: CatKeyboardView, y: Float): Boolean {
        val top = kv.floatH + 7 * dp
        val tabW = 1080 * 0.2f
        for (x in (tabW * 0.3f).toInt()..(tabW * 0.7f).toInt()) if (near(b.getPixel(x, (top + y).toInt()), gold)) return true
        return false
    }

    @Test fun jsonRoundTrip() {
        val t = TopBarStyle(tab = brown, tabBorder = gold, tabBorderWidth = 2.5f, tabBorderStyle = "stitch", tabBorderInset = 3.5f)
        assertEquals(t, TopBarStyle.fromJson(JSONObject(t.toJson().toString())))
        assertEquals(null, TopBarStyle.fromJson(JSONObject().put("tabBorderStyle", "zigzag")))
    }

    @Test fun capsuleWithStitchAndBorderOnly() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        // 深咖啡膠囊＋金色縫線
        KeyStyles.save(p, KeyStyles(topBar = TopBarStyle(tab = brown, text = gold, tabBorder = gold, tabBorderStyle = "stitch", tabBorderWidth = 2f, tabBorderInset = 3f)))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        val b = shot(kv)
        save(b, "tab-border-stitch")
        assertFalse("縫線不會貼著邊", goldAt(b, kv, 1f))
        assertTrue("往內 3dp 有金色縫線", goldAt(b, kv, 3 * dp))
        // 上緣那一排是深咖啡底色
        assertTrue(near(b.getPixel((1080 * 0.1f).toInt(), (kv.floatH + 7 * dp + 1).toInt()), brown, 20))

        // 候選字、表情分類也是同一塊
        kv.candidates = listOf("你好", "妳好", "擬好", "你", "妳")
        save(shot(kv), "tab-border-candidates")
        kv.candidates = emptyList()
        kv.symbolTabs = listOf<String?>(null, "🖼️", "😀", "👋", "🐱", "🍓")
        kv.symbolTab = 2
        save(shot(kv), "tab-border-symbol-tabs")

        // 只設外框（魔法主題原本選中那塊是透明的）：只畫一圈線，不會變成實心
        KeyStyles.save(p, KeyStyles(topBar = TopBarStyle(tabBorder = gold, tabBorderWidth = 2f)))
        val kv2 = CatKeyboardView(ctx, p, Haptics(ctx))
        val b2 = shot(kv2)
        save(b2, "tab-border-only")
        assertTrue("貼著邊有金色實線", goldAt(b2, kv2, 1.5f))
        val inside = b2.getPixel((1080 * 0.1f).toInt(), (kv2.floatH + 7 * dp + 12 * dp).toInt())
        assertFalse("膠囊裡面不是實心的金色", near(inside, gold, 30))
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    @Test fun studioPanel() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.onTopBarPicked!!.invoke()
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text == "選中那塊的外框" })
        assertNotNull(find(root) { it is TextView && it.text == "外框樣式" })
        (find(root) { it is Button && it.text.toString().endsWith("縫線") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals("stitch", KeyStyles.load(Prefs(ctx)).topBar!!.tabBorderStyle)
        assertNotNull(find(root) { it is TextView && it.text.startsWith("縫線離邊距離") })
        // 上一步：回到沒改的樣子
        StudioActivity::class.java.getDeclaredMethod("undo").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        assertEquals(null, KeyStyles.load(Prefs(ctx)).topBar)
    }
}
