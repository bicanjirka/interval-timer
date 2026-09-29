# TODO

## Verified on the Pixel 10 (Android 17, API 37) on 2026-09-29, over adb
- Debug build installs and launches; the routine list, get-ready (orange) and work (green) screens render on the real phone as they do in the JVM screenshots.
- Starting a routine runs `TimerService` as a foreground `mediaPlayback` service; the notification exists with 3 actions.
- Timing: segments change within ~30 ms of the schedule, 3-2-1 ticks fire, Pause freezes and Resume continues, backgrounding the app keeps the routine running.
- Screen off (`Dozing`): the wake lock is held, ticks and phase changes stay on the exact second across several segments, no `stalled` warnings, audio focus is requested and released around every cue and around the spoken phase name.
- Logging: the log file is written, and `adb pull` of the logs folder works without root.

## Still to check by ear, feel and hand (adb can't tell)
- **Sound is audible** with the screen off, at the app's own volume; spoken phase names are clear; ducking dips other audio and it returns, and with ducking off it doesn't dip.
- **Android 17 hardening:** no hardening violation was seen in the log with the screen off, but `cmd audio set-enable-hardening` doesn't exist on this build, so strict mode wasn't forced. Cues use `USAGE_MEDIA`. If sound ever fails while backgrounded, **Approach:** switch to `USAGE_ALARM` or add exact-alarm permission.
- **Vibration only** (**Where:** `service/CuePlayer.kt`): nothing is heard and each phase change is felt, also with the screen off and in silent/DND mode (alarm usage). Check that the pulse patterns are distinguishable.
- **Work until done:** the count-up runs, DONE starts the rest, the rest ends into the next set, the notification's button says Done during work.
- **Notification buttons** (Pause, Skip, Stop) work from the shade and the lock screen.
- **Keep screen on:** the screen stays lit on the running screen only.
- **Editor:** add, reorder, duplicate and delete blocks and phases; colours; per-round delta; leaving the screen saves.
- **Layout with sweaty hands:** buttons are easy to hit; digits readable from a distance.
- Speech and the start beep overlap slightly (speech starts 350 ms after the beep); tune `START_SPEECH_DELAY_MS` in `TimerService` if it sounds messy.

## Known gaps
- Process death mid-routine loses the running state (the service stops with the process). **Approach:** only if it happens in practice; persist the routine and start time and rebuild the engine.
- Doze exemption prompt not added. **Approach:** add only if the wake lock isn't enough over a long workout.
- The log records commands but not where they came from (notification, app or adb). **Approach:** add a source to the command intent if that ever matters.

## After the MVP runs on the phone
- Git remote, `.github/workflows/release.yml`, release keystore and signing (see CLAUDE.md); the `gh` CLI isn't installed yet.
- Register the package and signing key with Google's developer verification (limited distribution) before the sideloading rules apply.
- Later features: widget or launcher shortcut, lock-screen extras, Wear OS haptics, Health Connect, JSON import/export, per-phase custom sounds.
