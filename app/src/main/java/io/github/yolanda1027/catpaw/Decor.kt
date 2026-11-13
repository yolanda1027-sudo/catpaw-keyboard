package io.github.yolanda1027.catpaw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ======================== 功能鍵貼圖 ========================

/** 可以貼圖的功能鍵 */
enum class StickerSlot(val id: String, val title: String) {
    ENTER("enter", "Enter 鍵"),
    BACKSPACE("backspace", "刪除鍵"),
    SPACE("space", "空白鍵"),
    SYMBOL("symbol", "?123 鍵"),
    EMOJI("emoji", "表情符號鍵");

    companion object {
        fun of(k: Key): StickerSlot? = when (k.act) {
            Act.ENTER -> ENTER
            Act.BACKSPACE -> BACKSPACE
            Act.SPACE -> SPACE
            Act.TO_SYMBOL -> SYMBOL
            Act.TO_EMOJI -> EMOJI
            else -> null
        }
    }
}

/**
 * 貼圖存在 App 自己的資料夾裡（縮小過的 PNG），
 * 所以相簿裡的原圖刪掉也沒關係。
 */
object Stickers {
    private const val MAX_SIZE = 384

    fun file(ctx: Context, slot: StickerSlot) = File(ctx.filesDir, "sticker_${slot.id}.png")

    fun load(ctx: Context): Map<StickerSlot, Bitmap> =
        StickerSlot.values().mapNotNull { slot ->
            val f = file(ctx, slot)
            if (!f.exists()) null else BitmapFactory.decodeFile(f.path)?.let { slot to it }
        }.toMap()

    /** 從相簿選的圖：縮小、存起來。成功回傳縮好的圖 */
    fun import(ctx: Context, slot: StickerSlot, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIZE && bounds.outHeight / (sample * 2) >= MAX_SIZE) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        raw?.let { b ->
            val s = min(1f, MAX_SIZE.toFloat() / maxOf(b.width, b.height))
            val scaled = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
            file(ctx, slot).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            scaled
        }
    } catch (_: Exception) {
        null
    }

    fun remove(ctx: Context, slot: StickerSlot) {
        file(ctx, slot).delete()
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** 把貼圖等比例塞進按鍵裡（按下去會縮一點） */
    fun draw(c: Canvas, bmp: Bitmap, key: RectF, pressed: Boolean, pad: Float) {
        val area = RectF(key.left + pad, key.top + pad, key.right - pad, key.bottom - pad)
        val s = min(area.width() / bmp.width, area.height() / bmp.height) * if (pressed) 0.9f else 1f
        val w = bmp.width * s
        val h = bmp.height * s
        val dst = RectF(area.centerX() - w / 2, area.centerY() - h / 2, area.centerX() + w / 2, area.centerY() + h / 2)
        c.drawBitmap(bmp, null, dst, paint)
    }
}

// ======================== 按鍵動畫 ========================

/**
 * 按鍵動畫：
 * - bounce：按鍵彈一下
 * - heart / paw / star：從按鍵冒出小愛心／小貓爪／小星星，往上飄然後淡掉
 */
class PressEffects(private val dp: Float) {

    private class Fx(val rect: RectF, val start: Long, val kind: String)

    private val list = ArrayList<Fx>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    /** 白色外框，讓小圖案在什麼背景上都看得清楚 */
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val path = Path()

    fun add(kind: String, rect: RectF) {
        if (kind == "none") return
        list.add(Fx(RectF(rect), SystemClock.uptimeMillis(), kind))
    }

    private fun duration(kind: String) = if (kind == "bounce") 220L else 520L

    /** 還有動畫在跑嗎（有的話畫面要繼續更新） */
    fun active(now: Long): Boolean {
        list.removeAll { now - it.start > duration(it.kind) }
        return list.isNotEmpty()
    }

    /** 彈一下：這顆鍵現在要縮放多少 */
    fun scaleFor(rect: RectF, now: Long): Float {
        val fx = list.lastOrNull { it.kind == "bounce" && it.rect == rect } ?: return 1f
        val p = (now - fx.start).toFloat() / duration(fx.kind)
        if (p !in 0f..1f) return 1f
        // 先縮小再彈回來，稍微超過一點點
        return 1f - 0.14f * sin(p * Math.PI).toFloat() + 0.03f * sin(p * 2 * Math.PI).toFloat()
    }

    fun draw(c: Canvas, now: Long, color: Int) {
        for (fx in list) {
            if (fx.kind == "bounce") continue
            val p = ((now - fx.start).toFloat() / duration(fx.kind)).coerceIn(0f, 1f)
            val cx = fx.rect.centerX() + sin(p * 6f) * 4 * dp
            val cy = fx.rect.top + fx.rect.height() * 0.2f - p * fx.rect.height() * 1.1f
            val s = (11 + 7 * p) * dp
            // 前 60% 不透明，後面才慢慢淡掉
            val alpha = if (p < 0.6f) 255 else ((1f - p) / 0.4f * 255).toInt()
            for (pass in 0..1) {
                val pt = if (pass == 0) outline else paint
                if (pass == 1) pt.color = vivid(color)
                pt.alpha = alpha
                when (fx.kind) {
                    "heart" -> heart(c, cx, cy, s, pt)
                    "paw" -> paw(c, cx, cy, s, pt)
                    "star" -> star(c, cx, cy, s, pt)
                }
            }
        }
    }

    /** 主題的強調色如果太淡（粉粉的），就加深一點，動畫才看得到 */
    private fun vivid(c: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[1] = maxOf(hsv[1], 0.55f)
        hsv[2] = minOf(hsv[2], 0.95f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        path.reset()
        path.moveTo(cx, cy + s * 0.9f)
        path.cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.35f)
        path.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.9f)
        c.drawPath(path, paint)
    }

    private fun paw(c: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        c.drawOval(RectF(cx - s * 0.55f, cy - s * 0.05f, cx + s * 0.55f, cy + s * 0.75f), paint)
        val r = s * 0.22f
        c.drawCircle(cx - s * 0.62f, cy - s * 0.25f, r, paint)
        c.drawCircle(cx - s * 0.22f, cy - s * 0.6f, r, paint)
        c.drawCircle(cx + s * 0.22f, cy - s * 0.6f, r, paint)
        c.drawCircle(cx + s * 0.62f, cy - s * 0.25f, r, paint)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        path.reset()
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0)
            val rr = if (i % 2 == 0) s else s * 0.45f
            val x = cx + cos(a).toFloat() * rr
            val y = cy + sin(a).toFloat() * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, paint)
    }
}
