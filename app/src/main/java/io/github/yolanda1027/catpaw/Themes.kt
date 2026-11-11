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

    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()

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
    fun drawReading(c: Canvas, s: String, h: Float) {
        val size = h * 0.62f
        textPaint.textSize = size
        textPaint.typeface = Typeface.DEFAULT
        val tw = textPaint.measureText(s)
        val r = RectF(0f, 0f, tw + 20 * dp, h)
        fill.color = readingBg
        fill.shader = null
        c.drawRoundRect(RectF(r.left - 8 * dp, r.top, r.right, r.bottom + 8 * dp), 6 * dp, 6 * dp, fill)
        stroke.color = readingBorder
        stroke.strokeWidth = 1 * dp
        stroke.pathEffect = null
        c.drawRoundRect(RectF(r.left - 8 * dp, r.top + 0.5f * dp, r.right - 0.5f * dp, r.bottom + 8 * dp), 6 * dp, 6 * dp, stroke)
        drawTextLeft(c, s, 10 * dp, h / 2 + 1 * dp, size, readingText)
    }

    open fun drawPreview(c: Canvas, r: RectF, label: String) {
        fill.color = previewBg
        c.drawRoundRect(r, 8 * dp, 8 * dp, fill)
        drawText(c, label, r.centerX(), r.centerY(), r.height() * 0.5f, previewText)
    }

    fun drawText(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean = false) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val fm = textPaint.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    fun drawTextLeft(c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int) {
        textPaint.textAlign = Paint.Align.LEFT
        drawText(c, s, x, cy, size, color)
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun isLong(s: String) = s.codePointCount(0, s.length) > 2

    /** 主字＋小字（長按的字），或只有主字 */
    protected fun drawLabels(c: Canvas, key: Key, label: String, cx: Float, cy: Float, rowH: Float, main: Int, dim: Int) {
        val sub = key.sub
        when {
            sub != null && key.act == Act.ZHUYIN -> {
                drawText(c, label, cx, cy - rowH * 0.17f, rowH * 0.38f, main)
                drawText(c, sub, cx, cy + rowH * 0.24f, rowH * 0.25f, dim, bold = true)
            }
            sub != null -> {
                // 標點鍵：長按的字小小地放在上面（像原版的「，」上面有個「。」）
                drawText(c, sub, cx - rowH * 0.1f, cy - rowH * 0.16f, rowH * 0.26f, dim)
                drawText(c, label, cx, cy + rowH * 0.04f, rowH * 0.38f, main)
            }
            else -> drawText(c, label, cx, cy, if (isLong(label)) rowH * 0.24f else rowH * 0.38f, main)
        }
    }

    /** 有值的話，刪除鍵畫成實心、中間的 X 用這個顏色（原版的樣子） */
    protected open val backspaceCutout: Int? = null

    protected fun drawFuncContent(c: Canvas, key: Key, label: String, r: RectF, shift: Int, rowH: Float, color: Int) {
        val icon = key.icon
        if (icon != null) drawIcon(c, icon, r.centerX(), r.centerY(), rowH * 0.46f, color, shift, backspaceCutout)
        else drawText(c, label, r.centerX(), r.centerY(), if (label.length > 2) rowH * 0.34f else rowH * 0.3f, color)
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
        when (key.style) {
            Style.CHAR -> {
                if (pressed) pressedBox(c, r)
                drawLabels(c, key, label, r.centerX(), r.centerY(), rowH, text, textDim)
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
            Style.ENTER -> {
                fill.color = if (pressed) enterPressed else enterColor
                c.drawCircle(r.centerX(), r.centerY(), 17.5f * dp, fill)
                drawIcon(c, Icon.ENTER, r.centerX(), r.centerY(), rowH * 0.4f, bg)
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
    private val padPressed = Color.parseColor("#FFB3C4")
    private val bean = Color.parseColor("#FFB3C4")
    private val beanPressed = Color.parseColor("#FF8FA9")
    private val func = Color.parseColor("#FFE0D2")
    private val funcPressed = Color.parseColor("#FFC9B5")
    private val enterColor = Color.parseColor("#FF9EB5")
    private val enterPressed = Color.parseColor("#FF7F9E")

    override fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float) {
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
            Style.ENTER -> {
                // 圓圓的貓頭，加兩個小耳朵
                val cx = r.centerX()
                val cy = r.centerY() + rowH * 0.04f
                val rad = min(r.width(), r.height()) * 0.33f
                fill.color = if (pressed) enterPressed else enterColor
                c.drawPath(catEars(cx, cy, rad), fill)
                c.drawCircle(cx, cy, rad, fill)
                drawIcon(c, Icon.ENTER, cx, cy, rowH * 0.34f, Color.WHITE)
            }
        }
    }

    private fun drawPaw(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, rowH: Float) {
        val s = if (pressed) 0.9f else 1f // 按下去肉球會縮一下
        val cx = r.centerX()
        val w = min(r.width(), rowH * 1.1f) * s
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
        val size = if (label.codePointCount(0, label.length) > 2) rowH * 0.22f else rowH * 0.32f
        val sub = if (key.act == Act.ZHUYIN) key.sub else null
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
    private val white = Color.parseColor("#FFFBFC")
    private val pinkFill = Color.parseColor("#FAC9D6")
    private val pressedFill = Color.parseColor("#F7AFC3")
    private val funcFill = Color.parseColor("#F4A3BA")
    private val flowerColor = Color.parseColor("#F0607F")
    private val flowerCenter = Color.parseColor("#FFE3A0")
    private val dash = DashPathEffect(floatArrayOf(4 * dp, 3 * dp), 0f)

    override fun drawBackground(c: Canvas, w: Float, h: Float, topH: Float) {
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
            val inner = RectF(box.left + 3 * dp, box.top + 3 * dp, box.right - 3 * dp, box.bottom - 3 * dp)
            c.drawRoundRect(inner, rad * 0.6f, rad * 0.6f, stroke)
            stroke.pathEffect = null
        }

        val content = RectF(box)
        when (key.style) {
            Style.CHAR -> {
                // 字的大小跟經典版一樣
                val sub = key.sub
                if (sub != null && key.act == Act.ZHUYIN) {
                    drawText(c, label, content.centerX(), content.centerY() - rowH * 0.13f, rowH * 0.38f, text)
                    drawText(c, sub, content.centerX(), content.centerY() + rowH * 0.18f, rowH * 0.22f, if (solid) text else textDim, bold = true)
                } else {
                    drawLabels(c, key, label, content.centerX(), content.centerY(), rowH, text, textDim)
                }
            }
            Style.FUNC, Style.ENTER -> drawFuncContent(c, key, label, content, shift, rowH, Color.WHITE)
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

object Themes {
    fun byName(name: String, dp: Float): KbTheme = when (name) {
        "catpaw" -> CatPawTheme(dp)
        "stitch" -> StitchTheme(dp)
        else -> ClassicTheme(dp)
    }
}
