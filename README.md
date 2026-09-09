# SZCU Connect

个人安卓校园网连接工具。Kotlin + Jetpack Compose，Android 11 及以上。

支持多账号、每配置独立 SSID、四个运营商、手机本地加密保存、系统 Wi-Fi 面板兜底及 Dr.COM 认证。没有云端账号服务。

## 构建

安装 JDK 17 或 21、Android SDK Platform 36，在未跟踪的 `local.properties` 中填写 `sdk.dir`，或设置 `ANDROID_HOME`。

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

真实密码只在手机应用里输入。不要把签名密钥、个人账号、原始页面、日志、APK 或本地 SDK 配置提交到 Git。

首次连接可能需要精确位置、附近 Wi-Fi 权限以及系统连接确认。只有在系统确认连到目标 SSID 后才发送认证请求。

当前开发交付版本 `v0.1.3`，补齐学校已登录页和注销页解析，按“注销、确认、返回登录页、登录所选配置”连接；通过 Wi-Fi 实际访问百度后显示绿色成功，不添加自动连接建议。`v0.1.0` 已在小米 Android 16 / HyperOS 3.0 完成中国电信真实登录；新版验收状态见测试记录。交付为调试签名 APK；其他运营商及完整兼容矩阵以 `docs/TESTING.md` 为准。仅本地 Git 管理，不自动创建远程仓库。

- [安装与使用](docs/USAGE.md)
- [测试记录与待验收项目](docs/TESTING.md)
- [认证协议依据](docs/PROTOCOL.md)
- [视觉资源来源](docs/ASSETS.md)

本次按用户要求交付当前候选快照：63 项单元测试、Lint 和构建通过；绿色成功提示已确认，账号切换的注销问题仍未通过真机验收。最新参数顺序调整尚未安装到手机验证，详见测试记录。

提交检查后的源码后，可执行 `./scripts/package-debug.ps1 -Label 0.1.3` 构建并生成 `releases/` 下的 APK、源码提交号和 SHA-256 校验文件。发布目录不进入 Git；正式标签在现场验收后创建。
