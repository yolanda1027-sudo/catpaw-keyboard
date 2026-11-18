package io.github.yolanda1027.catpaw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 主題 = 決定每顆鍵怎麼畫。
 * 想加新造型的話，複製一個 class 改顏色、改形狀就好。
 *
 * 字的大小比例是照 Google 注音截圖量的（以一排的高度 rowH 為基準）：
 * 注音主字 0.38、右下小字 0.25、功能鍵文字 0.34。
 */
abstract class KbTheme(protected val dp: Float) {
    abstract val bg: Int
    abstract val topBar: Int
    abstract val tabSelected: Int
    abstract val text: Int
    abstract val textDim: Int
    abstract val previewBg: Int
    abstract val previewText: Int

    /** 畫字用的字體：中文、注音、英文和符號三組 */
    var fonts: FontSet = FontSet.DEFAULT

    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()

    /**
     * 每張畫面開始前把共用的畫筆整理乾淨。
     * fill、stroke 是大家共用的：上一張畫面最後誰設了半透明、透明（例如底色設透明的按鍵）、
     * 遮罩或虛線，都會留下來；背景的漸層用 shader 畫時會跟著那個透明度，整片背景就不見了。
     */
    fun resetPaints() {
        fill.reset()
        fill.isAntiAlias = true
        stroke.reset()
        stroke.isAntiAlias = true
        stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
    }

    open fun drawBackground(c: Canvas, w: Float, h: Float, topH: Float) {
        // 只塗鍵盤本體（不能用 drawColor，不然上方透明區也會被塗滿）
        fill.shader = null
        fill.color = bg
        c.drawRect(0f, 0f, w, h, fill)
        fill.color = topBar
        c.drawRect(0f, 0f, w, topH, fill)
    }

    /** 上方列：被選到的「中」或「En」底下的那塊 */
    open fun drawTabHighlight(c: Canvas, r: RectF) {
        fill.color = tabSelected
        c.drawRect(r, fill)
    }

