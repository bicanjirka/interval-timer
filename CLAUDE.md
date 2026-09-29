# Interval Timer

Personal Android interval-timer app for workouts (Kotlin, Jetpack Compose, Material 3). Built for one Pixel phone, sideloaded and updated via Obtainium from GitHub Releases.

## Build environment
- JDK 25 (Temurin) at `C:\Users\juras\.jdks\temurin-25.0.4.1`. `JAVA_HOME` is set as a user env var; in an already-open shell set `$env:JAVA_HOME` explicitly. The Gradle daemon toolchain is pinned to 25 in `gradle/gradle-daemon-jvm.properties`.
- Gradle 9.6 wrapper, AGP 9.4, Kotlin 2.4, compileSdk/targetSdk 37, minSdk 35, package `dev.juras.intervaltimer`.
- Versions live in `gradle/libs.versions.toml`. Check that a version is real and current (Google Maven / Maven Central) before changing it.
- Windows + PowerShell: use `./gradlew.bat`.

## Commands
```
./gradlew.bat assembleDebug testDebugUnitTest    # run before every commit
./gradlew.bat testDebugUnitTest --tests "*TimerEngineTest.someSentenceName"
./gradlew.bat installDebug                       # needs Wireless debugging paired
```

## Working here
- Multi-step plan: commit after each step, each step green on its own (builds, tests pass). Only commit work that does.
- Commit subject lines are imperative mood, capitalized, no trailing period (`Add X`, not `added X`), and end with the Co-Authored-By line the harness specifies.
- Verify with the cheapest thing that can prove it: a JVM unit test for rules and timing → a Compose preview or screenshot for how a screen looks → the phone only for what no test can assert (foreground service surviving screen-off, audio ducking, keep-screen-on, notification, layout at real size). Never re-check on the phone what a test already asserts. One phone pass per feature, after its last step. Say plainly when something was not verified on the phone.
- Known gaps go in `TODO.md` (with **Where** and **Approach**), never an inline TODO. Closing a gap deletes its entry in the same commit.
- Comments only for a non-obvious *why*; don't restate the code. KDoc a type to say why it exists, especially when two types could be confused.
- `CLAUDE.md` holds constraints only - no history, no feature narrative. A change that makes a line here false fixes it in the same commit. A package with its own concurrency or lifecycle rules gets a short `CLAUDE.md` beside it.
- IDE metadata (`.idea/`, `*.iml`) is never committed; the Gradle files are the project model.
- Before large refactors or destructive actions, tell the user first. Never delete files you haven't looked at.
- Verify version numbers and Android API behaviour against current docs instead of relying on memory.

## Packages (`dev.juras.intervaltimer.*`)
Package by feature, never `utils`, `impl` or by layer (`dto`, `model`).
- `engine` - routine model and the timer state machine. Pure Kotlin.
- `service` - foreground service, audio cues and ducking, voice.
- `data` - routine persistence and settings.
- `ui` (+ `ui/theme`, one subpackage per screen) - Compose screens.
- `MainActivity` wires them together.

## Boundaries
- **The engine is pure Kotlin.** It owns all timing and phase logic, imports nothing from `android.*` or Compose, and starts no threads. New timer behaviour goes there, never in the service or a composable.
- **Time is injected.** The engine reads a `Clock`-like source of monotonic time; tests drive it with explicit instants. State is computed from elapsed time, never by counting ticks, so pause, resume, skip and a late tick stay exact. No `System.currentTimeMillis()` or `delay` in engine code.
- **The engine reports through one small interface** (a state flow or listener). If it needs something from the platform (sound, vibration), add a method to that interface rather than reaching for an Android type. Never widen it speculatively.
- **Service and UI adapt the engine, they don't reimplement it.** A composable gets an immutable state value and event lambdas (state hoisted); it never holds timing logic.
- **Depend on the narrowest thing.** A consumer needing one collaborator takes that type, not the whole service or repository.
- **Threading.** The engine state crosses threads as one immutable snapshot behind one `StateFlow`, read through one accessor. Publish finished objects: build a value, then assign. Service and UI collect on their own dispatchers; Compose state is only touched on the main thread.
- A routine saved by an older app version must still load. Any change to the persisted format either stays compatible or ships a migration.

