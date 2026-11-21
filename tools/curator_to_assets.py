#!/usr/bin/env python3
"""
「表情整理工具」網頁（claude.ai 上的 artifact）整理好的結果 → App 的 emoji.txt、kaomoji.txt。

網頁的資料庫每個分類一筆：{order, label, items[], bin?}
  emoji：label 是分類列上的圖示；kaomoji：label 是分類名稱；bin = 「已刪除」那一筆（不收進 App）

用法：python3 tools/curator_to_assets.py <讀回來的資料夾> app/src/main/assets
  讀回來的資料夾裡要有 emoji/*.json、kaomoji/*.json（ArtifactData list 的 out_dir 存的就是這個樣子）
"""
import json
import os
import sys

EMOJI_HEAD = [
    "# 貓爪鍵盤收錄的 emoji：用「表情整理工具」挑選、分類（tools/curator_to_assets.py 產生）",
    "# 原始清單來自 unicode-emoji-json 0.9.0（MIT License，資料來自 unicode.org 的 emoji-test.txt）",
    "# 格式：[分類列上的圖示] 下面一行，emoji 用空白隔開",
]
KAOMOJI_HEAD = [
    "# 顏文字。部分選自 Wepplication 特殊符號表（wepplication.github.io/tools/charMap/#emoticon），重新挑選、分類。",
    "# 用「表情整理工具」整理（tools/curator_to_assets.py 產生）",
    "# 格式：[分類名稱] 開頭，下面一行一個。",
]


def load(dir_):
    cats = []
    if not os.path.isdir(dir_):
        return cats
    for f in sorted(os.listdir(dir_)):
        if not f.endswith(".json"):
            continue
        d = json.load(open(os.path.join(dir_, f), encoding="utf-8"))
        # out_dir 存的檔案可能把內容包在 data 裡
        d = d.get("data", d) if isinstance(d, dict) else d
        if d.get("bin"):
            continue
        cats.append(d)
    cats.sort(key=lambda c: c.get("order", 0))
    return cats


def clean(items, allow_space):
    out, seen = [], set()
    for it in items:
        it = str(it).strip()
        if not it or it in seen or (it.startswith("[") and it.endswith("]")):
            continue
        # 顏文字一行一個：# 開頭會被當成註解；emoji 在一行中間沒關係（例如 #️⃣）
        if allow_space and it.startswith("#"):
            continue
        if not allow_space and any(ch.isspace() for ch in it):
            continue
        seen.add(it)
        out.append(it)
    # emoji：整行不能用 # 開頭（App 會當成註解跳過整個分類），#️⃣ 排第一的話往後挪一格
    if not allow_space and len(out) > 1 and out[0].startswith("#"):
        out[0], out[1] = out[1], out[0]
    return out


def main(src, out):
    emoji = load(os.path.join(src, "emoji"))
    kao = load(os.path.join(src, "kaomoji"))
    if not emoji or not kao:
        sys.exit("讀不到 emoji 或 kaomoji 的資料，沒有寫檔")
    lines = EMOJI_HEAD + [""]
    for c in emoji:
        items = clean(c.get("items", []), allow_space=False)
        if not items:
            continue
        lines += [f"[{c['label']}]", " ".join(items)]
    open(os.path.join(out, "emoji.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    lines = list(KAOMOJI_HEAD)
    for c in kao:
        items = clean(c.get("items", []), allow_space=True)
        if not items:
            continue
        lines.append(f"[{c['label']}]")
        lines += items
    open(os.path.join(out, "kaomoji.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"emoji：{len(emoji)} 個分類、{sum(len(c.get('items', [])) for c in emoji)} 個")
    print(f"顏文字：{len(kao)} 個分類、{sum(len(c.get('items', [])) for c in kao)} 個")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2])
