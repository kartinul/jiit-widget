package com.github.kartinul.jiit_widget.data.api

import com.github.kartinul.jiit_widget.data.MenuItem
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken

/** JIIT Pulse has no menu API; the menu ships inside its Next.js JS bundle as an object literal. */
object JiitPulse : MenuApi {
    private const val SITE = "https://jiit-pulse.vercel.app"
    private const val MARKER = "\"messMenu\",0,"
    private val chunkPattern = Regex("""/_next/static/chunks/[\w-]+\.js""")
    private val gson = GsonBuilder().setStrictness(Strictness.LENIENT).create()
    private val daysType = object : TypeToken<Map<String, Map<String, String>>>() {}.type

    override fun fetch(get: (String) -> String): Map<String, MenuItem> {
        val chunks = chunkPattern.findAll(get(SITE)).map { it.value }.distinct()
        return checkNotNull(chunks.firstNotNullOfOrNull { parse(get(SITE + it)) }) { "No mess menu in bundle" }
    }

    /** Sector 62 menu only: the bundle lists it first, then a Sector 128 one that we skip. */
    fun parse(bundle: String): Map<String, MenuItem>? {
        val start = bundle.indexOf(MARKER).takeIf { it >= 0 } ?: return null
        val days: Map<String, Map<String, String>> = gson.fromJson(objectAt(bundle, start + MARKER.length), daysType)
        return days.mapValues { (_, day) -> MenuItem(day["day"], day["Breakfast"], day["Lunch"], day["Dinner"]) }
    }

    /** The `{...}` literal starting at [start], ignoring braces inside strings. */
    private fun objectAt(src: String, start: Int): String {
        var depth = 0
        var inString = false
        var i = start
        while (i < src.length) {
            when {
                inString && src[i] == '\\' -> i++
                src[i] == '"' -> inString = !inString
                !inString && src[i] == '{' -> depth++
                !inString && src[i] == '}' -> if (--depth == 0) return src.substring(start, i + 1)
            }
            i++
        }
        error("Unterminated menu literal")
    }
}
