# Quality Gates

This document defines the minimum required checks for enterprise delivery.

## Required (Local)

1) Compile/build

- `./gradlew :app:assembleDebug`
- `./gradlew :app:compileDebugKotlin`

2) Lint

- `./gradlew :app:lintDebug`

3) Unit tests

- `./gradlew test`

## Required (Device)

If camera/detection/UI is touched, run `docs/DEVICE_REGRESSION_CHECKLIST.md` on RK3566.

## Recommended

- `./gradlew assembleRelease` (to catch minify/proguard differences when enabled)
- Verify memory and CPU on device while detection runs
