# tools · 微云小工具

主程序（exe / apk）之外的两个轻量工具，用的是同一套逆向出来的接口。

## weiyun-via.user.js —— 浏览器脚本

打开 `share.weiyun.com/xxx` 时右下角自动出面板，列出文件，给「直链」和「下载」两个按钮。

**Via 浏览器**：菜单 → 脚本 → `+` → 导入本文件 → 启用 → 刷新分享页
**其他浏览器**（Kiwi / X / Yandex / 360 极速等）：装 Tampermonkey / Violentmonkey 后导入。

已知：直链在 `*.weiyun.com` CDN 上，跨域所以浏览器会忽略文件名，手机上还可能直接打开文件。
想要原文件名 + 断点续传，点「直链」复制到 ADM / IDM / 迅雷。

## weiyun_share.py —— 命令行

```bash
python weiyun_share.py list https://share.weiyun.com/xxxxxxxx     # 列出文件与目录
python weiyun_share.py url  https://share.weiyun.com/xxxxxxxx     # 只取直链（JSON）
python weiyun_share.py get  https://share.weiyun.com/xxxxxxxx ./out  # 全量下载（递归子目录）
```

只用标准库，Python 3.8+ 直接跑。

## 这两个工具为什么能免登录

微云网页未登录点「普通下载」会弹扫码，那是**前端** `localAuthorize()` 在发请求前就 `return` 了 ——
请求根本没发出去，不是服务端要求登录。实测服务端接口是匿名的：

1. `GET https://share.weiyun.com/{id}` 的 HTML 里，`window.syncData` 已经带全部文件清单；
2. `POST /webapp/json/weiyunShareNoLogin/WeiyunSharePartDownload`（cmd `12023`，appid **`30113`**，`g_tk` 取分享页下发的 `wyctoken`）匿名返回 `https_download_url`；
3. 大文件服务端额外下发 `FTN5K`，须以 `Cookie: FTN5K=…` 带上，否则直链 401。

细节（含踩过的三个坑）见仓库根目录的 README 与源码注释。
