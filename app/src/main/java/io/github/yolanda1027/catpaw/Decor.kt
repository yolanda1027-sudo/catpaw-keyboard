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
 * 功能鍵貼圖：縮小過的 PNG 存在 Decals.dir（跟圖案、底圖放一起），
 * 哪個功能鍵用哪張記在設定裡（Prefs.funcStickers），所以會跟著主題換。
 * 相簿裡的原圖刪掉也沒關係。
 */
object Stickers {
    private const val MAX_SIZE = 384

    /** 這個功能鍵的貼圖檔（沒有貼圖就是 null） */
    fun file(ctx: Context, prefs: Prefs, slot: StickerSlot): File? =
        prefs.funcStickers[slot.id]?.let { File(Decals.dir(ctx), it) }?.takeIf { it.exists() }

    fun load(ctx: Context, prefs: Prefs): Map<StickerSlot, Bitmap> =
        StickerSlot.values().mapNotNull { slot ->
            file(ctx, prefs, slot)?.let { f -> BitmapFactory.decodeFile(f.path)?.let { slot to it } }
        }.toMap()

    /** 存一張圖給這個功能鍵用（PNG 位元組；會縮小） */
    fun setBytes(ctx: Context, prefs: Prefs, slot: StickerSlot, bytes: ByteArray): Boolean {
        val name = Decals.storeBytes(ctx, bytes, maxPx = MAX_SIZE) ?: return false
        prefs.funcStickers = prefs.funcStickers + (slot.id to name)
        return true
    }

    /** 從相簿選的圖：縮小、存起來。成功回傳縮好的圖 */
    fun import(ctx: Context, prefs: Prefs, slot: StickerSlot, uri: Uri): Bitmap? = try {
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
            val name = Decals.storeBitmap(ctx, scaled, MAX_SIZE)
            prefs.funcStickers = prefs.funcStickers + (slot.id to name)
            scaled
        }
    } catch (_: Exception) {
        null
    }

    /** 拿掉這個功能鍵的貼圖（圖檔沒有主題在用時，之後會被清掉） */
    fun remove(prefs: Prefs, slot: StickerSlot) {
        prefs.funcStickers = prefs.funcStickers - slot.id
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

