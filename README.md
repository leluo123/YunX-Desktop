<div align="center">

<img src="desktop/src/main/resources/icon.png" width="110" alt="云析" />

# 云析 YunX · 微云版

**在原项目基础上增加「腾讯微云分享解析」的构建**

Windows 与 Android 两端，同一套解析逻辑

[⬇ Windows 版](https://github.com/leluo123/YunX-Desktop/releases/latest) ｜
[⬇ Android 版](https://github.com/leluo123/YunX/releases) ｜
[上游桌面端](https://github.com/tidain/YunX-Desktop) ｜
[上游安卓端](https://github.com/CYQawa/YunX)

</div>

---

## 关于这个项目

[云析 YunX](https://github.com/CYQawa/YunX) 是一个开源的网盘分享链接解析下载工具，支持夸克 / UC / 迅雷 / 百度 / 139 / 123 / 115 / 光鸭 / 蓝奏云等网盘，以及抖音 / B站 / 小红书等平台的视频解析。

本项目是它的**下游构建**：在原作者代码的基础上，增加了**腾讯微云**这一家网盘的支持，并把两端分别打包成可直接运行的 Windows 程序和 Android 安装包。

代码版权归原作者所有，本项目遵循上游的 **AGPL-3.0** 协议开放。

---

## 两个产物

| 平台 | 从这里下载 | 说明 |
|---|---|---|
| **Windows** | [Releases](https://github.com/leluo123/YunX-Desktop/releases/latest) | `YunX-Desktop-portable-v<版本>-wx.zip`，解压即用，无需安装 |
| **Android** | [Releases](https://github.com/leluo123/YunX/releases) | `YunX-<版本>-wx.apk`，7 MB 左右 |

- 源码：[Windows 端](https://github.com/leluo123/YunX-Desktop) ｜ [Android 端](https://github.com/leluo123/YunX)
- 两个仓库都会跟随上游更新，并自动构建新版本。

---

## 本版新增：腾讯微云

| 能力 | 说明 |
|---|---|
| 分享链接解析 | `https://share.weiyun.com/{id}` |
| 目录浏览 | 分享的是文件夹时可以一层层点进去 |
| 下载 | Range 分片并发 + 断点续传（微云 CDN 支持 `Accept-Ranges`） |
| 提取码 | 链接带 `?pwd=` 时自动识别 |
| 登录 | **不需要**，微云分享本身是匿名链路 |

### 怎么用

粘贴分享链接 → 解析 → 列出文件 → 点文件取直链下载。和用其它网盘没有区别。

### 两个轻量工具

除了主程序，`tools/` 里还有两个小工具，适合临时用：

- **[`weiyun-via.user.js`](tools/weiyun-via.user.js)** —— 浏览器脚本。打开微云分享页时右下角自动出现面板，列出文件并给「直链」「下载」两个按钮。支持 Via 浏览器和各类油猴管理器。
- **[`weiyun_share.py`](tools/weiyun_share.py)** —— 命令行工具，纯标准库。

```bash
python weiyun_share.py list https://share.weiyun.com/xxxxxxxx
python weiyun_share.py get  https://share.weiyun.com/xxxxxxxx ./out
```

安装与原理见 [`tools/README.md`](tools/README.md)。

---

## 相对上游做了哪些改动

改动集中在「接入一家新网盘」需要碰的地方，其余功能、界面、代码均保持上游原样。

**新增**：`WeiyunConstants.kt`、`WeiyunApi.kt`、`WeiyunResolveRepository.kt`

**修改**：`ShareLinkParser`（链接识别）、`DownloadPlatform`（平台标识）、`ResolveViewModel`（分发与凭证）、`MainScreen`（装配）、`ResolveScreen` / `ClipboardLinkPopup`（平台名）

**未改动**：账号体系、数据库、备份 —— 微云免登录，这些都用不到。

构建方面做了几处维护性调整：上游同步时保留本地补丁、构建前校验补丁存在、发版 tag 加 `-wx` 后缀与上游区分、CI 使用官方 Maven 源。

---

## 使用提示

- **Windows**：解压到任意目录后双击 `YunX-Desktop.exe`。首次运行若被 SmartScreen 拦截，选「更多信息 → 仍要运行」即可（程序没有代码签名）。
- **Android**：本版使用临时自签名证书，因此**每次构建的签名不同**，覆盖安装失败时先卸载旧版。如需稳定升级，可在仓库 Secrets 里配置自己的签名证书。
- 视频预览与磁力下载所需的 FFmpeg / Gopeed 不随包发布，首次使用时在「设置 → 引擎管理」里下载。

---

## 已知限制

1. 微云**不支持转存到个人盘**（转存需要登录账号），本版只做分享直链下载。
2. 分享方删除文件但分享链接仍在时，会显示空列表。
3. 需要提取码的分享需手动填写。
4. 桌面端每天跟随上游同步；若上游大幅改动解析入口文件，本地补丁会保留，但上游那次改动需要人工合并。

---

## 致谢与协议

本项目的代码主要来自：

- **[CYQawa/YunX](https://github.com/CYQawa/YunX)** —— 安卓端与全部网盘 / 视频解析能力
- **[tidain/YunX-Desktop](https://github.com/tidain/YunX-Desktop)** —— 桌面端（Kotlin + Compose Multiplatform）

感谢两位原作者把项目以 AGPL-3.0 开源出来。本项目对其代码的再分发与修改同样遵循 **AGPL-3.0**，相关源码在上述链接中完整公开。

如果本项目的描述有误，或你对改动有异议，欢迎直接提出，会尽快调整。
