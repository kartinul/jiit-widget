package com.github.kartinul.jiit_widget

data class MenuResponse(
    val menu: Map<String,DayMenu>
)

data class DayMenu(
    val breakfast: String,
    val lunch: String,
    val dinner: String
)

// API structure
data class ApiMenuResponse(
    val weekly: List<ApiMenuItem>
)

data class ApiMenuItem(
    val day: String,
    val breakfast: String,
    val lunch: String,
    val dinner: String
)
