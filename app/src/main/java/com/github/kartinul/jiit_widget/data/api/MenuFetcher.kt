package com.github.kartinul.jiit_widget.data.api

import android.util.Log
import com.github.kartinul.jiit_widget.data.MenuItem
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

object MenuFetcher {
    /** Tried in order until one has today's menu. */
    private val apis: List<MenuApi> = listOf(CampOS, JiitPulse)

    fun fetch(today: LocalDate = LocalDate.now()): Map<String, MenuItem> {
        var stale: Map<String, MenuItem>? = null
        val errors = mutableListOf<Exception>()
        for (api in apis) {
            try {
                val menu = api.fetch(::get)
                if (today.toString() in menu) return menu
                stale = stale ?: menu
            } catch (e: Exception) {
                Log.w("MenuFetcher", "${api.javaClass.simpleName} failed", e)
                errors += e
            }
        }
        return stale ?: throw IllegalStateException("All menu APIs failed").apply { errors.forEach(::addSuppressed) }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        // render.com free tier cold-starts slowly
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.reader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
