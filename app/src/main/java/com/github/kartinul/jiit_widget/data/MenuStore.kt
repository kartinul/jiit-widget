package com.github.kartinul.jiit_widget.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate

object MenuStore {
    private const val KEY_MENU = "menu"
    private const val KEY_COMPACT = "compact"
    private val gson = Gson()
    private val menuType = object : TypeToken<Map<String, MenuItem>>() {}.type

    fun load(context: Context): Map<String, MenuItem> =
        prefs(context).getString(KEY_MENU, null)?.let { gson.fromJson(it, menuType) } ?: emptyMap()

    fun save(context: Context, menu: Map<String, MenuItem>) =
        prefs(context).edit { putString(KEY_MENU, gson.toJson(menu)) }

    fun isCompact(context: Context) = prefs(context).getBoolean(KEY_COMPACT, false)

    fun setCompact(context: Context, compact: Boolean) = prefs(context).edit { putBoolean(KEY_COMPACT, compact) }

    fun hasMenuFor(context: Context, date: LocalDate) = date.toString() in load(context)

    /** Emits [read] now and again on every prefs change. */
    fun <T> watch(context: Context, read: (Context) -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read(context)) }
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
        send(read(context))
        awaitClose { prefs(context).unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("jiit_widget_prefs", Context.MODE_PRIVATE)
}
