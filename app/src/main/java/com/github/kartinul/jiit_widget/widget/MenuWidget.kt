package com.github.kartinul.jiit_widget.widget

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.kartinul.jiit_widget.R
import com.github.kartinul.jiit_widget.data.MENU_FETCH
import com.github.kartinul.jiit_widget.data.Meal
import com.github.kartinul.jiit_widget.data.MealSlot
import com.github.kartinul.jiit_widget.data.MenuFetchWorker
import com.github.kartinul.jiit_widget.data.MenuStore
import com.github.kartinul.jiit_widget.data.dishes
import com.github.kartinul.jiit_widget.data.mealAt
import com.github.kartinul.jiit_widget.data.nextMeal
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private const val PEEK_MS = 5_000L
private const val DOUBLE_TAP_MS = 600L
private val SQUARE = DpSize(120.dp, 120.dp)
private val WIDE = DpSize(250.dp, 120.dp)

val sampleSlot = MealSlot(Meal.LUNCH, "Lunch", "Kadai Veg, Rajma, Raita, Rice, Roti, Achar, Salad, Halwa")

class MenuWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SQUARE, WIDE))
    override val previewSizeMode = SizeMode.Responsive(setOf(SQUARE, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fetchWork = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(MENU_FETCH)
        provideContent {
            val menu by remember { MenuStore.watch(context, MenuStore::load) }.collectAsState(MenuStore.load(context))
            val compact by remember { MenuStore.watch(context, MenuStore::isCompact) }.collectAsState(false)
            val fetching = fetchWork.collectAsState(emptyList()).value.any { it.state == WorkInfo.State.RUNNING }
            var peeking by remember { mutableStateOf(false) }
            var lastTap by remember { mutableLongStateOf(0L) }
            LaunchedEffect(peeking) {
                if (peeking) {
                    delay(PEEK_MS)
                    peeking = false
                }
            }
            val now = LocalDateTime.now()
            val slot = when {
                menu.isEmpty() -> null
                peeking -> nextMeal(now, menu)
                else -> mealAt(now, menu)
            }
            GlanceTheme {
                MenuContent(
                    slot = slot,
                    fetching = fetching,
                    peeking = peeking,
                    compact = compact,
                    onPeek = { peeking = !peeking },
                    onTap = {
                        val tap = SystemClock.uptimeMillis()
                        // a forced enqueue replaces, which would cancel a fetch already in flight
                        if (tap - lastTap < DOUBLE_TAP_MS && !fetching) MenuFetchWorker.enqueue(context, force = true)
                        lastTap = tap
                    },
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) = provideContent {
        GlanceTheme { MenuContent(sampleSlot, fetching = false, peeking = false, compact = false, onPeek = {}, onTap = {}) }
    }
}

/** [slot] is null until the first menu has been fetched. */
@Composable
fun MenuContent(
    slot: MealSlot?,
    fetching: Boolean,
    peeking: Boolean,
    compact: Boolean,
    onPeek: () -> Unit,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= WIDE.width
    val loading = fetching || slot == null
    val dishes = slot?.items?.let(::dishes)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(16.dp)
            .clickable(onTap),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .background(ImageProvider(R.drawable.shape_cookie), colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer)),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(GlanceModifier.size(22.dp), color = GlanceTheme.colors.onPrimaryContainer)
                } else {
                    Image(
                        provider = ImageProvider(slot.meal.icon),
                        contentDescription = null,
                        modifier = GlanceModifier.size(22.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    )
                }
            }
            Spacer(GlanceModifier.width(10.dp))
            Text(
                text = if (loading) context.getString(R.string.refetching) else slot.title,
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 2,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
            )
            if (slot != null) {
                CircleIconButton(
                    imageProvider = ImageProvider(if (peeking) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right),
                    contentDescription = context.getString(if (peeking) R.string.current_meal else R.string.next_meal),
                    onClick = onPeek,
                    modifier = GlanceModifier.size(36.dp),
                    backgroundColor = GlanceTheme.colors.secondaryContainer,
                    contentColor = GlanceTheme.colors.onSecondaryContainer,
                )
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (slot != null) {
            if (dishes == null) {
                Text(
                    text = context.getString(R.string.menu_not_out),
                    maxLines = 2,
                    style = TextStyle(fontSize = if (wide) 26.sp else 22.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                )
            }
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                dishes?.take(2)?.forEachIndexed { i, dish ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(if (wide) 72.dp else 64.dp)
                            .background(GlanceTheme.colors.secondaryContainer)
                            .cornerRadius(16.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = dish,
                            maxLines = 2,
                            style = TextStyle(fontSize = if (wide) 20.sp else 16.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSecondaryContainer),
                        )
                    }
                }
            }
            val rest = dishes?.drop(2).orEmpty()
            if (!compact && rest.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = rest.joinToString(" · "),
                    maxLines = if (wide) 3 else 2,
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}
