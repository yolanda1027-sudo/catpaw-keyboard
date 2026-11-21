package io.github.yolanda1027.catpaw

import android.view.View
import android.widget.ScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** 設定：點進一頁再按返回，回到原本滑到的地方（不是跳回最上面） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SettingsScrollTest {
    private fun layout(a: SettingsActivity) {
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        ShadowLooper.idleMainLooper()
    }

    private fun scroll(a: SettingsActivity) = SettingsActivity::class.java.getDeclaredField("scroll").apply { isAccessible = true }.get(a) as ScrollView
    private fun show(a: SettingsActivity, id: String) =
        SettingsActivity::class.java.getDeclaredMethod("showPage", String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(a, id, false)

    @Test fun backReturnsToWhereYouWere() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        layout(a)
        scroll(a).scrollTo(0, 900)
        val y = scroll(a).scrollY
        assertTrue("首頁夠長可以往下滑：$y", y > 300)
        // 點進「剪貼簿」：從最上面開始
        show(a, "clip")
        layout(a)
        assertEquals(0, scroll(a).scrollY)
        // 按返回：回到首頁剛剛滑到的地方
        a.pressBack()
        layout(a)
        assertEquals("home", a.page)
        assertEquals(y, scroll(a).scrollY)
        // 再點進別的頁：一樣從最上面開始（不會沿用以前的位置）
        show(a, "look")
        layout(a)
        assertEquals(0, scroll(a).scrollY)
    }
}
