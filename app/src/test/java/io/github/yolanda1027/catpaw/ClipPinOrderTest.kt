package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 剪貼簿釘選的可以排順序：長按 → 釘選／取消釘選／往前／往後／刪除 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClipPinOrderTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun texts(h: ClipHistory) = h.all().map { (if (it.pinned) "📌" else "") + it.text }

    @Test fun pinReorderUnpinRemove() {
        val f = File(ctx.cacheDir, "clips-test.json").apply { delete() }
        val h = ClipHistory(f)
        for (t in listOf("地址", "電話", "帳號", "暗號")) h.add(t)
        // 新的在前：暗號 帳號 電話 地址
        h.action("pin", "電話")
        h.action("pin", "地址")
        h.action("pin", "暗號")
        // 釘選照釘的順序排在最前面
        assertEquals(listOf("📌電話", "📌地址", "📌暗號", "帳號"), texts(h))
        // 暗號往前兩格 → 最前面；再往前就停住
        h.action("up", "暗號")
        h.action("up", "暗號")
        h.action("up", "暗號")
        assertEquals(listOf("📌暗號", "📌電話", "📌地址", "帳號"), texts(h))
        h.action("down", "電話")
        assertEquals(listOf("📌暗號", "📌地址", "📌電話", "帳號"), texts(h))
        // 釘選的再複製一次：位置不變
        h.add("地址")
        assertEquals(listOf("📌暗號", "📌地址", "📌電話", "帳號"), texts(h))
        // 存檔再讀回來，順序一樣
        assertEquals(texts(h), texts(ClipHistory(f)))
        // 取消釘選：回到沒釘選的最前面
        h.action("unpin", "暗號")
        assertEquals(listOf("📌地址", "📌電話", "暗號", "帳號"), texts(h))
        h.action("remove", "電話")
        assertEquals(listOf("📌地址", "暗號", "帳號"), texts(h))
        // 舊的 togglePin 一樣能用
        h.togglePin("帳號")
        assertEquals(listOf("📌地址", "📌帳號", "暗號"), texts(h))
    }

    @Test fun longPressMenuOnClipboardPage() {
        File(ctx.filesDir, "clips.json").delete()
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        fun f(n: String) = CatImeService::class.java.getDeclaredField(n).apply { isAccessible = true }
        val clips = f("clips").get(s) as ClipHistory
        for (t in listOf("台北市大安區", "0912-345-678", "cat@example.com")) clips.add(t)
        clips.action("pin", "台北市大安區")
        clips.action("pin", "0912-345-678")
        s.onClipboard()
        val actions = CatImeService::class.java.getDeclaredMethod("itemActionsFor", Key::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        fun menu(t: String) = (actions.invoke(s, Key(t, Act.CLIP, text = t)) as List<Pair<String, String>>).map { it.second }
        assertEquals(listOf("取消釘選", "往前", "往後", "刪除"), menu("0912-345-678"))
        assertEquals(listOf("釘選", "刪除"), menu("cat@example.com"))
        // 往前：電話排到地址前面
        s.onItemAction(Key("0912-345-678", Act.CLIP, text = "0912-345-678"), "up")
        assertEquals(listOf("0912-345-678", "台北市大安區"), clips.all().filter { it.pinned }.map { it.text })

        // 截圖：在剪貼簿長按第一筆
        kv.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        fun kf(n: String) = CatKeyboardView::class.java.getDeclaredField(n).apply { isAccessible = true }
        val kr = (kf("keyRects").get(kv) as List<*>).first {
            val k = it!!.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(it) as Key
            k.act == Act.CLIP && k.text == "0912-345-678"
        }
        kf("pressed").set(kv, kr)
        (kf("longPressRunnable").get(kv) as Runnable).run()
        val b = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        File("build/renders").mkdirs()
        File("build/renders/clip-pin-menu.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 四個選項都在畫面裡
        val r = kf("popupRect").get(kv) as android.graphics.RectF
        org.junit.Assert.assertTrue("選單 $r 要整個在畫面裡", r.left >= 0f && r.right <= kv.width + 0.5f)
    }
}
