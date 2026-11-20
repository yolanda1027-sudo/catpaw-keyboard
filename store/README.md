# 主題下載區

網址：https://yolanda1027-sudo.github.io/catpaw-keyboard/

App 的「主題」頁有一顆「🛍️ 主題下載區」，按了會用瀏覽器打開這個網址。
使用者在網頁按「下載」，再點下載好的檔案（或在 App 裡「📥 匯入主題檔」）就能匯入。
鍵盤本身不連網路。

## 第一次：打開 GitHub Pages（只要做一次）

1. 到 repo 的 **Settings → Pages**
2. **Build and deployment → Source** 選 **GitHub Actions**
3. 到 **Actions → 主題下載區 → Run workflow** 跑一次，網址就會出現

## 上架一個主題

1. 在貓爪鍵盤 App：「主題」→ 想上架的主題按「⋯」→「📤 匯出成主題檔」→ 存下來（.json）
2. 在 GitHub 網頁打開這個 repo 的 `store/themes` 資料夾 → **Add file → Upload files** → 選那個 .json → **Commit changes**
3. 等大約 5 分鐘（Actions 裡「主題下載區」跑完），網頁上就會出現

- 網頁上顯示的名字是**匯出時取的主題名字**，不是檔名
- 想加一段說明：放一個**同名的 .txt**，例如 `草莓牛奶貓.json` ＋ `草莓牛奶貓.txt`
- 預覽圖（注音、英文兩頁）會自動畫，不用自己截圖
- 新上架的排在最前面
- 檔案有問題（不是主題檔、壞掉）不會放上網頁，Actions 的紀錄裡會有黃色警告

## 下架、更新

- 下架：把 `store/themes` 裡那個 .json（和 .txt）刪掉
- 更新：用同一個檔名再上傳一次，蓋掉舊的

## 注意

- 這個網站是公開的：放上去的主題、圖片誰都看得到、下載得到
- GitHub 網頁一次上傳最多 25 MB；主題檔太大的話，匯出時可以少勾一些圖片，或在「圖片存法」選省空間（WebP）
