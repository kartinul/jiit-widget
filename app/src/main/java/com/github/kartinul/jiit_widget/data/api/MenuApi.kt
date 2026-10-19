package com.github.kartinul.jiit_widget.data.api

import com.github.kartinul.jiit_widget.data.MenuItem

/** A menu source. Downloads through [fetch]'s `get` (url -> body) and returns items keyed by ISO date. */
interface MenuApi {
    fun fetch(get: (String) -> String): Map<String, MenuItem>
}
