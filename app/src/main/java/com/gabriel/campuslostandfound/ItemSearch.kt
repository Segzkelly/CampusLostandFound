package com.gabriel.campuslostandfound

import com.gabriel.campuslostandfound.data.model.LostFoundItem

internal fun searchItems(
    items: List<LostFoundItem>,
    query: String
): List<LostFoundItem> {
    val term = query.trim()
    if (term.isEmpty()) return items

    return items.filter { item ->
        item.title.contains(term, ignoreCase = true) ||
                item.location.contains(term, ignoreCase = true)
    }
}