    /** rowH：一整排的高度，用來決定字的大小 */
    abstract fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float)

    /** 「正在打的注音」小框框的配色（預設跟 Google 注音一樣：白底黑字） */
    open val readingBg = Color.WHITE
    open val readingText = Color.parseColor("#202124")
    open val readingBorder = Color.parseColor("#DADCE0")

    /** 在鍵盤左上方畫出正在打的注音，h = 透明區域的高度 */
    /** 正在打的注音小框框；顏色沒給就照主題的（主題工作室改了上方列時會給） */
    fun drawReading(c: Canvas, s: String, h: Float, bg: Int = readingBg, textColor: Int = readingText, border: Int = readingBorder) {
        val size = h * 0.62f
        val tw = measure(s, size)
        val r = RectF(0f, 0f, tw + 20 * dp, h)
        fill.color = bg
        fill.shader = null
        c.drawRoundRect(RectF(r.left - 8 * dp, r.top, r.right, r.bottom + 8 * dp), 6 * dp, 6 * dp, fill)
        stroke.color = border
        stroke.strokeWidth = 1 * dp
        stroke.pathEffect = null
        c.drawRoundRect(RectF(r.left - 8 * dp, r.top + 0.5f * dp, r.right - 0.5f * dp, r.bottom + 8 * dp), 6 * dp, 6 * dp, stroke)
        drawTextLeft(c, s, 10 * dp, h / 2 + 1 * dp, size, textColor)
    }

    /** 剪貼簿的一筆：圓角底框＋靠左的一行字，太長就用「…」截掉 */
    open fun drawClipItem(c: Canvas, r: RectF, label: String, pressed: Boolean, rowH: Float) {
        if (label.isEmpty()) return
        val box = RectF(r.left + 6 * dp, r.top + 4 * dp, r.right - 6 * dp, r.bottom - 4 * dp)
        fill.shader = null
        fill.color = text
        fill.alpha = if (pressed) 60 else 22
        c.drawRoundRect(box, 10 * dp, 10 * dp, fill)
        fill.alpha = 255
        drawClipText(c, r, label, rowH, text)
    }

    /** 剪貼簿條上的字：靠左，太長就截掉加「…」 */
    fun drawClipText(c: Canvas, r: RectF, label: String, rowH: Float, color: Int) {
        if (label.isEmpty()) return
        val box = RectF(r.left + 6 * dp, r.top + 4 * dp, r.right - 6 * dp, r.bottom - 4 * dp)
        val size = rowH * 0.3f
        val maxW = box.width() - 28 * dp
        var shown = label
        if (measure(shown, size) > maxW) {
            while (shown.isNotEmpty() && measure("$shown…", size) > maxW) shown = shown.dropLast(1)
            shown += "…"
        }
        drawTextLeft(c, shown, box.left + 14 * dp, box.centerY(), size, color)
    }

    /** 長按選單的配色 */
    open val popupBg get() = previewBg
    open val popupText get() = previewText
    open val popupSelBg = Color.parseColor("#80CBC4")
    open val popupSelText = Color.WHITE

    /** 長按選單：一排選項，選到的那格換顏色 */
    open fun drawPopup(c: Canvas, r: RectF, items: List<String>, sel: Int, rowH: Float) {
        val optW = r.width() / items.size
        fill.shader = null
        fill.color = popupBg
        c.drawRoundRect(r, 8 * dp, 8 * dp, fill)
        val s = RectF(r.left + optW * sel, r.top, r.left + optW * (sel + 1), r.bottom)
        fill.color = popupSelBg
        c.drawRoundRect(s, 8 * dp, 8 * dp, fill)
        items.forEachIndexed { i, t ->
            drawText(c, t, r.left + optW * (i + 0.5f), r.centerY(), rowH * 0.5f, if (i == sel) popupSelText else popupText)
        }
    }

    open fun drawPreview(c: Canvas, r: RectF, label: String) {
        fill.color = previewBg
        c.drawRoundRect(r, 8 * dp, 8 * dp, fill)
        drawText(c, label, r.centerX(), r.centerY(), r.height() * 0.5f, previewText)
    }

    /** 量文字寬度（每一段用自己的字體量） */
    fun measure(s: String, size: Float, bold: Boolean = false): Float {
        textPaint.textSize = size
        var w = 0f
        for ((t, tf) in fonts.runs(s)) {
            textPaint.typeface = if (bold) fonts.bold(tf) else tf
            w += textPaint.measureText(t)
        }
        return w
    }

    fun drawText(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean = false) {
        drawRuns(c, s, cx, cy, size, color, bold, center = textPaint.textAlign == Paint.Align.CENTER)
    }

    /** 一段文字可能混了中文、注音、英文：切成小段，各用各的字體，排在同一條基線上 */
    private fun drawRuns(c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int, bold: Boolean, center: Boolean) {
        val runs = fonts.runs(s)
        textPaint.textSize = size
        textPaint.color = color
        val oldAlign = textPaint.textAlign
        // 基線用第一段的字體來算，大家都排在同一條線上
        textPaint.typeface = if (bold) fonts.bold(runs[0].second) else runs[0].second
        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        if (runs.size == 1) {
            c.drawText(s, x, baseline, textPaint)
            return
        }
        var pen = if (center) x - measure(s, size, bold) / 2f else x
        textPaint.textSize = size
        textPaint.textAlign = Paint.Align.LEFT
        for ((t, tf) in runs) {
            textPaint.typeface = if (bold) fonts.bold(tf) else tf
            c.drawText(t, pen, baseline, textPaint)
            pen += textPaint.measureText(t)
        }
        textPaint.textAlign = oldAlign
    }

    fun drawTextLeft(c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int) {
        textPaint.textAlign = Paint.Align.LEFT
        drawText(c, s, x, cy, size, color)
        textPaint.textAlign = Paint.Align.CENTER
    }

    protected fun isLong(s: String) = s.codePointCount(0, s.length) > 2

    /** 表情符號（一整個，可能是好幾個字碼組成的，例如 👨‍👩‍👧、🇹🇼）：畫大一點才看得清楚 */
    protected fun isEmoji(s: String): Boolean {
        if (s.isEmpty()) return false
        val cp = s.codePointAt(0)
        // ★ ♥ ♪ 這類符號本身不算；要當表情顯示的會帶 FE0F（例如 ❤️ ☀️）
        return cp >= 0x1F000 || s.contains('\uFE0F') || s.contains('\u200D')
    }

    protected fun emojiSize(label: String, maxW: Float, rowH: Float): Float {
        val want = rowH * 0.52f
        return if (maxW > 0f) fitSize(label, maxW, want) else want
    }

    /** 主字＋小字（長按的字），或只有主字 */
    /** 長的字（顏文字、按鍵上的詞）：盡量放大，但不超出按鍵寬度 */
    protected fun fitSize(label: String, maxW: Float, desired: Float): Float {
        val w = measure(label, desired)
        return if (w <= maxW || w == 0f) desired else desired * maxW / w
    }

    protected fun drawLabels(c: Canvas, key: Key, label: String, cx: Float, cy: Float, rowH: Float, main: Int, dim: Int, maxW: Float = 0f) {
        val sub = key.sub
        when {
            sub != null && key.subBelow -> {
                drawText(c, label, cx, cy - rowH * 0.17f, rowH * 0.38f, main)
                drawText(c, sub, cx, cy + rowH * 0.24f, rowH * 0.25f, dim, bold = true)
            }
            sub != null -> {
                // 標點鍵：長按的字小小地放在上面（像原版的「，」上面有個「。」）
                drawText(c, sub, cx - rowH * 0.1f, cy - rowH * 0.16f, rowH * 0.26f, dim)
                drawText(c, label, cx, cy + rowH * 0.04f, rowH * 0.38f, main)
            }
            else -> {
                val size = when {
                    isEmoji(label) -> emojiSize(label, maxW, rowH)
                    !isLong(label) -> rowH * 0.38f
                    maxW > 0f -> fitSize(label, maxW, rowH * 0.36f)
                    else -> rowH * 0.24f
                }
                drawText(c, label, cx, cy, size, main)
            }
        }
    }

    /** 有值的話，刪除鍵畫成實心、中間的 X 用這個顏色（原版的樣子） */
    protected open val backspaceCutout: Int? = null

    protected fun drawFuncContent(c: Canvas, key: Key, label: String, r: RectF, shift: Int, rowH: Float, color: Int) {
        val icon = key.icon
        if (icon != null) drawIcon(c, icon, r.centerX(), r.centerY(), rowH * 0.46f, color, shift, backspaceCutout)
        else drawText(c, label, r.centerX(), r.centerY(), if (label.length > 2) rowH * 0.34f else rowH * 0.3f, color)
    }

    // ---------- 主題工作室：單顆按鍵自己的樣式 ----------

    /** 自訂底色、外框畫在這個框裡（比按鍵範圍內縮一點，鍵和鍵之間才有空隙） */
    fun styledBox(r: RectF): RectF = RectF(r.left + 3 * dp, r.top + 4 * dp, r.right - 3 * dp, r.bottom - 4 * dp)

    fun styledRadius(box: RectF, st: KeyStyle) = (st.radius ?: 0.3f) * min(box.width(), box.height()) / 2f

    /** 按下去的顏色：亮的變暗一點、暗的變亮一點 */
    fun pressedOf(color: Int): Int {
        val lum = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        val t = if (lum > 128) 0f else 255f
        val k = if (lum > 128) 0.14f else 0.18f
        fun mix(v: Int) = (v + (t - v) * k).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), mix(Color.red(color)), mix(Color.green(color)), mix(Color.blue(color)))
    }

    fun drawStyledBody(c: Canvas, r: RectF, st: KeyStyle, pressed: Boolean) {
        val color = st.bg ?: return
        if (Color.alpha(color) == 0) return // 透明的底色不用畫（也不要把共用畫筆設成透明）
        val box = styledBox(r)
        val rad = styledRadius(box, st)
        fill.shader = null
        fill.color = if (pressed) pressedOf(color) else color
        c.drawRoundRect(box, rad, rad, fill)
    }

    /**
     * 自訂外框：粗細、實線／虛線／點點／縫線。
     * 實線、虛線、點點貼著按鍵的邊（整條線都在按鍵裡面）；
     * 縫線照「離邊距離」往內縮，圓角跟著變小，加粗也不會往內跑。
     * 虛線的比例跟圖案的外框一樣，兩邊看起來才一致。
     */
    fun drawStyledBorder(c: Canvas, r: RectF, st: KeyStyle) {
        val color = st.border ?: return
        val box = styledBox(r)
        drawBorderBox(c, box, styledRadius(box, st), color, st.borderWidth, st.borderStyle, st.borderInset)
    }

    /**
     * 在 box 裡面畫一圈外框（按鍵、快捷列按鈕、上方列選中那塊共用）。
     * @param outerRad box 本身的圓角
     * @param width、inset 單位 dp；null ＝ 預設
     */
    fun drawBorderBox(c: Canvas, box: RectF, outerRad: Float, color: Int, width: Float?, style0: String?, inset0: Float?) {
        if (Color.alpha(color) == 0) return
        val w = (width ?: KeyStyle.DEFAULT_BORDER_WIDTH) * dp
        val style = style0 ?: "solid"
        val inset = if (style == "stitch") (inset0 ?: KeyStyle.DEFAULT_BORDER_INSET) * dp else 0f
        // 線的中心離邊緣：至少半條線粗（不然會跑到按鍵外面）
        val d = max(w / 2, inset)
        val line = RectF(box).apply { inset(d, d) }
        if (line.width() <= 0f || line.height() <= 0f) return
        val rad = max(0f, outerRad - d)
        stroke.color = color
        stroke.strokeWidth = w
        stroke.pathEffect = when (style) {
            "dash" -> DashPathEffect(floatArrayOf(w * 2.4f, w * 2.2f), 0f)
            "dot" -> DashPathEffect(floatArrayOf(0.01f, w * 2.1f), 0f)
            "stitch" -> DashPathEffect(floatArrayOf(w * 1.8f, w * 1.9f), 0f)
            else -> null
        }
        c.drawRoundRect(line, rad, rad, stroke)
        stroke.pathEffect = null
    }

    /**
     * 只畫按鍵上的字或圖示（主題工作室改了文字顏色時用）。
     * @param body 這顆鍵自訂的底色（刪除鍵的實心圖示要用它挖出 X）
     */
    fun drawKeyContent(c: Canvas, r: RectF, key: Key, label: String, shift: Int, rowH: Float, main: Int, dim: Int, body: Int?) {
        when (key.style) {
            Style.CHAR -> drawLabels(c, key, label, r.centerX(), r.centerY(), rowH, main, dim, maxW = r.width() * 0.9f)
            Style.SPACE -> drawText(c, label, r.centerX(), r.centerY(), rowH * 0.22f, main)
            // 剪貼簿條（借一般字鍵的樣式時走這裡；以前什麼都不畫，字就不見了）
            Style.CLIP -> drawClipText(c, r, label, rowH, main)
            else -> {
                val icon = key.icon
                if (icon != null) drawIcon(c, icon, r.centerX(), r.centerY(), rowH * 0.46f, main, shift, backspaceCutout?.let { body ?: it })
                else drawText(c, label, r.centerX(), r.centerY(), if (label.length > 2) rowH * 0.34f else rowH * 0.3f, main)
            }
        }
    }

    // ---------- 按鍵上的字，一個一個分開（主題工作室可以各自移動、換色） ----------

    /**
     * 按鍵上的一個字：主字（"main"）或小字（"sub"）；功能鍵的圖示也算主字。
     * cx, cy 是沒移動前的中心點，size 是字的大小。
     */
    class LabelElem(val id: String, val text: String, val icon: Icon?, val cx: Float, val cy: Float, val size: Float, val bold: Boolean)

    /** 這顆鍵上有哪些字、本來在哪裡（照 drawLabels 的排法） */
    fun labelElems(key: Key, label: String, r: RectF, rowH: Float): List<LabelElem> {
        val cx = r.centerX()
        val cy = r.centerY()
        val sub = key.sub
        return when (key.style) {
            Style.CLIP -> emptyList()
            Style.SPACE -> listOf(LabelElem("main", label, null, cx, cy, rowH * 0.22f, false))
            Style.FUNC, Style.ENTER -> {
                val icon = key.icon
                if (icon != null) listOf(LabelElem("main", "", icon, cx, cy, rowH * 0.46f, false))
                else listOf(LabelElem("main", label, null, cx, cy, if (label.length > 2) rowH * 0.34f else rowH * 0.3f, false))
            }
            Style.CHAR -> when {
                sub != null && key.subBelow -> listOf(
                    LabelElem("main", label, null, cx, cy - rowH * 0.17f, rowH * 0.38f, false),
                    LabelElem("sub", sub, null, cx, cy + rowH * 0.24f, rowH * 0.25f, true),
                )
                sub != null -> listOf(
                    LabelElem("sub", sub, null, cx - rowH * 0.1f, cy - rowH * 0.16f, rowH * 0.26f, false),
                    LabelElem("main", label, null, cx, cy + rowH * 0.04f, rowH * 0.38f, false),
                )
                else -> {
                    val size = when {
                        isEmoji(label) -> emojiSize(label, r.width() * 0.9f, rowH)
                        !isLong(label) -> rowH * 0.38f
                        else -> fitSize(label, r.width() * 0.9f, rowH * 0.36f)
                    }
                    listOf(LabelElem("main", label, null, cx, cy, size, false))
                }
            }
        }
    }

    /** 一個字的寬高（圖示當作正方形） */
    fun elemSize(e: LabelElem): Pair<Float, Float> =
        if (e.icon != null) e.size to e.size
        else measure(e.text, e.size, e.bold).coerceAtLeast(e.size * 0.3f) to e.size

    /** 在 (0,0) 畫一個字（外面先移好位置、轉好角度） */
    fun drawLabelElem(c: Canvas, e: LabelElem, color: Int, shift: Int, cutout: Int?) {
        val icon = e.icon
        if (icon != null) drawIcon(c, icon, 0f, 0f, e.size, color, shift, backspaceCutout?.let { cutout ?: it })
        else drawText(c, e.text, 0f, 0f, e.size, color, e.bold)
    }

    /** 用線條畫圖示 */
    fun drawIcon(c: Canvas, icon: Icon, cx: Float, cy: Float, s: Float, color: Int, shift: Int = 0, cutout: Int? = null) {
        stroke.color = color
        stroke.strokeWidth = 2.2f * dp
        stroke.pathEffect = null
        fill.color = color
        fill.shader = null
        path.reset()
        when (icon) {
            Icon.BACKSPACE -> {
                path.moveTo(cx - s * 0.55f, cy)
                path.lineTo(cx - s * 0.22f, cy - s * 0.34f)
                path.lineTo(cx + s * 0.55f, cy - s * 0.34f)
                path.lineTo(cx + s * 0.55f, cy + s * 0.34f)
                path.lineTo(cx - s * 0.22f, cy + s * 0.34f)
                path.close()
                if (cutout != null) {
                    c.drawPath(path, fill)
                    c.drawPath(path, stroke) // 讓角圓一點
                    stroke.color = cutout
                } else {
                    c.drawPath(path, stroke)
                }
                val x0 = cx + s * 0.06f
                val d = s * 0.15f
                c.drawLine(x0 - d, cy - d, x0 + d, cy + d, stroke)
                c.drawLine(x0 - d, cy + d, x0 + d, cy - d, stroke)
            }
            Icon.ENTER -> {
                path.moveTo(cx + s * 0.38f, cy - s * 0.32f)
                path.lineTo(cx + s * 0.38f, cy + s * 0.12f)
                path.lineTo(cx - s * 0.38f, cy + s * 0.12f)
                c.drawPath(path, stroke)
                c.drawLine(cx - s * 0.38f, cy + s * 0.12f, cx - s * 0.18f, cy - s * 0.08f, stroke)
                c.drawLine(cx - s * 0.38f, cy + s * 0.12f, cx - s * 0.18f, cy + s * 0.32f, stroke)
            }
            Icon.SHIFT -> {
                path.moveTo(cx, cy - s * 0.42f)
                path.lineTo(cx + s * 0.4f, cy)
                path.lineTo(cx + s * 0.18f, cy)
                path.lineTo(cx + s * 0.18f, cy + s * 0.3f)
                path.lineTo(cx - s * 0.18f, cy + s * 0.3f)
                path.lineTo(cx - s * 0.18f, cy)
                path.lineTo(cx - s * 0.4f, cy)
                path.close()
                c.drawPath(path, if (shift > 0) fill else stroke)
                if (shift == 2) c.drawLine(cx - s * 0.25f, cy + s * 0.48f, cx + s * 0.25f, cy + s * 0.48f, stroke)
            }
            Icon.CLIPBOARD -> {
                val b = RectF(cx - s * 0.32f, cy - s * 0.36f, cx + s * 0.32f, cy + s * 0.42f)
                c.drawRoundRect(b, s * 0.08f, s * 0.08f, stroke)
                c.drawRoundRect(RectF(cx - s * 0.15f, cy - s * 0.46f, cx + s * 0.15f, cy - s * 0.28f), s * 0.05f, s * 0.05f, fill)
                c.drawLine(cx - s * 0.17f, cy - s * 0.05f, cx + s * 0.17f, cy - s * 0.05f, stroke)
                c.drawLine(cx - s * 0.17f, cy + s * 0.15f, cx + s * 0.1f, cy + s * 0.15f, stroke)
            }
            Icon.CLOCK -> {
                c.drawCircle(cx, cy, s * 0.42f, stroke)
                c.drawLine(cx, cy, cx, cy - s * 0.26f, stroke)
                c.drawLine(cx, cy, cx + s * 0.18f, cy + s * 0.1f, stroke)
            }
            Icon.EMOJI -> {
                c.drawCircle(cx, cy, s * 0.42f, stroke)
                c.drawCircle(cx - s * 0.15f, cy - s * 0.1f, s * 0.055f, fill)
                c.drawCircle(cx + s * 0.15f, cy - s * 0.1f, s * 0.055f, fill)
                c.drawArc(RectF(cx - s * 0.21f, cy - s * 0.08f, cx + s * 0.21f, cy + s * 0.25f), 20f, 140f, false, stroke)
            }
        }
    }
}

