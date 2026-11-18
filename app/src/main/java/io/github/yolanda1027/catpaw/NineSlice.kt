package io.github.yolanda1027.catpaw

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 九宮格框圖（快捷列按鈕用）：把圖切成 3×3，
 * 四個角保持原樣不變形，上下邊只左右拉長、左右邊只上下拉長、中間自由拉伸。
 * 同一張雕花框套在寬窄不同的按鈕上，角落的花紋都不會被拉歪。
 *
 * @param sx 左右兩邊各保留圖片寬度的幾分之幾（0.05–0.49）
 * @param sy 上下兩邊各保留圖片高度的幾分之幾（0.05–0.49）
 */
data class NineFrame(val img: String, val sx: Float = DEFAULT, val sy: Float = DEFAULT) {

    fun toJson(): JSONObject = JSONObject().put("img", img).put("sx", sx.toDouble()).put("sy", sy.toDouble())

    companion object {
        const val DEFAULT = 0.3f
        const val MIN = 0.05f
        const val MAX = 0.49f

        fun fromJson(o: JSONObject?): NineFrame? {
            if (o == null) return null
            val img = o.optString("img")
            if (!Decals.safeName(img)) return null
            fun f(k: String) = o.optDouble(k, DEFAULT.toDouble()).toFloat().let { if (it.isNaN()) DEFAULT else it }.coerceIn(MIN, MAX)
            return NineFrame(img, f("sx"), f("sy"))
        }

        /** 不開反鋸齒：九塊拼起來的地方才不會有細縫（圖片本身還是會平滑縮放） */
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        /**
         * 把框圖畫進 dst。角落照「圖片高度剛好等於框的高度」的比例縮放；
         * 框太窄、左右兩個角放不下時再一起縮小（角還是不變形）。
         */
        fun draw(c: Canvas, bmp: Bitmap, sx: Float, sy: Float, dst: RectF) {
            val iw = bmp.width
            val ih = bmp.height
            if (iw < 3 || ih < 3 || dst.width() <= 0f || dst.height() <= 0f) return
            val l = (iw * sx).roundToInt().coerceIn(1, iw / 2)
            val r = l
            val t = (ih * sy).roundToInt().coerceIn(1, ih / 2)
            val b = t
            var k = dst.height() / ih
            k = min(k, dst.width() / (l + r))
            val dl = l * k
            val dr = r * k
            val dt = t * k
            val db = b * k
            // 切線對齊整數像素，九塊剛好接起來
            fun px(v: Float) = Math.round(v).toFloat()
            val xs = floatArrayOf(px(dst.left), px(dst.left + dl), px(dst.right - dr), px(dst.right))
            val ys = floatArrayOf(px(dst.top), px(dst.top + dt), px(dst.bottom - db), px(dst.bottom))
            val sxs = intArrayOf(0, l, iw - r, iw)
            val sys = intArrayOf(0, t, ih - b, ih)
            val s = Rect()
            val d = RectF()
            for (row in 0..2) for (col in 0..2) {
                if (sxs[col + 1] <= sxs[col] || sys[row + 1] <= sys[row]) continue
                if (xs[col + 1] <= xs[col] || ys[row + 1] <= ys[row]) continue
                s.set(sxs[col], sys[row], sxs[col + 1], sys[row + 1])
                d.set(xs[col], ys[row], xs[col + 1], ys[row + 1])
                c.drawBitmap(bmp, s, d, paint)
            }
        }
    }
}

/** 調整邊角的畫面：上面是整張圖和切線，下面預覽窄、中、寬三種按鈕 */
object NineSliceDialog {
    /**
     * @param buttonW 快捷列一顆按鈕實際的寬（預覽用；0 = 不知道）
     */
    fun show(
        a: Activity, bmp: Bitmap, initial: NineFrame, label: String, textColor: Int, buttonW: Float = 0f,
        buttonH: Float = 0f, title: String = "調整按鈕圖片的邊角", caption: String = "預覽：快捷列按鈕實際的大小，和更窄、更寬的",
        done: (NineFrame?) -> Unit,
    ): Dialog {
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
            val p = (8 * dp).toInt()
            setPadding(p * 2, p, p * 2, p)
        }
        root.addView(label(title, 17f))
        root.addView(label("虛線外面的四個角不會變形，按鈕變寬變窄時只拉長中間那段", 13f, 0.75f))
        val view = NineSliceView(a, bmp, initial, label, textColor, buttonW, buttonH, caption)
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun bar(title: String, value: Float, set: (Float) -> Unit) {
            val t = label("$title：${(value * 100).roundToInt()}%", 14f)
            t.gravity = Gravity.START
            root.addView(t)
            root.addView(SeekBar(a).apply {
                max = ((NineFrame.MAX - NineFrame.MIN) * 100).roundToInt()
                progress = ((value - NineFrame.MIN) * 100).roundToInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = NineFrame.MIN + p / 100f
                        t.text = "$title：${(v * 100).roundToInt()}%"
                        set(v)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
                val m = (14 * dp).toInt()
                setPadding(m, 0, m, 0)
            })
        }
        bar("左右的角", initial.sx) { view.frame = view.frame.copy(sx = it) }
        bar("上下的角", initial.sy) { view.frame = view.frame.copy(sy = it) }

