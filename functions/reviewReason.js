const { HttpsError } = require("firebase-functions/v2/https");

function validateReviewReason(decision, reason) {
    if (decision !== "REJECTED") return null;

    const cleaned = typeof reason === "string" ? reason.trim() : "";
    if (cleaned.length < 1 || cleaned.length > 500) {
        throw new HttpsError("invalid-argument", "Enter a reason of 1-500 characters.");
    }
    return cleaned;
}

module.exports = { validateReviewReason };