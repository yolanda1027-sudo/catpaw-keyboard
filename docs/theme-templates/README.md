# 鍵盤主題設計版型圖

給畫鍵盤主題的人用：把「描圖用」（overlay）的透明 PNG 放在繪圖軟體的最上層，照著框畫。
「參考用」（reference）有底色和標註，看尺寸用。

| 頁面 | 描圖用 | 參考用 | 整張大小 |
| --- | --- | --- | --- |
| 注音＋快捷符號列 | `zhuyin-quickbar-on-overlay.png` | `zhuyin-quickbar-on-reference.png` | 1080 × 843 |
| 注音，沒有快捷列 | `zhuyin-quickbar-off-overlay.png` | — | 1080 × 738 |
| 英文＋快捷符號列 | `english-quickbar-on-overlay.png` | — | 1080 × 843 |
| 英文，沒有快捷列 | `english-quickbar-off-overlay.png` | — | 1080 × 738 |
| 符號 | `symbol-overlay.png` | `symbol-reference.png` | 1080 × 738 |
| 顏文字 | `kaomoji-overlay.png` | `kaomoji-reference.png` | 1080 × 738 |
| 表情符號 | `emoji-overlay.png` | `emoji-reference.png` | 1080 × 738 |
| 我的貼圖 | `mystickers-overlay.png` | `mystickers-reference.png` | 1080 × 738 |
| 剪貼簿 | `clipboard-overlay.png` | `clipboard-reference.png` | 1080 × 738 |
| 數字 | `numpad-overlay.png` | `numpad-reference.png` | 1080 × 738 |

- 快捷符號列只有注音、英文頁有；其他頁最上面是分類列（每格一樣寬）。
- 以手機寬 1080px、按鍵高度預設（45）計算；按鍵上的數字是「自訂底色、底圖」實際鋪的範圍（px）。
- 不同手機寬度、按鍵高度設定會差一點，App 裡上傳圖片時可以再裁切。
- 按鍵排列從 App 匯出，跟鍵盤真正的排法一樣。重新產生：
  ```
  ./gradlew testDebugUnitTest --tests '*TemplateLayoutsTest*'
  python3 tools/make_theme_templates.py app/src/main/assets/fonts/jf-openhuninn.ttf app/build/template-layouts.json docs/theme-templates
  ```
