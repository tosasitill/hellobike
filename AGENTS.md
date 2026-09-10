# Repository Guidelines

## Project Structure & Module Organization

`RideCompact` is a single-module Android app (`:app`) in Kotlin/Java, package `dev.local.ridecompact`.

- `app/src/main/java/dev/local/ridecompact/` — app code: `MainActivity.kt`, `LoginActivity.kt`, `PortraitScanActivity.java`, plus API/state helpers (`RideApi`, `LoginApi`, `Gateway`, `ApiResponse`, `RideState.kt`, `SessionStore`, `ScanParser`, `Coordinates`).
- `app/src/main/res/` — layouts, drawables, styles, `xml/data_extraction_rules.xml`.
- `app/src/test/java/dev/local/ridecompact/` — JVM/Robolectric unit tests.
- `toolchain/` — pinned JDK 17, Gradle, Android SDK (git-ignored).
- `private/` — local `account.json` credentials (git-ignored, never committed).
- `releases/` — archived APKs; `build.sh` and `export-account.cjs` are the root scripts.

`*.legacy` / `*.migrated` files are retired sources kept for reference; do not edit or wire them into builds.

## Build, Test, and Development Commands

```bash
bash build.sh                              # assembleRelease + testDebugUnitTest + lintDebug
node export-account.cjs                    # regenerate private/account.json from parent HAR
```

`build.sh` exports `JAVA_HOME`/`ANDROID_HOME` from `toolchain/` and runs `gradle -p . :app:assembleRelease :app:testDebugUnitTest :app:lintDebug`. Outputs: `app/build/outputs/apk/release/app-release.apk`, reports under `app/build/reports/`. Network access is needed only for first-time dependency resolution.

## Coding Style & Naming Conventions

- 4-space indentation; no tabs. Kotlin for new code, Java only where existing files are Java.
- Types `PascalCase`, members/locals `camelCase`, constants `UPPER_SNAKE_CASE`, files named after their primary type.
- Tests are terse one-line `@Test` methods with behavior names, e.g. `expiredOrClockRewoundPrecheckCannotOpen`.
- No formatter is configured; keep diffs minimal and match surrounding style.

## Testing Guidelines

JUnit 4 with Robolectric (`@Config(sdk=28)`) and `testImplementation 'org.json:json'`. Add tests to `CoreTest`, `ApiTest`, or `AndroidRegressionTest` rather than new suites. Cover scan-parameter rejection, `RideState` transition guards, error-code mapping, and Keystore recovery. Run `bash build.sh`; results land in `app/build/test-results/testDebugUnitTest/`.

## Commit & Pull Request Guidelines

This checkout has no Git history, so follow Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`) with an imperative subject under ~72 characters. PRs must state what changed, list commands run and their results, link related issues, and note device-testing gaps. Never commit `private/`, `toolchain/`, keystores, or real tokens anywhere in a diff, issue, or screenshot.

## Security & Configuration Tips

Credentials live only in `private/account.json` (mode `0600`) and are encrypted at rest via Android Keystore. Do not log tokens, order IDs, or request bodies. Business endpoints must not be called during development; verify offline first, then confirm on-device manually.
