package io.github.yolanda1027.catpaw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 按鍵動畫（粒子系統）：按一下，從按鍵冒出一個或好幾個小東西，各自飄動、慢慢淡掉。
 * 所有圖案都是用程式畫的，不用圖片檔。
 *
 * 種類：
 *   bounce 彈一下｜heart 小愛心｜paw 小貓爪｜star 小星星
 *   hearts 愛心上湧｜sparkle 金色星屑｜flower 小花飄游｜butterfly 蝴蝶輕舞
 *   ripple 水面漣漪｜bubble 彩虹泡泡｜snow 雪花｜note 音符
 */
class PressEffects(private val dp: Float) {

    companion object {
        /** 設定頁用：(代號, 名稱) */
        val ALL = listOf(
            "none" to "不要動畫",
            "bounce" to "彈一下",
            "heart" to "冒小愛心 ♡",
            "paw" to "冒小貓爪 🐾",
            "star" to "冒小星星 ☆",
            "hearts" to "愛心上湧 💕",
            "sparkle" to "金色星屑 ✨",
            "flower" to "小花飄游 ✿",
            "butterfly" to "蝴蝶輕舞 🦋",
            "ripple" to "水面漣漪 ◎",
            "bubble" to "彩虹泡泡 ○",
            "snow" to "雪花 ❄",
            "note" to "音符 ♪",
        )
        private const val MAX_PARTICLES = 80
    }

    private enum class Shape { HEART, PAW, STAR, SPARKLE, FLOWER, BUTTERFLY, RING, BUBBLE, SNOW, NOTE, DOT }

    /** 一個小粒子。位置用「經過的時間」直接算，不用每格更新狀態 */
    private class P(
        val shape: Shape,
        val x0: Float, val y0: Float,
        val vx: Float, val vy: Float, val gravity: Float,
        val size: Float, val color: Int,
        val start: Long, val life: Long,
        val rot0: Float = 0f, val vrot: Float = 0f,
        val sway: Float = 0f, val phase: Float = 0f,
        val text: String = "",
        val outline: Boolean = true,
    )

    private class Bounce(val rect: RectF, val start: Long)

