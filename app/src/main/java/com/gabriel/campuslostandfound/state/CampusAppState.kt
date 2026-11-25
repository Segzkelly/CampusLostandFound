package com.gabriel.campuslostandfound.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabriel.campuslostandfound.data.repository.AuthRepository
import com.gabriel.campuslostandfound.data.repository.LostFoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class CampusAppState(
    authRepository: AuthRepository,
    private val itemsRepository: LostFoundRepository
) : ViewModel() {
    val auth = AuthStateHolder(authRepository, viewModelScope)
    private val _session = MutableStateFlow<SignedInSession?>(null)
    val session = _session.asStateFlow()

    init {
        viewModelScope.launch {
            auth.state.map { it.userId }.distinctUntilChanged().collect { userId ->
                _session.value?.close()
                _session.value = userId?.let { SignedInSession(it, itemsRepository, viewModelScope) }
            }
        }
    }
}

// One route and one list holder per signed-in user. Form and listener jobs
// cannot outlive this session. The Activity's ViewModel retains it on rotation.
internal class SignedInSession(
    val userId: String,
    private val repository: LostFoundRepository,
    parentScope: CoroutineScope
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val _form = MutableStateFlow<ItemFormStateHolder?>(null)
    val form = _form.asStateFlow()
    val list = MyItemsStateHolder(repository, scope).also { it.startObserving() }

    fun addItem() = openForm(null)

    fun editItem(itemId: String) {
        if (list.state.value.visibleItems.none { it.id == itemId && it.canEdit }) return
        openForm(itemId)
    }

    fun cancelForm() {
        if (_form.value?.state?.value?.saving == false) closeForm()
    }

    private fun openForm(itemId: String?) {
        val listState = list.state.value
        if (_form.value != null || listState.deleting || listState.pendingDelete != null) return
        list.stopObserving()
        _form.value = ItemFormStateHolder(repository, itemId, scope, ::closeForm)
    }

    private fun closeForm() {
        _form.value?.close()
        _form.value = null
        list.startObserving()
    }

    fun close() {
        job.cancel()
    }
}
