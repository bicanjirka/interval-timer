# TODO

## Check on the phone (nothing below has run on a device yet)
Install with `./gradlew.bat installDebug` once Wireless debugging is paired.

- **Start and run:** tap a routine; the running screen appears in the phase colour, digits count down, Skip/Pause/Resume/Stop work, Stop asks for confirmation.
- **Screen off / lock:** with the screen locked, the routine keeps going, beeps stay on time, the notification shows the phase and its Pause/Skip/Stop buttons work.
- **Notification permission:** the prompt appears on first launch; if denied, the routine must still run.
- **Vibration only** (**Where:** `service/CuePlayer.kt`): with "Vibration only (silent)" nothing is heard and each phase change is felt, including with the screen off and with the phone in silent/DND mode (vibration uses alarm usage). Check that the pulse patterns are distinguishable.
- **Work until done:** the count-up runs, DONE starts the rest, the rest ends into the next set, the notification's button says Done during work.
- **Audio** (**Where:** `service/CuePlayer.kt`)
  - Beeps and spoken phase names follow the app's own volume slider (Settings has a test-sound button).
  - Ducking: music dips during a cue and comes back; with ducking off it doesn't dip.
  - **Android 17 background-audio hardening:** cues must play with the screen off. Check with `adb shell cmd audio set-enable-hardening throw`. Cues use `USAGE_MEDIA` (follows media volume). If they are blocked or the media stream is too coupled to the phone's volume, **Approach:** switch to `USAGE_ALARM` (follows alarm volume) or add the exact-alarm permission.
  - Speech and the start beep overlap slightly (speech starts 350 ms after the beep); tune `START_SPEECH_DELAY_MS` in `TimerService`.
- **Keep screen on:** the screen stays lit on the running screen, and only there.
- **Editor:** add, reorder, duplicate and delete blocks and phases; colours; per-round delta; leaving the screen saves.
- **Layout:** digits fill the screen at real size; buttons are easy to hit with sweaty hands.

## Known gaps
- Process death mid-routine loses the running state (the service stops with the process). **Approach:** only if it happens in practice; persist the routine and start time and rebuild the engine.
- No Compose previews or UI tests. **Approach:** add previews for each running-screen colour if layout tuning gets painful.
- Doze exemption prompt not added. **Approach:** add only if the wake lock isn't enough on the device.

## After the MVP runs on the phone
- Git remote, `.github/workflows/release.yml`, release keystore and signing (see CLAUDE.md); the `gh` CLI isn't installed yet.
- Register the package and signing key with Google's developer verification (limited distribution) before the sideloading rules apply.
- Later features: widget or launcher shortcut, lock-screen extras, Wear OS haptics, Health Connect, JSON import/export, per-phase custom sounds.