    private val particles = ArrayList<P>()
    private val bounces = ArrayList<Bounce>()
    private val rnd = Random(SystemClock.uptimeMillis())
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()

    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    /** 在按鍵 rect 上觸發動畫；dark = 主題底色是不是深色（決定雪花、漣漪、音符用白的還黑的） */
    fun add(kind: String, rect: RectF, accent: Int, dark: Boolean) {
        val now = SystemClock.uptimeMillis()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val top = rect.top + rect.height() * 0.2f
        val ink = if (dark) Color.WHITE else Color.parseColor("#3E4A6B")
        fun add(p: P) = particles.add(p)
        when (kind) {
            "none" -> return
            "bounce" -> bounces.add(Bounce(RectF(rect), now))
            "heart", "paw", "star" -> add(P(
                when (kind) { "heart" -> Shape.HEART; "paw" -> Shape.PAW; else -> Shape.STAR },
                cx, top, 0f, -rect.height() * 2.1f, 0f, 11 * dp, vivid(accent), now, 520,
                sway = 4 * dp, phase = r(0f, 6f),
            ))
            "hearts" -> repeat(6) { i ->
                add(P(Shape.HEART, cx + r(-8f, 8f) * dp, top, r(-25f, 25f) * dp, -r(150f, 240f) * dp, 0f,
                    r(5f, 8.5f) * dp, Color.parseColor("#FF7EAA"), now + i * 45L, 650,
                    sway = r(2f, 5f) * dp, phase = r(0f, 6f), outline = false))
            }
            "sparkle" -> repeat(7) {
                val a = r(0f, 2 * PI.toFloat())
                val v = r(60f, 150f) * dp
                add(P(Shape.SPARKLE, cx, cy, cos(a) * v, sin(a) * v - 40 * dp, 60 * dp,
                    r(4f, 8f) * dp, Color.parseColor("#F5B82E"), now + rnd.nextLong(0, 60), 650,
                    phase = r(0f, 6f), outline = false))
            }
            "flower" -> repeat(5) { i ->
                // 往上半圈平均散開，彼此不要疊在一起
                val a = -PI.toFloat() * (0.1f + 0.8f * i / 4f) + r(-0.15f, 0.15f)
                val v = r(90f, 160f) * dp
                add(P(Shape.FLOWER, cx, cy, cos(a) * v, sin(a) * v, 20 * dp,
                    r(8f, 11f) * dp, Color.parseColor("#6FA3E8"), now + rnd.nextLong(0, 80), 950,
                    rot0 = r(0f, 72f), vrot = r(-180f, 180f), sway = r(3f, 8f) * dp, phase = r(0f, 6f), outline = false))
            }
            "butterfly" -> {
                val colors = listOf(Color.parseColor("#F7A1C4"), Color.WHITE)
                colors.forEachIndexed { i, col ->
                    add(P(Shape.BUTTERFLY, cx + (if (i == 0) -8 else 8) * dp, cy, (if (i == 0) -30f else 30f) * dp,
                        -r(80f, 120f) * dp, 0f, r(10f, 12f) * dp, col, now + i * 70L, 1100,
                        rot0 = if (i == 0) -12f else 12f, sway = r(8f, 14f) * dp, phase = r(0f, 6f)))
                }
                repeat(4) {
                    add(P(Shape.SPARKLE, cx + r(-14f, 14f) * dp, cy + r(-6f, 6f) * dp, r(-30f, 30f) * dp, -r(40f, 90f) * dp, 0f,
                        r(2.5f, 4f) * dp, Color.parseColor("#F7A1C4"), now + rnd.nextLong(0, 200), 700, phase = r(0f, 6f), outline = false))
                }
            }
            "ripple" -> {
                val col = if (dark) Color.parseColor("#BFE0FF") else Color.parseColor("#3D7BC4")
                repeat(3) { i -> add(P(Shape.RING, cx, cy, 0f, 0f, 0f, 30 * dp, col, now + i * 110L, 650, outline = false)) }
                repeat(4) {
                    val a = r(-PI.toFloat() * 0.9f, -PI.toFloat() * 0.1f)
                    add(P(Shape.DOT, cx, cy, cos(a) * 70 * dp, sin(a) * 110 * dp, 500 * dp, r(1.5f, 2.5f) * dp, col, now, 450, outline = false))
                }
            }
            "bubble" -> repeat(3) { i ->
                add(P(Shape.BUBBLE, cx + (i - 1) * 16 * dp + r(-4f, 4f) * dp, cy, r(-15f, 15f) * dp, -r(70f, 130f) * dp, 0f,
                    r(6f, 11f) * dp, Color.WHITE, now + i * 90L, 950, sway = r(3f, 6f) * dp, phase = r(0f, 6f), outline = false))
            }
            "snow" -> {
                repeat(2) { i ->
                    add(P(Shape.SNOW, cx + (if (i == 0) -16 else 16) * dp + r(-4f, 4f) * dp, top - 6 * dp - i * 10 * dp,
                        (if (i == 0) -20f else 20f) * dp, r(25f, 55f) * dp, 0f,
                        r(8f, 11f) * dp, ink, now + rnd.nextLong(0, 100), 1000,
                        rot0 = r(0f, 60f), vrot = r(-90f, 90f), sway = r(4f, 8f) * dp, phase = r(0f, 6f), outline = false))
                }
                repeat(5) {
                    add(P(Shape.DOT, cx + r(-18f, 18f) * dp, top + r(-10f, 4f) * dp, r(-15f, 15f) * dp, r(20f, 50f) * dp, 0f,
                        r(1f, 1.8f) * dp, ink, now + rnd.nextLong(0, 200), 800, sway = 3 * dp, phase = r(0f, 6f), outline = false))
                }
            }
            "note" -> repeat(2) { i ->
                add(P(Shape.NOTE, cx + (if (i == 0) -10 else 10) * dp, top, r(-15f, 15f) * dp, -r(90f, 130f) * dp, 0f,
                    r(22f, 27f) * dp, ink, now + i * 110L, 900,
                    rot0 = r(-15f, 15f), sway = r(4f, 8f) * dp, phase = r(0f, 6f),
                    text = listOf("♪", "♫", "♬", "♩").random(rnd), outline = false))
            }
        }
        while (particles.size > MAX_PARTICLES) particles.removeAt(0)
        while (bounces.size > 10) bounces.removeAt(0)
    }

    /** 還有動畫在跑嗎（有的話畫面要繼續更新） */
    fun active(now: Long): Boolean {
        particles.removeAll { now - it.start > it.life }
        bounces.removeAll { now - it.start > 220 }
        return particles.isNotEmpty() || bounces.isNotEmpty()
    }

    /** 彈一下：這顆鍵現在要縮放多少 */
    fun scaleFor(rect: RectF, now: Long): Float {
        val b = bounces.lastOrNull { it.rect == rect } ?: return 1f
        val p = (now - b.start) / 220f
        if (p !in 0f..1f) return 1f
        return 1f - 0.14f * sin(p * PI).toFloat() + 0.03f * sin(p * 2 * PI).toFloat()
    }

