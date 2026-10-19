package com.github.kartinul.jiit_widget.data

import androidx.annotation.DrawableRes
import com.github.kartinul.jiit_widget.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class Meal(val title: String, private val end: LocalTime, private val sundayEnd: LocalTime, @DrawableRes val icon: Int) {
    BREAKFAST("Breakfast", LocalTime.of(9, 0), LocalTime.of(9, 30), R.drawable.ic_breakfast),
    LUNCH("Lunch", LocalTime.of(14, 0), LocalTime.of(14, 30), R.drawable.ic_lunch),
    DINNER("Dinner", LocalTime.of(21, 30), LocalTime.of(21, 30), R.drawable.ic_dinner),
    ;

    fun endsAt(date: LocalDate): LocalDateTime = date.atTime(if (date.dayOfWeek == DayOfWeek.SUNDAY) sundayEnd else end)
}

data class MenuItem(val day: String?, val breakfast: String?, val lunch: String?, val dinner: String?)

data class MealSlot(val meal: Meal, val title: String, val items: String?)

/** Keys each item by its ISO date, assuming the API's week is the one containing [day]. */
fun weekMenu(items: List<MenuItem>, day: LocalDate): Map<String, MenuItem> {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return items.mapNotNull { item ->
        val weekday = DayOfWeek.entries.find { it.name.equals(item.day?.trim(), ignoreCase = true) }
            ?: return@mapNotNull null
        monday.plusDays(weekday.ordinal.toLong()).toString() to item
    }.toMap()
}

fun dishes(items: String) = items.split(",").map(String::trim).filter(String::isNotEmpty)

fun mealAt(now: LocalDateTime, menu: Map<String, MenuItem>): MealSlot {
    val (date, meal) = currentMeal(now)
    return slot(date, meal, menu, now.toLocalDate())
}

fun nextMeal(now: LocalDateTime, menu: Map<String, MenuItem>): MealSlot {
    val (date, meal) = currentMeal(now)
    val next = Meal.entries.getOrNull(meal.ordinal + 1)
    return if (next != null) slot(date, next, menu, now.toLocalDate())
    else slot(date.plusDays(1), Meal.BREAKFAST, menu, now.toLocalDate())
}

private fun currentMeal(now: LocalDateTime): Pair<LocalDate, Meal> {
    val meal = Meal.entries.firstOrNull { now.isBefore(it.endsAt(now.toLocalDate())) }
    return if (meal != null) now.toLocalDate() to meal else now.toLocalDate().plusDays(1) to Meal.BREAKFAST
}

private fun slot(date: LocalDate, meal: Meal, menu: Map<String, MenuItem>, today: LocalDate): MealSlot {
    val day = menu[date.toString()]
    val items = when (meal) {
        Meal.BREAKFAST -> day?.breakfast
        Meal.LUNCH -> day?.lunch
        Meal.DINNER -> day?.dinner
    }
    return MealSlot(meal, if (date > today) "Tomorrow's ${meal.title}" else meal.title, items)
}
