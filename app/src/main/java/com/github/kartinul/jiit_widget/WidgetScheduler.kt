package com.example.yourapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.github.kartinul.jiit_widget.JiitMain
import java.util.*

object WidgetScheduler {

    // Format: (hour, minute)
    private val updateTimes = listOf(
        0 to 0,
        9 to 30,
        15 to 0,
        21 to 0
    )

    fun scheduleNextUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, JiitMain::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Figure out next time from now
        val now = Calendar.getInstance()
        var nextTime: Calendar? = null

        for ((hour, minute) in updateTimes) {
            val candidate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.after(now)) {
                nextTime = candidate
                break
            }
        }

        // if no more times left today, roll over to tomorrow's first
        if (nextTime == null) {
            val (hour, minute) = updateTimes.first()
            nextTime = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val intent = Intent(context, JiitMain::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999, // just one repeating alarm
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTime.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        nextTime.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTime.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("WidgetScheduler", "Next update at ${nextTime.time}")
        } catch (e: SecurityException) {
            Log.e("WidgetScheduler", "Exact alarm permission missing", e)
        }
    }

    fun cancelUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, JiitMain::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
