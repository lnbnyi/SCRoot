package com.scr01.scroot

import android.content.Context

internal class RootMenuStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(RootMenu.PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): MenuDocument? = RootMenu.decode(prefs.getString(RootMenu.KEY_DOCUMENT, null))

    fun save(document: MenuDocument) {
        prefs.edit().putString(RootMenu.KEY_DOCUMENT, RootMenu.encode(document)).commit()
    }

    fun current(launchable: List<ScannedApp>): MenuDocument {
        val stored = load()
        val resolved = RootMenu.withDefaults(stored, launchable)
        if (stored == null) save(resolved)
        return resolved
    }
}
