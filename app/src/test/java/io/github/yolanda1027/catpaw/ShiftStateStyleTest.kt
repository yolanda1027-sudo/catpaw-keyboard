package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertEquals
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

/** Shift 鍵可以分別設定「小寫時」「大寫時」的樣子；大寫時沒設定就跟小寫時一樣 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ShiftStateStyleTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val shift = Layouts.ENGLISH.flatten().first { it.act == Act.SHIFT }
    private val a = Layouts.ENGLISH.flatten().first { it.label == "a" }
    private val red = 0xFFE53935.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun resolveFallsBackToLowercase() {
        var s = KeyStyles.EMPTY.with(KeyStyles.idOf(shift), KeyStyle(bg = red, text = white))
        // 大寫時沒設定：跟小寫時一樣
        assertEquals(red, s.resolve(shift, shifted = true)!!.bg)
        s = s.with(KeyStyles.upIdOf(shift), KeyStyle(bg = blue))
        assertEquals(red, s.resolve(shift)!!.bg)
        assertEquals(blue, s.resolve(shift, shifted = true)!!.bg)
        // 大寫時沒改的地方照小寫時
        assertEquals(white, s.resolve(shift, shifted = true)!!.text)
        // 其他鍵不受影響
        assertNull(s.resolve(a, shifted = true))
        // 存檔、讀回來一樣
        assertEquals(s, KeyStyles.fromJson(s.toJson()))
    }

    private fun pixelAt(kv: CatKeyboardView, key: Key): Int {
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        val f = kv.keyImageFrame(key, null)!!.first
        return bmp.getPixel((f.left + f.width() * 0.15f).toInt(), (f.centerY() + kv.floatH).toInt())
    }

    @Test fun keyboardDrawsEachState() {
        val kv = CatKeyboardView(ctx, Prefs(ctx), Haptics(ctx))
        kv.rows = Layouts.ENGLISH
        kv.isChinese = false
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        kv.setKeyStylesLive(KeyStyles.EMPTY.with(KeyStyles.idOf(shift), KeyStyle(bg = red)).with(KeyStyles.upIdOf(shift), KeyStyle(bg = blue)))
        assertEquals(red, pixelAt(kv, shift))
        kv.shiftState = 1
        assertEquals(blue, pixelAt(kv, shift))
        kv.shiftState = 2 // 大寫鎖定也用大寫時的樣子
        assertEquals(blue, pixelAt(kv, shift))
    }

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    @Test fun studioEditsUppercaseSeparately() {
        Prefs(ctx).theme = "magic_yellow"
        val act = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val cls = StudioActivity::class.java
        cls.getDeclaredMethod("switchTab", String::class.java).apply { isAccessible = true }.invoke(act, "en")
        val kv = cls.getDeclaredField("kv").apply { isAccessible = true }.get(act) as CatKeyboardView
        val sk = kv.rows.flatten().first { it.act == Act.SHIFT }
        kv.editPicker!!.invoke(sk)
        ShadowLooper.idleMainLooper()
        fun btn(prefix: String) = views(act.window.decorView).first { it is Button && it.text.toString().startsWith(prefix) } as Button
        fun stored() = KeyStyles.load(Prefs(ctx, ThemeStore.WORK))
        val update = cls.declaredMethods.first { it.name == "update" && it.parameterTypes.size == 2 }.apply { isAccessible = true }
        fun setBg(c: Int) = update.invoke(act, { st: KeyStyle -> st.copy(bg = c) }, true)
        // 小寫時：改到平常的樣子
        setBg(red)
        assertEquals(red, stored().map[KeyStyles.idOf(sk)]!!.bg)
        assertEquals(0, kv.shiftState)
        // 切到大寫時：預覽變大寫，改的存到大寫時
        btn("大寫時").performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(1, kv.shiftState)
        setBg(blue)
        assertEquals(red, stored().map[KeyStyles.idOf(sk)]!!.bg)
        assertEquals(blue, stored().map[KeyStyles.upIdOf(sk)]!!.bg)
        assertTrue(btn("大寫時").text.contains("已設計"))
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val root = act.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        root.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/studio-shift-upper.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 改回跟小寫時一樣
        btn("↺ 大寫時改回").performClick()
        ShadowLooper.idleMainLooper()
        assertNull(stored().map[KeyStyles.upIdOf(sk)])
        // 離開 Shift 鍵：預覽回到小寫
        kv.editPicker!!.invoke(kv.rows.flatten().first { it.label == "a" })
        ShadowLooper.idleMainLooper()
        assertEquals(0, kv.shiftState)
    }
}
