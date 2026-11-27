process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";

const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

const app = initializeApp({ projectId: "demo-campus-lab3" });
const auth = getAuth(app);
const db = getFirestore(app);

async function seed() {
    for (const email of ["lab3-owner@example.com", "lab3-reviewer@example.com"]) {
        try {
            await auth.getUserByEmail(email);
            console.log(`Kept existing user: ${email}`);
        } catch (error) {
            if (error.code !== "auth/user-not-found") throw error;
            await auth.createUser({ email, password: "Lab3LocalOnly!26" });
            console.log(`Created local user: ${email}`);
        }
    }

    const batch = db.batch();
    for (const [id, name] of Object.entries({
        clothing: "Clothing",
        electronics: "Electronics",
        keys: "Keys",
        other: "Other",
    })) {
        batch.set(db.collection("categories").doc(id), { name }, { merge: true });
    }
    await batch.commit();
    console.log("Seeded 4 local categories. Existing reports were not changed.");
}

seed()
    .catch((error) => {
        console.error("Seed failed:", error.message);
        process.exitCode = 1;
    })
    .finally(() => deleteApp(app));