package io.github.yolanda1027.catpaw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipHistoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun newestFirstAndNoDuplicates() {
        val h = ClipHistory(tmp.newFile("c.json"))
        h.add("a", 1000); h.add("b", 2000); h.add("a", 3000)
        assertEquals(listOf("a", "b"), h.all(3000).map { it.text })
    }

    @Test fun unpinnedExpireAfterAnHourPinnedStay() {
        val f = tmp.newFile("c.json")
        val h = ClipHistory(f)
        val now = System.currentTimeMillis()
        h.add("old", now - 2 * 60 * 60 * 1000)
        h.add("keep", now - 2 * 60 * 60 * 1000)
        h.togglePin("keep")
        h.add("fresh", now)
        assertEquals(listOf("keep", "fresh"), h.all(now).map { it.text })
        // 存檔後重新讀，釘選還在
        val again = ClipHistory(f).all(now)
        assertTrue(again.first { it.text == "keep" }.pinned)
    }

    @Test fun clearKeepsPinned() {
        val h = ClipHistory(tmp.newFile("c.json"))
        h.add("x"); h.add("y"); h.togglePin("x")
        h.clearUnpinned()
        assertEquals(listOf("x"), h.all().map { it.text })
    }

    @Test fun limitsUnpinnedCount() {
        val h = ClipHistory(tmp.newFile("c.json"))
        val now = System.currentTimeMillis()
        repeat(30) { h.add("item$it", now) }
        assertEquals(ClipHistory.MAX_ITEMS, h.all(now).size)
        assertEquals("item29", h.all(now)[0].text)
    }
}
