package com.github.kartinul.jiit_widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.google.gson.Gson
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private object MenuCache {
    var menuResponse: MenuResponse? = null
    var lastFetchTime: Long = 0
    private const val CACHE_DURATION_MS = 2 * 60 * 60 * 1000L

    fun isValid(): Boolean {
        return menuResponse != null && (System.currentTimeMillis() - lastFetchTime) < CACHE_DURATION_MS
    }

    fun set(response: MenuResponse?) {
        menuResponse = response
        lastFetchTime = System.currentTimeMillis()
    }
}

class JiitMain : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (MenuCache.isValid()) {
            for (appWidgetId in appWidgetIds)
                updateAppWidget(context, appWidgetManager, appWidgetId, MenuCache.menuResponse)
            return
        }
        fetchMessMenu { menuResponse ->

            if (menuResponse == null) {
                return@fetchMessMenu
            }

            MenuCache.set(menuResponse)
            for (appWidgetId in appWidgetIds)
                updateAppWidget(context, appWidgetManager, appWidgetId, MenuCache.menuResponse)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    menuResponse: MenuResponse?
) {
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

    if (widgetText == null) {
        widgetText = "Couldn't find the menu"
    }

    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.jiit_main)
    views.setTextViewText(R.id.mess_menu, widgetText)
    views.setTextViewText(R.id.time_of_meal, timeOfMeal)
    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
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
        // Before 9:30 AM is Breakfast
        nowHour < Constants.BREAKFAST_HOUR_END -> Constants.BREAKFAST

        // From 9:30 AM until 2:00 PM (14:00) is Lunch
        nowHour < Constants.LUNCH_HOUR_END -> Constants.LUNCH

        // From 2:00 PM (14:00) until 9:00 PM (21:00) is Dinner
        nowHour < Constants.DINNER_HOUR_END || (nowHour == Constants.DINNER_HOUR_END && nowMinute < Constants.DINNER_MINUTE_END) -> Constants.DINNER

        // After 9:00 PM, we can show the breakfast for the next day
        else -> Constants.UPCOMING_BREAKFAST
    }
}
private fun getTodayDataKey(daysToAdd: Long): String {
    val today = LocalDate.now().plusDays(daysToAdd)
    val formatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yy",
        Locale.ENGLISH)
    return today.format(formatter).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
}
