# 🐾 貓爪鍵盤 CatPaw Keyboard

一個 Android 注音輸入法。手感照著 Google 注音輸入法做（按鍵大小、排列、微震動），外觀可以自訂，例如換成貓爪肉球。

| 經典深色（Google 注音風） | 粉紅縫線 | 貓爪肉球 |
|---|---|---|
| ![classic](docs/classic-zhuyin.png) | ![stitch](docs/stitch-zhuyin.png) | ![catpaw](docs/catpaw-zhuyin.png) |

和原版 Google 注音的比對（左：原版截圖，右：貓爪鍵盤經典版）：

![compare](docs/compare-google.png)

## 安裝到手機

1. 打開 [Releases 頁面](../../releases)，下載「最新測試版」裡的 `catpaw-keyboard.apk`
2. 在手機上點開 APK 安裝（第一次安裝時，手機會要求你允許「安裝未知應用程式」）
3. 打開「貓爪鍵盤」App：
   - 按 ① 到系統設定啟用「貓爪鍵盤」
   - 按 ② 切換成貓爪鍵盤
4. 在 App 裡的「試打區」打字，一邊調震動和按鍵高度

之後有新版本時，下載新的 APK 直接安裝，就會覆蓋更新，不用先移除舊版。

## 開發進度

- [x] **第一階段：手感原型**（v0.2：字的比例照原版重新量過，新增「粉紅縫線」主題）
  ：照 Google 注音排的鍵盤、可調震動（清脆／輕點／自訂／系統）、可調按鍵高度、按鍵放大提示、長按打數字、英文／符號／表情符號鍵盤
- [x] **第二階段：能打中文**：整句智慧轉換、不打聲調也可以、聲母縮寫（ㄒㄒ → 謝謝）、選字列（可左右滑、可展開）、聯想詞、記住你選過的字
- [ ] **第三階段：造型自由**：用圖片自訂按鍵、按鍵動畫、多套主題

## 詞庫來源

注音詞庫來自 [小麥注音 McBopomofo](https://github.com/openvanilla/McBopomofo)（MIT License），
用 `tools/build_dict.py` 轉成 `app/src/main/assets/zhuyin.dict`。授權全文見 `third_party/McBopomofo-LICENSE.txt`。

## 給開發者

- 每次 push，GitHub Actions 會自動建置 APK，並更新 Releases 的「最新測試版」
- 本機建置：`./gradlew assembleDebug`
- 注音引擎測試：`./gradlew testDebugUnitTest --tests '*ZhuyinEngineTest*'`
- 畫面截圖測試：`./gradlew testDebugUnitTest`，用 Robolectric 把鍵盤畫成 PNG，存到 `app/build/renders/`
- `keystore/catpaw-debug.jks` 是測試用的簽章，讓每次建置出來的 APK 簽章都一樣，手機才能直接覆蓋更新。這個簽章是公開的，請只安裝從這個 repo 的 Releases 下載的 APK。
