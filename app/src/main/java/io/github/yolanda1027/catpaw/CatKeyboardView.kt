package io.github.yolanda1027.catpaw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 整個鍵盤畫面（上方「中 / En」列 + 按鍵）都在這一個 View 裡自己畫，
 * 這樣按鍵要長成貓爪、加裝飾都不受限制。
 */
@SuppressLint("ViewConstructor")
class CatKeyboardView(
    context: Context,
    private val prefs: Prefs,
    private val haptics: Haptics,
) : View(context) {

    interface Listener {
        fun onKey(key: Key, longPress: Boolean)
        fun onTabChinese()
        fun onTabEnglish()
        fun onHide()
        fun onCandidate(index: Int)
        fun onSymbolTab(index: Int)
        fun onSymbolSwipe(direction: Int)
        fun onClipboard()
        fun onQuick(item: String)
        /** 按了上方的「簡」：切換簡體／繁體 */
        fun onToggleSimplified() {}
        /** 長按「最近使用」裡的符號／表情，選了選單上的動作（pin、left、right、remove） */
        fun onItemAction(key: Key, action: String) {}
    }

    private class KeyRect(val key: Key, val rect: RectF)

    var listener: Listener? = null

    var rows: List<List<Key>> = Layouts.ZHUYIN
        set(v) {
            // 比內容不比是不是同一份：一樣的按鍵就不要重排、不要清掉正在按的鍵
            //（以前英文頁每打一個字都換一份一樣的清單，放大的字收不回去、動畫卡住、也變卡）
            if (field !== v && field != v) {
                field = v
                pressed = null
                requestLayout()
                layoutKeys()
                invalidate()
            }
        }
    var isChinese = true
        set(v) { field = v; invalidate() }
    var shiftState = 0
        set(v) { field = v; invalidate() }
    var composing = ""
        set(v) { field = v; invalidate() }

    /** 選字列的內容（空的時候顯示「中 / En」） */
    var candidates: List<String> = emptyList()
        set(v) {
            field = v
            candScroll = 0f
            panelScroll = 0f
            stripCache = null
            panelCache = null
            if (v.isEmpty()) expanded = false
            invalidate()
        }
    private var expanded = false
    private var candScroll = 0f
    private var panelScroll = 0f
    private var stripCache: List<RectF>? = null
    private var panelCache: List<RectF>? = null
    private val candPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // 拖曳選字列／展開面板
    private var dragMode = 0 // 0 = 沒有、1 = 選字列左右滑、2 = 展開面板上下滑
    private var dragStart = 0f
    private var dragScrollStart = 0f
    private var dragMoved = false

    private val dp = resources.displayMetrics.density
    private var theme: KbTheme = Themes.byName(prefs.theme, dp, prefs)
    private var stickers: Map<StickerSlot, android.graphics.Bitmap> = Stickers.load(context, prefs)
    private var keyStyles = KeyStyles.load(prefs)
    private var decals: List<Decal> = Decals.load(prefs)
    private var decalBitmaps: Map<String, android.graphics.Bitmap> = Decals.bitmaps(context, decals)
    private val effects = PressEffects(dp)
    private var fontSet = loadFonts()

    private fun loadFonts() = FontSet(
        Fonts.get(context, prefs.zhFont), Fonts.get(context, prefs.zhuyinFont), Fonts.get(context, prefs.enFont),
    )
    private var keyH = prefs.keyHeightDp * dp
    // 這些比例是照 Google 注音截圖量的：上方列 40dp，按鍵區上面留 6dp、下面留 10dp
    private val topH = 40f * dp // 跟 TOP_DP 一樣
    private val padTop = 6 * dp
    private val padBottom = 10 * dp
    private val keyRects = ArrayList<KeyRect>()

    /**
     * 鍵盤上方的透明區域，用來放「正在打的注音」小框框（像 Google 注音）。
     * 這塊不會擋到後面的 App，點下去也會穿過去（見 CatImeService.onComputeInsets）。
     */
    val floatH = 34 * dp
    /**
     * 最下面系統導覽列（手勢條或三顆按鈕）的高度。
     * Android 15 以後（targetSdk ≥ 35）鍵盤也是「滿版」，系統不會再自動讓位，要自己把按鍵往上移；
     * 這一段只畫背景，不放按鍵。只有真的當輸入法時才會設（設定頁、工作室的預覽是 0）。
     */
    var navInset = 0
        set(v) {
            if (field != v) {
                field = v
                requestLayout()
                invalidate()
            }
        }
    private val kbH get() = height - floatH - navInset

    /**
     * 現在顯示的是哪一頁（KeyStyles.PAGES）："" 注音、"en" 英文、"sym" 符號…
     * 背景圖、上方列、背景圖案都照這一頁自己的設定；按鍵本身用 Key.page 分。
     */
    var pageScope: String = ""
        set(v) {
            if (field == v) return
            field = v
            stripCache = null
            panelCache = null
            invalidate()
        }

    /** 這個圖案是貼在「這一頁」的背景上 */
    private fun onMyBg(d: Decal) = d.target == Decal.bgFor(pageScope)

    /** 導覽列那一條塗鍵盤底色（設定裡的開關）；false = 塗系統原本的導覽列顏色 */
    private var navBarBg = false

    /** 系統原本的導覽列顏色（輸入法服務會設定；0 = 不畫） */
    var systemNavColor = 0
        set(v) { field = v; invalidate() }
    private val navPaint = android.graphics.Paint()

    /** 符號鍵盤的分類列（null = 不是符號鍵盤）；裡面的 null 代表「最近使用」的時鐘圖示 */
    var symbolTabs: List<String?>? = null
        set(v) {
            // 換了一組分類（例如符號 → 顏文字）：從頭開始
            if (field != v) { tabScroll = 0f; tabEnsure = true; tabScroller.forceFinished(true) }
            field = v
            invalidate()
        }
    var symbolTab = 0
        set(v) { if (field != v) tabEnsure = true; field = v; invalidate() }
    var symbolPage = 0
        set(v) { field = v; invalidate() }
    var symbolPageCount = 1
        set(v) { field = v; invalidate() }

    /**
     * 按鍵區永遠是 5 排的高度；符號鍵盤只有 4 排，就把每排拉高一點，
     * 這樣切換時鍵盤不會忽高忽低（Google 注音也是這樣）。
     */
    private val keyAreaH get() = keyH * 5
    private val rowH get() = keyAreaH / (if (scrollRows > 0) scrollRows + 1 else rows.size).coerceAtLeast(1)

    /** 正在打的注音（顯示在鍵盤左上方的小框框裡） */
    var reading = ""
        set(v) { field = v; invalidate() }

    private var pressed: KeyRect? = null
    private var pressedPointer = -1
    private var longFired = false
    private var previewLabel: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        val kr = pressed ?: return@Runnable
        longFired = true
        haptics.perform(this, prefs)
        if (kr.key.act == Act.STICKER) {
            // 我的貼圖：長按顯示放大版，放開就關掉（不會送出）
            showStickerZoom(kr.key.text)
            invalidate()
            return@Runnable
        }
        val opts = popupOptionsFor(kr.key)
            ?: itemActions?.invoke(kr.key)?.let { acts ->
                // 一開始選「取消」（不在第一個也一樣，例如剪貼簿放中間）
                val def = acts.indexOfFirst { it.first == "cancel" }.coerceAtLeast(0)
                PopupOptions(acts.map { it.second }, def, def, actions = acts.map { it.first })
            }
        if (opts != null) {
            // 跳出長按選單，手指左右滑來選，放開就打出來
            popup = opts
            popupKr = kr
            popupSel = opts.default
            popupRect = popupRectFor(kr, opts)
            popupAnchorX = lastMoveX
            popupMoved = false
            previewLabel = null
        } else {
            listener?.onKey(kr.key, true)
        }
        invalidate()
    }

    // ---------- 長按選單（像 Google 注音：O ㄟ o） ----------

    /**
     * @param main 選項裡哪一個是原本的注音
     * @param actions 有的話＝這是「動作選單」（釘選、往前…），放開時不打字，改叫 onItemAction
     */
    private class PopupOptions(val items: List<String>, val default: Int, val main: Int, val actions: List<String>? = null)

    /** 這顆鍵長按要不要跳「動作選單」（最近使用的符號、表情）：(動作代號, 顯示的字)；null = 不用 */
    var itemActions: ((Key) -> List<Pair<String, String>>?)? = null

    /** 這顆鍵是不是釘選的（右上角畫一個小圖釘） */
    var isPinned: ((Key) -> Boolean)? = null

    private var popup: PopupOptions? = null
    /** 哪一顆鍵跳出來的長按選單（選單照這顆鍵的樣式畫） */
    private var popupKr: KeyRect? = null
    private var popupSel = 0
    private var popupRect = RectF()

    private fun popupOptionsFor(key: Key): PopupOptions? {
        val sub = key.sub ?: return null
        if (key.act != Act.ZHUYIN && key.act != Act.TEXT) return null
        return if (sub.length == 1 && sub[0].isLetter() && sub[0].code < 128) {
            // 英文字母：大寫、注音、小寫，預設停在注音
            PopupOptions(listOf(sub.uppercase(), key.label, sub.lowercase()), default = 1, main = 1)
        } else {
            val l = key.label
            if (key.act == Act.TEXT && l.length == 1 && l[0].isLetter()) {
                // 英文字母：符號、現在的大小寫、另一種大小寫（例如 @ a A），
                // 預設停在原本要打的字（小寫就是小寫、開 Shift 就是大寫），長按放開不會打錯字
                val now = labelFor(key)
                val other = if (now == l.uppercase()) l.lowercase() else l.uppercase()
                PopupOptions(listOf(sub, now, other), default = 1, main = 1)
            } else {
                // 數字、符號：預設停在數字／符號（長按放開＝打數字，跟以前一樣）
                PopupOptions(listOf(sub, key.label), default = 0, main = 1)
            }
        }
    }

    private fun popupRectFor(kr: KeyRect, o: PopupOptions): RectF {
        // 動作選單的字比較多（「釘選」「解除釘選」），每格寬一點（但不要太寬，「取消」才對得準手指）；
        // 但不能比「螢幕寬 ÷ 選項數」寬（剪貼簿那種整排寬的鍵，選單才放得進畫面）
        val raw = if (o.actions != null) max(kr.rect.width(), keyH * 1.3f) else kr.rect.width()
        val optW = if (o.actions != null) min(raw, min(keyH * 1.75f, width.toFloat() / o.items.size)) else min(raw, width.toFloat() / o.items.size)
        val w = optW * o.items.size
        // 預設的選項剛好在手指正上方（動作選單：剪貼簿那種整排寬的鍵，要對準手指，不是對準鍵的中間）
        val anchor = if (o.actions != null) downX else kr.rect.centerX()
        var left = anchor - optW * (o.default + 0.5f)
        left = left.coerceIn(0f, max(0f, width - w))
        // 整個選單浮在按鍵上面（像 Google 注音），手指才不會擋到選項；太上面放不下才往下壓
        val bottom = kr.rect.top - keyH * 0.3f
        val top = max(-floatH, bottom - keyH * 1.15f)
        return RectF(left, top, left + w, top + keyH * 1.15f)
    }

    private fun drawPopup(c: Canvas) {
        val o = popup ?: return
        val rowH = if (o.actions != null) keyH * 0.62f else keyH
        val kr = popupKr
        val st = kr?.let { keyStyles.resolve(it.key) }
        val ps = keyStyles.popup?.takeIf { it.menuSet }
        val styled = kr != null && st != null && st.customizedLook
        if (!styled && ps == null) {
            theme.drawPopup(c, popupRect, o.items, popupSel, rowH)
            return
        }
        // 主題工作室改過這顆鍵：選單照這顆鍵的顏色；有設「長按選單」的顏色就用那個
        val r = popupRect
        val (bg, selBg, text, selText) = menuColors(kr, st, styled).toList()
        val col = if (styled) lookOf(kr!!, st!!) else null
        val rad = if (styled) min(theme.styledRadius(r, st!!), r.height() / 2) else 8 * dp
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = bg
        c.drawRoundRect(r, rad, rad, p)
        val optW = r.width() / o.items.size
        val sel = RectF(r.left + optW * popupSel, r.top, r.left + optW * (popupSel + 1), r.bottom)
        p.color = selBg
        c.drawRoundRect(sel, rad, rad, p)
        o.items.forEachIndexed { i, t ->
            theme.drawText(c, t, r.left + optW * (i + 0.5f), r.centerY(), rowH * 0.5f, if (i == popupSel) selText else text)
        }
        if (styled) col?.border?.let { theme.drawBorderBox(c, r, rad, it, st!!.borderWidth, st.borderStyle, st.borderInset) }
    }

    /** 長按選單的四個顏色：底色、選中那格、字、選中那格的字 */
    private fun menuColors(kr: KeyRect?, st: KeyStyle?, styled: Boolean): IntArray {
        val ps = keyStyles.popup
        val col = if (styled) lookOf(kr!!, st!!) else Look(theme.popupBg, theme.popupText, null)
        val selBg = ps?.sel ?: (if (styled) col.border?.takeIf { android.graphics.Color.alpha(it) > 0 }
            ?: topStyle?.tab?.takeIf { android.graphics.Color.alpha(it) > 0 } else null)
            ?: theme.popupSelBg
        val selText = ps?.selText ?: if (!styled && ps?.sel == null) theme.popupSelText else CustomTheme.contrastOn(selBg)
        return intArrayOf(ps?.bg ?: col.bg, selBg, ps?.text ?: col.text, selText)
    }

    /** 主題工作室顯示用：長按這顆鍵時選單的顏色（底色、選中那格、字、選中那格的字） */
    fun popupMenuColors(key: Key?): IntArray {
        val kr = key?.let { k -> keyRects.firstOrNull { it.key == k } }
        val st = kr?.let { keyStyles.resolve(it.key) }
        return menuColors(kr, st, kr != null && st != null && st.customizedLook)
    }

    /** 主題工作室顯示用：按這顆鍵時泡泡的顏色（底色、字） */
    fun bubbleColors(key: Key?): IntArray {
        val kr = key?.let { k -> keyRects.firstOrNull { it.key == k } }
        val st = kr?.let { keyStyles.resolve(it.key) }
        val ps = keyStyles.popup
        val col = if (kr != null && st != null && st.customizedLook) lookOf(kr, st) else Look(theme.previewBg, theme.previewText, null)
        return intArrayOf(ps?.bubbleBg ?: col.bg, ps?.bubbleText ?: col.text)
    }

    /** 主題工作室的「長按選單」面板：畫一個樣本（跟長按某顆鍵時一樣的畫法） */
    fun drawPopupSample(c: Canvas, r: RectF, items: List<String>, sel: Int, key: Key?) {
        val savedPopup = popup
        val savedKr = popupKr
        val savedSel = popupSel
        val savedRect = popupRect
        try {
            popup = PopupOptions(items, sel, 0)
            popupKr = key?.let { k -> keyRects.firstOrNull { it.key == k } }
            popupSel = sel
            popupRect = r
            drawPopup(c)
        } finally {
            popup = savedPopup; popupKr = savedKr; popupSel = savedSel; popupRect = savedRect
        }
    }

    /** 主題工作室的樣本：泡泡（跟按下某顆鍵時一樣的畫法） */
    fun drawBubbleSample(c: Canvas, box: RectF, key: Key?, label: String) {
        val kr = key?.let { k -> keyRects.firstOrNull { it.key == k } }
        drawBubble(c, box, kr, label)
    }

    /** 泡泡、長按選單要用的顏色：自己設的 > 量到的（底圖的話量畫面上的顏色）> 主題原本的 */
    private class Look(val bg: Int, val text: Int, val border: Int?)

    private fun lookOf(kr: KeyRect, st: KeyStyle): Look {
        val native = nativeColors(kr)
        val bg = st.bg?.takeIf { android.graphics.Color.alpha(it) > 0 }
            ?: (if (st.bgImage != null) styledColors(kr)?.bg else null)
            ?: native?.takeIf { it.hasBody }?.bg
            ?: theme.previewBg
        val text = st.text ?: native?.text ?: theme.previewText
        val border = st.border ?: native?.takeIf { it.hasBorder && st.isShaped }?.border
        return Look(bg or (0xFF shl 24), text, border)
    }

    /** 畫面上（套了樣式之後）量到的顏色：有底圖的鍵用來決定泡泡、選單的底色 */
    private val styledCache = HashMap<String, KeyColors?>()
    private fun styledColors(kr: KeyRect): KeyColors? {
        val k = KeyStyles.idOf(kr.key) + "@" + kr.rect.width().toInt() + "x" + kr.rect.height().toInt()
        return styledCache.getOrPut(k) { sample(kr, styled = true) }
    }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val kr = pressed ?: return
            longFired = true
            haptics.perform(this@CatKeyboardView, prefs)
            listener?.onKey(kr.key, false)
            handler.postDelayed(this, 50)
        }
    }

    fun reloadPrefs() {
        theme = Themes.byName(prefs.theme, dp, prefs)
        stickers = Stickers.load(context, prefs)
        keyStyles = KeyStyles.load(prefs)
        globalEffect = KeyEffect(prefs.pressEffect, prefs.customEffect, prefs.pressEffectColor)
        effectBitmaps.clear()
        stopEffectAnims()
        decals = Decals.load(prefs).map(::placeDecal)
        decalBitmaps = Decals.bitmaps(context, decals, decalBitmaps)
        nativeCache.clear()
        styledCache.clear()
        fontSet = loadFonts()
        stripCache = null
        panelCache = null
        tabCache = null
        keyH = prefs.keyHeightDp * dp
        navBarBg = prefs.navBarKeyboardBg
        requestLayout()
        layoutKeys()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (floatH + topH + quickH + padTop + keyAreaH + padBottom).toInt() + navInset
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
        stripCache = null
        panelCache = null
    }

    private fun layoutKeys() {
        keyRects.clear()
        // 量好的按鍵顏色不用清：記的時候已經帶著「哪一頁的哪顆鍵、多大」，換頁回來還能用
        //（換主題、改設計時 reloadPrefs 會清）
        val w = width.toFloat()
        bottomRowFirst = 0
        if (w <= 0f) return
        // 連續捲動（剪貼簿）：上面那幾排跟著捲動的位置往上移；最下面那排固定
        gridScroll = gridScroll.coerceIn(0f, maxGridScroll)
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = w / total
            val last = ri == rows.size - 1
            if (last) bottomRowFirst = keyRects.size
            val top = topH + quickH + padTop + (if (scrollRows > 0 && last) scrollRows else ri) * rowH -
                (if (scrollRows > 0 && !last) gridScroll else 0f)
            var x = 0f
            for (k in row) {
                val kw = unit * k.weight
                keyRects.add(KeyRect(k, RectF(x, top, x + kw, top + rowH)))
                x += kw
            }
        }
        // 鍵盤大小、這一頁的按鍵變了：圖案照分區、靠邊重新放
        decals = decals.map(::placeDecal)
    }

    private fun labelFor(key: Key): String {
        if (shiftState > 0 && !isChinese && key.act == Act.TEXT && key.label.length == 1 && key.label[0].isLetter()) {
            return key.label.uppercase()
        }
        return key.label
    }

    // ---------- 畫圖 ----------

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        theme.fonts = fontSet
        if (reading.isNotEmpty()) drawReadingBox(c)
        c.save()
        c.translate(0f, floatH) // 下面全部用「鍵盤本體」的座標畫
        theme.resetPaints() // 上一張畫面留下的透明度、遮罩清掉（不然背景有時候會整片透明）
        theme.drawBackground(c, w, kbH, topH)
        if (navInset > 0 && navBarBg) {
            // 導覽列底下：設定要塗鍵盤底色時，背景延伸下去
            c.save()
            c.clipRect(0f, kbH, w, kbH + navInset)
            theme.drawBackground(c, w, kbH + navInset, topH)
            c.restore()
            theme.resetPaints()
        } else if (navInset > 0 && android.graphics.Color.alpha(systemNavColor) > 0) {
            // 預設：塗系統原本的導覽列顏色（跟其他鍵盤一樣）
            navPaint.color = systemNavColor
            c.drawRect(0f, kbH, w, kbH + navInset, navPaint)
        }
        drawTopBarBg(c, w)
        keyStyles.keyboardImageFor(pageScope)?.let { ImageFill.draw(c, it, keyboardImageFrame(), null, fillBitmap(it.img), fillAnim(it)) }
        drawBgDecals(c, front = false)
        // 選字列用「選字字體」，「中 / En」和分類列用按鍵字體
        drawTopBar(c, w)
        if (expanded) {
            // 展開的選字面板蓋住整個鍵盤，快捷符號列先收起來
            drawPanel(c, w)
        } else {
            drawQuickBar(c)
            val now = android.os.SystemClock.uptimeMillis()
            // 翻頁中：符號那幾排跟著偏移（最下面那排返回、空白不動）
            val sliding = pageOffset != 0f && symbolTabs != null
            val scrollGrid = scrollRows > 0
            val gridBottom = gridBottomY
            for ((idx, kr) in keyRects.withIndex()) {
                if (kr.key.act == Act.GAP) continue
                val gridKey = idx < bottomRowFirst
                // 捲動中：捲到看不到的那幾排不用畫
                if (scrollGrid && gridKey && (kr.rect.bottom <= topH + quickH || kr.rect.top >= gridBottom)) continue
                val inGrid = (sliding || scrollGrid) && gridKey
                if (inGrid) {
                    c.save()
                    c.clipRect(0f, if (scrollGrid) topH + quickH else topH, w, gridBottom)
                    if (verticalPaging) c.translate(0f, pageOffset) else c.translate(pageOffset, 0f)
                }
                val s = effects.scaleFor(kr.rect, now)
                if (s != 1f) { c.save(); c.scale(s, s, kr.rect.centerX(), kr.rect.centerY()) }
                drawKeyFull(c, kr)
                if (kr.key.act == Act.STICKER) drawMySticker(c, kr)
                if (isPinned?.invoke(kr.key) == true) {
                    // 釘選的：右上角一個小圖釘
                    theme.drawText(c, "📌", kr.rect.right - rowH * 0.16f, kr.rect.top + rowH * 0.17f, rowH * 0.2f, theme.text)
                }
                if (s != 1f) c.restore()
                if (inGrid) c.restore()
            }
            drawBgDecals(c, front = true)
            drawEditHighlight(c)
            drawDecalSelection(c)
            drawPageIndicator(c, w)
            effects.draw(c, now)
            drawPreview(c, w)
            if (effects.active(now)) postInvalidateOnAnimation()
        }
        c.restore()
    }

    /** 畫一顆鍵：主題原本的樣子，再套上功能鍵貼圖、主題工作室的單鍵樣式 */
    /**
     * 畫一顆鍵：主題原本的樣子，再套上功能鍵貼圖、主題工作室的單鍵樣式。
     *
     * 單鍵樣式只要改了底色、圓角或外框，這顆鍵就整個換成自訂的形狀；
     * 沒改到的顏色用主題原本的顏色（量出來的），所以看起來還是同一套。
     * @param content false = 只畫底框不畫字（量顏色用）
     * @param styled false = 不套單鍵樣式（量主題原本的顏色用）
     */
    private fun drawOneKey(c: Canvas, kr: KeyRect, content: Boolean = true, styled: Boolean = true) {
        val isPressed = kr === pressed
        val sticker = StickerSlot.of(kr.key)?.let { stickers[it] }
        val st = if (styled) keyStyles.resolve(kr.key) else null
        val shaped = st?.isShaped == true
        val split = !content || sticker != null || shaped || (st != null && (st.text != null || st.hideLabel == true))
        if (!split) {
            theme.drawKey(c, kr.rect, kr.key, labelFor(kr.key), isPressed, shiftState, rowH)
            return
        }
        val native = if (st != null) nativeColors(kr) else null
        // 底框
        var bodyColor: Int? = null
        if (shaped && st != null && st.bgImage?.ownsShape == true) {
            // 照圖片的形狀：只畫圖片（底色、外框都交給圖片自己）
            drawKeyImage(c, kr.rect, st, st.bgImage, isPressed)
        } else if (shaped && st != null && native != null) {
            bodyColor = st.bg ?: native.bg.takeIf { native.hasBody }
            if (bodyColor != null) theme.drawStyledBody(c, kr.rect, st.copy(bg = bodyColor), isPressed)
            st.bgImage?.let { drawKeyImage(c, kr.rect, st, it, isPressed) }
            val border = st.border ?: native.border.takeIf { native.hasBorder }
            if (border != null) theme.drawStyledBorder(c, kr.rect, st.copy(border = border))
        } else {
            theme.drawKey(c, kr.rect, kr.key.copy(label = "", icon = null, sub = null), "", isPressed, shiftState, rowH)
        }
        // 內容：貼圖、或用自訂（或主題原本）的顏色畫字
        when {
            !content -> Unit
            sticker != null -> Stickers.draw(c, sticker, kr.rect, isPressed, 5 * dp)
            st?.hideLabel == true -> Unit
            else -> {
                var main = st?.text ?: native?.text ?: theme.text
                if (kr.key.act == Act.CLIP && st?.text == null) {
                    // 剪貼簿條借了字鍵的底（顏色或圖片），主題原本的字色可能看不清楚：換成看得清楚的
                    val under = bodyColor?.takeIf { android.graphics.Color.alpha(it) > 0 }
                        ?: st?.bgImage?.let { f -> fillBitmap(f.img)?.let { b -> b.getPixel(b.width / 2, b.height / 2) } }
                    if (under != null && !readable(main, under)) main = CustomTheme.contrastOn(under)
                }
                val dim = if (st?.text != null || native != null) (main and 0x00FFFFFF) or (0xA8 shl 24) else theme.textDim
                // 透明的底色不能拿來挖刪除鍵的 X（不然 X 會不見）
                theme.drawKeyContent(c, kr.rect, kr.key, labelFor(kr.key), shiftState, rowH, main, dim, bodyColor?.takeIf { android.graphics.Color.alpha(it) > 0 })
            }
        }
    }

    // ---------- 按鍵上的東西一層一層畫（主題工作室調過字的位置或順序時） ----------

    /**
     * 一顆鍵完整畫出來：底框、字、圖案。
     * 沒調過字的位置／順序：照主題原本的畫法，圖案蓋在字上面。
     * 調過的話：字一個一個分開畫（可以移動、縮放、旋轉、換色），跟圖案照順序一層一層疊。
     */
    private fun drawKeyFull(c: Canvas, kr: KeyRect) {
        val st = keyStyles.resolve(kr.key)
        val sticker = StickerSlot.of(kr.key)?.let { stickers[it] }
        if (st == null || sticker != null || (st.labels.isNullOrEmpty() && st.order == null)) {
            drawOneKey(c, kr)
            drawKeyDecals(c, kr)
            return
        }
        drawOneKey(c, kr, content = false)
        val elems = labelElemsOf(kr).associateBy { "label:" + it.id }
        val keyId = KeyStyles.idOf(kr.key)
        for (id in elementOrder(kr.key)) {
            val e = elems[id]
            if (e != null) drawLabel(c, kr, e, st)
            else decals.firstOrNull { it.id == id && it.target == keyId }?.let { Decals.draw(c, it, kr.rect, kr.rect.height(), decalBitmaps[it.fileName], animOf(it)) }
        }
    }

    /** 這顆鍵上的字（藏起來的話就沒有） */
    private fun labelElemsOf(kr: KeyRect): List<KbTheme.LabelElem> {
        val st = keyStyles.resolve(kr.key)
        if (st?.hideLabel == true) return emptyList()
        theme.fonts = fontSet
        // 主題工作室裡刪掉的字：不畫、也選不到
        return theme.labelElems(kr.key, labelFor(kr.key), kr.rect, rowH).filter { st?.labels?.get(it.id)?.hidden != true }
    }

    /** 這顆鍵上被刪掉的字（"main"、"sub"…），主題工作室的面板顯示「恢復」用 */
    fun hiddenLabels(key: Key): List<String> =
        keyStyles.resolve(key)?.labels?.filterValues { it.hidden }?.keys?.toList() ?: emptyList()

    /** 這個字現在的顏色：自己設的 > 按鍵的文字顏色 > 主題原本的顏色 */
    private fun labelColor(kr: KeyRect, id: String, st: KeyStyle?): Int {
        st?.labels?.get(id)?.color?.let { return it }
        val native = nativeColors(kr)
        val main = st?.text ?: native?.text ?: theme.text
        return when {
            id != "sub" -> main
            st?.text != null -> (main and 0x00FFFFFF) or (0xA8 shl 24)
            else -> native?.sub ?: theme.textDim
        }
    }

    private fun drawLabel(c: Canvas, kr: KeyRect, e: KbTheme.LabelElem, st: KeyStyle?) {
        val ls = st?.labels?.get(e.id) ?: LabelStyle()
        val shaped = st?.isShaped == true && st.bgImage?.ownsShape != true
        val body = (st?.bg ?: nativeColors(kr)?.takeIf { shaped && it.hasBody }?.bg)?.takeIf { android.graphics.Color.alpha(it) > 0 }
        c.save()
        c.translate(e.cx + ls.dx * kr.rect.width(), e.cy + ls.dy * kr.rect.height())
        c.rotate(ls.rotation)
        c.scale(ls.scale, ls.scale)
        // 透明度：乘在顏色本身的透明度上
        val col = labelColor(kr, e.id, st).let { c0 ->
            if (ls.alpha >= 1f) c0 else (c0 and 0x00FFFFFF) or ((android.graphics.Color.alpha(c0) * ls.alpha).toInt().coerceIn(0, 255) shl 24)
        }
        theme.drawLabelElem(c, e, col, shiftState, body)
        c.restore()
    }

    /** 這顆鍵上的東西由下到上的順序：預設是字在下面、圖案照加的順序疊上去 */
    fun elementOrder(key: Key): List<String> {
        val kr = keyRects.firstOrNull { it.key == key }
        val labels = kr?.let { labelElemsOf(it).map { e -> "label:" + e.id } } ?: emptyList()
        val keyId = KeyStyles.idOf(key)
        val defaults = labels + decals.filter { it.target == keyId }.map { it.id }
        val saved = keyStyles.resolve(key)?.order ?: return defaults
        // 存過的順序裡已經沒有的（刪掉的圖案）跳過；新加的放最上面
        return saved.filter { it in defaults } + defaults.filter { it !in saved }
    }

    /** 主題工作室：按鍵上可以編輯的東西（由下到上）。字也包成一個 Decal（id 是 label:main），方便共用編輯框 */
    class KeyElement(val decal: Decal, val aspect: Float?, val isLabel: Boolean, val bmp: android.graphics.Bitmap?)

    fun keyElements(key: Key): List<KeyElement> {
        val kr = keyRects.firstOrNull { it.key == key } ?: return emptyList()
        val st = keyStyles.resolve(key)
        val r = kr.rect
        val keyId = KeyStyles.idOf(key)
        val labels = labelElemsOf(kr).associateBy { "label:" + it.id }
        return elementOrder(key).mapNotNull { id ->
            val e = labels[id]
            if (e != null) {
                val ls = st?.labels?.get(e.id) ?: LabelStyle()
                val (w, h) = theme.elemSize(e)
                KeyElement(Decal(
                    id = id, target = keyId, img = id,
                    x = (e.cx + ls.dx * r.width() - r.left) / r.width(),
                    y = (e.cy + ls.dy * r.height() - r.top) / r.height(),
                    size = w * ls.scale / r.height(),
                    rotation = ls.rotation,
                ), h / w, true, null)
            } else {
                decals.firstOrNull { it.id == id && it.target == keyId }?.let { KeyElement(it, null, false, decalBitmaps[it.fileName]) }
            }
        }
    }

    /** 編輯框拖完的「字」換回這個字的位置、大小、角度（顏色照舊） */
    fun labelStyleFrom(key: Key, d: Decal): LabelStyle? {
        val kr = keyRects.firstOrNull { it.key == key } ?: return null
        val name = d.id.removePrefix("label:")
        val e = labelElemsOf(kr).firstOrNull { it.id == name } ?: return null
        val r = kr.rect
        val (w, _) = theme.elemSize(e)
        val old = keyStyles.resolve(key)?.labels?.get(name)
        return LabelStyle(
            dx = d.x - (e.cx - r.left) / r.width(),
            dy = d.y - (e.cy - r.top) / r.height(),
            scale = (d.size * r.height() / w).coerceIn(0.1f, 6f),
            rotation = d.rotation,
            color = old?.color,
        )
    }

    /** 主題工作室：這個字現在畫面上的顏色 */
    fun labelColorOf(key: Key, id: String): Int? {
        val kr = keyRects.firstOrNull { it.key == key } ?: return null
        return labelColor(kr, id, keyStyles.resolve(key))
    }

    /** 每顆鍵在主題原本樣子下的顏色（量一次就記住，換主題或大小時清掉） */
    private val nativeCache = HashMap<String, KeyColors>()

    private fun nativeColors(kr: KeyRect): KeyColors? {
        val k = KeyStyles.idOf(kr.key) + "@" + kr.rect.width().toInt() + "x" + kr.rect.height().toInt()
        nativeCache[k]?.let { return it }
        return sample(kr, styled = false)?.also { nativeCache[k] = it }
    }

    // ---------- 底圖（按鍵底圖、鍵盤背景圖） ----------

    private val pressShade = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E000000 }
    /** 只蓋在已經畫了東西的地方（照圖片形狀的鍵：透明的地方不會變暗） */
    private val pressShadeAtop = android.graphics.Paint().apply {
        color = 0x2E000000
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
    }

    /** 底圖用的圖（GIF 的話是第一格；會動的另外用 fillAnim） */
    private fun fillBitmap(name: String) = effectBitmap(name)
    private fun fillAnim(f: ImageFill) = if (f.isGif) effectAnim(f.img) else null

    /** 按鍵底圖：照這顆鍵的圓角裁掉；按下去暗一點 */
    private fun drawKeyImage(c: Canvas, r: RectF, st: KeyStyle, fill: ImageFill, pressed: Boolean) {
        val box = theme.styledBox(r)
        if (fill.nine) {
            // 九宮格：四個角不變形；按下去只有圖片本身變暗
            val bmp = fillBitmap(fill.img) ?: return
            c.saveLayerAlpha(box, fill.alpha255)
            NineFrame.draw(c, bmp, fill.sx, fill.sy, box, fill.trim)
            if (pressed) c.drawRect(box, pressShadeAtop)
            c.restore()
            return
        }
        if (fill.shape) {
            // 透明度用這一層一起套（裡面的圖就當不透明的畫，才不會淡兩次）
            c.saveLayerAlpha(box, fill.alpha255)
            ImageFill.draw(c, fill.copy(alpha = 1f), box, null, fillBitmap(fill.img), fillAnim(fill))
            if (pressed) c.drawRect(box, pressShadeAtop)
            c.restore()
            return
        }
        val rad = theme.styledRadius(box, st)
        val clip = android.graphics.Path().apply { addRoundRect(box, rad, rad, android.graphics.Path.Direction.CW) }
        ImageFill.draw(c, fill, box, clip, fillBitmap(fill.img), fillAnim(fill))
        if (pressed) c.drawRoundRect(box, rad, rad, pressShade)
    }

    /** 鍵盤背景圖鋪的範圍：上方列（選字列）下面的整塊 */
    fun keyboardImageFrame(): RectF = RectF(0f, if (topBarClear) 0f else topH, width.toFloat(), kbH)

    // ---------- 上方列（中／En、選字列、符號分類列）的顏色 ----------

    private val topStyle get() = keyStyles.topBarFor(pageScope)
    /** 上方列設成透明：背景圖片往上鋪滿整個鍵盤 */
    private val topBarClear get() = topStyle?.bg?.let { android.graphics.Color.alpha(it) == 0 } == true
    private val topText get() = topStyle?.text ?: theme.text
    private val topDim get() = topStyle?.textDim ?: theme.textDim
    private val topIcon get() = topStyle?.icon ?: theme.textDim

    /** 上方列現在實際的顏色（主題工作室顯示用；主題原本是透明的就用背景色） */
    fun topBarColors(): TopBarStyle {
        fun solid(c: Int) = if (android.graphics.Color.alpha(c) == 0) theme.bg else c
        return TopBarStyle(
            bg = topStyle?.bg ?: solid(theme.topBar),
            tab = topStyle?.tab ?: solid(theme.tabSelected),
            tabBorder = topStyle?.tabBorder ?: theme.text,
            text = topText, textDim = topDim, icon = topIcon,
        )
    }

    /**
     * 注音小框框：主題工作室改了上方列的話跟著上方列。
     * 它浮在聊天 App 上面，不能透明：上方列透明就用「選中那塊」的顏色，再沒有才用主題的。
     * 字看不清楚（例如白字配白底）就自動換成黑或白。
     */
    private fun drawReadingBox(c: Canvas) {
        val ts = topStyle
        if (ts == null) { theme.drawReading(c, reading, floatH); return }
        fun solid(x: Int?) = x?.takeIf { android.graphics.Color.alpha(it) > 0 }
        val bg = (solid(ts.bg) ?: solid(ts.tab) ?: theme.readingBg) or (0xFF shl 24)
        val text = ts.text?.takeIf { readable(it, bg) } ?: theme.readingText.takeIf { ts.text == null && readable(it, bg) } ?: CustomTheme.contrastOn(bg)
        val border = ts.tabBorder ?: ts.textDim ?: theme.readingBorder
        theme.drawReading(c, reading, floatH, bg, text, border)
    }

    /** 字和底色的亮度差夠不夠（簡單算法：差 70 以上就看得清楚；白字配粉紅還算看得到） */
    private fun readable(fg: Int, bg: Int): Boolean {
        fun lum(c: Int) = 0.299 * android.graphics.Color.red(c) + 0.587 * android.graphics.Color.green(c) + 0.114 * android.graphics.Color.blue(c)
        return kotlin.math.abs(lum(fg) - lum(bg)) >= 70
    }

    /** 自訂了上方列的底色：蓋掉主題原本的上方列 */
    private fun drawTopBarBg(c: Canvas, w: Float) {
        val col = topStyle?.bg ?: return
        c.save()
        c.clipRect(0f, 0f, w, topH)
        // 先畫成跟下面一樣的主題背景（往上挪一列，主題自己的上方列就被擠到看不到的地方）
        c.translate(0f, -topH)
        theme.resetPaints()
        theme.drawBackground(c, w, kbH + topH, topH)
        c.restore()
        if (android.graphics.Color.alpha(col) > 0) {
            val p = android.graphics.Paint().apply { color = col }
            c.drawRect(0f, 0f, w, topH, p)
        }
    }

    /** 選中的那一塊（「中」、第一個候選字、目前的分類） */
    /**
     * @param padX、padY 往內縮多少。候選字的格子是照字寬算的、字也比較大，
     *   縮少一點，外框（尤其縫線）才不會壓到字。
     */
    private fun drawTab(c: Canvas, r: RectF, padX: Float = 8 * dp, padY: Float = 7 * dp) {
        val ts = topStyle
        val border = ts?.tabBorder
        val col = ts?.tab
        // 沒有自訂：照主題原本的
        if (col == null && border == null) { theme.drawTabHighlight(c, r); return }
        val box = RectF(r.left + padX, r.top + padY, r.right - padX, r.bottom - padY)
        val rad = box.height() / 2
        // 只自訂了外框：底色用主題原本的那個顏色（透明的主題就不填）
        val fillCol = col ?: theme.tabSelected
        if (android.graphics.Color.alpha(fillCol) > 0) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = fillCol }
            c.drawRoundRect(box, rad, rad, p)
        }
        if (border != null) theme.drawBorderBox(c, box, rad, border, ts.tabBorderWidth, ts.tabBorderStyle, ts.tabBorderInset)
    }

    /** 這顆鍵的底圖鋪的範圍、圓角半徑（裁切畫面用） */
    fun keyImageFrame(key: Key, st: KeyStyle?): Pair<RectF, Float>? {
        val r = keyRects.firstOrNull { it.key == key }?.rect ?: return null
        val box = theme.styledBox(r)
        return box to theme.styledRadius(box, st ?: KeyStyle())
    }

    // ---------- 我的貼圖：長按放大 ----------

    /** 長按中的貼圖（放大顯示） */
    var stickerZoom: String? = null
        private set
    private var stickerZoomBmp: android.graphics.Bitmap? = null
    private var stickerZoomAnim: android.graphics.drawable.Drawable? = null

    private fun showStickerZoom(name: String) {
        stickerZoom = name
        stickerZoomBmp = MyStickers.fullBitmap(context, name)
        stickerZoomAnim = null
        // GIF：放大的也會動
        if (name.endsWith(".gif")) try {
            MyStickers.file(context, name)?.let { f ->
                val d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(f))
                if (d is android.graphics.drawable.AnimatedImageDrawable) {
                    d.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                    d.callback = animCallback
                    d.start()
                    stickerZoomAnim = d
                }
            }
        } catch (_: Exception) {}
    }

    private fun hideStickerZoom() {
        (stickerZoomAnim as? android.graphics.drawable.AnimatedImageDrawable)?.let { it.stop(); it.callback = null }
        stickerZoomAnim = null
        stickerZoomBmp = null
        stickerZoom = null
    }

    private val zoomPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

    /** 整個鍵盤暗下來，中間一張白卡片放大圖；下面一行小字 */
    private fun drawStickerZoom(c: Canvas, w: Float) {
        val iw = stickerZoomAnim?.intrinsicWidth?.takeIf { it > 0 } ?: stickerZoomBmp?.width ?: return
        val ih = stickerZoomAnim?.intrinsicHeight?.takeIf { it > 0 } ?: stickerZoomBmp?.height ?: return
        zoomPaint.color = 0x99000000.toInt()
        c.drawRect(0f, 0f, w, kbH, zoomPaint)
        val pad = 14 * dp
        val capH = 26 * dp
        val maxW = w * 0.78f
        val maxH = kbH * 0.9f - capH
        val s = min((maxW - pad * 2) / iw, (maxH - pad * 2) / ih)
        val cw = iw * s + pad * 2
        val ch = ih * s + pad * 2 + capH
        val card = RectF((w - cw) / 2, (kbH - ch) / 2, (w + cw) / 2, (kbH + ch) / 2)
        zoomPaint.color = 0x40000000
        c.drawRoundRect(RectF(card.left, card.top + 4 * dp, card.right, card.bottom + 4 * dp), 20 * dp, 20 * dp, zoomPaint)
        zoomPaint.color = android.graphics.Color.WHITE
        c.drawRoundRect(card, 20 * dp, 20 * dp, zoomPaint)
        val img = RectF(card.left + pad, card.top + pad, card.right - pad, card.top + pad + ih * s)
        val anim = stickerZoomAnim
        if (anim != null) Decals.drawAnim(c, anim, img, 255)
        else stickerZoomBmp?.let { c.drawBitmap(it, null, img, zoomPaint) }
        theme.drawText(c, "放開手指就關掉（不會送出）", card.centerX(), card.bottom - capH / 2 - 2 * dp, 12 * dp, 0xFF8A8590.toInt())
    }

    // ---------- 我的貼圖（表情鍵盤的 🖼️） ----------

    private val stickerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

    private fun drawMySticker(c: Canvas, kr: KeyRect) {
        val b = MyStickers.bitmap(context, kr.key.text) ?: return
        val r = kr.rect
        val pad = 6 * dp
        val s = minOf((r.width() - pad * 2) / b.width, (r.height() - pad * 2) / b.height)
        val w = b.width * s * (if (kr === pressed) 0.9f else 1f)
        val h = b.height * s * (if (kr === pressed) 0.9f else 1f)
        c.drawBitmap(b, null, RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2), stickerPaint)
    }

    // ---------- 貼上的圖案 ----------

    /**
     * 背景圖案的參考範圍：整個鍵盤，但「不算快捷符號列」那一條。
     * 快捷列開著時，按鍵整個往下移一條，背景圖案也跟著往下移，才會一直貼在同一顆鍵旁邊，
     * 大小也不會因為多了一條而變。
     */
    private val bgRect get() = RectF(0f, quickH, width.toFloat(), kbH)

    /**
     * 把圖案放到現在的鍵盤上：
     * - 背景圖案的上下：照「分區」（最上面那一條高度固定、下面的按鍵區跟著按鍵高度變）
     * - 左右（背景和按鍵上的圖案都是）：照「靠邊」（靠左的貼著左邊、中間的在正中間、靠右的貼著右邊）
     * 沒記分區、靠邊的（舊的記法）照原本的 y、size、x。鍵盤還沒量好、那顆鍵不在這一頁時先不動。
     */
    fun placeDecal(d0: Decal): Decal {
        var d = d0
        if (d.onBackground && d.band != 0) {
            val h = bgRect.height()
            val t = topH
            val k = h - t
            if (h > 0f && k > 0f) {
                d = if (d.band == 1 || d.band == 3) {
                    // 最上面那一條：從鍵盤最上緣算（快捷列開關都不會動）。y 是相對 bgRect（快捷列下面）的，所以扣掉 bgRect.top
                    val byAbs = if (d.band == 3) d.by else legacyTopBy(d.by)
                    d.copy(y = (byAbs * t - bgRect.top) / h, size = (d.bs * t / h).coerceAtLeast(0.01f))
                } else d.copy(y = (t + d.by * k) / h, size = (d.bs * k / h).coerceAtLeast(0.01f))
            }
        }
        if (d.hx != 0) {
            val r = targetOf(d)?.first ?: return d
            if (r.width() <= 0f) return d
            val w = d.size * r.height() // 圖案的寬（px）
            val cx = when (d.hx) {
                1 -> r.left + d.ax * w
                3 -> r.right - d.ax * w
                else -> r.centerX() + d.ax * w
            }
            d = d.copy(x = (cx - r.left) / r.width())
        }
        return d
    }

    /** 反過來：照現在鍵盤上的位置，記下分區（背景的上下）和靠邊（左右），存檔前用 */
    fun encodeDecal(d0: Decal): Decal {
        val r = targetOf(d0)?.first ?: return d0
        if (r.width() <= 0f || r.height() <= 0f) return d0
        var d = d0
        if (d.onBackground) {
            val h = r.height()
            val t = topH
            val k = h - t
            if (k > 0f) {
                val cy = d.y * h
                // 圖案中心在鍵盤最上緣算起的位置：在最上面那一條（中／En、選字）裡＝跟著那一條（band 3）；
                // 不然跟著按鍵區（band 2；在快捷列那一排的也算，快捷列關掉時跟按鍵一起往上）
                val cyAbs = cy + r.top
                d = if (cyAbs < t) d.copy(band = 3, by = cyAbs / t, bs = d.size * h / t)
                else d.copy(band = 2, by = (cy - t) / k, bs = d.size * h / k)
            }
        }
        val w = d.size * r.height()
        if (w > 0f) {
            val cx = r.left + d.x * r.width()
            d = when {
                d.x < 1f / 3 -> d.copy(hx = 1, ax = (cx - r.left) / w)
                d.x > 2f / 3 -> d.copy(hx = 3, ax = (r.right - cx) / w)
                else -> d.copy(hx = 2, ax = (cx - r.centerX()) / w)
            }
        }
        return d
    }

    /**
     * 舊的「最上面那一條」（band 1）是從快捷列下面算的，快捷列一開關圖案就上下跑。
     * 算出來是負的＝那時快捷列開著（圖案在快捷列上面那一條），補回快捷列的高度（跟上方列一樣高 → +1）；
     * 不是負的＝那時快捷列關著，本來就是從最上緣算的。
     */
    private fun legacyTopBy(by: Float) = if (by < 0f) by + QUICK_DP / TOP_DP else by

    /** 舊名字（只算背景的分區＋靠邊） */
    fun bandDecal(d: Decal): Decal = if (d.onBackground) encodeDecal(d) else d

    private fun drawBgDecals(c: Canvas, front: Boolean) {
        if (decals.isEmpty()) return
        val r = bgRect
        for (d in decals) if (onMyBg(d) && d.front == front) Decals.draw(c, d, r, r.height(), decalBitmaps[d.fileName], animOf(d))
    }

    private fun drawKeyDecals(c: Canvas, kr: KeyRect) {
        if (decals.isEmpty()) return
        val id = KeyStyles.idOf(kr.key)
        for (d in decals) if (d.target == id) Decals.draw(c, d, kr.rect, kr.rect.height(), decalBitmaps[d.fileName], animOf(d))
    }

    // ---------- 按鍵動畫 ----------

    /** 全部按鍵共用的動畫（設定頁選的）；每次按都讀一次太慢，換設定時才重讀 */
    private var globalEffect = KeyEffect(prefs.pressEffect, prefs.customEffect, prefs.pressEffectColor)

    /** 這個動畫沒自己選顏色時的顏色（主題工作室顯示用） */
    fun effectDefaultColor(kind: String): Int {
        val bg = theme.bg
        val dark = (android.graphics.Color.red(bg) * 299 + android.graphics.Color.green(bg) * 587 + android.graphics.Color.blue(bg) * 114) / 1000 < 128
        return PressEffects.defaultColor(kind, theme.popupSelBg, dark)
    }

    /** 這顆鍵要用的動畫：主題工作室有單獨設定就用它，不然用全部按鍵共用的 */
    fun effectFor(key: Key): KeyEffect = keyStyles.resolve(key)?.effect ?: globalEffect

    private fun playEffect(kr: KeyRect) {
        val e = effectFor(kr.key)
        if (e.kind == "custom") {
            val c = e.custom ?: CustomEffect()
            val bmp = if (c.isImage) effectBitmap(c.fileName) else null
            val anim = if (c.isImage && c.fileName.endsWith(".gif")) effectAnim(c.fileName) else null
            effects.addCustom(c, kr.rect, bmp, anim)
        } else {
            val bg = theme.bg
            val dark = (android.graphics.Color.red(bg) * 299 + android.graphics.Color.green(bg) * 587 + android.graphics.Color.blue(bg) * 114) / 1000 < 128
            effects.add(e.kind, kr.rect, theme.popupSelBg, dark, e.color)
        }
        invalidate()
    }

    /**
     * 會動的圖（GIF）：貼上的圖案和按鍵動畫共用，同一個檔案只讀一次、一直播。
     * 換設定時停掉重讀。
     */
    private val effectAnims = HashMap<String, android.graphics.drawable.Drawable?>()

    /** 放大編輯畫面（主題工作室）也要跟著換格 */
    var onAnimFrame: (() -> Unit)? = null

    /** GIF 換下一格時通知鍵盤重畫（Drawable 只記弱參照，所以要自己留一份） */
    private val animCallback = object : android.graphics.drawable.Drawable.Callback {
        override fun invalidateDrawable(who: android.graphics.drawable.Drawable) {
            postInvalidateOnAnimation()
            onAnimFrame?.invoke()
        }
        override fun scheduleDrawable(who: android.graphics.drawable.Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, who, `when`)
        }
        override fun unscheduleDrawable(who: android.graphics.drawable.Drawable, what: Runnable) {
            handler.removeCallbacks(what, who)
        }
    }

    private fun effectAnim(name: String): android.graphics.drawable.Drawable? = effectAnims.getOrPut(name) {
        if (!name.endsWith(".gif")) return@getOrPut null
        try {
            val f = java.io.File(Decals.dir(context), name).takeIf { Decals.safeName(name) && it.exists() } ?: return@getOrPut null
            val d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(f))
            if (d is android.graphics.drawable.AnimatedImageDrawable) {
                d.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                d.callback = animCallback
                d.start()
                d
            } else null // 只有一格的 GIF：當一般圖片畫就好
        } catch (_: Exception) {
            null
        }
    }

    /** 這個圖案會動的話，回傳會動的圖 */
    private fun animOf(d: Decal): android.graphics.drawable.Drawable? = if (d.isShape) null else effectAnim(d.fileName)

    private fun stopEffectAnims() {
        for (d in effectAnims.values) (d as? android.graphics.drawable.AnimatedImageDrawable)?.let { it.stop(); it.callback = null }
        effectAnims.clear()
    }

    private val effectBitmaps = HashMap<String, android.graphics.Bitmap?>()
    private fun effectBitmap(name: String): android.graphics.Bitmap? = effectBitmaps.getOrPut(name) {
        decalBitmaps[name] ?: java.io.File(Decals.dir(context), name).takeIf { Decals.safeName(name) && it.exists() }
            ?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
    }

    /** 主題工作室：「試按看看」，在這顆鍵上放一次動畫（effect = 指定要播哪一種，null = 照目前設定） */
    fun previewEffect(key: Key, effect: KeyEffect? = null) {
        val kr = keyRects.firstOrNull { it.key == key } ?: return
        if (effect == null) { playEffect(kr); return }
        val saved = globalEffect
        val savedStyles = keyStyles
        keyStyles = KeyStyles.EMPTY
        globalEffect = effect
        playEffect(kr)
        globalEffect = saved
        keyStyles = savedStyles
    }

    /** 放大畫面用：現在還有動畫在跑嗎 */
    fun effectsActive(): Boolean = effects.active(android.os.SystemClock.uptimeMillis())

    // 主題工作室的放大編輯（KeyZoomView）借這幾個來畫

    fun keyRectOf(key: Key): RectF? = keyRects.firstOrNull { it.key == key }?.rect
    val keyboardBg: Int get() = theme.bg
    fun decalBitmap(d: Decal): android.graphics.Bitmap? = decalBitmaps[d.fileName]

    /** 把某顆鍵（含圖案）放大畫到 dst 這個框裡，跟鍵盤上看到的一模一樣 */
    fun drawKeyZoomed(c: Canvas, key: Key, dst: RectF) {
        val kr = keyRects.firstOrNull { it.key == key } ?: return
        theme.fonts = fontSet
        val s = dst.width() / kr.rect.width()
        c.save()
        c.translate(dst.left, dst.top)
        c.scale(s, s)
        c.translate(-kr.rect.left, -kr.rect.top)
        drawKeyFull(c, kr)
        // 動畫也一起放大畫（在這顆鍵上試按的才會出現在這裡）
        effects.draw(c, android.os.SystemClock.uptimeMillis())
        c.restore()
    }

    /** 主題工作室改了按鍵樣式（拖字、拖滑桿）：直接換掉，不用整個重讀（快很多） */
    fun setKeyStylesLive(s: KeyStyles) {
        keyStyles = s
        styledCache.clear()
        invalidate()
    }

    /** 主題工作室改了圖案（拖曳、滑桿）：直接換掉，不用重讀設定 */
    fun setDecalsLive(list: List<Decal>) {
        decals = list.map(::placeDecal)
        decalBitmaps = Decals.bitmaps(context, list, decalBitmaps)
        invalidate()
    }

    /** 圖案貼在哪（畫面上的範圍、參考高度）；那顆鍵不在目前的鍵盤上就是 null */
    private fun targetOf(d: Decal): Pair<RectF, Float>? =
        if (d.onBackground) bgRect.let { it to it.height() }
        else keyRects.firstOrNull { it.key.act != Act.GAP && KeyStyles.idOf(it.key) == d.target }?.let { it.rect to it.rect.height() }

    /** 點到的圖案（最上面的那個優先） */
    private fun decalAt(x: Float, y: Float): Decal? {
        val slop = 10 * dp
        fun hit(d: Decal) = targetOf(d)?.let { (r, ref) -> Decals.hit(d, r, ref, decalBitmaps[d.fileName], x, y, slop) } == true
        decals.lastOrNull { onMyBg(it) && it.front && hit(it) }?.let { return it }
        decals.lastOrNull { !it.onBackground && hit(it) }?.let { return it }
        // 按鍵後面的背景圖案：點在按鍵上就算點按鍵
        val onKey = keyRects.any { it.key.act != Act.GAP && it.rect.contains(x, y) }
        return if (onKey) null else decals.lastOrNull { onMyBg(it) && !it.front && hit(it) }
    }

    /** 主題工作室選到的圖案（畫虛線框，手勢會移動它） */
    var decalEditId: String? = null
        set(v) { field = v; invalidate() }

    /** 手勢改了圖案：done = 手指放開了 */
    var onDecalChanged: ((Decal, Boolean) -> Unit)? = null

    /** 在鍵盤上點到某個圖案 */
    var onDecalPicked: ((Decal) -> Unit)? = null
    /** 主題工作室：點了上方列 */
    var onTopBarPicked: (() -> Unit)? = null
    /** 主題工作室：點了快捷符號列 */
    var onQuickBarPicked: (() -> Unit)? = null
    /** 主題工作室：快捷符號列畫上「正在編輯」的虛線框 */
    var editQuickBar = false
        set(v) { field = v; invalidate() }
    /** 主題工作室：上方列畫上「正在編輯」的虛線框 */
    var editTopBar = false
        set(v) { field = v; invalidate() }

    /** 圖案按了小工具（copy / flip / delete） */
    var onDecalAction: ((String, Decal) -> Unit)? = null

    /** Canva 那種編輯框（背景圖案在整個鍵盤上編輯時用） */
    private val decalHandles = DecalHandles(dp)

    private fun geoOf(d: Decal): DecalHandles.Geo? =
        targetOf(d)?.let { (r, ref) -> DecalHandles.Geo(r, ref, decalBitmaps[d.fileName]) }

    /** 主題工作室「排列」勾選到的背景圖案（畫粉紅虛線框） */
    var decalMarkIds: Set<String> = emptySet()
        set(v) { field = v; invalidate() }

    /**
     * 主題工作室「對齊」：把這些元素對齊，回傳改好位置的（按鍵上的字也是包成 Decal 的，id 是 label:…）。
     * @param key 按鍵上的字和圖案；null ＝ 這一頁背景上的圖案
     */
    fun alignElements(key: Key?, ids: Collection<String>, op: ElementAlign.Op): List<Decal> {
        class E(val d: Decal, val target: RectF, val w: Float, val h: Float)
        val es: List<E>
        val frame: RectF
        if (key != null) {
            val r = keyRects.firstOrNull { it.key == key }?.rect ?: return emptyList()
            es = keyElements(key).filter { it.decal.id in ids }.map { el ->
                val (w, h) = Decals.sizeOf(el.decal, r.height(), el.bmp, el.aspect)
                E(el.decal, r, w, h)
            }
            frame = theme.styledBox(r) // 對齊按鍵看得到的那一塊（鍵和鍵之間的縫不算）
        } else {
            val r = bgRect
            es = decals.filter { onMyBg(it) && it.id in ids }.map { d ->
                val (w, h) = Decals.sizeOf(d, r.height(), decalBitmaps[d.fileName])
                E(d, r, w, h)
            }
            frame = r
        }
        val items = es.map { e ->
            val (cx, cy) = Decals.centerOf(e.d, e.target)
            val (hw, hh) = ElementAlign.halfExtents(e.w, e.h, e.d.rotation)
            ElementAlign.Item(e.d.id, cx, cy, hw, hh)
        }
        val moved = ElementAlign.apply(items, op, frame)
        return es.mapNotNull { e ->
            moved[e.d.id]?.let { (cx, cy) -> e.d.copy(x = (cx - e.target.left) / e.target.width(), y = (cy - e.target.top) / e.target.height(), band = 0, hx = 0) }
        }
    }

    private fun drawDecalSelection(c: Canvas) {
        for (id in decalMarkIds) decals.firstOrNull { it.id == id && onMyBg(it) }?.let { d -> geoOf(d)?.let { decalHandles.drawMark(c, d, it) } }
        val d = currentDecal() ?: return
        val g = geoOf(d) ?: return
        decalHandles.draw(c, d, g, width.toFloat(), kbH)
    }

    private fun currentDecal(): Decal? = decalEditId?.let { id -> decals.firstOrNull { it.id == id } }

    private fun replaceDecal(d0: Decal, done: Boolean) {
        // 手指拖過了：位置以畫面上的為準，分區、靠邊等存檔時再重算
        val d = d0.copy(band = 0, hx = 0)
        decals = decals.map { if (it.id == d.id) d else it }
        invalidate()
        onDecalChanged?.invoke(d, done)
    }

    /** 編輯模式：點按鍵／圖案來選；選到的圖案用 Canva 那種方式拖、縮放、旋轉 */
    private fun handleEditTouch(e: MotionEvent, pick: (Key) -> Unit) {
        decalHandles.onEmptyTap = { x, y ->
            if (y in 0f..topH && onTopBarPicked != null) onTopBarPicked?.invoke()
            else if (quickH > 0f && y in topH..topH + quickH && onQuickBarPicked != null) onQuickBarPicked?.invoke()
            else keyAt(x, y)?.let { pick(it.key) }
        }
        decalHandles.onPick = { d -> if (d != null) onDecalPicked?.invoke(d) }
        decalHandles.onChange = { d, done -> replaceDecal(d, done) }
        decalHandles.onAction = { a, d -> onDecalAction?.invoke(a, d) }
        decalHandles.onTouch(
            e,
            selected = currentDecal(),
            geo = { geoOf(it) },
            hitAt = { x, y -> decalAt(x, y) },
            current = { id -> decals.firstOrNull { it.id == id } },
        )
        invalidate()
    }

    // ---------- 主題工作室：點按鍵來編輯 ----------

    /** 這顆鍵「現在看起來」的顏色（主題工作室打開選色器時當起點） */
    class KeyColors(val bg: Int, val text: Int, val border: Int, val hasBody: Boolean, val hasBorder: Boolean, val sub: Int = text)

    /**
     * 直接把這顆鍵畫在一張小圖上量顏色，所以不管哪個主題（羊皮紙、肉球…）量到的都是畫面上真正的顏色：
     * - 底色：按鍵中間最常出現的顏色（先畫一次不含字的）
     * - 文字：有字和沒字兩張圖差最多的那一點
     * - 外框：按鍵邊緣的顏色
     */
    fun sampleColors(key: Key): KeyColors? = keyRects.firstOrNull { it.key == key }?.let { sample(it, styled = true) }

    private fun sample(kr: KeyRect, styled: Boolean): KeyColors? {
        val r = kr.rect
        val w = r.width().toInt()
        val h = r.height().toInt()
        if (w <= 0 || h <= 0) return null
        theme.fonts = fontSet
        val base = theme.bg or (0xFF shl 24)
        fun render(content: Boolean): android.graphics.Bitmap {
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(base)
            c.translate(-r.left, -r.top)
            drawOneKey(c, kr, content, styled)
            return bmp
        }
        // 一次把整張圖的顏色讀進陣列（一點一點 getPixel 很慢：每次換頁四十幾顆鍵，會卡住半秒）
        fun pixels(content: Boolean): IntArray {
            val bmp = render(content)
            val arr = IntArray(w * h)
            bmp.getPixels(arr, 0, w, 0, 0, w, h)
            bmp.recycle()
            return arr
        }
        val body = pixels(false)
        val full = pixels(true)
        fun opaque(c: Int) = c or (0xFF shl 24)

        // 底色：中間區域分成小格子統計，取最多的那一組平均
        val count = HashMap<Int, IntArray>() // 格子 → [數量, r 總和, g 總和, b 總和]
        for (y in (h * 0.2f).toInt() until (h * 0.8f).toInt()) for (x in (w * 0.2f).toInt() until (w * 0.8f).toInt()) {
            val p = body[y * w + x]
            val k = (android.graphics.Color.red(p) shr 4 shl 8) or (android.graphics.Color.green(p) shr 4 shl 4) or (android.graphics.Color.blue(p) shr 4)
            val a = count.getOrPut(k) { IntArray(4) }
            a[0]++; a[1] += android.graphics.Color.red(p); a[2] += android.graphics.Color.green(p); a[3] += android.graphics.Color.blue(p)
        }
        val top = count.values.maxByOrNull { it[0] }
        val bg = if (top == null) base else android.graphics.Color.rgb(top[1] / top[0], top[2] / top[0], top[3] / top[0])

        // 文字：差最多的那一點（字的正中間）
        var best = -1
        var text = theme.text
        for (y in 0 until h) for (x in 0 until w) {
            val a = full[y * w + x]
            val b = body[y * w + x]
            val d = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
            if (d > best) { best = d; text = a }
        }
        if (best < 30) text = theme.text // 這顆沒有字（或字被藏起來）

        // 小字（v、1…）的顏色：在小字附近找差最多的那一點
        var sub = text
        theme.labelElems(kr.key, labelFor(kr.key), r, rowH).firstOrNull { it.id == "sub" }?.let { e ->
            val half = e.size * 0.6f
            var subBest = -1
            val x0 = (e.cx - half - r.left).toInt().coerceIn(0, w - 1)
            val x1 = (e.cx + half - r.left).toInt().coerceIn(0, w - 1)
            val y0 = (e.cy - half - r.top).toInt().coerceIn(0, h - 1)
            val y1 = (e.cy + half - r.top).toInt().coerceIn(0, h - 1)
            for (y in y0..y1) for (x in x0..x1) {
                val a = full[y * w + x]
                val b = body[y * w + x]
                val d = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
                if (d > subBest) { subBest = d; sub = a }
            }
            if (subBest < 30) sub = text
        }

        // 外框：從按鍵左邊中間往內找，第一個跟鍵盤背景不一樣的顏色（按鍵最外圈）
        fun dist(a: Int, b: Int) = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
            kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
        var border = text // 沒有底框的主題：外框就先用字的顏色
        var edgeFound = false
        for (x in 0 until w / 2) {
            if (dist(body[(h / 2) * w + x], base) > 60) {
                edgeFound = true
                // 往內 1 點，避開邊緣半透明的那一圈
                border = body[(h / 2) * w + minOf(x + 1, w - 1)]
                break
            }
        }
        val hasBody = dist(bg, base) > 24
        val hasBorder = edgeFound && dist(border, bg) > 40
        return KeyColors(opaque(bg), opaque(text), opaque(border), hasBody, hasBorder, opaque(sub))
    }

    /** 有設定的話，鍵盤變成「編輯模式」：點按鍵不會打字，而是選到那顆鍵 */
    var editPicker: ((Key) -> Unit)? = null

    /** 編輯模式下要框起來的鍵（選到的那顆，或同一類的全部） */
    var editHighlight: ((Key) -> Boolean)? = null
        set(v) { field = v; invalidate() }

    private val editPaint by lazy {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f * dp
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(6 * dp, 4 * dp), 0f)
        }
    }

    private fun drawEditHighlight(c: Canvas) {
        if (editTopBar || (editQuickBar && quickH > 0f)) {
            val r = (if (editTopBar) RectF(0f, 0f, width.toFloat(), topH) else RectF(0f, topH, width.toFloat(), topH + quickH)).apply { inset(2f * dp, 2f * dp) }
            editPaint.color = android.graphics.Color.WHITE
            editPaint.pathEffect = null
            editPaint.strokeWidth = 4.5f * dp
            c.drawRoundRect(r, 8 * dp, 8 * dp, editPaint)
            editPaint.color = android.graphics.Color.parseColor("#FF4F8B")
            editPaint.strokeWidth = 2.5f * dp
            editPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6 * dp, 4 * dp), 0f)
            c.drawRoundRect(r, 8 * dp, 8 * dp, editPaint)
        }
        val f = editHighlight ?: return
        for (kr in keyRects) {
            if (kr.key.act == Act.GAP || !f(kr.key)) continue
            val r = RectF(kr.rect).apply { inset(1.5f * dp, 1.5f * dp) }
            editPaint.color = android.graphics.Color.WHITE
            editPaint.pathEffect = null
            editPaint.strokeWidth = 4.5f * dp
            c.drawRoundRect(r, 8 * dp, 8 * dp, editPaint)
            editPaint.color = android.graphics.Color.parseColor("#FF4F8B")
            editPaint.strokeWidth = 2.5f * dp
            editPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6 * dp, 4 * dp), 0f)
            c.drawRoundRect(r, 8 * dp, 8 * dp, editPaint)
        }
    }

    // ---------- 選字列 ----------

    private val expandW get() = 44 * dp
    private val stripTextSize get() = topH * 0.48f
    private val panelRowH get() = keyH * 0.95f

    private fun textWidth(s: String, size: Float): Float {
        theme.fonts = fontSet
        return theme.measure(s, size)
    }

    /** 每個選項在選字列上的位置（還沒加上捲動） */
    private fun stripLayout(): List<RectF> = stripCache ?: run {
        var x = 6 * dp
        candidates.map { t ->
            val iw = max(textWidth(t, stripTextSize) + 26 * dp, 44 * dp)
            RectF(x, 0f, x + iw, topH).also { x += iw }
        }.also { stripCache = it }
    }

    /** 展開面板裡每個選項的位置（還沒加上捲動） */
    /** 展開面板左邊那一欄（放「簡」切換鍵）；設定裡關掉切換鍵就沒有這欄 */
    private val panelLeft get() = if (prefs.showScToggle && isChinese) width / 7f else 0f // 英文建議字不用「簡」

    /** 「簡」切換鍵的位置：左邊那一欄的最下面 */
    private fun scToggleRect() = RectF(0f, kbH - padBottom - panelRowH, panelLeft, kbH - padBottom)

    private fun panelLayout(): List<RectF> = panelCache ?: run {
        val w = width.toFloat()
        val left = panelLeft
        val cell = (w - left) / 6f
        val size = keyH * 0.42f
        var x = left
        var y = topH + 2 * dp
        candidates.map { t ->
            var iw = max(textWidth(t, size) + 24 * dp, cell)
            iw = kotlin.math.ceil(iw / cell) * cell // 對齊格子，看起來比較整齊
            if (iw > w - left) iw = w - left
            if (x + iw > w + 1f) { x = left; y += panelRowH }
            RectF(x, y, x + iw, y + panelRowH).also { x += iw }
        }.also { panelCache = it }
    }

    private fun maxStripScroll(): Float {
        val last = stripLayout().lastOrNull() ?: return 0f
        return max(0f, last.right + 6 * dp - (width - expandW))
    }

    private fun maxPanelScroll(): Float {
        val last = panelLayout().lastOrNull() ?: return 0f
        return max(0f, last.bottom - kbH)
    }

    private fun drawCandidateStrip(c: Canvas, w: Float) {
        val items = stripLayout()
        c.save()
        c.clipRect(0f, 0f, w - expandW, topH)
        items.forEachIndexed { i, r0 ->
            val r = RectF(r0.left - candScroll, r0.top, r0.right - candScroll, r0.bottom)
            if (r.right < 0 || r.left > w) return@forEachIndexed
            if (i == 0) drawTab(c, r, padX = 2 * dp, padY = 3 * dp) // 第一個（最推薦的）有底色
            theme.drawText(c, candidates[i], r.centerX(), r.centerY(), stripTextSize, topText)
        }
        c.restore()
        // 右邊的展開／收起箭頭
        val cx = w - expandW / 2
        val cy = topH / 2
        val s = topH * 0.13f
        val d = if (expanded) -1f else 1f
        val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f * dp
            color = topIcon
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        c.drawPath(Path().apply {
            moveTo(cx - s * 1.3f, cy - s * 0.6f * d)
            lineTo(cx, cy + s * 0.6f * d)
            lineTo(cx + s * 1.3f, cy - s * 0.6f * d)
        }, stroke)
    }

    private fun drawPanel(c: Canvas, w: Float) {
        val items = panelLayout()
        // 展開的選字面板＝上方列往下延伸：底色、文字、分隔線都照上方列（底色透明就露出鍵盤背景）
        topStyle?.bg?.takeIf { android.graphics.Color.alpha(it) > 0 }?.let {
            c.drawRect(0f, topH, w, kbH, android.graphics.Paint().apply { color = it })
        }
        val line = android.graphics.Paint().apply { color = topDim; alpha = 50; strokeWidth = 1 * dp }
        drawScToggle(c, line)
        c.save()
        c.clipRect(panelLeft, topH, w, kbH)
        val size = keyH * 0.42f
        items.forEachIndexed { i, r0 ->
            val r = RectF(r0.left, r0.top - panelScroll, r0.right, r0.bottom - panelScroll)
            if (r.bottom < topH || r.top > kbH) return@forEachIndexed
            theme.drawText(c, candidates[i], r.centerX(), r.centerY(), size, topText)
            c.drawLine(r.left, r.bottom, r.right, r.bottom, line)
            if (r.right < w - 1f) c.drawLine(r.right, r.top + 8 * dp, r.right, r.bottom - 8 * dp, line)
        }
        c.restore()
    }

    /** 左下角的「簡」：開著＝亮色字＋底下一條線；關著＝淡淡的 */
    private fun drawScToggle(c: Canvas, line: android.graphics.Paint) {
        if (panelLeft <= 0f) return
        c.drawLine(panelLeft, topH + 8 * dp, panelLeft, kbH - 8 * dp, line)
        val r = scToggleRect()
        val on = prefs.simplified
        theme.drawText(c, "簡", r.centerX(), r.centerY(), keyH * 0.4f, if (on) topText else topDim)
        if (on) {
            val u = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = topStyle?.tabBorder ?: topStyle?.tab?.takeIf { android.graphics.Color.alpha(it) > 0 } ?: theme.popupSelBg
                strokeWidth = 2.5f * dp
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val y = r.centerY() + keyH * 0.32f
            c.drawLine(r.centerX() - keyH * 0.2f, y, r.centerX() + keyH * 0.2f, y, u)
        }
    }

    private val tabW get() = width * 0.2f

    private var downX = 0f
    private var downY = 0f
    /** 手指最後在哪（跳出長按選單時記下來，之後用「滑了多少」選） */
    private var lastMoveX = 0f
    /** 動作選單跳出來那時手指的位置 */
    private var popupAnchorX = 0f
    /** 動作選單跳出來之後，手指有沒有明顯移動過（還沒的話停在「取消」） */
    private var popupMoved = false
    /** 手指移動超過這麼多（dp）才算「滑開」 */
    private val POPUP_SLOP = 12f

    /** 上方列（中／En、選字列）、快捷符號列的高度（dp） */
    private val TOP_DP = 40f
    private val QUICK_DP = 40f

    // ---------- 快捷符號列 ----------
    /** 快捷符號列的內容（空的＝不顯示這一排） */
    var quickItems: List<String> = emptyList()
        set(v) {
            if (field != v) {
                field = v
                requestLayout()
                layoutKeys()
                invalidate()
            }
        }
    private val quickH get() = if (quickItems.isEmpty()) 0f else QUICK_DP * dp
    private var quickPressed = -1

    private fun quickRect(i: Int): RectF {
        val w = width.toFloat() / quickItems.size.coerceAtLeast(1)
        return RectF(w * i, topH + 3 * dp, w * (i + 1), topH + quickH - 1 * dp)
    }

    /** 顯示用：把游標位置的 | 拿掉 */
    private fun quickLabel(item: String) = item.replace("|", "")

    private fun drawQuickBar(c: Canvas) {
        if (quickItems.isEmpty()) return
        val qs = keyStyles.quick
        // 整列的底色
        qs?.strip?.takeIf { android.graphics.Color.alpha(it) > 0 }?.let {
            c.drawRect(0f, topH, width.toFloat(), topH + quickH, android.graphics.Paint().apply { color = it })
        }
        val native = if (qs != null) quickNative() else null
        quickItems.forEachIndexed { i, item ->
            val r = quickRect(i)
            val k = Key(quickLabel(item), Act.TEXT, style = Style.FUNC)
            val pressed = i == quickPressed
            val frame = qs?.frame
            val frameBmp = frame?.let { fillBitmap(it.img) }
            if (qs != null && frame != null && frameBmp != null) {
                // 按鈕圖片（九宮格）：只畫圖片，按下去只有圖片本身變暗
                val box = theme.styledBox(r)
                c.saveLayerAlpha(box, ((qs.frameAlpha ?: 1f) * 255).toInt())
                NineFrame.draw(c, frameBmp, frame.sx, frame.sy, box, frame.trim)
                if (pressed) c.drawRect(box, pressShadeAtop)
                c.restore()
                val main = qs.text ?: native?.text ?: theme.text
                theme.drawKeyContent(c, r, k, k.label, 0, quickH * 1.6f, main, main, null)
                return@forEachIndexed
            }
            if (qs == null || native == null) {
                theme.drawKey(c, r, k, k.label, pressed, 0, quickH * 1.6f)
                return@forEachIndexed
            }
            // 按鈕底：改了形狀就自己畫，不然照主題原本的功能鍵
            var body: Int? = null
            if (qs.isShaped) {
                val st = qs.asKeyStyle()
                body = qs.bg ?: native.bg.takeIf { native.hasBody }
                body?.takeIf { android.graphics.Color.alpha(it) > 0 }?.let { theme.drawStyledBody(c, r, st.copy(bg = it), pressed) }
                (qs.border ?: native.border.takeIf { native.hasBorder })?.takeIf { android.graphics.Color.alpha(it) > 0 }
                    ?.let { theme.drawStyledBorder(c, r, st.copy(border = it)) }
            } else {
                theme.drawKey(c, r, k.copy(label = ""), "", pressed, 0, quickH * 1.6f)
            }
            val main = qs.text ?: native.text
            theme.drawKeyContent(c, r, k, k.label, 0, quickH * 1.6f, main, main, body?.takeIf { android.graphics.Color.alpha(it) > 0 })
        }
    }

    /** 快捷列按鈕在主題原本的樣子（量出來的顏色） */
    private fun quickNative(): KeyColors? = if (quickItems.isEmpty()) null else nativeColors(KeyRect(Key("@quick", Act.TEXT, style = Style.FUNC), quickRect(0)))

    /** 主題原本的快捷列按鈕有沒有外框 */
    fun quickBarHasBorder(): Boolean? = quickNative()?.hasBorder

    /** 快捷列現在實際的顏色（主題工作室顯示用） */
    fun quickBarColors(): QuickBarStyle {
        val qs = keyStyles.quick
        val n = quickNative() ?: nativeColors(KeyRect(Key("@quick", Act.TEXT, style = Style.FUNC), RectF(0f, 0f, width / 8f, 40 * dp)))
        return QuickBarStyle(
            strip = qs?.strip ?: theme.bg,
            bg = qs?.bg ?: n?.bg ?: theme.bg,
            text = qs?.text ?: n?.text ?: theme.text,
            border = qs?.border ?: n?.border ?: theme.textDim,
            radius = qs?.radius,
        )
    }

    // ---------- 符號頁左右滑：整頁跟著手指走 ----------
    /** 目前這一頁往左右偏了多少（正 = 往右） */
    private var pageOffset = 0f
    private var paging = false
    private var pagingPointer = -1
    private var pageAnim: android.animation.ValueAnimator? = null

    /** 上方列要不要有剪貼簿按鈕（設定裡關掉剪貼簿就藏起來） */
    var showClipboard = true
        set(v) { if (field != v) { field = v; stripCache = null; invalidate() } }

    /** 上下滑換頁（剪貼簿）：往上滑＝下一頁；其他頁是左右滑 */
    var verticalPaging = false
        set(v) { if (field != v) { field = v; pageAnim?.cancel(); pageOffset = 0f } }

    /** 翻頁那一塊（不算最下面那排）的高度：上下滑時，新的一頁從下面推上來這麼多 */
    private val pagingGridH get() = rowH * gridRowCount + padTop

    // ---------- 剪貼簿：整份清單順順的上下捲（不分頁） ----------
    /** 連續捲動時一次看得到幾排；0 = 不捲（其他頁：一頁一頁翻） */
    var scrollRows = 0
        set(v) {
            if (field != v) {
                field = v
                gridScroll = 0f
                scroller.forceFinished(true)
                requestLayout()
                layoutKeys()
                invalidate()
            }
        }
    /** 捲下去多少（px） */
    internal var gridScroll = 0f
        private set
    private val scroller = android.widget.OverScroller(context)
    private var scrolling = false
    private var scrollPointer = -1
    private var scrollStartY = 0f
    private var scrollStartOffset = 0f
    private var velocity: android.view.VelocityTracker? = null
    /** keyRects 裡最下面那排從第幾個開始（前面的是會捲動、會翻頁的那幾排） */
    private var bottomRowFirst = 0

    /** 會捲動、會翻頁的那一塊有幾排高 */
    private val gridRowCount get() = if (scrollRows > 0) scrollRows else rows.size - 1
    private val gridBottomY get() = topH + quickH + padTop + rowH * gridRowCount
    private val maxGridScroll get() = if (scrollRows > 0) ((rows.size - 1 - scrollRows) * rowH).coerceAtLeast(0f) else 0f

    private fun setGridScroll(v: Float) {
        val n = v.coerceIn(0f, maxGridScroll)
        val d = n - gridScroll
        if (d == 0f) return
        gridScroll = n
        for (i in 0 until bottomRowFirst.coerceAtMost(keyRects.size)) keyRects[i].rect.offset(0f, -d)
        invalidate()
    }

    override fun computeScroll() {
        if (scrollRows > 0 && scroller.computeScrollOffset()) {
            setGridScroll(scroller.currY.toFloat())
            postInvalidateOnAnimation()
        }
        if (tabScroller.computeScrollOffset()) {
            tabScroll = tabScroller.currX.toFloat()
            postInvalidateOnAnimation()
        }
    }

    /** 放開手指：照手指的速度繼續滑一段再慢慢停下來 */
    private fun finishScroll() {
        val vt = velocity
        if (scrolling && vt != null) {
            vt.computeCurrentVelocity(1000)
            val vy = vt.getYVelocity(scrollPointer)
            if (kotlin.math.abs(vy) > 50 * dp) {
                scroller.fling(0, gridScroll.toInt(), 0, (-vy).toInt(), 0, 0, 0, maxGridScroll.toInt())
                postInvalidateOnAnimation()
            }
        }
        scrolling = false
        scrollPointer = -1
        vt?.recycle()
        velocity = null
    }

    /** 這次手指移動在翻頁方向上的距離 */
    private fun pageDelta(x: Float, y: Float) = if (verticalPaging) y - downY else x - downX

    /** 第一頁之前、最後一頁之後還有沒有東西（表情符號：滑到底接下一個分類） */
    var morePagesBefore = false
    var morePagesAfter = false

    /** 按在空白處（沒有按到鍵）的那隻手指：拖得夠遠就開始翻頁 */
    private var emptyPagePointer = -1

    private fun canPage(dx: Float) =
        (dx < 0 && (symbolPage < symbolPageCount - 1 || morePagesAfter)) || (dx > 0 && (symbolPage > 0 || morePagesBefore))

    private fun animatePageOffsetTo(target: Float) {
        pageAnim?.cancel()
        pageAnim = android.animation.ValueAnimator.ofFloat(pageOffset, target).apply {
            duration = 220
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { pageOffset = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** 放開手指：滑夠遠就翻頁（新的一頁從另一邊滑進來），不夠就彈回去 */
    private fun finishPaging() {
        paging = false
        pagingPointer = -1
        val w = if (verticalPaging) pagingGridH else width.toFloat()
        if (canPage(pageOffset) && kotlin.math.abs(pageOffset) > w * 0.22f) {
            val dir = if (pageOffset < 0) 1 else -1
            listener?.onSymbolSwipe(dir)
            pageOffset += dir * w // 新的一頁先放在旁邊（上下滑：下面），再滑進來
        }
        animatePageOffsetTo(0f)
    }

    // ---------- 分類列：放得下就平均分，放不下（例如顏文字）就左右滑 ----------
    private var tabScroll = 0f
    /** 下一次畫的時候，把選中的那格捲到看得到的地方 */
    private var tabEnsure = true
    private var tabCache: Triple<List<String?>, Float, FloatArray>? = null
    private val tabScroller = android.widget.OverScroller(context)
    private var tabPointer = -1
    private var tabDownX = 0f
    private var tabDragStart = 0f
    private var tabDragging = false
    private var tabVelocity: android.view.VelocityTracker? = null

    private fun tabLabelSize(label: String) = if (label.length > 3) topH * 0.32f else topH * 0.4f

    /** 每一格的左邊（最後多一個＝全部的寬度） */
    private fun tabEdges(w: Float, tabs: List<String?>): FloatArray {
        tabCache?.let { if (it.second == w && it.first == tabs) return it.third }
        val n = tabs.size
        val text = tabs.map { if (it == null) topH * 0.6f else textWidth(it, tabLabelSize(it)) }
        val equal = w / n.coerceAtLeast(1)
        // 全部的字加上一點間隔排得下：照舊平均分（一般符號、表情符號）；排不下（顏文字）才改成可以滑
        val fits = text.sumOf { (it + 16 * dp).toDouble() } <= w
        val out = FloatArray(n + 1)
        for (i in 0 until n) out[i + 1] = out[i] + if (fits) equal else maxOf(48 * dp, text[i] + 32 * dp)
        tabCache = Triple(tabs, w, out)
        return out
    }

    /** 分類列現在是不是可以左右滑（放不下） */
    internal fun tabsScrollable(): Boolean {
        val tabs = symbolTabs ?: return false
        val w = width.toFloat()
        return w > 0f && tabEdges(w, tabs).last() > w + 0.5f
    }

    private fun maxTabScroll(): Float {
        val tabs = symbolTabs ?: return 0f
        val w = width.toFloat()
        return (tabEdges(w, tabs).last() - w).coerceAtLeast(0f)
    }

    /** 符號鍵盤：分類列 */
    private fun drawSymbolTabs(c: Canvas, w: Float, tabs: List<String?>) {
        val e = tabEdges(w, tabs)
        val max = (e.last() - w).coerceAtLeast(0f)
        if (tabEnsure && symbolTab in tabs.indices) {
            tabEnsure = false
            val margin = 28 * dp
            if (e[symbolTab] - tabScroll < margin) tabScroll = e[symbolTab] - margin
            if (e[symbolTab + 1] - tabScroll > w - margin) tabScroll = e[symbolTab + 1] - w + margin
        }
        tabScroll = tabScroll.coerceIn(0f, max)
        c.save()
        c.clipRect(0f, 0f, w, topH)
        c.translate(-tabScroll, 0f)
        tabs.forEachIndexed { i, label ->
            if (e[i + 1] < tabScroll || e[i] > tabScroll + w) return@forEachIndexed
            val r = RectF(e[i], 0f, e[i + 1], topH)
            if (i == symbolTab) drawTab(c, r)
            val color = if (i == symbolTab) topText else topDim
            if (label == null) theme.drawIcon(c, Icon.CLOCK, r.centerX(), r.centerY(), topH * 0.6f, color)
            else theme.drawText(c, label, r.centerX(), r.centerY(), tabLabelSize(label), color)
        }
        c.restore()
    }

    /** 符號鍵盤：最後一排上面的小線，顯示目前在第幾頁 */
    private fun drawPageIndicator(c: Canvas, w: Float) {
        if (scrollRows > 0) {
            // 連續捲動：右邊一條捲軸，跟著捲的位置走
            val max = maxGridScroll
            if (max <= 0f) return
            val count = ((rows.size - 1 + scrollRows - 1) / scrollRows).coerceAtLeast(2)
            drawPageBar(c, w - 2.5f * dp, topH + quickH + padTop, w - 2.5f * dp, gridBottomY - 1.5f * dp,
                gridScroll / max * (count - 1), count, vertical = true, maxX = w)
            return
        }
        if (symbolTabs == null || symbolPageCount <= 1) return
        val y = topH + quickH + padTop + rowH * gridRowCount - 1.5f * dp
        if (verticalPaging) {
            // 上下滑：右邊一條直的捲軸
            val top = topH + quickH + padTop
            val pos = (symbolPage - pageOffset / pagingGridH).coerceIn(0f, (symbolPageCount - 1).toFloat())
            drawPageBar(c, w - 2.5f * dp, top, w - 2.5f * dp, y, pos, symbolPageCount, vertical = true, maxX = w)
            return
        }
        // 跟著手指滑動的進度一起移動
        val pos = (symbolPage - pageOffset / w).coerceIn(0f, (symbolPageCount - 1).toFloat())
        drawPageBar(c, 0f, y, w, y, pos, symbolPageCount, vertical = false, maxX = w)
    }

    /** 頁數條的兩個顏色：底線、目前那一段（主題工作室顯示用；沒設照主題） */
    fun pageBarColors(): IntArray {
        val pb = keyStyles.pageBar
        return intArrayOf(pb?.track ?: ((60 shl 24) or (theme.textDim and 0xFFFFFF)), pb?.bar ?: theme.popupSelBg)
    }

    /** 頁數條的小圖示大小 */
    private val thumbH get() = 20 * dp

    /**
     * 畫一條頁數條：(x0,y0)→(x1,y1) 是整條底線，pos 是目前在第幾頁（滑動中可以是小數）。
     * 有小圖示就把圖示放在目前那一段的正中間，沒有就畫目前那一段。
     */
    private fun drawPageBar(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, pos: Float, count: Int, vertical: Boolean, maxX: Float) {
        val (track, bar) = pageBarColors().let { it[0] to it[1] }
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 3 * dp
            strokeCap = if (vertical) android.graphics.Paint.Cap.ROUND else android.graphics.Paint.Cap.BUTT
            color = track
        }
        c.drawLine(x0, y0, x1, y1, p)
        val len = if (vertical) y1 - y0 else x1 - x0
        val seg = len / count
        val thumb = keyStyles.pageBar?.thumb?.let { fillBitmap(it) }
        if (thumb != null && thumb.width > 0 && thumb.height > 0) {
            // 小圖示：高度固定，寬度照比例（太寬的話限制在兩倍高）
            val h = thumbH
            val tw = (h * thumb.width / thumb.height).coerceAtMost(h * 2)
            val th = tw * thumb.height / thumb.width
            val mid = seg * (pos + 0.5f)
            val cx = if (vertical) (x0).coerceAtMost(maxX - tw / 2 - 1 * dp) else (x0 + mid).coerceIn(tw / 2, maxX - tw / 2)
            val cy = if (vertical) y0 + mid else y0
            c.drawBitmap(thumb, null, RectF(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2), thumbPaint)
            return
        }
        p.color = bar
        if (vertical) c.drawLine(x0, y0 + seg * pos, x1, y0 + seg * (pos + 1), p)
        else c.drawLine(x0 + seg * pos, y0, x0 + seg * (pos + 1), y1, p)
    }

    private val thumbPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

    /** 主題工作室的頁數條面板：畫一條樣本（3 頁，停在第 2 頁） */
    fun drawPageBarSample(c: Canvas, w: Float, h: Float) {
        drawPageBar(c, 0f, h / 2, w, h / 2, 1f, 3, vertical = false, maxX = w)
    }

    private fun drawTopBar(c: Canvas, w: Float) {
        symbolTabs?.let { drawSymbolTabs(c, w, it); return }
        if (candidates.isNotEmpty()) {
            drawCandidateStrip(c, w)
            return
        }
        if (composing.isNotEmpty()) {
            // 打注音時，上面這列先顯示打了什麼（之後會變成選字列）
            theme.drawTextLeft(c, composing, 16 * dp, topH / 2, topH * 0.5f, topText)
            return
        }
        val selLeft = if (isChinese) 0f else tabW
        drawTab(c, RectF(selLeft, 0f, selLeft + tabW, topH))
        val zhColor = if (isChinese) topText else topDim
        theme.drawText(c, "中", tabW / 2, topH / 2, topH * 0.52f, zhColor)
        // 「中」右下角的小三角形：只有選注音（中文）的時候才有
        if (isChinese) {
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = zhColor }
            val tx = tabW / 2 + topH * 0.33f
            val ty = topH / 2 + topH * 0.24f
            val ts = topH * 0.1f
            c.drawPath(Path().apply { moveTo(tx + ts, ty - ts); lineTo(tx + ts, ty + ts * 0.3f); lineTo(tx - ts * 0.3f, ty + ts * 0.3f); close() }, fill)
        }
        theme.drawText(c, "En", tabW * 1.5f, topH / 2, topH * 0.46f, if (isChinese) topDim else topText)

        // 剪貼簿按鈕（在 ▽ 左邊）：設定裡關掉剪貼簿就不顯示
        if (showClipboard) theme.drawIcon(c, Icon.CLIPBOARD, w - tabW * 1.2f, topH / 2, topH * 0.6f, topIcon)
        // 右邊的 ▽：收起鍵盤
        val cx = w - tabW * 0.4f
        val cy = topH / 2
        val s = topH * 0.18f
        val p = Path().apply {
            moveTo(cx - s, cy - s * 0.6f)
            lineTo(cx + s, cy - s * 0.6f)
            lineTo(cx, cy + s * 0.8f)
            close()
        }
        val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            color = topIcon
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        c.drawPath(p, stroke)
    }

    private fun drawPreview(c: Canvas, w: Float) {
        if (stickerZoom != null) { drawStickerZoom(c, w); return }
        if (popup != null) { drawPopup(c); return }
        val kr = pressed ?: return
        if (!prefs.preview) return
        val label = previewLabel ?: return
        val r = kr.rect
        val bw = max(r.width() * 1.2f, keyH * 0.95f)
        val bh = keyH * 1.25f
        var left = r.centerX() - bw / 2
        left = min(max(0f, left), w - bw)
        val bottom = r.top + keyH * 0.15f
        val top = max(-floatH, bottom - bh)
        val box = RectF(left, top, left + bw, top + bh)
        drawBubble(c, box, kr, label)
    }

    /** 放大字泡泡：有設「泡泡」顏色就用那個；不然改過的鍵長得跟鍵一樣、沒改過的照主題 */
    private fun drawBubble(c: Canvas, box: RectF, kr: KeyRect?, label: String) {
        val st = kr?.let { keyStyles.resolve(it.key) }
        val ps = keyStyles.popup?.takeIf { it.bubbleSet }
        val styled = kr != null && st != null && st.customizedLook
        if (ps?.bubbleBg != null) {
            // 自己設了泡泡底色：一個圓角泡泡（不畫按鍵的底圖）
            val rad = if (styled) min(theme.styledRadius(box, st!!), box.height() / 2) else 8 * dp
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ps.bubbleBg }
            c.drawRoundRect(box, rad, rad, p)
            val text = ps.bubbleText ?: if (styled) lookOf(kr!!, st!!).text else theme.previewText
            theme.drawText(c, label, box.centerX(), box.centerY(), box.height() * 0.5f, text)
            return
        }
        if (!styled) {
            if (ps?.bubbleText == null) { theme.drawPreview(c, box, label); return }
            // 只改了字的顏色：泡泡照主題，字自己畫
            theme.drawPreview(c, box, "")
            theme.drawText(c, label, box.centerX(), box.centerY(), box.height() * 0.5f, ps.bubbleText)
            return
        }
        drawStyledPreview(c, box, kr!!, st!!, label, ps?.bubbleText)
    }

    /** 主題工作室改過的鍵：預覽泡泡也長得跟這顆鍵一樣（底色、底圖、外框、文字顏色、圓角） */
    private fun drawStyledPreview(c: Canvas, box: RectF, kr: KeyRect, st: KeyStyle, label: String, textOverride: Int? = null) {
        val fill = st.bgImage
        val col = lookOf(kr, st)
        if (fill?.nine == true) {
            // 九宮格：泡泡也套同一個框
            fillBitmap(fill.img)?.let {
                c.saveLayerAlpha(box, fill.alpha255)
                NineFrame.draw(c, it, fill.sx, fill.sy, box, fill.trim)
                c.restore()
            }
        } else if (fill?.shape == true) {
            // 照圖片的形狀：泡泡就是那張圖
            ImageFill.draw(c, fill, box, null, fillBitmap(fill.img), fillAnim(fill))
        } else {
            val rad = theme.styledRadius(box, st)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = col.bg }
            c.drawRoundRect(box, rad, rad, p)
            if (fill != null) {
                val clip = android.graphics.Path().apply { addRoundRect(box, rad, rad, android.graphics.Path.Direction.CW) }
                ImageFill.draw(c, fill, box, clip, fillBitmap(fill.img), fillAnim(fill))
            }
            col.border?.let { theme.drawBorderBox(c, box, rad, it, st.borderWidth, st.borderStyle, st.borderInset) }
        }
        theme.drawText(c, label, box.centerX(), box.centerY(), box.height() * 0.5f, textOverride ?: col.text)
    }

    // ---------- 觸控 ----------

    private fun keyAt(x: Float, y: Float): KeyRect? {
        // 連續捲動：捲到最下面那排後面的鍵按不到（只找手指那一塊的鍵）
        val list: List<KeyRect> = if (scrollRows > 0 && bottomRowFirst <= keyRects.size) {
            if (y < gridBottomY) keyRects.subList(0, bottomRowFirst) else keyRects.subList(bottomRowFirst, keyRects.size)
        } else keyRects
        return list.firstOrNull { it.key.act != Act.GAP && it.rect.contains(x, y) }
            ?: list.firstOrNull { it.rect.contains(x, y) }?.let { gapRow ->
                // 點到空白的邊邊，就算最近的那顆鍵
                list.filter { it.key.act != Act.GAP && it.rect.top == gapRow.rect.top }
                    .minByOrNull { kotlin.math.abs(it.rect.centerX() - x) }
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // 換成「鍵盤本體」的座標（扣掉上方透明區域）
        e.offsetLocation(0f, -floatH)
        try {
            return handleTouch(e)
        } finally {
            e.offsetLocation(0f, floatH)
        }
    }

    private fun handleTouch(e: MotionEvent): Boolean {
        editPicker?.let { pick ->
            parent?.requestDisallowInterceptTouchEvent(true)
            handleEditTouch(e, pick)
            return true
        }
        if (handleCandidateTouch(e)) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 兩隻手指快速輪流打：前一顆還沒放開就先送出
                if (pressed != null) release(cancel = false)
                if (paging) return true // 翻頁中，不理會第二隻手指
                if (pageAnim?.isRunning == true) { pageAnim?.end() }
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                if (y < topH) {
                    if (symbolTabs != null && tabsScrollable() && tabPointer < 0) {
                        // 分類列可以左右滑：先記著，放開時沒滑動才算點一下
                        tabPointer = e.getPointerId(i)
                        tabDownX = x
                        tabDragStart = tabScroll
                        tabDragging = false
                        tabScroller.forceFinished(true)
                        tabVelocity?.recycle()
                        tabVelocity = android.view.VelocityTracker.obtain().also { it.addMovement(e) }
                    } else onTopBarTap(x)
                } else if (y < topH + quickH) {
                    // 快捷符號列：按下去馬上送出
                    val idx = (x / (width.toFloat() / quickItems.size)).toInt().coerceIn(0, quickItems.size - 1)
                    haptics.perform(this, prefs)
                    quickPressed = idx
                    invalidate()
                    handler.postDelayed({ quickPressed = -1; invalidate() }, 120)
                    listener?.onQuick(quickItems[idx])
                } else {
                    val firstTop = topH + quickH + padTop
                    val lastBottom = firstTop + keyAreaH - 1f
                    downX = x
                    downY = y
                    lastMoveX = x
                    // 剪貼簿：按在清單上，之後上下拖就是捲動；正在滑的時候按一下＝停下來（不算按到）
                    var stopOnly = false
                    if (scrollRows > 0 && y < gridBottomY && scrollPointer < 0) {
                        stopOnly = !scroller.isFinished
                        scroller.forceFinished(true)
                        scrollPointer = e.getPointerId(i)
                        scrollStartY = y
                        scrollStartOffset = gridScroll
                        velocity?.recycle()
                        velocity = android.view.VelocityTracker.obtain().also { it.addMovement(e) }
                    }
                    val hit = if (stopOnly) null else keyAt(x, y.coerceIn(firstTop, lastBottom))
                    if (hit != null) press(hit, e.getPointerId(i))
                    // 符號／表情的最後一頁常常下面是空的：按在空白處也要能滑動翻頁
                    else if (symbolTabs != null) emptyPagePointer = e.getPointerId(i)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == tabPointer) {
                    tabVelocity?.addMovement(e)
                    if (!tabDragging) onTopBarTap(e.getX(e.actionIndex))
                    else tabVelocity?.let { vt ->
                        vt.computeCurrentVelocity(1000)
                        val vx = vt.getXVelocity(tabPointer)
                        if (kotlin.math.abs(vx) > 50 * dp) {
                            tabScroller.fling(tabScroll.toInt(), 0, (-vx).toInt(), 0, 0, maxTabScroll().toInt(), 0, 0)
                            postInvalidateOnAnimation()
                        }
                    }
                    tabPointer = -1
                    tabDragging = false
                    tabVelocity?.recycle()
                    tabVelocity = null
                    return true
                }
                if (e.getPointerId(e.actionIndex) == emptyPagePointer) emptyPagePointer = -1
                if (paging && e.getPointerId(e.actionIndex) == pagingPointer) { finishPaging(); return true }
                if (e.getPointerId(e.actionIndex) == scrollPointer) {
                    velocity?.addMovement(e)
                    val was = scrolling
                    finishScroll()
                    if (was) return true
                }
                if (e.getPointerId(e.actionIndex) == pressedPointer) release(cancel = false)
            }
            MotionEvent.ACTION_MOVE -> {
                // 分類列左右滑
                val ti = if (tabPointer >= 0) e.findPointerIndex(tabPointer) else -1
                if (ti >= 0) {
                    tabVelocity?.addMovement(e)
                    val dx = e.getX(ti) - tabDownX
                    if (!tabDragging && kotlin.math.abs(dx) > 8 * dp) tabDragging = true
                    if (tabDragging) {
                        tabScroll = (tabDragStart - dx).coerceIn(0f, maxTabScroll())
                        invalidate()
                    }
                    if (pressed == null && !paging) return true
                }
                val o = popup
                val i = e.findPointerIndex(pressedPointer)
                if (i >= 0 && o == null) lastMoveX = e.getX(i)
                // 符號鍵盤：左右滑換頁（整頁跟著手指走）
                if (paging) {
                    val pi = e.findPointerIndex(pagingPointer)
                    if (pi >= 0) {
                        val dx = pageDelta(e.getX(pi), e.getY(pi))
                        pageOffset = if (canPage(dx)) dx else dx * 0.25f // 沒有下一頁：只能拉一點點
                        invalidate()
                    }
                    return true
                }
                // 剪貼簿：上下拖＝捲動清單
                if (scrollPointer >= 0) {
                    velocity?.addMovement(e)
                    val si = e.findPointerIndex(scrollPointer)
                    if (scrolling) {
                        if (si >= 0) setGridScroll(scrollStartOffset - (e.getY(si) - scrollStartY))
                        return true
                    }
                    if (si >= 0 && o == null && kotlin.math.abs(e.getY(si) - scrollStartY) > 10 * dp) {
                        if (pressed != null && pressedPointer == scrollPointer) release(cancel = true) // 開始捲，剛剛按的那筆不算
                        scrolling = true
                        scrollStartY = e.getY(si)
                        scrollStartOffset = gridScroll
                        return true
                    }
                }
                val ei = if (pressed == null && emptyPagePointer >= 0) e.findPointerIndex(emptyPagePointer) else -1
                if (scrollRows == 0 && pressed == null && ei >= 0 && symbolTabs != null) {
                    val dx = pageDelta(e.getX(ei), e.getY(ei))
                    if (kotlin.math.abs(dx) > 14 * dp) {
                        pagingPointer = emptyPagePointer
                        emptyPagePointer = -1
                        paging = true
                        pageAnim?.cancel()
                        pageOffset = if (canPage(dx)) dx else dx * 0.25f
                        invalidate()
                        return true
                    }
                }
                if (scrollRows == 0 && o == null && i >= 0 && symbolTabs != null && pressed != null) {
                    val dx = pageDelta(e.getX(i), e.getY(i))
                    if (kotlin.math.abs(dx) > 14 * dp) {
                        pagingPointer = pressedPointer
                        release(cancel = true) // 開始翻頁，剛剛按的那顆鍵不算
                        paging = true
                        pageAnim?.cancel()
                        pageOffset = if (canPage(dx)) dx else dx * 0.25f
                        invalidate()
                        return true
                    }
                }
                if (o != null && i >= 0) {
                    val optW = popupRect.width() / o.items.size
                    val under = ((e.getX(i) - popupRect.left) / optW).toInt().coerceIn(0, o.items.size - 1)
                    // 動作選單：手指還沒明顯移動就停在「取消」（選單靠到螢幕邊邊、對不準手指時也一樣）；
                    // 一滑開就選手指正上方那一格，靠邊邊長按也每一格都滑得到
                    if (o.actions != null && !popupMoved && kotlin.math.abs(e.getX(i) - popupAnchorX) > POPUP_SLOP * dp) popupMoved = true
                    val sel = if (o.actions != null && !popupMoved) o.default else under
                    if (sel != popupSel) {
                        popupSel = sel
                        haptics.perform(this, prefs)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                emptyPagePointer = -1
                tabPointer = -1
                tabDragging = false
                tabVelocity?.recycle()
                tabVelocity = null
                if (paging) finishPaging()
                scrolling = false
                finishScroll()
                release(cancel = true)
            }
        }
        return true
    }

    /** 選字列和展開面板的觸控：滑動捲動、點一下選字。回傳 true 代表已經處理 */
    private fun handleCandidateTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (candidates.isEmpty() || symbolTabs != null) return false
                val x = e.x
                val y = e.y
                dragMode = when {
                    y < topH && x >= width - expandW -> 3 // 展開箭頭
                    y < topH -> 1
                    expanded -> 2
                    else -> 0
                }
                if (dragMode == 0) return false
                dragStart = if (dragMode == 2) y else x
                dragScrollStart = if (dragMode == 2) panelScroll else candScroll
                dragMoved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode == 0) return false
                if (dragMode == 3) return true
                val pos = if (dragMode == 2) e.y else e.x
                val delta = pos - dragStart
                if (kotlin.math.abs(delta) > 8 * dp) dragMoved = true
                if (dragMoved) {
                    if (dragMode == 1) candScroll = (dragScrollStart - delta).coerceIn(0f, maxStripScroll())
                    else panelScroll = (dragScrollStart - delta).coerceIn(0f, maxPanelScroll())
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragMode == 0) return false
                val mode = dragMode
                dragMode = 0
                if (dragMoved) return true
                when (mode) {
                    3 -> {
                        haptics.perform(this, prefs)
                        expanded = !expanded
                        panelScroll = 0f
                        invalidate()
                    }
                    1 -> stripLayout().indexOfFirst { e.x + candScroll in it.left..it.right }
                        .takeIf { it >= 0 }?.let { pick(it) }
                    2 -> when {
                        // 左邊那一欄：只有最下面的「簡」有作用
                        e.x < panelLeft -> if (scToggleRect().contains(e.x, e.y)) {
                            haptics.perform(this, prefs)
                            listener?.onToggleSimplified()
                        }
                        else -> panelLayout().indexOfFirst { it.contains(e.x, e.y + panelScroll) }
                            .takeIf { it >= 0 }?.let { pick(it) }
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> return dragMode != 0
            MotionEvent.ACTION_CANCEL -> {
                val was = dragMode != 0
                dragMode = 0
                return was
            }
        }
        return false
    }

    private fun pick(index: Int) {
        haptics.perform(this, prefs)
        expanded = false
        listener?.onCandidate(index)
    }

    private fun onTopBarTap(x: Float) {
        symbolTabs?.let { tabs ->
            val e = tabEdges(width.toFloat(), tabs)
            val at = x + tabScroll
            val i = (1..tabs.size).firstOrNull { at < e[it] }?.minus(1) ?: (tabs.size - 1)
            haptics.perform(this, prefs)
            listener?.onSymbolTab(i)
            return
        }
        if (composing.isNotEmpty()) return
        val l = listener ?: return
        when {
            x < tabW -> { haptics.perform(this, prefs); l.onTabChinese() }
            x < tabW * 2 -> { haptics.perform(this, prefs); l.onTabEnglish() }
            x > width - tabW * 0.8f -> { haptics.perform(this, prefs); l.onHide() }
            x > width - tabW * 1.6f && showClipboard -> { haptics.perform(this, prefs); l.onClipboard() }
        }
    }

    private fun press(kr: KeyRect, pointerId: Int) {
        pressed = kr
        pressedPointer = pointerId
        longFired = false
        previewLabel = if (kr.key.style == Style.CHAR && kr.key.act != Act.STICKER) labelFor(kr.key) else null
        haptics.perform(this, prefs)
        playEffect(kr)
        when {
            kr.key.act == Act.BACKSPACE -> handler.postDelayed(repeatRunnable, 400)
            kr.key.sub != null || kr.key.act == Act.SPACE || kr.key.act == Act.CLIP || kr.key.act == Act.STICKER ||
                itemActions?.invoke(kr.key) != null ->
                handler.postDelayed(longPressRunnable, prefs.longPressMs.toLong())
        }
        invalidate()
    }

    private fun release(cancel: Boolean) {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        val kr = pressed
        val o = popup
        pressed = null
        pressedPointer = -1
        previewLabel = null
        popup = null
        hideStickerZoom()
        invalidate()
        if (cancel || kr == null) return
        when {
            o?.actions != null -> listener?.onItemAction(kr.key, o.actions[popupSel])
            o != null -> {
                // 長按選單：選到什麼就直接打出什麼（選注音就打出注音符號本身，不進選字）
                listener?.onKey(Key(o.items[popupSel], Act.TEXT, exact = true), false)
            }
            !longFired -> listener?.onKey(kr.key, false)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopEffectAnims()
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
    }
}
