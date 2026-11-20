#!/usr/bin/env python3
"""
主題下載區的網站產生器（.github/workflows/store.yml 用）。

store/themes/ 裡放主題檔（App「匯出成主題檔」存下來的 .json），
想加說明的話放一個同名的 .txt（例如 草莓牛奶貓.json + 草莓牛奶貓.txt）。

兩個步驟，中間由 Gradle 跑 StorePreviewTest 畫預覽圖：

  python3 tools/build_store.py prepare store/themes build/store/in
      把主題檔複製成英數字的檔名（測試機的 Java 不一定認得中文檔名），記下對照表。

  CATPAW_STORE_DIR=build/store/in CATPAW_PREVIEW_OUT=build/store/out ./gradlew testDebugUnitTest --tests '*StorePreviewTest*'

  python3 tools/build_store.py site store build/store _site
      做出網站：index.html、themes.json、files/（主題檔）、previews/（預覽圖）。
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys


def theme_id(filename: str) -> str:
    return "t" + hashlib.sha1(filename.encode("utf-8")).hexdigest()[:10]


def added_time(path: str) -> int:
    """這個檔案第一次加進 repo 的時間（新的排前面）；拿不到就用修改時間"""
    try:
        out = subprocess.run(
            ["git", "log", "--diff-filter=A", "--follow", "--format=%ct", "--", path],
            capture_output=True, text=True, check=True,
        ).stdout.split()
        if out:
            return int(out[-1])
    except Exception:
        pass
    return int(os.path.getmtime(path))


def prepare(themes_dir: str, work_in: str) -> None:
    os.makedirs(work_in, exist_ok=True)
    entries = []
    names = sorted(os.listdir(themes_dir)) if os.path.isdir(themes_dir) else []
    for name in names:
        if not name.endswith(".json"):
            continue
        src = os.path.join(themes_dir, name)
        tid = theme_id(name)
        shutil.copyfile(src, os.path.join(work_in, tid + ".json"))
        base = name[: -len(".json")]
        txt = os.path.join(themes_dir, base + ".txt")
        desc = open(txt, encoding="utf-8").read().strip() if os.path.exists(txt) else ""
        entries.append({
            "id": tid, "file": name, "desc": desc,
            "size": os.path.getsize(src), "added": added_time(src),
        })
    with open(os.path.join(os.path.dirname(work_in.rstrip("/")) or ".", "map.json"), "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)
    print(f"下載區：{len(entries)} 個主題檔")


def site(store_dir: str, work: str, out: str) -> None:
    with open(os.path.join(work, "map.json"), encoding="utf-8") as f:
        entries = json.load(f)
    if os.path.exists(out):
        shutil.rmtree(out)
    os.makedirs(os.path.join(out, "files"))
    os.makedirs(os.path.join(out, "previews"))
    shutil.copyfile(os.path.join(store_dir, "index.html"), os.path.join(out, "index.html"))
    previews = os.path.join(work, "out")
    themes, problems = [], []
    for e in sorted(entries, key=lambda x: -x["added"]):
        tid = e["id"]
        meta_path = os.path.join(previews, tid + ".meta.json")
        meta = json.load(open(meta_path, encoding="utf-8")) if os.path.exists(meta_path) else {"error": "沒有畫出預覽"}
        if meta.get("error"):
            # 壞掉的檔案不放上網頁，只在 workflow 的紀錄裡提醒
            problems.append(f"{e['file']}：{meta['error']}")
            continue
        shutil.copyfile(os.path.join(work, "in", tid + ".json"), os.path.join(out, "files", tid + ".json"))
        item = {
            "id": tid,
            "name": meta.get("name") or e["file"][: -len(".json")],
            "desc": e["desc"],
            "size": e["size"],
            "file": f"files/{tid}.json",
            "download": "貓爪鍵盤主題_" + e["file"],
            "added": e["added"],
        }
        for key, suffix in (("preview", ".png"), ("previewEn", "-en.png")):
            p = os.path.join(previews, tid + suffix)
            if os.path.exists(p):
                shutil.copyfile(p, os.path.join(out, "previews", tid + suffix))
                item[key] = f"previews/{tid}{suffix}"
        themes.append(item)
    with open(os.path.join(out, "themes.json"), "w", encoding="utf-8") as f:
        json.dump({"themes": themes}, f, ensure_ascii=False)
    # GitHub Pages 不要用 Jekyll 處理（檔名有底線之類的才不會被吃掉）
    open(os.path.join(out, ".nojekyll"), "w").close()
    print(f"下載區：放上 {len(themes)} 個主題")
    for p in problems:
        print(f"::warning::這個主題檔有問題，沒放上網頁：{p}")


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "prepare":
        prepare(sys.argv[2], sys.argv[3])
    elif len(sys.argv) == 5 and sys.argv[1] == "site":
        site(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        print(__doc__)
        sys.exit(2)
