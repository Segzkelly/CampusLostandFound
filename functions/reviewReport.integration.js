const test = require("node:test");
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const { setTimeout: sleep } = require("node:timers/promises");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");

async function waitForDecision(ref) {
    const deadline = Date.now() + 30_000;

    while (Date.now() < deadline) {
        const data = (await ref.get()).data();
        if (data?.reviewStatus === "CONFIRMED" || data?.reviewStatus === "REJECTED") {
            return data;
        }
        await sleep(250);
    }

    assert.fail(`No automatic decision within 30 seconds for ${ref.path}`);
}

test("R4: automatic decisions, title re-review, and concurrent duplicates", {
    timeout: 120_000,
}, async () => {
    assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8180");

    const app = initializeApp({ projectId: "demo-campus-lab3-r4" });
    const db = getFirestore(app);
    const reports = db.collection("lostFoundItems");
    const audit = db.collection("reviewDecisions");
    const owner = `test-owner-${randomUUID()}`;
    const concurrentOwner = `test-concurrent-${randomUUID()}`;

    async function submit(ownerId, title) {
        const ref = reports.doc();
        await ref.set({
            ownerId,
            title,
            reviewStatus: "PENDING",
            createdAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
        });
        return ref;
    }

    async function auditStatuses(ref) {
        const snapshot = await audit.where("reportId", "==", ref.id).get();
        return snapshot.docs.map((doc) => doc.get("reviewStatus")).sort();
    }

    try {
        const first = await submit(owner, "Library Keys");
        const confirmed = await waitForDecision(first);
        assert.equal(confirmed.reviewStatus, "CONFIRMED");
        assert.equal(confirmed.reviewReason, null);

        const duplicate = await submit(owner, "  LIBRARY   keys  ");
        const rejected = await waitForDecision(duplicate);
        assert.equal(rejected.reviewStatus, "REJECTED");
        assert.match(rejected.reviewReason, /last 24 hours/);

        await duplicate.update({
            title: "Keys near the front desk",
            reviewStatus: "PENDING",
            reviewReason: FieldValue.delete(),
            reviewedBy: FieldValue.delete(),
            reviewedAt: FieldValue.delete(),
            updatedAt: FieldValue.serverTimestamp(),
        });
        assert.equal((await waitForDecision(duplicate)).reviewStatus, "CONFIRMED");

        const pair = await Promise.all([
            submit(concurrentOwner, "Blue Umbrella"),
            submit(concurrentOwner, "blue umbrella"),
        ]);
        const decisions = await Promise.all(pair.map(waitForDecision));
        assert.deepEqual(
            decisions.map((result) => result.reviewStatus).sort(),
            ["CONFIRMED", "REJECTED"]
        );

        assert.deepEqual(await auditStatuses(first), ["CONFIRMED"]);
        assert.deepEqual(await auditStatuses(duplicate), ["CONFIRMED", "REJECTED"]);
        for (const [index, ref] of pair.entries()) {
            assert.deepEqual(await auditStatuses(ref), [decisions[index].reviewStatus]);
        }
    } finally {
        await db.terminate();
        await deleteApp(app);
    }
});