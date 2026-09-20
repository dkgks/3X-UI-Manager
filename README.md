# 3X-UI Manager · 简体中文版

> 这是 [yukh975/3X-UI-Manager](https://github.com/yukh975/3X-UI-Manager) 的**非官方汉化 fork**，由 [@dkgks](https://github.com/dkgks) 维护。
> 在 3x-ui 面板的原生 Android 客户端基础上补齐简体中文界面，并自动跟随上游版本发布。

用 REST API 管理 [3x-ui](https://github.com/MHSanaei/3x-ui) 面板的原生移动客户端 —— 仪表盘、入站、客户端（二维码分享）、节点、Xray 配置，手机上就能搞定。**支持多面板：保存多个面板，在同一个应用里切换。**

![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg) ![Platform: Android 7+](https://img.shields.io/badge/Android-7%2B-3DDC84?logo=android&logoColor=white) ![Made with Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose%20Material%203-4285F4)

🇬🇧 [English](README.en.md) · 🇷🇺 [Русский](README.ru.md) · 📝 [上游更新日志](CHANGELOG.md) · 📖 [用户手册](docs/3X-UI-MANAGER.en.md)

---

## 下载与安装

到本仓库的 [Releases](https://github.com/dkgks/3X-UI-Manager/releases) 下载最新 APK：

| 文件 | 说明 |
| --- | --- |
| `3x-ui-manager-standard-debug.apk` | 简体中文版，debug 自动签名，直接安装即可 |

安装须知：

- 首次安装需允许「安装未知来源应用」。
- **本版是 debug 签名，与上游 / F-Droid 官方版签名不同**，无法覆盖安装官方版本 —— 想换过来请先卸载官方版再装（卸载前记得保存面板信息和 API Token）。
- 应用内的「检查更新」指向**本仓库**的 Releases（已改为 `dkgks/3X-UI-Manager`），会下载汉化版而不是官方包；新版是否能直接覆盖安装取决于两次构建的签名是否一致 —— 若提示「应用未安装」，先卸载旧版再装新包。
- 系统要求：Android 7.0 及以上。

## 汉化内容

- 全部 **695 条**界面文案已翻译为简体中文（新增 `ZhStrings.kt` 语言表），覆盖仪表盘、入站、客户端、节点、Xray 配置编辑器、设置、提示与报错信息。
- 设置里的语言选择器新增 **中文** 一项；系统语言为中文时自动使用中文界面，其余情况保持英文。
- 日期/流量等单位跟随本地化（如剩余天数显示为「天」）。
- 汉化只涉及界面文案，不改动任何网络请求、面板 API 调用和数据处理逻辑。

## 功能一览

**仪表盘**

- 3 秒轮询的服务器状态：Xray 运行/停止 + 版本，一键重启 Xray（需确认）。
- CPU / 内存 / 磁盘（已用/总量）、在线客户端数、实时上下行速率、TCP/UDP 连接数、负载、运行时长、公网 IP。
- 点任意指标卡片可看历史曲线图（时间范围可切换，实时曲线最长 5 小时）。
- 本月流量（当前面板自身入站的代理流量，含统计起始日期）；点「在线」看当前连接中的客户端及其所属入站。
- 面板版本卡片；有新版本时可一键触发面板自更新。geo 规则文件（geoip.dat / geosite.dat 及 RU、IR 变体）可单独重新下载。

**入站（Inbounds）**

- 列表逐条启用/停用、流量用量/配额、客户端数量。
- 表单化编辑器（常规场景无需手写 JSON）：备注、端口、监听 IP、协议、启用状态；流量限制（GB）、重置周期、到期时间；传输层（tcp/ws/grpc/httpupgrade/xhttp/kcp）及对应参数；安全层 none / TLS（SNI）/ Reality（dest、serverNames、shortIds、指纹、公私钥）；Sniffing 开关与 destOverride；高级 JSON 区块编辑协议设置。
- 客户端列表在「客户端」页单独管理，保存入站时不会误动客户端。

**客户端（Clients）**

- 列表带在线状态点、流量、到期时间、最后在线；支持按邮箱搜索。
- 新建/编辑/删除：所属入站（多选，保存时挂载/卸载）、流量限制、IP 限制、重置周期、到期时间、Telegram ID、分组、备注。
- 分享面板：订阅二维码 + 链接，以及每条服务器链接各自的二维码 / 复制 / 分享。

**节点（多面板）**

- 远程面板在线状态、CPU/内存/延迟、入站与客户端数量、本月流量。
- 新增/编辑/删除：名称、地址、端口、协议、base path、API Token、TLS 校验模式、是否允许内网地址。

**Xray 配置编辑器**

- 出站：列表增删改排序 + 各协议表单（vless、vmess、trojan、shadowsocks、socks、http、freedom、blackhole、wireguard），带传输层与 TLS/REALITY；支持从 `vless://` 链接导入。
- 路由：规则（源/目标/入站 → 出站或 balancer，可排序）、balancer（策略/选择器/回退）、路由策略。
- DNS：开关、DNS 级选项、服务器（字符串或完整对象）、FakeDNS 池。
- 常规 / 日志：日志级别、路由策略、出站测试 URL、流量统计开关。
- 原始 Xray 配置：完整 JSON，表单覆盖不到的字段（Observatory、进阶 xHTTP、hysteria、reverse 等）的兜底入口。
- 所有编辑器都做整份配置回写，保留同级与未知字段。

**其他**

- 数据库备份/恢复：把整个面板数据库（设置、入站、客户端、Xray 配置）导出到文件，或从文件恢复；SQLite（`x-ui.db`）与 PostgreSQL（`x-ui.dump`）通吃。恢复前确认，恢复后重启 Xray（连接会短暂中断）。
- 管理员账号改密、API Token 管理（列出、创建时明文只显示一次、启用/停用、删除）、重启面板服务。
- 应用锁：可选的 4–8 位数字口令（+ 生物识别），仅保护已登录的面板界面。
- 英文 / 俄文 / 中文界面切换，无需重启。

## 使用前提

- 面板需运行 **3x-ui v3.3.0 或更新版本**（API Token 认证）。上游当前版本针对 **v3.4.x** 优化；面板停留在 v3.3.x 的用户请用上游 v0.3.23。
- 应用**仅支持 API Token（Bearer）登录**，没有账号密码模式。在面板 **设置 → 安全 → API Token** 里生成，粘进应用即可。
- 一个 3x-ui Token 等价于**完整管理员权限**（没有只读或分权限的 Token），请像保管密码一样保管它。
- Token 在设备上加密存储（EncryptedSharedPreferences，AES-256，密钥放在 Android Keystore）。
- 自签证书的面板可在连接页打开「跳过 TLS 校验」开关。

## 自动构建与发布

汉化版跟随上游发版，不需要人工干预：

1. GitHub Actions 每天 UTC 00:00 检查上游最新 Release（workflow：`.github/workflows/auto-publish-localized.yml`，也可手动触发）。
2. 若该版本还没汉化过：从上游 tag 建 `localized` 分支 → 依次套用 `patches/` 下的全部补丁（`zh-locale.patch` 界面汉化、`fork-updater.patch` 把应用内更新源指到本仓库）→ 构建 `assembleStandardDebug` → 推送分支并发布 Release，tag 为 `localized-<上游 tag>`（例如 `localized-v0.14.3`）。
3. 若补丁套不上（上游改了 i18n 或更新逻辑的结构），任务**明确失败**而不是发出半成品，此时需要人工更新对应的补丁文件。

## 自行构建

```bash
git clone https://github.com/dkgks/3X-UI-Manager.git
cd 3X-UI-Manager

# 方式一：直接构建 main 分支（已内置汉化）
gradle assembleStandardDebug

# 方式二：复现 CI 的汉化构建（上游 tag + 补丁）
git fetch --tags https://github.com/yukh975/3X-UI-Manager.git
git switch -C localized <上游 tag>
git apply patches/*.patch
gradle assembleStandardDebug
```

产物在 `app/build/outputs/apk/standard/debug/`。需要 JDK 17、Android SDK（platform 35 / build-tools 35.0.0），Gradle 8.10.2。

## 仓库与分支

| 分支 | 内容 |
| --- | --- |
| `main` | 上游代码 + 汉化提交 + 自动发布 workflow + `patches/` |
| `localized` | 当前发布版本的构建分支（上游 tag + 汉化补丁），由 CI 维护 |

上游项目：[MHSanaei/3x-ui](https://github.com/MHSanaei/3x-ui)（面板本身）、[yukh975/3X-UI-Manager](https://github.com/yukh975/3X-UI-Manager)（客户端原作者）。

## 许可与致谢

本项目沿用上游的 **MIT License**，版权归原作者 [Yuriy Khachaturian (yukh975)](https://github.com/yukh975) 所有，汉化与自动发布由 [@dkgks](https://github.com/dkgks) 添加。

这是**非官方**汉化版本，与原作者没有隶属关系。汉化相关的问题请提到本仓库的 Issues，**不要**去上游仓库提汉化相关的 issue。
