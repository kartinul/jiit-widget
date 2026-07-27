package com.github.kartinul.jiit_widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val MENU_URL = "https://campos-fmjh.onrender.com/api/mess/weekly"

class MenuFetchWorker(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        return try {
            val connection = URL(MENU_URL).openConnection() as HttpURLConnection

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { stream ->
                    InputStreamReader(stream).use { reader ->
                        val apiResponse = Gson().fromJson(reader, ApiMenuResponse::class.java)
                        
                        // Transform ApiMenuResponse to MenuResponse
                        val menuMap = mutableMapOf<String, DayMenu>()
                        val today = LocalDate.now()
                        // Assume the "weekly" menu is for the current week starting Monday
                        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        
                        val dateFormatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yy", Locale.ENGLISH)
                        
                        apiResponse.weekly.forEach { item ->
                            try {
                                val dayOfWeek = DayOfWeek.valueOf(item.day.uppercase(Locale.ENGLISH))
                                // Calculate the date for this day in the current week
                                val date = monday.plusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
                                val key = date.format(dateFormatter)
                                
                                menuMap[key] = DayMenu(
                                    breakfast = item.breakfast,
                                    lunch = item.lunch,
                                    dinner = item.dinner
                                )
                            } catch (e: Exception) {
                                Log.e("MenuFetchWorker", "Error parsing day: ${item.day}", e)
                            }
                        }

                        val response = MenuResponse(menu = menuMap)
                        MenuCache.set(response, ctx)

                        // update widget
                        val manager = AppWidgetManager.getInstance(ctx)
                        val component = ComponentName(ctx, JiitMain::class.java)
                        val ids = manager.getAppWidgetIds(component)
                        ids.forEach { id ->
                            updateAppWidget(ctx, manager, id)
                        }
                    }
                }
                Result.success()
            } else {
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e("MenuFetchWorker", "Error fetching menu", e)
            Result.retry()
        }
    }
}
