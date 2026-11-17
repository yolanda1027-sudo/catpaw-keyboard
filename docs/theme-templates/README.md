# 鍵盤主題設計版型圖

給畫鍵盤主題的人用：把「描圖用」的透明 PNG 放在繪圖軟體的最上層，照著框畫。

| 檔案 | 用途 |
| --- | --- |
| `zhuyin-quickbar-on-reference.png` | 注音鍵盤＋快捷符號列，有底色和標註（看尺寸用） |
| `zhuyin-quickbar-on-overlay.png` | 注音鍵盤＋快捷符號列，透明底（描圖用） |
| `zhuyin-quickbar-off-overlay.png` | 注音鍵盤、沒有快捷符號列 |
| `english-quickbar-on-overlay.png` | 英文鍵盤＋快捷符號列 |
| `english-quickbar-off-overlay.png` | 英文鍵盤、沒有快捷符號列 |

- 以手機寬 1080px、按鍵高度預設（45）計算；按鍵上的數字是「自訂底色、底圖」實際鋪的範圍（px）。
- 不同手機寬度、按鍵高度設定會差一點，App 裡上傳圖片時可以再裁切。
- 重新產生：`python3 tools/make_theme_templates.py app/src/main/assets/fonts/jf-openhuninn.ttf docs/theme-templates`