/** 照你截圖量出來的 Google 注音深色版 */
class ClassicTheme(dp: Float) : KbTheme(dp) {
    override val bg = Color.parseColor("#273238")
    override val topBar = Color.parseColor("#22272B")
    override val tabSelected = Color.parseColor("#273238")
    override val text = Color.parseColor("#D3D7DA")
    override val textDim = Color.parseColor("#A8ADB0")
    override val previewBg = Color.parseColor("#455A64")
    override val previewText = Color.WHITE
    private val keyPressed = Color.parseColor("#3B464C")
    private val enterColor = Color.parseColor("#80CBC4")
    private val enterPressed = Color.parseColor("#5FA9A2")
    override val backspaceCutout get() = bg

    override fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float) {
        if (key.style == Style.CLIP) { drawClipItem(c, r, label, pressed, rowH); return }
        when (key.style) {
            Style.CHAR -> {
                if (pressed) pressedBox(c, r)
                drawLabels(c, key, label, r.centerX(), r.centerY(), rowH, text, textDim, maxW = r.width() * 0.9f)
            }
            Style.FUNC -> {
                if (pressed) pressedBox(c, r)
                drawFuncContent(c, key, label, r, shift, rowH, text)
            }
            Style.SPACE -> {
                fill.color = if (pressed) Color.parseColor("#4A575E") else keyPressed
                val box = RectF(r.left + 10 * dp, r.centerY() - 14 * dp, r.right - 10 * dp, r.centerY() + 14 * dp)
                c.drawRoundRect(box, 4 * dp, 4 * dp, fill)
                drawText(c, label, r.centerX(), r.centerY(), rowH * 0.22f, textDim)
            }
            Style.CLIP -> Unit
            Style.ENTER -> {
                fill.color = if (pressed) enterPressed else enterColor
                c.drawCircle(r.centerX(), r.centerY(), 17.5f * dp, fill)
                key.icon?.let { drawIcon(c, it, r.centerX(), r.centerY(), rowH * 0.4f, bg) }
            }
        }
    }

    private fun pressedBox(c: Canvas, r: RectF) {
        fill.color = keyPressed
        c.drawRoundRect(RectF(r.left + 2 * dp, r.top + 2 * dp, r.right - 2 * dp, r.bottom - 2 * dp), 6 * dp, 6 * dp, fill)
    }
}

