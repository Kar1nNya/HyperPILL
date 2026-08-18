# HyperPILL – Moondrop Pill 适配记录

> 目标设备：MOONDROP Pill / MD-OWS-003（含 Bocchi the Rock 联名款，蓝牙名称形如 `Pill Kita Ikuyo`）
> 协议来源：Moondrop Link APK `com.moondroplab.moondrop.moondrop_app` v2.24.2c 逆向

## 传输层

- 经典蓝牙 RFCOMM / SPP
- UUID：`00001101-0000-1000-8000-00805F9B34FB`
- 帧格式（高通 GAIA v3 RFCOMM，已从 App 内 `GaiaReader$Rfcomm` / `GaiaFormatter$Rfcomm` 确认）：

```
FF <version=01> <flags> <length[1|2]> <GAIA PDU> [checksum]
```

- `flags`：
  - bit0 = checksum（App 发送时关闭）
  - bit1 = length extension（payload > 255 时使用，电量命令不需要）
- `length` = GAIA PDU 中 **payload 的字节数**（不包含 4 字节 PDU 头）
- GAIA PDU：

```
<vendor:u16> <command:u16> <payload...>
```

- vendor = `0x001D`（高通 QTIL V3）
- command 位域：

```
feature[6:0] << 9 | type[1:0] << 7 | command[6:0]
type: 0=COMMAND, 1=NOTIFICATION, 2=RESPONSE, 3=ERROR
```

## 电量命令（Battery Plugin, feature=13）

| 命令 | command id | payload |
|---|---|---|
| 查询支持的电池 | 0 | 无 |
| 查询电量 | 1 | 电池 ID 列表 |

电池 ID：
- `0` 单耳设备
- `1` 左耳
- `2` 右耳
- `3` 充电盒

查询电量示例：

```
GAIA PDU: 00 1D 1A 01 00 01 02 03
RFCOMM:   FF 01 00 04 00 1D 1A 01 00 01 02 03
```

响应 payload（command type=RESPONSE, command=1）为重复的 `[batteryId, level]` 对：

```
00 64 01 5A 02 4B 03 3C
=> 单耳 100%, 左耳 90%, 右耳 75%, 充电盒 60%
```

支持的电池响应 payload 为电池 ID 列表，例如 `00 01 02 03`。

## 已实现

- `pods/GaiaProtocol.kt`：GAIA v3 编解码、RFCOMM 帧封装/解析、电量解析
- `pods/PillClient.kt`：RFCOMM 连接、读线程、命令发送
- `PillViewModel.kt` / `MainActivity.kt`：配对设备列表、连接、电量展示
- LSPosed 模块元数据（API 102）+ `HookEntry` 占位

## Moondrop App 跳转

- Moondrop Link 是 Flutter 应用，所有页面都在 `com.moondroplab.moondrop.moondrop_app/.MainActivity`
- 已尝试通过 `route` / `initial_route` / `initialRoute` extra 与 `moondrop://` data 直达 PEQ 页，均被忽略，未发现可用的 Android 深链 intent-filter
- 因此 HyperPILL 的“打开 Moondrop App”按钮改为显式打开主 Activity：`com.moondroplab.moondrop.moondrop_app/.MainActivity`

## 提示音（VOICE, feature=14）

- 命令：
  - `1` GET_CURRENT_VOICE_CONF
  - `2` SET_VOICE_CONF
- V2 payload 固定 3 字节：`[enabled, volume, index]`
  - `enabled`：0=关闭，1=开启
  - `volume`：0~100（Moondrop App 滑块显示百分比）
  - `index`：提示音组号/语音包索引（当前 HyperPILL 保留并回传，不提供选择 UI）
- 已实现：读取提示音开关/音量，开关切换，音量滑块（0~100%）

## 产品信息（BASIC feature=0）

逆向自 `V3BasicPlugin.COMMANDS` / `V3BasicPlugin.fetchDeviceInfo`：

| 命令 ID | 含义 | 响应 |
|---|---|---|
| 3 | GET_SERIAL_NUMBER | ASCII 文本（`TextData`） |
| 4 | GET_VARIANT | ASCII 文本（`TextData`） |
| 5 | GET_APPLICATION_VERSION | ASCII 文本（`TextData`） |
| 18 | GET_EARBUD_COLOR | 1 字节 |
| 19 | GET_EARBUD_LANG | 1 字节 |
| 20 | GET_EARBUD_SN_LEFT | ASCII 文本 |
| 21 | GET_EARBUD_SN_RIGHT | ASCII 文本 |

HyperPILL 已实现读取：固件版本（命令 5）、Variant（命令 4）、序列号（命令 3）、左右耳 SN（命令 20/21）。

Pill 身份表（来自 `products/all`）：

- `MOONDROP Pill`
- `MOONDROP PILL`（name=`PILL`）
- `Pill Gotoh Hitori` — Bocchi the Rock 联名
- `Pill Ijichi Nijika` — Bocchi the Rock 联名
- `Pill Yamada Ryo` — Bocchi the Rock 联名
- `Pill Kita Ikuyo` — Bocchi the Rock 联名
- `PANDAER Open Air Pill` — PANDAER
- `LAPLACE-OBA-Ⅱ` — 重返未来：1999 联名

App 通过蓝牙广播名匹配产品表 `name`，从而确定产品 UUID 与对应资源。
