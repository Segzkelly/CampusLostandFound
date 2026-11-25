package com.gabriel.campuslostandfound.state

import com.gabriel.campuslostandfound.data.model.Category
import com.gabriel.campuslostandfound.data.model.categoryId
import com.gabriel.campuslostandfound.data.repository.LostFoundRepository
import com.gabriel.campuslostandfound.validation.ItemDraft
import com.gabriel.campuslostandfound.validation.ItemErrors
import com.gabriel.campuslostandfound.validation.validateItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ItemFormUiState(
    val isEditing: Boolean,
    val draft: ItemDraft = ItemDraft(),
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val loadError: String? = null,
    val errors: ItemErrors? = null,
    val saving: Boolean = false,
    val saveError: String? = null
) {
    val canSave: Boolean
        get() = !saving && !loading && loadError == null && categories.isNotEmpty()
}

internal class ItemFormStateHolder(
    private val repository: LostFoundRepository,
    private val itemId: String?,
    parentScope: CoroutineScope,
    private val onSaved: () -> Unit
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val _state = MutableStateFlow(ItemFormUiState(isEditing = itemId != null))
    val state = _state.asStateFlow()

    init {
        scope.launch { loadForm() }
    }

    fun changeTitle(value: String) = edit { copy(title = value) }
    fun changeDescription(value: String) = edit { copy(description = value) }
    fun changeLocation(value: String) = edit { copy(location = value) }
    fun changeDate(value: String) = edit { copy(date = value) }
    fun changeStatus(value: String) = edit { copy(status = value) }
    fun changeCategory(value: String) = edit { copy(categoryId = value) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        val errors = validateItem(current.draft, current.categoryIds())
        if (!errors.isValid) {
            _state.value = current.copy(errors = errors, saveError = null)
            return
        }

        _state.value = current.copy(errors = null, saving = true, saveError = null)
        scope.launch {
            try {
                val draft = current.draft
                if (itemId == null) {
                    repository.addItem(
                        title = draft.title.trim(),
                        description = draft.description.trim(),
                        location = draft.location.trim(),
                        date = draft.date.trim(),
                        status = draft.status,
                        categoryId = draft.categoryId
                    )
                } else {
                    repository.updateItem(
                        itemId = itemId,
                        title = draft.title.trim(),
                        description = draft.description.trim(),
                        location = draft.location.trim(),
                        date = draft.date.trim(),
                        status = draft.status,
                        categoryId = draft.categoryId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(saveError = "Could not save the item. Please try again.")
                return@launch
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
            onSaved()
        }
    }

    fun close() {
        job.cancel()
    }

    private suspend fun loadForm() {
        if (itemId != null) {
            try {
                val item = repository.getItem(itemId)
                if (item == null) {
                    _state.value = _state.value.copy(loading = false, loadError = "This item is no longer available.")
                    return
                }
                _state.value = _state.value.copy(
                    draft = ItemDraft(
                        title = item.title,
                        description = item.description,
                        location = item.location,
                        date = item.date,
                        status = item.status,
                        categoryId = item.categoryId
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, loadError = "Could not load this item.")
                return
            }
        }

        try {
            val categories = repository.getCategories()
            _state.value = _state.value.copy(loading = false, categories = categories)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = _state.value.copy(
                loading = false,
                loadError = "Could not load categories. Cancel and try again."
            )
        }
    }

    private fun edit(change: ItemDraft.() -> ItemDraft) {
        val current = _state.value
        if (current.loading || current.saving || current.loadError != null) return
        val draft = current.draft.change()
        _state.value = current.copy(
            draft = draft,
            errors = current.errors?.let { validateItem(draft, current.categoryIds()) },
            saveError = null
        )
    }

    private fun ItemFormUiState.categoryIds(): Set<String> =
        categories.map { it.id }.toSet()
}
