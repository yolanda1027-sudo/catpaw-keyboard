package io.github.yolanda1027.catpaw

/** 按下去要做的事 */
enum class Act { TEXT, ZHUYIN, BACKSPACE, ENTER, SPACE, SHIFT, TO_SYMBOL, TO_EMOJI, TO_MAIN, GAP }

/** 按鍵外觀類型：主題會依這個決定怎麼畫 */
enum class Style { CHAR, FUNC, SPACE, ENTER }

/** 用線條畫的圖示（不靠字型，免得不同手機顯示不一樣） */
enum class Icon { BACKSPACE, ENTER, SHIFT, EMOJI }

data class Key(
    val label: String,
    val act: Act = Act.TEXT,
    val text: String = label,
    /** 長按會打出的字（也會小小地顯示在按鍵上） */
    val sub: String? = null,
    val weight: Float = 1f,
    val style: Style = Style.CHAR,
    val icon: Icon? = null,
)

object Layouts {
    private fun chars(s: String) = s.split(" ").map { Key(it) }

    /** "ㄅ1" → 主字 ㄅ、長按 1 */
    private fun zhuyinRow(s: String) = s.split(" ").map {
        Key(it.substring(0, 1), Act.ZHUYIN, sub = it.substring(1))
    }

    private val backspace = Key("", Act.BACKSPACE, weight = 1.5f, style = Style.FUNC, icon = Icon.BACKSPACE)
    private val enter = Key("", Act.ENTER, weight = 1.5f, style = Style.ENTER, icon = Icon.ENTER)
    private val emoji = Key("", Act.TO_EMOJI, style = Style.FUNC, icon = Icon.EMOJI)
    private val toSymbol = Key("?123", Act.TO_SYMBOL, weight = 1.5f, style = Style.FUNC)
    private val toMain = Key("返回", Act.TO_MAIN, weight = 1.5f, style = Style.FUNC)
    private fun gap(w: Float) = Key("", Act.GAP, weight = w)

    /** 照 Google 注音的直式鍵盤排列 */
    val ZHUYIN: List<List<Key>> = listOf(
        zhuyinRow("ㄅ1 ㄉ2 ˇ3 ˋ4 ㄓ5 ˊ6 ˙7 ㄚ8 ㄞ9 ㄢ0"),
        zhuyinRow("ㄆq ㄊw ㄍe ㄐr ㄔt ㄗy ㄧu ㄛi ㄟo ㄣp"),
        zhuyinRow("ㄇa ㄋs ㄎd ㄑf ㄕg ㄘh ㄨj ㄜk ㄠl ㄤ:"),
        zhuyinRow("ㄈz ㄌx ㄏc ㄒv ㄖb ㄙn ㄩm ㄝ… ㄡ! ㄥ?"),
        listOf(
            toSymbol,
            Key("，", sub = "。"),
            emoji,
            Key("注音", Act.SPACE, text = " ", weight = 2.5f, style = Style.SPACE),
            Key("ㄦ", Act.ZHUYIN),
            backspace,
            enter,
        ),
    )

    val ENGLISH: List<List<Key>> = listOf(
        chars("1 2 3 4 5 6 7 8 9 0"),
        chars("q w e r t y u i o p"),
        listOf(gap(0.5f)) + chars("a s d f g h j k l") + listOf(gap(0.5f)),
        listOf(Key("", Act.SHIFT, weight = 1.5f, style = Style.FUNC, icon = Icon.SHIFT)) +
            chars("z x c v b n m") + listOf(backspace),
        listOf(
            toSymbol,
            Key(",", sub = "?"),
            emoji,
            Key("English", Act.SPACE, text = " ", weight = 4f, style = Style.SPACE),
            Key(".", sub = "!"),
            enter,
        ),
    )

    private val pageBottom = listOf(
        toMain,
        Key("，", sub = "。"),
        emoji,
        Key("空白", Act.SPACE, text = " ", weight = 2.5f, style = Style.SPACE),
        Key("。"),
        backspace,
        enter,
    )

    val SYMBOLS: List<List<Key>> = listOf(
        chars("1 2 3 4 5 6 7 8 9 0"),
        chars("@ # $ % & - + ( ) /"),
        chars("* \" ' : ; ! ? _ = ~"),
        chars("「 」 『 』 、 ： ； ！ ？ …"),
        pageBottom,
    )

    val EMOJI: List<List<Key>> = listOf(
        chars("😀 😂 🤣 😊 😍 🥰 😘 😭 😢 😡"),
        chars("😳 🤔 🙄 😴 😎 🥺 😱 🤗 😏 😅"),
        chars("👍 👏 🙏 💪 👀 ✨ 🔥 💯 🎉 ❤️"),
        chars("🐱 😺 😸 😻 🐾 🐶 🐰 🌸 ⭐ 💕"),
        pageBottom.map { if (it.act == Act.TO_EMOJI) toSymbol.copy(weight = 1f, label = "符號") else it },
    )
}