/** 試做版：每顆字鍵都是一個小貓肉球 🐾 */
class CatPawTheme(dp: Float) : KbTheme(dp) {
    override val bg = Color.parseColor("#FFF6EE")
    override val topBar = Color.parseColor("#FFE9DC")
    override val tabSelected = Color.parseColor("#FFF6EE")
    override val text = Color.parseColor("#6B4A3A")
    override val textDim = Color.parseColor("#B08D7C")
    override val previewBg = Color.parseColor("#FFB3C4")
    override val previewText = Color.parseColor("#5A3A2E")
    private val pad = Color.parseColor("#FFD3DC")
    override val readingText = Color.parseColor("#6B4A3A")
    override val readingBorder = Color.parseColor("#FFB3C4")
    override val popupBg get() = Color.WHITE
    override val popupSelBg = Color.parseColor("#FF9EB5")
    private val padPressed = Color.parseColor("#FFB3C4")
    private val bean = Color.parseColor("#FFB3C4")
    private val beanPressed = Color.parseColor("#FF8FA9")
    private val func = Color.parseColor("#FFE0D2")
    private val funcPressed = Color.parseColor("#FFC9B5")
    private val enterColor = Color.parseColor("#FF9EB5")
    private val enterPressed = Color.parseColor("#FF7F9E")

    override fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float) {
        if (key.style == Style.CLIP) { drawClipItem(c, r, label, pressed, rowH); return }
        when (key.style) {
            Style.CHAR -> drawPaw(c, r, key, label, pressed, rowH)
            Style.FUNC -> {
                fill.color = if (pressed) funcPressed else func
                val box = RectF(r.left + 4 * dp, r.top + 5 * dp, r.right - 4 * dp, r.bottom - 5 * dp)
                c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill)
                drawFuncContent(c, key, label, r, shift, rowH, text)
            }
            Style.SPACE -> {
                fill.color = if (pressed) funcPressed else func
                val box = RectF(r.left + 6 * dp, r.top + rowH * 0.16f, r.right - 6 * dp, r.bottom - rowH * 0.16f)
                c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill)
                drawText(c, label, r.centerX(), r.centerY(), rowH * 0.22f, textDim)
            }
            Style.CLIP -> Unit
            Style.ENTER -> {
                // 圓圓的貓頭，加兩個小耳朵
                val cx = r.centerX()
                val cy = r.centerY() + rowH * 0.04f
                val rad = min(r.width(), r.height()) * 0.33f
                fill.color = if (pressed) enterPressed else enterColor
                c.drawPath(catEars(cx, cy, rad), fill)
                c.drawCircle(cx, cy, rad, fill)
                key.icon?.let { drawIcon(c, it, cx, cy, rowH * 0.34f, Color.WHITE) }
            }
        }
    }

    private fun drawPaw(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, rowH: Float) {
        val s = if (pressed) 0.9f else 1f // 按下去肉球會縮一下
        val cx = r.centerX()
        // 一般鍵的肉球不要太寬；顏文字這種寬鍵就讓肉球跟著變寬
        val w = (if (r.width() > rowH * 2) r.width() * 0.85f else min(r.width(), rowH * 1.1f)) * s
        val h = r.height() * s
        val top = r.centerY() - h / 2

        // 四顆小肉球（腳趾）
        fill.color = if (pressed) beanPressed else bean
        val toeR = min(w * 0.085f, h * 0.075f)
        c.drawCircle(cx - w * 0.13f, top + h * 0.13f, toeR, fill)
        c.drawCircle(cx + w * 0.13f, top + h * 0.13f, toeR, fill)
        c.drawCircle(cx - w * 0.32f, top + h * 0.22f, toeR, fill)
        c.drawCircle(cx + w * 0.32f, top + h * 0.22f, toeR, fill)

        // 大肉球（掌心），字寫在上面
        fill.color = if (pressed) padPressed else pad
        val padRect = RectF(cx - w * 0.42f, top + h * 0.3f, cx + w * 0.42f, top + h * 0.97f)
        c.drawRoundRect(padRect, padRect.height() * 0.42f, padRect.height() * 0.42f, fill)
        val size = when {
            isEmoji(label) -> emojiSize(label, padRect.width() * 0.9f, rowH * 0.85f)
            label.codePointCount(0, label.length) > 2 -> fitSize(label, padRect.width() * 0.9f, rowH * 0.3f)
            else -> rowH * 0.32f
        }
        val sub = if (key.subBelow) key.sub else null
        if (sub != null) {
            // 主字稍微往左，長按的小字縮在掌心右下角
            drawText(c, label, padRect.centerX() - padRect.width() * 0.1f, padRect.centerY(), size, text)
            drawText(c, sub, padRect.right - padRect.width() * 0.18f, padRect.bottom - padRect.height() * 0.26f, rowH * 0.17f, textDim)
        } else {
            drawText(c, label, padRect.centerX(), padRect.centerY(), size, text)
        }
    }
}

/** 兩個貓耳朵（給 Enter 鍵用） */
internal fun catEars(cx: Float, cy: Float, rad: Float) = Path().apply {
    moveTo(cx - rad * 0.95f, cy - rad * 0.2f)
    lineTo(cx - rad * 0.75f, cy - rad * 1.25f)
    lineTo(cx - rad * 0.15f, cy - rad * 0.8f)
    close()
    moveTo(cx + rad * 0.95f, cy - rad * 0.2f)
    lineTo(cx + rad * 0.75f, cy - rad * 1.25f)
    lineTo(cx + rad * 0.15f, cy - rad * 0.8f)
    close()
}

/**
 * 粉紅縫線：白色／粉色的圓角鍵、粗粉紅外框、虛線縫線、
 * 隨機點綴小花，空白鍵上有小魚，Enter 鍵有貓耳朵。
 */
