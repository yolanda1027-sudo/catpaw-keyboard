package io.github.yolanda1027.catpaw

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class EyedropperTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun render(v: View, name: String) {
        Ui.spaceButtons(v, 8)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun button(v: View, t: String) = find(v) { it is Button && it.text.toString() == t } as Button

    @Test fun picksTheExactPixel() {
        val b = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.clipRect(0, 0, 2, 2); c.drawColor(Color.RED)
        val c2 = Canvas(b); c2.clipRect(2, 0, 4, 2); c2.drawColor(Color.BLUE)
        val v = EyedropView(ctx, b)
        v.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 400, 200)
        var got = 0
        v.onColor = { got = it }
        v.select(50f, 100f)
        assertEquals(Color.RED, got)
        v.select(350f, 100f)
        assertEquals(Color.BLUE, v.color)
        v.select(-999f, 9999f) // 拖到外面：停在邊邊
        assertEquals(0 to 1, v.px to v.py)
    }

    @Test fun bigImageIsShrunkAndNotSaved() {
        val f = File(ctx.cacheDir, "big.png")
        val big = Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF7BC6A4.toInt()) }
        f.outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val before = ctx.filesDir.walk().count()
        val got = Eyedropper.decode(ctx, Uri.fromFile(f))!!
        assertEquals(Eyedropper.MAX_PX, maxOf(got.width, got.height))
        assertEquals(0xFF7BC6A4.toInt(), got.getPixel(10, 10))
        assertEquals(before, ctx.filesDir.walk().count()) // 沒有多存任何檔案
    }

    @Test fun screenAndImageFlowInStudio() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        var picked: Int? = null
        ColorDialog.show(a, "外框顏色", 0xFFF8D03D.toInt()) { picked = it }
        ShadowLooper.idleMainLooper()
        val cd = ShadowDialog.getLatestDialog() as AlertDialog
        render(cd.window!!.decorView, "eyedrop-color-dialog")

        // 💧 從畫面汲色 → 全螢幕汲色畫面
        button(cd.window!!.decorView, "💧 從畫面汲色").performClick()
        ShadowLooper.idleMainLooper()
        val drop = ShadowDialog.getLatestDialog() as Dialog
        assertTrue(drop !== cd && drop.isShowing)
        assertTrue(!cd.isShowing)
        val ev = find(drop.window!!.decorView) { it is EyedropView } as EyedropView
        render(drop.window!!.decorView, "eyedrop-screen")
        // 拍到的是工作室畫面（不是白的）
        val kv = a.window.decorView.let { find(it) { v -> v is CatKeyboardView } as CatKeyboardView }
        val loc = IntArray(2).also { kv.getLocationInWindow(it) }
        val shot = ev.bmp
        val bg = shot.getPixel(loc[0] + 4, loc[1] + kv.height - 4)
        assertTrue(bg != Color.WHITE && bg != 0)
        ev.select(ev.width / 2f, ev.height * 0.3f)
        render(drop.window!!.decorView, "eyedrop-screen-loupe")
        val chosen = ev.color
        button(drop.window!!.decorView, "用這個顏色").performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(cd.isShowing)
        val hex = find(cd.window!!.decorView) { it is EditText } as EditText
        assertEquals(String.format("#%06X", 0xFFFFFF and chosen), hex.text.toString())

        // 🖼️ 從圖片汲色 → 開檔 → 回來直接進汲色畫面
        val f = File(ctx.cacheDir, "temp.png")
        val img = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC9A0FF.toInt()) }
        f.outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        button(cd.window!!.decorView, "🖼️ 從圖片汲色").performClick()
        val req = shadowOf(a).nextStartedActivityForResult
        assertEquals(android.provider.MediaStore.ACTION_PICK_IMAGES, req.intent.action) // 相片挑選器（按一次返回就關掉）
        assertEquals(Eyedropper.REQ_IMAGE, req.requestCode)
        shadowOf(a).receiveResult(req.intent, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(f)))
        ShadowLooper.idleMainLooper()
        val drop2 = ShadowDialog.getLatestDialog() as Dialog
        val ev2 = find(drop2.window!!.decorView) { it is EyedropView } as EyedropView
        assertEquals(0xFFC9A0FF.toInt(), ev2.color)
        button(drop2.window!!.decorView, "用這個顏色").performClick()
        ShadowLooper.idleMainLooper()
        button(cd.window!!.decorView, "確定").performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(0xFFC9A0FF.toInt(), picked)
        assertNotNull(find(drop2.window!!.decorView) { it is TextView })
    }
}
