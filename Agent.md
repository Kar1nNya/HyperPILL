# Agent.md — HyperPILL

**Project**: Moondrop Pill / OBA HyperOS LSPosed module  
**Package**: `com.karin.hyperpill`  
**UI**: Jetpack Compose + Miuix `0.9.4-rc01`  
**Last Updated**: 2026-08-18

---

## Repository Layout

This directory is the publishable Android project root.

```
app/
  src/main/java/com/karin/hyperpill/
    MainActivity.kt
    PillViewModel.kt
    pods/            # GAIA protocol, RFCOMM client, system controller
    hook/            # LSPosed entry, config, log, system hooks
    ui/              # Compose screens and navigation
    utils/           # notifications, icons, system API helpers
  src/main/res/      # resources, icons, drawables
gradle/
build.gradle.kts
settings.gradle.kts
gradlew
gradlew.bat
```

Do not commit machine-specific files such as `local.properties`, `signing.properties`, `*.jks`, `build/`, `.gradle/`, `.idea/`.

---

## Build

### Requirements

- JDK 21+
- Android SDK 37
- Gradle wrapper (included)

### Commands

```powershell
./gradlew :app:assembleDebug
```

Install to a connected device:

```powershell
./gradlew :app:installDebug
```

Output APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

If a local proxy is required for dependency resolution, set `GRADLE_OPTS` accordingly; do not hard-code it in the repo.

---

## Runtime Environment

- Android 15+ (`minSdk 35`, `targetSdk 37`)
- Xiaomi HyperOS
- LSPosed / compatible Xposed framework
- Recommended scope:
  - `com.android.systemui`
  - `com.android.bluetooth`
  - `com.xiaomi.bluetooth`
  - `com.milink.service`
  - `com.android.settings`

---

## No-Network Constraint

The app must not perform any network requests.

- No `INTERNET` permission
- No community/EQ server requests
- No remote image loading
- Developer avatars are bundled as local drawables

---

## UI Architecture

- Root navigation uses Miuix `NavDisplay` (`top.yukonga.miuix.kmp.nav.core`).
- Routes:
  - `Route.Main` — Home / About tabs
  - `Route.Config` — device configuration secondary menu
  - `Route.Debug` — debug / device spoofing secondary menu
- Main tabs use `HorizontalPager` + Miuix `NavigationBar`.
- Config and Debug are NavDisplay secondary routes; Miuix owns transitions and back handling.
- `BackHandler` is only active on `Route.Main`:
  - About tab → animate pager back to Home
  - Home tab → first back shows a toast, second back within 2s exits
- Do not use custom predictive-back gestures outside NavDisplay.
- Keep hidden pages out of composition; NavDisplay composes entries only when needed.

### Debug / Spoof Device

- On About page, tap the version number 3 times within 2 seconds to reveal the "Debug" button.
- `DebugPage` lists `PillProducts.all`.
- Selecting a product writes `PillUiState.spoofedDeviceName`.
- Spoofing affects:
  - Home status card (shows connected + spoofed device name)
  - Config page device image / title / band logo
  - No real Bluetooth connection is made
- `effectiveConnected = state.connected || state.spoofedDeviceName != null`.

---

## Key Source Files

| File | Role |
|---|---|
| `app/src/main/java/com/karin/hyperpill/pods/GaiaProtocol.kt` | GAIA v3 frame/PDU encode/decode, command builders, parsers |
| `app/src/main/java/com/karin/hyperpill/pods/PillClient.kt` | RFCOMM/SPP client, reader thread, request/set wrappers |
| `app/src/main/java/com/karin/hyperpill/pods/PillSystemController.kt` | Runs in Bluetooth process, connects GAIA, broadcasts state |
| `app/src/main/java/com/karin/hyperpill/PillViewModel.kt` | App UI state, connect/disconnect, actions, spoof state |
| `app/src/main/java/com/karin/hyperpill/MainActivity.kt` | Activity entry |
| `app/src/main/java/com/karin/hyperpill/ui/MainScreen.kt` | Miuix NavDisplay root |
| `app/src/main/java/com/karin/hyperpill/ui/HomePage.kt` | Home screen |
| `app/src/main/java/com/karin/hyperpill/ui/DeviceConfigPage.kt` | Device configuration screen |
| `app/src/main/java/com/karin/hyperpill/ui/AboutPage.kt` | About / developers / references / hidden debug entry |
| `app/src/main/java/com/karin/hyperpill/ui/DebugPage.kt` | Device spoofing screen |
| `app/src/main/java/com/karin/hyperpill/ui/navigation/HyperPillNav.kt` | Routes, Navigator, LocalNavigator |
| `app/src/main/java/com/karin/hyperpill/utils/DeviceIconProvider.kt` | Per-SKU main/L/R icon resolution + diagnostic logs |
| `app/src/main/java/com/karin/hyperpill/utils/HyperPillNotificationUtil.kt` | Notification-center headset control |
| `app/src/main/java/com/karin/hyperpill/utils/FocusIslandUtil.kt` | Focus Island battery display |
| `app/src/main/java/com/karin/hyperpill/hook/HookEntry.kt` | LSPosed entry and per-package hook loading |
| `app/src/main/java/com/karin/hyperpill/hook/SystemHooks.kt` | System UI / Bluetooth / MiLink / Settings hooks |
| `app/src/main/java/com/karin/hyperpill/hook/HookContext.kt` | Hook helper + Log utility |
| `app/src/main/java/com/karin/hyperpill/hook/ConfigManager.kt` | Shared preferences / module config |

