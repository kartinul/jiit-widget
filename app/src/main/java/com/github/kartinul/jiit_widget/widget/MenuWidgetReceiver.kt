package com.github.kartinul.jiit_widget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.github.kartinul.jiit_widget.data.MenuFetchWorker
import com.github.kartinul.jiit_widget.data.MenuStore
import java.time.LocalDate

class MenuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MenuWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val menuMissing = !MenuStore.hasMenuFor(context, LocalDate.now())
        if (menuMissing) MenuFetchWorker.enqueue(context)
        WidgetScheduler.scheduleNext(context, menuMissing)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != WidgetScheduler.ACTION_TICK && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        if (ids.isNotEmpty()) onUpdate(context, manager, ids)
    }
}
