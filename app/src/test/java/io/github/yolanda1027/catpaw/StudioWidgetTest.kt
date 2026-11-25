package io.github.yolanda1027.catpaw

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 主題工作室：沒有自己的桌面圖示，改成桌面小工具（需要的人自己加）＋長按 App 圖示的捷徑 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StudioWidgetTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Test fun onlyOneLauncherIcon() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName)
        val found = ctx.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.name }
        assertEquals(listOf(SettingsActivity::class.java.name), found)
    }

    @Test fun widgetOpensStudio() {
        // 小工具有註冊在系統的清單裡
        val q = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(ctx.packageName)
        val providers = ctx.packageManager.queryBroadcastReceivers(q, android.content.pm.PackageManager.GET_META_DATA)
        val mine = providers.firstOrNull { it.activityInfo.name == StudioWidget::class.java.name }
        assertTrue(providers.map { it.activityInfo.name }.toString(), mine != null)
        assertEquals(R.xml.studio_widget_info, mine!!.activityInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        // 點一下：打開工作室
        val pi = StudioWidget.openStudio(ctx)
        val intent = shadowOf(pi).savedIntent
        assertEquals(ComponentName(ctx, StudioActivity::class.java), intent.component)
        // 畫出來看看
        val v = StudioWidget.views(ctx).apply(ctx, FrameLayout(ctx))
        val size = (72 * 2.625f).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF5B7DB1.toInt()) // 假裝是桌布
        v.draw(c)
        File("build/renders").mkdirs()
        File("build/renders/studio-widget.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun appIconShortcut() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val list = ctx.getSystemService(ShortcutManager::class.java).dynamicShortcuts
        assertEquals(listOf("studio"), list.map { it.id })
        assertEquals(ComponentName(ctx, StudioActivity::class.java), list[0].intent!!.component)
    }
}
