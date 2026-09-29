# Interval Timer

Personal ad-free interval timer for the user's Google Pixel 11 (Android 17, API 37), used at the gym. Kotlin + Jetpack Compose + Material 3. Not for the Play Store: sideloaded, updated via Obtainium from GitHub Releases.

## How to work here
- **Goals:** fast development, good-enough tests, a working app on the phone in the end.
- **The user is not strict about standards or tooling.** Coding style is loose, and the choice of build, test and library tooling is yours. Decide, mention the choice in a line, and move on. Don't ask permission for framework choices. Don't add heavyweight tooling (formatters, linters, architecture rules) unless it clearly speeds delivery.
- Act instead of asking when the answer is obvious; give a recommendation rather than an option survey.
- Commit after each coherent task that builds and passes tests. Subject in imperative mood (`Add X`), ending with the Co-Authored-By line the harness specifies.
- Before large refactors or destructive actions, say what you'll do. Never delete files you haven't looked at.
- If a permission prompt blocks a command, tell the user and let them run it with `! <cmd>`. Don't work around a denial.
- Report honestly when a build or test fails, and when something was not verified on the phone.
- Verify version numbers and Android API behaviour against current docs; facts here come from research in September 2026 and may drift.
- Keep this file to constraints and decisions, and fix any line a change makes false.

## Build environment
- JDK 25 (Temurin) at `C:\Users\juras\.jdks\temurin-25.0.4.1` (`JAVA_HOME`; a shell opened before it was set needs `$env:JAVA_HOME` set by hand). Gradle daemon toolchain pinned to 25 in `gradle/gradle-daemon-jvm.properties`; CI must use 25 too.
- Gradle 9.6 wrapper, AGP 9.4.1 (built-in Kotlin, no `kotlin.android` plugin), Kotlin 2.4, compileSdk/targetSdk 37, minSdk 35, package `dev.juras.intervaltimer`. Android SDK at `C:\Users\juras\AppData\Local\Android\Sdk`.
- Versions live in `gradle/libs.versions.toml`.
- Windows + PowerShell: use `./gradlew.bat`.

```
./gradlew.bat assembleDebug testDebugUnitTest    # the check before every commit
./gradlew.bat testDebugUnitTest --tests "*TimerEngineTest*"
./gradlew.bat installDebug                       # needs Wireless debugging paired
```

## Tooling decisions
- **Build:** Gradle with the version catalog, as generated. No extra plugins beyond what a feature needs.
- **Unit tests:** JUnit 4 (already in the project, works with AGP out of the box) with plain `kotlin.test`-style assertions or JUnit asserts, plus `kotlinx-coroutines-test` if flows need testing. No mocking framework: the engine takes an injected clock, so hand-written fakes are enough.
- **UI:** Robolectric + Roborazzi render every screen to PNG on the JVM (`ScreenshotTest`), so layout and colour can be checked without a phone: run `./gradlew.bat recordRoborazziDebug`, then look at `app/build/screens/*.png`. A plain `testDebugUnitTest` only checks the screens compose. No assertion-based UI tests.
- **Persistence:** routines as JSON (kotlinx.serialization) in a file via DataStore, not Room. A routine is a small nested document, so this is faster to build and easy to add JSON export to later. Add the dependencies when that step starts.
- **CI:** GitHub Actions. Add `.github/workflows/release.yml` (below) after the MVP runs on the phone; a push workflow running `testDebugUnitTest` is welcome once tests exist.

## Product
- Needs: one-tap start of saved routines, a volume the user controls independently, big colour-coded high-contrast screens readable from a distance, no ads or accounts.
- Typical workouts: a simple repeat of work and rest (e.g. 10 × 1 min, rest optional), a plain timer, and **work until done**: the work time is unknown, so it counts up for information only, the user presses Done, then a fixed rest counts down, then the next set. Defaults on a fresh install: `10 × 1 min` and a work-until-done routine (60 s rest). No Tabata preset.
- Cues (Settings): sound, sound and vibration, or vibration only (silent: no beeps, no speech, no audio focus).
- Every screen is coloured: the running screen fills with the phase colour (colours per the research on other timer apps: whole background changes, current and next phase shown, one huge button for the main action), list cards use the routine colour, editor phase cards use the phase colour.
- MVP: routine list with one-tap start; routine editor (add, reorder, duplicate blocks and phases); running screen; settings (volume, ducking on/off, voice on/off, keep-screen-on).
- Not in the MVP: widget or launcher shortcut, lock-screen extras, Wear OS haptics, Health Connect, JSON import/export, dynamic colour.

## Data model
- `Routine`: name, colour/icon, ordered blocks, optional warm-up and cool-down.
- `Block`: rounds, ordered phases, rest between blocks.
- `Phase`: name, duration, colour, optional sound, optional spoken text, optional per-round delta (add or subtract seconds each round, with a minimum).
- A `Phase` with `manual = true` has no length: `TimerEngine` never ends it by itself, `skip()` is "Done", and the state reports `openEnded` (total left unknown) and counts up (`displaySeconds`).
- Provide small factories for the common shapes (`repeat(work, rest, rounds)`, `timer`, `untilDone`) so callers and tests don't build full structures. Rest absent means no rest phase, not a zero-length one.
- A routine saved by an older app version must still load; keep the JSON format compatible or migrate.

