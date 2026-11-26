package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** 每一頁（符號、顏文字、表情、我的貼圖、剪貼簿、數字）各自設計 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PageDesignTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val red = KeyStyle(bg = Color.RED)
    private val blue = KeyStyle(bg = Color.BLUE)

    @Test fun pagesDoNotBorrowFromEachOther() {
        val ks = KeyStyles(mapOf("backspace" to red, "@char" to red, "@func" to red))
        val symBack = Symbols.rows(Symbols.categories[1].pages[0], false).onPage("sym").flatten().first { it.act == Act.BACKSPACE }
        assertEquals("sym/backspace", KeyStyles.idOf(symBack))
        assertNull("符號頁的刪除鍵不借注音頁的", ks.resolve(symBack))
        assertNull(ks.resolve(Key("😀", Act.TEXT, page = "emo")))
        assertEquals(Color.RED, ks.resolve(Layouts.ZHUYIN.flatten().first { it.act == Act.BACKSPACE })!!.bg)
    }

    @Test fun copyToAllPagesThenTweakOne() {
        var ks = KeyStyles(mapOf("sym/backspace" to red))
        ks = ks.copyToAllPages("sym/backspace")
        for (p in KeyStyles.PAGES) assertEquals(p.id, red, ks.map[KeyStyles.scoped(p.id, "backspace")])
        // 複製過去之後還能各自調
        ks = ks.with("emo/backspace", blue)
        assertEquals(blue, ks.map["emo/backspace"])
        assertEquals(red, ks.map["clip/backspace"])
        // 上方列、背景圖
        ks = ks.withTopBarFor("num", TopBarStyle(bg = Color.GREEN)).copyTopBarToAllPages("num")
        assertEquals(Color.GREEN, ks.topBar!!.bg)
        assertEquals(Color.GREEN, ks.topBarFor("kao")!!.bg)
        ks = ks.withTopBarFor("kao", null)
        assertNull(ks.topBarFor("kao"))
        assertEquals(Color.GREEN, ks.topBarFor("sym")!!.bg)
    }

    @Test fun jsonKeepsPagesAndTopBars() {
        val ks = KeyStyles(mapOf("sym/backspace" to red, "clip/@keyboard" to KeyStyle(bgImage = ImageFill("decal_1.png"))))
            .withTopBarFor("emo", TopBarStyle(bg = Color.YELLOW))
        val back = KeyStyles.fromJson(ks.toJson())
        assertEquals(ks, back)
        assertEquals("decal_1.png", back.keyboardImageFor("clip")!!.img)
        assertNull(back.keyboardImage)
        assertTrue(back.imageFiles.contains("decal_1.png"))
    }

    /** 真的打字時：切到符號頁，按鍵標成符號頁、背景用符號頁自己的 */
    @Test fun imeTagsPages() {
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        assertEquals("", kv.pageScope)
        // 沒有真的輸入框時按鍵會被忽略：直接切頁
        val pageField = CatImeService::class.java.getDeclaredField("page").apply { isAccessible = true }
        fun go(name: String) {
            pageField.set(s, pageField.type.enumConstants!!.first { (it as Enum<*>).name == name })
            CatImeService::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(s)
        }
        go("SYMBOL")
        assertEquals("sym", kv.pageScope)
        assertTrue(kv.rows.flatten().all { it.page == "sym" })
        go("NUMPAD")
        assertEquals("num", kv.pageScope)
        go("EMOJI")
        assertTrue(kv.pageScope == "emo" || kv.pageScope == "stk")
        s.onClipboard()
        assertEquals("clip", kv.pageScope)
        assertTrue(kv.rows.flatten().all { it.page == "clip" })
    }

    private fun studio() = Robolectric.buildActivity(StudioActivity::class.java).setup().get()

    private fun buttons(v: View, out: ArrayList<Button> = ArrayList()): ArrayList<Button> {
        if (v is Button) out.add(v)
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) buttons(v.getChildAt(i), out)
        return out
    }

    private fun shot(a: StudioActivity, name: String) {
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun studioTabsAndCopyToAllPages() {
        val a = studio()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val tabs = buttons(a.window.decorView).map { it.text.toString() }
        for ((_, t) in StudioActivity.TABS) assertTrue("少了分頁 $t", t in tabs)
        // 每一頁都畫得出來
        for ((id, t) in StudioActivity.TABS) {
            buttons(a.window.decorView).first { it.text == t }.performClick()
            assertTrue("$t 頁沒有按鍵", kv.rows.flatten().any { it.act != Act.GAP })
            val page = if (id == "zh") "" else id
            assertEquals(page, kv.pageScope)
            assertTrue(kv.rows.flatten().all { it.page == page })
            shot(a, "studio-tab-$id")
        }
        // 符號頁：選刪除鍵、改底色
        buttons(a.window.decorView).first { it.text == "符號" }.performClick()
        val back = kv.rows.flatten().first { it.act == Act.BACKSPACE }
        kv.editPicker!!.invoke(back)
        val work = Prefs(ctx, ThemeStore.WORK)
        val cur = KeyStyles.load(work)
        KeyStyles.save(work, cur.with("sym/backspace", red))
        StudioActivity::class.java.getDeclaredField("styles").apply { isAccessible = true }.set(a, KeyStyles.load(work))
        // 再加一個圖案在這顆鍵上，順序也記一下
        Decals.save(work, listOf(Decal("dx", "sym/backspace", "shape:heart")))
        StudioActivity::class.java.getDeclaredField("decals").apply { isAccessible = true }.set(a, Decals.load(work))
        val withOrder = KeyStyles.load(work).with("sym/backspace", red.copy(order = listOf("label:main", "dx")))
        KeyStyles.save(work, withOrder)
        StudioActivity::class.java.getDeclaredField("styles").apply { isAccessible = true }.set(a, withOrder)
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        shot(a, "studio-sym-backspace")
        buttons(a.window.decorView).first { it.text.startsWith("📋 這顆鍵的設計套用到所有頁面") }.performClick()
        val d = ShadowAlertDialog.getLatestAlertDialog()
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        val after = KeyStyles.load(work)
        for (p in KeyStyles.PAGES) assertEquals(p.title, Color.RED, after.map[KeyStyles.scoped(p.id, "backspace")]!!.bg)
        val ds = Decals.load(work)
        assertEquals("每一頁的刪除鍵都有一個愛心", KeyStyles.PAGES.size, ds.count { KeyStyles.baseOf(it.target) == "backspace" })
        // 順序換成那一頁自己的圖案 id
        val emoDecal = ds.first { it.target == "emo/backspace" }
        assertEquals(listOf("label:main", emoDecal.id), after.map["emo/backspace"]!!.order)
        // 鍵盤（正在用的設定）還沒變：要儲存
        assertNull(KeyStyles.load(Prefs(ctx)).map["backspace"])
    }

    @Test fun backgroundPerPage() {
        val a = studio()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        buttons(a.window.decorView).first { it.text == "數字" }.performClick()
        val work = Prefs(ctx, ThemeStore.WORK)
        Decals.save(work, listOf(Decal("bg1", Decal.bgFor("num"), "shape:star", x = 0.5f, y = 0.5f, size = 0.3f)))
        StudioActivity::class.java.getDeclaredField("decals").apply { isAccessible = true }.set(a, Decals.load(work))
        kv.setDecalsLive(Decals.load(work))
        StudioActivity::class.java.getDeclaredField("bgSelected").apply { isAccessible = true }.set(a, true)
        StudioActivity::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(a)
        buttons(a.window.decorView).first { it.text == "📋 這一頁的背景套用到所有頁面" }.performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        val ds = Decals.load(work)
        for (p in KeyStyles.PAGES) assertNotNull(p.title, ds.firstOrNull { it.target == Decal.bgFor(p.id) })
        assertEquals(KeyStyles.PAGES.size, ds.map { it.id }.toSet().size)
    }

    /** 總覽：這個主題做了哪些設計，按「改 ›」直接跳到那一頁那一顆 */
    @Test fun overviewListsDesignsAndJumps() {
        val p = Prefs(ctx)
        KeyStyles.save(p, KeyStyles(mapOf(
            "@zhuyin" to KeyStyle(bgImage = ImageFill("decal_a.png", shape = true), text = Color.DKGRAY),
            "sym/backspace" to red,
            "clip/@keyboard" to KeyStyle(bgImage = ImageFill("decal_b.png")),
        )).withTopBarFor("emo", TopBarStyle(bg = Color.YELLOW)))
        Decals.save(p, listOf(Decal("d1", "enter", "shape:heart")))
        val a = studio()
        val cls = StudioActivity::class.java
        fun field(n: String) = cls.getDeclaredField(n).apply { isAccessible = true }
        buttons(a.window.decorView).first { it.text.startsWith("🔍 設計總覽") }.performClick()
        val texts = ArrayList<String>()
        fun walk(v: View) {
            if (v is android.widget.TextView && v !is Button) texts.add(v.text.toString())
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(a.window.decorView)
        for (want in listOf("注音頁", "所有注音鍵", "底圖（去背形狀）、文字顏色", "Enter 鍵", "圖案 1 個",
                "符號頁", "刪除鍵", "剪貼簿頁", "鍵盤背景", "表情符號頁", "上方列")) {
            assertTrue("總覽裡少了「$want」：$texts", texts.any { it.contains(want) })
        }
        shot(a, "studio-overview")
        // 符號頁的刪除鍵：按「改 ›」→ 跳到符號頁、選好那顆
        val rows = ArrayList<View>()
        fun rowsOf(v: View) {
            if (v is android.widget.LinearLayout && v.childCount == 2 && v.getChildAt(1) is Button && (v.getChildAt(1) as Button).text == "改 ›") rows.add(v)
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) rowsOf(v.getChildAt(i))
        }
        rowsOf(a.window.decorView)
        val symRow = rows.first { r ->
            val t = ArrayList<String>(); walk(r)
            ((r as android.view.ViewGroup).getChildAt(0) as android.view.ViewGroup).getChildAt(0).let { (it as android.widget.TextView).text == "刪除鍵" } &&
                rows.indexOf(r) > rows.indexOfFirst { x -> ((x as android.view.ViewGroup).getChildAt(0) as android.view.ViewGroup).getChildAt(0).let { (it as android.widget.TextView).text == "所有注音鍵" } }
        }
        ((symRow as android.view.ViewGroup).getChildAt(1) as Button).performClick()
        val sel = field("selected").get(a) as Key
        assertEquals(Act.BACKSPACE, sel.act)
        assertEquals("sym", sel.page)
        assertEquals("sym", field("tab").get(a))
        // 返回：回到總覽
        StudioActivity::class.java.getDeclaredMethod("back").apply { isAccessible = true }.invoke(a)
        assertEquals(null, field("selected").get(a))
        assertTrue(field("overview").getBoolean(a))
    }

    /** 點按鍵只改那一顆；一整類一起改從首頁進去 */
    @Test fun groupEditingFromHome() {
        val a = studio()
        val cls = StudioActivity::class.java
        fun field(n: String) = cls.getDeclaredField(n).apply { isAccessible = true }
        val kv = field("kv").get(a) as CatKeyboardView
        // 首頁：注音分頁只列注音鍵盤上的類別（「，」只有一顆，不列）
        val labels = buttons(a.window.decorView).map { it.text.toString() }
        for (g in listOf("所有注音鍵", "所有功能鍵")) assertTrue("首頁少了 $g：$labels", labels.any { it.contains(g) })
        assertTrue("注音分頁不該有英文鍵：$labels", labels.none { it.contains("所有英文") })
        // 英文分頁：列英文鍵，不列注音鍵
        buttons(a.window.decorView).first { it.text == "英文" }.performClick()
        val en = buttons(a.window.decorView).map { it.text.toString() }
        assertTrue("英文分頁要有英文鍵：$en", en.any { it.contains("所有英文字母、標點鍵") })
        assertTrue(en.none { it.contains("所有注音鍵") })
        // 符號、顏文字分頁：同一類，名字照那一頁寫
        buttons(a.window.decorView).first { it.text == "符號" }.performClick()
        assertTrue(buttons(a.window.decorView).any { it.text.toString().contains("所有符號鍵") })
        buttons(a.window.decorView).first { it.text == "顏文字" }.performClick()
        assertTrue(buttons(a.window.decorView).any { it.text.toString().contains("所有顏文字、標點鍵") })
        buttons(a.window.decorView).first { it.text == "注音" }.performClick()
        // 點單顆鍵：沒有「只改這一顆／所有注音鍵」的選項
        kv.editPicker!!.invoke(Layouts.ZHUYIN[0][0])
        assertTrue(!(field("groupScope").getBoolean(a)))
        val radios = ArrayList<String>()
        fun walk(v: View) {
            if (v is android.widget.RadioButton) radios.add(v.text.toString())
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(a.window.decorView)
        assertTrue("單顆鍵不該有範圍選項：$radios", radios.none { it == "只改這一顆" || it == "所有注音鍵" })
        // 回首頁、點「所有注音鍵」：改整類
        cls.getDeclaredMethod("back").apply { isAccessible = true }.invoke(a)
        buttons(a.window.decorView).first { it.text.contains("所有注音鍵") }.performClick()
        assertTrue(field("groupScope").getBoolean(a))
        shot(a, "studio-group-zhuyin")
        // 這時點鍵盤上的任何一顆（包括同一顆）：變成只改那顆
        kv.editPicker!!.invoke(field("selected").get(a) as Key)
        assertTrue(!(field("groupScope").getBoolean(a)))
        // 剪貼簿頁：有「所有剪貼簿項目」
        buttons(a.window.decorView).first { it.text == "剪貼簿" }.performClick()
        assertTrue(buttons(a.window.decorView).any { it.text.contains("所有剪貼簿項目") })
    }

    /** 注音、英文各改各的（同一顆空白鍵在兩頁大小不一樣） */
    @Test fun zhuyinAndEnglishAreSeparatePages() {
        val zhSpace = Layouts.ZHUYIN.flatten().first { it.act == Act.SPACE }
        val enSpace = Layouts.ENGLISH.onPage("en").flatten().first { it.act == Act.SPACE }
        assertEquals("space", KeyStyles.idOf(zhSpace))
        assertEquals("en/space", KeyStyles.idOf(enSpace))
        val ks = KeyStyles(mapOf("space" to red, "en/space" to blue))
        assertEquals(red, ks.resolve(zhSpace))
        assertEquals(blue, ks.resolve(enSpace))
        // 打字時：英文鍵盤是英文頁
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        CatImeService::class.java.getDeclaredField("chinese").apply { isAccessible = true }.setBoolean(s, false)
        CatImeService::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(s)
        assertEquals("en", kv.pageScope)
        assertTrue(kv.rows.flatten().all { it.page == "en" })
    }

    /** 以前注音和英文共用設計：拆開時複製一份給英文頁（只複製英文鍵盤用得到的），英文鍵盤樣子不變 */
    @Test fun splitCopiesSharedDesignToEnglish() {
        val p = Prefs(ctx)
        KeyStyles.save(p, KeyStyles(mapOf(
            "space" to red.copy(order = listOf("label:main", "d1")),
            "@char" to blue,
            "@zhuyin" to blue,
            "@keyboard" to KeyStyle(bgImage = ImageFill("decal_bg.png")),
        ), topBar = TopBarStyle(bg = Color.BLACK)))
        Decals.save(p, listOf(Decal("d1", "space", "shape:heart"), Decal("bg1", Decal.BG, "shape:star"), Decal("z1", "zy:ㄅ", "shape:paw")))
        ThemeStore.splitEnglish(p)
        val ks = KeyStyles.load(p)
        assertEquals(Color.RED, ks.map["en/space"]!!.bg)
        assertEquals(blue, ks.map["en/@char"])
        assertNull("注音鍵的設定不用複製", ks.map["en/@zhuyin"])
        assertEquals("decal_bg.png", ks.keyboardImageFor("en")!!.img)
        assertEquals(Color.BLACK, ks.topBarFor("en")!!.bg)
        val ds = Decals.load(p)
        val enHeart = ds.single { it.target == "en/space" }
        assertTrue(enHeart.id != "d1")
        assertEquals("上下順序換成英文頁那份的 id", listOf("label:main", enHeart.id), ks.map["en/space"]!!.order)
        assertEquals(1, ds.count { it.target == Decal.bgFor("en") })
        assertTrue(ds.none { it.target == "en/zy:ㄅ" })
        // 原本注音頁的不動；再拆一次不會重複
        assertEquals(Color.RED, ks.map["space"]!!.bg)
        ThemeStore.splitEnglish(p)
        assertEquals(ds.size, Decals.load(p).size)
    }

    /** 舊的主題檔（沒有分注音、英文）匯入：英文頁也有一份；新的主題檔不會多複製 */
    @Test fun oldThemeFileImportSplits() {
        val old = org.json.JSONObject().put("format", ThemeIO.FORMAT).put("version", 1).put("name", "舊主題").put("theme", "classic")
            .put("keyStyles", org.json.JSONObject().put("space", org.json.JSONObject().put("bg", "#FF0000")))
        val p = Prefs(ctx)
        ThemeIO.apply(ctx, p, old)
        assertNotNull(KeyStyles.load(p).map["en/space"])
        val fresh = ThemeIO.parse(ThemeIO.export(ctx, p, "新", ThemeIO.Part.values().toSet()))!!
        assertEquals(2, fresh.optInt("pages"))
        KeyStyles.save(p, KeyStyles(mapOf("space" to red)))
        ThemeIO.apply(ctx, p, fresh.put("keyStyles", org.json.JSONObject().put("space", org.json.JSONObject().put("bg", "#FF0000"))))
        assertNull("新格式的主題檔：英文頁沒設計就是沒設計", KeyStyles.load(p).map["en/space"])
    }

    /** 英文頁打字：按著的時候鍵盤重新整理（例如前一個字剛送出），放大的字和按的鍵不能被弄丟 */
    @Test fun englishPressSurvivesRefresh() {
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        CatImeService::class.java.getDeclaredField("chinese").apply { isAccessible = true }.setBoolean(s, false)
        val refresh = CatImeService::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }
        refresh.invoke(s)
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val rowsBefore = kv.rows
        refresh.invoke(s)
        assertTrue("同一頁重新整理：拿到同一份按鍵清單", kv.rows === rowsBefore)
        fun f(n: String) = CatKeyboardView::class.java.getDeclaredField(n).apply { isAccessible = true }
        val kr = (f("keyRects").get(kv) as List<*>).first {
            (it!!.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(it) as Key).label == "a"
        }!!
        val r = kr.javaClass.getDeclaredField("rect").apply { isAccessible = true }.get(kr) as android.graphics.RectF
        val t0 = android.os.SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, r.centerX(), r.centerY() + kv.floatH, 0))
        assertNotNull(f("pressed").get(kv))
        assertEquals("a", f("previewLabel").get(kv))
        // 按著的時候重新整理（就算拿到內容一樣、但不是同一份的清單）
        refresh.invoke(s)
        kv.rows = Layouts.ENGLISH.map { row -> row.map { it.copy(page = "en") } }
        assertNotNull("按著的鍵不能被弄丟", f("pressed").get(kv))
        kv.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 60, android.view.MotionEvent.ACTION_UP, r.centerX(), r.centerY() + kv.floatH, 0))
        assertNull("放開：放大的字收起來", f("previewLabel").get(kv))
        assertNull(f("pressed").get(kv))
    }
}
