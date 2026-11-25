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
    val updatedAt: Timestamp? = null
)