## Architecture
- **Timer engine** (`engine`): pure Kotlin, no `android.*`, no threads. Current phase and remaining time are computed from elapsed monotonic time and the routine, never by counting ticks, so pause, resume, skip and late ticks stay exact. The clock is injected (real one wraps `SystemClock.elapsedRealtime`). Timing logic lives here, not in the service or UI.
- **Foreground service** (`service`) runs the routine so it survives screen-off: persistent notification with pause, resume, skip; `POST_NOTIFICATIONS` runtime permission; a declared foreground service type (Android 14+). It publishes engine state as one immutable snapshot (StateFlow); the UI only reads that and sends events.
- **Screen on** during a session via `FLAG_KEEP_SCREEN_ON`; Doze exemption prompt only if needed.
- **UI** (`ui`): stateless composables (state in, events out). Running screen: giant countdown digits, full-screen colour per phase, current and next phase, round counter, total remaining time, large pause/skip/stop buttons, dark by default. Text in `strings.xml`.
- Packages by feature: `engine` (`Routine`/`Block`/`Phase`, `Timeline`, `TimerEngine`, `formatSeconds`), `service` (`TimerService`, `CuePlayer`: beeps, speech, vibration), `data` (`RoutineJson`, `RoutineStore`, `SettingsStore`), `ui` (screens, `AppViewModel`, `theme`).
- **Timeline rule:** a rest phase at the end of a block's last round is dropped (the next block, block rest or the end follows); zero-length steps are skipped. Deltas apply per round with a minimum.
- **Screen flow:** `App` shows `RunningScreen` whenever `TimerService.state` is non-null, else the screen `AppViewModel` is on (list, editor, settings). Leaving the editor saves. Stop asks for confirmation.
- **Storage:** all routines are one JSON string in DataStore; new fields need defaults. Default routines have fixed ids.
- Open items and the on-phone checklist are in `TODO.md`; the service and audio code have not been run on a device yet.

## Audio
- Own timer volume slider, separate from media volume.
- Request transient audio focus with ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) while a cue plays. Ducking must be switchable off in settings and the switch must really work.
- Cues: short beeps for phase changes, a 3-2-1 countdown, `TextToSpeech` for phase names.
- **Android 17 background-audio hardening:** apps targeting 17 need a foreground service with while-in-use capability to play audio in the background (or exact-alarm permission with `USAGE_ALARM`). Failures are silent. Test with `adb shell cmd audio set-enable-hardening throw`. `USAGE_ALARM` follows alarm volume, not media volume, which conflicts with the own-volume slider; decide on the device.

## Verification
Cheapest proof first: a JVM unit test for rules and timing → a Compose preview for how a screen looks → the phone only for what no test can assert (service surviving screen-off, ducking, keep-screen-on, notification, layout at real size). One phone pass per feature, after its last step. Engine tests cover phase math, deltas, pause and resume; write them as plain behaviour-named tests.

## Debugging a workout after the fact
The app logs through `AppLog` (`log/`): every line goes to logcat (tag `IntervalTimer`) and to a file in the app's external files directory, kept to about 1 MB in two rolling files. Uncaught exceptions are logged with a stack trace. With the phone connected:
```
adb pull /sdcard/Android/data/dev.juras.intervaltimer/files/logs/ ./phone-logs     # works for release builds too
adb logcat -d -s IntervalTimer                                                      # what logcat still holds
```
Read `timer.log.1` then `timer.log`. Lines are `MM-dd HH:mm:ss.SSS LEVEL Tag: message`; tags are `App`, `Activity`, `Service` (commands, status changes, one line per segment, wake lock, `timer loop stalled` warnings when cues may have been late), `Cues` (each cue, text to speech, audio focus, settings). Look first for `E` and `W` lines, then compare each `segment n/m` line's time with the expected schedule. Log new events in the service, cue and UI-to-service code; keep `engine` free of logging (pure).

## CI/CD (after the MVP runs on the phone)
- `.github/workflows/release.yml` on tags `v*`: `actions/setup-java` (temurin 25), `gradle/actions/setup-gradle`, `./gradlew assembleRelease`, then a GitHub Release with the APK. A public repo is simplest for Obtainium; a private repo needs a GitHub token in Obtainium.
- Signing: one release keystore signs every release so updates install. Keep it outside the repo, back it up, store it base64-encoded in GitHub Secrets, read passwords from env vars in `signingConfigs`. Never commit keystore or passwords.
- Google developer verification for sideloaded apps applies from 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, and globally from 2027. ADB and Android Studio installs are unaffected. For Obtainium installs, register the package name and signing key under the free limited-distribution account; verify the exact steps in Google's docs when you get there.
- Release minification only if it doesn't complicate things.
