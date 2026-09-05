# AIRA

**AIRA** is a panic companion for people living with acquired brain injury.

When someone is overwhelmed, they tap **Help** (or say a phrase they chose). The phone speaks a calm script and can alert a named carer. It works **offline**. It does **not** chat, diagnose, or replace 999.

That is the point. Unconstrained generative AI can invent advice, need a network, and send speech to the cloud. AIRA uses **on-device speech + reviewable rules**, so doctors and carers can see exactly why it responded.

## Design

```
Patient  →  Help / custom phrase  →  calm spoken script
                                 →  carer alert (consent only)
```

| Layer | What it is |
|---|---|
| UI | One large Help control, carer setup, safety copy |
| Voice | On-device speech-to-text and text-to-speech |
| Rules | Keyword / custom-phrase match → severity → fixed script |
| Alert | Encrypted carer details; SMS / call / optional HTTPS. No audio uploaded |

```
app/src/main/java/com/example/calmcompanion/
  MainActivity.kt            UI
  MainViewModel.kt           state, permissions
  VoiceAssistantService.kt   mic session
  SpeechEngine.kt            on-device STT
  DistressDetector.kt        phrase → severity
  ResponseEngine.kt          severity → script
  SettingsRepository.kt      encrypted carer + triggers
  AlertDispatcher.kt         retry, dedupe, SMS/HTTPS
```

## Try it

Android 8+ phone, Android Studio, run `main`. Grant microphone. Use a **test** carer number.

`./gradlew assembleDebug` — share the APK by USB/AirDrop (GitHub often **blocks** APKs).

Not a medical device. Research / education prototype.
