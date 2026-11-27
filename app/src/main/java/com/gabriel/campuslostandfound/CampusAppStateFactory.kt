package com.gabriel.campuslostandfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gabriel.campuslostandfound.data.repository.FirebaseAuthRepository
import com.gabriel.campuslostandfound.data.repository.FirestoreLostFoundRepository
import com.gabriel.campuslostandfound.state.CampusAppState
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

internal object CampusAppStateFactory : ViewModelProvider.Factory {
    private val emulatorSetup: Unit by lazy {
        if (BuildConfig.DEBUG) {
            check(FirebaseApp.getInstance().options.projectId == "demo-campus-lab3") {
                "Lab 3 debug must use demo-campus-lab3."
            }
            FirebaseAuth.getInstance().useEmulator("127.0.0.1", 9099)
            FirebaseFirestore.getInstance().useEmulator("127.0.0.1", 8080)
        }
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == CampusAppState::class.java)
        emulatorSetup
        @Suppress("UNCHECKED_CAST")
        return CampusAppState(FirebaseAuthRepository(), FirestoreLostFoundRepository()) as T
    }
}
