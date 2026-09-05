# AIRA pilot size report

Measured on 2026-09-05 after `test`, `lint`, R8, and resource shrinking:

- Debug APK: approximately 12 MB
- Minified unsigned release APK: approximately 976 KB
- Minified release Android App Bundle: approximately 2.6 MB

The release APK is unsigned because signing credentials are intentionally external to Git.

## Decisions

- Generated `app/build/` content is no longer tracked. About 700 generated files were removed from version control.
- The app uses Android's installed on-device recognizer instead of embedding a 40–150 MB speech model.
- R8 and resource shrinking are enabled for release.
- Pilot resources are restricted to English.
- Full Material icon packs and bundled calming audio are not included.

## Trade-off

Android 12 and newer use `createOnDeviceSpeechRecognizer` only when an on-device recognizer is installed. Older supported devices request offline recognition, but the system implementation may vary. This keeps the download small while preserving one-tap deterministic guidance when transcription is unavailable.

Re-run before each release:

```bash
./gradlew test lint assembleDebug assembleRelease bundleRelease
du -h app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  app/build/outputs/bundle/release/app-release.aab
```
