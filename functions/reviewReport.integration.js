const test = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp: initializeAdmin, deleteApp: deleteAdmin } = require("firebase-admin/app");
const { getAuth: getAdminAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require("firebase/auth");
const { getFunctions, connectFunctionsEmulator, httpsCallable } = require("firebase/functions");

test("R4: only a reviewer can decide another owner's pending report once", async () => {
    assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, "127.0.0.1:9199");
    assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8180");

    const projectId = "demo-campus-lab3-r4";
    const admin = initializeAdmin({ projectId });
    const auth = getAdminAuth(admin);
    const db = getFirestore(admin);
    const clients = [];
    const password = "LocalR4TestOnly123!";

    async function makeCaller(name, reviewer) {
        const email = `${name}@example.com`;
        const user = await auth.createUser({ email, password });
        if (reviewer) {
            await auth.setCustomUserClaims(user.uid, { reviewer: true });
        }

        const app = initializeApp({ projectId, apiKey: "demo-key" }, name);
        clients.push(app);
        const clientAuth = getAuth(app);
        connectAuthEmulator(clientAuth, "http://127.0.0.1:9199");
        await signInWithEmailAndPassword(clientAuth, email, password);

        const functions = getFunctions(app);
        connectFunctionsEmulator(functions, "127.0.0.1", 5101);
        return { uid: user.uid, review: httpsCallable(functions, "reviewReport") };
    }

    try {
        const owner = await makeCaller("r4-owner", false);
        const reviewer = await makeCaller("r4-reviewer", true);
        const categoryRef = db.collection("categories").doc("keys");
        await categoryRef.set({ name: "Keys" });

        const reports = db.collection("lostFoundItems");
        const pending = await reports.add({
            ownerId: owner.uid,
            reviewStatus: "PENDING",
            categoryRef,
        });
        const ownReport = await reports.add({
            ownerId: reviewer.uid,
            reviewStatus: "PENDING",
            categoryRef,
        });

        const confirm = (reportId) => ({ reportId, decision: "CONFIRMED" });

        await assert.rejects(owner.review(confirm(pending.id)), {
            code: "functions/permission-denied",
        });
        await assert.rejects(reviewer.review(confirm(ownReport.id)), {
            code: "functions/permission-denied",
        });
        assert.equal((await pending.get()).get("reviewStatus"), "PENDING");
        assert.equal((await ownReport.get()).get("reviewStatus"), "PENDING");

        const result = await reviewer.review(confirm(pending.id));
        assert.equal(result.data.reviewStatus, "CONFIRMED");

        await assert.rejects(reviewer.review(confirm(pending.id)), {
            code: "functions/failed-precondition",
        });

        const saved = await pending.get();
        assert.equal(saved.get("reviewStatus"), "CONFIRMED");
        assert.equal(saved.get("reviewedBy"), reviewer.uid);

        const audit = await db.collection("reviewDecisions").get();
        assert.equal(audit.size, 1);
        assert.equal(audit.docs[0].get("reportId"), pending.id);
        assert.equal(audit.docs[0].get("reviewStatus"), "CONFIRMED");
    } finally {
        await Promise.all(clients.map((app) => deleteApp(app)));
        await deleteAdmin(admin);
    }
});
