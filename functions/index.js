const { createHash } = require("node:crypto");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { decideListing, normalizeTitle } = require("./listingPolicy");

initializeApp();
const db = getFirestore();

function key(...parts) {
    return createHash("sha256").update(JSON.stringify(parts)).digest("hex");
}

exports.reviewListing = onDocumentWritten(
    "lostFoundItems/{reportId}",
    async (event) => {
        const after = event.data?.after;
        if (!after?.exists || after.get("reviewStatus") !== "PENDING") return;

        await db.runTransaction(async (tx) => {
            const report = await tx.get(after.ref);

            // Ignore deleted, already reviewed, or superseded versions.
            if (
                !report.exists ||
                report.get("reviewStatus") !== "PENDING" ||
                !report.updateTime.isEqual(after.updateTime)
            ) {
                return;
            }

            const ownerId = report.get("ownerId");
            const title = normalizeTitle(report.get("title"));
            const createdAt = report.get("createdAt");
            const utcDay = createdAt.toDate().toISOString().slice(0, 10);
            const checkedAtMs = Date.now();

            const reportKey = key(
                ownerId, report.id, createdAt.seconds, createdAt.nanoseconds
            );
            const auditKey = key(
                reportKey, report.updateTime.seconds, report.updateTime.nanoseconds
            );

            const receiptRef = db.collection("listingReceipts").doc(reportKey);
            const dayRef = db.collection("listingDailyCounts").doc(key(ownerId, utcDay));
            const titleRef = db.collection("listingTitleHistory").doc(key(ownerId, title));
            const auditRef = db.collection("reviewDecisions").doc(auditKey);

            const [receipt, day, titleHistory] = await tx.getAll(
                receiptRef, dayRef, titleRef
            );

            const isNew = !receipt.exists;
            const count = day.get("count") ?? 0;
            const acceptedByAnotherReport =
                titleHistory.exists && titleHistory.get("reportKey") !== reportKey;

            const decision = decideListing({
                checkedAtMs,
                isNew,
                earlierNewReportsToday: count,
                previousTitleAcceptedAtMs: acceptedByAnotherReport
                    ? titleHistory.get("acceptedAtMs")
                    : null,
            });

            if (isNew) {
                tx.create(receiptRef, { reportId: report.id });
                tx.set(dayRef, { count: count + 1 });
            }

            if (decision.status === "CONFIRMED") {
                tx.set(titleRef, { reportKey, acceptedAtMs: checkedAtMs });
            }

            const result = {
                reviewStatus: decision.status,
                reviewReason: decision.reason,
                reviewedBy: "listing-policy",
                reviewedAt: FieldValue.serverTimestamp(),
            };

            tx.update(report.ref, {
                ...result,
                updatedAt: FieldValue.serverTimestamp(),
            });
            tx.create(auditRef, { reportId: report.id, ...result });
        });
    }
);
