package com.gabriel.campuslostandfound.data.repository

import com.gabriel.campuslostandfound.data.model.Category
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreLostFoundRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : LostFoundRepository {

    override fun observeMyItems(): Flow<List<LostFoundItem>> = callbackFlow {
        val ownerId = auth.currentUser?.uid
        if (ownerId == null) {
            close(IllegalStateException("Sign in to view your items."))
            return@callbackFlow
        }

        val listener = firestore.collection("lostFoundItems")
            .whereEqualTo("ownerId", ownerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    try {
                        val items = snapshot.documents.mapNotNull { document ->
                            document.toObject(LostFoundItem::class.java)
                                ?.copy(id = document.id)
                        }
                        trySend(items)
                    } catch (exception: RuntimeException) {
                        close(exception)
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getItem(itemId: String): LostFoundItem? {
        val ownerId = checkNotNull(auth.currentUser?.uid) {
            "Sign in to edit an item."
        }
        require(itemId.isNotBlank() && '/' !in itemId) { "Invalid item ID." }
        val document = firestore.collection("lostFoundItems").document(itemId)
            .get().awaitResult()
        return document.toObject(LostFoundItem::class.java)
            ?.takeIf { it.ownerId == ownerId }
            ?.copy(id = document.id)
    }

    override suspend fun getCategories(): List<Category> {
        val snapshot = firestore.collection("categories")
            .orderBy("name")
            .get().awaitResult()

        return snapshot.documents.mapNotNull { document ->
            document.getString("name")?.let { name ->
                Category(id = document.id, name = name)
            }
        }
    }

    override suspend fun addItem(
        title: String,
        description: String,
        location: String,
        date: String,
        status: String,
        categoryId: String
    ): String {
        val ownerId = checkNotNull(auth.currentUser?.uid) {
            "Sign in to report an item."
        }
        val document = firestore.collection("lostFoundItems").document()
        val fields = editableFields(title, description, location, date, status, categoryId) +
            mapOf("ownerId" to ownerId, "createdAt" to FieldValue.serverTimestamp())

        document.set(fields).awaitResult()
        return document.id
    }

    override suspend fun updateItem(
        itemId: String,
        title: String,
        description: String,
        location: String,
        date: String,
        status: String,
        categoryId: String
    ) {
        checkNotNull(auth.currentUser?.uid) { "Sign in to edit an item." }
        require(itemId.isNotBlank() && '/' !in itemId) { "Invalid item ID." }

        firestore.collection("lostFoundItems").document(itemId)
            .update(editableFields(title, description, location, date, status, categoryId))
            .awaitResult()
    }

    override suspend fun deleteItem(itemId: String) {
        checkNotNull(auth.currentUser?.uid) { "Sign in to delete an item." }
        require(itemId.isNotBlank() && '/' !in itemId) { "Invalid item ID." }

        firestore.collection("lostFoundItems").document(itemId).delete().awaitResult()
    }

    private fun editableFields(
        title: String,
        description: String,
        location: String,
        date: String,
        status: String,
        categoryId: String
    ): Map<String, Any> = mapOf(
        "title" to title.trim(),
        "description" to description.trim(),
        "location" to location.trim(),
        "date" to date.trim(),
        "status" to status,
        "categoryRef" to firestore.collection("categories").document(categoryId),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
