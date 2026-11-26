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

/** Shift 鍵小寫時、大寫時、大寫鎖定可以是完全不同的三套；沒設定的往回找（大寫鎖定 → 大寫 → 小寫） */
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

    @Test fun resolveFallsBack() {
        var s = KeyStyles.EMPTY.with(KeyStyles.idOf(shift), KeyStyle(bg = red, text = white))
        // 大寫、大寫鎖定沒設定：跟小寫時一樣
        assertEquals(red, s.resolve(shift, 1)!!.bg)
        assertEquals(red, s.resolve(shift, 2)!!.bg)
        s = s.with(KeyStyles.upIdOf(shift), KeyStyle(bg = blue))
        assertEquals(red, s.resolve(shift)!!.bg)
        assertEquals(blue, s.resolve(shift, 1)!!.bg)
        // 大寫時是自己完整的一套：小寫時的文字顏色不會混進來
        assertNull(s.resolve(shift, 1)!!.text)
        // 大寫鎖定沒設定：跟大寫時一樣
        assertEquals(blue, s.resolve(shift, 2)!!.bg)
        s = s.with(KeyStyles.lockIdOf(shift), KeyStyle(bg = white))
        assertEquals(white, s.resolve(shift, 2)!!.bg)
        assertEquals(blue, s.resolve(shift, 1)!!.bg)
        // 其他鍵不受影響
        assertNull(s.resolve(a, 1))
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
        kv.shiftState = 2 // 大寫鎖定沒設定：跟大寫時一樣
        assertEquals(blue, pixelAt(kv, shift))
        val green = 0xFF43A047.toInt()
        kv.setKeyStylesLive(KeyStyles.EMPTY.with(KeyStyles.idOf(shift), KeyStyle(bg = red)).with(KeyStyles.upIdOf(shift), KeyStyle(bg = blue))
            .with(KeyStyles.lockIdOf(shift), KeyStyle(bg = green)))
        assertEquals(green, pixelAt(kv, shift))
        kv.shiftState = 1
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
        update.invoke(act, { st: KeyStyle -> st.copy(text = white) }, true)
        // 第一次改：先照小寫時複製一份（底色還是紅的），之後分開
        assertEquals(red, stored().map[KeyStyles.upIdOf(sk)]!!.bg)
        assertEquals(white, stored().map[KeyStyles.upIdOf(sk)]!!.text)
        assertNull(stored().map[KeyStyles.idOf(sk)]!!.text)
        setBg(blue)
        assertEquals(red, stored().map[KeyStyles.idOf(sk)]!!.bg)
        assertEquals(blue, stored().map[KeyStyles.upIdOf(sk)]!!.bg)
        assertTrue(btn("大寫時").text.contains("已設計"))
        // 大寫鎖定：預覽變成鎖定，第一次改照大寫時複製
        btn("大寫鎖定").performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(2, kv.shiftState)
        val green = 0xFF43A047.toInt()
        setBg(green)
        assertEquals(green, stored().map[KeyStyles.lockIdOf(sk)]!!.bg)
        assertEquals(white, stored().map[KeyStyles.lockIdOf(sk)]!!.text)
        assertEquals(blue, stored().map[KeyStyles.upIdOf(sk)]!!.bg)
        btn("↺ 大寫鎖定改回").performClick()
        ShadowLooper.idleMainLooper()
        assertNull(stored().map[KeyStyles.lockIdOf(sk)])
        btn("大寫時").performClick()
        ShadowLooper.idleMainLooper()
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
        // 圖案：大寫時沿用小寫時的；要加圖案時先複製一份，之後兩邊分開
        Decals.save(Prefs(ctx, ThemeStore.WORK), listOf(Decal("d_snitch", KeyStyles.idOf(sk), "shape:star")))
        cls.getDeclaredField("decals").apply { isAccessible = true }.set(act, Decals.load(Prefs(ctx, ThemeStore.WORK)))
        kv.setDecalsLive(Decals.load(Prefs(ctx, ThemeStore.WORK)))
        val addTarget = cls.getDeclaredMethod("decalAddTarget", Key::class.java).apply { isAccessible = true }
        assertEquals(KeyStyles.upIdOf(sk), addTarget.invoke(act, sk))
        val all = Decals.load(Prefs(ctx, ThemeStore.WORK))
        assertEquals(1, all.count { it.target == KeyStyles.idOf(sk) })
        assertEquals(1, all.count { it.target == KeyStyles.upIdOf(sk) })
        assertTrue(all.first { it.target == KeyStyles.upIdOf(sk) }.id != "d_snitch")
        // 離開 Shift 鍵：預覽回到小寫
        kv.editPicker!!.invoke(kv.rows.flatten().first { it.label == "a" })
        ShadowLooper.idleMainLooper()
        assertEquals(0, kv.shiftState)
    }

    /** 圖案也分開：小寫是金色飛賊、大寫是飛天掃帚 */
    @Test fun eachStateHasOwnDecals() {
        val snitch = Decal("d_snitch", KeyStyles.idOf(shift), "shape:star", x = 0.5f, y = 0.5f, size = 0.5f, color = 0xFFF0C23B.toInt())
        val broom = Decal("d_broom", KeyStyles.upIdOf(shift), "shape:heart", x = 0.5f, y = 0.5f, size = 0.5f, color = 0xFF8D6E63.toInt())
        val p = Prefs(ctx)
        Decals.save(p, listOf(snitch, broom))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.rows = Layouts.ENGLISH
        kv.isChinese = false
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        fun shown() = kv.keyElements(shift).filter { !it.isLabel }.map { it.decal.id }
        assertEquals(listOf("d_snitch"), shown())
        kv.shiftState = 1
        assertEquals(listOf("d_broom"), shown())
        kv.shiftState = 2 // 大寫鎖定沒自己的：跟大寫時一樣
        assertEquals(listOf("d_broom"), shown())
        Decals.save(p, emptyList())
    }
}
