// ==UserScript==
// @name         微云直链下载
// @namespace    yunx.weiyun
// @version      1.0
// @description  打开腾讯微云分享页时自动提取直链并下载（免登录）
// @match        *://share.weiyun.com/*
// @grant        none
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';

  // 分享页桌面端 appid（前端常量表 APPIDS.WEB_SHARE）；缺/错会返回 190039 无效的appid
  var APPID = 30113;
  var PROTO = 'weiyunShareNoLogin';

  function shareId() {
    var m = location.pathname.match(/^\/([A-Za-z0-9_-]{4,40})/);
    return m ? m[1] : null;
  }

  function cookie(name) {
    var m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
    return m ? m[1] : '';
  }

  // 拿分享信息：优先用页面已注入的 window.syncData，没有就重新拉一次 HTML 解析
  function readSync(html) {
    var idx = html.indexOf('window.syncData');
    if (idx < 0) return null;
    var start = html.indexOf('{', idx);
    if (start < 0) return null;
    var depth = 0, inStr = false, esc = false;
    for (var i = start; i < html.length; i++) {
      var c = html[i];
      if (inStr) {
        if (esc) esc = false;
        else if (c === '\\') esc = true;
        else if (c === '"') inStr = false;
        continue;
      }
      if (c === '"') inStr = true;
      else if (c === '{') depth++;
      else if (c === '}') {
        depth--;
        if (depth === 0) {
          try { return JSON.parse(html.slice(start, i + 1)); } catch (e) { return null; }
        }
      }
    }
    return null;
  }

  function entryFrom(info) {
    var out = [];
    (info.dir_list || []).forEach(function (d) {
      out.push({ id: d.dir_key, name: d.dir_name, size: 0, isdir: true, pdir: info.pdir_key });
    });
    (info.file_list || []).forEach(function (f) {
      out.push({
        id: f.file_id, name: f.file_name, size: f.file_size || 0,
        isdir: false, pdir: f.pdir_key, libId: f.lib_id
      });
    });
    return out;
  }

  // 调 /webapp/json/weiyunShareNoLogin/{name}
  async function callApi(sid, name, cmd, payload) {
    var seq = Math.floor(Date.now() / 1000) + Math.floor(Math.random() * 9999);
    var reqHeader = {
      seq: seq, type: 1, cmd: cmd, appid: APPID,
      version: 3, major_version: 3, minor_version: 3, fix_version: 3,
      wx_openid: '', qq_openid: '', user_flag: 0, env_id: '', uin: '', uid: ''
    };
    var reqBody = { ReqMsg_body: {
      ext_req_head: { token_info: {}, language_info: { language_type: 2052 } }
    } };
    reqBody.ReqMsg_body['.weiyun.' + name + 'MsgReq_body'] = payload;

    var gtk = cookie('wyctoken');
    var url = location.origin + '/webapp/json/' + PROTO + '/' + name +
      '?refer=chrome_windows&g_tk=' + gtk + '&r=' + Math.random();
    var resp = await fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Referer': location.href,
        'Origin': location.origin
      },
      body: JSON.stringify({ req_header: JSON.stringify(reqHeader), req_body: JSON.stringify(reqBody) })
    });
    var text = await resp.text();
    if (!resp.ok) throw new Error(name + ' HTTP ' + resp.status);
    var d = JSON.parse(text);
    var head = (d.data && d.data.rsp_header) || {};
    if (head.retcode !== 0) throw new Error(name + ' 失败: ' + head.retcode + ' ' + head.retmsg);
    return ((d.data && d.data.rsp_body) || {}).RspMsg_body || {};
  }

  async function listDir(sid, dirKey) {
    var out = [];
    var offset = 0;
    for (var page = 0; page < 100; page++) {
      var body = await callApi(sid, 'WeiyunShareDirList', 12031, {
        share_key: sid, pwd: '', file_owner: '',
        dir_key: dirKey, dir_name: '', item_offset: offset, item_limit: 100
      });
      var dirs = body.dir_list || [];
      var files = body.file_list || [];
      dirs.forEach(function (d) {
        out.push({ id: d.dir_key, name: d.dir_name, size: 0, isdir: true, pdir: dirKey });
      });
      files.forEach(function (f) {
        // DirList 的字段是 filename（无下划线），pdir_key 为 null => 用所在目录 key 兜底
        out.push({
          id: f.file_id, name: f.file_name || f.filename, size: f.file_size || 0,
          isdir: false, pdir: f.pdir_key || dirKey
        });
      });
      offset += dirs.length + files.length;
      if (body.finish_flag || (!dirs.length && !files.length) || dirs.length + files.length < 100) break;
    }
    return out;
  }

  async function getLink(sid, e) {
    var item = { file_id: e.id, pdir_key: e.pdir, file_name: e.name, file_size: e.size };
    if (e.libId) item.lib_id = e.libId;
    var body = await callApi(sid, 'WeiyunSharePartDownload', 12023, {
      share_key: sid, pwd: '', file_owner: '', download_type: 'Part',
      pdir_key: e.pdir, dir_list: [], file_list: [item]
    });
    var url = body.https_download_url || body.download_url;
    if (!url) throw new Error('未返回下载地址');
    // 大文件服务端要求 FTN5K，缺它直链 401；写到 .weiyun.com 域上让 CDN 子域也能带
    if (body.cookie_name && body.cookie_value) {
      document.cookie = body.cookie_name + '=' + body.cookie_value + '; domain=.weiyun.com; path=/; secure';
    }
    return url;
  }

  function human(n) {
    if (!n) return '-';
    var u = ['B', 'KB', 'MB', 'GB', 'TB'], i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return n.toFixed(i ? 1 : 0) + u[i];
  }

  function download(url, name) {
    // 注意：直链在 *.weiyun.com CDN 上，跨域 ⇒ a[download] 的文件名会被忽略，
    // 部分浏览器（尤其移动端）还会直接打开文件而不是另存。所以额外给「复制直链」，
    // 复制到 ADM/IDM 之类下载器是最稳的走法。
    var a = document.createElement('a');
    a.href = url;
    a.download = name || '';
    a.rel = 'noopener';
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { a.remove(); }, 1000);
  }

  function copyText(text) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text);
        return true;
      }
    } catch (e) { /* 落到下面 */ }
    try {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.style.cssText = 'position:fixed;left:-9999px';
      document.body.appendChild(ta);
      ta.select();
      var ok = document.execCommand('copy');
      ta.remove();
      return ok;
    } catch (e) { return false; }
  }

  function tip(text) {
    var el = document.getElementById('wy-dl-tip');
    if (!el) {
      el = document.createElement('div');
      el.id = 'wy-dl-tip';
      el.style.cssText = 'margin-top:8px;color:#8a93a3;font-size:11px;line-height:1.45';
      panel().appendChild(el);
    }
    el.textContent = text;
  }

  // ---------- UI ----------
  function panel() {
    var el = document.getElementById('wy-dl-panel');
    if (el) return el;
    el = document.createElement('div');
    el.id = 'wy-dl-panel';
    el.style.cssText = [
      'position:fixed', 'right:12px', 'bottom:12px', 'z-index:2147483647',
      'max-width:min(92vw,420px)', 'max-height:60vh', 'overflow:auto',
      'background:rgba(20,22,28,.96)', 'color:#e8ebf0', 'border:1px solid #333a46',
      'border-radius:12px', 'padding:10px 12px', 'font:13px/1.5 -apple-system,system-ui,sans-serif',
      'box-shadow:0 8px 28px rgba(0,0,0,.45)'
    ].join(';');
    document.body.appendChild(el);
    return el;
  }

  function row(el, text, sub) {
    var d = document.createElement('div');
    d.style.cssText = 'display:flex;align-items:center;gap:6px;padding:6px 0;border-bottom:1px solid #262b35';
    var t = document.createElement('div');
    t.style.cssText = 'flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap';
    t.textContent = text;
    if (sub) {
      var s = document.createElement('div');
      s.style.cssText = 'color:#8a93a3;font-size:11px';
      s.textContent = sub;
      t.appendChild(s);
    }
    var c = document.createElement('button');
    c.textContent = '直链';
    c.style.cssText = 'flex:none;background:#3a4150;color:#cfd6e2;border:0;border-radius:8px;padding:5px 9px;font-size:12px';
    var b = document.createElement('button');
    b.textContent = '下载';
    b.style.cssText = 'flex:none;background:#2b7dff;color:#fff;border:0;border-radius:8px;padding:5px 10px;font-size:12px';
    d.appendChild(t);
    d.appendChild(c);
    d.appendChild(b);
    el.appendChild(d);
    return { dl: b, cp: c };
  }

  async function main() {
    var sid = shareId();
    if (!sid) return;

    var root = window.syncData && window.syncData.shareInfo;
    if (!root) {
      var html = await (await fetch(location.href, { credentials: 'include' })).text();
      var data = readSync(html);
      if (!data || !data.shareInfo) return;
      root = data.shareInfo;
    }

    var el = panel();
    el.textContent = '';
    var title = document.createElement('div');
    title.style.cssText = 'font-weight:600;margin-bottom:6px';
    title.textContent = '微云直链 · ' + (root.share_name || sid);
    el.appendChild(title);

    var entries = entryFrom(root);
    if (!entries.length) {
      var empty = document.createElement('div');
      empty.style.cssText = 'color:#8a93a3';
      empty.textContent = '该分享没有可列出的内容';
      el.appendChild(empty);
      return;
    }

    entries.forEach(function (e) {
      if (e.isdir) {
        var r = row(el, '📁 ' + e.name, '点击展开');
        var btn = r.dl;
        btn.textContent = '展开';
        r.cp.style.display = 'none';
        btn.onclick = async function () {
          btn.textContent = '…';
          try {
            var sub = await listDir(sid, e.id);
            var wrap = document.createElement('div');
            wrap.style.cssText = 'padding-left:12px;border-left:2px solid #2b7dff33;margin:4px 0 8px';
            el.insertBefore(wrap, btn.parentNode.nextSibling);
            sub.forEach(function (se) {
              if (se.isdir) return;
              var rr = row(wrap, se.name.split(/[\\/]/).pop(), human(se.size));
              bindRow(rr, sid, se);
            });
            btn.style.display = 'none';
            if (!sub.filter(function (x) { return !x.isdir; }).length) {
              wrap.textContent = '（该目录没有文件）';
              wrap.style.color = '#8a93a3';
            }
          } catch (err) {
            btn.textContent = '失败';
            btn.title = err.message;
          }
        };
      } else {
        bindRow(row(el, e.name, human(e.size)), sid, e);
      }
    });

    tip('大文件服务端要校验令牌，直接点「下载」若只是打开了文件，就用「直链」复制到下载器（ADM/IDM/迅雷均可）。');
  }

  function bindRow(r, sid, e) {
    var dl = r.dl, cp = r.cp;
    dl.onclick = async function () {
      dl.textContent = '…';
      try { download(await getLink(sid, e), e.name); dl.textContent = '已发'; }
      catch (err) { dl.textContent = '失败'; dl.title = err.message; }
    };
    cp.onclick = async function () {
      cp.textContent = '…';
      try {
        var u = await getLink(sid, e);
        cp.textContent = copyText(u) ? '已复制' : '复制失败';
      } catch (err) {
        cp.textContent = '失败';
        cp.title = err.message;
      }
    };
  }

  if (document.readyState === 'complete') setTimeout(main, 800);
  else window.addEventListener('load', function () { setTimeout(main, 800); });
})();
