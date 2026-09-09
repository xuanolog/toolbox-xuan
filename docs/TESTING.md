# 测试记录

日期：2026-09-09。设备：小米 2407FRK8EC，Android 16 / HyperOS 3.0。

## 已完成

| 检查 | 结果 |
| --- | --- |
| `testDebugUnitTest` | 40 项通过，0 失败 |
| `lintDebug` | 通过，0 错误；保留固定依赖版本提示、静态 Logo 长路径及 KTX 风格建议等非阻断告警 |
| `assembleDebug` / `assembleDebugAndroidTest` | 构建成功 |
| APK 安装与首屏 | 真机通过，修复深色界面的状态栏对比度 |
| 配置新增、编辑、删除 | 虚构账号真机 UI 测试通过，未点击连接；小米测试页面需 ADB 前台启动辅助 |
| Keystore 与配置持久化 | 真机通过：两个虚构配置保存/读回、修改、密文不含明文；仅清理测试专用文件及密钥 |
| 真实校园网只读访问 | 真机通过：指定 Wi-Fi 上获取入口、IP、脚本、IP 模板及运营商/验证码/密码规则；未提交账号 |
| 已有 Wi-Fi/网络绑定 | 真机识别 `SZCU-313-5G`，读取认证流量走该 `Network`；未使用默认蜂窝网络 |
| 协议和状态机 | 单元测试覆盖四运营商、编码、JSONP、模板变化、已有会话、不重试、网络切换、取消及外网未确认 |
| 中国电信真实账号登录 | 用户在手机自行填写账号密码后反馈“运营商：电信，显示连接成功”；未向开发者提供凭据 |
| 登录后的系统网络状态 | ADB 复核：目标 Wi-Fi 为 `VALIDATED`，无 `CAPTIVE_PORTAL` 标记；检查时全局 `mobile_data` 设置值为 0 |
| 退到桌面后的连接 | ADB 返回桌面后再次检查，仍连接目标 Wi-Fi，保持 `VALIDATED` 且无认证门户标记 |
| 系统 Wi-Fi 接口可用性 | 小米系统能解析 Wi-Fi 面板及保存网络 Intent 对应 Activity；实际从其他 Wi-Fi 切换尚未实测 |

## 验收范围与未测试项目

本版完成 Android 16 / HyperOS 3.0、中国电信真实账号登录验收；账号由用户在手机中输入。其他情况不扩大宣称为通过。

- 其他没有实际账号的运营商：只完成协议映射测试，未实测认证。
- 首次用户授权、系统保存 Wi-Fi/网络面板切换、目标热点不在附近：需后续现场交互确认；权限在部分自动化测试中由 ADB 授予。
- 同时开启移动数据时的端到端登录、长时间稳定性尚未实测。网络绑定已通过代码检查和真实只读请求验证。
- Android 11–15 的实机兼容性未测试。

`v0.1.0` 作为个人使用首版发布，验收范围仅为上述已通过项。调试签名 APK 的源码提交与 SHA-256 位于发布目录中的 `release-manifest.json` 和 `SHA256SUMS.txt`。

## 复现

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class cn.edu.szcu.connect.ProfileUiTest cn.edu.szcu.connect.test/androidx.test.runner.AndroidJUnitRunner
```

小米若限制测试框架启动前台页面，在另一个终端执行：

```powershell
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10008000 -n cn.edu.szcu.connect/.MainActivity
```

只读校园网测试（先完成 Wi-Fi 权限授权并接入校园热点；测试期间保持应用前台）：

```powershell
adb shell am instrument -w -e class cn.edu.szcu.connect.DeviceSmokeTest -e campusProbe true cn.edu.szcu.connect.test/androidx.test.runner.AndroidJUnitRunner
```

若测试进程启动后应用退到后台，在另一个终端重新打开应用。`campusProbe` 默认关闭；明确开启后也不会提交登录或读写真实配置。

## 联调修复依据

- 补齐紧接 `<script>` 标签的变量解析，避免误判认证页。
- 修复学校 GB2312 页面/外部脚本无 charset 时的继承编码，避免运营商名称乱码。新增 3 项编码回归测试。
- 为 Android 11/12 的 NetworkCallback 分支声明准确 API 限制；编辑页面使用 `LocalActivity`，修复 Lint 错误。
- 升级 tracing 依赖以兼容 Compose 真机测试。原始测试输出和设备截图仅保留在 Git 忽略目录中。
