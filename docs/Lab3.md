# Lab 3 — Campus Lost and Found

New reports start as **Pending**. Reviewers can confirm reports or reject them with a reason. Owners can see the decision and rejection reason, and can edit report details without changing the review decision.

## Local setup (Windows)

Use the `sprint-3-lab3` branch with Android Studio, Android SDK Platform 36.1, JDK 21, Node.js 22, and Firebase CLI 15.37.0. Run the commands below from the repository root in PowerShell. This setup uses local emulators only; no deployment is required.

1. Install dependencies and start the emulators:

   ~~~powershell
   npm.cmd ci --prefix functions
   npm.cmd ci --prefix emulator-seed
   firebase.cmd emulators:start --project demo-campus-lab3 --only auth,firestore,functions
   ~~~

2. Leave that terminal running. In a second terminal, seed the demo accounts and categories:

   ~~~powershell
   node .\emulator-seed\seed.cjs
   ~~~

   Look for `Local reviewer claim: true`. The seed creates missing accounts without resetting existing passwords.

3. Connect an Android phone by USB, enable USB debugging, and accept the authorization prompt. With Android SDK Platform-Tools on your PATH, run:

   ~~~powershell
   adb -d reverse tcp:9099 tcp:9099
   adb -d reverse tcp:8080 tcp:8080
   adb -d reverse tcp:5001 tcp:5001
   adb -d reverse --list
   ~~~

   Run the Lab 3 debug build from Android Studio. If sign-in or the queue stops working after disconnecting the phone, reconnect it and repeat the `adb reverse` commands.

## Demo

Use `lab3-owner@example.com` and `lab3-reviewer@example.com` with the local-only password defined in `emulator-seed/seed.cjs` for freshly seeded accounts. If the reviewer was already signed in when you ran the seed, sign out and back in.

1. As the owner, submit two test reports. Both should show **Review: Pending**.
2. As the reviewer, open **Review Queue** and confirm one report. On the other, check that **Reject** is disabled with a blank reason, then enter a reason and reject it.
3. As the owner, verify both decisions and the rejection reason. Edit the rejected report’s location and verify that its review status and reason stay unchanged.

## Automated checks

~~~powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
node --test .\functions\reviewReason.test.js
firebase.cmd emulators:exec --config firebase.rules-test.json --only firestore --project demo-campus-lab3-rules "node --test functions/firestore.rules.test.js"
~~~

The rules regression test uses a separate local emulator on port 8081. GitHub Actions runs the debug build and all three test suites in **Lab 3 CI**.