package com.github.kartinul.jiit_widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import com.google.gson.Gson
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// -----------------------------------------------------------------------------
// Constants
// -----------------------------------------------------------------------------
private const val ACTION_WIDGET_RELOAD = "com.github.kartinul.jiit_widget.ACTION_WIDGET_REFRESH"
private const val ACTION_WIDGET_REFETCH = "com.github.kartinul.jiit_widget.ACTION_WIDGET_REFETCH"
private const val MENU_URL = "https://jportal2-0.vercel.app/mess_menu.json"

// -----------------------------------------------------------------------------
// Cache
// -----------------------------------------------------------------------------
private object MenuCache {
    var menuResponse: MenuResponse? = null
    var weekEndTime: Long = 0

    fun isValid(): Boolean {
        val todayKey = getTodayDataKey(0)
        val hasTodayMenu = menuResponse?.menu?.get(todayKey) != null
        val stillValid = System.currentTimeMillis() < weekEndTime
        return hasTodayMenu && stillValid
    }

    fun set(response: MenuResponse?) {
        if (response == null) return

        menuResponse = response
        val formatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yy", Locale.ENGLISH)

        val lastDate = response.menu.keys.maxOfOrNull {
            LocalDate.parse(it, formatter)
        }

        lastDate?.let {
            weekEndTime = it.atTime(LocalTime.MAX)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
    }
}

// -----------------------------------------------------------------------------
// Widget Provider
// -----------------------------------------------------------------------------
class JiitMain : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("JiitMain", "onUpdate called")

        val updateViews: (MenuResponse?) -> Unit = { menu ->
            MenuCache.set(menu)
            Handler(Looper.getMainLooper()).post {
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id, MenuCache.menuResponse)
                }
            }
        }

        if (MenuCache.isValid()) {
            updateViews(MenuCache.menuResponse)
        } else {
            fetchMessMenu(updateViews)
        }

        WidgetScheduler.scheduleNextUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("JiitMain", "Widget enabled")

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, JiitMain::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(component)

        fetchMessMenu { menu ->
            MenuCache.set(menu)
            Handler(Looper.getMainLooper()).post {
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id, menu)
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_RELOAD -> {
                Log.d("JiitMain", "Received scheduled refresh.")
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, JiitMain::class.java)
                val ids = manager.getAppWidgetIds(component)
                for (id in ids) {
                    updateAppWidget(context, manager, id, MenuCache.menuResponse)
                }
            }
            ACTION_WIDGET_REFETCH -> {
                Log.d("JiitMain", "Manual refresh clicked.")
                fetchMessMenu { menu ->
                    MenuCache.set(menu)
                    val manager = AppWidgetManager.getInstance(context)
                    val component = ComponentName(context, JiitMain::class.java)
                    val ids = manager.getAppWidgetIds(component)
                    Handler(Looper.getMainLooper()).post {
                        for (id in ids) {
                            updateAppWidget(context, manager, id, MenuCache.menuResponse)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// UI + Logic
// -----------------------------------------------------------------------------
internal fun updateAppWidget(context: Context, manager: AppWidgetManager, id: Int, menu: MenuResponse?) {
    val views = RemoteViews(context.packageName, R.layout.jiit_main)
    setupIntents(context, id, views)

    val todayKey = getTodayDataKey(0)
    val todayMenu = menu?.menu?.get(todayKey)
    val timeOfMeal = getTimeOfMeal()

    val widgetText = when (timeOfMeal) {
        Constants.UPCOMING_BREAKFAST -> menu?.menu?.get(getTodayDataKey(1))?.breakfast
        Constants.BREAKFAST -> todayMenu?.breakfast
        Constants.LUNCH -> todayMenu?.lunch
        Constants.DINNER -> todayMenu?.dinner
        else -> null
    } ?: if (todayMenu == null) {
        "Couldn't find weekly menu"
    } else {
        "Couldn't find current menu"
    }

    views.setTextViewText(R.id.mess_menu, widgetText)
    views.setTextViewText(R.id.time_of_meal, timeOfMeal)

    manager.updateAppWidget(id, views)
}

private fun setupIntents(context: Context, appWidgetId: Int, views: RemoteViews) {
    val reloadIntent = Intent(context, JiitMain::class.java).apply { action = ACTION_WIDGET_RELOAD }
    val reloadPending = PendingIntent.getBroadcast(
        context, appWidgetId, reloadIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, reloadPending)

    val refetchIntent = Intent(context, JiitMain::class.java).apply { action = ACTION_WIDGET_REFETCH }
    val refetchPending = PendingIntent.getBroadcast(
        context, appWidgetId, refetchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.refresh_menu, refetchPending)
}

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------
private fun fetchMessMenu(onResult: (MenuResponse?) -> Unit) {
    Thread {
        try {
            val connection = URL(MENU_URL).openConnection() as HttpURLConnection
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        val response = Gson().fromJson(reader, MenuResponse::class.java)
                        onResult(response)
                    }
                }
            } else {
                Log.e("JiitMain", "Failed: HTTP ${connection.responseCode}")
                onResult(null)
            }
        } catch (e: Exception) {
            Log.e("JiitMain", "Network fetch failed", e)
            onResult(null)
        }
    }.start()
}

private fun getTimeOfMeal(): String {
    val hour = LocalDateTime.now().hour
    return when {
        hour < Constants.BREAKFAST_HOUR_END -> Constants.BREAKFAST
        hour < Constants.LUNCH_HOUR_END -> Constants.LUNCH
        hour < Constants.DINNER_HOUR_END -> Constants.DINNER
        else -> Constants.UPCOMING_BREAKFAST
    }
}

private fun getTodayDataKey(daysToAdd: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yy", Locale.ENGLISH)
    return LocalDate.now().plusDays(daysToAdd)
        .format(formatter)
        .replaceFirstChar { it.titlecase(Locale.ENGLISH) }
}
