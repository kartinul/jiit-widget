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
import androidx.core.content.edit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
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

// -----------------------------------------------------------------------------
// Cache
// -----------------------------------------------------------------------------
object MenuCache {
    var menuResponse: MenuResponse? = null
    var weekEndTime: Long = 0

    fun isValid(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val prefs =
            context.applicationContext.getSharedPreferences(
                "jiit_widget_prefs",
                Context.MODE_PRIVATE
            )

        val expiryFromPrefs = prefs.getLong("cached_menu_expiry", 0)
        val expiryTime = if (weekEndTime > 0) weekEndTime else expiryFromPrefs

        val stillValid = now < expiryTime

        if (!stillValid) {
            prefs.edit(commit = true) {
                remove("cached_menu_json")
                remove("cached_menu_expiry")
            }
            menuResponse = null
            weekEndTime = 0

            return false
        }

        val todayKey = getTodayDataKey(0)
        val hasTodayMenu = menuResponse?.menu?.containsKey(todayKey) == true

        return hasTodayMenu
    }

    fun set(response: MenuResponse?, context: Context) {
        if (response == null) return

        menuResponse = response

        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.ENGLISH)

        // find the latest date from all keys
        val lastDate =
            response.menu.keys
                .mapNotNull { key ->
                    val datePart = key.substringAfter(" ").trim()
                    LocalDate.parse(datePart, dateFormatter)
                }
                .maxOrNull()

        if (lastDate != null) {
            weekEndTime =
                lastDate
                    .atTime(LocalTime.MAX)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
        }

        val json = Gson().toJson(response)
        val prefs =
            context.applicationContext.getSharedPreferences(
                "jiit_widget_prefs",
                Context.MODE_PRIVATE
            )
        prefs.edit(commit = true) {
            putString("cached_menu_json", json)
            putLong("cached_menu_expiry", weekEndTime)
        }

    }
}

// -----------------------------------------------------------------------------
// Widget Provider
// -----------------------------------------------------------------------------
class JiitMain : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("JiitMain", "onUpdate called")

        val updateViews: (MenuResponse?) -> Unit = { menu ->
            MenuCache.set(menu, context)
            Handler(Looper.getMainLooper()).post {
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }

        if (MenuCache.isValid(context)) {
            updateViews(null)
        } else {
            fetchMessMenu(context)
        }

        WidgetScheduler.scheduleNextUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        fetchMessMenu(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, JiitMain::class.java)
        val ids = manager.getAppWidgetIds(component)

        when (intent.action) {
            ACTION_WIDGET_RELOAD -> {
                Log.d("JiitMain", "Reload clicked.")
                for (id in ids) {
                    updateAppWidget(context, manager, id)
                }
            }

            ACTION_WIDGET_REFETCH -> {
                Log.d("JiitMain", "Refetch clicked")
                fetchMessMenu(context)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// UI + Logic
// -----------------------------------------------------------------------------
internal fun updateAppWidget(
    context: Context,
    manager: AppWidgetManager,
    id: Int,
) {
    if (MenuCache.menuResponse == null) {
        val prefs =
            context.applicationContext.getSharedPreferences(
                "jiit_widget_prefs",
                Context.MODE_PRIVATE
            )
        prefs.getString("cached_menu_json", null)?.let { json ->
            val cached = Gson().fromJson(json, MenuResponse::class.java)
            MenuCache.set(cached, context)
            Log.d("WidgetCache", "Restored cache in onUpdate()")
        }
    }

    val menuResponse = MenuCache.menuResponse

    val views = RemoteViews(context.packageName, R.layout.jiit_main)
    setupIntents(context, id, views)

    val todayKey = getTodayDataKey(0)
    val todayMenu = menuResponse?.menu?.get(todayKey)
    val timeOfMeal = getTimeOfMeal()

    var widgetText: String
    if (menuResponse == null) {
        val now = LocalTime.now()
        val hour = now.hour
        val minute = now.minute

        widgetText = "null at time: $hour:$minute"
    } else {
        widgetText =
            when (timeOfMeal) {
                Constants.UPCOMING_BREAKFAST ->
                    menuResponse.menu[getTodayDataKey(1)]?.breakfast

                Constants.BREAKFAST -> todayMenu?.breakfast
                Constants.LUNCH -> todayMenu?.lunch
                Constants.DINNER -> todayMenu?.dinner
                else -> null
            }
                ?: if (todayMenu == null) {
                    "Couldn't find today's menu"
                } else {
                    "Couldn't find timely menu"
                }
    }

    views.setTextViewText(R.id.mess_menu, widgetText)
    views.setTextViewText(R.id.time_of_meal, timeOfMeal)

    manager.updateAppWidget(id, views)
}

private fun setupIntents(context: Context, appWidgetId: Int, views: RemoteViews) {
    val reloadIntent = Intent(context, JiitMain::class.java).apply { action = ACTION_WIDGET_RELOAD }
    val reloadPending =
        PendingIntent.getBroadcast(
            context,
            appWidgetId,
            reloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    views.setOnClickPendingIntent(R.id.widget_root, reloadPending)

    val refetchIntent =
        Intent(context, JiitMain::class.java).apply { action = ACTION_WIDGET_REFETCH }
    val refetchPending =
        PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refetchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    views.setOnClickPendingIntent(R.id.refresh_menu, refetchPending)
}

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------
private fun fetchMessMenu(context: Context) {
    val request = OneTimeWorkRequestBuilder<MenuFetchWorker>().build()
    WorkManager.getInstance(context).enqueue(request)
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
    return LocalDate.now().plusDays(daysToAdd).format(formatter).replaceFirstChar {
        it.titlecase(Locale.ENGLISH)
    }
}
