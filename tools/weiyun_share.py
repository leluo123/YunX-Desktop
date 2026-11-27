#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
腾讯微云分享解析 / 下载 CLI（免登录）

用法：
    python weiyun_share.py list   <分享链接或ID>            # 列出文件与目录
    python weiyun_share.py url    <分享链接或ID> [文件名]     # 只取直链（含 FTN5K）
    python weiyun_share.py get    <分享链接或ID> [输出目录]   # 全量下载（递归子目录）

说明：
  - 分享页 HTML 里服务端直出 window.syncData（含文件清单），无需登录、无需执行 JS；
  - 直链走 weiyunShareNoLogin/WeiyunSharePartDownload（cmd 12023，appid 30113）；
  - 大文件服务端下发 FTN5K，必须作为 Cookie 带上，否则 401；
  - 直链支持 Range，可断点/多线程。

只用标准库，无需第三方依赖。
"""
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request

BASE = "https://share.weiyun.com"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
APPID_WEB_SHARE = 30113
PROTO = "weiyunShareNoLogin"


# ---------------- 基础请求 ----------------

def _opener(cookiejar):
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookiejar))
    op.addheaders = [("User-Agent", UA), ("Accept-Language", "zh-CN,zh;q=0.9")]
    return op


def parse_share_id(text):
    text = text.strip()
    m = re.search(r"share\.weiyun\.com/([A-Za-z0-9_-]{4,40})", text)
    if m:
        return m.group(1)
    m = re.fullmatch(r"[A-Za-z0-9_-]{4,40}", text)
    return text if m else None


def fetch_share(share_id):
    """GET 分享页 -> 解析 window.syncData -> 返回 (shareInfo, gtk, opener)。"""
    import http.cookiejar
    cj = http.cookiejar.CookieJar()
    op = _opener(cj)
    html = op.open(f"{BASE}/{share_id}", timeout=30).read().decode("utf-8", "replace")
    gtk = next((c.value for c in cj if c.name == "wyctoken"), "")

    idx = html.find("window.syncData")
    if idx < 0:
        raise RuntimeError("分享页结构异常：未找到 syncData")
    start = html.find("{", idx)
    depth = 0
    in_str = False
    esc = False
    end = -1
    for i in range(start, len(html)):
        c = html[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
            continue
        if c == '"':
            in_str = True
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise RuntimeError("分享数据括号不配对")

    data = json.loads(html[start:end])
    err = data.get("error") or {}
    if err.get("ret"):
        raise RuntimeError(f"分享不可用：{err.get('msg')}（ret={err.get('ret')}）")
    info = data.get("shareInfo")
    if not info:
        raise RuntimeError("未取到 shareInfo")
    return info, gtk, op


def call_webapp(op, share_id, gtk, name, cmd, payload):
    seq = int(time.time()) + random.randint(0, 9999)
    req_header = {
        "seq": seq, "type": 1, "cmd": cmd, "appid": APPID_WEB_SHARE,
        "version": 3, "major_version": 3, "minor_version": 3, "fix_version": 3,
        "wx_openid": "", "qq_openid": "", "user_flag": 0, "env_id": "", "uin": "", "uid": "",
    }
    body = {"ReqMsg_body": {
        "ext_req_head": {"token_info": {}, "language_info": {"language_type": 2052}},
        f".weiyun.{name}MsgReq_body": payload,
    }}
    envelope = json.dumps({"req_header": json.dumps(req_header), "req_body": json.dumps(body)}).encode()
    url = f"{BASE}/webapp/json/{PROTO}/{name}?refer=chrome_windows&g_tk={gtk}&r={random.random()}"
    req = urllib.request.Request(url, data=envelope, headers={
        "Content-Type": "application/json",
        "Referer": f"{BASE}/{share_id}",
        "Origin": BASE,
    })
    resp = json.loads(op.open(req, timeout=30).read().decode("utf-8", "replace"))
    hdr = ((resp.get("data") or {}).get("rsp_header") or {})
    if hdr.get("retcode") != 0:
        raise RuntimeError(f"{name} 失败：{hdr.get('retcode')} {hdr.get('retmsg')}")
    return ((resp.get("data") or {}).get("rsp_body") or {}).get("RspMsg_body") or {}


def list_dir(op, share_id, gtk, dir_key, pwd=""):
    out, offset = [], 0
    while True:
        body = call_webapp(op, share_id, gtk, "WeiyunShareDirList", 12031, {
            "share_key": share_id, "pwd": pwd, "file_owner": "",
            "dir_key": dir_key, "dir_name": "", "item_offset": offset, "item_limit": 100,
        })
        dirs = body.get("dir_list") or []
        files = body.get("file_list") or []
        for d in dirs:
            out.append({"id": d.get("dir_key"), "name": d.get("dir_name"), "size": 0,
                        "isdir": True, "pdir": dir_key})
        for f in files:
            # DirList 用 filename（无下划线），且 pdir_key 为 null => 用所在目录的 key 兜底
            out.append({"id": f.get("file_id"),
                        "name": f.get("file_name") or f.get("filename"),
                        "size": f.get("file_size", 0), "isdir": False,
                        "pdir": f.get("pdir_key") or dir_key, "lib_id": f.get("lib_id", 0)})
        offset += len(dirs) + len(files)
        if body.get("finish_flag") or (not dirs and not files) or len(dirs) + len(files) < 100:
            break
    return out


def entry_from_share_info(info):
    out = []
    for d in info.get("dir_list") or []:
        out.append({"id": d.get("dir_key"), "name": d.get("dir_name"), "size": 0,
                    "isdir": True, "pdir": info.get("pdir_key")})
    for f in info.get("file_list") or []:
        out.append({"id": f.get("file_id"), "name": f.get("file_name"),
                    "size": f.get("file_size", 0), "isdir": False,
                    "pdir": f.get("pdir_key"), "lib_id": f.get("lib_id", 0)})
    return out


def get_url(op, share_id, gtk, entry, pwd=""):
    body = call_webapp(op, share_id, gtk, "WeiyunSharePartDownload", 12023, {
        "share_key": share_id, "pwd": pwd, "file_owner": "", "download_type": "Part",
        "pdir_key": entry["pdir"], "dir_list": [],
        "file_list": [{"file_id": entry["id"], "pdir_key": entry["pdir"],
                       "file_name": entry["name"], "file_size": entry["size"],
                       **({"lib_id": entry["lib_id"]} if entry.get("lib_id") else {})}],
    })
    url = body.get("https_download_url") or body.get("download_url")
    if not url:
        raise RuntimeError("未返回下载地址")
    cookie = ""
    if body.get("cookie_name") and body.get("cookie_value"):
        cookie = f"{body['cookie_name']}={body['cookie_value']}"
    return url, cookie


def human(n):
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024 or unit == "TB":
            return f"{n:.1f}{unit}" if unit != "B" else f"{n}B"
        n /= 1024


# ---------------- 命令 ----------------

def cmd_list(arg):
    sid = parse_share_id(arg)
    if not sid:
        sys.exit("无法识别分享链接/ID")
    info, gtk, op = fetch_share(sid)
    entries = entry_from_share_info(info)
    print(f"分享：{info.get('share_name')}  (share_key={sid})")
    print(f"浏览 {info.get('view_cnt')} / 下载 {info.get('down_cnt')} / 条目 {len(entries)}")
    for e in entries:
        tag = "[目录]" if e["isdir"] else "      "
        print(f"  {tag} {human(e['size']) if not e['isdir'] else '-':>10}  {e['name']}")


def cmd_url(arg, name=None):
    sid = parse_share_id(arg)
    info, gtk, op = fetch_share(sid)
    entries = [e for e in entry_from_share_info(info) if not e["isdir"]]
    target = None
    if name:
        target = next((e for e in entries if e["name"] == name), None)
    if target is None:
        target = entries[0] if entries else None
    if target is None:
        sys.exit("该分享根目录没有文件（可能只有子目录）")
    url, cookie = get_url(op, sid, gtk, target)
    print(json.dumps({"file": target["name"], "size": target["size"],
                      "url": url, "cookie": cookie}, ensure_ascii=False, indent=2))


def cmd_get(arg, outdir=None):
    sid = parse_share_id(arg)
    info, gtk, op = fetch_share(sid)
    outdir = outdir or os.path.join(os.getcwd(), sid)
    os.makedirs(outdir, exist_ok=True)
    total = 0

    def walk(entries, prefix):
        nonlocal total
        for e in entries:
            if e["isdir"]:
                sub = list_dir(op, sid, gtk, e["id"])
                d = os.path.join(prefix, e["name"])
                os.makedirs(d, exist_ok=True)
                walk(sub, d)
            else:
                dst = os.path.join(prefix, e["name"])
                if os.path.exists(dst) and os.path.getsize(dst) == e["size"]:
                    print(f"  跳过（已存在）{e['name']}")
                    continue
                url, cookie = get_url(op, sid, gtk, e)
                headers = {"User-Agent": UA}
                if cookie:
                    headers["Cookie"] = cookie
                print(f"  下载 {e['name']} ({human(e['size'])}) ...")
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req, timeout=120) as r, open(dst, "wb") as f:
                    while True:
                        chunk = r.read(1 << 20)
                        if not chunk:
                            break
                        f.write(chunk)
                got = os.path.getsize(dst)
                if e["size"] and got != e["size"]:
                    print(f"    !! 大小不符：期望 {e['size']} 实际 {got}")
                total += got

    walk(entry_from_share_info(info), outdir)
    print(f"完成，共 {human(total)}，目录：{outdir}")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    action, arg = sys.argv[1], sys.argv[2]
    if action == "list":
        cmd_list(arg)
    elif action == "url":
        cmd_url(arg, sys.argv[3] if len(sys.argv) > 3 else None)
    elif action == "get":
        cmd_get(arg, sys.argv[3] if len(sys.argv) > 3 else None)
    else:
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
