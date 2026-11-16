package io.github.yolanda1027.catpaw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * 貼在按鍵或鍵盤背景上的圖案（主題工作室做的）。
 *
 * @param target 貼在哪：按鍵代號（zy:ㄅ、enter…，見 KeyStyles.idOf），或 [BG]（鍵盤背景）
 * @param img 「shape:cat」= 內建圖案；「file:decal_123.png」= 自己匯入的圖
 * @param x 中心點的位置，0–1（相對於按鍵或整個鍵盤，可以稍微超出去）
 * @param size 圖案寬度 = size × 參考高度（按鍵：按鍵的高度；背景：整個鍵盤的高度）
 * @param color 內建圖案的顏色（匯入的圖不會用到）
 * @param front 背景圖案：true = 蓋在按鍵上面，false = 在按鍵後面
 */
data class Decal(
    val id: String,
    val target: String,
    val img: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.5f,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
    val color: Int = 0xFFFF8FA9.toInt(),
    val front: Boolean = false,
    /** 左右翻轉（鏡像） */
    val flip: Boolean = false,
    /** 高度的拉伸倍率（拖上下的拉把只改高、左右的拉把只改寬時會變；1 = 原本比例） */
    val stretch: Float = 1f,
) {
    val isShape get() = img.startsWith(SHAPE)
    val shapeName get() = img.removePrefix(SHAPE)
    val fileName get() = img.removePrefix(FILE)
    val onBackground get() = target == BG

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("target", target).put("img", img)
        .put("x", x.toDouble()).put("y", y.toDouble()).put("size", size.toDouble())
        .put("rotation", rotation.toDouble()).put("alpha", alpha.toDouble())
        .put("color", String.format("#%08X", color)).put("front", front).put("flip", flip).put("stretch", stretch.toDouble())

    companion object {
        const val BG = "@bg"
        const val SHAPE = "shape:"
        const val FILE = "file:"

        fun fromJson(o: JSONObject): Decal? {
            val img = o.optString("img")
            val target = o.optString("target")
            if (target.isEmpty() || !(img.startsWith(SHAPE) || img.startsWith(FILE))) return null
            if (img.startsWith(FILE) && !Decals.safeName(img.removePrefix(FILE))) return null
            fun f(k: String, d: Float, lo: Float, hi: Float) = (if (o.has(k)) o.optDouble(k, d.toDouble()).toFloat() else d).coerceIn(lo, hi)
            return Decal(
                id = o.optString("id").ifEmpty { Decals.newId() },
                target = target,
                img = img,
                x = f("x", 0.5f, -1f, 2f),
                y = f("y", 0.5f, -1f, 2f),
                size = f("size", 0.5f, 0.03f, 5f),
                rotation = f("rotation", 0f, -720f, 720f),
                alpha = f("alpha", 1f, 0.05f, 1f),
                color = try { Color.parseColor(o.optString("color", "#FFFF8FA9")) } catch (_: Exception) { 0xFFFF8FA9.toInt() },
                front = o.optBoolean("front", false),
                flip = o.optBoolean("flip", false),
                stretch = f("stretch", 1f, 0.1f, 10f),
            )
        }
    }
}

object Decals {
    /** 匯入的圖最大邊長（太大的會縮小，省記憶體） */
    const val MAX_PX = 512

    /** 內建圖案：代號、名字 */
    val SHAPES = listOf(
        "cat" to "貓咪臉",
        "catline" to "線條貓咪",
        "paw" to "貓爪",
        "heart" to "愛心",
        "star" to "星星",
        "sparkle" to "閃光",
        "flower" to "小花",
        "bow" to "蝴蝶結",
        "moon" to "月亮",
    )

    private var counter = 0
    fun newId(): String = "d" + System.currentTimeMillis().toString(36) + (counter++).toString(36)

    /** 只接受自己產生的檔名（避免主題檔裡夾帶奇怪的路徑） */
    fun safeName(n: String) = n.matches(Regex("[A-Za-z0-9_\\-]{1,64}\\.(png|gif)"))

    /** 會動的 GIF 最大多大（太大的話，主題檔會超過匯入的上限） */
    const val MAX_GIF_BYTES = 4 * 1024 * 1024

