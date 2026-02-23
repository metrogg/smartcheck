# SmartCheck AI - AGENTS.md

This file is the primary repo guide for agentic coding tools operating in this repository.

## Project Snapshot

SmartCheck is an Android AIoT morning inspection app for industrial devices. Major capabilities:
- Face recognition (SeetaFace2 via `:face-sdk` JNI)
- Temperature measurement via serial/hardware abstractions
- Hand foreign-object detection (RKNN + OpenCV + JNI in `:hand-sdk`)

Tech stack: Kotlin, Jetpack Compose, MVVM, Room, Hilt, Coroutines/Flow, CameraX, JNI/C++.

Modules:
- `:app` Android application (`com.smartcheck.app`)
- `:hand-sdk` Android library + native (RKNN) (`com.smartcheck.sdk`)
- `:face-sdk` Android library + native (SeetaFace2) (`com.smartcheck.sdk.face`)

## Repo Rules (Cursor/Copilot)

No Cursor rules found (`.cursor/rules/`, `.cursorrules`).
No Copilot instructions found (`.github/copilot-instructions.md`).
Follow this `AGENTS.md` and existing code conventions.

## Build / Lint / Test Commands

Prereqs:
- Java 17 is required (AGP 8.x; app sets `sourceCompatibility = 17`, `kotlinOptions.jvmTarget = 17`).
- On Windows use `gradlew.bat`; on macOS/Linux use `./gradlew`.
- Native code builds require Android NDK + CMake (libraries configure CMake 3.22.1).

Common builds:
- Clean: `./gradlew clean`
- App debug APK: `./gradlew :app:assembleDebug`
- App release APK: `./gradlew :app:assembleRelease`
- Build all modules (debug): `./gradlew assembleDebug`

Lint:
- Lint all: `./gradlew lint`
- Lint app debug: `./gradlew :app:lintDebug`

Unit tests (JVM, fastest):
- All unit tests: `./gradlew test`
- App unit tests: `./gradlew :app:testDebugUnitTest`
- Single test class:
  `./gradlew :app:testDebugUnitTest --tests "com.smartcheck.app.SomeTest"`
- Single test method:
  `./gradlew :app:testDebugUnitTest --tests "com.smartcheck.app.SomeTest.someMethod"`

Instrumented tests (on device/emulator):
- All connected tests: `./gradlew :app:connectedDebugAndroidTest`
- Single class:
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartcheck.app.SomeInstrumentedTest`
- Single method:
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartcheck.app.SomeInstrumentedTest#someMethod`

Notes:
- There is no configured ktlint/detekt/spotless in Gradle; rely on standard Kotlin/Android Studio formatting.
- Avoid running tasks under `third_party/` unless you intentionally work there.

## Code Style Guidelines

### Kotlin: formatting, imports, types

- Formatting: 4 spaces; keep lines <= ~120 chars; prefer trailing commas where it improves diffs.
- Avoid `!!`. Use `?.`, `?:`, early returns, and explicit error paths.
- Prefer explicit types on public APIs and state holders (e.g., `val uiState: StateFlow<UiState>`).
- Prefer immutable collections in APIs (`List`, `Set`); mutate locally only.

Imports:
- Prefer explicit imports for non-UI code.
- Star imports are already used in UI files (e.g., `androidx.compose.runtime.*`, `androidx.compose.material3.*`, `androidx.room.*`). Preserve local style: do not churn imports just to change `*` to explicit.
- Do not introduce unused imports; keep file ordering consistent with IDE defaults.

Naming:
- Packages: lowercase.
- Classes/interfaces/composables: `PascalCase`.
- Functions/vars: `camelCase`.
- Constants: `UPPER_SNAKE_CASE` (`const val`).
- Files: match primary type/composable name.

### Architecture: Compose + MVVM + Hilt

- UI stays in `app/ui/**` and is as dumb as possible.
- Business logic and state live in `app/viewmodel/**` using `StateFlow` and unidirectional data flow.
- Data access goes through repositories (`app/data/repository/**`) and Room (`app/data/db/**`).
- Use Hilt for wiring (`@HiltAndroidApp` in `app/App.kt`, `@HiltViewModel` for VMs, `@Module` in `app/di/AppModule.kt`).
- Avoid manual singleton holders in `:app`; prefer DI.

Coroutines/Flow:
- Use `viewModelScope` for UI-driven work; choose dispatcher intentionally (`Dispatchers.Default` for CPU, `Dispatchers.IO` for blocking I/O).
- Treat `CancellationException` as a normal control path; do not log it as an error.
- Prefer `stateIn`/`asStateFlow` for exposing state.

Compose:
- Reusable composables take `modifier: Modifier = Modifier`.
- Side effects: use `LaunchedEffect`/`DisposableEffect`; do not start long-running work directly in the composable body.
- CameraX analyzers must be off the main thread; keep analysis throttled (see `DualCameraPreview.kt`).

### Logging, errors, and user-visible failures

- In `:app`, use Timber (`Timber.d/i/w/e`). Do not introduce `Log.*` or `println` there.
- In SDK/library modules (`:hand-sdk`, `:face-sdk`), `Log.*` is currently used (no Timber dependency). If you touch these modules, keep logging consistent within the module.
- Prefer handling errors with:
  - a logged diagnostic (tag/message + exception)
  - a safe return value
  - and (in `:app`) a UI state update or user prompt when appropriate.

### Hardware- and native-specific rules

- RKNN hand detection only works on Rockchip devices (RK3566 family). Guard init and runtime usage:
  - `app/App.kt` and `app/di/AppModule.kt` detect Rockchip via `Build.HARDWARE/BOARD/PRODUCT`.
  - Non-RK devices should skip init and UI should degrade gracefully.
- JNI and native code:
  - Keep JNI glue thin (`hand-sdk/src/main/cpp/hand_detector_jni.cpp`, `face-sdk/src/main/cpp/face_sdk_jni.cpp`).
  - Always release resources (`AndroidBitmap_unlockPixels`, local refs) and avoid leaks.
  - C++ standard is C++17 (see module `build.gradle.kts` and CMake).

## Agent Workflow Expectations

1. Read relevant files before changing behavior; follow existing patterns in that directory/module.
2. Do not add new dependencies unless explicitly requested.
3. Do not rewrite formatting across unrelated files.
4. Be careful with `third_party/**` and generated/native build outputs (e.g., `.cxx/**`): avoid editing them.
5. When tests/build fail, fix the issue using the error output; if JDK mismatch, ensure Java 17 is used.
