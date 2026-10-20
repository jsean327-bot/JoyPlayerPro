package com.joyplayer.iptv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedList(
    val id: String,
    val name: String,
    val type: String,
    val url: String = "",
    val server: String = "",
    val user: String = "",
    val pass: String = ""
)

class ListStore(context: Context) {
    private val prefs = context.getSharedPreferences("joyplayer", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): List<SavedList> {
        val json = prefs.getString("lists", null) ?: return emptyList()
        return try {
            gson.fromJson<List<SavedList>>(json, object : TypeToken<List<SavedList>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(lists: List<SavedList>) {
        prefs.edit().putString("lists", gson.toJson(lists)).apply()
    }
}
