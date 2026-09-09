# 校园网认证适配依据

2026-09-09 使用用户授权的 ADB 与浏览器调试接口，只读检查 SZCU 登录页的 DOM、运行中的函数源码与公开配置。协议调查阶段没有读取输入框密码或提交认证；实现后由用户在应用内填写账号并完成电信登录。本文不保存手机 IP、MAC、设备序列号或账号。

## 当前环境

- 认证入口 `http://172.16.8.22/`，Dr.COM 模板 WZXY，IP 模式，现场模板索引 2。
- 浏览器函数调用链：`ee(1)` → `login.init` → `login.setISP` → `login.login` → `login.login_portal` → `login.portal_login` → `dr._jsonp` → `util._jsonp`。
- `page.loginMethod=1`，`accountPrefix=1`，`customPerceive=0`，手机 `term.type=2`。
- 当前 `enablev6=0`、`password_cut=0`，验证码输入框存在但隐藏。

## 协议

`GET http://172.16.8.22:801/eportal/?c=Portal&a=login`

参数：`callback`、`login_method=1`、`user_account`、`user_password`、`wlan_user_ip`、`wlan_user_ipv6`、`wlan_user_mac`、`wlan_ac_ip`、`wlan_ac_name`、`jsVersion`、`v`。

`user_account` 为 `,1,` + 不含运营商后缀的学工号 + 选项后缀：

| 运营商 | 实际选项值 |
| --- | --- |
| 中国电信 | `@telecom` |
| 中国移动 | `@cmcc` |
| 中国联通 | `@unicom` |
| 校园内网 | 空字符串 |

使用显示表单中的选项值，不使用页面内另一个旧 `carrier` JSON 中的 `@dx/@lt` 示例值。页面输入框名 `DDDDD/upass` 也不是实际 Portal 请求的参数名。

密码按原值 UTF-8 URL 编码，不 trim、不截断、不自创 MD5。当前 `jsVersion=3.3.3` 为协议版本，`fileVersion` 只是静态资源缓存版本。

学校网页为 GB2312。无 charset 响应头的外部脚本按 GB18030（兼容 GB2312）读取；明确的响应头或页面声明优先。否则运营商中文名称会乱码，模板校验会拒绝提交。

IP 来源优先为本次页面 `v46ip`，其次 `ss5`，必须与绑定 Wi-Fi 的 IPv4 一致。MAC 来源于页面 `ss4`/`olmac`；本次页面给出零值哨兵，不能改成手机出厂 MAC。当前根入口无 AC 查询参数，AC IP/name 为空。

成功响应 JSONP 内 `result=1` 或 `result="ok"`。严格剥离预期回调后解析 JSON，不 eval；服务器原始错误文字可能回显凭据，不直接展示。

## 网络边界

每次请求通过指定 `Network.openConnection` 发出，禁用系统代理、缓存和自动重定向；不将密码交给其他主机。不使用进程级网络绑定，不影响其他应用。HTTPS 204 检测不能走默认蜂窝网络。HTTP 仅为兼容已确认校园服务器，其他域名不开放明文。

Android 系统 Wi-Fi 面板及手动打开的浏览器属于用户操作兜底，应用不向浏览器自动传递账号。没有无障碍、Root 或 Shizuku 依赖。

## 模板校验

每次获取根页、`a41.js` 和 WZXY `config.js`，按 IP 范围选择首个启用的模板（`ipPageAry` 第 5 项为 1，第 6 项为认证方式 1）；再读取对应 `loginbox.js` 和 `mobile.js`。静态解析 `bodyContent` 字符串，不运行 JavaScript。

核对四种运营商选项、手机账号前缀、密码不截断、IPv4 模式、隐藏验证码及协议版本。出现未支持的模板或规则时，在提交凭据之前停止并提示手动认证。其他学校、VLAN/SSID 模板选择、不同协议版本不在当前适配范围内。
