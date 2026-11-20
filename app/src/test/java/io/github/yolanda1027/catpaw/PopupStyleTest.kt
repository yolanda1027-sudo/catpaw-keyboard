package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

/** 長按選單、放大字泡泡的顏色：整個主題一組，存檔、匯出入、畫出來、工作室面板 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PopupStyleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val brown = Color.parseColor("#6B3A1F")
    private val gold = Color.parseColor("#E8B04A")
    private val cream = Color.parseColor("#FFF4DC")
    private val style = PopupStyle(bg = brown, sel = gold, text = cream, bubbleBg = cream, bubbleText = brown)

    @Test fun jsonAndExport() {
        val ks = KeyStyles(popup = style)
        assertEquals(style, KeyStyles.fromJson(JSONObject(ks.toJson().toString())).popup)
        // 匯出、匯入：跟著主題檔走
        val json = ThemeStore.withTemp(ctx, ThemeStore.factory("classic")) { t ->
            KeyStyles.save(t, ks)
            ThemeIO.export(ctx, t, "棕色選單", ThemeIO.Part.values().toSet())
        }
        val back = ThemeStore.withTemp(ctx, ThemeStore.factory("classic")) { t ->
            ThemeIO.apply(ctx, t, ThemeIO.parse(json)!!)
            KeyStyles.load(t).popup
        }
        assertEquals(style, back)
    }

    private fun keyboard(p: Prefs): CatKeyboardView {
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        return kv
    }

    private fun sample(kv: CatKeyboardView, key: Key?): Bitmap {
        val b = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        kv.drawBubbleSample(c, RectF(0f, 0f, 140f, 200f), key, "ㄜ")
        kv.drawPopupSample(c, RectF(200f, 40f, 590f, 160f), listOf("K", "ㄜ", "k"), 1, key)
        return b
    }

    @Test fun drawsTheChosenColors() {
        val p = Prefs(ctx)
        val key = Layouts.ZHUYIN.flatten().first { it.label == "ㄜ" }
        // 沒設：照主題原本的（經典深色的選中顏色）
        val kv0 = keyboard(p)
        val b0 = sample(kv0, key)
        assertEquals(kv0.popupMenuColors(key)[1], b0.getPixel(395, 100))
        // 設了：選單底色、選中那格、泡泡底色都照設的
        KeyStyles.save(p, KeyStyles(popup = style))
        val kv = keyboard(p)
        val b = sample(kv, key)
        save(b, "popup-style-sample")
        assertEquals("選單底色", brown, b.getPixel(215, 100))
        assertEquals("選中那格", gold, b.getPixel(395, 60))
        assertEquals("泡泡底色", cream, b.getPixel(70, 20))
        // 改過樣子的鍵（羊皮紙底圖那種）：設了顏色還是照設的
        KeyStyles.save(p, KeyStyles(popup = style).with("@zhuyin", KeyStyle(bg = Color.parseColor("#F2C48E"))))
        val b2 = sample(keyboard(p), key)
        assertEquals(brown, b2.getPixel(215, 100))
        assertEquals(gold, b2.getPixel(395, 60))
    }

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    @Test fun studioPanel() {
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        (views(a.window.decorView).first { it is Button && it.text.toString().startsWith("💬 長按選單") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        val texts = views(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), "長按選單" in texts && "選中那一格" in texts && "按下時跳出的放大字" in texts)
        StudioActivity::class.java.getDeclaredMethod("setPopup", Function1::class.java).apply { isAccessible = true }
            .invoke(a, { p: PopupStyle -> p.copy(sel = gold) })
        ShadowLooper.idleMainLooper()
        assertEquals(gold, KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).popup?.sel)
        assertNotNull(views(a.window.decorView).firstOrNull { it is Button && it.text == "全部改回原本的樣子" })
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        save(bmp, "popup-style-panel")
    }

    private fun save(b: Bitmap, name: String) {
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
