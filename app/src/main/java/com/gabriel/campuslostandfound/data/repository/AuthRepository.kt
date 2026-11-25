package com.gabriel.campuslostandfound.data.repository

import kotlinx.coroutines.flow.Flow

internal interface AuthRepository {
    val currentUserId: String?

    fun observeUserId(): Flow<String?>
    suspend fun authenticate(email: String, password: String, register: Boolean)
    fun signOut()
}
