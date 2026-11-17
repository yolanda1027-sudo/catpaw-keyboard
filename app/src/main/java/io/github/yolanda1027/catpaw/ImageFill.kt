package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * 拿一張圖片鋪滿一個框（按鍵底圖、鍵盤背景圖）：可以放大、縮小、選要露出哪一塊（裁切）。
 *
 * - zoom = 1：剛好鋪滿框（多出來的邊裁掉，像手機桌布）
 * - zoom > 1：再放大
 * - zoom < 1：縮小到整張圖都看得到（最小到「整張剛好放進框裡」，空出來的地方露出原本的底色）
 * - cx, cy：框的正中間對到圖片的哪個位置（0–1，0.5 = 圖片正中間）
 *
 * 只記比例，所以同一個設定套到大小不同的按鍵上也會長得差不多。
 */
data class ImageFill(
    /** 圖檔名稱（放在 Decals.dir 裡，跟貼上的圖案共用） */
    val img: String,
    val zoom: Float = 1f,
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
) {
    val isGif get() = img.endsWith(".gif")

    /** 整張圖要畫在哪裡，框裡才會剛好露出選好的那一塊 */
    fun destRect(frame: RectF, iw: Int, ih: Int): RectF {
        if (iw <= 0 || ih <= 0 || frame.isEmpty) return RectF(frame)
        val s = cover(frame, iw, ih) * zoom.coerceIn(minZoom(frame, iw, ih), MAX_ZOOM)
        val w = iw * s
        val h = ih * s
        val x = clampCenter(cx, frame.width() / w)
        val y = clampCenter(cy, frame.height() / h)
        val left = frame.centerX() - x * w
        val top = frame.centerY() - y * h
        return RectF(left, top, left + w, top + h)
    }

    /** 把中心點拉回合理的範圍（不會拖到圖片外面露出一大片空白） */
    fun normalized(frame: RectF, iw: Int, ih: Int): ImageFill {
        if (iw <= 0 || ih <= 0 || frame.isEmpty) return this
        val z = zoom.coerceIn(minZoom(frame, iw, ih), MAX_ZOOM)
        val s = cover(frame, iw, ih) * z
        return copy(zoom = z, cx = clampCenter(cx, frame.width() / (iw * s)), cy = clampCenter(cy, frame.height() / (ih * s)))
    }

    fun toJson(): JSONObject = JSONObject().put("img", img)
        .put("zoom", zoom.toDouble()).put("cx", cx.toDouble()).put("cy", cy.toDouble())

    companion object {
        const val MAX_ZOOM = 8f

        /** 剛好鋪滿框的倍率 */
        fun cover(frame: RectF, iw: Int, ih: Int) = max(frame.width() / iw, frame.height() / ih)

        /** 最小可以縮到多小：整張圖剛好放進框裡 */
        fun minZoom(frame: RectF, iw: Int, ih: Int): Float =
            if (iw <= 0 || ih <= 0 || frame.isEmpty) 1f else min(frame.width() / iw, frame.height() / ih) / cover(frame, iw, ih)

        /** @param visible 框佔圖片的幾分之幾（> 1 = 圖比框小，只能放正中間） */
        private fun clampCenter(c: Float, visible: Float): Float {
            val half = visible / 2f
            return if (half >= 0.5f) 0.5f else c.coerceIn(half, 1f - half)
        }

        fun fromJson(o: JSONObject?): ImageFill? {
            if (o == null) return null
            val img = o.optString("img")
            if (!Decals.safeName(img)) return null
            fun f(k: String, d: Float, lo: Float, hi: Float) = o.optDouble(k, d.toDouble()).toFloat().let { if (it.isNaN()) d else it }.coerceIn(lo, hi)
            return ImageFill(img, f("zoom", 1f, 0.05f, MAX_ZOOM), f("cx", 0.5f, 0f, 1f), f("cy", 0.5f, 0f, 1f))
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        /**
         * 畫出來：只畫在 clip（按鍵的圓角形狀）裡面，沒給 clip 就是整個框。
         * @param anim 會動的 GIF；有的話用它，沒有就用 bmp
         */
        fun draw(c: Canvas, fill: ImageFill, frame: RectF, clip: Path?, bmp: Bitmap?, anim: android.graphics.drawable.Drawable?) {
            val iw = anim?.intrinsicWidth?.takeIf { it > 0 } ?: bmp?.width ?: return
            val ih = anim?.intrinsicHeight?.takeIf { it > 0 } ?: bmp?.height ?: return
            val dst = fill.destRect(frame, iw, ih)
            c.save()
            if (clip != null) c.clipPath(clip) else c.clipRect(frame)
            if (anim != null) Decals.drawAnim(c, anim, dst, 255)
            else if (bmp != null) c.drawBitmap(bmp, null, dst, paint)
            c.restore()
        }
    }
}
