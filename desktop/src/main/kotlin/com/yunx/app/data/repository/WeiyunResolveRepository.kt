package com.yunx.app.data.repository

import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.WeiyunApi
import com.yunx.app.data.network.WeiyunEntry
import com.yunx.app.data.network.WeiyunPasswordRequiredException
import com.yunx.app.data.network.WeiyunShare
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import java.util.concurrent.ConcurrentHashMap

/**
 * 腾讯微云分享解析仓库。
 *
 * 微云的分享是**完全匿名**的：分享页服务端直出文件清单，取直链也不需要账号，
 * 因此本仓库不涉及 Cookie/账号，`cookie` 参数一律忽略；转存到个人盘（微云需要登录）
 * 不在本次支持范围内，相关方法明确返回「暂不支持」。
 *
 * 与其它平台的差异：`ShareSession` 只带 (shareId, stoken, title)，而微云后续列目录 /
 * 取直链还要 `pdir_key` 与 `g_tk`（分享页下发的 wyctoken），所以这里按 shareId 缓存
 * 整份 [WeiyunShare]；缓存失效（例如从历史记录重新打开）时会重新拉一次分享页。
 */
class WeiyunResolveRepository(
    private val api: WeiyunApi
) : ShareResolveRepository {

    private val cache = ConcurrentHashMap<String, WeiyunShare>()

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        return runCatching {
            val share = api.fetchShare(parsed.shareId, pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd)
            if (share.needPwd && pwd.isNullOrBlank() && parsed.pwd.isNullOrBlank()) {
                // 服务端对需要提取码的分享通常直接返回错误；这里兜底给出更明确的提示
                throw WeiyunPasswordRequiredException("该微云分享需要提取码，请填写后重试")
            }
            cache[parsed.shareId] = share
            ShareSession(shareId = parsed.shareId, stoken = share.gtk, title = share.title)
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            val share = cache[session.shareId] ?: reload(session)
            // 根目录内容分享页已经直出；微云对根目录（pdir_key）调 DirList 会返回
            // 114516 dir key is forbidden，所以只有「确实是已知子目录」才走接口，
            // 其余（空串 / pdir_key / 调用方按平台兜底的 "0" 之类）一律回根列表。
            val knownDirs = share.entries.asSequence().filter { it.isDir }.map { it.id }.toHashSet()
            val entries: List<WeiyunEntry> = if (dirFid.isBlank() || dirFid == share.pdirKey || dirFid !in knownDirs) {
                share.entries
            } else {
                api.listDir(
                    shareId = share.shareId,
                    shareKey = share.shareId,
                    pwd = share.pwd,
                    dirKey = dirFid,
                    gtk = share.gtk
                ).also { sub ->
                    // 把子目录条目并回缓存，便于后续按 fid 反查（取直链要用 pdir_key/lib_id）
                    val merged = share.entries.toMutableList()
                    val seen = merged.map { it.id }.toHashSet()
                    sub.forEach { if (seen.add(it.id)) merged.add(it) }
                    cache[share.shareId] = share.copy(entries = merged)
                }
            }
            entries.map { it.toShareFile() }
        }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("微云分享无需预建临时目录"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String,
        skipSpaceCheck: Boolean
    ): Result<String> = Result.failure(
        UnsupportedOperationException("微云转存需要登录账号，当前版本仅支持分享直链下载")
    )

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("微云分享请使用取分享直链接口"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String,
        skipSpaceCheck: Boolean
    ): Result<DownloadLink> = fetchLink(session, file)

    /**
     * 未登录取链：微云本身就是匿名链路，与 [getShareDownloadLink] 完全相同。
     */
    override suspend fun getGuestShareDownloadLink(
        session: ShareSession,
        file: ShareFile
    ): Result<DownloadLink> = fetchLink(session, file)

    private suspend fun fetchLink(session: ShareSession, file: ShareFile): Result<DownloadLink> = runCatching {
        val share = cache[session.shareId] ?: reload(session)
        val entry = share.entries.firstOrNull { it.id == file.fid }
            ?: WeiyunEntry(
                id = file.fid,
                name = file.fname,
                size = file.fsize,
                isDir = file.isdir,
                pdirKey = file.pdirFid.ifBlank { share.pdirKey }
            )
        val dl = api.getDownloadUrl(
            shareId = share.shareId,
            shareKey = share.shareId,
            pwd = share.pwd,
            entry = entry,
            gtk = share.gtk
        )
        DownloadLink(
            fid = file.fid,
            filename = file.fname,
            downloadUrl = dl.url,
            size = if (dl.size > 0) dl.size else file.fsize,
            // 大文件服务端会要求 FTN5K；DownloadLink.guestCookie 会被下载器写进 Cookie 头
            guestCookie = dl.cookie
        )
    }

    /** 缓存失效时重新拉一次分享页（例如从历史记录直接进入下载页）。 */
    private suspend fun reload(session: ShareSession): WeiyunShare {
        val share = api.fetchShare(session.shareId, null)
        cache[session.shareId] = share
        return share
    }

    private fun WeiyunEntry.toShareFile(): ShareFile = ShareFile(
        fid = id,
        fname = name,
        fsize = size,
        isdir = isDir,
        pdirFid = pdirKey,
        fidToken = ""
    )
}
