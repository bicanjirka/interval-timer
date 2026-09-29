# TODO

## Verified on the Pixel 10 (Android 17, API 37) on 2026-09-29, over adb
- Debug build installs and launches; the routine list, get-ready (orange) and work (green) screens render on the real phone as they do in the JVM screenshots.
- Starting a routine runs `TimerService` as a foreground `mediaPlayback` service; the notification exists with 3 actions.
- Timing: segments change within ~30 ms of the schedule, 3-2-1 ticks fire, Pause freezes and Resume continues, backgrounding the app keeps the routine running.
- Screen off (`Dozing`): the wake lock is held, ticks and phase changes stay on the exact second across several segments, no `stalled` warnings, audio focus is requested and released around every cue and around the spoken phase name.
- Logging: the log file is written, and `adb pull` of the logs folder works without root. A whole session of testing produced no `W`/`E` lines and no crash.
- Work until done: count-up shows real time, DONE starts the 60 s rest (red, counting down), the rest runs into Round 2's count-up from 0:00. Stop stopped it (before the undo-stop change).
- Vibration only: the system logged the vibrations (alarm usage, patterns as designed) for the test signal and for every cue of a routine, and the app took no audio focus. Sound mode was restored afterwards.
- Editor on the phone: typing works with the numeric keyboard, the total updates live, leaving saves.
- Bugs found on the phone and fixed: the first cue of a routine used default settings (a beep even in vibration-only mode) because settings loaded asynchronously; the on-screen keyboard covered the field being edited.

## Written on 2026-09-29 but not yet run on the phone (the phone was unreachable over adb)
Unit tests and JVM screenshots pass; these need one pass on the Pixel:
- **List:** long-press selects a card (haptic, white border, top bar shows edit / copy / delete); dragging it moves it and the order survives leaving the app; tap while selected only moves/clears the selection; back clears the selection. `detectDragGesturesAfterLongPress` next to `clickable` is the part most likely to misbehave.
- **Undo stop:** Stop pauses, the snackbar at the top says "Routine stopped / Undo stop"; Undo continues at the same second (and stays paused if it was paused before); doing nothing ends after 3 s and returns to the list. Stop from the notification does the same.
- **Undo delete:** delete from the selection bar, "Undo delete" puts it back in the same place.
- **New routine** left unchanged is not added; a new routine has no name and shows as e.g. "8 × 40 s work / 20 s rest".
- **Editor:** `+`/`−` step (5 s, rounds by 1), hold repeats, typing still works and the keyboard doesn't cover the row.
- **Running screen:** digits fill the width with the condensed face on the real device (measured, so should not clip), 100 s and up shows `m:ss`.
- **Done screen** shows per-round work and rest times.

## Still to check by ear, feel and hand (adb can't tell)
- **Sound is audible** with the screen off, at the app's own volume; spoken phase names are clear; ducking dips other audio and it returns, and with ducking off it doesn't dip.
- **Android 17 hardening:** no hardening violation was seen in the log with the screen off, but `cmd audio set-enable-hardening` doesn't exist on this build, so strict mode wasn't forced. Cues use `USAGE_MEDIA`. If sound ever fails while backgrounded, **Approach:** switch to `USAGE_ALARM` or add exact-alarm permission.
- **Vibration only** (**Where:** `service/CuePlayer.kt`): each phase change is *felt* and the patterns are distinguishable from each other, also with the screen off and in silent/DND mode.
- **Notification buttons** (Pause, Skip/Done, Stop) work from the shade and the lock screen. Not tested over adb because the shade shows the user's private notifications.
- **Keep screen on:** the screen stays lit on the running screen only.
- **Editor:** reorder, duplicate and delete blocks and phases, colours and per-round delta (typing and saving were verified).
- **Layout with sweaty hands:** buttons are easy to hit; digits readable from a distance.
- Speech and the start beep overlap slightly (speech starts 350 ms after the beep); tune `START_SPEECH_DELAY_MS` in `TimerService` if it sounds messy.

## Known gaps
- Process death mid-routine loses the running state (the service stops with the process). **Approach:** only if it happens in practice; persist the routine and start time and rebuild the engine.
- Doze exemption prompt not added. **Approach:** add only if the wake lock isn't enough over a long workout.
- The log records commands but not where they came from (notification, app or adb). **Approach:** add a source to the command intent if that ever matters.

## To publish the first release (needs the user)
1. **Back up** `C:\Users\juras\.android-keystores\` (keystore + passwords file) somewhere outside this PC. Losing the key means installs can never be updated.
2. **Add four GitHub secrets** in the repo: Settings → Secrets and variables → Actions → New repository secret: `ANDROID_KEYSTORE_BASE64` (paste the whole content of `interval-timer-release.jks.base64.txt`), `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD` (both the password in `interval-timer-signing.txt`), `ANDROID_KEY_ALIAS` = `intervaltimer`.
3. **Tag and push:** `git tag v1.0.0 && git push origin v1.0.0`. The Release workflow publishes `interval-timer-v1.0.0.apk` on the repo's Releases page.
4. **Obtainium:** add `https://github.com/bicanjirka/interval-timer` as an app. Uninstall the debug build from the phone first (different signing key).
5. **Google developer verification** (free; enforced from 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, globally from 2027). Google account needs 2-step verification and a payments profile. In the Android Developer Console (https://android.google.com/developerconsole/developers) choose the *limited distribution* account, open **Packages**, enter package `dev.juras.intervaltimer` and the SHA-256 fingerprint from CLAUDE.md; status shows *In review*. If Google asks for proof of ownership, it provides a snippet to add to the APK's assets folder: sign an APK with the release key, upload it, and wait for the confirmation email. Then authorise the phone through the QR code or link the console gives (up to 20 devices). Docs: https://developer.android.com/developer-verification/guides/limited-distribution
6. Not verifiable from here: the first Release run on GitHub (secrets are needed) and Obtainium updating from it.

## Later
- Features: widget or launcher shortcut, lock-screen extras, Wear OS haptics, Health Connect, JSON import/export, per-phase custom sounds.
