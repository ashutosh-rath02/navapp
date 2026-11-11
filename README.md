# NavApp

Independent Android navigation and mapping app for a phone-mounted differential-drive UGV.

> **Status: early prototype.** Camera preview, phone sensor access, occupancy-grid primitives, depth projection adapter, A* planning, map persistence, and an ESP32 WebSocket transport are included. Full ARCore visual-inertial SLAM and autonomous driving are not yet implemented or validated.

## Stack
- Kotlin, Jetpack Compose, CameraX
- Android sensor APIs
- Occupancy grid + DEPTH16 projection adapter
- A* grid planner
- Local map persistence
- WebSocket transport for ESP32

## Build
Open this project in Android Studio with Android SDK installed and let Gradle sync. A local Android build has not yet been verified.

## Hardware / safety
The default WebSocket endpoint is `ws://192.168.4.1:81/`. The JSON command format in `RobotLink` is a placeholder and **must be matched to the actual ESP32 firmware before motor control is enabled**. Keep motor output disabled during mapping tests. The ESP32 must enforce its own command timeout, speed limits, and physical emergency stop. Stop on Wi-Fi loss, stale commands, sensor failure, or localization loss.

## Current limitations
- No integrated ARCore session / tracked camera pose pipeline yet.
- No calibrated camera-to-robot extrinsics or validated metric depth projection.
- No loop closure, relocalization, or long-term SLAM.
- No local obstacle avoidance controller or autonomous motor execution.

See the source packages under `app/src/main/java/me/ashutosh/navapp`.
