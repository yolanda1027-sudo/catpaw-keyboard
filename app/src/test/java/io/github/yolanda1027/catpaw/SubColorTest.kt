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

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
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
    }
}
