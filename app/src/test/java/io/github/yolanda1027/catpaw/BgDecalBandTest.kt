package io.github.yolanda1027.catpaw

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import kotlin.math.abs

/**
 * 背景圖案分區記位置：最上面那一條（上方列／快捷列，高度固定）的圖案，按鍵調高調矮都不會偏、不會變大；
 * 按鍵區的圖案跟著按鍵走（以前整塊一起算比例，上面那條的圖案會被拖著跑）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class BgDecalBandTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dp = 2.625f

    private fun keyboard(heightDp: Int): CatKeyboardView {
        val p = Prefs(ctx)
        p.keyHeightDp = heightDp
        val v = CatKeyboardView(ctx, p, Haptics(ctx))
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }

    private fun placed(v: CatKeyboardView): List<Decal> {
        @Suppress("UNCHECKED_CAST")
        return CatKeyboardView::class.java.getDeclaredField("decals").apply { isAccessible = true }.get(v) as List<Decal>
    }

    @Test fun topStripStaysAndKeysFollow() {
        val p = Prefs(ctx)
        // 按鍵高度 45 的鍵盤上設計：背景範圍 = 上面那條 40dp ＋ 按鍵區
        val v45 = keyboard(45)
        val h45 = 40 * dp + 6 * dp + 45 * 5 * dp + 10 * dp
        val rowBoundary45 = 40 * dp + 6 * dp + 45 * dp // 第一排和第二排之間
        val star = Decal("star", Decal.BG, Decal.SHAPE + "star", x = 0.9f, y = 20 * dp / h45, size = 24 * dp / h45)
        val dot = Decal("dot", Decal.BG, Decal.SHAPE + "circle", x = 0.5f, y = rowBoundary45 / h45, size = 10 * dp / h45)
        Decals.save(p, listOf(star, dot).map { v45.bandDecal(it) })
        val saved = Decals.load(p)
        assertEquals("星星在上面那條", 1, saved.first { it.id == "star" }.band)
        assertEquals("圓點在按鍵區", 2, saved.first { it.id == "dot" }.band)
        assertEquals("舊版 App 看的 y 照樣存著", star.y, saved.first { it.id == "star" }.y, 0.001f)

        // 換到按鍵高度 60 的鍵盤
        val v60 = keyboard(60)
        val h60 = 40 * dp + 6 * dp + 60 * 5 * dp + 10 * dp
        val s = placed(v60).first { it.id == "star" }
        assertEquals("星星的位置（px）不變", 20 * dp, s.y * h60, 0.5f)
        assertEquals("星星的大小（px）不變", 24 * dp, s.size * h60, 0.5f)
        val d = placed(v60).first { it.id == "dot" }
        val rowBoundary60 = 40 * dp + 6 * dp + 60 * dp
        val newErr = abs(d.y * h60 - rowBoundary60)
        val oldErr = abs(dot.y * h60 - rowBoundary60) // 以前的記法
        assertTrue("圓點還在兩排中間（差 $newErr px；以前差 $oldErr px）", newErr < 6f && oldErr > 15f)
        Decals.save(p, emptyList())
    }

    @Test fun studioSavesBands() {
        Prefs(ctx).theme = "classic"
        val a = Robolectric.buildActivity(StudioActivity::class.java).setup().get()
        val kv = StudioActivity::class.java.getDeclaredField("kv").apply { isAccessible = true }.get(a) as CatKeyboardView
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val d = Decal("moon", Decal.BG, Decal.SHAPE + "moon", x = 0.2f, y = 0.03f, size = 0.05f)
        StudioActivity::class.java.getDeclaredMethod("saveDecals", List::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(a, listOf(d), false)
        ShadowLooper.idleMainLooper()
        val saved = Decals.load(Prefs(ctx, ThemeStore.WORK)).single()
        assertEquals("工作室存檔時記成「上面那條」", 1, saved.band)
    }

    private fun keyboardW(widthPx: Int): CatKeyboardView {
        val p = Prefs(ctx)
        p.keyHeightDp = 45
        val v = CatKeyboardView(ctx, p, Haptics(ctx))
        v.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }

    /** 左右靠邊：換到比較寬的手機，角落的葉子還是貼著角落，小獾還是在正中間；按鍵左上角的小愛心也一樣 */
    @Test fun edgesStayOnWiderPhone() {
        val p = Prefs(ctx)
        val v = keyboardW(1080)
        val h = 40 * dp + 6 * dp + 45 * 5 * dp + 10 * dp
        val key = Layouts.ZHUYIN[0][0] // ㄅ
        val kr = v.keyRectOf(key)!!
        val leafL = Decal("leafL", Decal.BG, Decal.SHAPE + "flower", x = 60f / 1080, y = 20 * dp / h, size = 30 * dp / h)
        val leafR = Decal("leafR", Decal.BG, Decal.SHAPE + "flower", x = 1020f / 1080, y = 20 * dp / h, size = 30 * dp / h)
        val badger = Decal("badger", Decal.BG, Decal.SHAPE + "cat", x = 0.5f, y = 20 * dp / h, size = 36 * dp / h)
        val heart = Decal("heart", KeyStyles.idOf(key), Decal.SHAPE + "heart", x = 0.15f, y = 0.15f, size = 0.2f)
        Decals.save(p, listOf(leafL, leafR, badger, heart).map { v.encodeDecal(it) })
        val saved = Decals.load(p)
        assertEquals(1, saved.first { it.id == "leafL" }.hx)
        assertEquals(3, saved.first { it.id == "leafR" }.hx)
        assertEquals(2, saved.first { it.id == "badger" }.hx)
        assertEquals("舊版 App 看的 x 照樣存著", 60f / 1080, saved.first { it.id == "leafL" }.x, 0.001f)
        val heartLeftPx = (kr.left + 0.15f * kr.width()) - kr.left

        // 寬 1300 的手機（高度一樣）
        val w = keyboardW(1300)
        val all = placed(w)
        assertEquals("左邊的葉子離左邊一樣遠", 60f, all.first { it.id == "leafL" }.x * 1300, 1f)
        assertEquals("右邊的葉子離右邊一樣遠", 60f, 1300 - all.first { it.id == "leafR" }.x * 1300, 1f)
        assertEquals("小獾在正中間", 0.5f, all.first { it.id == "badger" }.x, 0.001f)
        val kr2 = w.keyRectOf(key)!!
        val hx = all.first { it.id == "heart" }
        assertTrue("按鍵變寬了", kr2.width() > kr.width() + 10)
        assertEquals("小愛心離按鍵左邊一樣遠", heartLeftPx, hx.x * kr2.width(), 1f)
        Decals.save(p, emptyList())
    }
}
