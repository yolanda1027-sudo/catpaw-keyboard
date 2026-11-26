#!/usr/bin/env python3
"""
把 unicode-emoji-json（MIT，資料來自 unicode.org）的 data-by-group.json
轉成 app/src/main/assets/emoji.txt。

用法：python3 tools/build_emoji.py <data-by-group.json> <輸出的 emoji.txt>

⚠️ 現在 App 用的 emoji.txt 是用「表情整理工具」挑選、排過順序的（tools/curator_to_assets.py 產生）。
   這支程式只用來產生「整份原始清單」給整理工具當起點；直接輸出到 app/src/main/assets/emoji.txt
   會蓋掉整理好的分類和順序。

- 只收 Emoji 15.0 以前的（S24 Ultra 這類 Android 14/15 的手機都顯示得出來，太新的會變方塊）
- 不收「膚色、髮型」那種零件（Component 分類）
- 每個分類一段：[分類的圖示] 然後一行，emoji 用空白隔開
- 愛心（💌💘…❤️🧡…🤍）Unicode 放在「笑臉與情緒」，但分頁圖示是 ❤️ 的「符號」頁點進去卻沒有愛心，
  所以搬到符號頁的最前面（跟 Gboard 一樣）
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


def is_heart(group, e):
    """笑臉與情緒裡的愛心（不含 🥰😍😻 這種有愛心的臉）"""
    n = e["name"]
    return group == "Smileys & Emotion" and ("heart" in n or n == "love letter") and "face" not in n and "cat" not in n


def main(src, dst):
    groups = json.load(open(src, encoding="utf-8"))
    out = ["# 來源：unicode-emoji-json 0.9.0（MIT License，資料來自 unicode.org 的 emoji-test.txt）",
           "# 由 tools/build_emoji.py 產生，只收 Emoji 15.0 以前的",
           ""]
    total = 0
    ok = lambda e: float(e["emoji_version"]) <= MAX_VERSION
    hearts = [e["emoji"] for g in groups for e in g["emojis"] if ok(e) and is_heart(g["name"], e)]
    for g in groups:
        tab = TABS.get(g["name"])
        if tab is None:
            continue
        items = [e["emoji"] for e in g["emojis"] if ok(e) and not is_heart(g["name"], e)]
        if g["name"] == "Symbols":
            items = hearts + items
        total += len(items)
        out.append(f"[{tab}]")
        out.append(" ".join(items))
        print(f"{g['name']}: {len(items)}", file=sys.stderr)
    open(dst, "w", encoding="utf-8").write("\n".join(out) + "\n")
    print(f"total {total}", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
