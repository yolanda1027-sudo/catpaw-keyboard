package io.github.yolanda1027.catpaw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.min

/**
 * 主題 = 決定每顆鍵怎麼畫。
 * 想加新造型的話，複製一個 class 改顏色、改形狀就好。
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
        c.drawColor(bg)
        fill.color = topBar
        c.drawRect(0f, 0f, w, topH, fill)
    }

    /** rowH：一整排的高度，用來決定字的大小 */
    abstract fun drawKey(c: Canvas, r: RectF, key: Key, label: String, pressed: Boolean, shift: Int, rowH: Float)

    fun drawPreview(c: Canvas, r: RectF, label: String) {
        fill.color = previewBg
        c.drawRoundRect(r, 8 * dp, 8 * dp, fill)
        drawText(c, label, r.centerX(), r.centerY(), r.height() * 0.5f, previewText)
    }

    fun drawText(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int) {
        textPaint.textSize = size
        textPaint.color = color
        val fm = textPaint.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    fun drawTextLeft(c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int) {
        textPaint.textAlign = Paint.Align.LEFT
        drawText(c, s, x, cy, size, color)
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** 主字＋下方小字（長按的字），或只有主字 */
    protected fun drawLabels(c: Canvas, key: Key, label: String, cx: Float, cy: Float, rowH: Float, main: Int, dim: Int, showSub: Boolean = true) {
        val sub = key.sub
        if (showSub && sub != null && key.act == Act.ZHUYIN) {
            drawText(c, label, cx, cy - rowH * 0.11f, rowH * 0.30f, main)
            drawText(c, sub, cx, cy + rowH * 0.24f, rowH * 0.16f, dim)
        } else {
            val size = if (label.codePointCount(0, label.length) > 2) rowH * 0.22f else rowH * 0.30f
            drawText(c, label, cx, cy, size, main)
        }
    }

    protected fun drawFuncContent(c: Canvas, key: Key, label: String, r: RectF, shift: Int, rowH: Float, color: Int) {
        val icon = key.icon
        if (icon != null) drawIcon(c, icon, r.centerX(), r.centerY(), rowH * 0.36f, color, shift)
        else drawText(c, label, r.centerX(), r.centerY(), rowH * 0.24f, color)
    }

    /** 用線條畫圖示 */
    fun drawIcon(c: Canvas, icon: Icon, cx: Float, cy: Float, s: Float, color: Int, shift: Int = 0) {
        stroke.color = color
        stroke.strokeWidth = 2 * dp
        fill.color = color
        path.reset()
        when (icon) {
            Icon.BACKSPACE -> {
                path.moveTo(cx - s * 0.55f, cy)
                path.lineTo(cx - s * 0.22f, cy - s * 0.34f)
                path.lineTo(cx + s * 0.55f, cy - s * 0.34f)
                path.lineTo(cx + s * 0.55f, cy + s * 0.34f)
                path.lineTo(cx - s * 0.22f, cy + s * 0.34f)
                path.close()
                c.drawPath(path, stroke)
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
                c.drawCircle(cx, cy, s * 0.4f, stroke)
                c.drawCircle(cx - s * 0.14f, cy - s * 0.1f, s * 0.05f, fill)
                c.drawCircle(cx + s * 0.14f, cy - s * 0.1f, s * 0.05f, fill)
                c.drawArc(RectF(cx - s * 0.2f, cy - s * 0.08f, cx + s * 0.2f, cy + s * 0.24f), 20f, 140f, false, stroke)
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
                val box = RectF(r.left + 4 * dp, r.top + rowH * 0.18f, r.right - 4 * dp, r.bottom - rowH * 0.18f)
                c.drawRoundRect(box, 4 * dp, 4 * dp, fill)
                drawText(c, label, r.centerX(), r.centerY(), rowH * 0.2f, textDim)
            }
            Style.ENTER -> {
                fill.color = if (pressed) enterPressed else enterColor
                val rad = min(r.width(), r.height()) * 0.36f
                c.drawCircle(r.centerX(), r.centerY(), rad, fill)
                drawIcon(c, Icon.ENTER, r.centerX(), r.centerY(), rowH * 0.34f, bg)
            }
        }
    }

    private fun pressedBox(c: Canvas, r: RectF) {
        fill.color = keyPressed
        c.drawRoundRect(RectF(r.left + 2 * dp, r.top + 3 * dp, r.right - 2 * dp, r.bottom - 3 * dp), 6 * dp, 6 * dp, fill)
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
                val box = RectF(r.left + 4 * dp, r.top + rowH * 0.18f, r.right - 4 * dp, r.bottom - rowH * 0.18f)
                c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill)
                drawText(c, label, r.centerX(), r.centerY(), rowH * 0.2f, textDim)
            }
            Style.ENTER -> {
                // 圓圓的貓頭，加兩個小耳朵
                val cx = r.centerX()
                val cy = r.centerY() + rowH * 0.04f
                val rad = min(r.width(), r.height()) * 0.33f
                fill.color = if (pressed) enterPressed else enterColor
                val ear = Path()
                ear.moveTo(cx - rad * 0.95f, cy - rad * 0.2f)
                ear.lineTo(cx - rad * 0.75f, cy - rad * 1.25f)
                ear.lineTo(cx - rad * 0.15f, cy - rad * 0.8f)
                ear.close()
                ear.moveTo(cx + rad * 0.95f, cy - rad * 0.2f)
                ear.lineTo(cx + rad * 0.75f, cy - rad * 1.25f)
                ear.lineTo(cx + rad * 0.15f, cy - rad * 0.8f)
                ear.close()
                c.drawPath(ear, fill)
                c.drawCircle(cx, cy, rad, fill)
                drawIcon(c, Icon.ENTER, cx, cy, rowH * 0.3f, Color.WHITE)
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
        c.drawCircle(cx - w * 0.13f, top + h * 0.17f, toeR, fill)
        c.drawCircle(cx + w * 0.13f, top + h * 0.17f, toeR, fill)
        c.drawCircle(cx - w * 0.32f, top + h * 0.27f, toeR, fill)
        c.drawCircle(cx + w * 0.32f, top + h * 0.27f, toeR, fill)

        // 大肉球（掌心），字寫在上面
        fill.color = if (pressed) padPressed else pad
        val padRect = RectF(cx - w * 0.38f, top + h * 0.36f, cx + w * 0.38f, top + h * 0.95f)
        c.drawRoundRect(padRect, padRect.height() * 0.45f, padRect.height() * 0.45f, fill)
        val size = if (label.codePointCount(0, label.length) > 2) rowH * 0.2f else rowH * 0.26f
        val sub = if (key.act == Act.ZHUYIN) key.sub else null
        if (sub != null) {
            // 主字稍微往左，長按的小字縮在掌心右下角
            drawText(c, label, padRect.centerX() - padRect.width() * 0.08f, padRect.centerY(), size, text)
            drawText(c, sub, padRect.right - padRect.width() * 0.17f, padRect.bottom - padRect.height() * 0.24f, rowH * 0.12f, textDim)
        } else {
            drawText(c, label, padRect.centerX(), padRect.centerY(), size, text)
        }
    }
}

object Themes {
    fun byName(name: String, dp: Float): KbTheme = when (name) {
        "catpaw" -> CatPawTheme(dp)
        else -> ClassicTheme(dp)
    }
}
