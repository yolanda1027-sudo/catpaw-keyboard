package io.github.yolanda1027.catpaw

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** 上方列透明↔不透明時，背景圖片的範圍會變：有背景圖片就問要不要重新裁切 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TopBarRecropTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    /** 上方列頁「底色」那一列的「透明」按鈕 */
    private fun clearButton(root: View): Button {
        val label = find(root) { it is TextView && it.text == "底色" }!!
        val row = label.parent.parent as ViewGroup
        return find(row) { it is Button && it.text == "透明" } as Button
    }

    @Test fun asksToRecropWhenFrameChanges() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val name = Decals.storeBitmap(ctx, Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) })
        StudioActivity::class.java.declaredMethods.first { it.name == "setFill" }.apply { isAccessible = true }
            .invoke(a, KeyStyles.KEYBOARD, ImageFill(name))
        kv.onTopBarPicked!!.invoke()
        ShadowLooper.idleMainLooper()
        val root = a.window.decorView
        val before = kv.keyboardImageFrame()
        assertTrue(before.top > 0f)

        // 設透明 → 跳提醒 → 按「調整」打開裁切，框是整個鍵盤的高度
        clearButton(root).performClick()
        ShadowLooper.idleMainLooper()
        val ask = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(ask.isShowing)
        assertNotNull(find(ask.window!!.decorView) { it is TextView && it.text.toString().contains("往上鋪滿整個鍵盤") })
        ask.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        val crop = ShadowDialog.getLatestDialog() as Dialog
        val cv = find(crop.window!!.decorView) { it is CropView } as CropView
        val full = kv.keyboardImageFrame()
        assertEquals(0f, full.top)
        assertEquals(full.width() / full.height(), cv.frame.width() / cv.frame.height(), 0.01f)
        crop.dismiss()
        ShadowLooper.idleMainLooper()

        // 改回不透明（按「改回原色」）→ 再問一次
        val reset = find(find(root) { it is TextView && it.text == "底色" }!!.parent.parent as ViewGroup) { it is Button && it.text == "改回原色" } as Button
        reset.performClick()
        ShadowLooper.idleMainLooper()
        val ask2 = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(ask2 !== ask && ask2.isShowing)
        assertNotNull(find(ask2.window!!.decorView) { it is TextView && it.text.toString().contains("比例變扁") })
        ask2.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
    }

    @Test fun noQuestionWithoutBackgroundImage() {
        val p = Prefs(ctx)
        p.theme = "magic_yellow"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.onTopBarPicked!!.invoke()
        ShadowLooper.idleMainLooper()
        val before = ShadowDialog.getLatestDialog()
        clearButton(a.window.decorView).performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(ShadowDialog.getLatestDialog() === before)
    }
}
