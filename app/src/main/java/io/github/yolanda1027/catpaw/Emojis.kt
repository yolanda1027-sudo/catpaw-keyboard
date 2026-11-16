package io.github.yolanda1027.catpaw

/**
 * 表情符號鍵盤：上面一排分類（最近使用、表情、人物、動物…），每個分類好幾頁，左右滑換頁。
 * 每頁 4 排、一排 8 個（比符號大一點，比較好按），一頁 32 個；刪除鍵在最下面那排。
 *
 * 內容從 assets/emoji.txt 讀（tools/build_emoji.py 產生，資料來自 unicode.org，只收 Emoji 15.0 以前的）。
 */
object Emojis {
    const val COLS = 8
    const val PER_PAGE = COLS * 4

    /** (分類列上的圖示, 這個分類的表情符號) */
    var groups: List<Pair<String, List<String>>> = emptyList()
        private set

    /** 讀 emoji.txt：[分類圖示] 一行，下一行是用空白隔開的表情符號 */
    fun load(text: String) {
        val out = ArrayList<Pair<String, List<String>>>()
        var tab: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                tab = line.substring(1, line.length - 1)
            } else if (tab != null) {
                out.add(tab to line.split(' ').filter { it.isNotEmpty() })
                tab = null
            }
        }
        groups = out
    }

    /** 分類列：第一個是時鐘（最近使用，null），後面是各分類的圖示 */
    fun tabs(): List<String?> = listOf<String?>(null) + groups.map { it.first }

    fun pageCount(items: List<String>) = ((items.size + PER_PAGE - 1) / PER_PAGE).coerceAtLeast(1)

    fun page(items: List<String>, page: Int) = items.drop(page * PER_PAGE).take(PER_PAGE)

    private val cache = HashMap<String, List<List<Key>>>()

    /** 一頁排成鍵盤：4 排各 8 個，最下面是返回、空白、刪除那一排 */
    fun rows(items: List<String>): List<List<Key>> = cache.getOrPut(items.joinToString("\u0001")) {
        fun k(i: Int) = items.getOrNull(i)?.let { Key(it, Act.TEXT) } ?: Key("", Act.GAP)
        listOf(
            List(COLS) { k(it) },
            List(COLS) { k(COLS + it) },
            List(COLS) { k(COLS * 2 + it) },
            List(COLS) { k(COLS * 3 + it) },
            Layouts.emojiBottom,
        )
    }
}
