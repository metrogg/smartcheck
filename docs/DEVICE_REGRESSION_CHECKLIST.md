# Device Regression Checklist (RK3566)

Target: industrial tablet/device (landscape), Rockchip RK3566.

## Camera

- [ ] App requests camera permission and handles denial gracefully
- [ ] Face camera binds to Camera2 `cameraId=100`
- [ ] Hand camera binds to Camera2 `cameraId=102`
- [ ] Switching camera/mode does not freeze or black-screen
- [ ] Returning from background resumes preview reliably

## Hand Detection (Realtime)

- [ ] Realtime detection starts and updates results
- [ ] No permanent spinner (detection job completes)
- [ ] Overlay aligns with preview (no mirrored or offset skeleton/boxes)

## Still Image Detection

- [ ] Pick image from `Download/` shows files and can load images
- [ ] Pick image from gallery/other folders works
- [ ] Detection produces result and does not crash
- [ ] Large images do not OOM (scaled)

## Performance

- [ ] UI remains responsive (no input lag)
- [ ] Thermal/CPU acceptable during continuous run (record if possible)

## Logs / Diagnostics

- [ ] Logs contain cameraId, mode, and errors with stack traces
