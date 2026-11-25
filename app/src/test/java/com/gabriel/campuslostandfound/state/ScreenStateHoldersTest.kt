package com.gabriel.campuslostandfound.state

import com.gabriel.campuslostandfound.data.model.Category
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.gabriel.campuslostandfound.data.repository.LostFoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateHoldersTest {
    @Test
    fun formBlocksInvalidInputBeforeAnyWrite() = withFakeRepository { repository, scope ->
        var returnedToList = false
        val holder = ItemFormStateHolder(repository, null, scope) { returnedToList = true }
        holder.changeTitle("  Black umbrella  ")
        holder.changeLocation("Library")
        holder.changeDate("2026-11-24")
        holder.changeStatus("FOUND")
        holder.changeCategory("other")
        holder.save()

        assertNotNull(holder.state.value.errors?.description)
        assertEquals(0, repository.creates)
        assertFalse(returnedToList)

        holder.changeDescription("Folding umbrella")
        holder.save()
        assertEquals(1, repository.creates)
        assertEquals("Black umbrella", repository.createdTitle)
        assertTrue(returnedToList)
    }

    @Test
    fun editLoadsRequestedItemAndUpdatesItInsteadOfCreating() = withFakeRepository { repository, scope ->
        val holder = ItemFormStateHolder(repository, "existing", scope) {}
        assertEquals("existing", repository.readId)
        assertEquals("Black umbrella", holder.state.value.draft.title)
        holder.changeLocation("Main Library")
        holder.changeCategory("other")
        holder.save()
        assertEquals("existing", repository.updatedId)
        assertEquals(0, repository.creates)
    }

    @Test
    fun deleteCancelDoesNotWriteAndSuccessShowsNotice() = withFakeRepository { repository, scope ->
        val holder = MyItemsStateHolder(repository, scope)
        holder.startObserving()
        holder.requestDelete("existing")
        assertEquals("Black umbrella", holder.state.value.pendingDelete?.title)
        holder.cancelDelete()
        holder.confirmDelete()
        assertEquals(0, repository.deletes)
        assertEquals(1, holder.state.value.visibleItems.size)
        assertNull(holder.state.value.deleteNotice)

        holder.requestDelete("existing")
        holder.confirmDelete()
        assertEquals(1, repository.deletes)
        assertTrue(holder.state.value.visibleItems.isEmpty())
        assertEquals("Item deleted.", holder.state.value.deleteNotice)
    }

    @Test
    fun deleteFailureDoesNotShowSuccess() = withFakeRepository { repository, scope ->
        repository.failDelete = true
        val holder = MyItemsStateHolder(repository, scope)
        holder.startObserving()
        holder.requestDelete("existing")
        holder.confirmDelete()
        assertTrue(holder.state.value.deleteFailed)
        assertEquals(1, holder.state.value.visibleItems.size)
        assertNull(holder.state.value.deleteNotice)
    }

    @Test
    fun sessionStopsListListenerWhenFormOpensAndOnSignOut() = withFakeRepository { repository, scope ->
        val session = SignedInSession("owner", repository, scope)
        assertEquals(1, repository.activeListeners)
        session.addItem()
        assertEquals(0, repository.activeListeners)
        assertNotNull(session.form.value)
        session.cancelForm()
        assertEquals(1, repository.activeListeners)
        session.close()
        assertEquals(0, repository.activeListeners)
    }

    private fun withFakeRepository(test: (FakeItems, CoroutineScope) -> Unit) {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            test(FakeItems(), scope)
        } finally {
            scope.cancel()
        }
    }

    private class FakeItems : LostFoundRepository {
        private val records = MutableStateFlow(
            listOf(
                LostFoundItem(
                    id = "existing",
                    title = "Black umbrella",
                    location = "Library",
                    date = "2026-11-24"
                )
            )
        )
        var activeListeners = 0
        var creates = 0
        var createdTitle: String? = null
        var deletes = 0
        var failDelete = false
        var readId: String? = null
        var updatedId: String? = null

        override fun observeMyItems() = flow {
            activeListeners++
            try {
                emitAll(records)
            } finally {
                activeListeners--
            }
        }

        override suspend fun getItem(itemId: String): LostFoundItem? {
            readId = itemId
            return records.value.firstOrNull { it.id == itemId }
        }

        override suspend fun getCategories() = listOf(Category(id = "other", name = "Other"))

        override suspend fun addItem(
            title: String,
            description: String,
            location: String,
            date: String,
            status: String,
            categoryId: String
        ): String {
            creates++
            createdTitle = title
            return "new"
        }

        override suspend fun updateItem(
            itemId: String,
            title: String,
            description: String,
            location: String,
            date: String,
            status: String,
            categoryId: String
        ) {
            updatedId = itemId
        }

        override suspend fun deleteItem(itemId: String) {
            if (failDelete) error("Simulated delete failure.")
            deletes++
            records.value = records.value.filterNot { it.id == itemId }
        }
    }
}
