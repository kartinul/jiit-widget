package com.github.kartinul.jiit_widget

import com.google.gson.annotations.SerializedName

data class MenuResponse(
    val menu: Map<String,DayMenu>
)

data class DayMenu(
    @SerializedName(Constants.BREAKFAST) val breakfast: String,
    @SerializedName(Constants.LUNCH) val lunch: String,
    @SerializedName(Constants.DINNER) val dinner: String
)