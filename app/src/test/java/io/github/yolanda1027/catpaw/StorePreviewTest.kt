package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 主題下載區的預覽圖：把 store/themes 裡每個主題檔套上去，畫出注音、英文兩頁。
 * 平常跑測試時不做事；下載區的 workflow（store.yml）設了 CATPAW_STORE_DIR 才會跑。
 *
 * 輸出（CATPAW_PREVIEW_OUT）：<檔名>.png（注音）、<檔名>-en.png（英文）、
 * <檔名>.meta.json（主題名字、底色主題；讀不出來的檔案記 error）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StorePreviewTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Test fun renderStorePreviews() {
        val dir = System.getenv("CATPAW_STORE_DIR")?.let(::File)
        assumeTrue("沒設 CATPAW_STORE_DIR：不是在做下載區", dir != null && dir.isDirectory)
        val out = File(System.getenv("CATPAW_PREVIEW_OUT") ?: "build/store-previews").apply { mkdirs() }
        val files = dir!!.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        for (f in files) {
            val base = f.name.removeSuffix(".json")
            val meta = JSONObject()
            try {
                val o = ThemeIO.parse(f.readText()) ?: error("這不是貓爪鍵盤的主題檔")
                meta.put("name", o.optString("name", base)).put("theme", o.optString("theme"))
                ThemeStore.withTemp(ctx, ThemeStore.factory(o.optString("theme"))) { tmp ->
                    val warnings = ThemeIO.apply(ctx, tmp, o)
                    if (warnings.isNotEmpty()) meta.put("warnings", warnings.joinToString("；"))
                    save(render(tmp, Layouts.ZHUYIN), File(out, "$base.png"))
                    save(render(tmp, Layouts.ENGLISH), File(out, "$base-en.png"))
                }
                println("下載區預覽：${f.name} → ${meta.optString("name")}")
            } catch (e: Throwable) {
                // 一個檔案壞掉不要擋住整個下載區：網頁上那一個會顯示「這個檔案有問題」
                meta.put("error", e.message ?: e.javaClass.simpleName)
                println("下載區預覽失敗：${f.name}：${e.message}")
            }
            File(out, "$base.meta.json").writeText(meta.toString())
        }
    }

    private fun render(p: Prefs, rows: List<List<Key>>): Bitmap {
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        kv.rows = rows
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val full = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(full))
        // 最上面留給注音小框框的透明空間：裁掉
        val top = kv.floatH.toInt().coerceIn(0, full.height - 1)
        val b = Bitmap.createBitmap(full, 0, top, full.width, full.height - top)
        // 網頁上用不到這麼大：縮成 720 寬，手機流量省一點
        return Bitmap.createScaledBitmap(b, 720, b.height * 720 / b.width, true)
    }

    private fun save(b: Bitmap, f: File) = f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
