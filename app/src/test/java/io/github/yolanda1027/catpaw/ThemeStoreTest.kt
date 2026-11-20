package io.github.yolanda1027.catpaw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.io.ByteArrayOutputStream
import java.io.File

/** 每個主題一個檔案：換主題不殘留、匯入變新主題、工作室要儲存才套用、沒存要問 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ThemeStoreTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun live() = Prefs(ctx)

    @Test fun switchingThemesDoesNotLeak() {
        val p = live()
        assertEquals("classic", ThemeStore.activeId(p))
        // 在經典深色上加按鍵樣式、圖案、功能鍵貼圖、字體、高度
        val img = Decals.storeBytes(ctx, png(Color.RED))!!
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        Decals.save(p, listOf(Decal("d1", Decal.BG, Decal.FILE + img)))
        Stickers.setBytes(ctx, p, StickerSlot.ENTER, png(Color.BLUE))
        p.zhFont = "wenkai"
        p.keyHeightDp = 52

        ThemeStore.applyTheme(ctx, p, "stitch")
        assertEquals("stitch", p.theme)
        assertTrue("換到粉紅縫線：按鍵樣式不跟過去", KeyStyles.load(p).map.isEmpty())
        assertTrue(Decals.load(p).isEmpty())
        assertTrue(p.funcStickers.isEmpty())
        assertEquals(45, p.keyHeightDp)
        org.junit.Assert.assertNotEquals("字體不跟過去", "wenkai", p.zhFont)

        ThemeStore.applyTheme(ctx, p, "classic")
        assertEquals("換回來：經典深色記得自己的樣子", Color.RED, KeyStyles.load(p).map["enter"]!!.bg)
        assertEquals(1, Decals.load(p).size)
        assertNotNull(Stickers.file(ctx, p, StickerSlot.ENTER))
        assertEquals("wenkai", p.zhFont)
        assertEquals(52, p.keyHeightDp)
        assertTrue("圖檔沒被當成沒用的清掉", File(Decals.dir(ctx), img).exists())

        val list = ThemeStore.list(ctx, p)
        assertTrue(list.first { it.id == "classic" }.modified)
        assertFalse(list.first { it.id == "stitch" }.modified)
    }

    @Test fun resetBuiltInAndUserThemes() {
        val p = live()
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        val copy = ThemeStore.create(ctx, p, "紅色 Enter", ThemeStore.snapshot(p))
        assertEquals("紅色 Enter", ThemeStore.nameOf(ctx, copy))
        assertEquals("名字重複會自動加編號", "紅色 Enter (2)", ThemeStore.nameOf(ctx, ThemeStore.create(ctx, p, "紅色 Enter", ThemeStore.snapshot(p))))

        ThemeStore.resetBuiltIn(ctx, p, "classic")
        assertTrue("經典深色恢復原廠", KeyStyles.load(p).map.isEmpty())
        assertFalse(ThemeStore.list(ctx, p).first { it.id == "classic" }.modified)

        ThemeStore.applyTheme(ctx, p, copy)
        assertEquals("複製出來的主題不受影響", Color.RED, KeyStyles.load(p).map["enter"]!!.bg)
        ThemeStore.rename(ctx, p, copy, "我的紅")
        assertEquals("我的紅", ThemeStore.nameOf(ctx, copy))
        ThemeStore.delete(ctx, p, copy)
        // 還有另一個「紅色 Enter (2)」：換到清單第一個（自己做的排前面）
        val next = ThemeStore.activeId(p)
        assertTrue("刪掉正在用的主題：換到還在的主題", next != copy && ThemeStore.exists(ctx, next))
        assertEquals("紅色 Enter (2)", ThemeStore.nameOf(ctx, next))
        assertFalse(ThemeStore.exists(ctx, copy))
    }

    @Test fun importAddsNewThemeWithoutTouchingOthers() {
        val p = live()
        // 現在用的主題有自己的圖
        val mine = Decals.storeBytes(ctx, png(Color.GREEN))!!
        Decals.save(p, listOf(Decal("d1", Decal.BG, Decal.FILE + mine)))
        // 別人的主題檔：換底色、帶一張功能鍵貼圖、一個圖案
        val other = ThemeStore.withTemp(ctx, ThemeStore.factory("magic_yellow")) { t ->
            val img = Decals.storeBytes(ctx, png(Color.YELLOW))!!
            Decals.save(t, listOf(Decal("x", Decal.BG, Decal.FILE + img)))
            Stickers.setBytes(ctx, t, StickerSlot.ENTER, png(Color.MAGENTA))
            ThemeIO.export(ctx, t, "赫夫帕夫", ThemeIO.Part.values().toSet())
        }
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "look")).setup().get()
        val (id, warnings) = a.importAsTheme(ThemeIO.parse(other)!!, "赫夫帕夫")
        assertTrue(warnings.toString(), warnings.isEmpty())

        val q = live()
        assertEquals("匯入不會換掉正在用的主題", "classic", ThemeStore.activeId(q))
        assertEquals(1, Decals.load(q).size)
        assertTrue("正在用的主題的圖還在", File(Decals.dir(ctx), mine).exists())
        assertEquals("赫夫帕夫", ThemeStore.nameOf(ctx, id))

        ThemeStore.applyTheme(ctx, q, id)
        assertEquals("magic_yellow", q.theme)
        assertEquals("x", Decals.load(q).single().id)
        assertNotNull(Stickers.file(ctx, q, StickerSlot.ENTER))
        assertTrue("匯入的圖還在", File(Decals.dir(ctx), Decals.load(q).single().fileName).exists())
        // 再換回去：自己的圖也還在
        ThemeStore.applyTheme(ctx, q, "classic")
        assertTrue(File(Decals.dir(ctx), mine).exists())
    }

    /** 主題清單畫面：內建主題、我的主題、⋯ 選單 */
    @Test fun themeListScreen() {
        val p = live()
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        ThemeStore.create(ctx, p, "赫夫帕夫", ThemeStore.snapshot(p))
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "look")).setup().get()
        val radios = ArrayList<RadioButton>()
        val buttons = ArrayList<Button>()
        fun walk(v: View) {
            if (v is RadioButton) radios += v else if (v is Button) buttons += v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(a.window.decorView)
        assertTrue(radios.any { it.text == "經典深色（改過）" && it.isChecked })
        assertTrue(radios.any { it.text == "赫夫帕夫" && !it.isChecked })
        // 點「赫夫帕夫」：套用
        radios.first { it.text == "赫夫帕夫" }.performClick()
        assertEquals("赫夫帕夫", ThemeStore.nameOf(ctx, ThemeStore.activeId(Prefs(ctx))))
        // ⋯ 選單
        val again = ArrayList<Button>()
        fun walk2(v: View) {
            if (v is Button && v !is RadioButton) again += v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk2(v.getChildAt(i))
        }
        walk2(a.window.decorView)
        again.first { it.text == "⋯" }.performClick()
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog())
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val f = SettingsActivity::class.java.getDeclaredField("scroll").apply { isAccessible = true }
        (f.get(a) as android.widget.ScrollView).scrollTo(0, 0)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/theme-list.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun studio(intent: Intent? = null) =
        Robolectric.buildActivity(StudioActivity::class.java, intent).setup().get()

    private fun call(a: StudioActivity, name: String, vararg args: Any?) {
        val m = StudioActivity::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        m.invoke(a, *args)
    }

    @Test fun studioEditsNeedSave() {
        val a = studio()
        val work = Prefs(ctx, ThemeStore.WORK)
        // 在工作室改東西：鍵盤（正在用的設定）不變
        KeyStyles.save(work, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        assertTrue(ThemeStore.isDirty(work))
        assertTrue(KeyStyles.load(live()).map.isEmpty())
        call(a, "updateTitle")
        assertTrue(a.actionBar!!.title.toString().endsWith("•"))
        // 儲存：正在用的主題，鍵盤跟著變
        call(a, "save", null)
        assertEquals(Color.RED, KeyStyles.load(live()).map["enter"]!!.bg)
        assertFalse(ThemeStore.isDirty(work))
        assertFalse(a.actionBar!!.title.toString().endsWith("•"))
    }

    @Test fun studioEditsOtherThemeAndAsksToApply() {
        val a = studio(Intent().putExtra(StudioActivity.EXTRA_THEME, "stitch"))
        val work = Prefs(ctx, ThemeStore.WORK)
        assertEquals("工作室打開的是粉紅縫線", "stitch", work.theme)
        assertEquals("classic", live().theme)
        Decals.save(work, listOf(Decal("d1", Decal.BG, "shape:heart")))
        call(a, "save", null)
        // 不是正在用的主題：問要不要套用；鍵盤還沒變
        val d = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(d.isShowing)
        assertTrue(Decals.load(live()).isEmpty())
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("stitch", ThemeStore.activeId(live()))
        assertEquals(1, Decals.load(live()).size)
    }

    @Test fun backWithUnsavedChangesAsks() {
        val a = studio()
        val work = Prefs(ctx, ThemeStore.WORK)
        // 沒改：直接離開
        call(a, "confirmLeave", { a.finish() } as () -> Unit)
        assertTrue(a.isFinishing)

        val b = studio()
        KeyStyles.save(work, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        call(b, "confirmLeave", { b.finish() } as () -> Unit)
        assertFalse("有沒存的修改：先問", b.isFinishing)
        val d = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("「經典深色」還沒儲存", org.robolectric.Shadows.shadowOf(d).title.toString())
        // 不儲存：離開，鍵盤不變
        d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(b.isFinishing)
        assertTrue(KeyStyles.load(live()).map.isEmpty())
    }

    @Test fun saveAsCreatesNewTheme() {
        val a = studio()
        val work = Prefs(ctx, ThemeStore.WORK)
        KeyStyles.save(work, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.BLUE)))
        call(a, "saveAs", null)
        val d = ShadowAlertDialog.getLatestAlertDialog()
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val mine = ThemeStore.list(ctx, live()).filter { !it.builtIn }
        assertEquals(listOf("經典深色 的副本"), mine.map { it.name })
        assertTrue("經典深色本身沒被改", KeyStyles.load(live()).map.isEmpty())
        assertEquals(Color.BLUE, ThemeStore.withTemp(ctx, ThemeStore.load(ctx, live(), mine[0].id)) { KeyStyles.load(it).map["enter"]!!.bg })
    }

    /** 升級：以前全部主題共用的自訂清掉（使用者選的），舊的功能鍵貼圖檔刪掉 */
    @Test fun migrationClearsOldSharedCustomizations() {
        val p = live()
        p.theme = "stitch"
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        Decals.save(p, listOf(Decal("d1", Decal.BG, "shape:heart")))
        val old = File(ctx.filesDir, "sticker_enter.png").apply { writeBytes(png(Color.RED)) }
        ThemeStore.migrate(ctx, p)
        assertEquals("stitch", p.theme)
        assertEquals("stitch", ThemeStore.activeId(p))
        assertTrue(KeyStyles.load(p).map.isEmpty())
        assertTrue(Decals.load(p).isEmpty())
        assertFalse(old.exists())
        // 只做一次
        KeyStyles.save(p, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
        ThemeStore.migrate(ctx, p)
        assertEquals(Color.RED, KeyStyles.load(p).map["enter"]!!.bg)
    }

    /** 匯入的主題：改過之後「恢復」是變回匯入時的樣子，不是清成底色主題 */
    @Test fun restoreToImportedLook() {
        val p = live()
        val json = ThemeStore.withTemp(ctx, ThemeStore.factory("magic_yellow")) { t ->
            val img = Decals.storeBytes(ctx, png(Color.YELLOW))!!
            Decals.save(t, listOf(Decal("x", Decal.BG, Decal.FILE + img)))
            KeyStyles.save(t, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.RED)))
            ThemeIO.export(ctx, t, "赫夫帕夫", ThemeIO.Part.values().toSet())
        }
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "look")).setup().get()
        val (id, _) = a.importAsTheme(ThemeIO.parse(json)!!, "赫夫帕夫")
        assertEquals("import", ThemeStore.originKind(ctx, id))
        ThemeStore.applyTheme(ctx, p, id)
        val importedImg = Decals.load(p).single().fileName

        // 在工作室改：Enter 改藍、圖案刪掉，存檔
        val s = studio()
        val work = Prefs(ctx, ThemeStore.WORK)
        KeyStyles.save(work, KeyStyles.EMPTY.with("enter", KeyStyle(bg = Color.BLUE)))
        Decals.save(work, emptyList())
        call(s, "save", null)
        s.finish()
        call(s, "onDestroy")
        assertTrue("圖案刪掉了，但原本的樣子還用得到那張圖，不能被清掉", File(Decals.dir(ctx), importedImg).exists())

        // 再開工作室，按「恢復成匯入時的樣子」
        val s2 = studio()
        val btn = buttons(s2.window.decorView).first { it.text == "↺ 恢復成匯入時的樣子" }
        btn.performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        val w = Prefs(ctx, ThemeStore.WORK)
        assertEquals("Enter 回到匯入時的紅色", Color.RED, KeyStyles.load(w).map["enter"]!!.bg)
        assertEquals("圖案回來了", importedImg, Decals.load(w).single().fileName)
        assertEquals("底色主題不變", "magic_yellow", w.theme)
        assertEquals("還沒儲存，鍵盤不變", Color.BLUE, KeyStyles.load(live()).map["enter"]!!.bg)

        // 設定的「⋯ → 恢復成匯入時的樣子」：整個變回去
        ThemeStore.resetToOrigin(ctx, live(), id)
        assertEquals(Color.RED, KeyStyles.load(live()).map["enter"]!!.bg)
        assertEquals(1, Decals.load(live()).size)
    }

    private fun buttons(v: View, out: ArrayList<Button> = ArrayList()): ArrayList<Button> {
        if (v is Button) out.add(v)
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) buttons(v.getChildAt(i), out)
        return out
    }

    /** 工作室裡按返回：先回上一層，在首頁才離開 */
    @Test fun backGoesUpOneLevel() {
        val a = studio()
        val cls = StudioActivity::class.java
        fun field(n: String) = cls.getDeclaredField(n).apply { isAccessible = true }
        fun back() = call(a, "back")
        // 鍵盤背景 → 首頁
        field("bgSelected").setBoolean(a, true)
        call(a, "refresh")
        back()
        assertFalse(field("bgSelected").getBoolean(a))
        assertFalse(a.isFinishing)
        // 按鍵上的圖案 → 那顆鍵 → 首頁
        val kv = field("kv").get(a) as CatKeyboardView
        kv.editPicker!!.invoke(Layouts.ZHUYIN[0][0])
        field("decalSel").set(a, "label:main")
        back()
        assertEquals(null, field("decalSel").get(a))
        assertNotNull("還在那顆鍵", field("selected").get(a))
        back()
        assertEquals(null, field("selected").get(a))
        assertFalse(a.isFinishing)
        // 首頁：離開
        back()
        assertTrue(a.isFinishing)
    }

    /** 從設定打開工作室：在首頁按返回回到設定的外觀頁（不是跳回桌面）；從桌面圖示打開的就直接關 */
    @Test fun backFromStudioReturnsToSettings() {
        val a = Robolectric.buildActivity(StudioActivity::class.java,
            Intent().putExtra(StudioActivity.EXTRA_FROM_SETTINGS, true)).setup().get()
        call(a, "back")
        assertTrue(a.isFinishing)
        val next = org.robolectric.Shadows.shadowOf(a).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, next.component!!.className)
        assertEquals("look", next.getStringExtra("page"))
        assertTrue(next.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)

        val b = studio()
        call(b, "back")
        assertTrue(b.isFinishing)
        assertEquals("從桌面圖示打開的：不用叫設定出來", null, org.robolectric.Shadows.shadowOf(b).nextStartedActivity)
    }

    /** 升級到注音、英文分開：手機裡的主題（現在用的、存起來的、原本的樣子）都複製一份給英文頁 */
    @Test fun migrationSplitsEnglishInStoredThemes() {
        val p = live()
        p.sp.edit().putInt("theme_store", 1).commit()
        KeyStyles.save(p, KeyStyles.EMPTY.with("space", KeyStyle(bg = Color.RED)))
        val id = ThemeStore.create(ctx, p, "舊主題", ThemeStore.snapshot(p)) // 存起來的（也有原本的樣子）
        ThemeStore.migrate(ctx, p)
        assertEquals(2, p.sp.getInt("theme_store", 0))
        assertEquals(Color.RED, KeyStyles.load(p).map["en/space"]!!.bg)
        for (snap in listOf(ThemeStore.load(ctx, p, id), ThemeStore.origin(ctx, id)!!)) {
            val ks = ThemeStore.withTemp(ctx, snap) { KeyStyles.load(it) }
            assertEquals(Color.RED, ks.map["en/space"]!!.bg)
        }
    }
}
