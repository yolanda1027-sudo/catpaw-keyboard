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
}

/**
 * 三組字體：中文、注音、英文和符號。
 * 畫字的時候會把一段文字切成「同一組的字連在一起」的小段，每段用自己的字體畫。
 */
class FontSet(val zh: Typeface, val zhuyin: Typeface, val en: Typeface) {
    private val bolds = HashMap<Typeface, Typeface>()

    fun bold(t: Typeface): Typeface = bolds.getOrPut(t) { Typeface.create(t, Typeface.BOLD) }

    /** 三組都一樣：不用切段，直接畫 */
    val uniform: Boolean get() = zh === zhuyin && zhuyin === en

    fun forCodePoint(cp: Int): Typeface = when {
        isZhuyin(cp) -> zhuyin
        isChinese(cp) -> zh
        else -> en
    }

    /** 切成 (文字, 字體) 的小段；空白和附加符號跟著前一段，不另外切 */
    fun runs(s: String): List<Pair<String, Typeface>> {
        if (s.isEmpty() || uniform) return listOf(s to en)
        val out = ArrayList<Pair<String, Typeface>>()
        val sb = StringBuilder()
        var cur: Typeface? = null
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val tf = if (cur != null && attaches(cp)) cur else forCodePoint(cp)
            if (cur != null && tf !== cur) {
                out.add(sb.toString() to cur)
                sb.setLength(0)
            }
            cur = tf
            sb.append(s, i, i + n)
            i += n
        }
        if (cur != null) out.add(sb.toString() to cur)
        return out
    }

    companion object {
        val DEFAULT = FontSet(Typeface.DEFAULT, Typeface.DEFAULT, Typeface.DEFAULT)

        /** 注音符號、聲調 */
        fun isZhuyin(cp: Int) = cp in 0x3100..0x312F || cp in 0x31A0..0x31BF ||
            cp == 0x02CA || cp == 0x02C7 || cp == 0x02CB || cp == 0x02D9 || cp == 0x02C9

        /** 中文字、全形標點（「」，。、） */
        fun isChinese(cp: Int) = cp in 0x3400..0x9FFF || cp in 0xF900..0xFAFF || cp in 0x20000..0x3FFFF ||
            cp in 0x3000..0x303F || cp in 0xFF00..0xFFEF || cp in 0xFE30..0xFE4F

        /** 要跟著前一個字的：空白、附加符號（顏文字常用）、變體選擇符、零寬連接字 */
        private fun attaches(cp: Int): Boolean {
            if (cp == ' '.code || cp == 0x200D || cp in 0xFE00..0xFE0F) return true
            return when (Character.getType(cp)) {
                Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> true
                else -> false
            }
        }
    }
}

