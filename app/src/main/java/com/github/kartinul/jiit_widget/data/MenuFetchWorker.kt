package com.github.kartinul.jiit_widget.data

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.kartinul.jiit_widget.data.api.MenuFetcher
import com.github.kartinul.jiit_widget.widget.MenuWidget

const val MENU_FETCH = "menu_fetch"

class MenuFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val menu = try {
            MenuFetcher.fetch()
        } catch (e: Exception) {
            Log.e("MenuFetchWorker", "Error fetching menu", e)
            return Result.retry()
        }
        MenuStore.save(applicationContext, menu)
        MenuWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context, force: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<MenuFetchWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(MENU_FETCH, if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }
    }
}
