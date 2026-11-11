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

    private val dp = resources.displayMetrics.density
    private var theme: KbTheme = Themes.byName(prefs.theme, dp)
    private var keyH = prefs.keyHeightDp * dp
    // 這些比例是照 Google 注音截圖量的：上方列 40dp，按鍵區上面留 6dp、下面留 10dp
    private val topH = 40 * dp
    private val padTop = 6 * dp
    private val padBottom = 10 * dp
    private val keyRects = ArrayList<KeyRect>()

    private var pressed: KeyRect? = null
    private var pressedPointer = -1
    private var longFired = false
    private var previewLabel: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        val kr = pressed ?: return@Runnable
        longFired = true
        haptics.perform(this, prefs)
        kr.key.sub?.let { previewLabel = it }
        listener?.onKey(kr.key, true)
        invalidate()
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
        val h = (topH + padTop + keyH * rows.size + padBottom).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        keyRects.clear()
        val w = width.toFloat()
        if (w <= 0f) return
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = w / total
            val top = topH + padTop + ri * keyH
            var x = 0f
            for (k in row) {
                val kw = unit * k.weight
                keyRects.add(KeyRect(k, RectF(x, top, x + kw, top + keyH)))
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
        theme.drawBackground(c, w, height.toFloat(), topH)
        drawTopBar(c, w)
        for (kr in keyRects) {
            if (kr.key.act == Act.GAP) continue
            theme.drawKey(c, kr.rect, kr.key, labelFor(kr.key), kr === pressed, shiftState, keyH)
        }
        drawPreview(c, w)
    }

    private val tabW get() = width * 0.2f

    private fun drawTopBar(c: Canvas, w: Float) {
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
        val kr = pressed ?: return
        if (!prefs.preview) return
        val label = previewLabel ?: return
        val r = kr.rect
        val bw = max(r.width() * 1.2f, keyH * 0.95f)
        val bh = keyH * 1.25f
        var left = r.centerX() - bw / 2
        left = min(max(0f, left), w - bw)
        val bottom = r.top + keyH * 0.15f
        val top = max(0f, bottom - bh)
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
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 兩隻手指快速輪流打：前一顆還沒放開就先送出
                if (pressed != null) release(fire = !longFired)
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                if (y < topH) {
                    onTopBarTap(x)
                } else {
                    val firstTop = topH + padTop
                    val lastBottom = firstTop + keyH * rows.size - 1f
                    keyAt(x, y.coerceIn(firstTop, lastBottom))?.let { press(it, e.getPointerId(i)) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == pressedPointer) release(fire = !longFired)
            }
            MotionEvent.ACTION_CANCEL -> release(fire = false)
        }
        return true
    }

    private fun onTopBarTap(x: Float) {
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

    private fun release(fire: Boolean) {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        val kr = pressed
        pressed = null
        pressedPointer = -1
        previewLabel = null
        invalidate()
        if (fire && kr != null) listener?.onKey(kr.key, false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
    }
}
