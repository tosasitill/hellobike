# Scope and Timeline

- objective: Independent minimal Android riding client based on supplied APK/HAR.
- primary_skill: apk-reverse
- lead_role: lead
- specialist_roles: [cre, doc]
- permitted: offline reverse engineering, source implementation, local build and tests, SDK/dependency downloads
- not performed: live business requests, vehicle control, credential replay, BLE commands
- performed: read-only probes of the official LBS reverse-geocode endpoint (no account identity) to confirm the city/adCode source
- app runtime: user imports own HAR/account and explicitly enables authenticated online mode
- secrets: separate private/account.json (0600), not included in APK; Android Keystore at rest

## Work Items

- [x] Verify official scan host/parameter semantics and package version.
- [x] Implement QR scanning, credential import and network workflow.
- [x] Add pending-state persistence and duplicate-mutation guards.
- [x] Build release variant, run 41 offline tests (40 pass on JDK 17; see README known issue), run Lint.
- [x] Verify APK signature and scan artifact for embedded session data.
- [x] Document intentional differences and evidence limits.
- [x] Android startup and layout verified on a Pixel 6a emulator (Android 17); camera / on-device location / Keystore still pending.
- [ ] Clean baseline real ride validation: not performed.

## Findings

- Generic scan parameter DynamicModel.KEY_ABBR_DYNAMIC_NUM resolves to n.
- The official bicycle handler supports n/u (extended e); prototype restricts to n.
- Source references can reveal parameter semantics but do not prove this new client works online.
- Java installations named openjdk@24/@25 actually symlink to JDK 26; downloaded isolated JDK 17 to resolve AGP/Gradle compatibility.
- Android JSONObject and desktop org.json differ in string coercion; HAR imports normalize scalar fields explicitly.
- Release variant is non-debuggable but uses local debug signing key; suitable only for local testing.
- regeo returns cityCode (3-6 digits) plus 6-digit adCode; the note "auto location resolves the district" replaces the earlier manual city-code entry.
- No real-device testing; do not claim end-to-end completion beyond build/offline tests and emulator UI checks.
