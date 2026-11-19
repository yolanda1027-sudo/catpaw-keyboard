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
 * 拿一張圖片放進一個框（按鍵底圖、鍵盤背景圖）。三種放法（[mode]）：
 * - 照片、花紋（預設）：等比例鋪滿，可以放大、縮小、選要露出哪一塊（裁切）；換到框比例不同的手機，邊邊可能裁掉一點
 * - 邊框（[nine]）：四個角保持原樣，只拉長中間
 * - 整張都要看到（[fit]）：圖片有畫東西的範圍剛好塞滿框，不裁切；每台手機照自己的框重新算，可能被拉寬或壓扁
 *
 * 以下說明「照片、花紋」：
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
    /**
     * 照圖片的形狀（只有按鍵用）：不裁成圓角方形、不畫底色和外框，
     * PNG 透明的地方就是按鍵外面（毛邊、雲朵形的鍵）。
     */
    val shape: Boolean = false,
    /** 九宮格（只有按鍵用）：四個角不變形，按鍵變寬只拉長中間；sx、sy 是保留的邊角大小 */
    val nine: Boolean = false,
    val sx: Float = NineFrame.DEFAULT,
    val sy: Float = NineFrame.DEFAULT,
    /** 只拉寬或只拉高：圖片的寬度乘上這個倍數（> 1 變寬、< 1 變高，1 = 原比例） */
    val stretch: Float = 1f,
    /**
     * 整張都要看到：畫的時候照「現在這個框」把圖片有畫東西的範圍剛好塞滿。
     * 同時也留著 zoom、cx、cy、stretch（選的時候在當時的框算好的），舊版 App 看不懂 fit 也會長得差不多。
     */
    val fit: Boolean = false,
) {
    val isGif get() = img.endsWith(".gif")

    /** 放法：照片、花紋／邊框／整張都要看到 */
    val mode: Mode get() = when { nine -> Mode.BORDER; fit -> Mode.WHOLE; else -> Mode.COVER }

    enum class Mode(val title: String, val hint: String) {
        COVER("照片、花紋", "等比例放大蓋滿，邊邊可能裁掉一點"),
        BORDER("邊框", "四個角保持原樣，只拉長中間"),
        WHOLE("整張都要看到", "不裁切，但可能被拉寬或壓扁"),
    }

    /**
     * 換成另一種放法。
     * @param content 圖片有畫東西的範圍（「整張都要看到」用）；frame、iw、ih 是現在的框和圖片大小
     */
    fun withMode(m: Mode, frame: RectF, iw: Int, ih: Int, content: RectF): ImageFill = when (m) {
        Mode.COVER -> copy(nine = false, fit = false, zoom = if (shape) FIT else 1f, cx = 0.5f, cy = 0.5f, stretch = 1f)
        Mode.BORDER -> copy(nine = true, fit = false, shape = false)
        Mode.WHOLE -> fitContent(frame, iw, ih, content).copy(nine = false, fit = true)
    }

    /** 圖片自己決定按鍵的形狀（照圖片的形狀、九宮格）：底色、外框、圓角都不畫 */
    val ownsShape get() = shape || nine

    /** 整張圖要畫在哪裡，框裡才會剛好露出選好的那一塊 */
    fun destRect(frame: RectF, iw0: Int, ih0: Int): RectF {
        if (iw0 <= 0 || ih0 <= 0 || frame.isEmpty) return RectF(frame)
        // 拉寬／拉高：當成一張寬度不一樣的圖來算
        val iw = iw0 * stretch
        val ih = ih0.toFloat()
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
    fun normalized(frame: RectF, iw0: Int, ih0: Int): ImageFill {
        if (iw0 <= 0 || ih0 <= 0 || frame.isEmpty) return this
        val iw = iw0 * stretch
        val ih = ih0.toFloat()
        val z = zoom.coerceIn(minZoom(frame, iw, ih), MAX_ZOOM)
        val s = cover(frame, iw, ih) * z
        return copy(zoom = z, cx = clampCenter(cx, frame.width() / (iw * s)), cy = clampCenter(cy, frame.height() / (ih * s)))
    }

    fun toJson(): JSONObject = JSONObject().put("img", img)
        .put("zoom", zoom.toDouble()).put("cx", cx.toDouble()).put("cy", cy.toDouble())
        .apply {
            if (shape) put("shape", true)
            if (nine) put("nine", true).put("sx", sx.toDouble()).put("sy", sy.toDouble())
            if (stretch != 1f) put("stretch", stretch.toDouble())
            if (fit) put("fit", true)
        }

    /** 這張圖（含拉寬／拉高）最小可以縮到多小 */
    fun minZoomFor(frame: RectF, iw: Int, ih: Int): Float = minZoom(frame, iw * stretch, ih.toFloat())

    /** 剛好塞滿框：拉寬或拉高到跟框一樣的比例，整張圖都看得到、不裁掉 */
    fun fitFrame(frame: RectF, iw: Int, ih: Int): ImageFill {
        if (iw <= 0 || ih <= 0 || frame.isEmpty) return this
        val st = ((frame.width() / frame.height()) / (iw.toFloat() / ih)).coerceIn(MIN_STRETCH, MAX_STRETCH)
        return copy(stretch = st, zoom = 1f, cx = 0.5f, cy = 0.5f)
    }

    /**
     * 剛好塞滿框，但只看圖片裡「有畫東西的範圍」[content]（圖片座標）：
     * 去背 PNG 常常四周留一大片透明，整張塞進框的話，畫的東西會被擠扁、變小。
     */
    fun fitContent(frame: RectF, iw: Int, ih: Int, content: RectF): ImageFill {
        if (iw <= 0 || ih <= 0 || frame.isEmpty || content.isEmpty) return fitFrame(frame, iw, ih)
        val st = ((frame.width() / frame.height()) / (content.width() / content.height())).coerceIn(MIN_STRETCH, MAX_STRETCH)
        // 有畫的範圍剛好佔滿框的高 → 換算成 zoom（以整張圖鋪滿框為 1）
        val scale = frame.height() / content.height()
        val z = scale / cover(frame, iw * st, ih.toFloat())
        return copy(stretch = st, zoom = z.coerceIn(FIT, MAX_ZOOM), cx = content.centerX() / iw, cy = content.centerY() / ih)
    }

    companion object {
        const val MAX_ZOOM = 8f
        /** 小於任何按鍵的最小倍率：畫的時候會變成「整張圖剛好放進框裡」 */
        const val FIT = 0.05f

        /** 剛好鋪滿框的倍率 */
        /** 拉寬／拉高最多到幾倍 */
        const val MIN_STRETCH = 0.25f
        const val MAX_STRETCH = 4f

        fun cover(frame: RectF, iw: Int, ih: Int) = cover(frame, iw.toFloat(), ih.toFloat())
        fun cover(frame: RectF, iw: Float, ih: Float) = max(frame.width() / iw, frame.height() / ih)

        /** 最小可以縮到多小：整張圖剛好放進框裡 */
        fun minZoom(frame: RectF, iw: Int, ih: Int): Float = minZoom(frame, iw.toFloat(), ih.toFloat())
        fun minZoom(frame: RectF, iw: Float, ih: Float): Float =
            if (iw <= 0f || ih <= 0f || frame.isEmpty) 1f else min(frame.width() / iw, frame.height() / ih) / cover(frame, iw, ih)

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
            val nine = o.optBoolean("nine", false)
            return ImageFill(
                img, f("zoom", 1f, 0.05f, MAX_ZOOM), f("cx", 0.5f, 0f, 1f), f("cy", 0.5f, 0f, 1f),
                shape = o.optBoolean("shape", false) && !nine,
                nine = nine,
                sx = f("sx", NineFrame.DEFAULT, NineFrame.MIN, NineFrame.MAX),
                sy = f("sy", NineFrame.DEFAULT, NineFrame.MIN, NineFrame.MAX),
                stretch = f("stretch", 1f, MIN_STRETCH, MAX_STRETCH),
                fit = o.optBoolean("fit", false) && !nine,
            )
        }

        /** 每張圖有畫東西的範圍（量一次記住；圖片換掉時 Bitmap 也會是新的一個） */
        private val contentCache = java.util.WeakHashMap<Bitmap, RectF>()
        fun contentOf(bmp: Bitmap): RectF = synchronized(contentCache) { contentCache.getOrPut(bmp) { opaqueBounds(bmp) } }

        /** 整張都要看到：讓 content（圖片座標）剛好對到 frame，整張圖要畫在哪 */
        fun wholeDest(frame: RectF, iw: Int, ih: Int, content: RectF): RectF {
            val c = if (content.isEmpty) RectF(0f, 0f, iw.toFloat(), ih.toFloat()) else content
            val kx = frame.width() / c.width()
            val ky = frame.height() / c.height()
            val left = frame.left - c.left * kx
            val top = frame.top - c.top * ky
            return RectF(left, top, left + iw * kx, top + ih * ky)
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        /**
         * 畫出來：只畫在 clip（按鍵的圓角形狀）裡面，沒給 clip 就是整個框。
         * @param anim 會動的 GIF；有的話用它，沒有就用 bmp
         */
        fun draw(c: Canvas, fill: ImageFill, frame: RectF, clip: Path?, bmp: Bitmap?, anim: android.graphics.drawable.Drawable?) {
            if (fill.nine) {
                // 邊框：四個角不變形（會動的 GIF 用第一格）
                if (bmp != null) NineFrame.draw(c, bmp, fill.sx, fill.sy, frame)
                return
            }
            val iw = anim?.intrinsicWidth?.takeIf { it > 0 } ?: bmp?.width ?: return
            val ih = anim?.intrinsicHeight?.takeIf { it > 0 } ?: bmp?.height ?: return
            val dst = if (fill.fit) {
                // 整張都要看到：照現在的框重新算（GIF 沒辦法量透明範圍，用整張）
                wholeDest(frame, iw, ih, if (anim == null && bmp != null) contentOf(bmp) else RectF(0f, 0f, iw.toFloat(), ih.toFloat()))
            } else fill.destRect(frame, iw, ih)
            c.save()
            if (clip != null) c.clipPath(clip) else c.clipRect(frame)
            if (anim != null) Decals.drawAnim(c, anim, dst, 255)
            else if (bmp != null) c.drawBitmap(bmp, null, dst, paint)
            c.restore()
        }
    }
}
