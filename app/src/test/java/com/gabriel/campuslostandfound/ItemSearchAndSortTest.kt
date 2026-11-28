package com.gabriel.campuslostandfound

import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemSearchAndSortTest {
    @Test
    fun searchMatchesTitleOrLocationIgnoringCaseAndOuterSpaces() {
        val titleMatch = LostFoundItem(id = "1", title = "Library card")
        val locationMatch = LostFoundItem(id = "2", title = "Keys", location = "Main Library")
        val unrelated = LostFoundItem(id = "3", title = "Umbrella", location = "Gym")
        val items = listOf(titleMatch, unrelated, locationMatch)

        assertEquals(listOf(titleMatch, locationMatch), searchItems(items, "  LiBrArY  "))
        assertEquals(items, searchItems(items, "   "))
        assertEquals(emptyList<LostFoundItem>(), searchItems(items, "cafeteria"))
    }

    @Test
    fun dateSortWorksInBothDirectionsAndPutsMissingDatesLast() {
        val older = LostFoundItem(id = "older", createdAt = Timestamp(100L, 0))
        val newer = LostFoundItem(id = "newer", createdAt = Timestamp(200L, 0))
        val undated = LostFoundItem(id = "undated")
        val items = listOf(undated, older, newer)

        assertEquals(listOf(newer, older, undated), sortItems(items, ItemSortOrder.NEWEST))
        assertEquals(listOf(older, newer, undated), sortItems(items, ItemSortOrder.OLDEST))
    }

    @Test
    fun titleSortIgnoresCaseAndBreaksTiesById() {
        val first = LostFoundItem(id = "a", title = "adapter")
        val second = LostFoundItem(id = "b", title = "Adapter")
        val third = LostFoundItem(id = "c", title = "Backpack")
        val items = listOf(third, second, first)

        assertEquals(listOf(first, second, third), sortItems(items, ItemSortOrder.TITLE))
    }
}
