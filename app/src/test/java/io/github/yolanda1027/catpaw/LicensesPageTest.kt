package io.github.yolanda1027.catpaw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import java.io.File

/** 設定裡的「開放原始碼授權」：每一份都有授權全文，third_party/ 裡的授權一個都沒漏 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class LicensesPageTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Test fun everyLicenseTextIsBundled() {
        for (e in OpenSource.ENTRIES) for (f in e.files) {
            val t = OpenSource.text(ctx, f)
            assertNotNull("${e.name}：讀不到 $f", t)
            // 授權全文裡要看得到授權的名字
            val key = when {
                e.license.startsWith("MIT") -> "MIT"
                e.license.startsWith("Apache") -> "Apache License"
                e.license.startsWith("SIL") -> "SIL OPEN FONT LICENSE"
                else -> "UNICODE LICENSE"
            }
            assertTrue("${e.name} 的全文裡沒有「$key」", t!!.contains(key, ignoreCase = true))
        }
    }

    @Test fun reflowJoinsLinesKeepsParagraphs() {
        assertEquals("The quick brown fox\n\njumps over", OpenSource.reflow("The quick\r\nbrown fox\n\n  jumps\nover"))
    }

    /** repo 的 third_party/ 裡每個授權檔，都要有人用到（之後加新的授權不會忘記放進頁面） */
    @Test fun noLicenseFileIsForgotten() {
        val root = File("../third_party")
        val all = root.walkTopDown().filter { it.isFile && !it.name.equals("README.md", true) }
            .map { it.relativeTo(root).path }.toSet()
        val used = OpenSource.ENTRIES.flatMap { it.files }.toSet()
        assertEquals(all, used)
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    @Test fun settingsPage() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "licenses")).setup().get()
        val root = a.window.decorView
        for (e in OpenSource.ENTRIES) assertNotNull(e.name, find(root) { it is TextView && it.text == e.name })
        assertNotNull(find(root) { it is TextView && it.text == OpenSource.OTHER_SOURCES })
        Ui.spaceButtons(root, 8)
        fun shot(name: String) {
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2340)
            val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(b))
            File("build/renders").mkdirs()
            File("build/renders/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shot("licenses-page")
        // 點第一個「看授權全文」：展開小麥注音的 MIT 授權
        val btn = find(root) { it is Button && it.text == "看授權全文" } as Button
        btn.performClick()
        assertEquals("收起來", btn.text)
        assertNotNull(find(root) { it is TextView && it.visibility == View.VISIBLE && it.text.contains("Mengjuei Hsieh") && it.text.contains("Permission is hereby granted") })
        shot("licenses-page-open")
        btn.performClick()
        assertEquals("看授權全文", btn.text)
    }
}
