package com.yunx.app.data.network

/**
 * 腾讯微云（share.weiyun.com）分享解析常量。
 *
 * 与其它网盘不同，微云分享**不需要登录账号**：
 * 1. `GET https://share.weiyun.com/{shareId}` 返回的 HTML 里，服务端已把分享信息
 *    直接注入到 `window.syncData = {...}`（含 file_list / dir_list / pdir_key），
 *    无需执行 JS、无需任何 Cookie；
 * 2. 下载直链通过 `POST /webapp/json/weiyunShareNoLogin/WeiyunSharePartDownload`
 *    （cmd 12023）匿名换取，响应给出 `https_download_url`；
 * 3. 大文件（服务端判定）会额外下发 `FTN5K` cookie，必须随下载请求以
 *    `Cookie: FTN5K=xxx` 携带，否则直链 401；小文件该字段为空。
 *
 * 网页端点了「普通下载」却弹微信/QQ 扫码登录，是**前端** `localAuthorize()`
 * 在发请求前就拦下了（未登录直接 `return`，请求根本没发出），
 * 并不是服务端强制登录 —— 这也是必须走接口而不是靠浏览器嗅探的原因。
 */
object WeiyunConstants {

    const val BASE_URL = "https://share.weiyun.com"

    /** 分享页 UA：与桌面浏览器一致，避免命中移动端精简流程 */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /**
     * 分享页桌面端 appid（前端常量表 `APPIDS.WEB_SHARE`）。
     * 缺失或错误时服务端统一返回 `(190039)无效的appid`，与权限无关。
     */
    const val APPID_WEB_SHARE = 30113

    /** 分享信息（匿名协议 weiyunShareNoLogin） */
    const val CMD_SHARE_VIEW = 12002

    /** 分享目录列表（列子目录项） */
    const val CMD_SHARE_DIR_LIST = 12031

    /** 分享文件取下载直链 */
    const val CMD_SHARE_PART_DOWNLOAD = 12023

    /** 匿名分享协议名（登录态才是 weiyunShare） */
    const val PROTOCOL_SHARE_NO_LOGIN = "weiyunShareNoLogin"

    /** 业务成功码 */
    const val RET_OK = 0

    /** 分享已被删除/不存在 */
    const val RET_SHARE_GONE = 114200

    /** 目录分页大小 */
    const val DIR_PAGE_LIMIT = 100
}