class StitchTheme(dp: Float) : KbTheme(dp) {
    override val bg = Color.parseColor("#F9CCD8")
    override val topBar = Color.TRANSPARENT
    override val tabSelected = Color.parseColor("#FCE1E9")
    override val text = Color.parseColor("#E0688E")
    override val textDim = Color.parseColor("#EE9AB3")
    override val previewBg = Color.WHITE
    override val previewText = Color.parseColor("#E0688E")

    private val border = Color.parseColor("#EC8FA9")
    private val shadow = Color.parseColor("#E7A0B5")
    override val readingText = Color.parseColor("#E0688E")
    override val readingBorder = Color.parseColor("#EC8FA9")
    override val popupSelBg = Color.parseColor("#F4A3BA")

    override fun drawPopup(c: Canvas, r: RectF, items: List<String>, sel: Int, rowH: Float) {
        super.drawPopup(c, r, items, sel, rowH)
        stroke.color = border
        stroke.strokeWidth = 2.5f * dp
        stroke.pathEffect = null
        c.drawRoundRect(r, 8 * dp, 8 * dp, stroke)
    }
    private val white = Color.parseColor("#FFFBFC")
    private val pinkFill = Color.parseColor("#FAC9D6")
    private val pressedFill = Color.parseColor("#F7AFC3")
    private val funcFill = Color.parseColor("#F4A3BA")
    private val flowerColor = Color.parseColor("#F0607F")
    private val flowerCenter = Color.parseColor("#FFE3A0")
    private val dash = DashPathEffect(floatArrayOf(4 * dp, 3 * dp), 0f)

    override fun drawBackground(c: Canvas, w: Float, h: Float, topH: Float) {
        fill.color = Color.BLACK // 不透明：漸層才不會跟著上一個人留下的透明度
        fill.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#FBD7E1"), Color.parseColor("#F6BCCB"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // 上方列：白色膠囊
        val bar = RectF(6 * dp, 5 * dp, w - 6 * dp, topH - 3 * dp)
        fill.color = white
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, fill)
        stroke.color = border
        stroke.strokeWidth = 2 * dp
        stroke.pathEffect = null
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, stroke)
    }

    override fun drawTabHighlight(c: Canvas, r: RectF) {
        val box = RectF(r.left + 12 * dp, r.top + 9 * dp, r.right - 4 * dp, r.bottom - 7 * dp)
        fill.color = tabSelected
        c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill)
    }

    override fun drawPreview(c: Canvas, r: RectF, label: String) {
        fill.color = white
        c.drawRoundRect(r, 10 * dp, 10 * dp, fill)
        stroke.color = border
        stroke.strokeWidth = 2.5f * dp
        stroke.pathEffect = null
        c.drawRoundRect(r, 10 * dp, 10 * dp, stroke)
        drawText(c, label, r.centerX(), r.centerY(), r.height() * 0.5f, previewText)
    }

    /** 同一顆鍵每次都得到一樣的「隨機」數，裝飾才不會一直跳 */
    private fun seed(key: Key, r: RectF) = abs(key.label.hashCode() * 31 + (r.left / dp).toInt() * 7 + (r.top / dp).toInt())

    override fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float) {
        if (key.style == Style.CLIP) { drawClipItem(c, r, label, pressed, rowH); return }
        val n = seed(key, r)
        val sink = if (pressed) 1.5f * dp else 0f // 按下去會往下沉一點
        // 鍵與鍵之間只留一點點縫，讓按鍵本體盡量大
        val box = RectF(r.left + 1.5f * dp, r.top + 2f * dp + sink, r.right - 1.5f * dp, r.bottom - 3.5f * dp + sink)
        val rad = 9 * dp

        val solid = key.style == Style.FUNC || key.style == Style.ENTER || (key.style == Style.CHAR && n % 3 == 0)
        val face = when {
            pressed -> pressedFill
            key.style == Style.FUNC || key.style == Style.ENTER -> funcFill
            solid -> pinkFill
            else -> white
        }

        if (key.style == Style.ENTER) {
            // 貓耳朵：先畫，再被按鍵本體蓋住下半部
            val ears = catEars(box.centerX(), box.top + box.height() * 0.32f, box.height() * 0.5f)
            fill.color = face
            fill.shader = null
            c.drawPath(ears, fill)
            stroke.color = border
            stroke.strokeWidth = 2.5f * dp
            stroke.pathEffect = null
            c.drawPath(ears, stroke)
        }

        // 立體陰影 → 底色 → 外框
        if (!pressed) {
            fill.color = shadow
            c.drawRoundRect(RectF(box.left, box.top + 2f * dp, box.right, box.bottom + 2f * dp), rad, rad, fill)
        }
        fill.color = face
        c.drawRoundRect(box, rad, rad, fill)
        stroke.color = border
        stroke.strokeWidth = 2f * dp
        stroke.pathEffect = null
        c.drawRoundRect(box, rad, rad, stroke)

        // 縫線
        if (!solid) {
            stroke.pathEffect = dash
            stroke.strokeWidth = 1.4f * dp
            val inner = RectF(box.left + 2.5f * dp, box.top + 2.5f * dp, box.right - 2.5f * dp, box.bottom - 2.5f * dp)
            c.drawRoundRect(inner, rad * 0.6f, rad * 0.6f, stroke)
            stroke.pathEffect = null
        }

        val content = RectF(box)
        when (key.style) {
            Style.CHAR -> {
                // 字的大小跟經典版一樣
                val sub = key.sub
                if (sub != null && key.subBelow) {
                    drawText(c, label, content.centerX(), content.centerY() - rowH * 0.13f, rowH * 0.38f, text)
                    drawText(c, sub, content.centerX(), content.centerY() + rowH * 0.18f, rowH * 0.22f, if (solid) text else textDim, bold = true)
                } else {
                    drawLabels(c, key, label, content.centerX(), content.centerY(), rowH, text, textDim, maxW = content.width() - 10 * dp)
                }
            }
            Style.FUNC, Style.ENTER -> drawFuncContent(c, key, label, content, shift, rowH, Color.WHITE)
            Style.CLIP -> Unit
            Style.SPACE -> {
                drawText(c, label, content.centerX(), content.centerY(), rowH * 0.22f, textDim)
                drawFish(c, content.left + content.width() * 0.18f, content.centerY() - 3 * dp, 7 * dp, false)
                drawFish(c, content.right - content.width() * 0.18f, content.centerY() + 3 * dp, 7 * dp, true)
            }
        }

        // 點綴小花（約七顆鍵一朵）
        if (key.style == Style.CHAR && n % 7 == 2) drawFlower(c, box.right - 3 * dp, box.top + 3 * dp, 3 * dp)
    }

    private fun drawFlower(c: Canvas, cx: Float, cy: Float, s: Float) {
        fill.shader = null
        fill.color = flowerColor
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72.0)
            c.drawCircle(cx + cos(a).toFloat() * s, cy + sin(a).toFloat() * s, s * 0.85f, fill)
        }
        fill.color = flowerCenter
        c.drawCircle(cx, cy, s * 0.6f, fill)
    }

    private fun drawFish(c: Canvas, cx: Float, cy: Float, s: Float, flip: Boolean) {
        fill.shader = null
        fill.color = flowerColor
        val d = if (flip) -1f else 1f
        c.drawOval(RectF(cx - s, cy - s * 0.5f, cx + s, cy + s * 0.5f), fill)
        val tail = Path().apply {
            moveTo(cx - d * s * 0.8f, cy)
            lineTo(cx - d * s * 1.6f, cy - s * 0.55f)
            lineTo(cx - d * s * 1.6f, cy + s * 0.55f)
            close()
        }
        c.drawPath(tail, fill)
        fill.color = Color.WHITE
        c.drawCircle(cx + d * s * 0.45f, cy - s * 0.1f, s * 0.14f, fill)
    }
}

