package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SettingsScreenTest {

    private fun shot(a: SettingsActivity, name: String) {
        val v = a.window.decorView
        Ui.spaceButtons(v, (3 * a.resources.displayMetrics.density).toInt()) // 手機上每次畫之前會做
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun showPage(a: SettingsActivity, id: String) {
        val m = SettingsActivity::class.java.getDeclaredMethod("showPage", String::class.java)
        m.isAccessible = true
        m.invoke(a, id)
    }

    @Test fun pages() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        shot(a, "settings-home")
        Prefs(a).quickBar = true
        for (p in listOf("quick", "look", "haptic", "color")) {
            showPage(a, p)
            shot(a, "settings-$p")
        }
        // 自訂配色往下捲，看色票表
        showPage(a, "color")
        shot(a, "settings-color")
        val f = SettingsActivity::class.java.getDeclaredField("scroll"); f.isAccessible = true
        (f.get(a) as android.widget.ScrollView).scrollTo(0, 5000)
        shot(a, "settings-color-scrolled")
        // 外觀頁最下面：主題匯入／匯出
        showPage(a, "look")
        shot(a, "settings-look") // 先排版一次，才捲得到底
        (f.get(a) as android.widget.ScrollView).scrollTo(0, 99999)
        shot(a, "settings-look-bottom")
        // 匯出視窗
        val m = SettingsActivity::class.java.getDeclaredMethod("showExportDialog"); m.isAccessible = true
        m.invoke(a)
        val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val dv = d.window!!.decorView
        dv.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        dv.layout(0, 0, 960, dv.measuredHeight)
        val bmp = Bitmap.createBitmap(960, dv.measuredHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        dv.draw(Canvas(bmp))
        File("build/renders/settings-export-dialog.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun colorPicker() {
        val ctx = RuntimeEnvironment.getApplication()
        val v = ColorPickerView(ctx, android.graphics.Color.parseColor("#F49AB7"))
        v.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        v.draw(c)
        File("build/renders/color-picker.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 點彩虹條最左邊 → 紅色系；點大色塊右上角 → 最鮮豔最亮
        var got = 0
        v.onColorChanged = { got = it }
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 40f, v.measuredHeight - 40f, 0))
        v.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_UP, 40f, v.measuredHeight - 40f, 0))
        v.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 880f, 35f, 0))
        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(got, hsv)
        assert(hsv[0] < 15f) { "hue ${hsv[0]}" }
        assert(hsv[1] > 0.95f && hsv[2] > 0.95f) { "sat ${hsv[1]} val ${hsv[2]}" }
    }

    @Test fun quickEntriesRoundTrip() {
        val prefs = Prefs(RuntimeEnvironment.getApplication())
        // 舊版用空白分開的設定，讀得出來
        prefs.quickItems = "() 「」 ⇥"
        assertEquals(listOf("()", "「」", "⇥"), prefs.quickList())
        // 新版：勾選的才顯示、最多 7 個
        prefs.setQuickEntries(listOf(
            Prefs.QuickEntry("()", true), Prefs.QuickEntry("「」", false), Prefs.QuickEntry("⇤", true),
        ))
        assertEquals(listOf("()", "⇤"), prefs.quickList())
        prefs.setQuickEntries((1..10).map { Prefs.QuickEntry("a$it", true) })
        assertEquals(7, prefs.quickList().size)
    }
}
