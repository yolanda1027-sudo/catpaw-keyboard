package io.github.yolanda1027.catpaw

import android.content.Intent
import android.net.Uri
import android.view.View
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 主題頁的「主題下載區」用瀏覽器打開；下載好的主題檔點一下就能匯入 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StoreLinkTest {
    private val ctx = RuntimeEnvironment.getApplication()


    @Test fun storeButtonOpensBrowser() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "look")).setup().get()
        val b = views(a.window.decorView).first { it is Button && it.text.toString().startsWith("🛍️ 主題下載區") } as Button
        b.performClick()
        val next = shadowOf(a).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, next.action)
        assertEquals(SettingsActivity.STORE_URL, next.dataString)
    }

    @Test fun openingDownloadedThemeAsksToImport() {
        val json = ThemeStore.withTemp(ctx, ThemeStore.factory("magic_yellow")) { t ->
            ThemeIO.export(ctx, t, "下載的主題", ThemeIO.Part.values().toSet())
        }
        val f = File(ctx.cacheDir, "theme.json").apply { writeText(json) }
        val i = Intent(ctx, SettingsActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(f), "application/json")
        Robolectric.buildActivity(SettingsActivity::class.java, i).setup()
        ShadowLooper.idleMainLooper()
        val d = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("跳出匯入的確認", d)
        val msg = views(d.window!!.decorView).filterIsInstance<TextView>().joinToString { it.text.toString() }
        assertTrue(msg, msg.contains("下載的主題") || msg.contains("主題清單"))
        val before = ThemeStore.list(ctx, Prefs(ctx)).size
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(before + 1, ThemeStore.list(ctx, Prefs(ctx)).size)
        assertTrue(ThemeStore.list(ctx, Prefs(ctx)).any { it.name == "下載的主題" })
    }
}
