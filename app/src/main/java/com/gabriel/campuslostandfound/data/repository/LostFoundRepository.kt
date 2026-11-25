package com.gabriel.campuslostandfound.data.repository

import com.gabriel.campuslostandfound.data.model.Category
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import kotlinx.coroutines.flow.Flow

interface LostFoundRepository {
    fun observeMyItems(): Flow<List<LostFoundItem>>

    suspend fun getItem(itemId: String): LostFoundItem?

    suspend fun getCategories(): List<Category>

    suspend fun addItem(
        title: String,
        description: String,
        location: String,
        date: String,
        status: String,
        categoryId: String
    ): String

    suspend fun updateItem(
        itemId: String,
        title: String,
        description: String,
        location: String,
        date: String,
        status: String,
        categoryId: String
    )

    suspend fun deleteItem(itemId: String)
}
