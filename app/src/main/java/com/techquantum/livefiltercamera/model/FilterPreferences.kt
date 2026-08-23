package com.techquantum.livefiltercamera.model

import android.content.Context
import android.content.SharedPreferences

class FilterPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filter_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITES = "favorite_filter_ids"
        private const val KEY_RECENTS = "recent_filter_ids"
        private const val MAX_RECENTS = 20
    }

    fun getFavoriteFilterIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun saveFavoriteFilterIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, ids).apply()
    }

    fun toggleFavorite(filterId: String): Set<String> {
        val current = getFavoriteFilterIds().toMutableSet()
        if (current.contains(filterId)) {
            current.remove(filterId)
        } else {
            current.add(filterId)
        }
        saveFavoriteFilterIds(current)
        return current
    }

    fun getRecentFilterIds(): List<String> {
        val raw = prefs.getString(KEY_RECENTS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun addRecentFilterId(filterId: String): List<String> {
        if (filterId.isBlank() || filterId == "normal") {
            return getRecentFilterIds()
        }
        val current = getRecentFilterIds().toMutableList()
        current.remove(filterId)
        current.add(0, filterId)
        if (current.size > MAX_RECENTS) {
            current.subList(MAX_RECENTS, current.size).clear()
        }
        prefs.edit().putString(KEY_RECENTS, current.joinToString(",")).apply()
        return current
    }
}
