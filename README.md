# Campus Lost and Found

An Android app built with Kotlin, Jetpack Compose, Firebase Authentication,
and Cloud Firestore.

## Firestore schema

### `categories/{categoryId}`

Reference collection for item categories. Each document has a `name` (string).
The app uses the Firestore document ID as the category's `id`.

### `lostFoundItems/{itemId}`

| Field | Type | Purpose |
| --- | --- | --- |
| `title` | String | Short item name |
| `description` | String | Details about the item |
| `location` | String | Where the item was lost or found |
| `date` | String | Date the item was lost or found (`YYYY-MM-DD`) |
| `status` | String | `LOST`, `FOUND`, or `RESOLVED` |
| `categoryRef` | Document reference | Points to a document in `categories` |
| `ownerId` | String | Firebase Authentication UID of the owner |
| `createdAt` | Timestamp | Intended server timestamp for creation |
| `updatedAt` | Timestamp | Intended server timestamp for the latest change |

The app uses the Firestore document ID as the item's `id`; it does not
need a separate `id` field in Firestore.

## Repository

`LostFoundRepository` defines the read operations. Its Firestore
implementation loads categories by name and listens for items matching
the signed-in user's UID. The write operations and runtime verification
are still pending.
