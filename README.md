# AIRA Pilot

AIRA is an Android research pilot for people who benefit from simple support during panic or distress. It offers one-tap calming guidance, offline-preferred speech recognition, custom trigger phrases, and consented caregiver escalation.

> AIRA is supportive technology, not a medical device, diagnosis tool, vital-sign monitor, or replacement for local emergency services.

## Current capabilities

- Panic-first Compose interface with a large Help action, spoken guidance, and emergency dialer fallback
- Deterministic, reviewable distress phrase rules; no network or LLM is required to choose a response
- Custom patient trigger phrases and encrypted caregiver settings
- Started-and-bound microphone foreground service with visible state and degraded-mode messaging
- On-device Android TTS and offline-preferred Android speech recognition
- Durable Room alert outbox, duplicate suppression, retry handling, and delivery states
- Consented direct-SMS fallback when internet is unavailable and the permission is granted
- Isolated debug demo delivery and caregiver acknowledgement for conference demonstrations

Android's `EXTRA_PREFER_OFFLINE` speech option is a request, not a guarantee. Voice transcription depends on an offline recognition service/language pack installed on the device. The one-tap Help flow and fixed calming guidance remain available when recognition is unavailable.

Secure production push is intentionally fail-closed until an authenticated caregiver endpoint is configured. Debug builds simulate that endpoint using isolated test contacts; they do not contact a real backend.

Configure a pilot endpoint outside Git with `AIRA_CAREGIVER_ENDPOINT=https://…` in the user's Gradle properties. The caregiver service must issue a revocable pairing token, entered during setup and stored in Android Keystore-backed encrypted preferences. Requests contain an idempotency key, severity, event ID, and timestamp—never speech content.

## Build

Requirements:

- Android Studio with JDK 17
- Android SDK 34
- Android 8.0 / API 26 or newer device

```bash
./gradlew test lint assembleDebug
```

For a release build, create an untracked `keystore.properties`:

```properties
storeFile=/absolute/path/to/aira-release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Never commit a keystore or signing credentials. The previously exposed pilot key must not be reused.

## Device setup and pilot check

1. Install a device's offline English speech language pack.
2. Open AIRA and configure the patient name, caregiver, phone, custom phrases, and consent.
3. Grant microphone and notification access. Grant SMS only if direct offline fallback is approved.
4. Send a test caregiver alert and verify its displayed state.
5. Test Help, custom phrases, screen lock, airplane mode, large text, and TalkBack on the target device.

Automated SMS is a Google Play restricted permission. Distribution teams must confirm policy eligibility; otherwise use the user-visible caregiver call/message path.

## Privacy model

- Raw audio is not stored by AIRA.
- Session messages remain in memory and can be cleared.
- Caregiver configuration is encrypted with Android Keystore-backed preferences.
- Alert records contain severity, status, attempts, and timestamps—never raw audio or transcripts.

## Architecture

The core path is:

`Compose UI → foreground voice service → speech engine → deterministic detector → calming TTS → durable alert outbox`

Alert delivery uses online secure push when configured and direct SMS fallback with consent. WorkManager handles durable retries; Room provides status history and deduplication.

## Conference demo

Debug builds expose a clearly labeled critical-phrase simulation and simulated online caregiver acknowledgement. The demo uses the same state machine and outbox as the pilot path. Use test contacts only.

Recommended demonstration:

1. Configure a custom phrase.
2. Trigger one-tap or spoken calming support.
3. Show queued/sent/acknowledged caregiver state.
4. Enable airplane mode and demonstrate local guidance plus the fallback state.

## Known pilot constraints

- No clinical validation has been completed.
- Offline transcription availability varies by Android device.
- Production caregiver push requires a separately operated authenticated backend.
- Battery/Doze and SMS behavior must be qualified on every supported device model.
- The microphone service is intentionally non-sticky: Android may require the user to reopen AIRA after terminating its process rather than permitting an unsafe background microphone restart.
