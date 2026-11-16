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
