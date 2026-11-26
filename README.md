# GrihaLedger

A mobile-first family expense tracker and tool-grounded financial advisor built in native Kotlin and Jetpack Compose.

- **Simple household UX**: Log an expense or income in ~10 seconds with a custom numeric keypad, keyword category suggestions, funding-source pills, and live shared updates across every family member.
- **Ledger truth first**: All money is stored and summed in integer **paise (`Long`)** with `en-IN` formatting. Voided entries never alter totals and every change records an append-only audit event.
- **Tool-grounded AI Advisor**: Powered by Gemini via **Firebase AI Logic** with function calling over the user's already-loaded, already-authorized ledger. Gemini computes nothing itself and answers strictly using verified results (**Finding · Evidence · Action**).

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| UI & Navigation | Jetpack Compose, Material 3, Adaptive `NavigationBar` / `NavigationRail`, Type-safe Navigation Compose (`@Serializable`) |
| Architecture | Single `:app` module, MVVM + `StateFlow`, Hilt (KSP), Coroutines / `Flow`, `java.time` |
| Auth & Database | Firebase Auth (Credential Manager Google Sign-In), Cloud Firestore (offline persistence enabled, named database) |
| AI Engine | Firebase AI Logic (`Firebase.ai(GenerativeBackend.googleAI())`) with Gemini tool/function calling (no API key shipped in app) |
| Security & Quality | Zero-trust `firestore.rules`, Firebase App Check (Play Integrity in release, Debug token in debug), Crashlytics, R8 minification |
| Testing | JUnit4, Turbine, Compose UI + Robolectric, Firebase Local Emulator + `node --test` |

---

## Architecture Diagram

```text
+-----------------------------------------------------------------------+
|                              ui/ (Compose + ViewModels)               |
|  Home | Records + Detail | Insights (Charts) | Advisor | Family       |
|  AddTransactionSheet (Keypad, Categories, Funding, Paid By, Undo)     |
+-----------------------------------+-----------------------------------+
                                    | StateFlow<UiState> / Intents
                                    v
+-----------------------------------------------------------------------+
|                        domain/ (PURE Kotlin - Zero Android/Firebase)  |
|  model/      Money (Long paise), Transaction, Member, Role, Category  |
|  rules/      Permissions matrix, TransactionValidation, InviteRules   |
|  analytics/  LedgerAnalytics (summarize, breakdown, compare, forecast)|
|  repository/ Session, Auth, Family, Invite, Ledger, AiAdvisor         |
+-----------------------------------+-----------------------------------+
                                    ^
                                    | Hilt DI (@Binds RepositoryModule)
        +---------------------------+---------------------------+
        |                                                       |
        v                                                       v
+-------------------------------+       +-------------------------------+
|       data/firebase/          |       |          data/ai/             |
| Firestore + Offline Cache     |       | GeminiAiAdvisorRepository     |
| Batch Writes + Audit Events   |       | Domain Analytics Tool Engine  |
| CredentialManager Auth        |       | Prompt-Injection Guards       |
+-------------------------------+       +-------------------------------+
        ^
        | (Swappable in-memory fakes for tests/previews)
+-------------------------------+
|         data/fake/            |
| Seeded family, 3 roles, ~30tx |
+-------------------------------+
```

---

## Setup Instructions

### 1. Prerequisites
- Android Studio (Koala / Ladybug or newer) with JDK 11+ and Android SDK 36 (`minSdk = 26`, `targetSdk = 36`)
- Node.js 18+ and Firebase CLI (`firebase-tools`) for running Firestore Rules emulator tests

### 2. Firebase Configuration
1. Place your downloaded `google-services.json` into `app/google-services.json`.
2. In the **Firebase Console** (`Project Settings -> Your apps -> Android`, package `com.aistudio.grihaledger.fkmzqr`):
   - Register your **debug SHA-1** and **release/upload-key SHA-1 & SHA-256** fingerprints (required for Google Sign-In via Credential Manager and Play Integrity App Check).
   - Enable **Google** as a sign-in provider in Firebase Authentication.
   - Ensure the **Default Web Client ID** in `app/src/main/res/values/strings.xml` (`default_web_client_id`) matches the OAuth 2.0 Web Client generated in `google-services.json`.
3. **Named Firestore Database**:
   - GrihaLedger targets the named Firestore database configured in `app/src/main/res/values/strings.xml` (`firestore_database_id`).
   - Deploy rules and indexes:
     ```bash
     firebase deploy --only firestore
     ```
