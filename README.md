# AIRA

AIRA is a calm companion on the phone for someone living with acquired brain injury.

When they are overwhelmed, they do not need to hunt for a button. The phone listens. They can speak a phrase they chose with their carer, or touch the large circle. AIRA answers with a short, fixed calming script. If they have agreed to it, a carer is told that help may be needed.

It is meant to work when the person can barely move, and when there is no internet.

AIRA is **not** a chatbot, a doctor, or an emergency service.

## Why it is built this way

Generative AI can invent advice, need a network, and send speech to the cloud. That is a poor fit in a panic.

AIRA keeps the path small and reviewable:

1. The microphone turns speech into words on the phone.
2. Those words are checked against phrases the carer set up (for example `call Abi`).
3. A matching phrase picks a severity and a fixed spoken reply. The reply is the same every time.
4. If alerts are allowed, the carer is notified. In the **demo** build, that notification is only shown on screen. Nothing is actually sent.

Audio is not saved. Transcripts are not uploaded.

## Screens

| Screen | Who it is for |
|---|---|
| Listen | The patient. Name, listening circle, one status word. |
| Setup | The carer. Patient name, carer name, urgent phrase. |
| Demo | Showing the idea. Run a critical phrase without using the microphone. |
| History | Later review of what was heard and said. Not on the patient screen. |

## How to try it

You need an Android phone (Android 8 or newer) or the Android emulator.

```
./gradlew assembleDemo
```

That builds a **demo** APK: listening, names, and simulated carer alerts. It does not send SMS or place a call.

```
./gradlew assembleRelease
```

That builds the **production** APK: real carer contact when setup and consent are complete.

Open the project in Android Studio if you prefer Run over the command line. Grant the microphone when asked.

## For engineers

```
Listen  →  on-device speech-to-text  →  phrase rules  →  spoken script
                                              ↓
                                    carer alert (if allowed)
```

| File | Role |
|---|---|
| `MainActivity.kt` | Screens |
| `MainViewModel.kt` | Permissions and service |
| `VoiceAssistantService.kt` | Microphone session |
| `SpeechEngine.kt` | On-device speech-to-text |
| `DistressDetector.kt` | Phrase → severity |
| `ResponseEngine.kt` | Severity → script |
| `SettingsRepository.kt` | Encrypted names and phrases |
| `AlertDispatcher.kt` | Alert delivery and retries |

Demo vs production is a Gradle build type (`demo` / `release`). Demo mode never requests SMS.

## Safety

This is a research prototype for education and discussion. It does not diagnose, monitor vital signs, or replace calling emergency services.
