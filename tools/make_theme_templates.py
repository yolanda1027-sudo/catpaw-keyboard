"""產生鍵盤主題設計用的版型圖（8 頁：注音、英文、符號、顏文字、表情、我的貼圖、剪貼簿、數字）。

每一頁的按鍵排列從 App 匯出（跟鍵盤真正的排法一樣），不用在這裡另外抄一份：
  ./gradlew testDebugUnitTest --tests '*TemplateLayoutsTest*'   → app/build/template-layouts.json
再畫圖：
  python3 tools/make_theme_templates.py app/src/main/assets/fonts/jf-openhuninn.ttf app/build/template-layouts.json docs/theme-templates

以手機寬 1080px、1dp = 2.625px、按鍵高度預設 45 計算（大多數 1080 寬的手機在這附近，差一點點沒關係，App 裡可以裁切）。
"""
import json
import sys
from PIL import Image, ImageDraw, ImageFont

FONT, LAYOUTS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
W = 1080
DP = 2.625
TOP = 40 * DP        # 上方列（中／En、選字列、分類列）
QUICK = 40 * DP      # 快捷符號列（只有注音、英文頁有，可以關掉）
PAD_TOP = 6 * DP
PAD_BOTTOM = 10 * DP
KEY_AREA = 45 * 5 * DP  # 按鍵高度設定預設 45dp × 5（不管幾排，總高度一樣）

COL = {
    "top": (91, 141, 239), "quick": (156, 107, 230), "key": (255, 143, 177), "func": (255, 176, 59),
    "pad": (120, 120, 120), "line": (40, 40, 40),
}

# 分類列上哪一格是「選中」的（畫底框）
SELECTED_TAB = {"symbol": 1, "kaomoji": 2, "emoji": 2, "mystickers": 1, "clipboard": 0, "numpad": 0}


def f(size):
    return ImageFont.truetype(FONT, int(size))


def layout(rows, quick):
    h = TOP + (QUICK if quick else 0) + PAD_TOP + KEY_AREA + PAD_BOTTOM
    row_h = KEY_AREA / len(rows)
    keys = []
    for ri, row in enumerate(rows):
        unit = W / sum(k["weight"] for k in row)
        y = TOP + (QUICK if quick else 0) + PAD_TOP + ri * row_h
        x = 0
        for k in row:
            keys.append((k, x, y, x + unit * k["weight"], y + row_h))
            x += unit * k["weight"]
    return round(h), keys


def body(r):
    """自訂底色、底圖畫在這個框裡：左右各內縮 3dp、上下各 4dp"""
    x0, y0, x1, y1 = r
    return (x0 + 3 * DP, y0 + 4 * DP, x1 - 3 * DP, y1 - 4 * DP)


def fit_text(d, text, max_w, size):
    while size > 12 and d.textlength(text, font=f(size)) > max_w:
        size -= 1
    return f(size)


# 每一張版型圖寫上是哪一頁（注音、英文長得很像，不寫會搞混）
TITLES = {
    "zhuyin": "注音鍵盤", "english": "英文鍵盤", "symbol": "符號頁", "kaomoji": "顏文字頁",
    "emoji": "表情符號頁", "mystickers": "我的貼圖頁", "clipboard": "剪貼簿頁", "numpad": "數字頁",
}