        val btns = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = (12 * dp).toInt()
            setPadding(p, p / 2, p, p)
        }
        var result: NineFrame? = null
        fun btn(s: String, onClick: () -> Unit) = btns.addView(Button(a).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = (4 * dp).toInt()
            setMargins(m, m, m, m)
        })
        btn("取消") { d.dismiss() }
        btn("完成") { result = view.frame; d.dismiss() }
        root.addView(btns)

        Ui.fitSystemBars(root)
        d.setContentView(root)
        d.setOnDismissListener { done(result) }
        d.show()
        return d
    }
}

@SuppressLint("ViewConstructor")
class NineSliceView(
    ctx: Context, val bmp: Bitmap, initial: NineFrame, private val label: String, private val textColor: Int,
    private val buttonW: Float = 0f,
    private val buttonH: Float = 0f,
    private val captionText: String = "預覽：快捷列按鈕實際的大小，和更窄、更寬的",
) : View(ctx) {
    var frame: NineFrame = initial
        set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private val img = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFF4F8B.toInt()
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f)
    }
    private val checker = Paint()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt(); textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val pad = 16 * dp
        val w = width - pad * 2
        // 上面：整張圖＋切線（最多佔一半高度）
        val topH = height * 0.5f
        val s = min(w / bmp.width, (topH - pad) / bmp.height)
        val iw = bmp.width * s
        val ih = bmp.height * s
        val r = RectF((width - iw) / 2, pad, (width + iw) / 2, pad + ih)
        drawChecker(c, r)
        c.drawBitmap(bmp, null, r, img)
        val gx = iw * frame.sx
        val gy = ih * frame.sy
        c.drawLine(r.left + gx, r.top - 6 * dp, r.left + gx, r.bottom + 6 * dp, guide)
        c.drawLine(r.right - gx, r.top - 6 * dp, r.right - gx, r.bottom + 6 * dp, guide)
        c.drawLine(r.left - 6 * dp, r.top + gy, r.right + 6 * dp, r.top + gy, guide)
        c.drawLine(r.left - 6 * dp, r.bottom - gy, r.right + 6 * dp, r.bottom - gy, guide)

        // 下面：實際按鈕大小，再加一顆窄的、一顆寬的
        caption.textSize = 13 * dp
        val bh = if (buttonH > 0f) buttonH else 32 * dp // 按鈕實際的高度
        var y = r.bottom + 34 * dp
        c.drawText(captionText, width / 2f, y, caption)
        y += 14 * dp
        val real = if (buttonW > 0f) min(buttonW, w) else w * 0.24f
        val widths = floatArrayOf(real * 0.7f, real, min(w, real * 2.6f))
        text.color = textColor
        text.textSize = bh * 0.5f
        for (bw in widths) {
            val b = RectF((width - bw) / 2, y, (width + bw) / 2, y + bh)
            NineFrame.draw(c, bmp, frame.sx, frame.sy, b)
            c.drawText(label, b.centerX(), b.centerY() - (text.descent() + text.ascent()) / 2, text)
            y += bh + 12 * dp
        }
    }

    /** 灰白格子：看得出圖片哪裡是透明的 */
    private fun drawChecker(c: Canvas, r: RectF) {
        val s = 8 * dp
        c.save()
        c.clipRect(r)
        var yy = r.top
        var row = 0
        while (yy < r.bottom) {
            var xx = r.left
            var col = row % 2
            while (xx < r.right) {
                checker.color = if (col % 2 == 0) 0xFF4A4550.toInt() else 0xFF3A3540.toInt()
                c.drawRect(xx, yy, xx + s, yy + s, checker)
                xx += s
                col++
            }
            yy += s
            row++
        }
        c.restore()
    }
}
