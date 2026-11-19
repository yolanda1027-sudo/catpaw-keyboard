package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 剪貼簿：長按 → 釘選｜取消｜刪除，釘選的是 解除釘選｜取消｜排順序（取消在中間、一開始選它）；釘選的在設定裡用 ↑ ↓ 排順序 */
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
        assertEquals("取消在中間；釘選的沒有刪除", listOf("解除釘選", "取消", "排順序"), menu("0912-345-678"))
        assertEquals(listOf("釘選", "取消", "刪除"), menu("cat@example.com"))
        // 取消：什麼都沒變
        s.onItemAction(Key("cat@example.com", Act.CLIP, text = "cat@example.com"), "cancel")
        assertEquals(listOf("台北市大安區", "0912-345-678", "cat@example.com"), clips.all().map { it.text })

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

    @Test fun releaseWithoutSlidingCancels() {
        File(ctx.filesDir, "clips.json").delete()
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        val kv = s.onCreateInputView() as CatKeyboardView
        val clips = CatImeService::class.java.getDeclaredField("clips").apply { isAccessible = true }.get(s) as ClipHistory
        clips.add("留著")
        s.onClipboard()
        kv.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val r = kv.keyRectOf(kv.rows[0][0])!!
        val y = r.centerY() + kv.floatH
        fun popupRect() = CatKeyboardView::class.java.getDeclaredField("popupRect").apply { isAccessible = true }.get(kv) as android.graphics.RectF
        /** 在 x 長按，手指往右移 dx 再放開；回傳跳出選單時的位置 */
        fun longPress(x: Float, dx: Float): android.graphics.RectF {
            val t0 = android.os.SystemClock.uptimeMillis()
            kv.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
            org.robolectric.shadows.ShadowLooper.idleMainLooper(Prefs(ctx).longPressMs + 50L, java.util.concurrent.TimeUnit.MILLISECONDS)
            val rect = android.graphics.RectF(popupRect())
            kv.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 800, android.view.MotionEvent.ACTION_MOVE, x + dx, y, 0))
            kv.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 900, android.view.MotionEvent.ACTION_UP, x + dx, y, 0))
            return rect
        }
        fun state() = clips.all().map { it.text to it.pinned }
        // 中間長按：中間的「取消」就在手指正上方；手指抖一下（10px）放開也是取消
        val rect = longPress(540f, 10f)
        val optW = rect.width() / 3
        assertEquals("取消在手指正上方", 540f, rect.left + optW * 1.5f, 1f)
        assertEquals(listOf("留著" to false), state())
        // 靠左邊長按：選單碰到螢幕邊緣沒辦法對準手指，一樣先停在取消
        longPress(60f, 8f)
        assertEquals(listOf("留著" to false), state())
        // 靠右邊長按、手指往左滑一點：選到手指正上方那格（右邊的刪除旁邊是取消，再左是釘選）
        val edge = longPress(1040f, -8f)
        assertTrue("選單整個在畫面裡", edge.right <= 1080.5f)
        assertEquals(listOf("留著" to false), state())
        // 靠左邊長按、往右滑開：手指在最左邊那格上面＝釘選（以前滑不到）
        longPress(30f, 40f)
        assertEquals(listOf("留著" to true), state())
        clips.action("unpin", "留著")
        // 往左滑一格：釘選
        longPress(540f, -optW)
        assertEquals(listOf("留著" to true), state())
        // 釘選的再長按、往左滑一格：解除釘選
        longPress(540f, -optW)
        assertEquals(listOf("留著" to false), state())
    }

    @Test fun settingsArrowsReorderPinned() {
        val f = File(ctx.filesDir, "clips.json").apply { delete() }
        ClipHistory(f).apply {
            for (t in listOf("甲", "乙", "丙")) add(t)
            for (t in listOf("甲", "乙", "丙")) action("pin", t)
        }
        val act = Robolectric.buildActivity(SettingsActivity::class.java, android.content.Intent().putExtra("page", "clip")).setup().get()
        fun buttons(v: android.view.View, out: MutableList<android.widget.Button>) {
            if (v is android.widget.Button) out.add(v)
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) buttons(v.getChildAt(i), out)
        }
        val dv = act.window.decorView
        dv.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.EXACTLY))
        dv.layout(0, 0, 1080, 2000)
        Bitmap.createBitmap(1080, 2000, Bitmap.Config.ARGB_8888).also { b ->
            dv.draw(Canvas(b))
            File("build/renders").mkdirs()
            File("build/renders/settings-clip-order.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val all = mutableListOf<android.widget.Button>().also { buttons(act.window.decorView, it) }
        val downs = all.filter { it.text == "↓" }
        assertEquals(3, downs.size)
        assertFalse("最後一個不能再往後", downs.last().isEnabled)
        downs.first().performClick() // 甲 往後
        assertEquals(listOf("乙", "甲", "丙"), ClipHistory(f).all().filter { it.pinned }.map { it.text })
        val unpin = mutableListOf<android.widget.Button>().also { buttons(act.window.decorView, it) }.filter { it.text == "解除" }
        unpin.last().performClick() // 丙 解除釘選
        assertEquals(listOf("乙", "甲"), ClipHistory(f).all().filter { it.pinned }.map { it.text })
    }
}
