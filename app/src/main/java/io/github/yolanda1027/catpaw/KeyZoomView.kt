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

/**
 * 主題工作室：把一顆鍵放大到整個畫面，在上面擺圖案。
 * 選到的圖案用 Canva 那種方式編輯（見 DecalHandles）：拖中間移動、拖四角縮放、
 * 下面的把手旋轉、上面的小工具複製／翻轉／刪除。點空白取消選取。
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

    private val dp = resources.displayMetrics.density
    val handles = DecalHandles(dp)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        handles.onEmptyTap = { _, _ -> handles.onPick?.invoke(null) }
    }

    /**
     * 自己不決定高度：跟著底下的鍵盤一樣高（外面的 FrameLayout 會用鍵盤的高度再量一次）。
     * 不這樣做的話，它會把整個畫面撐滿，下面的面板就被擠不見了。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    /** 放大後的按鍵在畫面上的位置（四周留空間，圖案超出去、編輯把手也放得下） */
    private fun keyBox(): RectF? {
        val k = key ?: return null
        val src = kv.keyRectOf(k) ?: return null
        val availW = width * 0.6f
        val availH = (height - 40 * dp) * 0.6f
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

    private fun geo(d: Decal): DecalHandles.Geo? = keyBox()?.let { DecalHandles.Geo(it, it.height(), kv.decalBitmap(d)) }

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
        if (kv.effectsActive()) postInvalidateOnAnimation()

        val sel = mine().firstOrNull { it.id == selectedId }
        if (sel != null) geo(sel)?.let { handles.draw(c, sel, it, width.toFloat(), height.toFloat()) }

        hint.color = if (light) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        hint.textSize = 13 * dp
        val tip = when {
            mine().isEmpty() -> "按右下角「＋ 加圖案」貼第一個圖案"
            sel == null -> "點一下圖案來選它，或直接拖著走"
            else -> "拖中間移動・拖四角縮放・拖下面的圓圈旋轉"
        }
        c.drawText(tip, width / 2f, height - 14 * dp, hint)
    }

    private fun isLight(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        val box = keyBox() ?: return true
        handles.onTouch(
            e,
            selected = mine().firstOrNull { it.id == selectedId },
            geo = { geo(it) },
            hitAt = { x, y -> mine().lastOrNull { Decals.hit(it, box, box.height(), kv.decalBitmap(it), x, y, 14 * dp) } },
            current = { id -> decals().firstOrNull { it.id == id } },
        )
        invalidate()
        return true
    }
}
