package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 把鍵盤畫成 PNG（存到 app/build/renders/），不用手機也能檢查長相。
 * 螢幕設定成跟 S24 Ultra 預設解析度一樣：寬 1080px。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RenderTest {

    private fun render(
        theme: String, layout: List<List<Key>>, name: String,
        pressAt: Pair<Float, Float>? = null, composing: String = "",
        candidates: List<String> = emptyList(), expand: Boolean = false, reading: String = "",
        longPress: Boolean = false, slideToX: Float? = null,
    ) {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        prefs.theme = theme
        val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
        v.rows = layout
        v.isChinese = layout === Layouts.ZHUYIN
        v.composing = composing
        v.candidates = candidates
        v.reading = reading
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        if (expand) {
            val t = SystemClock.uptimeMillis()
            val x = v.measuredWidth - 40f
            v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, 40f, 0))
            v.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, 40f, 0))
        }
        if (pressAt != null) {
            val t = SystemClock.uptimeMillis()
            v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pressAt.first, pressAt.second, 0))
            if (longPress) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            if (slideToX != null) {
                v.dispatchTouchEvent(MotionEvent.obtain(t, t + 700, MotionEvent.ACTION_MOVE, slideToX, pressAt.second, 0))
            }
        }
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = File("build/renders").apply { mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(f.length() > 0)
    }

    private val sampleCandidates: List<String> by lazy {
        val dict = java.io.File("src/main/assets/zhuyin.dict").inputStream().use {
            io.github.yolanda1027.catpaw.engine.ZhuyinDict.load(it)
        }
        val e = io.github.yolanda1027.catpaw.engine.ZhuyinEngine(dict, io.github.yolanda1027.catpaw.engine.UserScores(null))
        "ㄐㄧㄣ ㄊㄧㄢ ㄊㄧㄢ ㄑㄧˋ".forEach { c ->
            if (c == ' ') e.typeTone("") else if (c == 'ˋ') e.typeTone("ˋ") else e.typeSymbol(c)
        }
        e.candidates().map { it.text }
    }

    @Test
    fun renderLongPressPopup() {
        // ㄟ 在第二排第 9 顆；420dpi 下：透明區 89px + 上方列 105px + 留白 16px + 1.5 排 × 118px
        val y = 89f + 105f + 16f + 118f * 1.5f
        for (t in listOf("classic", "stitch")) {
            render(t, Layouts.ZHUYIN, "$t-popup", pressAt = 918f to y, longPress = true, slideToX = 810f)
            render(t, Layouts.ZHUYIN, "$t-popup-digit", pressAt = 54f to y - 118f, longPress = true)
        }
    }

    private fun renderSymbols(theme: String, cat: Int, page: Int, name: String) {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        prefs.theme = theme
        val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
        val c = Symbols.categories[cat]
        v.rows = Symbols.rows(c.pages[page], c.wide)
        v.symbolTabs = Symbols.categories.map { it.tab }
        v.symbolTab = cat
        v.symbolPageCount = c.pages.size
        v.symbolPage = page
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderKaomoji(theme: String, name: String) {
        val ctx = RuntimeEnvironment.getApplication()
        Symbols.loadKaomoji(File("src/main/assets/kaomoji.txt").readText())
        val prefs = Prefs(ctx)
        prefs.theme = theme
        val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
        val cats = Symbols.kaomojiGroups[0].second
        v.rows = Symbols.rows(cats.take(Symbols.PER_PAGE_WIDE), true)
        v.symbolTabs = listOf("◁", null) + Symbols.kaomojiGroups.map { it.first }
        v.symbolTab = 2
        v.symbolPageCount = (cats.size + 8) / 9
        v.symbolPage = 0
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderView(v: CatKeyboardView, name: String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 做一張假的貼圖（粉紅圓臉貓），代替使用者從相簿選的圖 */
    private fun fakeSticker(color: Int, face: String): Bitmap {
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawPath(android.graphics.Path().apply { moveTo(30f, 80f); lineTo(50f, 10f); lineTo(90f, 50f); close()
            moveTo(170f, 80f); lineTo(150f, 10f); lineTo(110f, 50f); close() }, p)
        c.drawCircle(100f, 115f, 80f, p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 60f
        p.textAlign = android.graphics.Paint.Align.CENTER
        c.drawText(face, 100f, 135f, p)
        return b
    }

    @Test
    fun renderPhase3() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        // D. 自訂配色（預設的薰衣草粉）
        prefs.theme = "custom"
        renderView(CatKeyboardView(ctx, prefs, Haptics(ctx)), "custom-default")
        // 換一組：薄荷綠
        prefs.customBg = android.graphics.Color.parseColor("#E8F5E9")
        prefs.customKey = android.graphics.Color.WHITE
        prefs.customText = android.graphics.Color.parseColor("#2E4D3A")
        prefs.customSub = android.graphics.Color.parseColor("#7FA88C")
        prefs.customAccent = android.graphics.Color.parseColor("#81C784")
        renderView(CatKeyboardView(ctx, prefs, Haptics(ctx)), "custom-mint")

        // B. 功能鍵貼圖
        val pink = android.graphics.Color.parseColor("#F48FB1")
        val purple = android.graphics.Color.parseColor("#9E86C8")
        for ((slot, img) in listOf(
            StickerSlot.ENTER to fakeSticker(pink, "ω"),
            StickerSlot.BACKSPACE to fakeSticker(purple, "×"),
            StickerSlot.SYMBOL to fakeSticker(purple, "#"),
            StickerSlot.EMOJI to fakeSticker(pink, "^^"),
        )) Stickers.file(ctx, slot).outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        for (t in listOf("classic", "stitch")) {
            prefs.theme = t
            renderView(CatKeyboardView(ctx, prefs, Haptics(ctx)), "$t-stickers")
        }

        // C. 按鍵動畫：按下 ㄍ，過 180 毫秒的樣子
        for (fx in listOf("paw", "heart", "star", "hearts", "sparkle", "flower", "butterfly", "ripple", "bubble", "snow", "note")) {
            prefs.pressEffect = fx
            prefs.theme = if (fx in listOf("star", "ripple", "snow", "bubble")) "classic" else "stitch"
            val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val t0 = SystemClock.uptimeMillis()
            val y = 89f + 105f + 16f + 118f * 1.5f
            v.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 270f, y, 0))
            v.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 50, MotionEvent.ACTION_UP, 270f, y, 0))
            org.robolectric.shadows.ShadowLooper.idleMainLooper(if (fx == "ripple") 280L else 220L, java.util.concurrent.TimeUnit.MILLISECONDS)
            renderView(v, "effect-$fx")
        }
        for (slot in StickerSlot.values()) Stickers.remove(ctx, slot)
        prefs.pressEffect = "none"
    }

    @Test
    fun renderNumpad() {
        val ctx = RuntimeEnvironment.getApplication()
        for (t in listOf("classic", "stitch")) {
            val prefs = Prefs(ctx)
            prefs.theme = t
            val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
            v.rows = Layouts.NUMPAD
            v.symbolTabs = listOf("數字")
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File("build/renders").mkdirs()
            File("build/renders/$t-numpad.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun renderClipboard() {
        val ctx = RuntimeEnvironment.getApplication()
        val h = ClipHistory(File(ctx.filesDir, "test-clips.json"))
        h.add("https://wepplication.github.io/tools/charMap/#emoticon")
        h.add("塞德里克推開玻璃木門帶她走進去，挑了最裡頭一張靠著壁爐與弧形落地窗的雙人軟墊圓桌。")
        h.add("0912-345-678")
        h.togglePin("0912-345-678")
        for (t in listOf("classic", "stitch")) {
            val prefs = Prefs(ctx)
            prefs.theme = t
            val v = CatKeyboardView(ctx, prefs, Haptics(ctx))
            v.rows = Layouts.clipRows(h.all(), 0)
            v.symbolTabs = listOf("剪貼簿　點一下貼上，長按釘選")
            v.symbolTab = 0
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File("build/renders").mkdirs()
            File("build/renders/$t-clipboard.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun renderSymbolBoards() {
        File("build/renders").mkdirs()
        renderSymbols("classic", 1, 0, "classic-sym-common")
        renderSymbols("classic", 2, 1, "classic-sym-brackets2")

        renderSymbols("stitch", 6, 0, "stitch-sym-shapes")
        renderKaomoji("classic", "classic-sym-kaomoji")
        renderKaomoji("catpaw", "catpaw-sym-kaomoji")
    }

    @Test
    fun renderCandidates() {
        for (t in listOf("classic", "stitch")) {
            render(t, Layouts.ZHUYIN, "$t-candidates", candidates = sampleCandidates, reading = "ㄐㄧㄣ ㄊㄧㄢ ㄊㄧㄢ ㄑㄧˋ")
            render(t, Layouts.ZHUYIN, "$t-candidates-expanded", candidates = sampleCandidates, expand = true)
        }
    }

    @Test
    fun renderAll() {
        for (t in listOf("classic", "stitch", "catpaw")) {
            render(t, Layouts.ZHUYIN, "$t-zhuyin")
            render(t, Layouts.ZHUYIN, "$t-zhuyin-pressed", pressAt = 270f to 420f, composing = "ㄇㄠ")
            render(t, Layouts.ENGLISH, "$t-english")
            render(t, Layouts.SYMBOLS, "$t-symbols")
            render(t, Layouts.EMOJI, "$t-emoji")
        }
    }
}
