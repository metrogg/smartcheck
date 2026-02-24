# SmartCheck AI - AGENTS.md

This file is the primary repo guide for agentic coding tools operating in this repository.

## Repo Rules (Cursor/Copilot)

- No Cursor rules found (`.cursor/rules/`, `.cursorrules`).
- No Copilot instructions found (`.github/copilot-instructions.md`).
- Follow this `AGENTS.md` and local conventions; do not churn formatting/imports.

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

## Build / Lint / Test Commands

Prereqs:
- Java 17 is required (AGP 8.x; modules set `sourceCompatibility = 17`, `kotlinOptions.jvmTarget = 17`).
- On Windows use `gradlew.bat`; on macOS/Linux use `./gradlew`.
- Native builds require Android NDK + CMake (CMake 3.22.1 configured in `:hand-sdk`, `:face-sdk`).

Build:
- Clean: `./gradlew clean`
- Build all (debug): `./gradlew assembleDebug`
- App debug APK: `./gradlew :app:assembleDebug`
- App release APK: `./gradlew :app:assembleRelease`

Lint:
- Lint all: `./gradlew lint`
- Lint app debug: `./gradlew :app:lintDebug`

Unit tests (JVM):
- All unit tests: `./gradlew test`
- App unit tests: `./gradlew :app:testDebugUnitTest`
- Face SDK unit tests: `./gradlew :face-sdk:testDebugUnitTest`
- Hand SDK unit tests: `./gradlew :hand-sdk:testDebugUnitTest`

Run a single unit test:
- Single class:
  `./gradlew :app:testDebugUnitTest --tests "com.smartcheck.app.SomeTest"`
- Single method:
  `./gradlew :app:testDebugUnitTest --tests "com.smartcheck.app.SomeTest.someMethod"`

Instrumented tests (device/emulator):
- All connected tests: `./gradlew :app:connectedDebugAndroidTest`
- Single class:
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartcheck.app.SomeInstrumentedTest`
- Single method:
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartcheck.app.SomeInstrumentedTest#someMethod`

Troubleshooting:
- If native build fails, confirm NDK is installed and Gradle sees CMake 3.22.1.
- If you see CRLF/LF warnings on Windows, avoid reformatting unrelated files; keep diffs minimal.
- `fr_2_10.dat` is tracked via Git LFS (`face-sdk/src/main/assets/fr_2_10.dat`). If the model is a tiny text pointer file, run `git lfs pull`.

Notes:
- No ktlint/detekt/spotless configured; use Android Studio formatting.
- Avoid running tasks under `third_party/` unless intentionally working there.

## Code Style Guidelines

Kotlin formatting:
- 4 spaces; aim for <= ~120 chars; prefer trailing commas where it improves diffs.
- Prefer early returns and small helpers over deep nesting.
- Avoid `!!`; use `?.`, `?:`, `require/check`, or explicit error states.

Imports:
- Non-UI code: prefer explicit imports.
- UI files already use star imports (`androidx.compose.*`, `androidx.room.*` in some files). Preserve local style; do not churn imports just to “clean up”.

Types and APIs:
- Prefer explicit types on public APIs and state holders (`val uiState: StateFlow<UiState>`).
- Use immutable collections in APIs (`List`, `Set`). Mutate locally only.
- Prefer sealed types/`enum class` for UI state and navigation identifiers.

Naming:
- Packages: lowercase.
- Classes/interfaces/composables: `PascalCase`.
- Functions/vars: `camelCase`.
- Constants: `UPPER_SNAKE_CASE` (`const val`).
- File names: match the primary type/composable.

Error handling and logging:
- `:app` uses Timber (`Timber.d/i/w/e`). Do not introduce `Log.*`/`println` there.
- `:hand-sdk`/`:face-sdk` use `Log.*`. Keep module-consistent logging.
- Treat `CancellationException` as normal control flow; do not log it as an error.
- Prefer: log diagnostic + safe return value + UI state update/user prompt.

Coroutines/Flow:
- UI work goes through ViewModels; use `viewModelScope`.
- Choose dispatcher intentionally: `Dispatchers.Default` for CPU (ML), `Dispatchers.IO` for blocking IO (DB/files/serial).
- Expose state via `StateFlow` (`asStateFlow`, `stateIn`).

Compose:
- Reusable composables take `modifier: Modifier = Modifier`.
- Side effects via `LaunchedEffect`/`DisposableEffect`; never start long-running work directly in composable bodies.
- CameraX analyzers must be off main thread; throttle analysis to keep UX responsive (see `app/.../DualCameraPreview.kt`).

Architecture (MVVM + Hilt + Room):
- UI in `app/ui/**` (dumb), logic/state in `app/viewmodel/**`, data in `app/data/**`.
- Hilt wiring: `@HiltAndroidApp` in `app/App.kt`, `@HiltViewModel` for VMs, modules in `app/di/**`.
- Room entities/dao in `app/data/db/**`; repositories in `app/data/repository/**`.

Hardware/native specifics:
- RKNN hand detection runs only on Rockchip (RK3566 family). Guard init/usage; non-RK devices must degrade gracefully.
- JNI glue stays thin (`hand-sdk/src/main/cpp/hand_detector_jni.cpp`, `face-sdk/src/main/cpp/face_sdk_jni.cpp`).
- Always release native resources (e.g., `AndroidBitmap_unlockPixels`, local refs) and avoid allocations in hot loops.
- C++ standard is C++17.

## Agent Workflow Expectations

1. Read relevant files before changing behavior; follow conventions in that module/dir.
2. Do not add new dependencies unless explicitly requested.
3. Keep diffs focused; do not reformat unrelated code.
4. Avoid editing generated/native build outputs (e.g., `.cxx/**`, `build/**`).
5. When tests/build fail, use the error output to fix; ensure Java 17 is used.
