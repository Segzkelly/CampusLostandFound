const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { validateReviewReason } = require("./reviewReason");

initializeApp();
const db = getFirestore();
const categoryPaths = new Set([
    "categories/clothing",
    "categories/electronics",
    "categories/keys",
    "categories/other",
]);

exports.reviewReport = onCall(async (request) => {
    if (!request.auth) {
        throw new HttpsError("unauthenticated", "Sign in to review reports.");
    }
    if (request.auth.token.reviewer !== true) {
        throw new HttpsError("permission-denied", "Reviewer access is required.");
    }

    const { reportId, decision, reason } = request.data ?? {};
    if (typeof reportId !== "string" || !/^[A-Za-z0-9]{20}$/.test(reportId)) {
        throw new HttpsError("invalid-argument", "Invalid report ID.");
    }
    if (decision !== "CONFIRMED" && decision !== "REJECTED") {
        throw new HttpsError("invalid-argument", "Choose Confirm or Reject.");
    }

        const reviewReason = validateReviewReason(decision, reason);

    const reportRef = db.collection("lostFoundItems").doc(reportId);
    const logRef = db.collection("reviewDecisions").doc();

    await db.runTransaction(async (tx) => {
        const report = await tx.get(reportRef);
        if (!report.exists) {
            throw new HttpsError("not-found", "Report not found.");
        }
        if (report.get("ownerId") === request.auth.uid) {
            throw new HttpsError("permission-denied", "You cannot review your own report.");
        }
        if (report.get("reviewStatus") !== "PENDING") {
            throw new HttpsError("failed-precondition", "Only pending reports can be reviewed.");
        }

        if (decision === "CONFIRMED") {
            const path = report.get("categoryRef")?.path;
            if (!categoryPaths.has(path) || !(await tx.get(db.doc(path))).exists) {
                throw new HttpsError("failed-precondition", "The report needs a valid category.");
            }
        }

        const result = {
            reviewStatus: decision,
            reviewReason: decision === "REJECTED" ? reviewReason : null,
            reviewedBy: request.auth.uid,
            reviewedAt: FieldValue.serverTimestamp(),
        };

        tx.update(reportRef, { ...result, updatedAt: FieldValue.serverTimestamp() });
        tx.set(logRef, { reportId, ...result });
    });

    return { reportId, reviewStatus: decision };
});

exports.listPendingReports = onCall(async (request) => {
    if (!request.auth) {
        throw new HttpsError("unauthenticated", "Sign in to view the review queue.");
    }
    if (request.auth.token.reviewer !== true) {
        throw new HttpsError("permission-denied", "Reviewer access is required.");
    }

    const snapshot = await db.collection("lostFoundItems")
        .where("reviewStatus", "==", "PENDING")
        .limit(50)
        .get();

    return {
        reports: snapshot.docs
            .filter((doc) => doc.get("ownerId") !== request.auth.uid)
            .map((doc) => ({
                id: doc.id,
                title: doc.get("title"),
                description: doc.get("description"),
                location: doc.get("location"),
                date: doc.get("date"),
                status: doc.get("status"),
            })),
    };
});