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

开发阶段安装调试 APK；最终版本和实测范围以 `docs/TESTING.md` 为准。仅本地 Git 管理，不自动创建远程仓库。
