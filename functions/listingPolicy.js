const DAY_MS = 24 * 60 * 60 * 1000;
const DAILY_LIMIT = 5;

function normalizeTitle(title) {
    return title.trim().replace(/\s+/g, " ").toLowerCase();
}

function decideListing({
    title,
    checkedAtMs,
    isNew,
    earlierNewReportsToday,
    previousTitleAcceptedAtMs,
}) {
    if (isNew && earlierNewReportsToday >= DAILY_LIMIT) {
        return {
            status: "REJECTED",
            reason: "You can submit at most 5 new reports per UTC day.",
        };
    }

    if (
        previousTitleAcceptedAtMs != null &&
        checkedAtMs - previousTitleAcceptedAtMs < DAY_MS
    ) {
        return {
            status: "REJECTED",
            reason: "You already used this title on another report in the last 24 hours.",
        };
    }

    return { status: "CONFIRMED", reason: null };
}

module.exports = { decideListing, normalizeTitle };
