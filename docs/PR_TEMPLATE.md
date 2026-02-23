# PR Title

<One-line, user-facing description>

## Background

- Ticket/Issue: <ID>
- Problem statement:
- Affected devices/builds:

## Changes

- What changed (high level):
- Why this approach:
- Alternatives considered (if any):

## Verification

### Build / Lint / Tests

- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew :app:lintDebug`
- [ ] `./gradlew test`

### Device Validation

- [ ] RK3566: cameraId=100 (face) opens reliably
- [ ] RK3566: cameraId=102 (hand) opens reliably
- [ ] Mode switching does not black-screen
- [ ] Image picking works for Downloads and external storage

### UX

- [ ] No overlapping UI; layout works on target landscape resolution
- [ ] Error states show actionable messages

## Notes / Risks

- Risk:
- Rollback plan:

## Screenshots / Recordings

- Attach:
