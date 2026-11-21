package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

/** 主題工作室：顏色、圖片可以調透明度 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class OpacityTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val red = Color.rgb(230, 20, 20)
    private val blue = Color.rgb(20, 40, 230)

    private fun solid(c: Int) = Decals.storeBitmap(ctx, Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(c) })

    private fun render(p: Prefs): Pair<CatKeyboardView, Bitmap> {
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        return kv to b
    }

    /** 按鍵正中間偏下（避開字）的顏色 */
    private fun keyPixel(kv: CatKeyboardView, b: Bitmap, k: Key): Int {
        val r = kv.keyRectOf(k)!!
        return b.getPixel((r.left + r.width() * 0.2f).toInt(), (r.top + r.height() * 0.75f + kv.floatH).toInt())
    }

    @Test fun jsonKeepsAlpha() {
        val f = ImageFill("a.png", alpha = 0.4f)
        assertEquals(0.4f, ImageFill.fromJson(JSONObject(f.toJson().toString()))!!.alpha, 1e-4f)
        assertEquals(1f, ImageFill.fromJson(JSONObject(ImageFill("a.png").toJson().toString()))!!.alpha, 0f)
        assertTrue("不透明的不多存一個欄位", !ImageFill("a.png").toJson().has("alpha"))
        val q = QuickBarStyle(frameAlpha = 0.5f)
        assertEquals(0.5f, QuickBarStyle.fromJson(JSONObject(q.toJson().toString()))!!.frameAlpha!!, 1e-4f)
    }

    @Test fun keyImageAndColorAreSeeThrough() {
        val p = Prefs(ctx)
        val bo = Layouts.ZHUYIN[0][0]
        val po = Layouts.ZHUYIN[1][0]
        // 鍵盤背景：紅色圖片；ㄅ：藍色底圖 50%（底色設透明，才透得到背景）；ㄆ：藍色底色 50%
        val bg = KeyStyle(bgImage = ImageFill(solid(red)))
        KeyStyles.save(p, KeyStyles.EMPTY.with(KeyStyles.KEYBOARD, bg)
            .with(KeyStyles.idOf(bo), KeyStyle(bg = Color.TRANSPARENT, bgImage = ImageFill(solid(blue), alpha = 0.5f)))
            .with(KeyStyles.idOf(po), KeyStyle(bg = (0x80 shl 24) or (blue and 0xFFFFFF))))
        val (kv, b) = render(p)
        for (k in listOf(bo, po)) {
            val c = keyPixel(kv, b, k)
            assertTrue("${k.label}：紅和藍混在一起 ${Integer.toHexString(c)}", Color.red(c) in 90..170 && Color.blue(c) in 90..170)
        }
        // 背景圖片本身 30%：混進主題原本的深色背景，紅色變淡
        KeyStyles.save(p, KeyStyles.EMPTY.with(KeyStyles.KEYBOARD, KeyStyle(bgImage = ImageFill(solid(red), alpha = 0.3f))))
        val (kv2, b2) = render(p)
        val gap = b2.getPixel(3, (kv2.keyRectOf(bo)!!.top + kv2.floatH + 4).toInt())
        assertTrue("背景圖 30%：${Integer.toHexString(gap)}", Color.red(gap) in 60..140)
    }

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    @Test fun colorDialogReturnsAlpha() {
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        var picked: Int? = null
        ColorDialog.show(a, "底色", blue, withAlpha = true) { picked = it }
        ShadowLooper.idleMainLooper()
        val d = ShadowAlertDialog.getLatestAlertDialog()
        val all = views(d.window!!.decorView)
        assertNotNull(all.firstOrNull { it is TextView && it.text.toString() == "透明度：100%" })
        val bar = all.filterIsInstance<SeekBar>().single()
        bar.progress = 40
        val l = SeekBar::class.java.getDeclaredField("mOnSeekBarChangeListener").apply { isAccessible = true }.get(bar) as SeekBar.OnSeekBarChangeListener
        l.onProgressChanged(bar, 40, true)
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(102, Color.alpha(picked!!))
        assertEquals(blue and 0xFFFFFF, picked!! and 0xFFFFFF)
        // 設定頁用的（沒開透明度）：一律不透明
        ColorDialog.show(a, "底色", blue) { picked = it }
        ShadowLooper.idleMainLooper()
        val d2 = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(views(d2.window!!.decorView).none { it is SeekBar })
        d2.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(255, Color.alpha(picked!!))
    }

    @Test fun studioImageRowHasSlider() {
        val p = Prefs(ctx)
        p.theme = "classic"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work = Prefs(ctx, ThemeStore.WORK)
        val img = solid(blue)
        val styles = KeyStyles.EMPTY.with(KeyStyles.KEYBOARD, KeyStyle(bgImage = ImageFill(img)))
        KeyStyles.save(work, styles)
        StudioActivity::class.java.getDeclaredField("styles").apply { isAccessible = true }.set(a, styles)
        StudioActivity::class.java.getDeclaredField("bgSelected").apply { isAccessible = true }.setBoolean(a, true)
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper()
        assertNotNull(views(a.window.decorView).firstOrNull { it is TextView && it.text.toString() == "圖片透明度：100%" })
        StudioActivity::class.java.getDeclaredMethod("setFillLive", String::class.java, ImageFill::class.java).apply { isAccessible = true }
            .invoke(a, KeyStyles.KEYBOARD, ImageFill(img, alpha = 0.6f))
        assertEquals(0.6f, KeyStyles.load(work).keyboardImage!!.alpha, 1e-4f)
    }
}
