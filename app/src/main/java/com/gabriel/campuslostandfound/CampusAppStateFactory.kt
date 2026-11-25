package com.gabriel.campuslostandfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gabriel.campuslostandfound.data.repository.FirebaseAuthRepository
import com.gabriel.campuslostandfound.data.repository.FirestoreLostFoundRepository
import com.gabriel.campuslostandfound.state.CampusAppState

internal object CampusAppStateFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == CampusAppState::class.java)
        @Suppress("UNCHECKED_CAST")
        return CampusAppState(FirebaseAuthRepository(), FirestoreLostFoundRepository()) as T
    }
}
