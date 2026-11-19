#!/usr/bin/env python3
"""
把 AOSP 英文鍵盤（LatinIME）的單字表轉成貓爪鍵盤用的 app/src/main/assets/en_words.txt。

來源：AOSP packages/inputmethods/LatinIME 的 dictionaries/en_US_wordlist.combined.gz
（Apache License 2.0，見 third_party/aosp-latinime/NOTICE）
下載（LineageOS 的 AOSP 鏡像）：
  https://raw.githubusercontent.com/LineageOS/android_packages_inputmethods_LatinIME/lineage-23.0/dictionaries/en_US_wordlist.combined.gz

用法：python3 tools/build_en_dict.py en_US_wordlist.combined.gz app/src/main/assets/en_words.txt

輸出：一行一個字「單字<TAB>常用度」，常用度 0~255（越大越常用），照小寫排序（查開頭用二分搜尋）。
拿掉的：標成冒犯／可能冒犯、不是單字、嬰兒語、毒品的字；常用度太低（< 2）的罕見字；
有鍵盤上打不出來的字母（é、連字號、數字…）的字。
"""
import gzip
import re
import sys

DROP_FLAGS = {"offensive", "nonword", "babytalk", "drugs"}
WORD = re.compile(r"^[A-Za-z][A-Za-z']*$")


def main(src, dst):
    best = {}
    with gzip.open(src, "rt", encoding="utf-8") as f:
        for line in f:
            if not line.startswith(" word="):
                continue  # 開頭那行說明、shortcut、bigram
            fields = dict(p.split("=", 1) for p in line.strip().split(",") if "=" in p)
            w = fields.get("word", "")
            flags = set(filter(None, fields.get("flags", "").split(":")))
            if flags & DROP_FLAGS:
                continue
            if fields.get("possibly_offensive") == "true" or fields.get("not_a_word") == "true":
                continue
            try:
                freq = int(fields.get("f", "0"))
            except ValueError:
                continue
            if freq < 2 or not WORD.match(w) or w.endswith("'"):
                continue
            if freq > best.get(w, -1):
                best[w] = freq
    rows = sorted(best.items(), key=lambda kv: (kv[0].lower(), -kv[1], kv[0]))
    with open(dst, "w", encoding="utf-8", newline="\n") as out:
        for w, freq in rows:
            out.write(f"{w}\t{freq}\n")
    print(f"{len(rows)} words -> {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
