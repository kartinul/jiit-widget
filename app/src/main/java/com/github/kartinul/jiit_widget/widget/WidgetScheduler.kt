package com.github.kartinul.jiit_widget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.github.kartinul.jiit_widget.data.Meal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object WidgetScheduler {
    const val ACTION_TICK = "com.github.kartinul.jiit_widget.ACTION_TICK"
    private const val RETRY_HOURS = 2L

    /** Midnight and each meal's end on [date], plus a few minutes of slack. */
    private fun boundaries(date: LocalDate) =
        (listOf(date.atStartOfDay()) + Meal.entries.map { it.endsAt(date) }).map { it.plusMinutes(4) }

    fun nextUpdate(now: LocalDateTime, menuMissing: Boolean): LocalDateTime {
        val boundary = boundaries(now.toLocalDate()).firstOrNull { it.isAfter(now) }
            ?: boundaries(now.toLocalDate().plusDays(1)).first()
        return if (menuMissing) minOf(boundary, now.plusHours(RETRY_HOURS)) else boundary
    }

    fun scheduleNext(context: Context, menuMissing: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val next = nextUpdate(LocalDateTime.now(), menuMissing)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, tickIntent(context))
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, tickIntent(context))
            }
            Log.d("WidgetScheduler", "Next update scheduled for $next")
        } catch (e: SecurityException) {
            Log.e("WidgetScheduler", "Missing exact alarm permission", e)
        }
    }

    fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))

    private fun tickIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MenuWidgetReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
