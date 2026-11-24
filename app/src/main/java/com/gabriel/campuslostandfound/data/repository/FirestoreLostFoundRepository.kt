package com.gabriel.campuslostandfound.data.repository

import com.gabriel.campuslostandfound.data.model.Category
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

    override suspend fun getCategories(): List<Category> =
        suspendCoroutine { continuation ->
            firestore.collection("categories")
                .orderBy("name")
                .get()
                .addOnSuccessListener { snapshot ->
                    val categories = snapshot.documents.mapNotNull { document ->
                        document.getString("name")?.let { name ->
                            Category(id = document.id, name = name)
                        }
                    }
                    continuation.resume(categories)
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
                .addOnCanceledListener {
                    continuation.resumeWithException(
                        java.util.concurrent.CancellationException("Category request canceled.")
                    )
                }
        }
}

