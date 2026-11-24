package com.gabriel.campuslostandfound.data.repository

import com.gabriel.campuslostandfound.data.model.Category
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import kotlinx.coroutines.flow.Flow

interface LostFoundRepository {
    fun observeMyItems(): Flow<List<LostFoundItem>>
    suspend fun getCategories(): List<Category>
}