4. **Firebase App Check**:
   - **Release builds** automatically install `PlayIntegrityAppCheckProviderFactory`. Register your Play App Signing / release SHA-256 in **Firebase Console -> App Check -> Play Integrity**.
   - **Debug builds** install `DebugAppCheckProviderFactory` (supports launching with `--es FIREBASE_APPCHECK_DEBUG_TOKEN "<token>"` or copying the generated debug token from Logcat into Firebase Console -> App Check -> Manage debug tokens).
5. **Firebase AI Logic**:
   - Enable **Vertex AI / Gemini Developer API (Firebase AI Logic)** in Firebase Console for the project so `Firebase.ai(backend = GenerativeBackend.googleAI())` succeeds without embedding an API key in the client.
6. **Crashlytics**:
   - Crashlytics collection is disabled in debug builds (`!BuildConfig.DEBUG`) and active in release builds, with R8 mapping files uploaded automatically when assembling release builds with a valid `google-services.json`.

### 3. Secrets & Release Signing (No `local.properties` / No Committed Keystores)
- Never commit `.jks`, `.keystore`, `keystore.properties`, or `local.properties`.
- `app/build.gradle.kts` reads release signing credentials from environment variables:
  - `KEYSTORE_PATH` (defaults to `${rootDir}/my-upload-key.jks`)
  - `STORE_PASSWORD`
  - `KEY_PASSWORD`
  - Key alias: `upload`
- `.env.example` / `.env` are handled by the Secrets Gradle Plugin for non-Firebase environment variables if needed (no Gemini API key is required or used in client code).

---

## Security Rules & Role Model (`firestore.rules`)

`firestore.rules` follows a **deny-by-default** architecture (`match /{document=**} { allow read, write: if false; }`) with no overlapping broad `allow` statements.

### Role Permission Matrix

| Capability | Admin | Member | Viewer | Outsider / Signed-out |
| --- | :---: | :---: | :---: | :---: |
| Read family, members, categories, transactions, auditEvents | Yes | Yes | Yes | **Denied** |
| Create transaction (+ `created` audit event in same batch) | Yes | Yes | **Denied** | **Denied** |
| Edit / Void **own** active transaction (+ audit event in same batch) | Yes | Yes | **Denied** | **Denied** |
| Edit / Void **another member's** active transaction | Yes | **Denied** | **Denied** | **Denied** |
| Hard-delete transactions or modify/delete `auditEvents` | **Denied** | **Denied** | **Denied** | **Denied** |
| Create / List / Revoke 48h single-use `invites` | Yes | **Denied** | **Denied** | **Denied** |
| Manage categories (`create` / `update` metadata / `delete`) | Yes | **Denied** | **Denied** | **Denied** |
| Change member roles / remove members (protecting last admin) | Yes | **Denied** | **Denied** | **Denied** |

### Key Enforcement Guarantees
- **One Family Membership**: Users can only read/write their own `users/{uid}` profile (`allow list: if false`) and cannot unilaterally assign `familyId` unless a matching `members/{uid}` document is created in the same atomic batch.
- **No Privilege Escalation via Invites**: Invite creation only permits `member` or `viewer` roles with a max 49-hour window; joining atomically creates `members/{uid}` and marks `invites/{code}` `status == 'used'` in a single batch (`role == 'admin'` on invite join is rejected).
- **Last Admin Protection**: Role demotion and member removal check `getAfter(familyPath).data.adminCount >= 1` and exact `±1` deltas so a family can never lose its last admin.
- **Append-Only Audit Trail & No Hard Deletes**: `transactions` forbid `allow delete` and require every `create`/`update` (`edited`/`voided`) to reference a new `auditEvents/{eventId}` written in the same batch with `beforeState`/`afterState`. Voided transactions are permanently immutable (`existing().status == 'active'`).
- **Privacy in Telemetry**: Crashlytics non-fatal error breadcrumbs sanitize Firestore paths (collection names only, never document IDs) and never log transaction titles, notes, emails, or prompt text.

---

## Required Firestore Indexes (`firestore.indexes.json`)

Only the exact composite index required by the app's queries is configured:

| Collection | Fields | Query Purpose |
| --- | --- | --- |
| `invites` | `familyId` (`ASC`), `status` (`ASC`) | Admin-only listener for pending invite codes (`whereEqualTo("familyId", ...).whereEqualTo("status", "pending")`) |

