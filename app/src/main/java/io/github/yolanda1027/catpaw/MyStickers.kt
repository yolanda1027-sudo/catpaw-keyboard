package io.github.yolanda1027.catpaw

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * 我的貼圖：自己上傳的圖片，放在表情鍵盤的「🖼️」分類。
 * 點一下會把圖片送進輸入框（App 支援的話），不支援的話複製到剪貼簿讓使用者貼上。
 *
 * 存在 filesDir/my_stickers/：PNG 縮到 MAX_PX 以內；GIF 照原檔（會動，4MB 以內）。
 */
object MyStickers {
    const val MAX_PX = 512
    const val COLS = 5
    const val ROWS = 4
    const val PER_PAGE = COLS * ROWS

    fun dir(ctx: Context) = File(ctx.filesDir, "my_stickers").apply { mkdirs() }

    fun safeName(n: String) = Decals.safeName(n)

    /** 全部（新的在前面） */
    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles()?.filter { it.isFile && safeName(it.name) }?.sortedByDescending { it.name }?.map { it.name } ?: emptyList()

    fun file(ctx: Context, name: String): File? = File(dir(ctx), name).takeIf { safeName(name) && it.exists() }

    fun mime(name: String) = if (name.endsWith(".gif")) "image/gif" else "image/png"

    /** 存一張；不是圖片就回傳 null。GIF 太大會轉成 PNG（只留第一格） */
    fun add(ctx: Context, bytes: ByteArray): String? {
        val bmp = Decals.decodeCapped(bytes) ?: return null
        val ts = System.currentTimeMillis()
        if (Decals.isGif(bytes) && bytes.size <= Decals.MAX_GIF_BYTES) {
            val name = unique(ctx, "sticker_$ts", "gif")
            File(dir(ctx), name).writeBytes(bytes)
            return name
        }
        val s = minOf(1f, MAX_PX.toFloat() / maxOf(bmp.width, bmp.height))
        val out = if (s < 1f) Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * s).toInt()), maxOf(1, (bmp.height * s).toInt()), true) else bmp
        val name = unique(ctx, "sticker_$ts", "png")
        File(dir(ctx), name).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    private fun unique(ctx: Context, base: String, ext: String): String {
        var name = "$base.$ext"
        var n = 1
        while (File(dir(ctx), name).exists()) name = "${base}_${n++}.$ext"
        return name
    }

    fun delete(ctx: Context, name: String) {
        file(ctx, name)?.delete()
        cache.remove(name)
    }

    private val cache = HashMap<String, Bitmap?>()

    /** 鍵盤上顯示用的小圖（GIF 是第一格） */
    fun bitmap(ctx: Context, name: String): Bitmap? = cache.getOrPut(name) {
        file(ctx, name)?.let { f ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 200) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    fun pageCount(ctx: Context) = ((list(ctx).size + PER_PAGE - 1) / PER_PAGE).coerceAtLeast(1)

    private val bottomCache = HashMap<String, List<List<Key>>>()

    /** 一頁排成鍵盤：4 排各 5 個；還沒有貼圖的話放一顆「去上傳」 */
    fun rows(ctx: Context, page: Int): List<List<Key>> {
        val items = list(ctx).drop(page * PER_PAGE).take(PER_PAGE)
        if (items.isEmpty()) {
            return listOf(
                listOf(Key("＋ 還沒有貼圖，點這裡上傳自己的圖片", Act.OPEN_STICKERS, weight = 10f, style = Style.CLIP)),
                listOf(Key("", Act.GAP, weight = 10f)),
                listOf(Key("", Act.GAP, weight = 10f)),
                listOf(Key("", Act.GAP, weight = 10f)),
                Layouts.emojiBottom,
            )
        }
        fun k(i: Int) = items.getOrNull(i)?.let { Key("", Act.STICKER, text = it) } ?: Key("", Act.GAP)
        return List(ROWS) { r -> List(COLS) { c -> k(r * COLS + c) } } + listOf(Layouts.emojiBottom)
    }
}

/**
 * 把「我的貼圖」交給別的 App 讀（只讀、只給這個資料夾裡的檔案）。
 * 沒有公開（exported=false），別的 App 只有在鍵盤送圖或剪貼簿貼上時，才會拿到那一張的暫時讀取權限。
 */
class StickerProvider : ContentProvider() {
    override fun onCreate() = true

    private fun fileOf(uri: Uri): File? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        return MyStickers.file(ctx, name)
    }

    override fun getType(uri: Uri): String = MyStickers.mime(uri.lastPathSegment ?: "")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = fileOf(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = fileOf(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map { if (it == OpenableColumns.SIZE) f.length() else if (it == OpenableColumns.DISPLAY_NAME) f.name else null }
        return MatrixCursor(cols).apply { addRow(row.toTypedArray()) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        fun uri(ctx: Context, name: String): Uri = Uri.parse("content://${ctx.packageName}.stickers/s/$name")
    }
}
