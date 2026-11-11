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
    }

    private class KeyRect(val key: Key, val rect: RectF)

    var listener: Listener? = null

    var rows: List<List<Key>> = Layouts.ZHUYIN
        set(v) {
            if (field !== v) {
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
    private var theme: KbTheme = Themes.byName(prefs.theme, dp)
    private var keyH = prefs.keyHeightDp * dp
    // 這些比例是照 Google 注音截圖量的：上方列 40dp，按鍵區上面留 6dp、下面留 10dp
    private val topH = 40 * dp
    private val padTop = 6 * dp
    private val padBottom = 10 * dp
    private val keyRects = ArrayList<KeyRect>()

    /**
     * 鍵盤上方的透明區域，用來放「正在打的注音」小框框（像 Google 注音）。
     * 這塊不會擋到後面的 App，點下去也會穿過去（見 CatImeService.onComputeInsets）。
     */
    val floatH = 34 * dp
    private val kbH get() = height - floatH

    /** 符號鍵盤的分類列（null = 不是符號鍵盤）；裡面的 null 代表「最近使用」的時鐘圖示 */
    var symbolTabs: List<String?>? = null
        set(v) { field = v; invalidate() }
    var symbolTab = 0
        set(v) { field = v; invalidate() }
    var symbolPage = 0
        set(v) { field = v; invalidate() }
    var symbolPageCount = 1
        set(v) { field = v; invalidate() }

    /**
     * 按鍵區永遠是 5 排的高度；符號鍵盤只有 4 排，就把每排拉高一點，
     * 這樣切換時鍵盤不會忽高忽低（Google 注音也是這樣）。
     */
    private val keyAreaH get() = keyH * 5
    private val rowH get() = keyAreaH / rows.size.coerceAtLeast(1)

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
        val opts = popupOptionsFor(kr.key)
        if (opts != null) {
            // 跳出長按選單，手指左右滑來選，放開就打出來
            popup = opts
            popupSel = opts.default
            popupRect = popupRectFor(kr, opts)
            previewLabel = null
        } else {
            listener?.onKey(kr.key, true)
        }
        invalidate()
    }

    // ---------- 長按選單（像 Google 注音：O ㄟ o） ----------

    /** @param main 選項裡哪一個是「原本的注音」（選它就跟一般點一下一樣） */
    private class PopupOptions(val items: List<String>, val default: Int, val main: Int)

    private var popup: PopupOptions? = null
    private var popupSel = 0
    private var popupRect = RectF()

    private fun popupOptionsFor(key: Key): PopupOptions? {
        val sub = key.sub ?: return null
        if (key.act != Act.ZHUYIN && key.act != Act.TEXT) return null
        return if (sub.length == 1 && sub[0].isLetter() && sub[0].code < 128) {
            // 英文字母：大寫、注音、小寫，預設停在注音
            PopupOptions(listOf(sub.uppercase(), key.label, sub.lowercase()), default = 1, main = 1)
        } else {
            // 數字、符號：預設停在數字／符號（長按放開＝打數字，跟以前一樣）
            PopupOptions(listOf(sub, key.label), default = 0, main = 1)
        }
    }

    private fun popupRectFor(kr: KeyRect, o: PopupOptions): RectF {
        val optW = kr.rect.width()
        val w = optW * o.items.size
        // 預設的選項剛好在按鍵正上方
        var left = kr.rect.centerX() - optW * (o.default + 0.5f)
        left = left.coerceIn(0f, max(0f, width - w))
        val bottom = kr.rect.top + keyH * 0.45f
        val top = max(-floatH, bottom - keyH * 1.15f)
        return RectF(left, top, left + w, top + keyH * 1.15f)
    }

    private fun drawPopup(c: Canvas) {
        val o = popup ?: return
        theme.drawPopup(c, popupRect, o.items, popupSel, keyH)
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
        theme = Themes.byName(prefs.theme, dp)
        keyH = prefs.keyHeightDp * dp
        requestLayout()
        layoutKeys()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (floatH + topH + padTop + keyAreaH + padBottom).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
        stripCache = null
        panelCache = null
    }

    private fun layoutKeys() {
        keyRects.clear()
        val w = width.toFloat()
        if (w <= 0f) return
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = w / total
            val top = topH + padTop + ri * rowH
            var x = 0f
            for (k in row) {
                val kw = unit * k.weight
                keyRects.add(KeyRect(k, RectF(x, top, x + kw, top + rowH)))
                x += kw
            }
        }
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
        if (reading.isNotEmpty()) theme.drawReading(c, reading, floatH)
        c.save()
        c.translate(0f, floatH) // 下面全部用「鍵盤本體」的座標畫
        theme.drawBackground(c, w, kbH, topH)
        drawTopBar(c, w)
        if (expanded) {
            drawPanel(c, w)
        } else {
            for (kr in keyRects) {
                if (kr.key.act == Act.GAP) continue
                theme.drawKey(c, kr.rect, kr.key, labelFor(kr.key), kr === pressed, shiftState, keyH)
            }
            drawPageIndicator(c, w)
            drawPreview(c, w)
        }
        c.restore()
    }

    // ---------- 選字列 ----------

    private val expandW get() = 44 * dp
    private val stripTextSize get() = topH * 0.48f
    private val panelRowH get() = keyH * 0.95f

    private fun textWidth(s: String, size: Float): Float {
        candPaint.textSize = size
        return candPaint.measureText(s)
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
    private fun panelLayout(): List<RectF> = panelCache ?: run {
        val w = width.toFloat()
        val cell = w / 6f
        val size = keyH * 0.42f
        var x = 0f
        var y = topH + 2 * dp
        candidates.map { t ->
            var iw = max(textWidth(t, size) + 24 * dp, cell)
            iw = kotlin.math.ceil(iw / cell) * cell // 對齊格子，看起來比較整齊
            if (iw > w) iw = w
            if (x + iw > w + 1f) { x = 0f; y += panelRowH }
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
            if (i == 0) theme.drawTabHighlight(c, r) // 第一個（最推薦的）有底色
            theme.drawText(c, candidates[i], r.centerX(), r.centerY(), stripTextSize, theme.text)
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
            color = theme.textDim
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
        val line = android.graphics.Paint().apply { color = theme.textDim; alpha = 50; strokeWidth = 1 * dp }
        c.save()
        c.clipRect(0f, topH, w, kbH)
        val size = keyH * 0.42f
        items.forEachIndexed { i, r0 ->
            val r = RectF(r0.left, r0.top - panelScroll, r0.right, r0.bottom - panelScroll)
            if (r.bottom < topH || r.top > kbH) return@forEachIndexed
            theme.drawText(c, candidates[i], r.centerX(), r.centerY(), size, theme.text)
            c.drawLine(r.left, r.bottom, r.right, r.bottom, line)
            if (r.right < w - 1f) c.drawLine(r.right, r.top + 8 * dp, r.right, r.bottom - 8 * dp, line)
        }
        c.restore()
    }

    private val tabW get() = width * 0.2f

    private var downX = 0f

    /** 符號鍵盤：分類列 */
    private fun drawSymbolTabs(c: Canvas, w: Float, tabs: List<String?>) {
        val tw = w / tabs.size
        tabs.forEachIndexed { i, label ->
            val r = RectF(tw * i, 0f, tw * (i + 1), topH)
            if (i == symbolTab) theme.drawTabHighlight(c, r)
            val color = if (i == symbolTab) theme.text else theme.textDim
            if (label == null) theme.drawIcon(c, Icon.CLOCK, r.centerX(), r.centerY(), topH * 0.6f, color)
            else theme.drawText(c, label, r.centerX(), r.centerY(), if (label.length > 3) topH * 0.32f else topH * 0.4f, color)
        }
    }

    /** 符號鍵盤：最後一排上面的小線，顯示目前在第幾頁 */
    private fun drawPageIndicator(c: Canvas, w: Float) {
        if (symbolTabs == null || symbolPageCount <= 1) return
        val y = topH + padTop + rowH * (rows.size - 1) - 1.5f * dp
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3 * dp }
        p.color = theme.textDim
        p.alpha = 60
        c.drawLine(0f, y, w, y, p)
        p.color = theme.popupSelBg
        val seg = w / symbolPageCount
        c.drawLine(seg * symbolPage, y, seg * (symbolPage + 1), y, p)
    }

    private fun drawTopBar(c: Canvas, w: Float) {
        symbolTabs?.let { drawSymbolTabs(c, w, it); return }
        if (candidates.isNotEmpty()) {
            drawCandidateStrip(c, w)
            return
        }
        if (composing.isNotEmpty()) {
            // 打注音時，上面這列先顯示打了什麼（之後會變成選字列）
            theme.drawTextLeft(c, composing, 16 * dp, topH / 2, topH * 0.5f, theme.text)
            return
        }
        val selLeft = if (isChinese) 0f else tabW
        theme.drawTabHighlight(c, RectF(selLeft, 0f, selLeft + tabW, topH))
        val zhColor = if (isChinese) theme.text else theme.textDim
        theme.drawText(c, "中", tabW / 2, topH / 2, topH * 0.52f, zhColor)
        // 「中」右下角的小三角形（跟原版一樣）
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = zhColor }
        val tx = tabW / 2 + topH * 0.33f
        val ty = topH / 2 + topH * 0.24f
        val ts = topH * 0.1f
        c.drawPath(Path().apply { moveTo(tx + ts, ty - ts); lineTo(tx + ts, ty + ts * 0.3f); lineTo(tx - ts * 0.3f, ty + ts * 0.3f); close() }, fill)
        theme.drawText(c, "En", tabW * 1.5f, topH / 2, topH * 0.46f, if (isChinese) theme.textDim else theme.text)
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
            color = theme.textDim
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        c.drawPath(p, stroke)
    }

    private fun drawPreview(c: Canvas, w: Float) {
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
        theme.drawPreview(c, RectF(left, top, left + bw, top + bh), label)
    }

    // ---------- 觸控 ----------

    private fun keyAt(x: Float, y: Float): KeyRect? =
        keyRects.firstOrNull { it.key.act != Act.GAP && it.rect.contains(x, y) }
            ?: keyRects.firstOrNull { it.rect.contains(x, y) }?.let { gapRow ->
                // 點到空白的邊邊，就算最近的那顆鍵
                keyRects.filter { it.key.act != Act.GAP && it.rect.top == gapRow.rect.top }
                    .minByOrNull { kotlin.math.abs(it.rect.centerX() - x) }
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
        if (handleCandidateTouch(e)) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 兩隻手指快速輪流打：前一顆還沒放開就先送出
                if (pressed != null) release(cancel = false)
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                if (y < topH) {
                    onTopBarTap(x)
                } else {
                    val firstTop = topH + padTop
                    val lastBottom = firstTop + keyAreaH - 1f
                    downX = x
                    keyAt(x, y.coerceIn(firstTop, lastBottom))?.let { press(it, e.getPointerId(i)) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == pressedPointer) release(cancel = false)
            }
            MotionEvent.ACTION_MOVE -> {
                val o = popup
                val i = e.findPointerIndex(pressedPointer)
                // 符號鍵盤：左右滑換頁
                if (o == null && i >= 0 && symbolTabs != null && pressed != null) {
                    val dx = e.getX(i) - downX
                    if (kotlin.math.abs(dx) > 40 * dp) {
                        release(cancel = true)
                        listener?.onSymbolSwipe(if (dx < 0) 1 else -1)
                        return true
                    }
                }
                if (o != null && i >= 0) {
                    val optW = popupRect.width() / o.items.size
                    val sel = ((e.getX(i) - popupRect.left) / optW).toInt().coerceIn(0, o.items.size - 1)
                    if (sel != popupSel) {
                        popupSel = sel
                        haptics.perform(this, prefs)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> release(cancel = true)
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
                    2 -> panelLayout().indexOfFirst { it.contains(e.x, e.y + panelScroll) }
                        .takeIf { it >= 0 }?.let { pick(it) }
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
            val i = (x / (width.toFloat() / tabs.size)).toInt().coerceIn(0, tabs.size - 1)
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
        }
    }

    private fun press(kr: KeyRect, pointerId: Int) {
        pressed = kr
        pressedPointer = pointerId
        longFired = false
        previewLabel = if (kr.key.style == Style.CHAR) labelFor(kr.key) else null
        haptics.perform(this, prefs)
        when {
            kr.key.act == Act.BACKSPACE -> handler.postDelayed(repeatRunnable, 400)
            kr.key.sub != null || kr.key.act == Act.SPACE ->
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
        invalidate()
        if (cancel || kr == null) return
        when {
            o != null -> {
                // 長按選單：選原本的注音＝一般點一下；選其他的就直接打出那個字
                if (popupSel == o.main) listener?.onKey(kr.key, false)
                else listener?.onKey(Key(o.items[popupSel], Act.TEXT), false)
            }
            !longFired -> listener?.onKey(kr.key, false)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
    }
}
