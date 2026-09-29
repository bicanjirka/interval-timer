# TODO

## Verified on the Pixel 10 (Android 17, API 37) on 2026-09-29, over adb
- Debug build installs and launches; the routine list, get-ready (orange) and work (green) screens render on the real phone as they do in the JVM screenshots.
- Starting a routine runs `TimerService` as a foreground `mediaPlayback` service; the notification exists with 3 actions.
- Timing: segments change within ~30 ms of the schedule, 3-2-1 ticks fire, Pause freezes and Resume continues, backgrounding the app keeps the routine running.
- Screen off (`Dozing`): the wake lock is held, ticks and phase changes stay on the exact second across several segments, no `stalled` warnings, audio focus is requested and released around every cue and around the spoken phase name.
- Logging: the log file is written, and `adb pull` of the logs folder works without root. A whole session of testing produced no `W`/`E` lines and no crash.
- Work until done: count-up shows real time, DONE starts the 60 s rest (red, counting down), the rest runs into Round 2's count-up from 0:00. Stop asks for confirmation and stops.
- Vibration only: the system logged the vibrations (alarm usage, patterns as designed) for the test signal and for every cue of a routine, and the app took no audio focus. Sound mode was restored afterwards.
- Editor on the phone: typing works with the numeric keyboard, the total updates live, leaving saves.
- Bugs found on the phone and fixed: the first cue of a routine used default settings (a beep even in vibration-only mode) because settings loaded asynchronously; the on-screen keyboard covered the field being edited.

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

## After the MVP runs on the phone
- Git remote, `.github/workflows/release.yml`, release keystore and signing (see CLAUDE.md); the `gh` CLI isn't installed yet.
- Register the package and signing key with Google's developer verification (limited distribution) before the sideloading rules apply.
- Later features: widget or launcher shortcut, lock-screen extras, Wear OS haptics, Health Connect, JSON import/export, per-phase custom sounds.
