const test = require("node:test");
const assert = require("node:assert/strict");
const { decideListing, normalizeTitle } = require("./listingPolicy");

const dayMs = 24 * 60 * 60 * 1000;
const checkedAtMs = Date.parse("2026-11-28T12:00:00Z");

function decide(overrides = {}) {
    return decideListing({
        checkedAtMs,
        isNew: true,
        earlierNewReportsToday: 0,
        previousTitleAcceptedAtMs: null,
        ...overrides,
    });
}

test("the fifth new report is confirmed and the sixth is rejected", () => {
    assert.equal(decide({ earlierNewReportsToday: 4 }).status, "CONFIRMED");
    assert.equal(decide({ earlierNewReportsToday: 5 }).status, "REJECTED");
});

test("duplicate titles ignore case and extra whitespace", () => {
    assert.equal(normalizeTitle("  Library   KEYS  "), normalizeTitle("library keys"));
});

test("a duplicate is rejected just before 24 hours and allowed at 24 hours", () => {
    const justBefore = decide({ previousTitleAcceptedAtMs: checkedAtMs - dayMs + 1 });
    assert.equal(justBefore.status, "REJECTED");
    assert.match(justBefore.reason, /last 24 hours/);
    assert.equal(
        decide({ previousTitleAcceptedAtMs: checkedAtMs - dayMs }).status,
        "CONFIRMED"
    );
});

test("a title edit still checks for duplicates but does not use the daily quota", () => {
    const edit = { isNew: false, earlierNewReportsToday: 5 };
    assert.equal(decide(edit).status, "CONFIRMED");
    assert.equal(
        decide({ ...edit, previousTitleAcceptedAtMs: checkedAtMs - 1000 }).status,
        "REJECTED"
    );
});
