#!/usr/bin/env python3
"""
把小麥注音（McBopomofo, MIT 授權）的原始詞庫資料，編成貓爪鍵盤用的詞庫檔。
目前的 zhuyin.dict 是用 McBopomofo commit 2f50c20（2026-11-08）編出來的。

用法：
    git clone --depth 1 https://github.com/openvanilla/McBopomofo /tmp/McBopomofo
    python3 tools/build_dict.py /tmp/McBopomofo/Source/Data app/src/main/assets/zhuyin.dict

輸出格式（UTF-8，一行一筆，用 tab 分隔）：
    詞<TAB>讀音（音節用空白分隔）<TAB>分數

分數是 log10 的機率（越接近 0 越常用），計算方式比照小麥注音的
frequency_builder.py / main_compiler.py：
  - 詞頻 = phrase.occ 的出現次數（扣掉 exclusion.txt 指定的重複計算）
  - 分數 = log10(2.7^(字數-1) × 次數 / 總數)，長詞有加分
  - 破音字：第一讀音給原分數，第二讀音 -ln2，第三讀音 -2ln2，其他讀音給 -6.8
"""
import math
import sys
from pathlib import Path

FSCALE = 2.7
H_DEFLT_FREQ = -6.8
UNK_FREQ = -9.0  # 有收字但沒有詞頻資料的罕用字
LN2 = math.log(2)


def read_lines(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            yield line


def main(src: Path, out: Path):
    # 1. 詞頻
    occ = {}
    for line in read_lines(src / "phrase.occ"):
        parts = line.split()
        if len(parts) >= 2:
            occ[parts[0]] = int(parts[1])

    for line in read_lines(src / "exclusion.txt"):
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        k, v = parts[0].strip(), parts[1].strip()
        if k in v and k in occ and v in occ:
            occ[k] -= occ[v]

    norm = sum(FSCALE ** (len(k) - 1) * c for k, c in occ.items())
    freq = {}
    for k, c in occ.items():
        freq[k] = math.log10(FSCALE ** (len(k) - 1) * (c if c >= 1 else 0.5) / norm)

    # 2. 讀音
    readings = {}  # 詞 -> [讀音, ...]（保持原檔順序）
    char_order = []
    for line in read_lines(src / "BPMFBase.txt"):
        parts = line.split()
        if len(parts) < 2:
            continue
        ch, r = parts[0], parts[1]
        if ch not in readings:
            readings[ch] = []
            char_order.append(ch)
        readings[ch].append(r)
    for line in read_lines(src / "BPMFMappings.txt"):
        parts = line.split()
        if len(parts) < 2:
            continue
        readings.setdefault(parts[0], []).append(" ".join(parts[1:]))

    # 3. 破音字的主要／次要讀音
    def load_phon(name):
        d = {}
        for line in read_lines(src / name):
            parts = line.split()
            if len(parts) >= 2:
                d[parts[0]] = parts[1]
        return d

    phon1, phon2, phon3 = (load_phon(f"heterophony{i}.list") for i in (1, 2, 3))

    # 4. 輸出
    rows = []
    seen = set()
    for phrase, rs in readings.items():
        if phrase in freq:
            base = freq[phrase]
        elif len(phrase) == 1:
            base = UNK_FREQ
        else:
            continue  # 沒有詞頻的多字詞不收
        for r in rs:
            if (phrase, r) in seen:
                continue
            seen.add((phrase, r))
            score = base
            if len(phrase) == 1 and phrase in phon1 and phon1[phrase] != r:
                if phon2.get(phrase) == r:
                    score = max(base - LN2, H_DEFLT_FREQ)
                elif phon3.get(phrase) == r:
                    score = max(base - 2 * LN2, H_DEFLT_FREQ)
                else:
                    score = H_DEFLT_FREQ
            rows.append((phrase, r, score))

    rows.sort(key=lambda x: (x[1], -x[2]))
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("# 來源：小麥注音 McBopomofo 詞庫（MIT License, Copyright (c) 2011-2026 Mengjuei Hsieh et al.）\n")
        for phrase, r, score in rows:
            f.write(f"{phrase}\t{r}\t{score:.4f}\n")
    print(f"wrote {len(rows)} entries to {out}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]))
