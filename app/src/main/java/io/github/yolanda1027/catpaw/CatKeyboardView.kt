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
    private var theme: KbTheme = Themes.byName(prefs.theme, dp, prefs)
    private var stickers: Map<StickerSlot, android.graphics.Bitmap> = Stickers.load(context)
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

    /** @param main 選項裡哪一個是原本的注音 */
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
            val l = key.label
            if (key.act == Act.TEXT && l.length == 1 && l[0].isLetter()) {
                // 英文字母：符號、現在的大小寫、另一種大小寫（例如 @ a A），預設停在符號
                val now = labelFor(key)
                val other = if (now == l.uppercase()) l.lowercase() else l.uppercase()
                PopupOptions(listOf(sub, now, other), default = 0, main = 1)
            } else {
                // 數字、符號：預設停在數字／符號（長按放開＝打數字，跟以前一樣）
                PopupOptions(listOf(sub, key.label), default = 0, main = 1)
            }
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
        theme = Themes.byName(prefs.theme, dp, prefs)
        stickers = Stickers.load(context)
        keyStyles = KeyStyles.load(prefs)
        globalEffect = KeyEffect(prefs.pressEffect, prefs.customEffect)
        effectBitmaps.clear()
        stopEffectAnims()
        decals = Decals.load(prefs)
        decalBitmaps = Decals.bitmaps(context, decals, decalBitmaps)
        nativeCache.clear()
        fontSet = loadFonts()
        stripCache = null
        panelCache = null
        keyH = prefs.keyHeightDp * dp
        requestLayout()
        layoutKeys()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (floatH + topH + quickH + padTop + keyAreaH + padBottom).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
        stripCache = null
        panelCache = null
    }

    private fun layoutKeys() {
        keyRects.clear()
        nativeCache.clear()
        val w = width.toFloat()
        if (w <= 0f) return
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = w / total
            val top = topH + quickH + padTop + ri * rowH
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
        theme.fonts = fontSet
        if (reading.isNotEmpty()) theme.drawReading(c, reading, floatH)
        c.save()
        c.translate(0f, floatH) // 下面全部用「鍵盤本體」的座標畫
        theme.drawBackground(c, w, kbH, topH)
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
            val gridBottom = topH + quickH + padTop + rowH * (rows.size - 1)
            for (kr in keyRects) {
                if (kr.key.act == Act.GAP) continue
                val inGrid = sliding && kr.rect.top < gridBottom - 1f
                if (inGrid) {
                    c.save()
                    c.clipRect(0f, topH, w, gridBottom)
                    c.translate(pageOffset, 0f)
                }
                val s = effects.scaleFor(kr.rect, now)
                if (s != 1f) { c.save(); c.scale(s, s, kr.rect.centerX(), kr.rect.centerY()) }
                drawKeyFull(c, kr)
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
        val shaped = st != null && (st.bg != null || st.radius != null || st.border != null)
        val split = !content || sticker != null || shaped || (st != null && (st.text != null || st.hideLabel == true))
        if (!split) {
            theme.drawKey(c, kr.rect, kr.key, labelFor(kr.key), isPressed, shiftState, rowH)
            return
        }
        val native = if (st != null) nativeColors(kr) else null
        // 底框
        var bodyColor: Int? = null
        if (shaped && st != null && native != null) {
            bodyColor = st.bg ?: native.bg.takeIf { native.hasBody }
            if (bodyColor != null) theme.drawStyledBody(c, kr.rect, st.copy(bg = bodyColor), isPressed)
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
                val main = st?.text ?: native?.text ?: theme.text
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
            else decals.firstOrNull { it.id == id && it.target == keyId }?.let { Decals.draw(c, it, kr.rect, kr.rect.height(), decalBitmaps[it.fileName]) }
        }
    }

    /** 這顆鍵上的字（藏起來的話就沒有） */
    private fun labelElemsOf(kr: KeyRect): List<KbTheme.LabelElem> {
        if (keyStyles.resolve(kr.key)?.hideLabel == true) return emptyList()
        theme.fonts = fontSet
        return theme.labelElems(kr.key, labelFor(kr.key), kr.rect, rowH)
    }

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
        val shaped = st != null && (st.bg != null || st.radius != null || st.border != null)
        val body = (st?.bg ?: nativeColors(kr)?.takeIf { shaped && it.hasBody }?.bg)?.takeIf { android.graphics.Color.alpha(it) > 0 }
        c.save()
        c.translate(e.cx + ls.dx * kr.rect.width(), e.cy + ls.dy * kr.rect.height())
        c.rotate(ls.rotation)
        c.scale(ls.scale, ls.scale)
        theme.drawLabelElem(c, e, labelColor(kr, e.id, st), shiftState, body)
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

    // ---------- 貼上的圖案 ----------

    /**
     * 背景圖案的參考範圍：整個鍵盤，但「不算快捷符號列」那一條。
     * 快捷列開著時，按鍵整個往下移一條，背景圖案也跟著往下移，才會一直貼在同一顆鍵旁邊，
     * 大小也不會因為多了一條而變。
     */
    private val bgRect get() = RectF(0f, quickH, width.toFloat(), kbH)

    private fun drawBgDecals(c: Canvas, front: Boolean) {
        if (decals.isEmpty()) return
        val r = bgRect
        for (d in decals) if (d.onBackground && d.front == front) Decals.draw(c, d, r, r.height(), decalBitmaps[d.fileName])
    }

    private fun drawKeyDecals(c: Canvas, kr: KeyRect) {
        if (decals.isEmpty()) return
        val id = KeyStyles.idOf(kr.key)
        for (d in decals) if (d.target == id) Decals.draw(c, d, kr.rect, kr.rect.height(), decalBitmaps[d.fileName])
    }

    // ---------- 按鍵動畫 ----------

    /** 全部按鍵共用的動畫（設定頁選的）；每次按都讀一次太慢，換設定時才重讀 */
    private var globalEffect = KeyEffect(prefs.pressEffect, prefs.customEffect)

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
            effects.add(e.kind, kr.rect, theme.popupSelBg, dark)
        }
        invalidate()
    }

    /** 按鍵動畫用的 GIF（會一直播；換設定時停掉重讀） */
    private val effectAnims = HashMap<String, android.graphics.drawable.Drawable?>()

    private fun effectAnim(name: String): android.graphics.drawable.Drawable? = effectAnims.getOrPut(name) {
        try {
            val f = java.io.File(Decals.dir(context), name).takeIf { Decals.safeName(name) && it.exists() } ?: return@getOrPut null
            val d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(f))
            if (d is android.graphics.drawable.AnimatedImageDrawable) {
                d.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
                d
            } else null // 只有一格的 GIF：當一般圖片畫就好
        } catch (_: Exception) {
            null
        }
    }

    private fun stopEffectAnims() {
        for (d in effectAnims.values) (d as? android.graphics.drawable.AnimatedImageDrawable)?.stop()
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
        invalidate()
    }

    /** 主題工作室改了圖案（拖曳、滑桿）：直接換掉，不用重讀設定 */
    fun setDecalsLive(list: List<Decal>) {
        decals = list
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
        decals.lastOrNull { it.onBackground && it.front && hit(it) }?.let { return it }
        decals.lastOrNull { !it.onBackground && hit(it) }?.let { return it }
        // 按鍵後面的背景圖案：點在按鍵上就算點按鍵
        val onKey = keyRects.any { it.key.act != Act.GAP && it.rect.contains(x, y) }
        return if (onKey) null else decals.lastOrNull { it.onBackground && !it.front && hit(it) }
    }

    /** 主題工作室選到的圖案（畫虛線框，手勢會移動它） */
    var decalEditId: String? = null
        set(v) { field = v; invalidate() }

    /** 手勢改了圖案：done = 手指放開了 */
    var onDecalChanged: ((Decal, Boolean) -> Unit)? = null

    /** 在鍵盤上點到某個圖案 */
    var onDecalPicked: ((Decal) -> Unit)? = null

    /** 圖案按了小工具（copy / flip / delete） */
    var onDecalAction: ((String, Decal) -> Unit)? = null

    /** Canva 那種編輯框（背景圖案在整個鍵盤上編輯時用） */
    private val decalHandles = DecalHandles(dp)

    private fun geoOf(d: Decal): DecalHandles.Geo? =
        targetOf(d)?.let { (r, ref) -> DecalHandles.Geo(r, ref, decalBitmaps[d.fileName]) }

    private fun drawDecalSelection(c: Canvas) {
        val d = currentDecal() ?: return
        val g = geoOf(d) ?: return
        decalHandles.draw(c, d, g, width.toFloat(), kbH)
    }

    private fun currentDecal(): Decal? = decalEditId?.let { id -> decals.firstOrNull { it.id == id } }

    private fun replaceDecal(d: Decal, done: Boolean) {
        decals = decals.map { if (it.id == d.id) d else it }
        invalidate()
        onDecalChanged?.invoke(d, done)
    }

    /** 編輯模式：點按鍵／圖案來選；選到的圖案用 Canva 那種方式拖、縮放、旋轉 */
    private fun handleEditTouch(e: MotionEvent, pick: (Key) -> Unit) {
        decalHandles.onEmptyTap = { x, y -> keyAt(x, y)?.let { pick(it.key) } }
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
        val body = render(false)
        val full = render(true)
        fun opaque(c: Int) = c or (0xFF shl 24)

        // 底色：中間區域分成小格子統計，取最多的那一組平均
        val count = HashMap<Int, IntArray>() // 格子 → [數量, r 總和, g 總和, b 總和]
        for (y in (h * 0.2f).toInt() until (h * 0.8f).toInt()) for (x in (w * 0.2f).toInt() until (w * 0.8f).toInt()) {
            val p = body.getPixel(x, y)
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
            val a = full.getPixel(x, y)
            val b = body.getPixel(x, y)
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
                val a = full.getPixel(x, y)
                val b = body.getPixel(x, y)
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
        var border = text // 沒有底框的主題（像經典版）：外框就先用字的顏色
        var edgeFound = false
        for (x in 0 until w / 2) {
            if (dist(body.getPixel(x, h / 2), base) > 60) {
                edgeFound = true
                // 往內 1 點，避開邊緣半透明的那一圈
                border = body.getPixel(minOf(x + 1, w - 1), h / 2)
                break
            }
        }
        body.recycle()
        full.recycle()
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
    private val panelLeft get() = if (prefs.showScToggle) width / 7f else 0f

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
        drawScToggle(c, line)
        c.save()
        c.clipRect(panelLeft, topH, w, kbH)
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

    /** 左下角的「簡」：開著＝亮色字＋底下一條線；關著＝淡淡的 */
    private fun drawScToggle(c: Canvas, line: android.graphics.Paint) {
        if (panelLeft <= 0f) return
        c.drawLine(panelLeft, topH + 8 * dp, panelLeft, kbH - 8 * dp, line)
        val r = scToggleRect()
        val on = prefs.simplified
        theme.drawText(c, "簡", r.centerX(), r.centerY(), keyH * 0.4f, if (on) theme.text else theme.textDim)
        if (on) {
            val u = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.popupSelBg
                strokeWidth = 2.5f * dp
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val y = r.centerY() + keyH * 0.32f
            c.drawLine(r.centerX() - keyH * 0.2f, y, r.centerX() + keyH * 0.2f, y, u)
        }
    }

    private val tabW get() = width * 0.2f

    private var downX = 0f

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
    private val quickH get() = if (quickItems.isEmpty()) 0f else 40 * dp
    private var quickPressed = -1

    private fun quickRect(i: Int): RectF {
        val w = width.toFloat() / quickItems.size.coerceAtLeast(1)
        return RectF(w * i, topH + 3 * dp, w * (i + 1), topH + quickH - 1 * dp)
    }

    /** 顯示用：把游標位置的 | 拿掉 */
    private fun quickLabel(item: String) = item.replace("|", "")

    private fun drawQuickBar(c: Canvas) {
        if (quickItems.isEmpty()) return
        quickItems.forEachIndexed { i, item ->
            val r = quickRect(i)
            val k = Key(quickLabel(item), Act.TEXT, style = Style.FUNC)
            theme.drawKey(c, r, k, k.label, i == quickPressed, 0, quickH * 1.6f)
        }
    }

    // ---------- 符號頁左右滑：整頁跟著手指走 ----------
    /** 目前這一頁往左右偏了多少（正 = 往右） */
    private var pageOffset = 0f
    private var paging = false
    private var pagingPointer = -1
    private var pageAnim: android.animation.ValueAnimator? = null

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
        val w = width.toFloat()
        if (canPage(pageOffset) && kotlin.math.abs(pageOffset) > w * 0.22f) {
            val dir = if (pageOffset < 0) 1 else -1
            listener?.onSymbolSwipe(dir)
            pageOffset += dir * w // 新的一頁先放在旁邊，再滑進來
        }
        animatePageOffsetTo(0f)
    }

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
        val y = topH + quickH + padTop + rowH * (rows.size - 1) - 1.5f * dp
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3 * dp }
        p.color = theme.textDim
        p.alpha = 60
        c.drawLine(0f, y, w, y, p)
        p.color = theme.popupSelBg
        val seg = w / symbolPageCount
        // 跟著手指滑動的進度一起移動
        val pos = (symbolPage - pageOffset / w).coerceIn(0f, (symbolPageCount - 1).toFloat())
        c.drawLine(seg * pos, y, seg * (pos + 1), y, p)
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

        // 剪貼簿按鈕（在 ▽ 左邊）
        theme.drawIcon(c, Icon.CLIPBOARD, w - tabW * 1.2f, topH / 2, topH * 0.6f, theme.textDim)
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
                    onTopBarTap(x)
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
                    val hit = keyAt(x, y.coerceIn(firstTop, lastBottom))
                    if (hit != null) press(hit, e.getPointerId(i))
                    // 符號／表情的最後一頁常常下面是空的：按在空白處也要能左右滑翻頁
                    else if (symbolTabs != null) emptyPagePointer = e.getPointerId(i)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == emptyPagePointer) emptyPagePointer = -1
                if (paging && e.getPointerId(e.actionIndex) == pagingPointer) { finishPaging(); return true }
                if (e.getPointerId(e.actionIndex) == pressedPointer) release(cancel = false)
            }
            MotionEvent.ACTION_MOVE -> {
                val o = popup
                val i = e.findPointerIndex(pressedPointer)
                // 符號鍵盤：左右滑換頁（整頁跟著手指走）
                if (paging) {
                    val pi = e.findPointerIndex(pagingPointer)
                    if (pi >= 0) {
                        val dx = e.getX(pi) - downX
                        pageOffset = if (canPage(dx)) dx else dx * 0.25f // 沒有下一頁：只能拉一點點
                        invalidate()
                    }
                    return true
                }
                val ei = if (pressed == null && emptyPagePointer >= 0) e.findPointerIndex(emptyPagePointer) else -1
                if (pressed == null && ei >= 0 && symbolTabs != null) {
                    val dx = e.getX(ei) - downX
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
                if (o == null && i >= 0 && symbolTabs != null && pressed != null) {
                    val dx = e.getX(i) - downX
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
                    val sel = ((e.getX(i) - popupRect.left) / optW).toInt().coerceIn(0, o.items.size - 1)
                    if (sel != popupSel) {
                        popupSel = sel
                        haptics.perform(this, prefs)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                emptyPagePointer = -1
                if (paging) finishPaging()
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
            x > width - tabW * 1.6f -> { haptics.perform(this, prefs); l.onClipboard() }
        }
    }

    private fun press(kr: KeyRect, pointerId: Int) {
        pressed = kr
        pressedPointer = pointerId
        longFired = false
        previewLabel = if (kr.key.style == Style.CHAR) labelFor(kr.key) else null
        haptics.perform(this, prefs)
        playEffect(kr)
        when {
            kr.key.act == Act.BACKSPACE -> handler.postDelayed(repeatRunnable, 400)
            kr.key.sub != null || kr.key.act == Act.SPACE || kr.key.act == Act.CLIP ->
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
