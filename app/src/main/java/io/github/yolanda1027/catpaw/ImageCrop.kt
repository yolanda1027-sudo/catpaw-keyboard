package io.github.yolanda1027.catpaw

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.exp

/**
 * 裁切畫面（按鍵底圖、鍵盤背景圖共用）：
 * 中間一個跟按鍵（或鍵盤）一樣比例的框，框外面暗掉＝會被裁掉的部分。
 * 一根手指拖＝移動圖片，兩根手指捏＝放大縮小，下面的滑桿也可以縮放。
 */
object ImageCrop {
    /** 拉寬、拉高最多到原本的幾倍（往左最小到幾分之一） */
    private const val REL_MAX = 4f

    /**
     * @param aspect 框的寬 ÷ 高
     * @param corner 框的圓角（框短邊的幾分之幾，0 = 直角、0.5 = 膠囊）
     * @param done 按「完成」回傳新的設定；取消回傳 null
     */
    fun show(a: Activity, bmp: Bitmap, title: String, aspect: Float, corner: Float, initial: ImageFill, done: (ImageFill?) -> Unit): Dialog {
        // 照圖片形狀的按鍵：框裡畫灰白格子，看得出哪裡是透明的
        val showAlpha = initial.shape
        val dp = a.resources.displayMetrics.density
        val d = Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1B1A.toInt())
        }
        fun label(s: String, size: Float, alpha: Float = 1f) = TextView(a).apply {
            text = s
            setTextColor(Color.WHITE)
            textSize = size
            this.alpha = alpha
            gravity = Gravity.CENTER
            val p = (6 * dp).toInt()
            setPadding(p * 2, p, p * 2, p)
        }
        root.addView(label(title, 17f).apply { setPadding(paddingLeft, (14 * dp).toInt(), paddingRight, 0) })
        root.addView(label("一根手指拖＝移動　兩根手指捏＝放大縮小\n框外面暗掉的地方會被裁掉", 13f, 0.75f))
        val view = CropView(a, bmp, aspect, corner, initial, showAlpha)
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // 縮放滑桿（對數刻度：小的時候調得細）
        val zoomRow = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), 0, (14 * dp).toInt(), 0)
        }
        // 左右兩邊的符號固定一樣寬：三條拉桿才會上下對齊
        val iconW = (36 * dp).toInt()
        fun icon(row: LinearLayout, s: String, size: Float) =
            row.addView(label(s, size).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(iconW, ViewGroup.LayoutParams.WRAP_CONTENT))
        icon(zoomRow, "－", 20f)
        val bar = SeekBar(a).apply { max = 1000 }
        zoomRow.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        icon(zoomRow, "＋", 20f)
        root.addView(zoomRow)
        fun barOf(z: Float): Int {
            val lo = ln(view.minZoom)
            val hi = ln(ImageFill.MAX_ZOOM)
            return (((ln(z) - lo) / (hi - lo)) * 1000).toInt().coerceIn(0, 1000)
        }
        var syncing = false

        // 拉寬、拉高分開調（對數刻度：中間＝原本的大小，往右變寬／變高，往左變窄／變矮）
        val lnMax = ln(REL_MAX)
        fun relBar(p: Int) = exp((p / 500f - 1f) * lnMax).let { if (kotlin.math.abs(it - 1f) < 0.03f) 1f else it }
        fun barRel(v: Float) = (((ln(v) / lnMax) + 1f) * 500).toInt().coerceIn(0, 1000)
        fun relText(name: String, v: Float) = if (kotlin.math.abs(v - 1f) < 0.02f) "$name：原本" else "$name：×%.2f".format(v)
        fun relRow(icon: String): Pair<TextView, SeekBar> {
            val lbl = label("", 13f, 0.85f).apply { gravity = Gravity.START; setPadding((22 * dp).toInt(), (4 * dp).toInt(), 0, 0) }
            root.addView(lbl)
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), 0, (14 * dp).toInt(), 0)
            }
            icon(row, icon, 18f)
            val sb = SeekBar(a).apply { max = 1000 }
            row.addView(sb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            icon(row, "", 18f) // 右邊空一格，跟上面「＋」那條一樣長
            root.addView(row)
            return lbl to sb
        }
        val (wLabel, wBar) = relRow("↔")
        val (hLabel, hBar) = relRow("↕")
        fun sync(f: ImageFill) {
            syncing = true
            bar.progress = barOf(f.zoom)
            val (w, h) = view.sizeRel()
            wBar.progress = barRel(w)
            hBar.progress = barRel(h)
            wLabel.text = relText("拉寬", w)
            hLabel.text = relText("拉高", h)
            syncing = false
        }
        // 拉一條的時候另一條不動（高度維持、只改寬度；反過來也一樣）
        wBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (syncing || !fromUser) return
                view.setSizeRel(relBar(p), view.sizeRel().second)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        hBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (syncing || !fromUser) return
                view.setSizeRel(view.sizeRel().first, relBar(p))
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val quick = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
        }
        fun qbtn(s: String, onClick: () -> Unit) = quick.addView(Button(a).apply {
            text = s
            isAllCaps = false
            textSize = 13f
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = (4 * dp).toInt()
            setMargins(m, 0, m, 0)
        })
        qbtn("原比例") { view.setStretch(1f) }
        // 「剛好塞滿框」（會拉寬、壓扁）改成另一種放法「整張都要看到」；這裡是不變形的照片模式
        qbtn("整張放進框") { view.setZoom(view.minZoom) }
        root.addView(quick)

        view.onChange = { f -> sync(f) }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (syncing || !fromUser) return
                val lo = ln(view.minZoom)
                val hi = ln(ImageFill.MAX_ZOOM)
                view.setZoom(exp(lo + (hi - lo) * p / 1000f))
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        view.post { sync(view.fill) }

        val btns = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = (12 * dp).toInt()
            setPadding(p, p / 2, p, p)
        }
        var result: ImageFill? = null
        fun btn(s: String, onClick: () -> Unit) = btns.addView(Button(a).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = (4 * dp).toInt()
            setMargins(m, m, m, m)
        })
        btn("重設") { view.reset() }
        btn("取消") { d.dismiss() }
        btn("完成") { result = view.fill; d.dismiss() }
        root.addView(btns)

        Ui.fitSystemBars(root)
        d.setContentView(root)
        d.setOnDismissListener { done(result) }
        d.show()
        return d
    }
}

