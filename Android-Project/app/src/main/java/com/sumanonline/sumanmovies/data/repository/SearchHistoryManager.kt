package com.sumanonline.sumanmovies.data.repository

import android.content.Context
import android.content.SharedPreferences

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sumanmovies_search_prefs", Context.MODE_PRIVATE)

    fun getSearchHistory(): List<String> {
        val raw = prefs.getString("search_history", null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun addSearchQuery(query: String) {
        val q = query.trim()
        if (q.isBlank() || q.length < 2) return
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(q, ignoreCase = true) }
        current.add(0, q)
        val trimmed = current.take(12)
        prefs.edit().putString("search_history", trimmed.joinToString("|||")).apply()
    }

    fun removeSearchQuery(query: String) {
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        prefs.edit().putString("search_history", current.joinToString("|||")).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove("search_history").apply()
    }
}
