<div align="center">

<img src="desktop/src/main/resources/icon.png" width="120" alt="云析 YunX" />

# 云析 YunX · Windows 版（含腾讯微云）

**在 YunX-Desktop 基础上增加「腾讯微云分享解析」的 Windows 构建**

[⬇ 下载最新版](../../releases/latest) · [原项目（桌面端）](https://github.com/tidain/YunX-Desktop) · [原项目（安卓端）](https://github.com/CYQawa/YunX) · [上游 README 原文](README.upstream.md)

</div>

---

## 致谢与立场

这个项目的**代码、设计和绝大部分功能都不是我做的**。

- 感谢 **[tidain](https://github.com/tidain)** 写出 YunX 桌面版（Kotlin + Compose Multiplatform）
- 感谢 **[CYQawa](https://github.com/CYQawa)** 写出安卓端与整套网盘 / 视频解析能力

两者均以 **AGPL-3.0** 开源。本仓库是它们的下游：**只新增了「腾讯微云」这一家网盘的支持**，其余功能、代码、UI 全部来自原作者。描述若有不准确，请原作者直接指出。

> **云析永远免费开源。** 如果你是在任何地方「花钱买到」的，说明你被骗了，请退款。

---

## 本版新增：腾讯微云

上次问「云析能不能解析微云」的答案是**不能**（上游 10 家网盘里没有微云）。本版把它补上了，而且是**免登录**的。

| 能力 | 说明 |
|---|---|
| 分享链接解析 | `https://share.weiyun.com/{id}`，几百 KB 到大文件都支持 |
| 目录浏览 | 分享的是文件夹时，可以一层层点进去 |
| 直链下载 | 走 Range 分片并发 + 断点续传（微云 CDN 支持 `Accept-Ranges`） |
| 提取码 | 链接带 `?pwd=` 自动识别；需要码的分享会提示填写 |
| 登录 | **不需要**。微云分享本身是匿名链路 |

### 怎么用

1. 打开「解析」页，粘贴微云分享链接
2. 点「开始解析」→ 列出文件
3. 点文件 → 弹出「下载直链」→ 选目录 → 开始下载

### 顺手做了个浏览器脚本

不想装客户端？仓库里 [`tools/weiyun-via.user.js`](tools/weiyun-via.user.js) 是一个油猴 / Via 浏览器脚本：打开微云分享页时右下角自动出现面板，列出文件、给「直链」和「下载」两个按钮。装法见 [`tools/README.md`](tools/README.md)。

命令行也能用：[`tools/weiyun_share.py`](tools/weiyun_share.py)（纯标准库）

```bash
python weiyun_share.py list   https://share.weiyun.com/xxxxxxxx
python weiyun_share.py url    https://share.weiyun.com/xxxxxxxx
python weiyun_share.py get    https://share.weiyun.com/xxxxxxxx ./out
```

---

## 本仓库相对上游改了什么

**源码**（只碰与「加一家网盘」相关的地方）：

| 文件 | 改动 |
|---|---|
| `data/network/WeiyunConstants.kt` | 新增：域名 / UA / `appid=30113` / cmd 常量 |
| `data/network/WeiyunApi.kt` | 新增：分享页解析 → 列目录 → 取直链 |
| `data/repository/WeiyunResolveRepository.kt` | 新增：实现 `ShareResolveRepository` |
| `data/network/ShareLinkParser.kt` | `SharePlatform` 加 `WEIYUN` + 链接正则 + parse 分支 |
| `data/download/DownloadPlatform.kt` | 加 `WEIYUN` 常量 |
| `ui/viewmodel/ResolveViewModel.kt` | 注入、凭证、`currentRepo()` 分发、平台名、免登录白名单、默认目录 |
| `ui/MainScreen.kt` | 装配 `WeiyunApi` / `WeiyunResolveRepository` |
| `ui/screens/ResolveScreen.kt` · `ui/clipboard/ClipboardLinkPopup.kt` | 两处穷尽 `when` 补分支 |

**不碰**：账号、数据库、备份 —— 微云免登录，`AccountDirectory` / `AppDatabase` / `SecureAccountDaos` / `AuthBackupManager` 全部无需改动。

**CI / 构建**：

| 改动 | 原因 |
|---|---|
| 上游同步的「补丁回放名单」纳入上面的文件 | 原 workflow 冲突兜底是 `git reset --hard` + 只回放 README，会把源码补丁整体丢掉 |
| 新增「校验微云补丁仍在」步骤 | 上游重构可能让补丁「merge 成功但注册行消失」，构建前 grep 断言 |
| tag 改成 `v<版本>-wx` | 与上游 `v<版本>` 区分命名空间，补丁迭代也能再发版 |
| `YUNX_USE_MIRROR` 开关 + CI 设 `false` | 阿里云 Maven 镜像对 `org.jetbrains.compose.desktop:*-windows-x64` 返回 **502**，Runner 上直接编译失败 |

---

## 下载与使用

去 [**Releases**](../../releases/latest) 下载 `YunX-Desktop-portable-v<版本>-wx.zip`：

1. 解压到任意目录（**别放 `C:\Program Files`，别在压缩包里直接双击**）
2. 双击 `YunX-Desktop.exe`
3. 首次若弹「Windows 已保护你的电脑」→ **更多信息 → 仍要运行**（未做代码签名，开源项目普遍如此）

视频预览 / 磁力下载所需的 FFmpeg、Gopeed 不随包发布，首次使用时在「设置 → 引擎管理」里下。

其余功能（账号登录、视频解析、磁力、浏览器插件等）**请看原项目文档**：
[功能清单](https://github.com/tidain/YunX-Desktop/blob/main/docs/FEATURES.md) ｜ [使用说明](https://github.com/tidain/YunX-Desktop/blob/main/docs/USAGE.md) ｜ [与安卓版的差异](https://github.com/tidain/YunX-Desktop/blob/main/docs/UPSTREAM-DIFF.md)

---

## 微云支持的边界（丑话说前面）

1. **不支持转存到微云个人盘** —— 那需要登录账号，本版只做分享直链下载。UI 上「保存到网盘」对微云会提示暂不支持。
2. **空壳分享**：分享方删了文件但分享链接还在时，会显示空列表（服务端如此）。
3. **需要提取码的分享**：链接不带 `?pwd=` 时要手动填。
4. **上游同步**：本仓库每天跟随上游。若上游大改 `ShareLinkParser.kt`，当前策略是保留我方版本（不会丢微云），但上游那次改动需要人工合并。

---

## 自动构建

```
每天 05:00（北京时间）/ 手动 Run workflow / 推送到 main
        │
        ├─ 同步上游 main（冲突保留我方补丁文件）
        ├─ 校验微云补丁仍在（少了就直接失败，不产出残缺包）
        ├─ 读 version.txt，判断 v<版本>-wx 是否已发布
        ├─ Windows runner 跑上游 portable-package.ps1 → 编译 + jlink + jpackage
        └─ 打包 zip → 发布 Release
```

## 授权

[AGPL-3.0](./LICENSE)。代码版权归 tidain / CYQawa；本仓库的改动同样以 AGPL-3.0 开放。
