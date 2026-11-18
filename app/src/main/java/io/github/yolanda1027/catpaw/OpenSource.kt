package io.github.yolanda1027.catpaw

import android.content.Context

/**
 * 鍵盤裡用到的開放原始碼和字體。
 * 授權全文放在 repo 的 third_party/，打包時會整個複製到 App 的 assets/licenses/（見 app/build.gradle.kts）。
 * 這些授權都要求「散布時附上版權聲明和授權全文」，所以設定裡有一頁可以看。
 */
object OpenSource {
    data class Entry(
        val name: String,
        /** 用在鍵盤的哪裡 */
        val usage: String,
        val license: String,
        val copyright: String,
        val url: String,
        /** 授權全文（assets/licenses/ 底下的路徑） */
        val files: List<String>,
    )

    val ENTRIES = listOf(
        Entry(
            "小麥注音 McBopomofo", "注音詞庫（轉換成鍵盤用的格式）", "MIT License",
            "Copyright (c) 2011-2026 Mengjuei Hsieh et al.",
            "https://github.com/openvanilla/McBopomofo",
            listOf("McBopomofo-LICENSE.txt"),
        ),
        Entry(
            "OpenCC", "打出簡體字用的繁簡對照表（原檔未修改）", "Apache License 2.0",
            "Copyright (c) Carbo Kuo (BYVoid) and contributors",
            "https://github.com/BYVoid/OpenCC",
            listOf("opencc/LICENSE"),
        ),
        Entry(
            "unicode-emoji-json", "表情符號的清單和分類", "MIT License",
            "Copyright (c) 2019 Mu-An Chiou",
            "https://github.com/muan/unicode-emoji-json",
            listOf("emoji/unicode-emoji-json-LICENSE.txt"),
        ),
        Entry(
            "Unicode 資料", "表情符號清單的原始資料", "Unicode License v3",
            "Copyright © 1991-2023 Unicode, Inc.",
            "https://www.unicode.org/license.txt",
            listOf("emoji/Unicode-LICENSE.txt"),
        ),
        Entry(
            "jf open 粉圓 2.1", "字體（原檔未修改；漢字部分衍生自 Kosugi Maru，Apache License 2.0）", "SIL Open Font License 1.1",
            "Copyright (c) 2020-2024 justfont Co., LTD.",
            "https://github.com/justfont/open-huninn-font",
            listOf("fonts/jf-openhuninn-LICENSE.txt"),
        ),
        Entry(
            "芫荽 Iansui", "字體（只保留常用字與注音、標點）", "SIL Open Font License 1.1",
            "Copyright 2025 The Iansui Project Authors",
            "https://github.com/ButTaiwan/iansui",
            listOf("fonts/Iansui-OFL.txt"),
        ),
        Entry(
            "霞鶩文楷 TC", "字體（只保留常用字與注音、標點）", "SIL Open Font License 1.1",
            "Copyright 2022-2026 The LXGW WenKai Project Authors",
            "https://github.com/lxgw/LxgwWenkaiTC",
            listOf("fonts/LXGWWenKaiTC-OFL.txt"),
        ),
    )

    /** 其他來源（沒有授權檔，但標明出處） */
    const val OTHER_SOURCES = "顏文字有一部分選自 Wepplication 特殊符號表（wepplication.github.io/tools/charMap），經過重新挑選、分類。"

    /**
     * 顯示用：原文每行固定寬度斷行，手機上會變成一行半一行半；
     * 同一段裡的換行接起來，段落之間的空行留著。（只改顯示，原檔不動）
     */
    fun reflow(text: String): String = text.replace("\r\n", "\n")
        .split(Regex("\n\\s*\n"))
        .joinToString("\n\n") { para -> para.lines().joinToString(" ") { it.trim() }.trim() }

    /** 讀授權全文；讀不到就回傳 null */
    fun text(ctx: Context, file: String): String? = try {
        ctx.assets.open("licenses/$file").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