---

## GAIA v3 Protocol

### Transport

- RFCOMM / SPP
- SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`
- Vendor ID: `0x001D`

### RFCOMM frame

```
FF <version=01> <flags> <length> <GAIA PDU>
```

### GAIA PDU

```
<vendor:u16> <command:u16> <payload...>
```

### Command layout

```
command = feature[6:0] << 9 | type[1:0] << 7 | command_id[6:0]
```

- type `0` = command, `2` = response

### Pill features

| ID | Name |
|---|---|
| 0 | BASIC |
| 1 | EARBUD |
| 5 | MUSIC_PROCESSING |
| 6 | UPGRADE |
| 13 | BATTERY |
| 14 | VOICE |
| 15 | DAC_GAIN |
| 20 | ONEBRINGTWO |
| 21 | BT_ADDRESS |

---

## Interface Map

### BASIC (0)

- `1` GET_SUPPORTED_FEATURES
- `2` GET_SUPPORTED_FEATURES_NEXT
- Response: `hasMore(1 byte)` + `[featureId, version]` pairs
- Serial / Variant / firmware / SN / BT address are intentionally not requested.

### BATTERY (13)

- `0` GET_SUPPORTED_BATTERIES
- `1` GET_BATTERY_LEVELS
- Battery IDs: `0` single, `1` left, `2` right, `3` case

### MUSIC_PROCESSING (5) — EQ

- `0` GET_EQ_STATE
- `1` GET_AVAILABLE_EQ_PRE_SETS
- `2` GET_EQ_SET
- `3` SET_EQ_SET
- Presets: `0=Reference`, `1=Bass+`, `2=Bass-`, `63=Custom`

### DAC_GAIN (15)

- `1` GET_GAIN
- `2` SET_GAIN
- Gain: `0=High`, `1=Mid`, `2=Low`

### VOICE (14)

- `1` GET_CURRENT_VOICE_CONF
- `2` SET_VOICE_CONF
- Payload: `[enabled, volume, index]`

### ONEBRINGTWO (20)

- `1` GET_STATE
- `2` SET_STATE
- `3` GET_TIMEOUT
- `4` SET_TIMEOUT
- Timeout values are minutes: `5 / 10 / 30 / 60`

### BT_ADDRESS (21)

- `1` GET_BT_ADDRESS
- Not currently requested by the app.

---

## Supported Devices

| Product | UUID |
|---|---|
| MOONDROP Pill | `20f874df-7f71-4446-880c-95bbc39995d4` |
| PILL | `7afb7e3c-99c6-45be-b0a8-6adb8603643a` |
| Pill Gotoh Hitori | `fb36c2bb-845d-4e0a-9b83-193b046bc6cb` |
| Pill Ijichi Nijika | `91e6febd-d61b-4849-9c0f-5d4e9627700d` |
| Pill Yamada Ryo | `655903e7-046f-49d8-be63-bbadb3ea7881` |
| Pill Kita Ikuyo | `42b775b3-2781-47f2-95b1-86ef7de4f9bd` |
| PANDAER Open Air Pill | `3795b453-41f8-4f7b-aaa8-2709481a2f91` |
| LAPLACE-OBA-Ⅱ | `0767fc45-888d-4e99-b81d-c0566a42b4a2` |

Product matching is in `app/src/main/java/com/karin/hyperpill/pods/PillProduct.kt`.

---

## Icon System

- Main icons: `app/src/main/res/drawable-nodpi/pill_*.png`
- Left/right icons: `app/src/main/res/drawable-nodpi/pill_*_l.png` / `pill_*_r.png`
- Band logo: `app/src/main/res/drawable-nodpi/band_logo.png`
- `DeviceIconProvider` maps product UUID → main / left / right drawable.
- Diagnostic logs use tag `HyperPILL-Icon` and include:
  - device name
  - matched UUID
  - matched product name
  - fallback flag
  - resolved resource name

---

## Logging

- `Log.n` — normal important logs
- `Log.i/d/v` — debug-only logs
- There is no user-facing log-level selector in the current UI.
- `Log` refreshes `ConfigManager` from preferences before each output.

---

## Referenced Open Source GPL3.0

- HyperOriG: https://github.com/KiriChen-Wind/HyperOriG
- OppoPods (by 1812z): https://github.com/1812z/OppoPods
- OppoPods (by Leaf-lsgtky): https://github.com/Leaf-lsgtky/OppoPods
- HyperPods: https://github.com/Art-Chen/HyperPods
- Miuix: https://github.com/compose-miuix-ui/miuix
- LibXposed API: https://github.com/LSPosed/LibXposed

## 许可证
GNU General Public License v3.0