package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 像 Canva 那樣編輯圖案：
 * - 選到的圖案外面有框、四個角有圓點、下面有一個旋轉把手、上面有一排小工具（複製、翻轉、刪除）
 * - 拖中間＝移動；拖四個角＝等比例放大縮小；拖旋轉把手＝旋轉；兩隻手指也可以縮放旋轉
 * - 按在別的圖案上拖＝換成拖那一個
 *
 * 放大的按鍵（KeyZoomView）和整個鍵盤（CatKeyboardView 的背景圖案）共用這一份。
 */
class DecalHandles(private val dp: Float) {

    /** 圖案被改了；done = 手指放開 */
    var onChange: ((Decal, Boolean) -> Unit)? = null
    /** 選到某個圖案（null = 取消選取） */
    var onPick: ((Decal?) -> Unit)? = null
    /** 按了小工具：copy / flip / delete */
    var onAction: ((String, Decal) -> Unit)? = null
    /** 點在空白的地方（沒點到圖案、把手、工具） */
    var onEmptyTap: ((Float, Float) -> Unit)? = null

    /** 圖案在畫面上的位置：貼的範圍、參考高度、圖片 */
    class Geo(val target: RectF, val ref: Float, val bmp: Bitmap?)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val pad get() = 5 * dp
    private val rotGap get() = 30 * dp

    // 上次畫出來的小工具位置（點擊判斷用）
    private val toolRects = ArrayList<Pair<String, RectF>>()

    // ---------- 幾何 ----------

    private class Frame(val cx: Float, val cy: Float, val hw: Float, val hh: Float, val rot: Float) {
        /** 圖案自己座標（沒旋轉前）的點 → 畫面上的點 */
        fun map(lx: Float, ly: Float): Pair<Float, Float> {
            val a = Math.toRadians(rot.toDouble())
            return (cx + lx * cos(a) - ly * sin(a)).toFloat() to (cy + lx * sin(a) + ly * cos(a)).toFloat()
        }
    }

    private fun frame(d: Decal, g: Geo): Frame {
        val (cx, cy) = Decals.centerOf(d, g.target)
        val (w, h) = Decals.sizeOf(d, g.ref, g.bmp)
        return Frame(cx, cy, w / 2 + pad, h / 2 + pad, d.rotation)
    }

    private fun corners(f: Frame) = listOf(
        f.map(-f.hw, -f.hh), f.map(f.hw, -f.hh), f.map(f.hw, f.hh), f.map(-f.hw, f.hh),
    )

    private fun rotHandle(f: Frame) = f.map(0f, f.hh + rotGap)

    // ---------- 畫 ----------

    fun draw(c: Canvas, d: Decal, g: Geo, viewW: Float, viewH: Float) {
        val f = frame(d, g)
        // 框：白色底線＋藍色虛線
        c.save()
        c.translate(f.cx, f.cy)
        c.rotate(f.rot)
        val box = RectF(-f.hw, -f.hh, f.hw, f.hh)
        line.pathEffect = null
        line.color = Color.WHITE
        line.strokeWidth = 4f * dp
        c.drawRect(box, line)
        line.color = BLUE
        line.strokeWidth = 2f * dp
        line.pathEffect = DashPathEffect(floatArrayOf(6 * dp, 4 * dp), 0f)
        c.drawRect(box, line)
        // 旋轉把手的連接線
        line.pathEffect = null
        line.strokeWidth = 1.5f * dp
        c.drawLine(0f, f.hh, 0f, f.hh + rotGap - 11 * dp, line)
        c.restore()

        // 四個角
        for ((x, y) in corners(f)) knob(c, x, y, 8.5f * dp)
        // 旋轉把手：圓圈裡一個繞一圈的箭頭
        val (rx, ry) = rotHandle(f)
        knob(c, rx, ry, 12 * dp)
        line.color = BLUE
        line.strokeWidth = 1.8f * dp
        val r = 5.5f * dp
        c.drawArc(RectF(rx - r, ry - r, rx + r, ry + r), -60f, 280f, false, line)
        val tipA = Math.toRadians(-60.0)
        val tx = rx + (r * cos(tipA)).toFloat()
        val ty = ry + (r * sin(tipA)).toFloat()
        c.drawLine(tx, ty, tx - 3.5f * dp, ty - 0.5f * dp, line)
        c.drawLine(tx, ty, tx + 0.5f * dp, ty + 3.5f * dp, line)

        drawToolbar(c, f, viewW, viewH)
    }

