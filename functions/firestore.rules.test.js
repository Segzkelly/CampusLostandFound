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

const { assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");

async function withRulesFixture(run) {
    assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8081");
    const env = await initializeTestEnvironment({
        projectId: "demo-campus-lab3-rules",
    });

    try {
        const ownerId = "r5-test-owner";
        await env.withSecurityRulesDisabled(async (context) => {
            await setDoc(doc(context.firestore(), "categories", "keys"), {
                name: "Keys",
            });
        });

        const db = env.authenticatedContext(ownerId).firestore();
        const payload = {
            title: "R5 test report",
            description: "A set of keys",
            location: "Main Library",
            date: "2026-11-27",
            incidentDate: Timestamp.fromDate(new Date("2026-11-27T00:00:00Z")),
            status: "LOST",
            categoryRef: doc(db, "categories", "keys"),
            ownerId,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
            reviewStatus: "PENDING",
        };

        await run({ env, db, payload });
    } finally {
        await env.cleanup();
    }
}

test("R5 #9: owner cannot create a report as confirmed", async () => {
    await withRulesFixture(async ({ db, payload }) => {
        const ref = doc(db, "lostFoundItems", "r5-create");
        await assertFails(setDoc(ref, { ...payload, reviewStatus: "CONFIRMED" }));
        await assertSucceeds(setDoc(ref, payload));
    });
});

test("R5 #10: owner cannot directly confirm a pending report", async () => {
    await withRulesFixture(async ({ db, payload }) => {
        const ref = doc(db, "lostFoundItems", "r5-transition");
        await assertSucceeds(setDoc(ref, payload));
        await assertFails(updateDoc(ref, {
            reviewStatus: "CONFIRMED",
            updatedAt: serverTimestamp(),
        }));
        assert.equal((await getDoc(ref)).data().reviewStatus, "PENDING");
    });
});

test("R5 #11: owner cannot change a confirmed report's location", async () => {
    await withRulesFixture(async ({ env, db, payload }) => {
        const ref = doc(db, "lostFoundItems", "r5-confirmed-edit");
        await assertSucceeds(setDoc(ref, payload));

        await env.withSecurityRulesDisabled(async (context) => {
            await updateDoc(doc(context.firestore(), ref.path), {
                reviewStatus: "CONFIRMED",
            });
        });

        // Leave the review status unchanged so this tests the location lock.
        await assertFails(updateDoc(ref, {
            location: "Student Center",
            updatedAt: serverTimestamp(),
        }));
        await assertSucceeds(updateDoc(ref, {
            status: "RESOLVED",
            updatedAt: serverTimestamp(),
        }));

        const saved = (await getDoc(ref)).data();
        assert.equal(saved.location, "Main Library");
        assert.equal(saved.reviewStatus, "CONFIRMED");
        assert.equal(saved.status, "RESOLVED");
    });
});
