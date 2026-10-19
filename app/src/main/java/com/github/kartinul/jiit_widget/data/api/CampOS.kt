package com.github.kartinul.jiit_widget.data.api

import com.github.kartinul.jiit_widget.data.MenuItem
import com.github.kartinul.jiit_widget.data.weekMenu
import com.google.gson.Gson
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private data class WeeklyMenuResponse(val weekly: List<MenuItem>?, val dateSpan: String?)

private val spanFormat = DateTimeFormatter.ofPattern("dd.MM.yy")

/** First date of a span like "12.10.26 TO 18.10.26". */
fun weekStart(dateSpan: String?): LocalDate? = try {
    dateSpan?.substringBefore(" ")?.let { LocalDate.parse(it, spanFormat) }
} catch (_: DateTimeParseException) {
    null
}

object CampOS : MenuApi {
    private const val URL = "https://campos-fmjh.onrender.com/api/mess/weekly"

    override fun fetch(get: (String) -> String) = parse(get(URL))

    fun parse(body: String): Map<String, MenuItem> {
        val response = Gson().fromJson(body, WeeklyMenuResponse::class.java)
        val weekly = checkNotNull(response?.weekly) { "Missing weekly menu" }
        return weekMenu(weekly, weekStart(response.dateSpan) ?: LocalDate.now())
    }
}
