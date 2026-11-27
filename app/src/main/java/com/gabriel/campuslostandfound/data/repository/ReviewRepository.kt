package com.gabriel.campuslostandfound.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

internal data class PendingReport(
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val date: String,
    val status: String
)

internal class ReviewRepository {
    suspend fun isReviewer(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return user.getIdToken(true).awaitResult().claims["reviewer"] == true
    }

    suspend fun listPendingReports(): List<PendingReport> {
        val result = FirebaseFunctions.getInstance()
            .getHttpsCallable("listPendingReports")
            .call()
            .awaitResult()

        val response = result.data as? Map<*, *> ?: error("Invalid review queue response.")
        val reports = response["reports"] as? List<*> ?: error("Missing review queue.")

        return reports.map { entry ->
            val report = entry as? Map<*, *> ?: error("Invalid report.")
            PendingReport(
                id = report["id"] as? String ?: error("Missing report ID."),
                title = report["title"] as? String ?: "",
                description = report["description"] as? String ?: "",
                location = report["location"] as? String ?: "",
                date = report["date"] as? String ?: "",
                status = report["status"] as? String ?: ""
            )
        }
    }

    suspend fun submitDecision(reportId: String, decision: String, reason: String = "") {
        FirebaseFunctions.getInstance()
            .getHttpsCallable("reviewReport")
            .call(mapOf("reportId" to reportId, "decision" to decision, "reason" to reason))
            .awaitResult()
    }
}