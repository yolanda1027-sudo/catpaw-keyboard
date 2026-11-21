package io.github.yolanda1027.catpaw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** 設定裡把剪貼簿關掉：上方列的剪貼簿按鈕藏起來、點了沒反應；正在看剪貼簿的話回到鍵盤 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClipToggleTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Test fun turningOffHidesTheButton() {
        val p = Prefs(ctx)
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        val page = CatImeService::class.java.getDeclaredField("page").apply { isAccessible = true }
        assertTrue(kv.showClipboard)
        s.onClipboard()
        assertEquals("CLIP", page.get(s).toString())

        // 關掉：正在看剪貼簿 → 回到鍵盤；按鈕藏起來
        p.clipboardHistory = false
        ShadowLooper.idleMainLooper()
        assertEquals("MAIN", page.get(s).toString())
        assertFalse(kv.showClipboard)
        // 點了也不會打開剪貼簿
        s.onClipboard()
        assertEquals("MAIN", page.get(s).toString())

        // 再打開：回來了
        p.clipboardHistory = true
        ShadowLooper.idleMainLooper()
        assertTrue(kv.showClipboard)
        s.onClipboard()
        assertEquals("CLIP", page.get(s).toString())
    }
}
