# Interval Timer

Personal Android interval-timer app for workouts (Kotlin, Jetpack Compose, Material 3). Built for one Pixel phone, sideloaded and updated via Obtainium from GitHub Releases.

## Build environment
- JDK 25 (Temurin) at `C:\Users\juras\.jdks\temurin-25.0.4.1`. `JAVA_HOME` is set as a user env var; in an already-open shell set `$env:JAVA_HOME` explicitly. The Gradle daemon toolchain is pinned to 25 in `gradle/gradle-daemon-jvm.properties`.
- Gradle 9.6 wrapper, AGP 9.4, Kotlin 2.4, compileSdk/targetSdk 37, minSdk 35, package `dev.juras.intervaltimer`.
- Versions live in `gradle/libs.versions.toml`. Check that a version is real and current (Google Maven / Maven Central) before changing it.
- Windows + PowerShell: use `./gradlew.bat`.

## Commands
- Build debug APK: `./gradlew.bat assembleDebug`
- Unit tests: `./gradlew.bat testDebugUnitTest`
- Both at once are the check to run before every commit.
- Install on phone (needs Wireless debugging paired): `./gradlew.bat installDebug`

## Architecture (planned, MVP)
- **Timer engine**: pure Kotlin, no Android imports, so it is unit-testable on the JVM. A routine is a list of blocks; a block is work N s, optional rest M s, repeated for R rounds. Rest is optional and a plain timer is one round with no rest. The engine is driven by a clock (monotonic time, injectable in tests) and computes state from elapsed-time deltas rather than counting ticks, so pause, resume and skip stay exact.
- **Foreground service** runs the active routine so it survives screen-off; it handles audio cues with ducking, voice, and the keep-screen-on setting.
- **UI** (Compose): routine list with one-tap start, routine editor (add, reorder, duplicate blocks and phases), running screen, settings (volume, ducking, voice, keep-screen-on).
- **Running screen**: giant countdown digits, full-screen colour per phase, current and next phase, round counter, total remaining time, large pause/skip/stop buttons. High contrast, dark by default.
- Not in the MVP: widget/launcher shortcut, lock-screen extras, Wear OS haptics, Health Connect, JSON import/export, dynamic colour.

## Conventions
- Keep the code small and readable; match the style of the wizard-generated code (`ui/theme/`, `MainActivity.kt`).
- Write unit tests for the engine: phase math, deltas, pause and resume.
- Commit after each coherent, building and tested task, with a short message ending in the Co-Authored-By line the harness specifies. Only commit work that builds and passes tests.
- Before large refactors or destructive actions, tell the user first. Never delete files you haven't looked at.
- Report honestly if a build or test fails, and say when something was not verified on the actual phone.
- Verify version numbers and Android API behaviour against current docs instead of relying on memory.

## CI/CD (after the MVP runs on the phone)
- Release workflow `.github/workflows/release.yml` on tags `v*`: `actions/setup-java` (temurin 25), `gradle/actions/setup-gradle`, `./gradlew assembleRelease`, then a GitHub Release with the APK.
- Signing: one release keystore signs every release. Keep it outside the repo and back it up. In CI store it base64-encoded in GitHub Secrets and read passwords from env vars in `signingConfigs`. Never commit keystore or passwords.
- Google developer verification for sideloaded apps applies from 2026-09-30 in some countries and globally from 2027; register the package name and signing key under the free limited-distribution account. Verify the exact steps in Google's docs when reaching this step.
