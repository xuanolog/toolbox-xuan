# toolbox-xuan

个人工具箱仓库，收集自用的校园网相关工具：一个在电脑浏览器里自动登录的 Chrome 扩展，一个在手机上连接与管理的安卓应用。

## 目录结构

```
toolbox-xuan/
├── campus-auto-login/       # 校园网自动登录 Chrome 扩展
└── szcu-connect-android/    # SZCU Connect 安卓校园网连接工具（当前主仓库）
```

## campus-auto-login

校园网自动登录 Chrome 扩展（Manifest V3，`v1.0.0`），在电脑端自动填写账号并登录校园网。

- 自动登录，断网自动重连（可设置检测间隔）
- 多账号配置，支持电信 / 移动 / 联通
- 登录成功或失败通知
- 一键注销
- 配置导入 / 导出

安装：打开 `chrome://extensions/`，开启右上角「开发者模式」，点击「加载已解压的扩展程序」，选择本目录下的 `campus-auto-login/`。

详细说明见 [campus-auto-login/README.md](campus-auto-login/README.md)。

## szcu-connect-android

SZCU Connect，安卓校园网连接工具。Kotlin + Jetpack Compose，Android 11 及以上。

- 多账号，每个配置可绑定独立 SSID
- 电信 / 移动 / 联通 / 校园网 四个运营商
- 手机本地加密保存（Keystore），没有云端账号服务
- 系统 Wi-Fi 面板兜底，Dr.COM 认证

当前为 `v0.1.3` 候选快照，交付调试签名 APK。构建需要 JDK 17 或 21、Android SDK Platform 36：

```powershell
cd szcu-connect-android
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

文档：[项目说明](szcu-connect-android/README.md) · [安装与使用](szcu-connect-android/docs/USAGE.md) · [测试记录与待验收项目](szcu-connect-android/docs/TESTING.md) · [认证协议依据](szcu-connect-android/docs/PROTOCOL.md) · [视觉资源来源](szcu-connect-android/docs/ASSETS.md)

## 仓库说明

- `szcu-connect-android/` 由独立仓库 `Android-szcu-network` 以子树合并（subtree）方式整体引入，原提交历史完整保留在本仓库中。
- 真实密码只在手机应用或浏览器里输入。签名密钥、个人账号、原始页面、日志、APK 以及本地 SDK 配置不进入 Git。
