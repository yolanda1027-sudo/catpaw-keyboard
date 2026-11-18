package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 上方列（中／En、選字列）可以自己改：底色、選中的底色、文字（選中／沒選中）、圖示 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TopBarStyleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val brown = Color.parseColor("#5B3A29")
    private val style = TopBarStyle(
        bg = brown, tab = Color.parseColor("#FF8FB1"), text = Color.WHITE,
        textDim = Color.parseColor("#E6CBB8"), icon = Color.parseColor("#FFD166"),
    )

    private fun save(bmp: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(kv: CatKeyboardView, name: String): Bitmap {
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        save(b, name)
        return b
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    @Test fun jsonRoundTrip() {
        val ks = KeyStyles(mapOf("zy:ㄅ" to KeyStyle(bg = Color.RED)), style)
        val back = KeyStyles.fromJson(JSONObject(ks.toJson().toString()))
        assertEquals(ks, back)
        assertEquals(setOf("zy:ㄅ"), back.map.keys) // 上方列不會被當成一顆鍵
        assertEquals(style, back.with("zy:ㄆ", KeyStyle(text = Color.BLUE)).topBar) // 改別的鍵不會弄丟
        assertNull(KeyStyles.fromJson(JSONObject(ks.withTopBar(TopBarStyle()).toJson().toString())).topBar)
    }

    @Test fun keyboardDrawsCustomTopBar() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        KeyStyles.save(p, KeyStyles(topBar = style))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        val b = shot(kv, "topbar-zh-en")
        val top = kv.floatH.toInt()
        // 上方列空白的地方是咖啡色
        assertEquals(brown, b.getPixel(b.width / 2, top + 6))
        // 「中」那塊是粉紅色膠囊
        val tabW = kv.width / 8
        val pink = b.getPixel(tabW / 6, top + (40 * 2.625f / 2).toInt())
        assertTrue(Color.red(pink) > 230 && Color.green(pink) < 180)
        // 下面的按鍵區不受影響（還是奶油黃）
        assertTrue(Color.red(b.getPixel(4, b.height - 4)) > 200)

        kv.candidates = listOf("你好", "妳好", "擬好", "你", "妳")
        shot(kv, "topbar-candidates")
        kv.candidates = emptyList()
        kv.symbolTabs = listOf<String?>(null, "🖼️", "😀", "👋", "🐱", "🍓")
        kv.symbolTab = 2
        shot(kv, "topbar-symbol-tabs")
    }

    @Test fun clearTopBarLetsImageReachTheTop() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        val img = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.parseColor("#7BC6A4")) }
        val name = Decals.storeBitmap(ctx, img)
        KeyStyles.save(p, KeyStyles(mapOf(KeyStyles.KEYBOARD to KeyStyle(bgImage = ImageFill(name))), TopBarStyle(bg = Color.TRANSPARENT, text = Color.WHITE)))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        val b = shot(kv, "topbar-clear-with-image")
        assertEquals(0f, kv.keyboardImageFrame().top)
        assertEquals(Color.parseColor("#7BC6A4"), b.getPixel(b.width / 2, kv.floatH.toInt() + 6))
    }

    @Test fun studioTopBarPanelUndoAndExport() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        // 點最上面那一列
        kv.onTopBarPicked!!.invoke()
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        assertNotNull(find(root) { it is TextView && it.text == "正在編輯：上方列" })
        assertTrue(kv.editTopBar)
        for (label in listOf("底色", "選中的底色（「中」底下那塊）", "文字（選中的「中」、候選字）", "文字（沒選中的「En」）", "圖示（剪貼簿、▽）"))
            assertNotNull(label, find(root) { it is TextView && it.text == label })
        val set = StudioActivity::class.java.declaredMethods.first { it.name == "setTopBar" }.apply { isAccessible = true }
        set.invoke(a, { _: TopBarStyle -> style })
        ShadowLooper.idleMainLooper()
        assertEquals(style, KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).topBar)
        // 截圖：工作室裡的樣子
        Ui.spaceButtons(root, 8)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        save(bmp, "topbar-studio")

        // 匯出再匯入：跟著走
        val json = ThemeIO.export(ctx, Prefs(ctx, ThemeStore.WORK), "咖啡上方列", setOf(ThemeIO.Part.KEYS))
        KeyStyles.save(Prefs(ctx, ThemeStore.WORK), KeyStyles.EMPTY)
        ThemeIO.apply(ctx, Prefs(ctx, ThemeStore.WORK), ThemeIO.parse(json)!!)
        assertEquals(style, KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).topBar)

        // 上一步：回到沒改的樣子
        StudioActivity::class.java.getDeclaredMethod("undo").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        assertNull(KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).topBar)
    }
}
