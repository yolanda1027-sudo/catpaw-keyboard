package io.github.yolanda1027.catpaw

import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** 內建按鍵動畫（冒小貓爪…）可以自己選顏色；沒選跟著主題 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class EffectColorTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val mint = Color.rgb(80, 210, 180)

    private fun colors(fx: PressEffects): List<Int> {
        val list = PressEffects::class.java.getDeclaredField("particles").apply { isAccessible = true }.get(fx) as List<*>
        return list.map { p -> p!!.javaClass.getDeclaredField("color").apply { isAccessible = true }.getInt(p) }
    }

    @Test fun pawUsesChosenColor() {
        val fx = PressEffects(2.625f)
        val r = RectF(0f, 0f, 100f, 100f)
        fx.add("paw", r, Color.rgb(243, 166, 188), dark = true, color = mint)
        assertEquals(listOf(mint), colors(fx))
        // 沒選：跟著主題（強調色）
        val fx2 = PressEffects(2.625f)
        fx2.add("paw", r, Color.rgb(243, 166, 188), dark = true)
        assertEquals(PressEffects.defaultColor("paw", Color.rgb(243, 166, 188), true), colors(fx2).single())
        // 雪花、音符這種：主要顏色也換掉
        val fx3 = PressEffects(2.625f)
        fx3.add("snow", r, 0, dark = true, color = mint)
        assertEquals(setOf(mint), colors(fx3).toSet())
    }

    @Test fun jsonAndExport() {
        val e = KeyEffect("paw", color = mint)
        assertEquals(e, KeyEffect.fromJson(JSONObject(e.toJson().toString())))
        // 不能換顏色的動畫：不留顏色
        assertNull(KeyEffect.fromJson(JSONObject().put("kind", "bubble").put("color", "#FF00FF00"))!!.color)
        // 全部按鍵的：跟著主題檔走
        val json = ThemeStore.withTemp(ctx, ThemeStore.factory("classic")) { t ->
            t.pressEffect = "paw"
            t.pressEffectColor = mint
            ThemeIO.export(ctx, t, "薄荷貓爪", ThemeIO.Part.values().toSet())
        }
        val back = ThemeStore.withTemp(ctx, ThemeStore.factory("classic")) { t ->
            ThemeIO.apply(ctx, t, ThemeIO.parse(json)!!)
            t.pressEffect to t.pressEffectColor
        }
        assertEquals("paw" to mint, back)
    }


    @Test fun studioColorRow() {
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        StudioActivity::class.java.getDeclaredField("animMode").apply { isAccessible = true }.set(a, "global")
        val set = StudioActivity::class.java.getDeclaredMethod("setEffect", KeyEffect::class.java).apply { isAccessible = true }
        set.invoke(a, KeyEffect("paw"))
        ShadowLooper.idleMainLooper()
        assertNotNull(views(a.window.decorView).firstOrNull { it is TextView && it.text == "動畫顏色" })
        set.invoke(a, KeyEffect("paw", color = mint))
        assertEquals(mint, Prefs(ctx, ThemeStore.WORK).pressEffectColor)
        // 「試按看看」：冒出來的貓爪是選的顏色（不是主題的粉紅）
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val fx = CatKeyboardView::class.java.getDeclaredField("effects").apply { isAccessible = true }.get(kv) as PressEffects
        (PressEffects::class.java.getDeclaredField("particles").apply { isAccessible = true }.get(fx) as MutableList<*>).clear()
        StudioActivity::class.java.getDeclaredMethod("playPreview").apply { isAccessible = true }.invoke(a)
        assertEquals(listOf(mint), colors(fx))
        // 改回原色
        set.invoke(a, KeyEffect("paw"))
        assertNull(Prefs(ctx, ThemeStore.WORK).pressEffectColor)
        // 彩虹泡泡：沒有顏色可以選
        set.invoke(a, KeyEffect("bubble"))
        ShadowLooper.idleMainLooper()
        assertNull(views(a.window.decorView).firstOrNull { it is TextView && it.text == "動畫顏色" })
    }
}
