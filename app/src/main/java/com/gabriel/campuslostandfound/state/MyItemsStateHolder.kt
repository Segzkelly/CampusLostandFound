package com.gabriel.campuslostandfound.state

import com.gabriel.campuslostandfound.ItemSortOrder
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.gabriel.campuslostandfound.data.repository.LostFoundRepository
import com.gabriel.campuslostandfound.searchItems
import com.gabriel.campuslostandfound.sortItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class ItemRow(
    val id: String,
    val title: String,
    val location: String,
    val status: String,
    val canEdit: Boolean,
    val reviewStatus: String = "PENDING",
    val reviewReason: String? = null,
)

internal data class MyItemsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val hasItems: Boolean = false,
    val visibleItems: List<ItemRow> = emptyList(),
    val sortOrder: ItemSortOrder = ItemSortOrder.NEWEST,
    val searchQuery: String = "",
    val pendingDelete: ItemRow? = null,
    val deleting: Boolean = false,
    val deleteFailed: Boolean = false,
    val deleteNotice: String? = null
)

internal class MyItemsStateHolder(
    private val repository: LostFoundRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(MyItemsUiState())
    val state = _state.asStateFlow()

    private var allItems: List<LostFoundItem>? = null
    private var observation: Job? = null

    fun startObserving() {
        if (observation?.isActive == true) return
        _state.value = _state.value.copy(isLoading = allItems == null, loadFailed = false)
        observation = scope.launch {
            try {
                repository.observeMyItems().collect { snapshot ->
                    allItems = snapshot
                    renderRows(_state.value.copy(isLoading = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoading = false, loadFailed = true)
            }
        }
    }

    fun stopObserving() {
        observation?.cancel()
        observation = null
    }

    fun search(query: String) {
        renderRows(_state.value.copy(searchQuery = query))
    }

    fun sort(order: ItemSortOrder) {
        renderRows(_state.value.copy(sortOrder = order))
    }

    fun requestDelete(itemId: String) {
        val current = _state.value
        if (current.deleting || current.pendingDelete != null) return
        val row = current.visibleItems.firstOrNull { it.id == itemId } ?: return
        _state.value = current.copy(pendingDelete = row, deleteFailed = false)
    }

    fun cancelDelete() {
        if (!_state.value.deleting) {
            _state.value = _state.value.copy(pendingDelete = null)
        }
    }

    fun confirmDelete() {
        val current = _state.value
        val row = current.pendingDelete ?: return
        if (current.deleting) return

        _state.value = current.copy(deleting = true, deleteNotice = null)
        scope.launch {
            try {
                repository.deleteItem(row.id)
                _state.value = _state.value.copy(deleteNotice = "Item deleted.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(deleteFailed = true)
            } finally {
                _state.value = _state.value.copy(deleting = false, pendingDelete = null)
            }
        }
    }

    fun consumeDeleteNotice() {
        _state.value = _state.value.copy(deleteNotice = null)
    }

    private fun renderRows(current: MyItemsUiState) {
        val items = allItems.orEmpty()
        val matches = searchItems(items, current.searchQuery)
        _state.value = current.copy(
            hasItems = items.isNotEmpty(),
            visibleItems = sortItems(matches, current.sortOrder).map { item ->
                ItemRow(
                    id = item.id,
                    title = item.title,
                    location = item.location,
                    status = item.status,
                    canEdit = item.createdAt != null,
                    reviewStatus = item.reviewStatus,
                    reviewReason = item.reviewReason,
                )
            }
        )
    }
}