    private fun knob(c: Canvas, x: Float, y: Float, rad: Float) {
        fill.color = 0x33000000
        c.drawCircle(x, y + 1 * dp, rad + 1 * dp, fill)
        fill.color = Color.WHITE
        c.drawCircle(x, y, rad, fill)
        line.pathEffect = null
        line.color = BLUE
        line.strokeWidth = 2f * dp
        c.drawCircle(x, y, rad, line)
    }

    /** 框上面的一排小工具（上面放不下就放到下面） */
    private fun drawToolbar(c: Canvas, f: Frame, viewW: Float, viewH: Float) {
        val pts = corners(f) + listOf(rotHandle(f))
        val top = pts.minOf { it.second }
        val bottom = pts.maxOf { it.second }
        val tools = listOf("copy" to "複製", "flip" to "翻轉", "delete" to "刪除")
        val bw = 54 * dp
        val bh = 34 * dp
        val w = bw * tools.size
        var y = top - 14 * dp - bh
        if (y < 2 * dp) y = bottom + 14 * dp
        if (y + bh > viewH - 2 * dp) y = (viewH - bh - 2 * dp).coerceAtLeast(2 * dp)
        val x = (f.cx - w / 2).coerceIn(4 * dp, (viewW - w - 4 * dp).coerceAtLeast(4 * dp))
        val bar = RectF(x, y, x + w, y + bh)
        fill.color = 0x30000000
        c.drawRoundRect(RectF(bar.left, bar.top + 2 * dp, bar.right, bar.bottom + 2 * dp), bh / 2, bh / 2, fill)
        fill.color = Color.WHITE
        c.drawRoundRect(bar, bh / 2, bh / 2, fill)
        toolRects.clear()
        label.textSize = 13.5f * dp
        tools.forEachIndexed { i, (id, text) ->
            val r = RectF(x + bw * i, y, x + bw * (i + 1), y + bh)
            toolRects.add(id to r)
            if (i > 0) {
                line.color = 0x22000000
                line.strokeWidth = 1 * dp
                line.pathEffect = null
                c.drawLine(r.left, r.top + 8 * dp, r.left, r.bottom - 8 * dp, line)
            }
            label.color = if (id == "delete") Color.parseColor("#E5484D") else Color.parseColor("#33363D")
            val fm = label.fontMetrics
            c.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, label)
        }
    }

    // ---------- 手勢 ----------

    private enum class Mode { NONE, TOOL, MOVE, SCALE, ROTATE, PINCH }

    private var mode = Mode.NONE
    private var tool: String? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var start: Decal? = null
    private var subjectId: String? = null
    private var dist0 = 1f
    private var angle0 = 0f
    private var span0 = 1f
    private var pinchAngle0 = 0f
    private var pcx0 = 0f
    private var pcy0 = 0f

    /**
     * @param selected 目前選到的圖案
     * @param geo 圖案在畫面上的位置（null = 不在畫面上）
     * @param hitAt 這個點上最上面的圖案（沒有就 null）
     * @param current 用 id 拿最新的圖案（拖的時候會一直變）
     */
    fun onTouch(
        e: MotionEvent,
        selected: Decal?,
        geo: (Decal) -> Geo?,
        hitAt: (Float, Float) -> Decal?,
        current: (String) -> Decal?,
    ) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                moved = false
                tool = null
                start = null
                subjectId = null
                mode = Mode.NONE
                val sel = selected
                val g = sel?.let(geo)
                if (sel != null && g != null) {
                    val f = frame(sel, g)
                    val grab = 22 * dp
                    tool = toolRects.firstOrNull { it.second.contains(e.x, e.y) }?.first
                    when {
                        tool != null -> mode = Mode.TOOL
                        corners(f).any { (x, y) -> hypot(e.x - x, e.y - y) < grab } -> {
                            mode = Mode.SCALE
                            dist0 = hypot(e.x - f.cx, e.y - f.cy).coerceAtLeast(1f)
                        }
                        rotHandle(f).let { (x, y) -> hypot(e.x - x, e.y - y) < grab } -> {
                            mode = Mode.ROTATE
                            angle0 = angle(f.cx, f.cy, e.x, e.y)
                        }
                        Decals.hit(sel, g.target, g.ref, g.bmp, e.x, e.y, 12 * dp) -> mode = Mode.MOVE
                    }
                    if (mode != Mode.NONE && mode != Mode.TOOL) { start = sel; subjectId = sel.id }
                }
                if (mode == Mode.NONE) {
                    // 按在別的圖案上：拖那一個
                    hitAt(e.x, e.y)?.let { other ->
                        mode = Mode.MOVE
                        start = other
                        subjectId = other.id
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) {
                val s = subjectId?.let(current) ?: selected ?: return
                start = s
                subjectId = s.id
                mode = Mode.PINCH
                moved = true
                span0 = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)).coerceAtLeast(1f)
                pinchAngle0 = angle(e.getX(0), e.getY(0), e.getX(1), e.getY(1))
                pcx0 = (e.getX(0) + e.getX(1)) / 2
                pcy0 = (e.getY(0) + e.getY(1)) / 2
            }
            MotionEvent.ACTION_MOVE -> {
                val s = start ?: return
                val g = geo(s) ?: return
                if (!moved && mode != Mode.PINCH && hypot(e.x - downX, e.y - downY) < 6 * dp) return
                if (mode == Mode.TOOL || mode == Mode.NONE) return
                moved = true
                val f = frame(s, g)
                val d = when (mode) {
                    Mode.MOVE -> s.copy(
                        x = (s.x + (e.x - downX) / g.target.width()).coerceIn(-1f, 2f),
                        y = (s.y + (e.y - downY) / g.target.height()).coerceIn(-1f, 2f),
                    )
                    Mode.SCALE -> s.copy(size = (s.size * hypot(e.x - f.cx, e.y - f.cy) / dist0).coerceIn(0.03f, 5f))
                    Mode.ROTATE -> s.copy(rotation = snap(s.rotation + angle(f.cx, f.cy, e.x, e.y) - angle0))
                    Mode.PINCH -> {
                        if (e.pointerCount < 2) return
                        val span = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)).coerceAtLeast(1f)
                        val a = angle(e.getX(0), e.getY(0), e.getX(1), e.getY(1))
                        s.copy(
                            size = (s.size * span / span0).coerceIn(0.03f, 5f),
                            rotation = snap(s.rotation + a - pinchAngle0),
                            x = (s.x + ((e.getX(0) + e.getX(1)) / 2 - pcx0) / g.target.width()).coerceIn(-1f, 2f),
                            y = (s.y + ((e.getY(0) + e.getY(1)) / 2 - pcy0) / g.target.height()).coerceIn(-1f, 2f),
                        )
                    }
                    else -> return
                }
                onChange?.invoke(d, false)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 兩指變一指：用剩下那隻手指接著移動
                start = subjectId?.let(current)
                mode = Mode.MOVE
                val keep = if (e.actionIndex == 0) 1 else 0
                downX = e.getX(keep); downY = e.getY(keep)
            }
            MotionEvent.ACTION_UP -> {
                val sel = selected
                when {
                    mode == Mode.TOOL -> {
                        val t = tool
                        val stillOn = toolRects.firstOrNull { it.first == t }?.second?.contains(e.x, e.y) == true
                        if (t != null && stillOn && sel != null) onAction?.invoke(t, sel)
                    }
                    moved -> {
                        val d = subjectId?.let(current)
                        if (d != null) {
                            onChange?.invoke(d, true)
                            if (d.id != sel?.id) onPick?.invoke(d) // 拖了哪個就選哪個
                        }
                    }
                    mode == Mode.NONE -> onEmptyTap?.invoke(e.x, e.y)
                    subjectId != sel?.id -> onPick?.invoke(subjectId?.let(current))
                }
                mode = Mode.NONE
                start = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (moved) subjectId?.let(current)?.let { onChange?.invoke(it, true) }
                mode = Mode.NONE
                start = null
            }
        }
    }

    private fun angle(x0: Float, y0: Float, x1: Float, y1: Float) =
        Math.toDegrees(atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()

    /** 接近水平／垂直時吸附一下，比較好擺正 */
    private fun snap(rot: Float): Float {
        val s = Math.round(rot / 90f) * 90f
        return if (abs(rot - s) < 4f) s else rot
    }

    companion object {
        val BLUE = Color.parseColor("#4F8BFF")
    }
}