All other active queries use Firestore automatic single-field indexes:
- `families/{familyId}/transactions`: `orderBy("createdAt", DESC)`
- `families/{familyId}/auditEvents`: `whereEqualTo("transactionId", ...)` / ordered in app memory
- `families/{familyId}/categories`: collection snapshot ordered by `sortOrder` in app memory

---

## Running Every Test Suite

### 1. Domain, ViewModel (Turbine), & Compose UI / Accessibility (Robolectric)
Runs all unit tests, analytics reconciliation tests, prompt-injection & AI tool-grounding tests, shell navigation tests, the **Add → Edit → Void** core journey UI test, and the **200% font-scale / TalkBack / 48dp target** accessibility test:
```bash
gradle :app:testDebugUnitTest
```

### 2. Firestore Security Rules Emulator Suite
Runs the 23 allowed/denied matrix tests across all collections (`users`, `families`, `members`, `categories`, `transactions`, `auditEvents`, `invites`) and roles (`Admin`, `Member`, `Viewer`, `Outsider`, signed-out):
```bash
firebase emulators:exec --only firestore,auth --project gen-lang-client-0840137777 "FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node --test firestore-tests/firestore.test.js"
```

### 3. R8 Release Minification & Build Verification
Verify R8 shrinking, resource shrinking, and ProGuard rules (no upload keystore needed for R8 step):
```bash
gradle :app:minifyReleaseWithR8
```
Build signed release APK & AAB (requires upload keystore + signing env vars):
```bash
export KEYSTORE_PATH="/absolute/path/to/my-upload-key.jks"
export STORE_PASSWORD="your-store-password"
export KEY_PASSWORD="your-key-password"
gradle :app:assembleRelease :app:bundleRelease
```

---

## Release APK / AAB Checklist

1. **Signing & Secrets Hygiene**
   - [ ] Generate upload keystore locally (`keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`) — **never commit it**.
   - [ ] Export `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` in CI/local shell.
   - [ ] Verify `app/google-services.json` matches production Firebase project `gen-lang-client-0840137777`.
2. **Firebase Console Verification**
   - [ ] Deploy `firestore.rules` and `firestore.indexes.json` (`firebase deploy --only firestore`).
   - [ ] Register Play App Signing SHA-1 & SHA-256 + upload key SHA-1 in Firebase Console.
   - [ ] Enable Play Integrity provider in **App Check** for `com.aistudio.grihaledger.fkmzqr`.
   - [ ] Confirm Firebase Crashlytics and Firebase AI Logic are active on the project.
3. **Quality & Version Bump**
   - [ ] Increment `versionCode` and `versionName` in `app/build.gradle.kts` when cutting a new release.
   - [ ] Run `gradle :app:testDebugUnitTest`.
   - [ ] Run Firestore Emulator rules suite (23/23 passing).
   - [ ] Run `gradle :app:bundleRelease` and `gradle :app:assembleRelease`.
4. **Two-Phone Pre-Release Smoke Test**
   - [ ] Install the release APK (`adb install app/build/outputs/apk/release/app-release.apk`) on **two physical phones** signed into the same family (e.g., Phone A as Admin, Phone B as Member).
   - [ ] Confirm offline add on Phone A shows pending sync badge and syncs live to Phone B when online without duplicates.
   - [ ] Verify Edit + Void audit trail, Viewer read-only enforcement, Play Integrity App Check acceptance on Firestore + Gemini Advisor, and readable stack traces in Crashlytics.

---

## Google Play Internal Testing Distribution Steps

1. Run `gradle :app:bundleRelease` to produce `app/build/outputs/bundle/release/app-release.aab`.
2. Open **Google Play Console** → Select **GrihaLedger** → **Test and release** → **Testing** → **Internal testing**.
3. Create a new release and upload `app-release.aab` (also upload the R8 mapping file `app/build/outputs/mapping/release/mapping.txt` if Play App Signing prompts or rely on Firebase Crashlytics Gradle upload).
4. Copy the **Play App Signing key SHA-1 and SHA-256** from **Setup → App integrity → App signing** into Firebase Console (**Project Settings** and **App Check → Play Integrity**) so signed builds delivered by Play pass Google Sign-In and App Check.
5. Add family / QA tester Gmail accounts under the **Testers** tab, save, review release, and click **Start rollout to Internal testing**.
6. Share the **Copy link** opt-in URL with testers so they install directly from Google Play.