def draw_top_zhen(d, a, title, sel):
    """注音、英文頁：中／En、剪貼簿、▽。sel＝0 框住「中」（注音），1 框住「En」（英文）"""
    tab = W * 0.2
    x0 = tab * sel
    d.rounded_rectangle((x0 + 8 * DP, 7 * DP, x0 + tab - 8 * DP, TOP - 7 * DP), radius=(TOP - 14 * DP) / 2, outline=COL["top"] + (a,), width=3)
    d.text((tab / 2, TOP / 2), "中", font=f(TOP * 0.45), fill=COL["top"] + (a,), anchor="mm")
    d.text((tab * 1.5, TOP / 2), "En", font=f(TOP * 0.4), fill=COL["top"] + (a,), anchor="mm")
    for cx, t in ((W - tab * 1.2, "剪貼簿"), (W - tab * 0.4, "▽")):
        d.ellipse((cx - TOP * 0.3, TOP / 2 - TOP * 0.3, cx + TOP * 0.3, TOP / 2 + TOP * 0.3), outline=COL["top"] + (a,), width=2)
        d.text((cx, TOP / 2), t, font=f(TOP * (0.18 if len(t) > 1 else 0.3)), fill=COL["top"] + (a,), anchor="mm")
    # 字放在 En 和剪貼簿中間（En 被框起來時才不會撞到）
    d.text(((tab * 2 + W - tab * 1.2 - TOP * 0.3) / 2, TOP / 2), f"{title}・上方列 1080×{round(TOP)}", font=f(23), fill=COL["top"] + (a,), anchor="mm")


# 分類列排不下、可以左右滑的頁（跟 App 一樣：每格照字寬，不再平均分）
SCROLL_TABS = {"kaomoji"}


def tab_widths(d, tabs):
    """照 App 的算法：字寬（「最近」是時鐘圖示、「返回」是 ◁）＋左右各 16dp，最少 48dp"""
    out = []
    for t in tabs:
        if t == "最近":
            tw = TOP * 0.6
        else:
            label = "◁" if t == "返回" else t
            size = TOP * (0.32 if len(label) > 3 else 0.4)
            bb = d.textbbox((0, 0), label, font=f(size))
            tw = bb[2] - bb[0]
        out.append(max(48 * DP, tw + 32 * DP))
    return out


def draw_top_tabs(d, a, tabs, sel, scroll=False):
    """其他頁：分類列。平常每一格一樣寬；排不下的（顏文字）每格照字寬、可以左右滑"""
    widths = tab_widths(d, tabs) if scroll else [W / len(tabs)] * len(tabs)
    x0 = 0
    for i, t in enumerate(tabs):
        tw = widths[i]
        if x0 >= W:
            break
        if i == sel:
            d.rounded_rectangle((x0 + 3 * DP, 6 * DP, min(W - 1, x0 + tw - 3 * DP), TOP - 6 * DP), radius=10, outline=COL["top"] + (a,), width=3)
        elif i > 0:
            d.line((x0, TOP * 0.3, x0, TOP * 0.7), fill=COL["top"] + (120,), width=1)
        d.text((x0 + tw / 2, TOP / 2), t, font=fit_text(d, t, tw - 10, 30), fill=COL["top"] + (a,), anchor="mm")
        x0 += tw
    if scroll:
        # 右邊淡出＋箭頭：後面還有
        for k in range(40):
            d.line((W - 40 + k, 2, W - 40 + k, TOP - 2), fill=(255, 255, 255, int(6 * k)), width=1)
        d.text((W - 16, TOP / 2), "›", font=f(36), fill=COL["top"] + (a,), anchor="mm")
        return f"分類列 1080×{round(TOP)}，排不下：每格照字寬（約 {round(min(widths[2:] or widths))} 寬），左右滑看其他分類"
    # 尺寸寫在最下面那條（上方列整條都是分類，沒地方寫）
    tw = widths[0]
    return f"分類列 1080×{round(TOP)}，每格 {round(tw)} 寬" if len(tabs) > 1 else f"上方列 1080×{round(TOP)}"


