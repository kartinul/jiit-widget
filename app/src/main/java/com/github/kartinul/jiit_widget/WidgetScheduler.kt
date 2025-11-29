package com.github.kartinul.jiit_widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.*

object WidgetScheduler {

    // Format: (hour, minute) — add/change these for your meal schedule
    private val updateTimes = listOf(
        0 to 4, // midnight reset
        Constants.BREAKFAST_HOUR_END to 4,
        Constants.LUNCH_HOUR_END to 4,
        Constants.DINNER_HOUR_END to 4
    )

    private const val ALARM_REQUEST_CODE = 999

    fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val componentName = ComponentName(context, JiitMain::class.java)
        val widgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(componentName)

        val now = Calendar.getInstance()
        val nextTime = getNextTriggerTime(now)

        if (nextTime == null) {
            Log.w("WidgetScheduler", "No next update time found.")
            return
        }

        val intent = Intent(context, JiitMain::class.java).apply {
            action = "com.github.kartinul.jiit_widget.ACTION_WIDGET_REFRESH"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTime.timeInMillis,
                        pendingIntent
                    )
                }
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTime.timeInMillis,
                        pendingIntent
                    )
                }
                else -> {
                    // Fallback if user denied exact alarms
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        nextTime.timeInMillis,
                        pendingIntent
                    )
                }
            }

            Log.d("WidgetScheduler", "Next update scheduled for: ${nextTime.time}")
        } catch (e: SecurityException) {
            Log.e("WidgetScheduler", "Missing exact alarm permission", e)
        }
    }

    fun cancelUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, JiitMain::class.java).apply {
            action = "com.github.kartinul.jiit_widget.ACTION_WIDGET_REFRESH"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d("WidgetScheduler", "Canceled all scheduled updates")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------
    private fun getNextTriggerTime(now: Calendar): Calendar? {
        for ((hour, minute) in updateTimes) {
            val candidate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.after(now)) return candidate
        }

        // None left today → next day's first trigger
        val (firstHour, firstMinute) = updateTimes.first()
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, firstHour)
            set(Calendar.MINUTE, firstMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