    fun isGif(bytes: ByteArray) = bytes.size > 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()

    fun dir(ctx: Context) = File(ctx.filesDir, "decals").apply { mkdirs() }

    fun load(prefs: Prefs): List<Decal> = try {
        val arr = JSONArray(prefs.sp.getString("decals", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Decal::fromJson) }
    } catch (_: Exception) {
        emptyList()
    }

    fun toJson(list: List<Decal>) = JSONArray().apply { list.forEach { put(it.toJson()) } }

    fun save(prefs: Prefs, list: List<Decal>) {
        prefs.sp.edit().putString("decals", toJson(list).toString()).apply()
    }

    /** 把圖縮到 MAX_PX 以內存成 PNG，回傳檔名 */
    fun storeBitmap(ctx: Context, src: Bitmap): String {
        val s = minOf(1f, MAX_PX.toFloat() / maxOf(src.width, src.height))
        val bmp = if (s < 1f) Bitmap.createScaledBitmap(src, maxOf(1, (src.width * s).toInt()), maxOf(1, (src.height * s).toInt()), true) else src
        var name = "decal_${System.currentTimeMillis()}.png"
        var n = 1
        while (File(dir(ctx), name).exists()) name = "decal_${System.currentTimeMillis()}_${n++}.png"
        File(dir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    /** 從檔案位元組（例如主題檔裡的）存一張圖；不是圖片就回傳 null */
    /**
     * @param keepGif true = GIF 照原檔存（按鍵動畫用，會動）；false = 一律轉成 PNG（只留第一格）
     *   GIF 太大（超過 MAX_GIF_BYTES）也會轉成 PNG。
     */
    fun storeBytes(ctx: Context, bytes: ByteArray, keepGif: Boolean = false): String? {
        val bmp = decodeCapped(bytes) ?: return null // 順便確認真的是圖片
        if (keepGif && isGif(bytes) && bytes.size <= MAX_GIF_BYTES) {
            var name = "decal_${System.currentTimeMillis()}.gif"
            var n = 1
            while (File(dir(ctx), name).exists()) name = "decal_${System.currentTimeMillis()}_${n++}.gif"
            File(dir(ctx), name).writeBytes(bytes)
            return name
        }
        return storeBitmap(ctx, bmp)
    }

    /** 解碼時先縮小，太大的照片才不會把記憶體吃光 */
    fun decodeCapped(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 存起來的圖檔原本的內容（PNG 或 GIF） */
    fun fileBytes(ctx: Context, name: String): ByteArray? =
        File(dir(ctx), name).takeIf { safeName(name) && it.exists() }?.readBytes()

    /** 讀圖（有快取：同一張圖不會一直重讀） */
    fun bitmaps(ctx: Context, list: List<Decal>, old: Map<String, Bitmap> = emptyMap()): Map<String, Bitmap> {
        val out = HashMap<String, Bitmap>()
        for (d in list) {
            if (d.isShape || out.containsKey(d.fileName)) continue
            val b = old[d.fileName] ?: File(dir(ctx), d.fileName).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            if (b != null) out[d.fileName] = b
        }
        return out
    }

    /** 刪掉沒有任何圖案、按鍵動畫在用的圖檔 */
    fun cleanup(ctx: Context, list: List<Decal>) {
        val used = list.filter { !it.isShape }.map { it.fileName }.toMutableSet()
        used += effectFiles(Prefs(ctx))
        dir(ctx).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    /** 按鍵動畫用到的圖檔（全部按鍵共用的、單顆鍵自己的） */
    fun effectFiles(prefs: Prefs): Set<String> {
        val out = HashSet<String>()
        prefs.customEffect.takeIf { it.isImage }?.let { out.add(it.fileName) }
        for (st in KeyStyles.load(prefs).map.values) st.effect?.custom?.takeIf { it.isImage }?.let { out.add(it.fileName) }
        return out
    }

    // ---------- 畫 ----------

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** 圖案在畫面上的大小（還沒旋轉前） */
    /**
     * 圖案在畫面上的寬高（還沒旋轉前）。
     * @param aspect 原本的高/寬比例（不給的話：圖片照圖片的比例，內建圖案是正方形）
     */
    fun sizeOf(d: Decal, refH: Float, bmp: Bitmap?, aspect: Float? = null): Pair<Float, Float> {
        val w = d.size * refH
        val base = aspect ?: if (bmp != null && bmp.width > 0) bmp.height.toFloat() / bmp.width else 1f
        return w to w * base * d.stretch
    }

    fun centerOf(d: Decal, target: RectF) = (target.left + d.x * target.width()) to (target.top + d.y * target.height())

    fun draw(c: Canvas, d: Decal, target: RectF, refH: Float, bmp: Bitmap?) {
        val (cx, cy) = centerOf(d, target)
        val (w, h) = sizeOf(d, refH, bmp)
        if (!d.isShape && bmp == null) return
        c.save()
        c.translate(cx, cy)
        c.rotate(d.rotation)
        if (d.flip) c.scale(-1f, 1f)
        val r = RectF(-w / 2, -h / 2, w / 2, h / 2)
        if (bmp != null) {
            paint.alpha = (d.alpha * 255).toInt()
            paint.colorFilter = null
            c.drawBitmap(bmp, null, r, paint)
        } else {
            // 內建圖案先畫成正方形，再上下拉伸
            if (w > 0f) c.scale(1f, h / w)
            DecalShapes.draw(c, d.shapeName, RectF(-w / 2, -w / 2, w / 2, w / 2), d.color, d.alpha)
        }
        c.restore()
    }

    /** 手指點的位置有沒有在這個圖案上（會考慮旋轉） */
    fun hit(d: Decal, target: RectF, refH: Float, bmp: Bitmap?, x: Float, y: Float, slop: Float, aspect: Float? = null): Boolean {
        val (cx, cy) = centerOf(d, target)
        val (w, h) = sizeOf(d, refH, bmp, aspect)
        val a = Math.toRadians(-d.rotation.toDouble())
        val dx = x - cx
        val dy = y - cy
        val lx = (dx * cos(a) - dy * sin(a)).toFloat()
        val ly = (dx * sin(a) + dy * cos(a)).toFloat()
        val hw = maxOf(w / 2, slop)
        val hh = maxOf(h / 2, slop)
        return lx in -hw..hw && ly in -hh..hh
    }

    // ---------- 存進主題檔 ----------

    fun exportJson(ctx: Context, list: List<Decal>): JSONObject {
        val images = JSONObject()
        for (d in list) if (!d.isShape && !images.has(d.fileName)) {
            fileBytes(ctx, d.fileName)?.let { images.put(d.fileName, android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
        }
        return JSONObject().put("items", toJson(list)).put("images", images)
    }

    /** 從主題檔讀回來：圖檔重新存一份（換新檔名），回傳圖案清單和壞掉的張數 */
    fun importJson(ctx: Context, o: JSONObject): Pair<List<Decal>, Int> {
        val images = o.optJSONObject("images") ?: JSONObject()
        val rename = HashMap<String, String?>()
        var broken = 0
        val arr = o.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<Decal>()
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i)?.let(Decal::fromJson) ?: continue
            if (d.isShape) { out.add(d); continue }
            val newName = rename.getOrPut(d.fileName) {
                try {
                    val b64 = images.optString(d.fileName)
                    if (b64.isEmpty()) null else storeBytes(ctx, android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                } catch (_: Exception) { null }
            }
            if (newName == null) { broken++; continue }
            out.add(d.copy(img = Decal.FILE + newName))
        }
        return out to broken
    }

    fun toBytes(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/** 內建的小圖案：全部用線條和形狀畫，所有手機看起來都一樣，也可以換顏色 */
object DecalShapes {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun lighter(c: Int, t: Float): Int {
        fun m(v: Int) = (v + (255 - v) * t).toInt()
        return Color.argb(Color.alpha(c), m(Color.red(c)), m(Color.green(c)), m(Color.blue(c)))
    }

    private fun darker(c: Int, t: Float): Int {
        fun m(v: Int) = (v * (1 - t)).toInt()
        return Color.argb(Color.alpha(c), m(Color.red(c)), m(Color.green(c)), m(Color.blue(c)))
    }

    fun draw(c: Canvas, name: String, r: RectF, color: Int, alpha: Float) {
        val a = (alpha * 255).toInt()
        fill.color = color
        fill.alpha = a
        val s = minOf(r.width(), r.height()) / 2 // 半徑
        val cx = r.centerX()
        val cy = r.centerY()
        when (name) {
            "heart" -> c.drawPath(heart(cx, cy, s), fill)
            "star" -> c.drawPath(star(cx, cy, s, s * 0.45f, 5), fill)
            "sparkle" -> c.drawPath(sparkle(cx, cy, s), fill)
            "paw" -> {
                c.drawOval(RectF(cx - s * 0.52f, cy - s * 0.02f, cx + s * 0.52f, cy + s * 0.78f), fill)
                for ((dx, dy) in listOf(-0.68f to -0.22f, -0.26f to -0.62f, 0.26f to -0.62f, 0.68f to -0.22f)) {
                    c.drawOval(RectF(cx + dx * s - s * 0.2f, cy + dy * s - s * 0.25f, cx + dx * s + s * 0.2f, cy + dy * s + s * 0.25f), fill)
                }
            }
            "flower" -> {
                for (i in 0 until 5) {
                    val ang = Math.toRadians(-90.0 + i * 72)
                    c.drawCircle(cx + (cos(ang) * s * 0.52f).toFloat(), cy + (sin(ang) * s * 0.52f).toFloat(), s * 0.42f, fill)
                }
                fill.color = lighter(color, 0.75f)
                fill.alpha = a
                c.drawCircle(cx, cy, s * 0.3f, fill)
            }
            "bow" -> {
                val p = Path()
                p.moveTo(cx, cy)
                p.cubicTo(cx - s * 0.5f, cy - s * 0.85f, cx - s * 1.05f, cy - s * 0.5f, cx - s * 0.95f, cy)
                p.cubicTo(cx - s * 1.05f, cy + s * 0.5f, cx - s * 0.5f, cy + s * 0.85f, cx, cy)
                p.cubicTo(cx + s * 0.5f, cy - s * 0.85f, cx + s * 1.05f, cy - s * 0.5f, cx + s * 0.95f, cy)
                p.cubicTo(cx + s * 1.05f, cy + s * 0.5f, cx + s * 0.5f, cy + s * 0.85f, cx, cy)
                c.drawPath(p, fill)
                // 垂下來的兩條緞帶
                val tail = Path()
                tail.moveTo(cx - s * 0.12f, cy)
                tail.lineTo(cx - s * 0.45f, cy + s * 0.95f)
                tail.lineTo(cx - s * 0.2f, cy + s * 0.85f)
                tail.lineTo(cx, cy + s * 0.2f)
                tail.lineTo(cx + s * 0.2f, cy + s * 0.85f)
                tail.lineTo(cx + s * 0.45f, cy + s * 0.95f)
                tail.lineTo(cx + s * 0.12f, cy)
                tail.close()
                c.drawPath(tail, fill)
                fill.color = darker(color, 0.15f)
                fill.alpha = a
                c.drawRoundRect(RectF(cx - s * 0.2f, cy - s * 0.24f, cx + s * 0.2f, cy + s * 0.24f), s * 0.1f, s * 0.1f, fill)
            }
            "moon" -> {
                val p = Path().apply { addCircle(cx, cy, s * 0.9f, Path.Direction.CW) }
                val cut = Path().apply { addCircle(cx + s * 0.45f, cy - s * 0.3f, s * 0.75f, Path.Direction.CW) }
                p.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(p, fill)
            }
            "cat" -> {
                c.drawPath(catHead(cx, cy, s), fill)
                // 臉：比底色深一點的眼睛、鼻子
                fill.color = darker(color, 0.55f)
                fill.alpha = a
                c.drawCircle(cx - s * 0.32f, cy + s * 0.08f, s * 0.09f, fill)
                c.drawCircle(cx + s * 0.32f, cy + s * 0.08f, s * 0.09f, fill)
                val nose = Path().apply {
                    moveTo(cx - s * 0.08f, cy + s * 0.25f); lineTo(cx + s * 0.08f, cy + s * 0.25f); lineTo(cx, cy + s * 0.34f); close()
                }
                c.drawPath(nose, fill)
            }
            "catline" -> {
                // 像手畫的線條貓：輪廓＋鬍鬚
                stroke.color = color
                stroke.alpha = a
                stroke.strokeWidth = s * 0.09f
                c.drawPath(catHead(cx, cy, s * 0.92f), stroke)
                for (side in listOf(-1f, 1f)) {
                    c.drawLine(cx + side * s * 0.45f, cy + s * 0.22f, cx + side * s * 1.0f, cy + s * 0.1f, stroke)
                    c.drawLine(cx + side * s * 0.45f, cy + s * 0.36f, cx + side * s * 1.0f, cy + s * 0.42f, stroke)
                }
                c.drawCircle(cx - s * 0.3f, cy + s * 0.05f, s * 0.04f, stroke)
                c.drawCircle(cx + s * 0.3f, cy + s * 0.05f, s * 0.04f, stroke)
            }
            else -> c.drawCircle(cx, cy, s * 0.8f, fill)
        }
    }

    private fun catHead(cx: Float, cy: Float, s: Float) = Path().apply {
        // 圓臉＋兩個三角耳朵（耳朵根部埋進臉裡，看起來是一體的）
        moveTo(cx - s * 0.88f, cy + s * 0.15f)
        lineTo(cx - s * 0.78f, cy - s * 0.85f)
        lineTo(cx - s * 0.3f, cy - s * 0.48f)
        quadTo(cx, cy - s * 0.58f, cx + s * 0.3f, cy - s * 0.48f)
        lineTo(cx + s * 0.78f, cy - s * 0.85f)
        lineTo(cx + s * 0.88f, cy + s * 0.15f)
        cubicTo(cx + s * 0.9f, cy + s * 0.65f, cx + s * 0.45f, cy + s * 0.78f, cx, cy + s * 0.78f)
        cubicTo(cx - s * 0.45f, cy + s * 0.78f, cx - s * 0.9f, cy + s * 0.65f, cx - s * 0.88f, cy + s * 0.15f)
        close()
    }

    private fun heart(cx: Float, cy: Float, s: Float) = Path().apply {
        moveTo(cx, cy + s * 0.85f)
        cubicTo(cx - s * 1.1f, cy + s * 0.05f, cx - s * 0.75f, cy - s * 0.95f, cx, cy - s * 0.38f)
        cubicTo(cx + s * 0.75f, cy - s * 0.95f, cx + s * 1.1f, cy + s * 0.05f, cx, cy + s * 0.85f)
        close()
    }

    private fun star(cx: Float, cy: Float, outer: Float, inner: Float, n: Int) = Path().apply {
        for (i in 0 until n * 2) {
            val rad = if (i % 2 == 0) outer else inner
            val ang = Math.toRadians(-90.0 + i * 180.0 / n)
            val x = cx + (cos(ang) * rad).toFloat()
            val y = cy + (sin(ang) * rad).toFloat() + outer * 0.08f
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun sparkle(cx: Float, cy: Float, s: Float) = Path().apply {
        // 四個尖角、邊是往內凹的弧線
        moveTo(cx, cy - s)
        quadTo(cx + s * 0.12f, cy - s * 0.12f, cx + s, cy)
        quadTo(cx + s * 0.12f, cy + s * 0.12f, cx, cy + s)
        quadTo(cx - s * 0.12f, cy + s * 0.12f, cx - s, cy)
        quadTo(cx - s * 0.12f, cy - s * 0.12f, cx, cy - s)
        close()
    }
}

/** 小縮圖：清單、選圖案的視窗用 */
class DecalThumb(ctx: Context, var decal: Decal, var bmp: Bitmap? = null) : android.view.View(ctx) {
    var bgColor: Int = 0x14888888
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        bgPaint.color = bgColor
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 10 * d, 10 * d, bgPaint)
        val box = minOf(width, height) * 0.7f
        // 縮圖裡不轉、不透明，看得清楚就好
        val show = decal.copy(x = 0.5f, y = 0.5f, rotation = 0f, alpha = 1f, size = 1f)
        val (w, h) = Decals.sizeOf(show, box, bmp)
        val k = if (h > box) box / h else 1f
        Decals.draw(c, show.copy(size = k), RectF(0f, 0f, width.toFloat(), height.toFloat()), box, bmp)
    }
}
