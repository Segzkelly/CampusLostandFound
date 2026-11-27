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
import com.gabriel.campuslostandfound.data.repository.PendingReport
import com.gabriel.campuslostandfound.data.repository.ReviewRepository
import kotlinx.coroutines.CancellationException

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

    private val reviewRepository = ReviewRepository()

    private val _isReviewer = MutableStateFlow(false)
    val isReviewer = _isReviewer.asStateFlow()

    private val _pendingReports = MutableStateFlow<List<PendingReport>>(emptyList())
    val pendingReports = _pendingReports.asStateFlow()

    private val _reviewLoading = MutableStateFlow(false)
    val reviewLoading = _reviewLoading.asStateFlow()

    private val _reviewError = MutableStateFlow<String?>(null)
    val reviewError = _reviewError.asStateFlow()

    init {
        scope.launch {
            try {
                _isReviewer.value = reviewRepository.isReviewer()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _isReviewer.value = false
            }
        }
    }

    fun loadReviewQueue() {
        if (!_isReviewer.value || _reviewLoading.value) return
        _reviewLoading.value = true
        _reviewError.value = null

        scope.launch {
            try {
                _pendingReports.value = reviewRepository.listPendingReports()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _pendingReports.value = emptyList()
                _reviewError.value = "Could not load the review queue. Please try again."
            } finally {
                _reviewLoading.value = false
            }
        }
    }

    private val _reviewSubmitting = MutableStateFlow(false)
    val reviewSubmitting = _reviewSubmitting.asStateFlow()

    private val _reviewDecisionError = MutableStateFlow<String?>(null)
    val reviewDecisionError = _reviewDecisionError.asStateFlow()

    fun clearReviewDecisionError() {
        _reviewDecisionError.value = null
    }

    fun submitReviewDecision(reportId: String, decision: String, reason: String) {
        if (!_isReviewer.value || _reviewSubmitting.value || _reviewLoading.value) return
        if (_pendingReports.value.none { it.id == reportId }) return

        val trimmedReason = reason.trim()
        if (decision != "CONFIRMED" && decision != "REJECTED") return
        if (decision == "REJECTED" && trimmedReason.length !in 1..500) return

        _reviewSubmitting.value = true
        _reviewDecisionError.value = null

        scope.launch {
            try {
                reviewRepository.submitDecision(reportId, decision, trimmedReason)
                _pendingReports.value = _pendingReports.value.filterNot { it.id == reportId }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _reviewDecisionError.value = "Could not save the decision. Please try again."
            } finally {
                _reviewSubmitting.value = false
            }
        }
    }

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
