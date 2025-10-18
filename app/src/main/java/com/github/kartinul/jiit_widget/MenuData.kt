package com.github.kartinul.jiit_widget

import com.google.gson.annotations.SerializedName
/**
 * This is the top-level class that matches the root of the JSON object.
 * It contains one key: "menu".
 */
data class MenuResponse(
    val menu: Map<String,DayMenu>
)

/**
 * This class represents the meals for a single day.
 */
data class DayMenu(
    @SerializedName(Constants.BREAKFAST) val breakfast: String,
    @SerializedName(Constants.LUNCH) val lunch: String,
    @SerializedName(Constants.DINNER) val dinner: String
)