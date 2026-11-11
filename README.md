# NavApp

Independent Android app for the UGV Beast / OpenBot-derived platform. NavApp owns its camera, sensor access, mapping, UI and ESP32 connection; it does not require the OpenBot app or runtime.

## Current implementation status — 0.3.0

### Implemented
- Kotlin + Jetpack Compose Android application shell.
- CameraX rear-camera preview.
- Independent ESP32 WebSocket adapter with conservative velocity clamps and stop command.
- Phone accelerometer and gyroscope listener.
- Log-odds 2D occupancy grid with ray integration, snapshots and map replacement.
- Handheld ARCore scanning activity: starts an ARCore session, enables automatic depth, renders the live camera feed, tracks camera pose, samples DEPTH16, and accumulates a 2D occupancy grid while the user walks.
- Depth-to-map projection uses ARCore camera intrinsics and tracked pose, with a provisional assumed phone height of 1.2 m to filter points by height. The current grid covers approximately 20 m × 20 m at 5 cm per cell.
- Conservative 8-connected A* path preview; unknown and occupied cells are blocked, including diagonal corner-cutting prevention.
- Versioned local map save/load.

### Not yet implemented / not safe to use for autonomy
- No loop closure, global relocalization or drift correction; long walks may produce a warped or duplicated floor plan.
- Floor height is estimated from the starting phone height (assumed 1.2 m), not measured from a detected floor plane; hold the phone around chest height and expect false hits/missed points until calibrated. The current 2D grid is about 20 m × 20 m; scan one floor at a time.
- Visual-inertial pose estimation, relocalization and loop closure.
- Camera-to-robot extrinsic calibration and camera pitch/height compensation.
- Live depth mapping and map quality/confidence display.
- Robot footprint inflation, dynamic obstacle tracking, local planner and MPC.
- ESP32 protocol validation against the actual firmware and hardware watchdog verification.
- Instrumentation/device testing on the OnePlus Nord CE 3.

The handheld scanner is an early on-device prototype, not a production floor-plan scanner. It uses ARCore tracking and depth, but has no loop closure or drift correction and uses a rough floor-height assumption. Inspect the live map and expect imperfections. A* only previews a route through cells marked known-free by the mapper.

## Build

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35

Open the repository in Android Studio and sync Gradle. Install on the phone and grant camera permission. A successful Android build has not yet been verified in this environment.

## ESP32 transport contract (provisional)

Default endpoint: `ws://192.168.4.1:81/`.

```json
{"type":"drive","v":0.12,"w":0.0,"ttl_ms":300}
{"type":"stop"}
```

`v` is linear velocity in m/s and `w` is angular velocity in rad/s. This is a provisional adapter contract, **not a claim about the existing ESP32 firmware**. Confirm the firmware's actual transport, endpoint and message schema before connecting motors. The ESP32 must independently enforce command expiry, speed/current limits and an emergency stop. The phone-side TTL is not a hardware watchdog.

## Coordinate conventions

- Occupancy grid: X right, Y forward; origin is the robot's map-frame start cell.
- `DepthProjector.Pose2D`: metres and yaw radians, with yaw zero facing +Y.
- `DepthProjector` expects ARCore-style DEPTH16 millimetres in a little-endian buffer and camera intrinsics in pixel units. It currently assumes a level camera aligned with the robot. Calibrate extrinsics and compensate for camera mounting pitch before using its output for navigation.

## Architecture

- `camera/`: CameraX preview
- `sensors/`: phone accelerometer and gyroscope listener
- `robot/`: independent ESP32 WebSocket transport
- `mapping/OccupancyGrid.kt`: log-odds 2D map
- `mapping/DepthProjector.kt`: depth-image to occupancy-ray adapter
- `navigation/AStarPlanner.kt`: conservative grid planner
- `persistence/MapStore.kt`: local versioned map persistence
- `MainActivity.kt`: field UI and manual control

## Safety and staged validation

1. For house scanning, install Google Play Services for AR if prompted, start “Walk & Scan My House”, and move slowly around each room while sweeping the camera across walls and openings.
2. Keep the phone roughly chest-height and avoid fast turns; pause if ARCore reports tracking loss.
3. Save the map before leaving the scanner.
4. For robot operation, verify ESP32 IP, transport and JSON schema with motors physically disconnected or the UGV lifted.
2. Verify ESP32-side watchdog, motor limits and physical E-stop independently of NavApp.
3. Validate camera and ARCore tracking/depth support on the target phone.
4. Calibrate camera intrinsics and camera-to-robot transform; check map orientation with known measured distances.
5. Test depth projection against stationary obstacles while the robot is immobilized.
6. Validate pose drift on a square/return-to-start trajectory before enabling mapping while moving.
7. Only then add local planning and closed-loop navigation. Stop on stale commands, Wi-Fi loss, localization loss or sensor failure.

Mapping must remain passive during early tests. Do not run autonomous drive using this build.
