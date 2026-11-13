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

