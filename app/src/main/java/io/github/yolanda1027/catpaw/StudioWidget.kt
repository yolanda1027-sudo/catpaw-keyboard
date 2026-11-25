package io.github.yolanda1027.catpaw

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * 桌面小工具「主題工作室」：點一下打開工作室。
 * 工作室不再有自己的桌面圖示，需要的人長按桌面 → 小工具 → 貓爪鍵盤，把它拉到桌面上。
 */
class StudioWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(ctx))
    }

    companion object {
        fun views(ctx: Context): RemoteViews = RemoteViews(ctx.packageName, R.layout.widget_studio).apply {
            setOnClickPendingIntent(R.id.widget_root, openStudio(ctx))
        }

        fun openStudio(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
