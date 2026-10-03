package ru.crmod.app

import android.content.Context

object Prefs {
    private const val FILE = "crmod"
    const val DEFAULT_SERVER = "http://10.0.2.2:8000"

    private fun store(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun server(context: Context) = store(context).getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER

    fun saveServer(context: Context, server: String) {
        store(context).edit().putString("server", server.trim().trimEnd('/')).apply()
    }

    fun selectedId(context: Context) = store(context).getInt("selected", -1)

    fun select(context: Context, id: Int) {
        store(context).edit().putInt("selected", id).apply()
    }
}
