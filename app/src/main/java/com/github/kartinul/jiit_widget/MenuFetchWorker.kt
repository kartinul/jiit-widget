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

private const val MENU_URL = "https://jportal2-0.vercel.app/mess_menu.json"

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
                        val response = Gson().fromJson(reader, MenuResponse::class.java)
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
