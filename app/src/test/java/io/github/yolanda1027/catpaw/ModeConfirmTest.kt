package io.github.yolanda1027.catpaw

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 「這張圖要怎麼放？」：點選項只是選起來，按「確認」才套用，按「取消」什麼都不改 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ModeConfirmTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    private fun upload(a: StudioActivity): AlertDialog {
        (views(a.window.decorView).first { it is Button && it.text.toString() == "📷 上傳圖片" } as Button).performClick()
        val req = shadowOf(a).nextStartedActivityForResult
        val f = File(ctx.cacheDir, "red.png").apply {
            outputStream().use { Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shadowOf(a).receiveResult(req.intent, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(f)))
        ShadowLooper.idleMainLooper()
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    private fun row(d: AlertDialog, title: String) =
        views(d.window!!.decorView).first { it is TextView && (it.text.toString() == title || it.text.toString() == "✓ $title") }

    @Test fun pickThenConfirm() {
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        (views(a.window.decorView).first { it is Button && it.text.toString().startsWith("🖼️ 鍵盤背景") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        val d = upload(a)
        assertEquals("確認", d.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        // 點「整張都要看到」：視窗還開著、還沒套用，✓ 換到它身上
        (row(d, "整張都要看到").parent.parent as View).performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(d.isShowing)
        assertNull("還沒按確認：沒套用", KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).keyboardImage)
        assertNotNull(views(d.window!!.decorView).firstOrNull { it is TextView && it.text == "✓ 整張都要看到" })
        // 按確認：套用整張都要看到
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(ImageFill.Mode.WHOLE, KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).keyboardImage!!.mode)
    }

    @Test fun cancelKeepsNothing() {
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        (views(a.window.decorView).first { it is Button && it.text.toString().startsWith("🖼️ 鍵盤背景") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        val d = upload(a)
        (row(d, "邊框").parent.parent as View).performClick()
        ShadowLooper.idleMainLooper()
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertNull(KeyStyles.load(Prefs(ctx, ThemeStore.WORK)).keyboardImage)
    }
}
