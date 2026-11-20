<div align="center">

<img src="desktop/src/main/resources/icon.png" width="120" alt="云析 YunX" />

# 云析 YunX · Windows 封装版

**一个基于原项目封装的 Windows 版**

自动跟随上游代码 → 自动编译 → 自动打包出免安装的 Windows 可执行程序 → 自动发布到 Releases

[⬇ 下载最新版](../../releases/latest) · [原项目（桌面端）](https://github.com/tidain/YunX-Desktop) · [原项目（安卓端）](https://github.com/CYQawa/YunX)

</div>

---

## 致谢

这个项目的**代码、设计和全部功能都不是我做的**。

感谢 **[tidain](https://github.com/tidain)** 写出 YunX 桌面版（Kotlin + Compose Multiplatform，从 0 到能用的完整 Windows 桌面应用），
感谢 **[CYQawa](https://github.com/CYQawa)** 写出安卓端与整套网盘 / 视频解析能力。

他们把这么完整的东西以 **AGPL-3.0** 开源出来、还自带一套可复现的打包脚本，这个仓库才可能存在。如果我哪里用错了、或者描述得不对，请原作者直接指出，我立刻改。

## 这是什么

本仓库是 [**YunX-Desktop（云析桌面版）**](https://github.com/tidain/YunX-Desktop) 的 **Windows 封装构建仓库**，只做三件事：

1. **同步**：每天自动拉取上游 `tidain/YunX-Desktop` 的最新代码（含新版本号）；
2. **构建**：在 GitHub Actions 的 Windows runner 上跑上游自带的 `portable-package.ps1`，编译出 `YunX-Desktop.exe`；
3. **发布**：打包成免安装 zip 挂到 [Releases](../../releases)，点开就能下。

**除下面两个文件外，本仓库代码与上游完全一致**，没有任何功能改动、没有加壳、没有二次打包收费：

| 文件 | 作用 |
|---|---|
| `README.md` | 就是本页（上游那份原文完整保留在 [`README.upstream.md`](README.upstream.md)） |
| `.github/workflows/sync-upstream-and-build.yml` | 同步上游 + 自动构建 + 发布 |

## 下载与使用

去 [**Releases**](../../releases/latest) 下载 `YunX-Desktop-portable-v<版本>.zip`：

1. 解压到任意目录（**别放 C:\Program Files，别在压缩包里直接双击**）
2. 双击 `YunX-Desktop.exe`
3. 第一次若弹「Windows 已保护你的电脑」→ **更多信息 → 仍要运行**（程序未做代码签名，开源项目普遍如此）

功能、网盘登录、视频解析、常见问题（含百度网盘风控警告、FFmpeg/Gopeed 引擎二段下载等）**请看原项目文档**：

- [功能清单](https://github.com/tidain/YunX-Desktop/blob/main/docs/FEATURES.md) ｜ [使用说明](https://github.com/tidain/YunX-Desktop/blob/main/docs/USAGE.md) ｜ [构建说明](https://github.com/tidain/YunX-Desktop/blob/main/docs/BUILD.md) ｜ [与安卓版的差异](https://github.com/tidain/YunX-Desktop/blob/main/docs/UPSTREAM-DIFF.md)

## 自动构建是怎么跑的

```
每天 05:00（北京时间）/ 手动点 Run workflow / 推送到 main
        │
        ├─ 同步上游 main（冲突一律保留本仓库的 README）
        ├─ 读 version.txt 拿到版本号，判断该版本是否已经发过 Release
        │        ├─ 已发过 → 结束（不浪费构建时长）
        │        └─ 没发过 → 继续
        ├─ Windows runner：JDK 17 + MSVC → portable-package.ps1 → release\YunX-Desktop\
        ├─ 7z 打成 YunX-Desktop-portable-v<版本>.zip（约 210MB）
        └─ 建 Release（tag = v<版本>）→ 附带 zip + 构建日志
```

想自己触发一次：进 **Actions → 同步上游并构建 Windows 便携版 → Run workflow**，勾上 `force` 可以强制重构建同一个版本。

> **两个要知道的限制**
> 1. GitHub 上 **fork 仓库的定时任务默认是关的**，需要你在 Actions 页点一次启用（本仓库已尝试自动开启，若发现没在跑，点一下就行）。
> 2. 仓库连续 **60 天没有活动**，GitHub 会自动暂停定时任务；届时手动 Run 一次即可恢复。

## 出问题了找谁

- **程序本身的 bug / 功能请求**：请去 [上游仓库提 issue](https://github.com/tidain/YunX-Desktop/issues) —— 那里有原作者和 6.9k star 的社区，比在这里问有效得多。
- **只是这个封装仓库的构建失败 / Release 下载不动**：在这里提 issue。

## 协议

与上游一致，**AGPL-3.0**（见 [LICENSE](LICENSE)）。如果你要再分发，请同样保留原作者署名并以 AGPL-3.0 开源。

---

> 本仓库是**非官方**构建，与原作者的官方发布（[tidain/YunX-Desktop/releases](https://github.com/tidain/YunX-Desktop/releases)）相比，唯一的区别是产物由这里重新编译一次。
> **如果上游有官方发布的安装包，请优先用官方那份。**
> 云析永远免费开源；任何花钱买到的「云析」都是二次打包的骗子版本。
