package com.gabriel.campuslostandfound.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference

data class LostFoundItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val date: String = "",
    val status: String = "LOST",
    val categoryRef: DocumentReference? = null,
    val ownerId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val incidentDate: Timestamp? = null,
    val reviewStatus: String = "PENDING"
)

// Used by the form state holder. Not a field in the Firestore document.
internal val LostFoundItem.categoryId: String
    get() = categoryRef?.id.orEmpty()
