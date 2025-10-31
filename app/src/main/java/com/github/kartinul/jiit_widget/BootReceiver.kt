package com.github.kartinul.jiit_widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.yourapp.WidgetScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            WidgetScheduler.scheduleNextUpdate(context)
        }
    }
}
