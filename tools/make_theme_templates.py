"""產生鍵盤主題設計用的版型圖（跟 CatKeyboardView 的排版算法一樣）。

以手機寬 1080px、1dp = 2.625px 計算（大多數 1080 寬的手機在這附近，差一點點沒關係，App 裡可以裁切）。
用法：python3 tools/make_theme_templates.py app/src/main/assets/fonts/jf-openhuninn.ttf docs/theme-templates
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FONT, OUT = sys.argv[1], sys.argv[2]
W = 1080
DP = 2.625
TOP = 40 * DP        # 上方列（中／En、選字列）
QUICK = 40 * DP      # 快捷符號列（可以關掉）
PAD_TOP = 6 * DP
PAD_BOTTOM = 10 * DP
KEY_AREA = 45 * 5 * DP  # 按鍵高度設定預設 45dp × 5

ZHUYIN = [[1] * 10] * 4 + [[1.5, 1, 1, 2.5, 1, 1.5, 1.5]]
ZHUYIN_LABELS = [list("ㄅㄉˇˋㄓˊ˙ㄚㄞㄢ"), list("ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ"), list("ㄇㄋㄎㄑㄕㄘㄨㄜㄠㄤ"), list("ㄈㄌㄏㄒㄖㄙㄩㄝㄡㄥ"),
                 ["?123", "，", "表情", "空白鍵", "ㄦ", "刪除", "Enter"]]
ENGLISH = [[1] * 10, [0.5] + [1] * 9 + [0.5], [1.5] + [1] * 7 + [1.5], [1.5, 1, 1, 4, 1, 1.5]]
ENGLISH_LABELS = [list("qwertyuiop"), [None] + list("asdfghjkl") + [None], ["Shift"] + list("zxcvbnm") + ["刪除"],
                  ["?123", ",", "表情", "空白鍵", ".", "Enter"]]

COL = {
    "top": (91, 141, 239), "quick": (156, 107, 230), "key": (255, 143, 177), "func": (255, 176, 59),
    "pad": (120, 120, 120), "line": (40, 40, 40),
}


def f(size):
    return ImageFont.truetype(FONT, int(size))


def layout(rows, quick):
    h = TOP + (QUICK if quick else 0) + PAD_TOP + KEY_AREA + PAD_BOTTOM
    row_h = KEY_AREA / len(rows)
    keys = []
    for ri, row in enumerate(rows):
        unit = W / sum(row)
        y = TOP + (QUICK if quick else 0) + PAD_TOP + ri * row_h
        x = 0
        for ci, wgt in enumerate(row):
            keys.append((ri, ci, x, y, x + unit * wgt, y + row_h))
            x += unit * wgt
    return round(h), keys, row_h


def body(r):
    """自訂底色、底圖畫在這個框裡：左右各內縮 3dp、上下各 4dp"""
    x0, y0, x1, y1 = r
    return (x0 + 3 * DP, y0 + 4 * DP, x1 - 3 * DP, y1 - 4 * DP)


def draw(rows, labels, quick, name, overlay):
    h, keys, row_h = layout(rows, quick)
    bg = (0, 0, 0, 0) if overlay else (250, 248, 252, 255)
    im = Image.new("RGBA", (W, h), bg)
    d = ImageDraw.Draw(im, "RGBA")
    a = 255 if overlay else 255
    line = COL["line"] + (200,)

    # 上方列
    d.rectangle((0, 0, W - 1, TOP), outline=COL["top"] + (a,), width=3, fill=None if overlay else COL["top"] + (40,))
    tab = W * 0.2
    d.rounded_rectangle((8 * DP, 7 * DP, tab - 8 * DP, TOP - 7 * DP), radius=(TOP - 14 * DP) / 2, outline=COL["top"] + (a,), width=3)
    d.text((tab / 2, TOP / 2), "中", font=f(TOP * 0.45), fill=COL["top"] + (a,), anchor="mm")
    d.text((tab * 1.5, TOP / 2), "En", font=f(TOP * 0.4), fill=COL["top"] + (a,), anchor="mm")
    for cx, t in ((W - tab * 1.2, "剪貼簿"), (W - tab * 0.4, "▽")):
        d.ellipse((cx - TOP * 0.3, TOP / 2 - TOP * 0.3, cx + TOP * 0.3, TOP / 2 + TOP * 0.3), outline=COL["top"] + (a,), width=2)
        d.text((cx, TOP / 2), t, font=f(TOP * (0.18 if len(t) > 1 else 0.3)), fill=COL["top"] + (a,), anchor="mm")
    d.text((W * 0.5, TOP / 2), f"上方列 1080×{round(TOP)}", font=f(26), fill=COL["top"] + (a,), anchor="mm")

    y = TOP
    if quick:
        n = 4
        for i in range(n):
            x0, x1 = W / n * i, W / n * (i + 1)
            d.rounded_rectangle(body((x0, y + 3 * DP - 4 * DP + 4 * DP, x1, y + QUICK - 1 * DP)), radius=12, outline=COL["quick"] + (a,), width=3)
        t = f"快捷符號列 1080×{round(QUICK)}（按鈕數量照設定，寬＝1080÷個數）"
        bb = d.textbbox((W / 2, y + QUICK / 2), t, font=f(22), anchor="mm")
        d.rounded_rectangle((bb[0] - 10, bb[1] - 6, bb[2] + 10, bb[3] + 6), radius=8, fill=(255, 255, 255, 235))
        d.text((W / 2, y + QUICK / 2), t, font=f(22), fill=COL["quick"] + (255,), anchor="mm")
        y += QUICK
    d.rectangle((0, y, W - 1, y + PAD_TOP), fill=COL["pad"] + (40,))

    for (ri, ci, x0, y0, x1, y1) in keys:
        lab = labels[ri][ci]
        if lab is None:
            continue  # 英文第二排兩邊的空隙
        func = len(lab) > 1 and lab not in ("，",)
        c = COL["func"] if func else COL["key"]
        d.rectangle((x0, y0, x1, y1), outline=line if not overlay else c + (90,), width=1)
        b = body((x0, y0, x1, y1))
        d.rounded_rectangle(b, radius=min(b[2] - b[0], b[3] - b[1]) * 0.15, outline=c + (a,), width=3, fill=None if overlay else c + (35,))
        d.text(((x0 + x1) / 2, (y0 + y1) / 2 - 8), lab, font=f(30 if len(lab) < 3 else 24), fill=c + (a,), anchor="mm")
        d.text(((x0 + x1) / 2, b[3] - 16), f"{round(b[2] - b[0])}×{round(b[3] - b[1])}", font=f(17), fill=c + (a,), anchor="mm")

    bottom = h - PAD_BOTTOM
    d.rectangle((0, bottom, W - 1, h - 1), fill=COL["pad"] + (40,))
    d.text((W - 10, h - PAD_BOTTOM / 2), f"整張 1080×{h}", font=f(18), fill=COL["line"] + (220,), anchor="rm")
    im.save(f"{OUT}/{name}.png")
    return h


if __name__ == "__main__":
    print("zhuyin quick", draw(ZHUYIN, ZHUYIN_LABELS, True, "zhuyin-quickbar-on-reference", False))
    draw(ZHUYIN, ZHUYIN_LABELS, True, "zhuyin-quickbar-on-overlay", True)
    print("zhuyin noquick", draw(ZHUYIN, ZHUYIN_LABELS, False, "zhuyin-quickbar-off-overlay", True))
    print("english quick", draw(ENGLISH, ENGLISH_LABELS, True, "english-quickbar-on-overlay", True))
    draw(ENGLISH, ENGLISH_LABELS, False, "english-quickbar-off-overlay", True)
