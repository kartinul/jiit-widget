package com.github.kartinul.jiit_widget

import com.github.kartinul.jiit_widget.data.MenuItem
import com.github.kartinul.jiit_widget.data.api.JiitPulse
import com.github.kartinul.jiit_widget.data.api.weekStart
import com.github.kartinul.jiit_widget.data.dishes
import com.github.kartinul.jiit_widget.data.mealAt
import com.github.kartinul.jiit_widget.data.nextMeal
import com.github.kartinul.jiit_widget.data.weekMenu
import com.github.kartinul.jiit_widget.widget.WidgetScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MenuTest {
    private val sunday = LocalDate.of(2026, 10, 18)
    private val menu = weekMenu(
        listOf(
            MenuItem("Monday", "poha", "rajma", "paneer"),
            MenuItem(" sunday ", "idli", "chole", "dal"),
            MenuItem("Funday", "x", "x", "x"),
        ),
        sunday,
    )

    @Test fun keysDaysByIsoDateOfCurrentWeek() =
        assertEquals(setOf("2026-10-12", "2026-10-18"), menu.keys)

    @Test fun parsesWeekStartFromDateSpan() {
        assertEquals(LocalDate.of(2026, 10, 12), weekStart("12.10.26 TO 18.10.26"))
        assertEquals(null, weekStart("garbage"))
        assertEquals(null, weekStart(null))
    }

    @Test fun picksMealByHour() {
        assertEquals("idli", mealAt(sunday.atTime(8, 59), menu).items)
        assertEquals("chole", mealAt(sunday.atTime(9, 0), menu).items)
        assertEquals("dal", mealAt(sunday.atTime(20, 0), menu).items)
        assertEquals("Tomorrow's Breakfast", mealAt(sunday.atTime(21, 0), menu).title)
        assertEquals(null, mealAt(sunday.atTime(21, 0), menu).items)
    }

    @Test fun peeksNextMeal() {
        assertEquals("chole", nextMeal(sunday.atTime(8, 0), menu).items)
        assertEquals("Tomorrow's Breakfast", nextMeal(sunday.atTime(20, 0), menu).title)
        assertEquals("Tomorrow's Lunch", nextMeal(sunday.atTime(22, 0), menu).title)
        assertEquals(null, nextMeal(sunday.atTime(20, 0), menu).items)
        assertEquals("rajma", nextMeal(LocalDate.of(2026, 10, 11).atTime(22, 0), menu).items)
    }

    @Test fun parsesJiitPulseBundle() {
        val bundle = """x.s(["rules",0,["a {b}"],"messMenu",0,{"2026-10-19":{day:"Monday",Breakfast:"Poha, Tea {hot}",Lunch:"Rajma",Dinner:"Veg Keema"}},""" +
            """"messTimings",0,{}]),a.s(["messMenu",0,{"2026-10-19":{day:"Monday",Lunch:"Other"}}])"""
        assertEquals(mapOf("2026-10-19" to MenuItem("Monday", "Poha, Tea {hot}", "Rajma", "Veg Keema")), JiitPulse.parse(bundle))
        assertEquals(null, JiitPulse.parse("no menu here"))
    }

    @Test fun splitsDishes() =
        assertEquals(listOf("Chana - Poori", "Bread Jam", "Tea"), dishes("Chana - Poori, Bread Jam,  Tea,"))

    @Test fun schedulesNextBoundary() {
        assertEquals(LocalDateTime.of(2026, 10, 18, 14, 4), WidgetScheduler.nextUpdate(sunday.atTime(9, 4), false))
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 4), WidgetScheduler.nextUpdate(sunday.atTime(22, 0), false))
    }

    @Test fun retriesSoonerWhileMenuMissing() {
        assertEquals(LocalDateTime.of(2026, 10, 18, 11, 4), WidgetScheduler.nextUpdate(sunday.atTime(9, 4), true))
        assertEquals(LocalDateTime.of(2026, 10, 18, 14, 4), WidgetScheduler.nextUpdate(sunday.atTime(13, 0), true))
    }
}
