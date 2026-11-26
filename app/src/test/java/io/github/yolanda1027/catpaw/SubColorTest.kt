package io.github.yolanda1027.catpaw

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** 整類一起改小字（注音鍵下面的 1、q、a…）的顏色；單顆自己移過字的鍵也跟著變 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SubColorTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val brown = 0xFF6B3A1F.toInt()

    @Test fun groupSubColorReachesEveryKey() {
        val bo = Layouts.ZHUYIN[0][0] // ㄅ
        val po = Layouts.ZHUYIN[1][0] // ㄆ
        val ks = KeyStyles.EMPTY
            .with("@zhuyin", KeyStyle(labels = mapOf("sub" to LabelStyle(color = brown))))
            // ㄆ 自己把主字往上移了一點
            .with(KeyStyles.idOf(po), KeyStyle(labels = mapOf("main" to LabelStyle(dy = -0.05f))))
        assertEquals(brown, ks.resolve(bo)!!.labels!!.getValue("sub").color)
        val p = ks.resolve(po)!!.labels!!
        assertEquals("自己移過主字的鍵，小字顏色還是跟整類", brown, p.getValue("sub").color)
        assertEquals("主字的位置照自己的", -0.05f, p.getValue("main").dy, 0.0001f)
    }

    @Test fun groupMovesLabelsButOwnDragWins() {
        for (rows in listOf(Layouts.ZHUYIN, Layouts.ENGLISH)) {
            val a = rows[0][0]; val b = rows[0][1]; val c = rows[0][2]
            val gid = KeyStyles.groupIdOf(a)
            val ks = KeyStyles.EMPTY
                .with(gid, KeyStyle(labels = mapOf("main" to LabelStyle(dy = 0.1f, scale = 1.2f), "sub" to LabelStyle(dx = -0.2f))))
                // b 只改了主字顏色：位置還是跟整類
                .with(KeyStyles.idOf(b), KeyStyle(labels = mapOf("main" to LabelStyle(color = brown))))
                // c 自己拖過主字：照自己的
                .with(KeyStyles.idOf(c), KeyStyle(labels = mapOf("main" to LabelStyle(dx = 0.05f))))
            val la = ks.resolve(a)!!.labels!!
            assertEquals(0.1f, la.getValue("main").dy, 1e-4f)
            assertEquals(1.2f, la.getValue("main").scale, 1e-4f)
            assertEquals(-0.2f, la.getValue("sub").dx, 1e-4f)
            val lb = ks.resolve(b)!!.labels!!.getValue("main")
            assertEquals("只改顏色的鍵，位置跟整類", 0.1f, lb.dy, 1e-4f)
            assertEquals(brown, lb.color)
            val lc = ks.resolve(c)!!.labels!!.getValue("main")
            assertEquals("自己拖過的鍵照自己的", 0.05f, lc.dx, 1e-4f)
            assertEquals(0f, lc.dy, 1e-4f)
            assertEquals(-0.2f, ks.resolve(c)!!.labels!!.getValue("sub").dx, 1e-4f)
        }
    }


    @Test fun groupPanelHasSubColorRow() {
        Prefs(ctx).theme = "classic"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        val root = a.window.decorView
        kv.editPicker!!.invoke(Layouts.ZHUYIN[0][0])
        ShadowLooper.idleMainLooper()
        StudioActivity::class.java.getDeclaredMethod("back").apply { isAccessible = true }.invoke(a) // 回工作室首頁
        ShadowLooper.idleMainLooper()
        (find(root) { it is android.widget.Button && it.text.contains("所有注音鍵") } as android.widget.Button).performClick()
        ShadowLooper.idleMainLooper()
        assertNotNull("整類的面板有「小字顏色」", find(root) { it is TextView && it.text == "小字顏色" })
        assertNotNull("整類的面板可以移字", find(root) { it is TextView && it.text == "字的位置和大小" })
        val bar = find(root) { it is TextView && it.text.startsWith("上下（往下是 +）") }!!
        val parent = bar.parent as ViewGroup
        val seek = parent.getChildAt(parent.indexOfChild(bar) + 1) as android.widget.SeekBar
        // 用滑桿把整類主字往下移 10%
        seek.progress = 60
        android.widget.SeekBar::class.java.getDeclaredField("mOnSeekBarChangeListener").apply { isAccessible = true }
            .get(seek).let { it as android.widget.SeekBar.OnSeekBarChangeListener }.onProgressChanged(seek, 60, true)
        val styles = StudioActivity::class.java.getDeclaredField("styles").apply { isAccessible = true }.get(a) as KeyStyles
        assertEquals(0.1f, styles.resolve(Layouts.ZHUYIN[2][3])!!.labels!!.getValue("main").dy, 1e-4f)
    }
}
