@file:OptIn(ExperimentalGlancePreviewApi::class)

package com.github.kartinul.jiit_widget.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.github.kartinul.jiit_widget.data.Meal
import com.github.kartinul.jiit_widget.data.MealSlot

@Composable
private fun Sample(slot: MealSlot? = sampleSlot, fetching: Boolean = false, peeking: Boolean = false, compact: Boolean = false) =
    GlanceTheme { MenuContent(slot, fetching, peeking, compact, onPeek = {}, onTap = {}) }

@Preview(widthDp = 180, heightDp = 180)
@Composable
private fun Square() = Sample()

@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun Wide() = Sample()

@Preview(widthDp = 180, heightDp = 180)
@Composable
private fun Compact() = Sample(compact = true)

@Preview(widthDp = 180, heightDp = 180)
@Composable
private fun Peeking() = Sample(slot = MealSlot(Meal.DINNER, "Dinner", "Paneer Masala, Masoor Dal, Raita, Rice, Roti, Achar, Salad, Milk"), peeking = true)

@Preview(widthDp = 180, heightDp = 180)
@Composable
private fun Refetching() = Sample(fetching = true)

@Preview(widthDp = 180, heightDp = 180)
@Composable
private fun FirstLoad() = Sample(slot = null)

@Preview(widthDp = 180, heightDp = 180)
@Composable
private fun MenuNotOut() = Sample(slot = sampleSlot.copy(items = null))
