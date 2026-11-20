package io.github.yolanda1027.catpaw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 主題清單不分內建、自己做的：每個都能改名、刪除；刪掉的內建主題可以加回來 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ThemeDeleteTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private fun live() = Prefs(ctx)
    private fun ids() = ThemeStore.list(ctx, live()).map { it.id }

    @Test fun deleteAndRestoreBuiltIn() {
        val p = live()
        // 粉紅縫線改過，然後刪掉
        ThemeStore.applyTheme(ctx, p, "stitch")
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        ThemeStore.applyTheme(ctx, p, "classic")
        ThemeStore.delete(ctx, p, "stitch")
        assertFalse("stitch" in ids())
        assertFalse(ThemeStore.exists(ctx, "stitch"))
        assertEquals(setOf("stitch"), ThemeStore.hiddenBuiltIns(p))
        // 加回來：原本的樣子
        ThemeStore.restoreBuiltIn(ctx, p, "stitch")
        assertTrue("stitch" in ids())
        ThemeStore.applyTheme(ctx, p, "stitch")
        assertTrue("改過的地方沒有留著", KeyStyles.load(p).map.isEmpty())
    }

    @Test fun deletingActiveSwitchesToFirst() {
        val p = live()
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.BLUE)))
        val mine = ThemeStore.create(ctx, p, "赫夫帕夫", ThemeStore.snapshot(p))
        // 正在用經典深色，刪掉它 → 換到清單第一個（自己做的排前面）
        ThemeStore.delete(ctx, p, "classic")
        assertEquals(mine, ThemeStore.activeId(live()))
        assertEquals(Color.BLUE, KeyStyles.load(live()).map["enter"]!!.bg)
        // 我的主題也能刪
        ThemeStore.delete(ctx, live(), mine)
        assertFalse(mine in ids())
        assertTrue(ThemeStore.exists(ctx, ThemeStore.activeId(live())))
    }

    @Test fun keepAtLeastOne() {
        val p = live()
        for (id in ids().drop(1)) ThemeStore.delete(ctx, p, id)
        assertEquals(1, ids().size)
        assertFalse(ThemeStore.canDelete(ctx, p))
        val last = ids().single()
        ThemeStore.delete(ctx, p, last)
        assertEquals("最後一個刪不掉", listOf(last), ids())
        assertEquals(last, ThemeStore.activeId(live()))
    }

    @Test fun renameBuiltIn() {
        val p = live()
        ThemeStore.rename(ctx, p, "stitch", "我的縫線")
        assertEquals("我的縫線", ThemeStore.nameOf(ctx, "stitch"))
        assertTrue(ThemeStore.list(ctx, p).any { it.id == "stitch" && it.name == "我的縫線" })
        // 跟別的主題同名：自動加 (2)
        ThemeStore.rename(ctx, p, "catpaw", "我的縫線")
        assertEquals("我的縫線 (2)", ThemeStore.nameOf(ctx, "catpaw"))
        // 改回原本的名字
        ThemeStore.rename(ctx, p, "stitch", "粉紅縫線")
        assertEquals("粉紅縫線", ThemeStore.nameOf(ctx, "stitch"))
        // 刪掉再加回來：名字也回到原本的
        ThemeStore.delete(ctx, p, "catpaw")
        ThemeStore.restoreBuiltIn(ctx, p, "catpaw")
        assertEquals("貓爪肉球", ThemeStore.nameOf(ctx, "catpaw"))
    }

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    @Test fun listScreenDeleteAndRestore() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "look")).setup().get()
        val root = a.window.decorView
        val all = views(root)
        assertFalse("不再分「內建主題」", all.any { it is android.widget.TextView && it.text == "內建主題" })
        // 粉紅縫線那一列的「⋯」→ 刪除 → 確定
        val rb = all.first { it is RadioButton && it.text == "粉紅縫線" }
        val dots = (rb.parent as ViewGroup).let { row -> (0 until row.childCount).map { row.getChildAt(it) } }.first { it is Button && it !is RadioButton && (it as Button).text == "⋯" }
        dots.performClick()
        val menu = ShadowAlertDialog.getLatestAlertDialog()
        val items = (0 until menu.listView.adapter.count).map { menu.listView.adapter.getItem(it).toString() }
        assertTrue(items.toString(), "✍️ 重新命名" in items && "🗑 刪除" in items)
        shadowOf(menu).clickOnItem(items.indexOf("🗑 刪除"))
        ShadowLooper.idleMainLooper()
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertFalse("stitch" in ids())
        // 最下面出現「加回刪掉的內建主題」
        val back = views(a.window.decorView).first { it is Button && it.text.toString().startsWith("↩️ 加回刪掉的內建主題") } as Button
        render(a, "theme-list-unified")
        back.performClick()
        ShadowLooper.idleMainLooper()
        val pick = ShadowAlertDialog.getLatestAlertDialog()
        shadowOf(pick).clickOnItem(0)
        pick.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertTrue("stitch" in ids())
    }

    private fun shadowOf(d: android.app.AlertDialog) = org.robolectric.Shadows.shadowOf(d)

    private fun render(a: SettingsActivity, name: String) {
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
