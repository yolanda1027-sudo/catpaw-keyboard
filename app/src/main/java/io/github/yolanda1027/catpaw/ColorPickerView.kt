package io.github.yolanda1027.catpaw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/**
 * 簡單的選色器：
 * - 上面的大色塊：左右 = 鮮豔程度（飽和度），上下 = 亮暗
 * - 下面的彩虹條：色相
 * 手指點或拖都可以。
 */
@SuppressLint("ViewConstructor")
class ColorPickerView(context: Context, initial: Int) : View(context) {

    /** 顏色一變就通知（給色碼欄、預覽用） */
    var onColorChanged: ((Int) -> Unit)? = null

    private val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
    val color: Int get() = Color.HSVToColor(hsv)

    private val dp = resources.displayMetrics.density
    private val gap = 14 * dp
    private val barH = 30 * dp
    private val pad = 12 * dp // 留邊，讓選取圈不會被切掉
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val svRect = RectF()
    private val hueRect = RectF()
    private var dragging = 0 // 1 = 大色塊、2 = 彩虹條

    /** 從外面（色碼欄）設定顏色 */
    fun setColor(c: Int) {
        Color.colorToHSV(c, hsv)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val sv = (w - pad * 2) * 0.75f
        setMeasuredDimension(w, (pad + sv + gap + barH + pad).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val sv = (w - pad * 2) * 0.75f
        svRect.set(pad, pad, w - pad, pad + sv)
        hueRect.set(pad, svRect.bottom + gap, w - pad, svRect.bottom + gap + barH)
    }

    override fun onDraw(c: Canvas) {
        val r = 10 * dp
        // 大色塊：白 → 目前色相（左右），再疊上透明 → 黑（上下）
        val pure = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        val horizontal = LinearGradient(svRect.left, 0f, svRect.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        val vertical = LinearGradient(0f, svRect.top, 0f, svRect.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        paint.shader = ComposeShader(horizontal, vertical, PorterDuff.Mode.SRC_OVER)
        c.drawRoundRect(svRect, r, r, paint)

        // 彩虹條
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        paint.shader = LinearGradient(hueRect.left, 0f, hueRect.right, 0f, hues, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(hueRect, hueRect.height() / 2, hueRect.height() / 2, paint)
        paint.shader = null

        // 選取的位置：白圈＋深色細圈，什麼顏色上都看得到
        val sx = svRect.left + hsv[1] * svRect.width()
        val sy = svRect.top + (1 - hsv[2]) * svRect.height()
        drawKnob(c, sx, sy, 11 * dp, color)
        val hx = hueRect.left + hsv[0] / 360f * hueRect.width()
        drawKnob(c, hx, hueRect.centerY(), barH / 2 + 2 * dp, pure)
    }

    private fun drawKnob(c: Canvas, x: Float, y: Float, rad: Float, fillColor: Int) {
        paint.color = fillColor
        c.drawCircle(x, y, rad, paint)
        ring.color = Color.WHITE
        ring.strokeWidth = 3.5f * dp
        c.drawCircle(x, y, rad, ring)
        ring.color = 0x66000000
        ring.strokeWidth = 1 * dp
        c.drawCircle(x, y, rad + 2 * dp, ring)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = when {
                    e.y <= svRect.bottom + gap / 2 -> 1
                    else -> 2
                }
                parent?.requestDisallowInterceptTouchEvent(true) // 拖的時候不要讓外面的頁面跟著捲
                update(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> update(e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = 0
        }
        return true
    }

    private fun update(x: Float, y: Float) {
        when (dragging) {
            1 -> {
                hsv[1] = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                hsv[2] = 1f - ((y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
            }
            2 -> hsv[0] = ((x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 359.9f
            else -> return
        }
        invalidate()
        onColorChanged?.invoke(color)
    }
}
