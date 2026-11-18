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
 * - 選到的東西外面有框、四個角有圓點、四邊中間有拉把、下面有一個旋轉把手、
 *   上面（旋轉把手的對面）有一個移動把手、再上面有一排小工具
 * - 拖中間或移動把手＝移動（圖案太小、手指會蓋住時用移動把手）；拖四個角＝等比例放大縮小；
 *   拖左右的拉把＝只改寬、上下的拉把＝只改高；拖旋轉把手＝旋轉；兩隻手指也可以縮放旋轉
 * - 按鍵上的字也用這一套（不能拉伸、小工具不一樣）
 * - 按在別的圖案上拖＝換成拖那一個
 *
 * 放大的按鍵（KeyZoomView）和整個鍵盤（CatKeyboardView 的背景圖案）共用這一份。
 */
class DecalHandles(private val dp: Float) {

    /** 圖案被改了；done = 手指放開 */
    var onChange: ((Decal, Boolean) -> Unit)? = null
    /** 選到某個圖案（null = 取消選取） */
    var onPick: ((Decal?) -> Unit)? = null
    /** 按了小工具（工具的代號見 Geo.tools） */
    var onAction: ((String, Decal) -> Unit)? = null
    /** 畫面上被別的按鈕蓋住的地方（工具列不要放在那裡，不然會按到別的按鈕）；呼叫的人依畫面大小給 */
    var blocked: (Float, Float) -> List<RectF> = { _, _ -> emptyList() }

    /** 點在空白的地方（沒點到圖案、把手、工具） */
    var onEmptyTap: ((Float, Float) -> Unit)? = null