/** 馬卡龍主題的一組顏色 */
class MacaronPalette(
    val id: String,
    val title: String,
    val bg: String, val dots: String, val key: String, val shadow: String,
    val func: String, val text: String, val sub: String, val accent: String,
    /** 空白鍵上的小圖案：star / heart / clover / cloud / flower / paw */
    val charm: String,
)

/**
 * 馬卡龍主題：粉嫩底色＋淡淡圓點、圓胖的糖果按鍵（下面有立體邊）、
 * 糖果感的 Enter（帶一點亮面反光）、空白鍵上有個小圖案。
 */
class MacaronTheme(dp: Float, private val pal: MacaronPalette) : KbTheme(dp) {
    private fun c(s: String) = Color.parseColor(s)
    override val bg = c(pal.bg)
    override val topBar = Color.TRANSPARENT
    override val tabSelected = c(pal.key)
    override val text = c(pal.text)
    override val textDim = c(pal.sub)
    override val previewBg = c(pal.key)
    override val previewText = c(pal.text)
    override val popupBg get() = c(pal.key)
    override val popupSelBg = c(pal.accent)
    override val popupSelText = CustomTheme.contrastOn(c(pal.accent))
    override val readingBg = c(pal.key)
    override val readingText = c(pal.text)
    override val readingBorder = c(pal.accent)
    private val keyC = c(pal.key)
    private val shadowC = c(pal.shadow)
    private val funcC = c(pal.func)
    private val accentC = c(pal.accent)
    private val dotsC = c(pal.dots)
    private val charmPath = Path()

    override fun drawBackground(c: Canvas, w: Float, h: Float, topH: Float) {
        fill.shader = null
        fill.color = bg
        c.drawRect(0f, 0f, w, h, fill)
        // 淡淡的圓點花紋（交錯排列）
        fill.color = dotsC
        val step = 22 * dp
        var row = 0
        var y = step / 2
        while (y < h) {
            var x = if (row % 2 == 0) step / 2 else step
            while (x < w) {
                c.drawCircle(x, y, 2.2f * dp, fill)
                x += step
            }
            y += step * 0.85f
            row++
        }
    }

    override fun drawTabHighlight(c: Canvas, r: RectF) {
        val box = RectF(r.left + 8 * dp, r.top + 7 * dp, r.right - 8 * dp, r.bottom - 7 * dp)
        fill.shader = null
        fill.color = keyC
        c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill)
    }

    override fun drawPreview(c: Canvas, r: RectF, label: String) {
        fill.shader = null
        fill.color = shadowC
        c.drawRoundRect(RectF(r.left, r.top + 3 * dp, r.right, r.bottom + 3 * dp), 14 * dp, 14 * dp, fill)
        fill.color = keyC
        c.drawRoundRect(r, 14 * dp, 14 * dp, fill)
        drawText(c, label, r.centerX(), r.centerY(), r.height() * 0.5f, text)
    }

    override fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float) {
        if (key.style == Style.CLIP) { drawClipItem(c, r, label, pressed, rowH); return }
        val sink = if (pressed) 2.5f * dp else 0f
        val box = RectF(r.left + 3 * dp, r.top + 3.5f * dp + sink, r.right - 3 * dp, r.bottom - 5.5f * dp + sink)
        val rad = minOf(box.height(), box.width()) * 0.32f
        val face = when (key.style) {
            Style.ENTER -> accentC
            Style.FUNC -> funcC
            else -> keyC
        }
        fill.shader = null
        // 糖果的立體邊
        if (!pressed) {
            fill.color = if (key.style == Style.ENTER) CustomTheme.blend(accentC, Color.BLACK, 0.15f) else shadowC
            c.drawRoundRect(RectF(box.left, box.top + 3 * dp, box.right, box.bottom + 3 * dp), rad, rad, fill)
        }
        fill.color = if (pressed) CustomTheme.blend(face, shadowC, 0.5f) else face
        c.drawRoundRect(box, rad, rad, fill)

        when (key.style) {
            Style.CHAR -> drawLabels(c, key, label, box.centerX(), box.centerY(), rowH * 0.95f, text, textDim, maxW = box.width() - 6 * dp)
            Style.FUNC -> drawFuncContent(c, key, label, box, shift, rowH * 0.95f, text)
            Style.SPACE -> {
                drawText(c, label, box.centerX(), box.centerY(), rowH * 0.22f, textDim)
                if (box.width() > rowH * 1.6f) drawCharm(c, box.left + box.height() * 0.6f, box.centerY(), box.height() * 0.24f)
            }
            Style.ENTER -> {
                // 糖果的亮面反光
                fill.color = Color.WHITE
                fill.alpha = 110
                c.drawOval(RectF(box.left + rad * 0.5f, box.top + 3 * dp, box.left + rad * 0.5f + box.width() * 0.28f, box.top + 3 * dp + box.height() * 0.2f), fill)
                fill.alpha = 255
                key.icon?.let { drawIcon(c, it, box.centerX(), box.centerY(), rowH * 0.4f, CustomTheme.contrastOn(accentC)) }
            }
            Style.CLIP -> Unit
        }
    }

    /** 空白鍵上的小圖案 */
    private fun drawCharm(c: Canvas, cx: Float, cy: Float, s: Float) {
        fill.shader = null
        fill.color = accentC
        val p = charmPath
        p.reset()
        when (pal.charm) {
            "star" -> {
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rr = if (i % 2 == 0) s else s * 0.45f
                    val x = cx + cos(a).toFloat() * rr
                    val y = cy + sin(a).toFloat() * rr
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                c.drawPath(p, fill)
            }
            "heart" -> {
                p.moveTo(cx, cy + s * 0.9f)
                p.cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.35f)
                p.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.9f)
                c.drawPath(p, fill)
            }
            "clover" -> {
                for (i in 0 until 4) {
                    val a = Math.toRadians(45.0 + i * 90.0)
                    c.drawCircle(cx + cos(a).toFloat() * s * 0.45f, cy + sin(a).toFloat() * s * 0.45f, s * 0.42f, fill)
                }
            }
            "cloud" -> {
                c.drawCircle(cx - s * 0.45f, cy + s * 0.15f, s * 0.42f, fill)
                c.drawCircle(cx + s * 0.05f, cy - s * 0.15f, s * 0.55f, fill)
                c.drawCircle(cx + s * 0.55f, cy + s * 0.15f, s * 0.4f, fill)
                c.drawRoundRect(RectF(cx - s * 0.85f, cy + s * 0.05f, cx + s * 0.95f, cy + s * 0.55f), s * 0.25f, s * 0.25f, fill)
            }
            "flower" -> {
                for (i in 0 until 5) {
                    val a = Math.toRadians(-90.0 + i * 72.0)
                    c.drawCircle(cx + cos(a).toFloat() * s * 0.6f, cy + sin(a).toFloat() * s * 0.6f, s * 0.4f, fill)
                }
                fill.color = Color.WHITE
                c.drawCircle(cx, cy, s * 0.28f, fill)
            }
            else -> { // paw
                c.drawOval(RectF(cx - s * 0.55f, cy - s * 0.05f, cx + s * 0.55f, cy + s * 0.75f), fill)
                val r = s * 0.22f
                c.drawCircle(cx - s * 0.62f, cy - s * 0.25f, r, fill)
                c.drawCircle(cx - s * 0.22f, cy - s * 0.6f, r, fill)
                c.drawCircle(cx + s * 0.22f, cy - s * 0.6f, r, fill)
                c.drawCircle(cx + s * 0.62f, cy - s * 0.25f, r, fill)
            }
        }
    }

    companion object {
        val PALETTES = listOf(
            MacaronPalette("macaron_butter", "馬卡龍・奶油黃 🧈",
                bg = "#FFF6D5", dots = "#FFEAAE", key = "#FFFDF4", shadow = "#F0D894",
                func = "#FFE9A0", text = "#7A5C2E", sub = "#C49A4A", accent = "#FFCC45", charm = "star"),
            MacaronPalette("macaron_strawberry", "馬卡龍・草莓牛奶 🍓",
                bg = "#FFE8EE", dots = "#FFD5E0", key = "#FFFAFB", shadow = "#F3C1CE",
                func = "#FFD0DC", text = "#8C4A5E", sub = "#D08AA0", accent = "#FF9EBB", charm = "heart"),
            MacaronPalette("macaron_mint", "馬卡龍・薄荷綠 🌿",
                bg = "#E3F6EE", dots = "#CDEEDE", key = "#FBFFFD", shadow = "#B7E0CC",
                func = "#C6EBD9", text = "#3F6E5A", sub = "#7DB59B", accent = "#7FD3AE", charm = "clover"),
            MacaronPalette("macaron_blueberry", "馬卡龍・藍莓蘇打 🫐",
                bg = "#E3F0FF", dots = "#D0E3FF", key = "#FBFDFF", shadow = "#B9D3F0",
                func = "#CADFF8", text = "#435C85", sub = "#8AA6CF", accent = "#8DB8F2", charm = "cloud"),
            MacaronPalette("macaron_lavender", "馬卡龍・薰衣草 💜",
                bg = "#EFE8FB", dots = "#E2D7F7", key = "#FDFBFF", shadow = "#D0C1EC",
                func = "#DED0F5", text = "#5E4B85", sub = "#A08CC8", accent = "#B79CF0", charm = "flower"),
            MacaronPalette("macaron_peach", "馬卡龍・蜜桃 🍑",
                bg = "#FFEBDD", dots = "#FFDCC6", key = "#FFFBF8", shadow = "#F2C6AA",
                func = "#FFD6BD", text = "#8A5038", sub = "#D2957A", accent = "#FFAA80", charm = "paw"),
        )
    }
}