    fun draw(c: Canvas, now: Long) {
        for (p in particles) {
            val t = (now - p.start) / 1000f
            if (t < 0f) continue
            val prog = ((now - p.start).toFloat() / p.life).coerceIn(0f, 1f)
            val x = p.x0 + p.vx * t + sin(t * 6f + p.phase) * p.sway
            val y = p.y0 + p.vy * t + 0.5f * p.gravity * t * t
            val alpha = if (prog < 0.6f) 255 else ((1f - prog) / 0.4f * 255).toInt()
            val rot = p.rot0 + p.vrot * t
            c.save()
            c.translate(x, y)
            c.rotate(rot)
            when (p.shape) {
                Shape.RING -> ring(c, p, prog, alpha)
                Shape.BUBBLE -> bubble(c, p, prog, alpha)
                else -> {
                    if (p.outline) shape(c, p, t, prog, outlinePaint(alpha))
                    fill.color = p.color
                    fill.alpha = alpha
                    shape(c, p, t, prog, fill)
                }
            }
            c.restore()
        }
    }

    private fun outlinePaint(alpha: Int) = line.apply {
        color = Color.WHITE
        this.alpha = alpha
        strokeWidth = 2.5f * dp
        shader = null
    }

    private fun shape(c: Canvas, p: P, t: Float, prog: Float, pt: Paint) {
        val s = p.size
        when (p.shape) {
            Shape.HEART -> heart(c, s * (1f + 0.4f * prog), pt)
            Shape.PAW -> paw(c, s * (1f + 0.4f * prog), pt)
            Shape.STAR -> star(c, s * (1f + 0.4f * prog), pt)
            Shape.SPARKLE -> sparkle(c, s * (0.6f + 0.4f * abs(sin(t * 14f + p.phase))), pt)
            Shape.FLOWER -> flower(c, s, pt)
            Shape.BUTTERFLY -> butterfly(c, s, 0.3f + 0.7f * abs(sin(t * 17f + p.phase)), p.color, pt)
            Shape.SNOW -> snow(c, s, pt)
            Shape.NOTE -> {
                textPaint.textSize = s
                textPaint.color = pt.color
                textPaint.alpha = pt.alpha
                c.drawText(p.text, 0f, s * 0.35f, textPaint)
            }
            Shape.DOT -> c.drawCircle(0f, 0f, s, pt)
            Shape.RING, Shape.BUBBLE -> Unit
        }
    }

    // ---------- 各種圖案（原點在中心） ----------

    private fun heart(c: Canvas, s: Float, pt: Paint) {
        path.reset()
        path.moveTo(0f, s * 0.9f)
        path.cubicTo(-s * 1.4f, -s * 0.1f, -s * 0.7f, -s * 1.1f, 0f, -s * 0.35f)
        path.cubicTo(s * 0.7f, -s * 1.1f, s * 1.4f, -s * 0.1f, 0f, s * 0.9f)
        c.drawPath(path, pt)
    }

    private fun paw(c: Canvas, s: Float, pt: Paint) {
        c.drawOval(RectF(-s * 0.55f, -s * 0.05f, s * 0.55f, s * 0.75f), pt)
        val r = s * 0.22f
        c.drawCircle(-s * 0.62f, -s * 0.25f, r, pt)
        c.drawCircle(-s * 0.22f, -s * 0.6f, r, pt)
        c.drawCircle(s * 0.22f, -s * 0.6f, r, pt)
        c.drawCircle(s * 0.62f, -s * 0.25f, r, pt)
    }

