# AIRA pilot and conference release gate

This checklist is evidence-driven. Do not mark AIRA pilot-ready from a successful build alone.

## Safety and caregiver

- [ ] Patient and caregiver complete consent together.
- [ ] Custom phrases are reviewed for accidental/common-word matches.
- [ ] Test alert reaches the intended test caregiver.
- [ ] Alert state moves through queued, sent, and acknowledged.
- [ ] Airplane-mode SMS fallback is tested with the target carrier and device.
- [ ] Duplicate phrases within five minutes create only one alert.
- [ ] Emergency dialer opens without relying on AIRA's service.
- [ ] Local emergency number and pilot escalation script are documented.

## Accessibility

- [ ] TalkBack announces Help, status, caregiver call, and emergency dialer.
- [ ] Controls remain usable at 200% font size and display size.
- [ ] The flow works without interpreting color.
- [ ] A patient can start guidance with one action from the support screen.
- [ ] Haptic, audio, and visible feedback are evaluated with representative users.

## Reliability matrix

- [ ] Cold launch and first permission grant
- [ ] Permission denial and later grant in system settings
- [ ] Screen locked for 30 minutes
- [ ] App task removed while support service is active
- [ ] Battery saver and Doze
- [ ] Incoming phone call and other audio focus changes
- [ ] No offline speech language pack installed
- [ ] TTS engine missing or disabled
- [ ] Airplane mode, poor connectivity, and restored connectivity
- [ ] Device reboot and app update

Record device model, Android version, outcome, battery use, and logs for every run.

## Privacy and security

- [ ] No raw audio or transcript appears in app storage, alert database, logs, or network payloads.
- [ ] Backup extraction of private settings is disabled.
- [ ] Release signing secrets are supplied outside Git and the old exposed key is retired.
- [ ] Production caregiver endpoint uses HTTPS and a revocable, short-lived pairing token.
- [ ] Opt-in crash reporting is reviewed to exclude health and conversation content.
- [ ] Automated SMS distribution is approved for the chosen store/channel.

## Conference rehearsal

Use test contacts and a debug build only.

1. Configure patient, caregiver, custom phrase, and consent.
2. Press Help and show deterministic spoken calming guidance.
3. Speak the custom phrase and show caregiver alert state.
4. Acknowledge the isolated demo alert.
5. Enable airplane mode and demonstrate local guidance plus SMS/degraded delivery state.
6. Disable the speech service and show the one-tap fallback.

Keep a second charged, preconfigured device and a screen recording of all three flows. Describe AIRA as a research pilot; do not claim clinical validation or guaranteed emergency monitoring.
