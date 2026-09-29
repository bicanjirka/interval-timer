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
- Gradle 9.6 wrapper, AGP 9.4.1 (built-in Kotlin, no `kotlin.android` plugin), Kotlin 2.4, compileSdk/targetSdk 37, minSdk 35, package `dev.juras.intervaltimer`. `material3` is pinned to `1.5.0-alpha29` (the BOM's 1.4.0 keeps the expressive theme internal); drop the pin once 1.5 is stable. Android SDK at `C:\Users\juras\AppData\Local\Android\Sdk`.
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
- **Design system:** Material 3 Expressive (`MaterialExpressiveTheme`, spring motion, large rounded shapes, own dark colour scheme, no dynamic colour), Material icons from `material-icons-core` (extra icons like copy and minus are drawn in `ui/Icons.kt`; don't pull in the huge extended set). Primary action = extended FAB with icon and label that collapses to the icon when the list scrolls.
- **No confirmation dialogs anywhere.** Destructive actions (stop a routine, delete a routine) happen at once and a 3-second snackbar offers "Undo stop" / "Undo delete" (`showUndo` in `ui/Undo.kt`). Use the same for any new destructive action.
- Every screen is coloured: the running screen fills with the phase colour (colours per the research on other timer apps: whole background changes, current and next phase shown, one huge button for the main action), list cards use the routine colour, editor phase cards use the phase colour.
- MVP: routine list with one-tap start; routine editor (add, reorder, duplicate blocks and phases); running screen; settings (volume, ducking on/off, voice on/off, keep-screen-on).
- Not in the MVP: widget or launcher shortcut, lock-screen extras, Wear OS haptics, Health Connect, JSON import/export, dynamic colour.

## Data model
- `Routine`: name (may be blank: the list and running screen then label it by its shape, `Routine.title()`), colour/icon, ordered blocks, optional warm-up and cool-down.
- `Block`: rounds, ordered phases, rest between blocks.
- `Phase`: name, duration, colour, optional sound, optional spoken text, optional per-round delta (add or subtract seconds each round, with a minimum).
- A `Phase` with `manual = true` has no length: `TimerEngine` never ends it by itself, `skip()` is "Done", and the state reports `openEnded` (total left unknown) and counts up (`displaySeconds`).
- Provide small factories for the common shapes (`repeat(work, rest, rounds)`, `timer`, `untilDone`) so callers and tests don't build full structures. Rest absent means no rest phase, not a zero-length one.
- A routine saved by an older app version must still load; keep the JSON format compatible or migrate.

## Architecture
- **Timer engine** (`engine`): pure Kotlin, no `android.*`, no threads. Current phase and remaining time are computed from elapsed monotonic time and the routine, never by counting ticks, so pause, resume, skip and late ticks stay exact. The clock is injected (real one wraps `SystemClock.elapsedRealtime`). Timing logic lives here, not in the service or UI.
- **Foreground service** (`service`) runs the routine so it survives screen-off: persistent notification with pause, resume, skip; `POST_NOTIFICATIONS` runtime permission; a declared foreground service type (Android 14+). It publishes engine state as one immutable snapshot (StateFlow); the UI only reads that and sends events.
- **Screen on** during a session via `FLAG_KEEP_SCREEN_ON`; Doze exemption prompt only if needed.
- **UI** (`ui`): stateless composables (state in, events out). Running screen: giant countdown digits (plain seconds below 100, `m:ss` from 100, drawn centred on the measured outline of the digits, not on the font's line box, which let a single digit overflow on the phone), full-screen colour per phase, phase name top left, `#round/rounds` top right, next phase, total remaining time, large pause/skip/stop buttons, dark by default. Text in `strings.xml`.
- Packages by feature: `engine` (`Routine`/`Block`/`Phase`, `Timeline`, `TimerEngine`, `formatSeconds`), `service` (`TimerService`, `CuePlayer`: beeps, speech, vibration), `data` (`RoutineJson`, `RoutineStore`, `SettingsStore`), `ui` (screens, `AppViewModel`, `theme`).
- **Timeline rule:** a rest phase at the end of a block's last round is dropped (the next block, block rest or the end follows); zero-length steps are skipped. Deltas apply per round with a minimum.
- **Screen flow:** `App` shows `RunningScreen` whenever `TimerService.state` is non-null, else the screen `AppViewModel` is on (list, editor, settings). Leaving the editor saves, unless nothing changed (a new routine left as it was is not kept). **List:** tap starts; long-press selects (top bar becomes edit / copy / delete) and drags to reorder; while something is selected taps only move the selection. **Stop:** the service pauses the engine for 3 s (`TimerService.stopPending`), the snackbar (right above the pause/skip/stop buttons, so an accidental Stop has its undo under the thumb) offers undo, then it really ends. **Done screen:** per-round real times from `TimerState.results` via `WorkoutSummary`.
- **Storage:** all routines are one JSON string in DataStore; new fields need defaults. Default routines have fixed ids.
- Open items and the on-phone checklist are in `TODO.md`; the service and audio code have not been run on a device yet.

## Audio
- Own timer volume slider, separate from media volume.
- Request transient audio focus with ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) while a cue plays. Ducking must be switchable off in settings and the switch must really work.
- Cues: short beeps for phase changes, a 3-2-1 countdown, `TextToSpeech` for phase names.
- **Android 17 background-audio hardening:** apps targeting 17 need a foreground service with while-in-use capability to play audio in the background (or exact-alarm permission with `USAGE_ALARM`). Failures are silent. Test with `adb shell cmd audio set-enable-hardening throw`. `USAGE_ALARM` follows alarm volume, not media volume, which conflicts with the own-volume slider; decide on the device.

## Verification
Cheapest proof first: a JVM unit test for rules and timing → a Compose preview for how a screen looks → the phone only for what no test can assert (service surviving screen-off, ducking, keep-screen-on, notification, layout at real size). One phone pass per feature, after its last step. Engine tests cover phase math, deltas, pause and resume; write them as plain behaviour-named tests.

## Talking to the phone from this machine
`adb` is at `C:\Users\juras\AppData\Local\Android\Sdk\platform-tools\adb.exe`. The user pairs over Wireless debugging (`adb pair <ip>:<pairing-port> <code>`); after that, if `adb devices` is empty, restart the server (`adb kill-server`, `adb start-server`, wait a few seconds) and mDNS finds the connect port by itself (`adb mdns services`). The phone is a Pixel 10 on Android 17. The port changes when Wi-Fi toggles.
- In Git Bash set `MSYS_NO_PATHCONV=1` before adb commands that take `/sdcard/...` paths, and give local destinations as `C:/...` paths.
- Install and launch: `./gradlew.bat installDebug` (it can end with a harmless `TimeoutException` after "Installed on 1 device"), `adb shell am start -n dev.juras.intervaltimer/.MainActivity`. Grant notifications with `adb shell pm grant dev.juras.intervaltimer android.permission.POST_NOTIFICATIONS`.
- Look at the screen: `adb exec-out screencap -p > file.png` (phone screenshots come out at 1080×2424). Tap with `adb shell input tap x y`; screen off/on is `input keyevent 223` / `224`.
- The user may be holding the phone: expect their own taps in the log, and warn before making it beep.

## Debugging a workout after the fact
The app logs through `AppLog` (`log/`): every line goes to logcat (tag `IntervalTimer`) and to a file in the app's external files directory, kept to about 1 MB in two rolling files. Uncaught exceptions are logged with a stack trace. With the phone connected:
```
adb pull /sdcard/Android/data/dev.juras.intervaltimer/files/logs/ ./phone-logs     # works for release builds too
adb logcat -d -s IntervalTimer                                                      # what logcat still holds
```
Read `timer.log.1` then `timer.log`. Lines are `MM-dd HH:mm:ss.SSS LEVEL Tag: message`; tags are `App`, `Activity`, `Service` (commands, status changes, one line per segment, wake lock, `timer loop stalled` warnings when cues may have been late), `Cues` (each cue, text to speech, audio focus, settings). Look first for `E` and `W` lines, then compare each `segment n/m` line's time with the expected schedule. Log new events in the service, cue and UI-to-service code; keep `engine` free of logging (pure).

## CI/CD
- Remote: `origin` = `git@github.com:bicanjirka/interval-timer.git` (public, branch `main`). Public keeps Obtainium simple.
- `.github/workflows/ci.yml`: unit tests + debug build on every push to `main` and every PR.
- `.github/workflows/release.yml`: pushing a tag `v*` (e.g. `git tag v1.0.0 && git push origin v1.0.0`) runs the tests, builds a signed release APK and publishes it as a GitHub Release named `interval-timer-<tag>.apk`. `versionName` comes from the tag (without the `v`), `versionCode` from the workflow run number, so every release is newer than the last. The job fails early with a clear message if a signing secret is missing.
- Signing: one release key signs every release so updates install; a different key can never update an installed copy. The key lives **outside the repo** in `C:\Users\juras\.android-keystores\` (`interval-timer-release.jks`, `interval-timer-signing.txt` with alias and passwords, `interval-timer-release.jks.base64.txt`). **The user must back this folder up.** The build reads `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` from the environment; with none set, a release build is left unsigned. Never commit keystore or passwords.
- GitHub repo secrets the release workflow needs (Settings → Secrets and variables → Actions): `ANDROID_KEYSTORE_BASE64` (contents of the `.base64.txt` file), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`intervaltimer`), `ANDROID_KEY_PASSWORD`. The `gh` CLI is not installed here, so the user adds them in the web UI.
- Signing certificate SHA-256: `10:1B:AE:58:D5:19:37:C8:AD:A7:8A:76:42:B1:90:76:38:06:9F:A5:2B:BD:13:12:A7:3C:04:F2:46:57:3A:28`. A release APK signed with this key can't be installed over the debug build on the phone (different key); uninstall the debug build first.
- Google developer verification (verified against the docs on 2026-09-29): enforced from 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, globally from 2027. ADB/Android Studio installs are unaffected. For Obtainium installs register `dev.juras.intervaltimer` and the fingerprint above in the Android Developer Console (limited distribution: free, no ID, up to 20 devices, needs 2-step verification and a Google payments profile). Steps are in `TODO.md`.
- Release minification only if it doesn't complicate things.