    private fun star(c: Canvas, s: Float, pt: Paint) {
        path.reset()
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0)
            val rr = if (i % 2 == 0) s else s * 0.45f
            val x = cos(a).toFloat() * rr
            val y = sin(a).toFloat() * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, pt)
    }

    /** 四角閃光（中間細、四個尖角） */
    private fun sparkle(c: Canvas, s: Float, pt: Paint) {
        path.reset()
        val k = s * 0.18f
        path.moveTo(0f, -s)
        path.quadTo(k, -k, s, 0f)
        path.quadTo(k, k, 0f, s)
        path.quadTo(-k, k, -s, 0f)
        path.quadTo(-k, -k, 0f, -s)
        path.close()
        c.drawPath(path, pt)
    }

    private fun flower(c: Canvas, s: Float, pt: Paint) {
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72.0)
            c.drawCircle(cos(a).toFloat() * s * 0.62f, sin(a).toFloat() * s * 0.62f, s * 0.38f, pt)
        }
        // 白色花心，看得出是一朵花
        if (pt.style == Paint.Style.FILL) {
            val old = pt.color
            val a = pt.alpha
            pt.color = Color.WHITE
            pt.alpha = a
            c.drawCircle(0f, 0f, s * 0.24f, pt)
            pt.color = old
            pt.alpha = a
        }
    }

    /** 蝴蝶：flap 0~1 是翅膀張開的程度（左右翅膀橫向縮放，看起來在拍） */
    private fun butterfly(c: Canvas, s: Float, flap: Float, color: Int, pt: Paint) {
        for (side in listOf(-1f, 1f)) {
            c.save()
            c.scale(side * flap, 1f)
            path.reset()
            // 上翅
            path.moveTo(0f, -s * 0.1f)
            path.cubicTo(s * 0.4f, -s * 1.05f, s * 1.25f, -s * 0.95f, s * 1.0f, -s * 0.2f)
            path.cubicTo(s * 0.9f, s * 0.05f, s * 0.4f, s * 0.05f, 0f, 0f)
            // 下翅
            path.moveTo(0f, s * 0.05f)
            path.cubicTo(s * 0.55f, s * 0.05f, s * 0.85f, s * 0.35f, s * 0.6f, s * 0.75f)
            path.cubicTo(s * 0.4f, s * 0.95f, s * 0.1f, s * 0.6f, 0f, s * 0.2f)
            c.drawPath(path, pt)
            c.restore()
        }
        if (pt.style == Paint.Style.FILL) {
            // 身體和觸角（白蝴蝶用深一點的顏色，才看得出輪廓）
            val body = if (color == Color.WHITE) Color.parseColor("#9A8FA8") else darker(color)
            line.color = body
            line.alpha = pt.alpha
            line.strokeWidth = s * 0.16f
            line.shader = null
            c.drawLine(0f, -s * 0.25f, 0f, s * 0.45f, line)
            line.strokeWidth = s * 0.06f
            c.drawLine(0f, -s * 0.25f, -s * 0.25f, -s * 0.7f, line)
            c.drawLine(0f, -s * 0.25f, s * 0.25f, -s * 0.7f, line)
        }
    }

    /** 六角雪花 */
    private fun snow(c: Canvas, s: Float, pt: Paint) {
        line.color = pt.color
        line.alpha = pt.alpha
        line.strokeWidth = s * 0.14f
        line.shader = null
        for (i in 0 until 6) {
            c.save()
            c.rotate(i * 60f)
            c.drawLine(0f, 0f, 0f, -s, line)
            c.drawLine(0f, -s * 0.55f, -s * 0.28f, -s * 0.8f, line)
            c.drawLine(0f, -s * 0.55f, s * 0.28f, -s * 0.8f, line)
            c.restore()
        }
    }

    /** 漣漪：扁扁的圓圈從中間往外擴散 */
    private fun ring(c: Canvas, p: P, prog: Float, alpha: Int) {
        val rr = p.size * (0.15f + 0.85f * prog)
        line.color = p.color
        line.alpha = ((1f - prog) * 230).toInt().coerceAtMost(alpha)
        line.strokeWidth = 2f * dp * (1.4f - prog)
        line.shader = null
        c.drawOval(RectF(-rr, -rr * 0.45f, rr, rr * 0.45f), line)
    }

    /** 彩虹泡泡：透明＋彩虹邊＋白色反光，最後破掉散成小水珠 */
    private fun bubble(c: Canvas, p: P, prog: Float, alpha: Int) {
        val s = p.size
        if (prog > 0.85f) {
            val q = (prog - 0.85f) / 0.15f
            fill.color = Color.parseColor("#9FD3FF")
            fill.alpha = ((1f - q) * 220).toInt()
            for (i in 0 until 6) {
                val a = i * PI.toFloat() / 3
                c.drawCircle(cos(a) * s * (1f + q), sin(a) * s * (1f + q), 1.3f * dp, fill)
            }
            return
        }
        fill.color = Color.WHITE
        fill.alpha = (alpha * 0.18f).toInt()
        c.drawCircle(0f, 0f, s, fill)
        line.shader = SweepGradient(0f, 0f, intArrayOf(
            Color.parseColor("#FF9EC7"), Color.parseColor("#FFE08A"), Color.parseColor("#9EF0C8"),
            Color.parseColor("#8FC8FF"), Color.parseColor("#D5A6FF"), Color.parseColor("#FF9EC7"),
        ), null)
        line.alpha = alpha
        line.strokeWidth = 1.6f * dp
        c.drawCircle(0f, 0f, s, line)
        line.shader = null
        line.color = Color.WHITE
        line.alpha = alpha
        line.strokeWidth = 1.8f * dp
        c.drawArc(RectF(-s * 0.65f, -s * 0.65f, s * 0.65f, s * 0.65f), 200f, 60f, false, line)
    }

    private fun darker(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] *= 0.7f
        return Color.HSVToColor(hsv)
    }

    /** 主題的強調色如果太淡，就加深一點，動畫才看得到 */
    private fun vivid(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = maxOf(hsv[1], 0.55f)
        hsv[2] = minOf(hsv[2], 0.95f)
        return Color.HSVToColor(hsv)
    }
}
