package io.github.yolanda1027.catpaw

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
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

/** 設定頁重新整理：跟著主題的設定標清楚、目前主題放最上面、版面設定獨立一頁、快捷列改了就存 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SettingsLayoutTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun texts(a: SettingsActivity) = views(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
    private fun open(id: String) = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", id)).setup().get()
    private fun show(a: SettingsActivity, id: String) =
        SettingsActivity::class.java.getDeclaredMethod("showPage", String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(a, id, false)

    @Test fun homeGroups() {
        val t = texts(open("home"))
        assertTrue(t.any { it == "📐 鍵盤大小與位置" })
        assertTrue(t.indexOf("版面") in 0 until t.indexOf("其他"))
        // 桌面小工具的說明搬到小提示
        assertFalse(t.any { it.startsWith("想從桌面直接打開主題工作室") })
        assertTrue(texts(open("tips")).any { it.contains("小工具") })
        // 字體、按鍵動畫寫明跟著主題
        assertTrue(t.any { it.startsWith("跟著主題：中文、注音") })
        assertTrue(t.any { it.startsWith("跟著主題：彈一下") })
    }

    @Test fun layoutPageHasLayoutThings() {
        val t = texts(open("layout"))
        assertTrue(t.any { it.startsWith("按鍵高度：") })
        assertTrue(t.any { it.startsWith("鍵盤最下面多留空間") })
        assertTrue(t.any { it == "最下面的導覽列也塗上鍵盤底色" })
        assertTrue(t.any { it.contains("存在目前的主題") })
        // 手感頁只剩手感
        val h = texts(open("haptic"))
        assertFalse(h.any { it == "最下面的導覽列也塗上鍵盤底色" })
        assertFalse(h.any { it.startsWith("鍵盤最下面多留空間") })
    }

    @Test fun themePageCurrentFirst() {
        val a = open("look")
        val t = texts(a)
        val cur = t.indexOfFirst { it.startsWith("目前主題：") }
        val change = t.indexOf("換主題")
        val adv = t.indexOf("進階")
        assertTrue("目前主題 $cur 換主題 $change 進階 $adv", cur in 0 until change && change < adv)
        assertTrue(t.indexOf("圖片存法") > adv)
        // 功能鍵貼圖收成一頁，主題頁上看不到那 5 列
        assertFalse(t.any { it == "選圖片" })
        val link = views(a.window.decorView).first { it is Button && it.text == "🖼️ 功能鍵貼圖" } as Button
        link.performClick()
        assertEquals("stickers", a.page)
        assertEquals(5, texts(a).count { it == "選圖片" })
        assertTrue(texts(a).any { it.contains("存在目前的主題") })
        // 從主題頁點進來的：返回回到主題頁
        a.pressBack()
        assertEquals("look", a.page)
        // 從主題頁點「字體」→ 返回回主題頁；從首頁點字體 → 返回回首頁
        (views(a.window.decorView).first { it is Button && it.text == "🔤 字體" } as Button).performClick()
        assertEquals("font", a.page)
        assertTrue(texts(a).any { it.contains("存在目前的主題") })
        a.pressBack()
        assertEquals("look", a.page)
        show(a, "home")
        show(a, "font")
        a.pressBack()
        assertEquals("home", a.page)
    }

    @Test fun quickBarSavesRightAway() {
        val p = Prefs(ctx)
        p.quickBar = false
        val a = open("quick")
        // 沒有「儲存」「取消」了
        assertFalse(texts(a).any { it == "儲存" || it == "取消" })
        // 關著：編輯區變淡、有提示
        val editor = views(a.window.decorView).first { it is CheckBox }.parent.parent.parent as View
        assertTrue(editor.alpha < 1f)
        assertTrue(texts(a).any { it.startsWith("快捷符號列現在關著") && views(a.window.decorView).any { v -> v is TextView && v.text == it && v.visibility == View.VISIBLE } })
        // 取消勾第一個：馬上存
        val first = views(a.window.decorView).first { it is CheckBox } as CheckBox
        val before = Prefs(ctx).quickEntries().first().on
        first.performClick()
        assertEquals(!before, Prefs(ctx).quickEntries().first().on)
        // 打開開關：編輯區恢復
        (views(a.window.decorView).first { it is Switch } as Switch).performClick()
        assertEquals(1f, editor.alpha, 0.001f)
    }
}
