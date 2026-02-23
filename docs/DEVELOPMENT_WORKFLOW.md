# Development Workflow

## Environment

- Java: 17 (required by AGP 8.x)
- Build tool: Gradle wrapper

## Common Commands

- Clean:
  - `./gradlew clean`
- Build debug APK:
  - `./gradlew :app:assembleDebug`
- Lint:
  - `./gradlew :app:lintDebug`
- Unit tests:
  - `./gradlew test`

## Code Standards (Project)

- Follow existing patterns (Compose + MVVM + Hilt)
- Avoid `!!` in Kotlin; prefer safe calls and explicit error paths
- In `:app` use Timber for logging

## Device-Specific Requirements

- RK3566 dual cameras:
  - Face: `cameraId=100`
  - Hand: `cameraId=102`
- Do not rely on lensFacing heuristics for selection on RK devices

## Release Discipline

- Any UI change must be verified on the target landscape screen size
- Any camera/detection change must be verified on RK3566 device