/** 裁切的框＋圖片；[fill] 就是現在的設定（已經整理過，不會拖出圖片外面） */
@SuppressLint("ViewConstructor")
class CropView(
    ctx: Context, val bmp: Bitmap, private val aspect: Float, private val corner: Float, initial: ImageFill,
    private val showAlpha: Boolean = false,
) : View(ctx) {
    var fill: ImageFill = initial
        private set
    var onChange: ((ImageFill) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    /** 框（畫面座標） */
    val frame = RectF()
    private val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint().apply { color = 0xB0000000.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x66FFFFFF }
    private val outside = Path()

    val minZoom: Float get() = fill.minZoomFor(frame, bmp.width, bmp.height)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 24 * dp
        val aw = w - pad * 2
        val ah = h - pad * 2
        val fw: Float
        val fh: Float
        if (aw / ah > aspect) { fh = ah; fw = ah * aspect } else { fw = aw; fh = aw / aspect }
        frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)
        set(fill)
    }

    private fun set(f: ImageFill) {
        fill = if (frame.isEmpty) f else f.normalized(frame, bmp.width, bmp.height)
        onChange?.invoke(fill)
        invalidate()
    }

    fun setZoom(z: Float) = set(fill.copy(zoom = z))

    /** 重設：一般是「剛好鋪滿」；照圖片形狀的是「整張放進框裡」（毛邊才不會被裁掉）；拉寬拉高回到原比例 */
    fun reset() = set(fill.copy(zoom = if (fill.shape) ImageFill.FIT else 1f, cx = 0.5f, cy = 0.5f, stretch = 1f))

    /** 只拉寬或只拉高（倍數＝寬度乘上多少） */
    fun setStretch(st: Float) = set(fill.copy(stretch = st.coerceIn(ImageFill.MIN_STRETCH, ImageFill.MAX_STRETCH)))

    /** 剛好塞滿框：拉到跟框一樣的比例，整張圖都看得到 */
    fun fitFrame() = set(fill.fitContent(frame, bmp.width, bmp.height, contentBounds))

    /** 圖片裡有畫東西（不透明）的範圍；整張都不透明就是整張 */
    private val contentBounds: RectF by lazy { opaqueBounds(bmp) }

    /** 原本的樣子（原比例、剛好鋪滿框）在畫面上的寬高：「拉寬 ×1」「拉高 ×1」的基準 */
    private fun baseSize(): Pair<Float, Float> {
        val c = ImageFill.cover(frame, bmp.width, bmp.height)
        return bmp.width * c to bmp.height * c
    }

    /** 現在畫面上的寬、高是原本的幾倍 */
    fun sizeRel(): Pair<Float, Float> {
        if (frame.isEmpty) return 1f to 1f
        val d = fill.destRect(frame, bmp.width, bmp.height)
        val (bw, bh) = baseSize()
        return d.width() / bw to d.height() / bh
    }

    /**
     * 寬、高各自調：畫面上的寬度＝原本的 w 倍、高度＝原本的 h 倍。
     * （換算成 ImageFill 的比例 stretch 和大小 zoom；超出範圍會被拉回來）
     */
    fun setSizeRel(w: Float, h: Float) {
        if (frame.isEmpty) return
        val (bw, bh) = baseSize()
        val targetW = bw * w
        val targetH = bh * h
        val st = ((targetW / targetH) / (bmp.width.toFloat() / bmp.height)).coerceIn(ImageFill.MIN_STRETCH, ImageFill.MAX_STRETCH)
        val c = ImageFill.cover(frame, bmp.width * st, bmp.height.toFloat())
        val z = targetH / (bmp.height * c)
        set(fill.copy(stretch = st, zoom = z))
    }

    private val checker = Paint()

    /** 拖：圖片跟著手指走（dx, dy 是畫面上的像素） */
    fun pan(dx: Float, dy: Float) {
        val dst = fill.destRect(frame, bmp.width, bmp.height)
        set(fill.copy(cx = fill.cx - dx / dst.width(), cy = fill.cy - dy / dst.height()))
    }

    /** 捏：以兩指中間為中心放大縮小（手指下面的那一點不會跑掉） */
    fun pinch(factor: Float, fx: Float, fy: Float) {
        val before = fill.destRect(frame, bmp.width, bmp.height)
        // 手指下面是圖片的哪一點
        val u = (fx - before.left) / before.width()
        val v = (fy - before.top) / before.height()
        val z = (fill.zoom * factor).coerceIn(minZoom, ImageFill.MAX_ZOOM)
        val w = before.width() * z / fill.zoom
        val h = before.height() * z / fill.zoom
        // 新的中心：讓 (u, v) 還是在手指下面
        val cx = u - (fx - frame.centerX()) / w
        val cy = v - (fy - frame.centerY()) / h
        set(fill.copy(zoom = z, cx = cx, cy = cy))
    }

    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        // 用所有手指的中心點，加手指放開時也不會跳
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { sx += e.getX(i); sy += e.getY(i); n++ }
        if (n == 0) return true
        val cx = sx / n
        val cy = sy / n
        var span = 0f
        if (n >= 2) {
            for (i in 0 until e.pointerCount) if (i != skip) span += hypot(e.getX(i) - cx, e.getY(i) - cy)
            span /= n
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> Unit
            MotionEvent.ACTION_MOVE -> {
                if (n >= 2 && lastSpan > 0f && span > 0f) pinch(span / lastSpan, cx, cy)
                pan(cx - lastX, cy - lastY)
            }
        }
        lastX = cx
        lastY = cy
        lastSpan = span
        return true
    }

    override fun onDraw(c: Canvas) {
        if (frame.isEmpty) return
        if (showAlpha) {
            // 灰白格子（透明的地方）
            val s = 10 * dp
            c.save()
            c.clipRect(frame)
            var y = frame.top
            var row = 0
            while (y < frame.bottom) {
                var x = frame.left
                var col = row % 2
                while (x < frame.right) {
                    checker.color = if (col % 2 == 0) 0xFFE9E6EE.toInt() else 0xFFFFFFFF.toInt()
                    c.drawRect(x, y, x + s, y + s, checker)
                    x += s
                    col++
                }
                y += s
                row++
            }
            c.restore()
        }
        c.drawBitmap(bmp, null, fill.destRect(frame, bmp.width, bmp.height), imgPaint)
        val rad = corner * minOf(frame.width(), frame.height())
        // 框外面暗掉
        outside.reset()
        outside.fillType = Path.FillType.EVEN_ODD
        outside.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        outside.addRoundRect(frame, rad, rad, Path.Direction.CW)
        c.drawPath(outside, shade)
        // 九宮格參考線（對齊用）
        guide.strokeWidth = 1 * dp
        for (i in 1..2) {
            val x = frame.left + frame.width() * i / 3
            val y = frame.top + frame.height() * i / 3
            c.drawLine(x, frame.top, x, frame.bottom, guide)
            c.drawLine(frame.left, y, frame.right, y, guide)
        }
        line.strokeWidth = 2 * dp
        c.drawRoundRect(frame, rad, rad, line)
    }
}

/**
 * 圖片裡不透明的範圍（去背 PNG 四周的透明空白不算）。
 * 圖很大的話跳著看（最多看 400 × 400 個點），夠準又不會卡。
 */
fun opaqueBounds(bmp: Bitmap): RectF {
    val w = bmp.width
    val h = bmp.height
    if (!bmp.hasAlpha() || w <= 0 || h <= 0) return RectF(0f, 0f, w.toFloat(), h.toFloat())
    val step = maxOf(1, maxOf(w, h) / 400)
    var l = w; var t = h; var r = -1; var b = -1
    val row = IntArray(w)
    var y = 0
    while (y < h) {
        bmp.getPixels(row, 0, w, 0, y, w, 1)
        var x = 0
        while (x < w) {
            if ((row[x] ushr 24) > 16) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
            x += step
        }
        y += step
    }
    if (r < 0) return RectF(0f, 0f, w.toFloat(), h.toFloat())
    return RectF(l.toFloat(), t.toFloat(), minOf(w, r + step).toFloat(), minOf(h, b + step).toFloat())
}
