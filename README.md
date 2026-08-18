# HyperPILL

HyperPILL 是一个 LSPosed 模块，用于让 Moondrop Pil 耳机适配小米 HyperOS

Tips:本项目实现几乎全程 VibeCoding, 人工对素材和UI进行处理, 没有足够的资源测试其余SKU的可用性, 如别的SKU存在问题可以尝试通过AI修复, 仓库提供了Agent.md

## 功能

- 电量读取
- 增益切换
- EQ 切换
- 双设备连接开关
- 提示音开关 + 音量控制
- HyperOS 通知中心耳机控制
- 超级岛电量显示，支持各 SKU 左右耳独立图标

## 环境要求

- Android 15+
- 小米 HyperOS
- LSPosed API 102+

## 支持设备

| 产品 | UUID |
|---|---|
| MOONDROP Pill | `20f874df-7f71-4446-880c-95bbc39995d4` |
| PILL | `7afb7e3c-99c6-45be-b0a8-6adb8603643a` |
| Pill Gotoh Hitori | `fb36c2bb-845d-4e0a-9b83-193b046bc6cb` |
| Pill Ijichi Nijika | `91e6febd-d61b-4849-9c0f-5d4e9627700d` |
| Pill Yamada Ryo | `655903e7-046f-49d8-be63-bbadb3ea7881` |
| Pill Kita Ikuyo | `42b775b3-2781-47f2-95b1-86ef7de4f9bd` |
| PANDAER Open Air Pill | `3795b453-41f8-4f7b-aaa8-2709481a2f91` |
| LAPLACE-OBA-Ⅱ | `0767fc45-888d-4e99-b81d-c0566a42b4a2` |

## 引用的开源项目

- HyperOriG：https://github.com/KiriChen-Wind/HyperOriG
- OppoPods (by 1812z)：https://github.com/1812z/OppoPods
- OppoPods (by Leaf-lsgtky)：https://github.com/Leaf-lsgtky/OppoPods
- HyperPods：https://github.com/Art-Chen/HyperPods
- Miuix：https://github.com/compose-miuix-ui/miuix
- LibXposed API：https://github.com/LSPosed/LibXposed
