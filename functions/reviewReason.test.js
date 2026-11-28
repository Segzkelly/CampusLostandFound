const test = require("node:test");
const assert = require("node:assert/strict");
const { validateReviewReason } = require("./reviewReason");

test("rejecting a report requires a nonblank reason", () => {
    for (const reason of [undefined, "", " \t "]) {
        assert.throws(
            () => validateReviewReason("REJECTED", reason),
            { code: "invalid-argument" }
        );
    }
});

test("rejection trims the reason and accepts exactly 500 characters", () => {
    const limit = "a".repeat(500);
    assert.equal(validateReviewReason("REJECTED", ` ${limit} `), limit);
});

test("rejection refuses reasons longer than 500 characters", () => {
    assert.throws(
        () => validateReviewReason("REJECTED", "a".repeat(501)),
        { code: "invalid-argument" }
    );
});

test("confirmation does not store a reason", () => {
    assert.equal(validateReviewReason("CONFIRMED", "Not needed"), null);
});