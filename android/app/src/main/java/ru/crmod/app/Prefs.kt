package ru.crmod.app

import android.content.Context

object Prefs {
    private const val FILE = "crmod"

    private fun store(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun selectedId(context: Context) = store(context).getInt("selected", -1)

    fun select(context: Context, id: Int) {
        store(context).edit().putInt("selected", id).apply()
    }
}
