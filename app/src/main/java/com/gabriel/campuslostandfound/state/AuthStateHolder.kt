package com.gabriel.campuslostandfound.state

import com.gabriel.campuslostandfound.data.repository.AuthRepository
import com.gabriel.campuslostandfound.validation.validateCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class AuthUiState(
    val userId: String? = null,
    val email: String = "",
    val password: String = "",
    val registering: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

internal class AuthStateHolder(
    private val repository: AuthRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AuthUiState(userId = repository.currentUserId))
    val state = _state.asStateFlow()

    init {
        scope.launch { repository.observeUserId().collect { updateSession(it) } }
    }

    fun changeEmail(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun changePassword(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun toggleRegistration() {
        val current = _state.value
        if (!current.loading) {
            _state.value = current.copy(registering = !current.registering, error = null)
        }
    }

    fun submit() {
        val current = _state.value
        if (current.loading || current.userId != null) return

        val email = current.email.trim()
        val error = validateCredentials(email, current.password, current.registering)
        if (error != null) {
            _state.value = current.copy(error = error)
            return
        }

        _state.value = current.copy(loading = true, error = null)
        scope.launch {
            try {
                repository.authenticate(email, current.password, current.registering)
                updateSession(repository.currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_state.value.userId == null) {
                    _state.value = _state.value.copy(
                        error = if (current.registering) "Could not create the account. Check your details and try again."
                        else "Could not sign in. Check your email and password and try again."
                    )
                }
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun signOut() {
        try {
            repository.signOut()
            updateSession(repository.currentUserId)
        } catch (_: Exception) {
            _state.value = _state.value.copy(notice = "Could not sign out. Please try again.")
        }
    }

    fun consumeNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    private fun updateSession(userId: String?) {
        val current = _state.value
        if (current.userId != userId) {
            // Drop the old screen state, especially the password, on a session change.
            _state.value = AuthUiState(
                userId = userId,
                notice = "Welcome!".takeIf { userId != null && current.loading }
            )
        }
    }
}
