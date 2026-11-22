package io.github.yolanda1027.catpaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class EmojiTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Before fun load() {
        Emojis.load(File("src/main/assets/emoji.txt").readText())
    }

    @Test fun dataLooksRight() {
        val all = Emojis.groups.flatMap { it.second }
        assertEquals(9, Emojis.groups.size)
        assertTrue("只有 ${all.size} 個", all.size > 1000) // 表情整理工具挑過的（約 1400 個）
        assertEquals(all.size, all.toSet().size) // 沒有重複
        // 不收膚色變化（U+1F3FB–1F3FF），那些另外長按才會有
        assertTrue(all.none { e -> e.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF } })
        assertTrue("😀" in Emojis.groups[0].second)
        assertTrue("🐱" in Emojis.groups[2].second)
        assertTrue("🇹🇼" in Emojis.groups.last().second)
        // 一頁 32 個（4 排 × 8），刪除鍵只有最下面那排一個
        val rows = Emojis.rows(Emojis.page(Emojis.groups[0].second, 0))
        assertEquals(listOf(8, 8, 8, 8), rows.take(4).map { it.size })
        assertEquals(1, rows.flatten().count { it.act == Act.BACKSPACE })
    }

    private fun board(theme: String, cat: Int, page: Int): CatKeyboardView {
        val p = Prefs(ctx)
        p.theme = theme
        val kv = CatKeyboardView(ctx, p, Haptics(ctx))
        val items = Emojis.groups[cat - 1].second
        kv.rows = Emojis.rows(Emojis.page(items, page))
        kv.symbolTabs = Emojis.tabs()
        kv.symbolTab = cat
        kv.symbolPageCount = Emojis.pageCount(items)
        kv.symbolPage = page
        kv.morePagesAfter = cat < Emojis.groups.size
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        return kv
    }

    private fun shot(kv: View, name: String) {
        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** ❤️ 那一頁點進去要先看到愛心（Unicode 把愛心放在笑臉類，工具有搬過來） */
    @Test fun heartsAreInHeartTab() {
        val hearts = Emojis.groups.first { it.first == "❤️" }.second
        assertEquals("💌", hearts.first())
        for (h in listOf("❤️", "🩷", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕")) {
            assertTrue("❤️ 頁沒有 $h", h in hearts.take(30))
        }
        val smileys = Emojis.groups.first { it.first == "😀" }.second
        assertTrue("❤️ 不要在笑臉頁重複", "❤️" !in smileys)
        assertTrue("有愛心的臉還是在笑臉頁", "🥰" in smileys && "😻" in smileys)
    }

    @Test fun renderBoards() {
        shot(board("classic", 1, 0), "emoji-smileys")
        shot(board("macaron_butter", 3, 1), "emoji-animals")
        shot(board("magic_yellow", 4, 0), "emoji-food")
    }

    /** 一個分類滑到最後一頁，再往左滑會叫下一個分類 */
    @Test fun swipePastLastPageGoesToNextCategory() {
        val items = Emojis.groups[0].second
        val kv = board("classic", 1, Emojis.pageCount(items) - 1)
        var swiped = 0
        kv.listener = object : CatKeyboardView.Listener {
            override fun onKey(key: Key, longPress: Boolean) {}
            override fun onTabChinese() {}
            override fun onTabEnglish() {}
            override fun onHide() {}
            override fun onCandidate(index: Int) {}
            override fun onSymbolTab(index: Int) {}
            override fun onSymbolSwipe(direction: Int) { swiped = direction }
            override fun onClipboard() {}
            override fun onQuick(item: String) {}
        }
        val y = kv.measuredHeight * 0.55f
        val t = SystemClock.uptimeMillis()
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 800f, y, 0))
        for (i in 1..10) kv.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, 800f - i * 50f, y, 0))
        kv.dispatchTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, 300f, y, 0))
        assertEquals(1, swiped)
    }
}
