package io.github.yolanda1027.catpaw

import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import java.io.File

/** 有 GIF、很多圖片的主題檔會超過 10 MB：以前匯不回來，還被說成「不是主題檔」 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BigThemeImportTest {
    private fun importFile(f: File): SettingsActivity {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "look")).setup().get()
        SettingsActivity::class.java.getDeclaredMethod("handleImport", Uri::class.java).apply { isAccessible = true }.invoke(a, Uri.fromFile(f))
        return a
    }

    @Test fun themeOver10MbImports() {
        val o = JSONObject().put("format", ThemeIO.FORMAT).put("version", 1).put("name", "大主題").put("theme", "classic")
            .put("pad", "x".repeat(14 * 1024 * 1024))
        val f = File.createTempFile("big", ".json").apply { writeText(o.toString()) }
        importFile(f)
        val d = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("要跳出「匯入？」的確認視窗", d)
        assertEquals("匯入「大主題」？", org.robolectric.Shadows.shadowOf(d).title.toString())
    }

    @Test fun notAThemeSaysSo() {
        val f = File.createTempFile("notatheme", ".json").apply { writeText("{\"hello\":1}") }
        importFile(f)
        assertEquals("這不是貓爪鍵盤的主題檔", ShadowToast.getTextOfLatestToast())
    }
}