/** 魔法學院主題的一組顏色 */
class MagicPalette(
    val id: String,
    val title: String,
    /** 學院色（底色）、深一點的學院色、金屬色（金、銀、銅…） */
    val house: String, val houseDark: String, val metal: String,
    /** 蠟封的顏色、蠟封上圖示的顏色 */
    val wax: String, val waxIcon: String,
)

/**
 * 魔法學院風：深色學院底色＋星光、舊羊皮紙按鍵（墨水字）、
 * 學院色功能鍵＋金屬色字、蠟封印章 Enter、空白鍵上的月亮和星星。
 */
class MagicTheme(dp: Float, private val pal: MagicPalette) : KbTheme(dp) {
    private fun c(s: String) = Color.parseColor(s)
    private val houseC = c(pal.house)
    private val houseDarkC = c(pal.houseDark)
    private val metalC = c(pal.metal)
    // 羊皮紙的顏色（照使用者給的「霍格華茲日誌」狀態欄取色）
    private val ink = c("#3B2A1A")
    private val inkSoft = c("#8A6A2F") // 金褐色：小字（數字、英文）
    private val paperLight = c("#F3E8D2")
    private val paperDark = c("#EBDFC6") // 紙張邊緣稍微深一點
    private val paperPressed = c("#E4D5B5")
    private val paperEdge = c("#B39863") // 細細的深金褐邊框
    private val edgeGlow = android.graphics.BlurMaskFilter(5f * dp, android.graphics.BlurMaskFilter.Blur.NORMAL)

    // 底色是深的：上方列、選字列用淺色字
    override val bg = houseC
    override val topBar = Color.TRANSPARENT
    override val tabSelected = Color.TRANSPARENT
    override val text = c("#F6EBCB")
    override val textDim = metalC
    override val previewBg = paperLight
    override val previewText = ink
    override val popupBg get() = paperLight
    override val popupText get() = ink
    override val popupSelBg = metalC
    override val popupSelText = CustomTheme.contrastOn(metalC)
    override val readingBg = paperLight
    override val readingText = ink
    override val readingBorder = metalC
    override val backspaceCutout: Int? = null

