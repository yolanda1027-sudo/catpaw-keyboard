#!/usr/bin/env python3
"""
把 unicode-emoji-json（MIT，資料來自 unicode.org）的 data-by-group.json
轉成 app/src/main/assets/emoji.txt。

用法：python3 tools/build_emoji.py <data-by-group.json> <輸出的 emoji.txt>

- 只收 Emoji 15.0 以前的（S24 Ultra 這類 Android 14/15 的手機都顯示得出來，太新的會變方塊）
- 不收「膚色、髮型」那種零件（Component 分類）
- 每個分類一段：[分類的圖示] 然後一行，emoji 用空白隔開
"""
import json
import sys

MAX_VERSION = 15.0

TABS = {
    "Smileys & Emotion": "😀",
    "People & Body": "👋",
    "Animals & Nature": "🐱",
    "Food & Drink": "🍓",
    "Travel & Places": "🚗",
    "Activities": "⚽",
    "Objects": "💡",
    "Symbols": "❤️",
    "Flags": "🏳️",
}


def main(src, dst):
    groups = json.load(open(src, encoding="utf-8"))
    out = ["# 來源：unicode-emoji-json 0.9.0（MIT License，資料來自 unicode.org 的 emoji-test.txt）",
           "# 由 tools/build_emoji.py 產生，只收 Emoji 15.0 以前的",
           ""]
    total = 0
    for g in groups:
        tab = TABS.get(g["name"])
        if tab is None:
            continue
        items = [e["emoji"] for e in g["emojis"] if float(e["emoji_version"]) <= MAX_VERSION]
        total += len(items)
        out.append(f"[{tab}]")
        out.append(" ".join(items))
        print(f"{g['name']}: {len(items)}", file=sys.stderr)
    open(dst, "w", encoding="utf-8").write("\n".join(out) + "\n")
    print(f"total {total}", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
