package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
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
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class CustomEffectTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val b = Layouts.ZHUYIN[0][0]
    private val enter = Layouts.ZHUYIN[4].last()

    @Test fun jsonRoundTrip() {
        val c = CustomEffect("fountain", "shape:paw", 0xFF123456.toInt(), rainbow = true, count = 8, size = 14f, speed = 1.5f, spin = true, bounce = true)
        assertEquals(c, CustomEffect.fromJson(JSONObject(c.toJson().toString())))
        // 不認得的動作、奇怪的檔名都換成安全的預設
        val bad = CustomEffect.fromJson(JSONObject().put("motion", "explode").put("particle", "file:../x.png").put("count", 999))!!
        assertEquals("rise", bad.motion)
        assertEquals("shape:heart", bad.particle)
        assertEquals(15, bad.count)
        assertNull(KeyEffect.fromJson(JSONObject().put("kind", "rm -rf")))
        assertEquals(KeyEffect("spell"), KeyEffect.fromJson(KeyEffect("spell").toJson()))
    }

    @Test fun perKeyEffectWinsOverGlobal() {
        val p = Prefs(ctx)
        p.pressEffect = "heart"
        KeyStyles.save(p, KeyStyles.EMPTY
            .with("@zhuyin", KeyStyle(effect = KeyEffect("snow")))
            .with("enter", KeyStyle(effect = KeyEffect("custom", CustomEffect(motion = "burst")))))
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        assertEquals("snow", kv.effectFor(b).kind)
        assertEquals("burst", kv.effectFor(enter).custom?.motion)
        assertEquals("heart", kv.effectFor(Layouts.ZHUYIN[4][0]).kind) // ?123 沒有單獨設定
        // 存起來再讀回來也一樣
        assertEquals(KeyEffect("snow"), KeyStyles.load(Prefs(ctx)).map["@zhuyin"]?.effect)
    }

    /** 五種動作各拍一張（按下去 180ms 的樣子），排成一張圖看 */
    @Test fun renderMotions() {
        val p = Prefs(ctx)
        p.theme = "macaron_butter"
        val shots = CustomEffect.MOTIONS.mapIndexed { i, (m, _) ->
            val particle = listOf("shape:heart", "shape:star", "shape:flower", "shape:paw", "shape:sparkle")[i]
            p.pressEffect = "custom"
            p.customEffect = CustomEffect(m, particle, 0xFFFF6FA3.toInt(), rainbow = i % 2 == 1, count = 7, size = 10f, spin = i == 2)
            val kv = CatKeyboardView(ctx, p, Haptics(ctx))
            kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
            kv.previewEffect(Layouts.ZHUYIN[2][4]) // ㄕ：鍵盤中間
            ShadowSystemClock.advanceBy(Duration.ofMillis(if (m == "fall") 400 else 180))
            val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
            kv.draw(Canvas(bmp))
            bmp
        }
        val h = shots[0].height
        val out = Bitmap.createBitmap(1080, (h * 0.5f).toInt() * 5, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        shots.forEachIndexed { i, s ->
            // 只要鍵盤中間那一塊
            c.drawBitmap(s, android.graphics.Rect(0, (h * 0.25f).toInt(), 1080, (h * 0.75f).toInt()),
                android.graphics.Rect(0, i * (h * 0.5f).toInt(), 1080, (i + 1) * (h * 0.5f).toInt()), null)
        }
        File("build/renders").mkdirs()
        File("build/renders/custom-effects.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun themeFileCarriesEffectImages() {
        val p = Prefs(ctx)
        val pic = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val name = Decals.storeBitmap(ctx, pic)
        p.pressEffect = "custom"
        p.customEffect = CustomEffect("burst", Decal.FILE + name, count = 6)
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(effect = KeyEffect("custom", CustomEffect("pop", Decal.FILE + name)))))
        val json = ThemeIO.export(ctx, p, "動畫", setOf(ThemeIO.Part.EFFECT, ThemeIO.Part.KEYS))

        p.sp.edit().clear().commit()
        Decals.cleanup(ctx, emptyList())
        assertTrue(Decals.dir(ctx).listFiles()!!.isEmpty())

        val w = ThemeIO.apply(ctx, p, ThemeIO.parse(json)!!)
        assertTrue(w.toString(), w.isEmpty())
        val q = Prefs(ctx)
        assertEquals("custom", q.pressEffect)
        assertEquals("burst", q.customEffect.motion)
        assertTrue(q.customEffect.isImage)
        assertNotNull(Decals.fileBytes(ctx, q.customEffect.fileName))
        val keyEffect = KeyStyles.load(q).map["enter"]!!.effect!!.custom!!
        assertNotNull(Decals.fileBytes(ctx, keyEffect.fileName))
        // 清理圖案用的圖時，動畫的圖要留著
        Decals.cleanup(ctx, emptyList())
        assertNotNull(Decals.fileBytes(ctx, q.customEffect.fileName))
    }

    @Test fun studioAnimationPanel() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        p.pressEffect = "custom"
        p.customEffect = CustomEffect("fountain", "shape:star", 0xFFF5C542.toInt(), count = 9)
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val work_p = Prefs(ctx, ThemeStore.WORK) // 工作室改的是工作副本
        val f = StudioActivity::class.java.getDeclaredField("kv"); f.isAccessible = true
        val kv = f.get(a) as CatKeyboardView
        val mode = StudioActivity::class.java.getDeclaredField("animMode"); mode.isAccessible = true
        mode.set(a, "global")
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        StudioActivity::class.java.getDeclaredMethod("playPreview").apply { isAccessible = true }.invoke(a)
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        shot(a, "studio-anim-global")

        // 單顆鍵：Enter 噴愛心
        kv.editPicker!!.invoke(enter)
        mode.set(a, "key")
        StudioActivity::class.java.getDeclaredMethod("setEffect", KeyEffect::class.java).apply { isAccessible = true }
            .invoke(a, KeyEffect("custom", CustomEffect("burst", "shape:heart", 0xFFFF4F8B.toInt(), count = 8, size = 8f)))
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        shot(a, "studio-anim-key")
        assertEquals("burst", KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).map["enter"]?.effect?.custom?.motion)
    }

    private fun shot(a: StudioActivity, name: String) {
        val v = a.window.decorView
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2600, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2600)
        val bmp = Bitmap.createBitmap(1080, 2600, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