    /**
     * 東西在畫面上的位置：貼的範圍、參考高度、圖片。
     * @param aspect 原本的高/寬比例（按鍵上的字用；圖案不用給）
     * @param tools 框上面的小工具（代號, 顯示的字）
     * @param stretchable 有沒有四邊的拉把（可以只改寬或只改高）
     */
    class Geo(
        val target: RectF, val ref: Float, val bmp: Bitmap?,
        val aspect: Float? = null,
        val tools: List<Pair<String, String>> = DECAL_TOOLS,
        val stretchable: Boolean = true,
    )

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
        val (w, h) = Decals.sizeOf(d, g.ref, g.bmp, g.aspect)
        return Frame(cx, cy, w / 2 + pad, h / 2 + pad, d.rotation)
    }

    /** 四邊中間的拉把：左、右、上、下 */
    private fun sides(f: Frame) = listOf(f.map(-f.hw, 0f), f.map(f.hw, 0f), f.map(0f, -f.hh), f.map(0f, f.hh))

    private fun corners(f: Frame) = listOf(
        f.map(-f.hw, -f.hh), f.map(f.hw, -f.hh), f.map(f.hw, f.hh), f.map(-f.hw, f.hh),
    )

    private fun rotHandle(f: Frame) = f.map(0f, f.hh + rotGap)

    /** 移動把手：在旋轉把手的對面（跟著圖案一起轉） */
    private fun moveHandle(f: Frame) = f.map(0f, -(f.hh + rotGap))

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
        // 旋轉把手、移動把手的連接線
        line.pathEffect = null
        line.strokeWidth = 1.5f * dp
        c.drawLine(0f, f.hh, 0f, f.hh + rotGap - 11 * dp, line)
        c.drawLine(0f, -f.hh, 0f, -(f.hh + rotGap - 11 * dp), line)
        // 四邊中間的拉把（扁扁的小膠囊，順著邊的方向）
        if (g.stretchable) {
            val lw = 15 * dp
            val lh = 6 * dp
            for ((x, y, horiz) in listOf(Triple(-f.hw, 0f, false), Triple(f.hw, 0f, false), Triple(0f, -f.hh, true), Triple(0f, f.hh, true))) {
                val r = if (horiz) RectF(x - lw / 2, y - lh / 2, x + lw / 2, y + lh / 2) else RectF(x - lh / 2, y - lw / 2, x + lh / 2, y + lw / 2)
                fill.color = 0x33000000
                c.drawRoundRect(RectF(r.left, r.top + 1 * dp, r.right, r.bottom + 1 * dp), lh / 2, lh / 2, fill)
                fill.color = Color.WHITE
                c.drawRoundRect(r, lh / 2, lh / 2, fill)
                line.color = BLUE
                line.strokeWidth = 1.8f * dp
                c.drawRoundRect(r, lh / 2, lh / 2, line)
            }
        }
        c.restore()

        // 四個角
        for ((x, y) in corners(f)) knob(c, x, y, 8.5f * dp)
        // 旋轉把手：圓圈裡一個繞一圈的箭頭
        val (rx, ry) = rotHandle(f)
        knob(c, rx, ry, 12 * dp)
        line.color = BLUE
        line.strokeWidth = 1.8f * dp
        val r = 5.5f * dp
        // 繞大半圈的弧線，尾端一個實心小三角形（順著繞的方向指出去）
        val startDeg = -40f
        val sweep = 250f
        c.drawArc(RectF(rx - r, ry - r, rx + r, ry + r), startDeg, sweep, false, line)
        val endA = Math.toRadians((startDeg + sweep).toDouble())
        val ex = rx + (r * cos(endA)).toFloat()
        val ey = ry + (r * sin(endA)).toFloat()
        // 順時針前進的方向（畫面座標 y 往下）＝ (-sin, cos)
        arrowHead(c, ex, ey, -sin(endA).toFloat(), cos(endA).toFloat(), 3.4f * dp)

        // 移動把手：圓圈裡一個上下左右的十字箭頭
        val (mx, my) = moveHandle(f)
        knob(c, mx, my, 12 * dp)
        c.save()
        c.translate(mx, my)
        c.rotate(f.rot)
        line.color = BLUE
        line.strokeWidth = 1.8f * dp
        val arm = 4.2f * dp
        c.drawLine(-arm, 0f, arm, 0f, line)
        c.drawLine(0f, -arm, 0f, arm, line)
        // 上下左右四個實心小三角形
        for ((ux, uy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) arrowHead(c, ux * arm, uy * arm, ux, uy, 3f * dp)
        c.restore()
        drawToolbar(c, f, g.tools, viewW, viewH)
    }

    /** 實心小三角形箭頭：底邊中心在 (x, y)，往 (ux, uy) 方向指，長度 size */
    private fun arrowHead(c: Canvas, x: Float, y: Float, ux: Float, uy: Float, size: Float) {
        val len = hypot(ux, uy).coerceAtLeast(0.0001f)
        val dx = ux / len
        val dy = uy / len
        val half = size * 0.75f
        val p = android.graphics.Path().apply {
            moveTo(x + dx * size, y + dy * size) // 尖端
            lineTo(x - dy * half, y + dx * half)
            lineTo(x + dy * half, y - dx * half)
            close()
        }
        fill.color = BLUE
        c.drawPath(p, fill)
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
    private fun drawToolbar(c: Canvas, f: Frame, tools: List<Pair<String, String>>, viewW: Float, viewH: Float) {
        val pts = corners(f) + listOf(rotHandle(f), moveHandle(f))
        val top = pts.minOf { it.second }
        val bottom = pts.maxOf { it.second }
        val bw = 46 * dp
        val bh = 34 * dp
        val w = bw * tools.size
        val x0 = (f.cx - w / 2).coerceIn(4 * dp, (viewW - w - 4 * dp).coerceAtLeast(4 * dp))
        val blockedRects = blocked(viewW, viewH)
        /** 在 yy 這個高度放得下的話，回傳 x（會蓋到按鈕就往旁邊挪一點）；放不下回傳 null */
        fun place(yy: Float): Float? {
            if (yy < 2 * dp || yy + bh > viewH - 2 * dp) return null
            var xx = x0
            for (b in blockedRects) {
                if (!RectF.intersects(b, RectF(xx, yy, xx + w, yy + bh))) continue
                xx = when {
                    b.right + 4 * dp + w <= viewW - 4 * dp -> b.right + 4 * dp
                    b.left - 4 * dp - w >= 4 * dp -> b.left - 4 * dp - w
                    else -> return null
                }
            }
            return xx.takeIf { nx -> blockedRects.none { RectF.intersects(it, RectF(nx, yy, nx + w, yy + bh)) } }
        }
        // 先放框的上面；放不下或會蓋到按鈕（「← 整個鍵盤」「＋ 加圖案」）就挪一挪或改放下面
        val above = top - 14 * dp - bh
        val below = bottom + 14 * dp
        val pa = place(above)
        val pb = if (pa == null) place(below) else null
        val y = when {
            pa != null -> above
            pb != null -> below
            else -> above.coerceIn(2 * dp, (viewH - bh - 2 * dp).coerceAtLeast(2 * dp))
        }
        val x = pa ?: pb ?: x0
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

    private enum class Mode { NONE, TOOL, MOVE, SCALE, SIDE_X, SIDE_Y, ROTATE, PINCH }

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
                        // 移動把手：圖案太小、手指會蓋住整個圖案時用這個拖
                        moveHandle(f).let { (x, y) -> hypot(e.x - x, e.y - y) < grab } -> mode = Mode.MOVE
                        corners(f).any { (x, y) -> hypot(e.x - x, e.y - y) < grab } -> {
                            mode = Mode.SCALE
                            dist0 = hypot(e.x - f.cx, e.y - f.cy).coerceAtLeast(1f)
                        }
                        g.stretchable && sides(f).withIndex().any { (_, p) -> hypot(e.x - p.first, e.y - p.second) < grab } -> {
                            val i = sides(f).indexOfFirst { (x, y) -> hypot(e.x - x, e.y - y) < grab }
                            mode = if (i < 2) Mode.SIDE_X else Mode.SIDE_Y
                        }
                        rotHandle(f).let { (x, y) -> hypot(e.x - x, e.y - y) < grab } -> {
                            mode = Mode.ROTATE
                            angle0 = angle(f.cx, f.cy, e.x, e.y)
                        }
                        Decals.hit(sel, g.target, g.ref, g.bmp, e.x, e.y, 12 * dp, g.aspect) -> mode = Mode.MOVE
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
                    Mode.SIDE_X, Mode.SIDE_Y -> {
                        // 手指的位置換成圖案自己的座標（扣掉旋轉），以中心為準拉寬或拉高
                        val a = Math.toRadians(-f.rot.toDouble())
                        val dx = e.x - f.cx
                        val dy = e.y - f.cy
                        val lx = abs((dx * cos(a) - dy * sin(a)).toFloat())
                        val ly = abs((dx * sin(a) + dy * cos(a)).toFloat())
                        val (w, h) = Decals.sizeOf(s, g.ref, g.bmp, g.aspect)
                        if (mode == Mode.SIDE_X) {
                            val nw = (2 * (lx - pad)).coerceAtLeast(8 * dp)
                            // 只改寬：高度不變，所以拉伸倍率反過來補
                            s.copy(size = (nw / g.ref).coerceIn(0.03f, 5f), stretch = (s.stretch * w / nw).coerceIn(0.1f, 10f))
                        } else {
                            val nh = (2 * (ly - pad)).coerceAtLeast(8 * dp)
                            s.copy(stretch = (s.stretch * nh / h).coerceIn(0.1f, 10f))
                        }
                    }
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
        val DECAL_TOOLS = listOf("copy" to "複製", "flip" to "翻轉", "up" to "上層", "down" to "下層", "delete" to "刪除")
        val LABEL_TOOLS = listOf("color" to "顏色", "up" to "上層", "down" to "下層", "reset" to "重設")
    }
}
