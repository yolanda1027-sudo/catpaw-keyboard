package io.github.yolanda1027.catpaw

import android.content.Intent
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 升到 API 36：返回鍵改用 OnBackInvokedCallback、畫面滿版後內容要避開系統列 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class Api36Test {
    @Test fun backOnSubpageGoesHomeThenLeaves() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "licenses")).setup().get()
        assertEquals("licenses", a.page)
        assertTrue("子頁面要攔返回鍵", a.backRegistered)
        a.pressBack()
        assertEquals("home", a.page)
        assertFalse("首頁不攔返回鍵，系統才會播回桌面的動畫", a.backRegistered)
        a.pressBack()
        assertTrue(a.isFinishing)
    }

    @Test fun fitSystemBarsPadsByInsetsKeepingOwnPadding() {
        val v = FrameLayout(RuntimeEnvironment.getApplication())
        v.setPadding(10, 20, 30, 40)
        Ui.fitSystemBars(v)
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 60, 0, 0))
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .build()
        v.dispatchApplyWindowInsets(insets)
        assertEquals(listOf(10, 80, 30, 88), listOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom))
        // 再收到一次（例如鍵盤收起來）不會越加越多
        v.dispatchApplyWindowInsets(insets)
        assertEquals(80, v.paddingTop)
        // 打字鍵盤跳出來：底部讓出鍵盤的高度
        v.dispatchApplyWindowInsets(WindowInsets.Builder().setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 700)).build())
        assertEquals(740, v.paddingBottom)
    }
}
