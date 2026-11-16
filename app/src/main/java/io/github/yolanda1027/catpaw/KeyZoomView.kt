package io.github.yolanda1027.catpaw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 主題工作室：把一顆鍵放大到整個畫面，在上面擺圖案。
 * - 點圖案＝選它；點空白＝取消選取
 * - 一指拖曳＝移動（按在別的圖案上拖，就換成拖那一個）
 * - 兩指＝放大縮小、旋轉
 *
 * 按鍵和圖案的畫法跟真正的鍵盤一模一樣（借 CatKeyboardView 來畫）。
 */
@SuppressLint("ViewConstructor")
class KeyZoomView(context: Context, private val kv: CatKeyboardView) : View(context) {

    var key: Key? = null
        set(v) { field = v; invalidate() }
    var selectedId: String? = null
        set(v) { field = v; invalidate() }

    /** 目前所有圖案（主題工作室提供） */
    var decals: () -> List<Decal> = { emptyList() }
    /** 點到圖案（null = 點到空白） */
    var onPick: ((Decal?) -> Unit)? = null
    /** 圖案被拖動了；done = 手指放開 */
    var onChange: ((Decal, Boolean) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /** 放大後的按鍵在畫面上的位置（四周留空間，圖案超出去也看得到） */
    private fun keyBox(): RectF? {
        val k = key ?: return null
        val src = kv.keyRectOf(k) ?: return null
        val availW = width * 0.62f
        val availH = (height - 40 * dp) * 0.7f
        val s = minOf(availW / src.width(), availH / src.height())
        val w = src.width() * s
        val h = src.height() * s
        val cx = width / 2f
        val cy = (height - 24 * dp) / 2f
        return RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    private fun mine(): List<Decal> {
        val id = key?.let { KeyStyles.idOf(it) } ?: return emptyList()
        return decals().filter { it.target == id }
    }

    override fun onDraw(c: Canvas) {
        val bg = kv.keyboardBg
        c.drawColor(bg or (0xFF shl 24))
        val k = key ?: return
        val box = keyBox() ?: return
        // 按鍵的範圍：淡淡的虛線（經典版這種沒有底框的主題才看得出鍵有多大）
        val light = isLight(bg)
        line.color = if (light) 0x40000000 else 0x50FFFFFF
        line.strokeWidth = 1.5f * dp
        line.pathEffect = DashPathEffect(floatArrayOf(6 * dp, 5 * dp), 0f)
        c.drawRect(box, line)

        kv.drawKeyZoomed(c, k, box)

        // 選到的圖案：藍色虛線框＋四個角
        val sel = mine().firstOrNull { it.id == selectedId }
        if (sel != null) {
            val (cx, cy) = Decals.centerOf(sel, box)
            val (w, h) = Decals.sizeOf(sel, box.height(), kv.decalBitmap(sel))
            c.save()
            c.translate(cx, cy)
            c.rotate(sel.rotation)
            val r = RectF(-w / 2 - 4 * dp, -h / 2 - 4 * dp, w / 2 + 4 * dp, h / 2 + 4 * dp)
            line.pathEffect = null
            line.color = Color.WHITE
            line.strokeWidth = 4f * dp
            c.drawRect(r, line)
            line.color = BLUE
            line.strokeWidth = 2f * dp
            line.pathEffect = DashPathEffect(floatArrayOf(6 * dp, 4 * dp), 0f)
            c.drawRect(r, line)
            for (px in listOf(r.left, r.right)) for (py in listOf(r.top, r.bottom)) {
                dot.color = Color.WHITE
                c.drawCircle(px, py, 7 * dp, dot)
                dot.color = BLUE
                c.drawCircle(px, py, 5 * dp, dot)
            }
            c.restore()
        }

        hint.color = if (light) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        hint.textSize = 13 * dp
        val tip = when {
            mine().isEmpty() -> "按右下角「＋ 加圖案」貼第一個圖案"
            sel == null -> "點一下圖案來選它，或直接拖著走"
            else -> "一指拖曳移動・兩指放大縮小、旋轉"
        }
        c.drawText(tip, width / 2f, height - 14 * dp, hint)
    }

    private fun isLight(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140

    // ---------- 手勢 ----------

    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var two = false
    private var subject: Decal? = null
    private var start: Decal? = null
    private var span0 = 1f
    private var angle0 = 0f
    private var cx0 = 0f
    private var cy0 = 0f

    private fun hitAt(x: Float, y: Float): Decal? {
        val box = keyBox() ?: return null
        return mine().lastOrNull { Decals.hit(it, box, box.height(), kv.decalBitmap(it), x, y, 14 * dp) }
    }

    private fun current(id: String?) = id?.let { i -> decals().firstOrNull { it.id == i } }

    private fun twoFinger(e: MotionEvent): FloatArray {
        val x0 = e.getX(0); val y0 = e.getY(0); val x1 = e.getX(1); val y1 = e.getY(1)
        return floatArrayOf(
            hypot(x1 - x0, y1 - y0).coerceAtLeast(1f),
            Math.toDegrees(atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat(),
            (x0 + x1) / 2, (y0 + y1) / 2,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val box = keyBox() ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x; downY = e.y
                moved = false
                two = false
                // 按在某個圖案上就拖那個；按在空白處就拖目前選到的
                subject = hitAt(e.x, e.y) ?: current(selectedId)
                start = subject
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) {
                val t = twoFinger(e)
                span0 = t[0]; angle0 = t[1]; cx0 = t[2]; cy0 = t[3]
                subject = current(subject?.id ?: selectedId)
                start = subject
                two = true
                moved = true
            }
            MotionEvent.ACTION_MOVE -> {
                val s = start ?: return true
                if (two && e.pointerCount >= 2) {
                    val t = twoFinger(e)
                    var rot = s.rotation + (t[1] - angle0)
                    val snap = Math.round(rot / 90f) * 90f
                    if (abs(rot - snap) < 4f) rot = snap
                    change(s.copy(
                        size = (s.size * t[0] / span0).coerceIn(0.03f, 5f),
                        rotation = rot,
                        x = (s.x + (t[2] - cx0) / box.width()).coerceIn(-1f, 2f),
                        y = (s.y + (t[3] - cy0) / box.height()).coerceIn(-1f, 2f),
                    ), false)
                } else if (!two) {
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (!moved && hypot(dx, dy) < 8 * dp) return true
                    moved = true
                    change(s.copy(
                        x = (s.x + dx / box.width()).coerceIn(-1f, 2f),
                        y = (s.y + dy / box.height()).coerceIn(-1f, 2f),
                    ), false)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                two = false
                start = current(subject?.id)
                val keep = if (e.actionIndex == 0) 1 else 0
                downX = e.getX(keep); downY = e.getY(keep)
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    onPick?.invoke(hitAt(e.x, e.y))
                } else {
                    val d = current(subject?.id)
                    if (d != null) {
                        onChange?.invoke(d, true)
                        if (d.id != selectedId) onPick?.invoke(d) // 拖了哪個就選哪個
                    }
                }
                subject = null
                start = null
            }
            MotionEvent.ACTION_CANCEL -> {
                current(subject?.id)?.let { if (moved) onChange?.invoke(it, true) }
                subject = null
                start = null
            }
        }
        return true
    }

    private fun change(d: Decal, done: Boolean) {
        subject = d
        onChange?.invoke(d, done)
        invalidate()
    }

    companion object {
        private val BLUE = Color.parseColor("#4F8BFF")
    }
}
