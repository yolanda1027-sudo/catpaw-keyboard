package io.github.yolanda1027.catpaw

import android.graphics.Color
import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
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
import org.robolectric.shadows.ShadowToast

/** 編輯歸編輯、套用歸套用：在工作室換主題編輯、儲存，鍵盤用的主題都不會變 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StudioEditApartTest {
    private val ctx = RuntimeEnvironment.getApplication()


    @Test fun editAnotherThemeWithoutApplying() {
        val live = Prefs(ctx)
        assertEquals("classic", ThemeStore.activeId(live))
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        assertEquals("鍵盤正在用這個主題", a.actionBar!!.subtitle)
        // 首頁的「換一個主題編輯」→ 選粉紅縫線
        (views(a.window.decorView).first { it is Button && it.text.toString().startsWith("🎨 換一個主題編輯") } as Button).performClick()
        ShadowLooper.idleMainLooper()
        val pick = ShadowAlertDialog.getLatestAlertDialog()
        val labels = (0 until pick.listView.adapter.count).map { pick.listView.adapter.getItem(it).toString() }
        assertTrue(labels.toString(), labels.any { it.startsWith("✏️ 經典深色") && it.endsWith("（鍵盤正在用）") })
        shadowOf(pick).clickOnItem(labels.indexOf("粉紅縫線"))
        ShadowLooper.idleMainLooper()
        val f = StudioActivity::class.java.getDeclaredField("themeId").apply { isAccessible = true }
        assertEquals("stitch", f.get(a))
        assertEquals("換主題編輯，鍵盤沒換", "classic", ThemeStore.activeId(Prefs(ctx)))
        // 改一下、儲存：只存進粉紅縫線，不問要不要套用、鍵盤也不換
        val work = Prefs(ctx, ThemeStore.WORK)
        assertEquals("stitch", work.theme)
        KeyStyles.save(work, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        StudioActivity::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(a, work)
        val dialogsBefore = ShadowAlertDialog.getShownDialogs().size
        StudioActivity::class.java.getDeclaredMethod("save", Function0::class.java).apply { isAccessible = true }.invoke(a, null)
        ShadowLooper.idleMainLooper()
        assertEquals("儲存後不跳「要套用嗎」", dialogsBefore, ShadowAlertDialog.getShownDialogs().size)
        assertTrue(ShadowToast.getTextOfLatestToast(), ShadowToast.getTextOfLatestToast().contains("鍵盤還是用「經典深色」"))
        assertEquals("classic", ThemeStore.activeId(Prefs(ctx)))
        assertTrue("鍵盤上的樣子沒變", KeyStyles.load(Prefs(ctx)).map.isEmpty())
        assertEquals(Color.RED, KeyStyles.load(ThemeStore.load(ctx, Prefs(ctx), "stitch").let { snap ->
            ThemeStore.withTemp(ctx, snap) { it } }).map["enter"]?.bg)
    }
}
