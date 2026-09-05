# AIRA

AIRA is a calm companion on the phone.

The phone listens. Speak a chosen phrase, or touch the circle. AIRA replies with a short, fixed script and can tell a named carer. It works offline.

Not a chatbot. Not a doctor. Not an emergency service.

## How it works

Speech stays on the phone. Chosen phrases map to a fixed reply. The reply is the same every time. A carer can be told if that is allowed. Audio is not saved.

**Listen** — name, circle, status.  
**Setup** — names and the urgent phrase.  
**Demo** — run the story without the microphone. Nothing is sent.  
**History** — what was heard and said.

## Try it

Android 8+, or the emulator. Grant the microphone.

```
./gradlew assembleDemo      # simulated alerts, no SMS or calls
./gradlew assembleRelease   # real carer contact after setup
```

Or open the project in Android Studio and Run.

## Code

```
listen → on-device speech → phrase rules → spoken script
                                      → carer alert (if allowed)
```

`MainActivity` screens · `MainViewModel` state · `VoiceAssistantService` mic · `SpeechEngine` speech-to-text · `DistressDetector` phrases · `ResponseEngine` scripts · `SettingsRepository` encrypted setup · `AlertDispatcher` alerts.

`demo` and `release` are Gradle build types. Demo never requests SMS.
