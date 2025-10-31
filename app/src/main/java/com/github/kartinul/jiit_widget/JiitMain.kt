package com.github.kartinul.jiit_widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import com.example.yourapp.WidgetScheduler
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

private const val ACTION_WIDGET_RELOAD = "com.github.kartinul.jiit_widget.ACTION_WIDGET_REFRESH"
private const val ACTION_WIDGET_REFETCH = "com.github.kartinul.jiit_widget.SEARCH_CLICK"

private object MenuCache {
    var menuResponse: MenuResponse? = null
    var weekEndTime: Long = 0

    fun isValid(): Boolean {
        val isTodayMenuExist = menuResponse?.menu[getTodayDataKey(0)] != null
        val isCacheValid = System.currentTimeMillis() < weekEndTime
        return isTodayMenuExist && isCacheValid
    }

    fun set(response: MenuResponse?) {
        if (response != null) {
            menuResponse = response

            val lastMenuDate = response.menu.keys.maxOfOrNull { key ->
                val formatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yy", Locale.ENGLISH)
                LocalDate.parse(key, formatter)
            }
            if (lastMenuDate != null) {
                weekEndTime = lastMenuDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
    }
}

class JiitMain : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        if (MenuCache.isValid()) {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, MenuCache.menuResponse)
            }
        }
        else {
            fetchMessMenu { menuResponse ->
                MenuCache.set(menuResponse)
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, MenuCache.menuResponse)
                }
            }
        }

        WidgetScheduler.scheduleNextUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_WIDGET_RELOAD -> {
                Log.d("JiitMain", "Received widget refresh alarm.")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = android.content.ComponentName(context, JiitMain::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            ACTION_WIDGET_REFETCH -> {
                fetchMessMenu { menuResponse ->
                    MenuCache.set(menuResponse)

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = android.content.ComponentName(context, JiitMain::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    onUpdate(context, appWidgetManager, appWidgetIds)
                }
            }
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    menuResponse: MenuResponse?
) {
    val views = RemoteViews(context.packageName, R.layout.jiit_main)
    handleIntents(context, appWidgetId, views)

    val todayKey = getTodayDataKey(0)
    val todayMenu = menuResponse?.menu[todayKey]

    var widgetText: String?
    val timeOfMeal = getTimeOfMeal()
    if (timeOfMeal == Constants.UPCOMING_BREAKFAST) {
        val tomorrowMenu = menuResponse?.menu[getTodayDataKey(1)]
        widgetText = tomorrowMenu?.breakfast
    }
    else {
        widgetText = when (timeOfMeal) {
            Constants.BREAKFAST  -> todayMenu?.breakfast
            Constants.LUNCH -> todayMenu?.lunch
            Constants.DINNER -> todayMenu?.dinner
            else -> null
        }
    }


    val isTodayMenuExist = MenuCache.menuResponse?.menu[getTodayDataKey(0)] != null
    if (menuResponse == null || !isTodayMenuExist) {
        widgetText = "Couldn't find weekly menu"
    }
    else if (widgetText == null) {
        widgetText = "Couldn't find current menu"
    }

    views.setTextViewText(R.id.mess_menu, widgetText)
    views.setTextViewText(R.id.time_of_meal, timeOfMeal)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun handleIntents(
    context: Context,
    appWidgetId: Int,
    views: RemoteViews
) {
    val reloadWidgetIntent = Intent(context, JiitMain::class.java).apply {
        action = ACTION_WIDGET_RELOAD
    }
    val reloadWidgetPendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        reloadWidgetIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, reloadWidgetPendingIntent)

    val refetchIntent = Intent(context, JiitMain::class.java).apply {
        action = ACTION_WIDGET_REFETCH
    }
    val refetchPendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        refetchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.refresh_menu, refetchPendingIntent)
}

private fun fetchMessMenu(onResult: (MenuResponse?) -> Unit) {
    Thread {
        try {
            val url = URL("https://jportal2-0.vercel.app/mess_menu.json")
            val connection = url.openConnection() as HttpURLConnection

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputSystem = connection.inputStream
                val inputSystemReader = InputStreamReader(inputSystem, "UTF-8")
                val request = Gson().fromJson(inputSystemReader, MenuResponse::class.java)

                onResult(request)

                inputSystemReader.close()
                inputSystem.close()
            } else {
                onResult(null)
            }
        }
        catch (e: Exception) {
            Log.e("FETCH_ERROR", "Network req failed", e)
            onResult(null)
        }
    }.start()

}

private fun getTimeOfMeal(): String {
    val now = LocalDateTime.now()
    val nowHour = now.hour
    val nowMinute = now.minute

    return when {
        nowHour < Constants.BREAKFAST_HOUR_END || (nowHour == Constants.BREAKFAST_HOUR_END && nowMinute < Constants.DINNER_MINUTE_END) -> Constants.BREAKFAST

        nowHour < Constants.LUNCH_HOUR_END -> Constants.LUNCH

        nowHour < Constants.DINNER_HOUR_END || (nowHour == Constants.DINNER_HOUR_END && nowMinute < Constants.DINNER_MINUTE_END) -> Constants.DINNER

        else -> Constants.UPCOMING_BREAKFAST
    }
}
private fun getTodayDataKey(daysToAdd: Long): String {
    val today = LocalDate.now().plusDays(daysToAdd)
    val formatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yy",
        Locale.ENGLISH)
    return today.format(formatter).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
}
