const test = require("node:test");
const assert = require("node:assert/strict");
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } =
    require("firebase/firestore");

test("owner can edit a rejected report without changing the review decision", async () => {
    assert.equal(
        process.env.FIRESTORE_EMULATOR_HOST,
        "127.0.0.1:8081",
        "Run this test with the dedicated rules-test emulator."
    );

    const env = await initializeTestEnvironment({
        projectId: "demo-campus-lab3-rules",
    });

    try {
        const ownerId = "test-owner";
        const reason = "Please provide a more specific location";
        const reportPath = "lostFoundItems/reviewed-test";
        const date = Timestamp.fromDate(new Date("2026-11-27T00:00:00Z"));

        // Seed a reviewed report, then perform the edit as the owner.
        await env.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            const categoryRef = doc(db, "categories", "keys");

            await setDoc(categoryRef, { name: "Keys" });
            await setDoc(doc(db, reportPath), {
                title: "Regression test report",
                description: "A set of keys",
                location: "Behind the chair",
                date: "2026-11-27",
                incidentDate: date,
                status: "LOST",
                categoryRef,
                ownerId,
                createdAt: date,
                updatedAt: date,
                reviewStatus: "REJECTED",
                reviewReason: reason,
                reviewedBy: "test-reviewer",
                reviewedAt: date,
            });
        });

        const ownerDb = env.authenticatedContext(ownerId).firestore();
        const reportRef = doc(ownerDb, reportPath);

        await updateDoc(reportRef, {
            location: "Main Library, behind the chair",
            updatedAt: serverTimestamp(),
        });

        const saved = (await getDoc(reportRef)).data();
        assert.equal(saved.location, "Main Library, behind the chair");
        assert.equal(saved.reviewStatus, "REJECTED");
        assert.equal(saved.reviewReason, reason);
        assert.equal(saved.reviewedBy, "test-reviewer");
        assert.ok(saved.reviewedAt.isEqual(date));
    } finally {
        await env.cleanup();
    }
});