    override fun drawBackground(c: Canvas, w: Float, h: Float, topH: Float) {
        fill.color = Color.BLACK // 不透明：漸層才不會跟著上一個人留下的透明度
        fill.shader = LinearGradient(0f, 0f, 0f, h, houseC, houseDarkC, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // 星光：固定位置（每次畫都一樣），大小亮度不一
        var seed = 7
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 10000) / 10000f }
        repeat(70) {
            val x = rnd() * w
            val y = rnd() * h
            val big = rnd() > 0.85f
            fill.color = if (big) metalC else Color.WHITE
            fill.alpha = (40 + rnd() * 110).toInt()
            if (big) sparkle(c, x, y, 3.5f * dp) else c.drawCircle(x, y, (0.6f + rnd()) * dp, fill)
        }
        fill.alpha = 255
        // 上方列底下一條金屬色細線
        stroke.color = metalC
        stroke.alpha = 120
        stroke.strokeWidth = 1 * dp
        stroke.pathEffect = null
        c.drawLine(10 * dp, topH - 1 * dp, w - 10 * dp, topH - 1 * dp, stroke)
        stroke.alpha = 255
    }

    override fun drawTabHighlight(c: Canvas, r: RectF) {
        val box = RectF(r.left + 8 * dp, r.top + 7 * dp, r.right - 8 * dp, r.bottom - 7 * dp)
        stroke.color = metalC
        stroke.strokeWidth = 1.5f * dp
        stroke.pathEffect = null
        c.drawRoundRect(box, box.height() / 2, box.height() / 2, stroke)
    }

    override fun drawPreview(c: Canvas, r: RectF, label: String) {
        parchment(c, r, false, 0)
        drawText(c, label, r.centerX(), r.centerY(), r.height() * 0.5f, ink)
    }

    /**
     * 羊皮紙：很淡的米色，邊緣往內一點點變深（像舊紙），外面一圈細細的深金褐邊框。
     * 作法：先塗邊緣色，再把中間的米色「暈開」疊上去。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun parchment(c: Canvas, box: RectF, pressed: Boolean, seed: Int) {
        val rad = 8 * dp
        fill.shader = null
        fill.color = if (pressed) paperPressed else paperDark
        c.drawRoundRect(box, rad, rad, fill)
        val inner = RectF(box.left + 3 * dp, box.top + 3 * dp, box.right - 3 * dp, box.bottom - 3 * dp)
        fill.color = if (pressed) CustomTheme.blend(paperPressed, paperLight, 0.4f) else paperLight
        fill.maskFilter = edgeGlow
        c.drawRoundRect(inner, rad * 0.7f, rad * 0.7f, fill)
        fill.maskFilter = null
        stroke.color = paperEdge
        stroke.strokeWidth = 1f * dp
        stroke.pathEffect = null
        c.drawRoundRect(box, rad, rad, stroke)
    }

    override fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float) {
        if (key.style == Style.CLIP) { drawClipItem(c, r, label, pressed, rowH); return }
        val box = RectF(r.left + 2.5f * dp, r.top + 3.5f * dp, r.right - 2.5f * dp, r.bottom - 3.5f * dp)
        when (key.style) {
            Style.CHAR -> {
                parchment(c, box, pressed, (r.left.toInt() * 31 + r.top.toInt()))
                drawLabels(c, key, label, box.centerX(), box.centerY(), rowH, ink, inkSoft, maxW = box.width() - 6 * dp)
            }
            Style.FUNC -> {
                fill.shader = null
                fill.color = if (pressed) houseC else houseDarkC
                c.drawRoundRect(box, 6 * dp, 6 * dp, fill)
                stroke.color = metalC
                stroke.strokeWidth = 1.2f * dp
                stroke.pathEffect = null
                c.drawRoundRect(box, 6 * dp, 6 * dp, stroke)
                drawFuncContent(c, key, label, box, shift, rowH, metalC)
            }
            Style.SPACE -> {
                parchment(c, box, pressed, 99)
                drawText(c, label, box.centerX(), box.centerY(), rowH * 0.22f, inkSoft)
                if (box.width() > rowH * 1.6f) moonAndStar(c, box.left + box.height() * 0.5f, box.centerY(), box.height() * 0.24f)
            }
            Style.ENTER -> waxSeal(c, r, key, pressed, rowH)
            Style.CLIP -> Unit
        }
    }

    /** 蠟封印章：波浪邊的圓、裡面一圈金屬色 */
    private fun waxSeal(c: Canvas, r: RectF, key: Key, pressed: Boolean, rowH: Float) {
        val cx = r.centerX()
        val cy = r.centerY()
        val rad = minOf(r.width(), r.height()) * 0.36f * if (pressed) 0.92f else 1f
        val wax = c(pal.wax)
        fill.shader = null
        fill.color = CustomTheme.blend(wax, Color.BLACK, 0.25f)
        c.drawCircle(cx + 1.5f * dp, cy + 2 * dp, rad * 1.05f, fill) // 影子
        fill.color = wax
        for (i in 0 until 14) {
            val a = Math.toRadians(i * 360.0 / 14)
            c.drawCircle(cx + cos(a).toFloat() * rad * 0.88f, cy + sin(a).toFloat() * rad * 0.88f, rad * 0.2f, fill)
        }
        c.drawCircle(cx, cy, rad * 0.92f, fill)
        // 蠟封的光澤
        fill.color = Color.WHITE
        fill.alpha = 60
        c.drawCircle(cx - rad * 0.3f, cy - rad * 0.35f, rad * 0.25f, fill)
        fill.alpha = 255
        stroke.color = c(pal.waxIcon)
        stroke.strokeWidth = 1.4f * dp
        stroke.pathEffect = null
        c.drawCircle(cx, cy, rad * 0.68f, stroke)
        key.icon?.let { drawIcon(c, it, cx, cy, rowH * 0.32f, c(pal.waxIcon)) }
    }

    private fun moonAndStar(c: Canvas, cx: Float, cy: Float, s: Float) {
        fill.shader = null
        fill.color = metalC
        val p = Path()
        p.addCircle(cx, cy, s, Path.Direction.CW)
        val cut = Path().apply { addCircle(cx + s * 0.45f, cy - s * 0.25f, s * 0.85f, Path.Direction.CW) }
        p.op(cut, Path.Op.DIFFERENCE)
        c.drawPath(p, fill)
        sparkle(c, cx + s * 1.05f, cy - s * 0.7f, s * 0.42f)
    }

    private fun sparkle(c: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Path()
        val k = s * 0.18f
        p.moveTo(cx, cy - s)
        p.quadTo(cx + k, cy - k, cx + s, cy)
        p.quadTo(cx + k, cy + k, cx, cy + s)
        p.quadTo(cx - k, cy + k, cx - s, cy)
        p.quadTo(cx - k, cy - k, cx, cy - s)
        p.close()
        c.drawPath(p, fill)
    }

    companion object {
        val PALETTES = listOf(
            MagicPalette("magic_red", "魔法學院・紅金 🦁", house = "#7F1D1D", houseDark = "#4A0E0E", metal = "#E2B33C",
                wax = "#B3262A", waxIcon = "#F2C75C"),
            MagicPalette("magic_green", "魔法學院・綠銀 🐍", house = "#1E4D33", houseDark = "#0F2A1C", metal = "#C9CED3",
                wax = "#2F7D52", waxIcon = "#E4E8EC"),
            MagicPalette("magic_blue", "魔法學院・藍銅 🦅", house = "#1B2A57", houseDark = "#0D1630", metal = "#C08A4E",
                wax = "#3A5BA8", waxIcon = "#E8B57A"),
            MagicPalette("magic_yellow", "魔法學院・黃黑 🦡", house = "#3A322B", houseDark = "#1F1A16", metal = "#F0C23B",
                wax = "#E2A92B", waxIcon = "#3A2A10"),
        )
    }
}

/** 自訂配色：5 個顏色都可以在設定頁調 */
class CustomTheme(
    dp: Float,
    override val bg: Int,
    private val key: Int,
    override val text: Int,
    override val textDim: Int,
    private val accent: Int,
) : KbTheme(dp) {
    override val topBar = blend(bg, text, 0.07f)
    override val tabSelected = bg
    override val previewBg = key
    override val previewText = text
    override val popupBg get() = key
    override val popupSelBg = accent
    override val popupSelText = contrastOn(accent)
    override val readingBg = key
    override val readingText = text
    override val readingBorder = accent

    override fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float) {
        if (key.style == Style.CLIP) { drawClipItem(c, r, label, pressed, rowH); return }
        val box = RectF(r.left + 3 * dp, r.top + 4 * dp, r.right - 3 * dp, r.bottom - 4 * dp)
        val rad = 8 * dp
        fill.shader = null
        when (key.style) {
            Style.CHAR -> {
                fill.color = if (pressed) blend(this.key, text, 0.18f) else this.key
                c.drawRoundRect(box, rad, rad, fill)
                drawLabels(c, key, label, r.centerX(), r.centerY(), rowH, text, textDim, maxW = box.width() - 6 * dp)
            }
            Style.FUNC -> {
                fill.color = blend(this.key, text, if (pressed) 0.25f else 0.1f)
                c.drawRoundRect(box, rad, rad, fill)
                drawFuncContent(c, key, label, r, shift, rowH, text)
            }
            Style.SPACE -> {
                fill.color = if (pressed) blend(this.key, text, 0.18f) else this.key
                c.drawRoundRect(box, rad, rad, fill)
                drawText(c, label, r.centerX(), r.centerY(), rowH * 0.22f, textDim)
            }
            Style.ENTER -> {
                fill.color = if (pressed) blend(accent, Color.BLACK, 0.15f) else accent
                c.drawRoundRect(box, rad, rad, fill)
                key.icon?.let { drawIcon(c, it, r.centerX(), r.centerY(), rowH * 0.4f, contrastOn(accent)) }
            }
            Style.CLIP -> Unit
        }
    }

    companion object {
        fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )

        /** 在這個底色上，用白字還是深色字比較清楚 */
        fun contrastOn(c: Int): Int {
            val l = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
            return if (l > 170) Color.parseColor("#3E3A4F") else Color.WHITE
        }
    }
}

object Themes {
    fun byName(name: String, dp: Float, prefs: Prefs? = null): KbTheme = when (name) {
        "catpaw" -> CatPawTheme(dp)
        "stitch" -> StitchTheme(dp)
        in MacaronTheme.PALETTES.map { it.id } -> MacaronTheme(dp, MacaronTheme.PALETTES.first { it.id == name })
        in MagicTheme.PALETTES.map { it.id } -> MagicTheme(dp, MagicTheme.PALETTES.first { it.id == name })
        "custom" -> if (prefs != null) CustomTheme(
            dp, prefs.customBg, prefs.customKey, prefs.customText, prefs.customSub, prefs.customAccent,
        ) else ClassicTheme(dp)
        else -> ClassicTheme(dp)
    }
}
