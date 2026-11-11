package io.github.yolanda1027.catpaw

/**
 * 符號鍵盤（像 Google 注音）：上面一排分類，每個分類有好幾頁，左右滑換頁。
 * 每頁 28 格：第一排 10 個、第二排 10 個、第三排 8 個（右邊是刪除鍵）。
 * 顏文字比較長，每頁 3 排 × 3 個。
 */
object Symbols {

    class Category(
        /** 分類列上顯示的字；null = 畫時鐘圖示（最近使用） */
        val tab: String?,
        val pages: List<List<String>>,
        /** 顏文字這類比較長的，每排放 3 個 */
        val wide: Boolean = false,
    )

    const val PER_PAGE = 28
    const val PER_PAGE_WIDE = 9
    const val RECENT = 0
    const val KAOMOJI = 7

    /** 顏文字的小分類：(名稱, 顏文字列表) */
    var kaomojiGroups: List<Pair<String, List<String>>> = emptyList()
        private set

    /** 讀 kaomoji.txt：[分類名稱] 開頭，下面一行一個 */
    fun loadKaomoji(text: String) {
        val groups = ArrayList<Pair<String, MutableList<String>>>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                groups.add(line.substring(1, line.length - 1) to ArrayList())
            } else {
                groups.lastOrNull()?.second?.add(line)
            }
        }
        kaomojiGroups = groups.map { it.first to it.second.toList() }
    }

    private fun page(s: String) = s.trim().split(Regex("\\s+"))

    val categories: List<Category> = listOf(
        Category(null, emptyList()), // 最近使用：內容由 CatImeService 填
        Category(
            "1?#",
            listOf(
                page("1 2 3 4 5 6 7 8 9 0  ! @ # $ % ^ & * ( )  「 」 。 、 ： ； ？ ！"),
                page("～ = + - / \\ | · 『 』  [ ] { } < > … — “ ”  ‘ ’ _ ' \" : ; ?"),
                page("` ~ ／ ¥ € £ ¢ © ® ™  ¡ ¿ ‖ ‗ † ‡ ‥ ※ ‼ ⁇  ⁈ ℃ ℉ ° § № 々 〒"),
                page("， 、 。 ． ‧ ； ： ？ ！ ︰  … ‥ ﹐ ﹑ ﹒ ﹔ ﹕ ﹖ ﹗ ｜  – ︱ — ︳ ╴ ︴ ﹏ ～"),
            ),
        ),
        Category(
            "( )",
            listOf(
                page("( ) [ ] { } （ ） ［ ］  ｛ ｝ ❨ ❩ ❪ ❫ ❴ ❵ ‘ ’  “ ” ❛ ❜ ❝ ❞ < >"),
                page("〈 〉 《 》 〔 〕 【 】 〖 〗  「 」 『 』 ︵ ︶ ︷ ︸ ︹ ︺  ︻ ︼ ︽ ︾ ︿ ﹀ ﹁ ﹂"),
                page("﹃ ﹄ ﹙ ﹚ ﹛ ﹜ ﹝ ﹞ ‹ ›  « » ｢ ｣ ⟨ ⟩ ⟪ ⟫ ⌈ ⌉  ⌊ ⌋ ⦅ ⦆ ⦗ ⦘ ⧼ ⧽"),
            ),
        ),
        Category(
            "→",
            listOf(
                page("← ↑ → ↓ ↖ ↗ ↘ ↙ ↔ ↕  ⇐ ⇑ ⇒ ⇓ ⇖ ⇗ ⇘ ⇙ ⇔ ⇕  ↩ ↪ ↺ ↻ ⇄ ⇅ ⇆ ⇵"),
                page("➔ ➘ ➙ ➚ ➛ ➜ ➝ ➞ ➟ ➠  ➡ ➢ ➣ ➤ ➥ ➦ ➧ ➨ ➩ ➪  ⤴ ⤵ ↯ ↬ ↫ ⇢ ⇠ ⇡"),
            ),
        ),
        Category(
            "+−×÷",
            listOf(
                page("+ − × ÷ = ≠ ≈ ± ≤ ≥  < > ∞ √ ∑ ∏ ∫ ∂ ∆ ∇  ∈ ∉ ∩ ∪ ⊂ ⊃ ∠ ⊥"),
                page("% ‰ ° ′ ″ ∴ ∵ ∀ ∃ ¬  ∧ ∨ ≡ ≒ ≦ ≧ ⊕ ⊙ ∮ ∝  ½ ⅓ ¼ ¾ ² ³ ¹ ⁿ"),
            ),
        ),
        Category(
            "①",
            listOf(
                page("① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩  ⑪ ⑫ ⑬ ⑭ ⑮ ⑯ ⑰ ⑱ ⑲ ⑳  ❶ ❷ ❸ ❹ ❺ ❻ ❼ ❽"),
                page("❾ ❿ ⑴ ⑵ ⑶ ⑷ ⑸ ⑹ ⑺ ⑻  Ⅰ Ⅱ Ⅲ Ⅳ Ⅴ Ⅵ Ⅶ Ⅷ Ⅸ Ⅹ  ㈠ ㈡ ㈢ ㈣ ㈤ ㈥ ㈦ ㈧"),
                page("⒈ ⒉ ⒊ ⒋ ⒌ ⒍ ⒎ ⒏ ⒐ ⒑  ⅰ ⅱ ⅲ ⅳ ⅴ ⅵ ⅶ ⅷ ⅸ ⅹ  ㊀ ㊁ ㊂ ㊃ ㊄ ㊅ ㊆ ㊇"),
            ),
        ),
        Category(
            "◇",
            listOf(
                page("★ ☆ ● ○ ◎ ◆ ◇ ■ □ ▲  △ ▼ ▽ ♥ ♡ ♦ ♢ ♠ ♤ ♣  ♧ ♪ ♫ ♬ ✓ ✔ ✗ ✘"),
                page("☀ ☁ ☂ ☃ ✦ ✧ ❀ ✿ ❁ ✾  ❤ ❣ ☺ ☻ ☎ ✉ ✂ ✎ ✏ ⌘  ♀ ♂ ☯ ☮ ※ ◐ ◑ ❖"),
                page("▪ ▫ ◾ ◽ ▶ ◀ ▷ ◁ ► ◄  ⊿ ◢ ◣ ◤ ◥ ▣ ▤ ▥ ▦ ▧  ▨ ▩ ░ ▒ ▓ █ ▁ ▔"),
            ),
        ),
        // 顏文字：內容從 assets/kaomoji.txt 讀進來（見 loadKaomoji），分類列會換成顏文字的小分類
        Category("^ω^", emptyList(), wide = true),
    )

    private val cache = HashMap<String, List<List<Key>>>()

    private val bottomRow = listOf(
        Key("返回", Act.TO_MAIN, weight = 1.5f, style = Style.FUNC),
        Key("，", sub = "。"),
        Key("", Act.TO_EMOJI, style = Style.FUNC, icon = Icon.EMOJI),
        Key("空白", Act.SPACE, text = " ", weight = 4f, style = Style.SPACE),
        Key("。"),
        Key("", Act.ENTER, weight = 1.5f, style = Style.ENTER, icon = Icon.ENTER),
    )
    private val backspace = Key("", Act.BACKSPACE, weight = 1.5f, style = Style.FUNC, icon = Icon.BACKSPACE)

    /** 把一頁符號排成鍵盤的樣子（同一頁會重複用同一份，避免一直重排） */
    fun rows(items: List<String>, wide: Boolean): List<List<Key>> {
        val cacheKey = (if (wide) "w:" else "n:") + items.joinToString("\u0001")
        return cache.getOrPut(cacheKey) {
            fun k(i: Int) = items.getOrNull(i)?.let { Key(it, Act.TEXT) } ?: Key("", Act.GAP)
            if (wide) {
                fun kw(i: Int, w: Float) = k(i).copy(weight = w)
                listOf(
                    List(3) { kw(it, 10f / 3) },
                    List(3) { kw(3 + it, 10f / 3) },
                    List(3) { kw(6 + it, 2.8f) } + listOf(backspace.copy(weight = 1.6f)),
                    bottomRow,
                )
            } else {
                listOf(
                    List(10) { k(it) },
                    List(10) { k(10 + it) },
                    listOf(Key("", Act.GAP, weight = 0.5f)) + List(8) { k(20 + it) } + listOf(backspace),
                    bottomRow,
                )
            }
        }
    }
}
