package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * 微云分享页里的一个条目（文件或目录）。
 */
data class WeiyunEntry(
    val id: String,
    val name: String,
    val size: Long,
    val isDir: Boolean,
    val pdirKey: String,
    val libId: Int = 0,
    val modifyTime: Long = 0L
)

/**
 * 一次分享的根信息（来自 `window.syncData.shareInfo`）。
 *
 * @param needPwd 该分享是否设置了提取码（提取码本身在 [shareInfo.pwd] 里，仅分享者可见）
 * @param gtk 随分享页下发的 `wyctoken`，后续 webapp 接口的 `g_tk` 参数
 */
data class WeiyunShare(
    val shareId: String,
    val pdirKey: String,
    val title: String,
    val needPwd: Boolean,
    val pwd: String,
    val gtk: String,
    val entries: List<WeiyunEntry>
)

/**
 * 取到的下载直链。
 *
 * @param cookie 需要随下载请求携带的 Cookie 串（形如 `FTN5K=xxx`）；小文件为空串。
 *   大文件（服务端判定）缺它会 401。
 */
data class WeiyunDownload(
    val url: String,
    val cookie: String,
    val size: Long
)

/**
 * 腾讯微云分享 API（匿名协议 weiyunShareNoLogin）。
 *
 * 全流程不需要登录账号，因此不涉及 Cookie 持久化；每次解析以分享页响应里的
 * `wyctoken` 作为 `g_tk` 使用。
 */
