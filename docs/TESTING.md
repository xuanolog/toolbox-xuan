# 测试记录

日期：2026-09-09。设备：小米 2407FRK8EC，Android 16 / HyperOS 3.0。

## v0.1.2 修复验证

- 56 项 JVM 测试通过，含“Wi-Fi 可访问外网但在线列表为空时仍先注销再登录”、页面与列表冲突、同账号免登录、注销失败不提交密码、取消与超时回归。构建、Lint 通过（Android 11–12 的清理建议 API 已增加兼容分支）。
- 真机只读协议对照：RADIUS 与内核状态均返回 0；页面里出现的成功字符串属于通用脚本配置，不能据此宣称已登录。随后绑定 Wi-Fi 的百度探测可达，默认网络为蜂窝，说明判定不应依赖默认网络或单独依赖在线列表。
- 旧自动连接建议清除调用成功；清理后手机实际不再连接该建议网络，已请用户在 WLAN 面板手动选择校园网。
- 真机注销检查尚未完成：第一次被旧在线断言阻止，移除该错误前置断言后因目标 Wi-Fi 已断开而未能发送注销请求。没有把这些失败记录当成注销成功。
- 最终绿色成功提示、真实注销后换账号、移除建议后的完整连接流程仍待用户恢复校园 Wi-Fi 后验收。保持现有账号文件，不卸载主应用；发布目录记录最终源码和 APK 校验值，不打未完成验收的正式标签。

## v0.1.1 修复与验收状态

- 自动测试：53 项 JVM 测试，覆盖同账号免登录、不同/未知账号先注销、失败或仍在线时禁止登录、注销后的新参数读取、取消、网络切换、密码超时不重试，以及会话/探测/系统状态组合与 30 秒复查超时。
- `testDebugUnitTest lintDebug assembleDebug` 构建检查通过；Lint 0 错误、4 项原有非阻断警告。
- 首次 v0.1.1 候选已使用原调试签名 ADB 覆盖安装，设备读回 `versionCode=2`、`versionName=0.1.1`，原 `profiles.vault` 文件仍存在，未卸载主应用或清除数据。小米更新后重新撤销定位和附近设备权限，联调时已恢复这些原有权限。
- 候选真机只读会话探测通过：`authenticated=false`、`identityAvailable=false`、`wifiProbe=true`、`wifiValidated=true`、`defaultCellular=false`。这证明探测地址和系统联网判断不能单独代替校园会话检查；并不证明移动数据开启时的完整修复验收已经完成。测试没有读取账号配置或提交认证/注销。
- 已直接核实学校公开脚本中的注销调用链与请求参数，并验证在线列表结构、成功页参数来源和离线响应；未通过电脑代用户提交真实密码。
- 待用户现场验收：移动数据开启且起初未连接 Wi-Fi 的完整连接；两个真实账号之间的注销/登录；同账号免登录；退出新版应用后继续联网。其他运营商、首次授权与热点不在附近等未新增实测结果。

当前仅交付候选版本，不创建 `v0.1.1` 正式验收标签。最终 APK、源码提交和 SHA-256 位于忽略的 `releases/` 目录；用户验收后再记录结果和创建标签。以下为保留的 v0.1.0 测试历史。

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
