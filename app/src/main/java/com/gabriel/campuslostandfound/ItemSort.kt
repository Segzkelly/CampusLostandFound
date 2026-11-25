package com.gabriel.campuslostandfound

import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.google.firebase.Timestamp

internal enum class ItemSortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    TITLE("Title (A–Z)")
}

internal fun sortItems(
    items: List<LostFoundItem>,
    order: ItemSortOrder
): List<LostFoundItem> {
    val comparator = when (order) {
        ItemSortOrder.NEWEST ->
            compareBy<LostFoundItem, Timestamp?>(nullsLast(reverseOrder<Timestamp>())) {
                it.createdAt
            }

        ItemSortOrder.OLDEST ->
            compareBy<LostFoundItem, Timestamp?>(nullsLast(naturalOrder<Timestamp>())) {
                it.createdAt
            }

        ItemSortOrder.TITLE ->
            compareBy<LostFoundItem, String>(String.CASE_INSENSITIVE_ORDER) {
                it.title
            }
    }

    return items.sortedWith(comparator.thenBy { it.id })
}