class WeiyunApi(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()
    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()
    private val seqCounter = AtomicInteger(0)

    /**
     * 拉取分享页并解析出分享信息。
     *
     * @throws WeiyunShareNotFoundException 分享已删除/不存在
     * @throws WeiyunPasswordRequiredException 需要提取码但未提供或提供了错误提取码
     */
    suspend fun fetchShare(shareId: String, pwd: String?): WeiyunShare = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${WeiyunConstants.BASE_URL}/${shareId}")
            .header("User-Agent", WeiyunConstants.USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("微云分享页请求失败：HTTP ${resp.code}")
            }
            val gtk = resp.headers("Set-Cookie")
                .mapNotNull { c -> c.split(';').firstOrNull()?.trim() }
                .firstOrNull { it.startsWith("wyctoken=") }
                ?.substringAfter("wyctoken=")?.trim()
                .orEmpty()
            val html = resp.body?.string().orEmpty()
            parseShare(shareId, html, gtk)
        }
    }

    /**
     * 列某个目录下的条目。dirKey 为分享根目录 ([WeiyunShare.pdirKey]) 时不必调用本方法，
     * 根目录条目已随 [fetchShare] 返回。
     */
    suspend fun listDir(shareId: String, shareKey: String, pwd: String?, dirKey: String, gtk: String): List<WeiyunEntry> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<WeiyunEntry>()
            var offset = 0
            var pages = 0
            while (true) {
                val payload = JSONObject().apply {
                    put("share_key", shareKey)
                    put("pwd", pwd.orEmpty())
                    put("file_owner", "")
                    put("dir_key", dirKey)
                    put("dir_name", "")
                    put("item_offset", offset)
                    put("item_limit", WeiyunConstants.DIR_PAGE_LIMIT)
                }
                val body = callWebapp(
                    shareId = shareId,
                    gtk = gtk,
                    name = "WeiyunShareDirList",
                    cmd = WeiyunConstants.CMD_SHARE_DIR_LIST,
                    payload = payload
                )
                val files = body.optJSONArray("file_list") ?: JSONArray()
                val dirs = body.optJSONArray("dir_list") ?: JSONArray()
                for (i in 0 until dirs.length()) {
                    dirs.optJSONObject(i)?.let { out.add(parseDir(it, dirKey)) }
                }
                for (i in 0 until files.length()) {
                    files.optJSONObject(i)?.let { out.add(parseFile(it)) }
                }
                offset += files.length() + dirs.length()
                pages++
                val finished = body.optBoolean("finish_flag", false) ||
                    (files.length() == 0 && dirs.length() == 0)
                if (finished || files.length() + dirs.length() < WeiyunConstants.DIR_PAGE_LIMIT || pages > 200) {
                    break
                }
            }
            out
        }

    /**
     * 取单个文件的下载直链（匿名）。
     */
    suspend fun getDownloadUrl(
        shareId: String,
        shareKey: String,
        pwd: String?,
        entry: WeiyunEntry,
        gtk: String
    ): WeiyunDownload = withContext(Dispatchers.IO) {
        val fileItem = JSONObject().apply {
            put("file_id", entry.id)
            put("pdir_key", entry.pdirKey)
            put("file_name", entry.name)
            put("file_size", entry.size)
            if (entry.libId > 0) put("lib_id", entry.libId)
        }
        val payload = JSONObject().apply {
            put("share_key", shareKey)
            put("pwd", pwd.orEmpty())
            put("file_owner", "")
            put("download_type", "Part")
            put("pdir_key", entry.pdirKey)
            put("dir_list", JSONArray())
            put("file_list", JSONArray().put(fileItem))
        }
        val body = callWebapp(
            shareId = shareId,
            gtk = gtk,
            name = "WeiyunSharePartDownload",
            cmd = WeiyunConstants.CMD_SHARE_PART_DOWNLOAD,
            payload = payload
        )
        val url = body.optString("https_download_url")
            .ifBlank { body.optString("download_url") }
        if (url.isBlank()) {
            throw IllegalStateException("微云未返回下载地址，请确认分享是否允许下载")
        }
        val cookieName = body.optString("cookie_name")
        val cookieValue = body.optString("cookie_value")
        val cookie = if (cookieName.isNotBlank() && cookieValue.isNotBlank()) {
            "$cookieName=$cookieValue"
        } else {
            ""
        }
        WeiyunDownload(
            url = url,
            cookie = cookie,
            size = body.optLong("file_size", entry.size)
        )
    }

    // ---------------- 内部实现 ----------------

    /**
     * 调用 `/webapp/json/{protocol}/{name}` 接口，返回 RspMsg_body。
     *
     * 请求体是两层字符串化的 JSON：`{"req_header": "<json>", "req_body": "<json>"}`，
     * `req_body` 里再包一层 `ReqMsg_body`，业务参数放在 `.weiyun.{name}MsgReq_body` 键下。
     */
    private fun callWebapp(
        shareId: String,
        gtk: String,
        name: String,
        cmd: Int,
        payload: JSONObject
    ): JSONObject {
        val seq = (System.currentTimeMillis() / 1000).toInt() + seqCounter.incrementAndGet() * 7
        val reqHeader = JSONObject().apply {
            put("seq", seq)
            put("type", 1)
            put("cmd", cmd)
            put("appid", WeiyunConstants.APPID_WEB_SHARE)
            put("version", 3)
            put("major_version", 3)
            put("minor_version", 3)
            put("fix_version", 3)
            put("wx_openid", "")
            put("qq_openid", "")
            put("user_flag", 0)
            put("env_id", "")
            put("uin", "")
            put("uid", "")
        }
        val reqBody = JSONObject().apply {
            put(
                "ReqMsg_body",
                JSONObject().apply {
                    put(
                        "ext_req_head",
                        JSONObject().apply {
                            put("token_info", JSONObject())
                            put("language_info", JSONObject().put("language_type", 2052))
                        }
                    )
                    put(".weiyun.${name}MsgReq_body", payload)
                }
            )
        }
        val envelope = JSONObject().apply {
            put("req_header", reqHeader.toString())
            put("req_body", reqBody.toString())
        }
        val url = "${WeiyunConstants.BASE_URL}/webapp/json/${WeiyunConstants.PROTOCOL_SHARE_NO_LOGIN}/${name}" +
            "?refer=chrome_windows&g_tk=${gtk}&r=${Math.random()}"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", WeiyunConstants.USER_AGENT)
            .header("Referer", "${WeiyunConstants.BASE_URL}/${shareId}")
            .header("Origin", WeiyunConstants.BASE_URL)
            .header("Content-Type", "application/json")
            // 分享页下发的 wyctoken 既做 g_tk 又必须作为 Cookie 回传，缺它服务端直接 403
            .apply { if (gtk.isNotBlank()) header("Cookie", "wyctoken=$gtk") }
            .post(envelope.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("微云接口 $name 失败：HTTP ${resp.code}")
            }
            val text = resp.body?.string().orEmpty()
            val root = runCatching { JSONObject(text) }
                .getOrElse { throw IllegalStateException("微云接口 $name 返回非 JSON：${text.take(120)}") }
            val header = root.optJSONObject("data")?.optJSONObject("rsp_header")
                ?: throw IllegalStateException("微云接口 $name 缺少 rsp_header")
            val retcode = header.optInt("retcode", -1)
            if (retcode != WeiyunConstants.RET_OK) {
                val msg = header.optString("retmsg")
                if (retcode == WeiyunConstants.RET_SHARE_GONE) {
                    throw WeiyunShareNotFoundException(msg)
                }
                throw IllegalStateException("微云 $name 返回错误：$retcode $msg")
            }
            return root.optJSONObject("data")
                ?.optJSONObject("rsp_body")
                ?.optJSONObject("RspMsg_body")
                ?: JSONObject()
        }
    }

    /** 解析分享页 HTML 里的 `window.syncData`。 */
    private fun parseShare(shareId: String, html: String, gtk: String): WeiyunShare {
        val marker = "window.syncData"
        val idx = html.indexOf(marker)
        if (idx < 0) {
            throw IllegalStateException("微云分享页结构异常：未找到 syncData")
        }
        val braceStart = html.indexOf('{', idx)
        if (braceStart < 0) throw IllegalStateException("微云分享页结构异常：syncData 无 JSON")
        val jsonText = extractBalancedJson(html, braceStart)
            ?: throw IllegalStateException("微云分享页结构异常：syncData 括号不配对")
        val root = runCatching { JSONObject(jsonText) }
            .getOrElse { throw IllegalStateException("微云分享数据解析失败：${it.message}") }

        root.optJSONObject("error")?.let { err ->
            val ret = err.optInt("ret", 0)
            if (ret != 0) {
                val msg = err.optString("msg")
                if (ret == WeiyunConstants.RET_SHARE_GONE) throw WeiyunShareNotFoundException(msg)
                throw IllegalStateException("微云分享不可用：$msg")
            }
        }

        val info = root.optJSONObject("shareInfo")
            ?: throw IllegalStateException("微云分享信息缺失")
        val needPwd = info.optInt("need_pwd", 0) == 1
        val pwd = info.optString("pwd")
        val pdirKey = info.optString("pdir_key")
        val shareName = info.optString("share_name").ifBlank { "微云分享" }

        val entries = mutableListOf<WeiyunEntry>()
        info.optJSONArray("dir_list")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { entries.add(parseDir(it, pdirKey)) }
        }
        info.optJSONArray("file_list")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { entries.add(parseFile(it)) }
        }
        return WeiyunShare(
            shareId = shareId,
            pdirKey = pdirKey,
            title = shareName,
            needPwd = needPwd,
            pwd = pwd,
            gtk = gtk,
            entries = entries
        )
    }

    private fun parseFile(obj: JSONObject): WeiyunEntry = WeiyunEntry(
        id = obj.optString("file_id"),
        name = obj.optString("file_name"),
        size = obj.optLong("file_size", 0L),
        isDir = false,
        pdirKey = obj.optString("pdir_key"),
        libId = obj.optInt("lib_id", 0),
        modifyTime = obj.optLong("file_mtime", 0L)
    )

    private fun parseDir(obj: JSONObject, pdirKey: String): WeiyunEntry = WeiyunEntry(
        id = obj.optString("dir_key").ifBlank { obj.optString("key") },
        name = obj.optString("dir_name").ifBlank { obj.optString("name") },
        size = 0L,
        isDir = true,
        pdirKey = pdirKey,
        modifyTime = obj.optLong("dir_mtime", 0L)
    )

    /** 从 start 处的 `{` 开始，返回括号配平后的 JSON 子串。 */
    private fun extractBalancedJson(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

/** 分享已删除或不存在（服务端 114200）。 */
class WeiyunShareNotFoundException(message: String) : Exception(message.ifBlank { "微云分享不存在或已被删除" })

/** 该分享需要提取码。 */
class WeiyunPasswordRequiredException(message: String = "该微云分享需要提取码") : Exception(message)
