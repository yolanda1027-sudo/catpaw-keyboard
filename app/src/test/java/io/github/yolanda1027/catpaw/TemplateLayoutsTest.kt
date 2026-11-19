package io.github.yolanda1027.catpaw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 把 8 頁鍵盤的排列（每一排有哪些鍵、各佔多寬）匯出成 build/template-layouts.json，
 * 給 tools/make_theme_templates.py 畫主題設計用的版型圖。用 App 真正的排列，版型圖才不會跟鍵盤對不上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemplateLayoutsTest {
    /** 版型圖上寫的字：內容格（表情、顏文字、貼圖…）寫它是什麼，功能鍵寫名字 */
    private fun keyJson(k: Key, content: String?): JSONObject {
        val kind = when {
            k.act == Act.GAP -> "gap"
            k.style == Style.FUNC || k.style == Style.ENTER || k.style == Style.SPACE -> "func"
            else -> "key"
        }
        val label = when {
            k.act == Act.GAP -> ""
            k.icon == Icon.BACKSPACE -> "刪除"
            k.icon == Icon.ENTER -> "Enter"
            k.icon == Icon.SHIFT -> "Shift"
            k.icon == Icon.EMOJI -> "表情"
            k.act == Act.SPACE -> "空白鍵"
            kind == "key" && content != null -> content
            else -> k.label
        }
        return JSONObject().put("label", label).put("weight", k.weight.toDouble()).put("kind", kind)
    }

    private fun page(rows: List<List<Key>>, tabs: Any, quick: Boolean, content: String? = null) = JSONObject()
        // 最下面那排（返回、，、空白…）是一般按鍵，照原本的字寫
        .put("rows", JSONArray().apply {
            rows.forEachIndexed { ri, r -> put(JSONArray().apply { r.forEach { put(keyJson(it, if (ri < rows.size - 1) content else null)) } }) }
        })
        .put("tabs", tabs)
        .put("quick", quick)

    @Test fun exportLayouts() {
        Symbols.loadKaomoji(File("src/main/assets/kaomoji.txt").readText())
        Emojis.load(File("src/main/assets/emoji.txt").readText())
        fun tabs(list: List<String?>) = JSONArray().apply { list.forEach { put(it ?: "最近") } }

        val sym = Symbols.categories[1]
        val kao = Symbols.categories[Symbols.KAOMOJI]
        val kaoItems = Symbols.kaomojiGroups.first().second.take(Symbols.PER_PAGE_WIDE)
        val emoItems = Emojis.page(Emojis.groups.first().second, 0)
        val stickers = List(MyStickers.ROWS) { List(MyStickers.COLS) { Key("", Act.STICKER, text = "x.png") } } + listOf(Layouts.emojiBottom)
        val clips = List(Layouts.CLIPS_PER_PAGE) { ClipHistory.Item("剪貼簿紀錄 ${it + 1}", 0L, false) }
        // 表情分類列：最近、我的貼圖、各分類（表情符號在版型圖上寫成「分類」）
        val emoTabs = listOf("最近", "貼圖") + Emojis.groups.map { "分類" }

        val out = JSONObject()
            .put("zhuyin", page(Layouts.ZHUYIN, "zhen", true))
            .put("english", page(Layouts.ENGLISH, "zhen", true))
            .put("symbol", page(Symbols.rows(sym.pages[0], false), tabs(Symbols.categories.map { it.tab }), false))
            .put("kaomoji", page(Symbols.rows(kaoItems, true), tabs(listOf("返回", null) + Symbols.kaomojiGroups.map { it.first }), false, "顏文字"))
            .put("emoji", page(Emojis.rows(emoItems), tabs(emoTabs), false, "表情"))
            .put("mystickers", page(stickers, tabs(emoTabs), false, "貼圖"))
            .put("clipboard", page(Layouts.clipRows(clips, 0), tabs(listOf("剪貼簿　點一下貼上，長按釘選、排順序")), false, "剪貼簿的一筆"))
            .put("numpad", page(Layouts.NUMPAD, tabs(listOf("數字")), false))
        File("build").mkdirs()
        File("build/template-layouts.json").writeText(out.toString(1))
        assertEquals(8, out.length())
    }
}
