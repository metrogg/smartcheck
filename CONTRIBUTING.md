# Contributing Guide (SmartCheck)

This project is intended for enterprise delivery. Changes must be traceable, reviewable, and verified.

## 1) Workflow (Local Repository)

- Track work with a ticket/issue ID (even if it's a text file or spreadsheet for now).
- Create a branch per change:
  - `feature/<ticket>-<short-name>`
  - `fix/<ticket>-<short-name>`
  - `hotfix/<ticket>-<short-name>`
- Keep changes small and focused. Avoid mixing unrelated refactors with bug fixes.

## 2) Commit Conventions

- Prefer short, clear commits. One commit = one logical change.
- Commit message format (recommended):
  - `fix(camera): select cameraId 100/102 on RK3566`
  - `feat(hand): add still-image detection flow`
  - `refactor(ui): simplify HandCheck layout`

## 3) Pull Request Standard (Before GitHub)

Even without GitHub, every change should ship with a PR-like description.

- Create a file in the ticket record (or paste in chat) using `docs/PR_TEMPLATE.md`.
- Include screenshots/recordings for UI changes.

## 4) Quality Gates (Must Pass)

Run these locally before handing off:

- Build:
  - `./gradlew :app:assembleDebug`
- Kotlin compile:
  - `./gradlew :app:compileDebugKotlin`
- Lint:
  - `./gradlew :app:lintDebug`
- Unit tests:
  - `./gradlew test`

Notes:
- Android Gradle Plugin requires Java 17.
- Native/JNI modules may require NDK/CMake depending on what you touched.

## 5) Device Regression (RK3566)

If your change affects camera/detection/UI flows, validate on a RK3566 device:

- Camera selection:
  - Face uses Camera2 `cameraId=100`
  - Hand detection uses Camera2 `cameraId=102`
- Start/stop stability: open, switch modes, return from background, no black screen.
- Performance: detection latency is acceptable; no UI jank.

Use `docs/DEVICE_REGRESSION_CHECKLIST.md` as the acceptance checklist.

## 6) Security and Privacy

- Never commit secrets (API keys, credentials, `.jks`, `.pem`, etc.).
- Do not persist user images/features unless explicitly required and reviewed.
- Keep permissions minimal.
