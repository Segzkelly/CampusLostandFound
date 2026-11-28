# Campus Lost and Found

An Android app for reporting lost and found items. Sign in to add, view, edit,
search, sort, or delete your own reports.

## Tech stack

| Component | Technology |
| --- | --- |
| App framework | Kotlin and Jetpack Compose for Android |
| Backend | Firebase on the Spark plan |
| Database | Cloud Firestore, EU region `europe-central2` |
| Authentication | Firebase Authentication (email and password) |
| Test tools | JUnit via Gradle, Firebase Local Emulator Suite, and Firestore Rules Playground |

## Firestore data model

| Collection | Field | Type | Set by | Purpose |
| --- | --- | --- | --- | --- |
| `categories` | `name` | String | Maintainer | Category name shown in the picker |
| `lostFoundItems` | `title` | String | User | Short item name |
| `lostFoundItems` | `description` | String | User | Item details; the form requires these for `FOUND` |
| `lostFoundItems` | `location` | String | User | Where the item was lost or found |
| `lostFoundItems` | `date` | String (`YYYY-MM-DD`) | User | Date text used by the form |
| `lostFoundItems` | `incidentDate` | Timestamp | App, from `date` | Same calendar day stored at midnight UTC |
| `lostFoundItems` | `status` | String | User | `LOST`, `FOUND`, or `RESOLVED` |
| `lostFoundItems` | `categoryRef` | Document reference | App, from the user's selection | Reference to a `categories` document |
| `lostFoundItems` | `ownerId` | String | App, from the authenticated session | Firebase UID; set on create and never editable |
| `lostFoundItems` | `createdAt` | Timestamp | Firestore server | Set on create and never editable |
| `lostFoundItems` | `updatedAt` | Timestamp | Firestore server | Set on create and refreshed on each edit |

The app maps Firestore documents to `LostFoundItem` and `Category` and uses
document IDs as model IDs. IDs are not stored as extra fields. Signed-in users
can read categories but cannot change them from the app.

New and edited reports write both date fields. Older reports may lack
`incidentDate`; the app can still open them using `date`. Saving a valid edit
adds the timestamp without changing `ownerId` or `createdAt`.

## Repository and rules

`LostFoundRepository` provides the live item list, item and category reads,
and create, edit, and delete operations. The Firestore repository listens
only to records matching the signed-in owner's UID and removes the listener
when observation stops. Screen state holders call repository interfaces.
Form validation and the search and sort logic are testable separately from UI.

`firestore.rules` enforces owner-only access to reports, validates writes,
and protects read-only reference data. The rules require a matching native
timestamp on every new report or edit. Verify the app and rules together in
the emulator before rolling them out; builds without the new field can still
read their reports, but cannot save once the updated rules are active.

Run Android unit tests and build the debug APK on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Definition of Done

- Reviewed and approved pull request merged into `main`.
- Verified on a second team member's device.
- Invalid input is handled without crashing.
- The corresponding Sprint 2 issue is closed.

## How to run Lab 3 locally

Follow the [Lab 3 local setup guide](docs/Lab3.md) to start the Firebase emulators, seed the demo accounts and categories, and run the Android debug build on a USB-connected phone.