## Code style
- **Values** are immutable `data class`es (or `@JvmInline value class`es) with `val`s only and named factories (`Phase.work(40.seconds)`, `Rest.none()`). Operations return new values; nothing mutates in place.
- **"Nothing" is a value in the domain model** (`Rest.none()`, an empty list), not `null`. Nullable types only at the platform edge, where Android hands them in, and they are normalised there. Optional rest is one such value, so the engine never branches on whether rest exists.
- Values that combine get an algebra: an operation and an identity, so they fold with `fold`/`reduce` (`Routine` total duration, remaining time).
- A class with 5+ properties gets a narrow factory and grows by `copy` or fluent `withX`, never a wider constructor.
- **Classes** take collaborators by constructor injection into `private val`s; one constructor, no init step. Small classes, one idea; past about 150 lines a class holds two ideas. Interfaces of 1-5 methods; a `fun interface` when there is one method.
- Inherit only for a closed set of variants: a `sealed interface` with `final` leaves. Otherwise compose.
- Branch on type with an exhaustive `when` over a sealed type, never `is` chains and never an `else` that hides a new case.
- A variation point is an enum constant or a strategy object carrying its collaborators, not a `when` on it inside the operation.
- Names put the role noun last and say what the thing does (`SegmentedTimerEngine`, `DuckingAudioCuePlayer`); a strategy is the prefix.
- Streams/collection operators to transform, `fold`/`reduce` to combine; no accumulator loop over a mutable local.
- Never expose a mutable collection; expose `List` and return a copy or a new value.
- Randomness and time come from injected sources, never `Math.random()` or the wall clock.
- Catch `Exception`, never `Throwable`, and rethrow `CancellationException`. Log or rethrow; never swallow or `printStackTrace`. Don't use exceptions as control flow.
- Prefer Kotlin idiom over Java translation: named arguments for literals, `Duration` over raw millis, extension functions only where they read better than a method.

## UI
Dark and high contrast by default, one look, no dynamic colour in the MVP. Composables are small and stateless: state in, events out, previews for each state that looks different (running per phase colour, paused, finished). Tap targets on the running screen are large; digits scale to fill the screen. Text comes from `strings.xml`.

## Tests
- JVM unit tests (`app/src/test`) for everything in `engine`; instrumented tests only where a real device is the point.
- Method names are behaviour sentences in `lowerCamelCase` (`skippingTheLastRestEndsTheRoutine`): no `test`/`should` prefix, no underscores, no backticked names.
- Assertions read as sentences; arrange / act / assert are three blocks separated by blank lines, no `// given` comments.
- Hand-written fakes named by role (`FakeClock`, `FakeCuePlayer`), no mocking framework. Reuse a fake, never copy it.
- Deterministic and clock-free: drive the engine with explicit instants; never sleep or wait on wall time.
- One behaviour per test; parameterise only genuine input tables. Test the composition (engine with a real routine), not only each part alone.
- New engine rules must be provable in `TimerEngineTest`. Build wide values through their factory, not a full positional constructor.

## CI/CD (after the MVP runs on the phone)
- Release workflow `.github/workflows/release.yml` on tags `v*`: `actions/setup-java` (temurin 25), `gradle/actions/setup-gradle`, `./gradlew assembleRelease`, then a GitHub Release with the APK.
- Signing: one release keystore signs every release. Keep it outside the repo and back it up. In CI store it base64-encoded in GitHub Secrets and read passwords from env vars in `signingConfigs`. Never commit keystore or passwords.
- Google developer verification for sideloaded apps applies from 2026-09-30 in some countries and globally from 2027; register the package name and signing key under the free limited-distribution account. Verify the exact steps in Google's docs when reaching this step.
