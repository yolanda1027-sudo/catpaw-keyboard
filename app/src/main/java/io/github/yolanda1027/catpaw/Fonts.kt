package io.github.yolanda1027.catpaw

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * 可以選的字體。
 *
 * 內建（都附在 App 裡，所有手機看起來都一樣，授權都是 SIL Open Font License 1.1，全文在 third_party/fonts/）：
 * - 粉圓：jf open 粉圓 2.1（justfont），原檔未修改
 * - 芫荽：Iansui，只保留常用字（約 7,900 字），其他字會自動用系統字體顯示
 * - 霞鶩文楷：LXGW WenKai TC，同上
 *
 * 也可以自己上傳 .ttf / .otf，存在 App 的私人資料夾（user_fonts/）。
 */
object Fonts {
    private val BUILT_IN = listOf(
        "system" to "系統預設",
        "huninn" to "粉圓（圓圓的、很可愛）",
        "iansui" to "芫荽（手寫感）",
        "wenkai" to "霞鶩文楷（楷書、書卷感）",
    )

    private val files = mapOf(
        "huninn" to "fonts/jf-openhuninn.ttf",
        "iansui" to "fonts/iansui.ttf",
        "wenkai" to "fonts/lxgw-wenkai-tc.ttf",
    )

    private const val USER_PREFIX = "user:"
    private const val MAX_BYTES = 40L * 1024 * 1024

    private val cache = HashMap<String, Typeface>()

    private fun userDir(ctx: Context) = File(ctx.filesDir, "user_fonts").apply { mkdirs() }

    /** 上傳的字體檔（檔名一律用英數字，顯示名稱另外存在「檔名.name」裡） */
    fun userFonts(ctx: Context): List<File> =
        userDir(ctx).listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".name") }
            ?.sortedBy { it.name } ?: emptyList()

    fun displayName(f: File): String =
        File(f.parentFile, f.name + ".name").takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
            ?: f.nameWithoutExtension

    /** 內建＋自己上傳的，(代號, 顯示名稱) */
    fun options(ctx: Context): List<Pair<String, String>> =
        BUILT_IN + userFonts(ctx).map { USER_PREFIX + it.name to "我的字體：" + displayName(it) }

    fun get(ctx: Context, id: String): Typeface {
        if (id.startsWith(USER_PREFIX)) {
            val f = File(userDir(ctx), id.removePrefix(USER_PREFIX))
            if (!f.exists()) return Typeface.DEFAULT
            return cache.getOrPut(id + "@" + f.lastModified()) {
                Typeface.Builder(f).build() ?: Typeface.DEFAULT
            }
        }
        val file = files[id] ?: return Typeface.DEFAULT
        return cache.getOrPut(id) {
            try {
                Typeface.createFromAsset(ctx.assets, file)
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
    }

    /** 上傳的結果：成功就有 id，另外附上要跟使用者說的話 */
    class ImportResult(val id: String?, val message: String)

    fun import(ctx: Context, uri: Uri): ImportResult {
        var name = "font"
        var size = -1L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (!c.isNull(it)) size = c.getLong(it) }
                }
            }
        } catch (_: Exception) {
        }
        if (name == "font") uri.lastPathSegment?.let { name = it.substringAfterLast('/') }
        if (size > MAX_BYTES) return ImportResult(null, "檔案太大了（超過 40MB），換一個試試")

        // 副檔名不一定可靠（有些雲端硬碟的檔名沒有副檔名），真正的檢查是下面「能不能當字體打開」
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it in listOf("ttf", "otf", "ttc") } ?: "ttf"
        val base = (if (name.contains('.')) name.substringBeforeLast('.') else name).replace(Regex("[^\\p{L}\\p{N} _\\-（）()]"), "_").take(40).ifBlank { "font" }

        val tmp = File(userDir(ctx), ".importing")
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return ImportResult(null, "讀不到這個檔案")
            val tf = Typeface.Builder(tmp).build()
                ?: run { tmp.delete(); return ImportResult(null, "這個檔案不是可以用的字體（請選 .ttf 或 .otf）") }
            var dest = File(userDir(ctx), "font_${System.currentTimeMillis()}.$ext")
            while (dest.exists()) dest = File(userDir(ctx), "font_${System.currentTimeMillis() + 1}.$ext")
            if (!tmp.renameTo(dest)) { tmp.delete(); return ImportResult(null, "存檔失敗，再試一次") }
            File(userDir(ctx), dest.name + ".name").writeText(base)

            // 看看有沒有注音、中文
            val p = Paint().apply { typeface = tf }
            val zhuyin = p.hasGlyph("ㄅ") && p.hasGlyph("ㄤ")
            val han = p.hasGlyph("貓") && p.hasGlyph("的")
            val note = when {
                zhuyin && han -> "有注音也有中文 👍"
                han -> "這個字體沒有注音符號，按鍵上的注音會用系統字體顯示"
                zhuyin -> "這個字體沒有中文字，選字列的中文會用系統字體顯示"
                else -> "這個字體沒有注音也沒有中文，只有英文、數字會變"
            }
            ImportResult(USER_PREFIX + dest.name, "已加入「$base」：$note")
        } catch (e: Exception) {
            tmp.delete()
            ImportResult(null, "上傳失敗：${e.message ?: "未知的錯誤"}")
        }
    }

    fun delete(ctx: Context, id: String) {
        if (!id.startsWith(USER_PREFIX)) return
        File(userDir(ctx), id.removePrefix(USER_PREFIX)).delete()
        File(userDir(ctx), id.removePrefix(USER_PREFIX) + ".name").delete()
        cache.keys.removeAll { it.startsWith("$id@") }
    }

    fun isUser(id: String) = id.startsWith(USER_PREFIX)
}