def draw(name, page, quick, file, overlay):
    rows = page["rows"]
    h, keys = layout(rows, quick)
    bg = (0, 0, 0, 0) if overlay else (250, 248, 252, 255)
    im = Image.new("RGBA", (W, h), bg)
    d = ImageDraw.Draw(im, "RGBA")
    a = 255
    line = COL["line"] + (200,)

    # 上方列
    d.rectangle((0, 0, W - 1, TOP), outline=COL["top"] + (a,), width=3, fill=None if overlay else COL["top"] + (40,))
    top_note = None
    if page["tabs"] == "zhen":
        draw_top_zhen(d, a, TITLES[name], 1 if name == "english" else 0)
    else:
        top_note = draw_top_tabs(d, a, page["tabs"], SELECTED_TAB.get(name, 0), scroll=name in SCROLL_TABS)

    y = TOP
    if quick:
        n = 4
        for i in range(n):
            x0, x1 = W / n * i, W / n * (i + 1)
            d.rounded_rectangle(body((x0, y + 3 * DP, x1, y + QUICK - 1 * DP)), radius=12, outline=COL["quick"] + (a,), width=3)
        t = f"快捷符號列 1080×{round(QUICK)}（按鈕數量照設定，寬＝1080÷個數）"
        bb = d.textbbox((W / 2, y + QUICK / 2), t, font=f(22), anchor="mm")
        d.rounded_rectangle((bb[0] - 10, bb[1] - 6, bb[2] + 10, bb[3] + 6), radius=8, fill=(255, 255, 255, 235))
        d.text((W / 2, y + QUICK / 2), t, font=f(22), fill=COL["quick"] + (255,), anchor="mm")
        y += QUICK
    d.rectangle((0, y, W - 1, y + PAD_TOP), fill=COL["pad"] + (40,))

    for (k, x0, y0, x1, y1) in keys:
        if k["kind"] == "gap":
            continue  # 空隙（英文第二排兩邊、符號第三排左邊）
        c = COL["func"] if k["kind"] == "func" else COL["key"]
        lab = k["label"]
        d.rectangle((x0, y0, x1, y1), outline=line if not overlay else c + (90,), width=1)
        b = body((x0, y0, x1, y1))
        d.rounded_rectangle(b, radius=min(b[2] - b[0], b[3] - b[1]) * 0.15, outline=c + (a,), width=3, fill=None if overlay else c + (35,))
        size = 30 if len(lab) < 3 else 24
        d.text(((x0 + x1) / 2, (y0 + y1) / 2 - 8), lab, font=fit_text(d, lab, b[2] - b[0] - 8, size), fill=c + (a,), anchor="mm")
        d.text(((x0 + x1) / 2, b[3] - 16), f"{round(b[2] - b[0])}×{round(b[3] - b[1])}", font=f(17), fill=c + (a,), anchor="mm")

    bottom = h - PAD_BOTTOM
    d.rectangle((0, bottom, W - 1, h - 1), fill=COL["pad"] + (40,))
    d.text((W - 10, h - PAD_BOTTOM / 2), f"整張 1080×{h}", font=f(18), fill=COL["line"] + (220,), anchor="rm")
    note = f"【{TITLES[name]}】" + (f"　上面藍色：{top_note}" if top_note else "")
    d.text((10, h - PAD_BOTTOM / 2), note, font=f(18), fill=COL["top"] + (255,), anchor="lm")
    im.save(f"{OUT}/{file}.png")
    return h


if __name__ == "__main__":
    pages = json.load(open(LAYOUTS, encoding="utf-8"))
    # 注音、英文：有快捷列、沒有快捷列各一張
    print("zhuyin quick", draw("zhuyin", pages["zhuyin"], True, "zhuyin-quickbar-on-reference", False))
    draw("zhuyin", pages["zhuyin"], True, "zhuyin-quickbar-on-overlay", True)
    print("zhuyin noquick", draw("zhuyin", pages["zhuyin"], False, "zhuyin-quickbar-off-overlay", True))
    print("english quick", draw("english", pages["english"], True, "english-quickbar-on-overlay", True))
    draw("english", pages["english"], False, "english-quickbar-off-overlay", True)
    # 其他頁：沒有快捷列；參考用（有底色）＋描圖用（透明）
    for name in ("symbol", "kaomoji", "emoji", "mystickers", "clipboard", "numpad"):
        print(name, draw(name, pages[name], False, f"{name}-reference", False))
        draw(name, pages[name], False, f"{name}-overlay", True)
