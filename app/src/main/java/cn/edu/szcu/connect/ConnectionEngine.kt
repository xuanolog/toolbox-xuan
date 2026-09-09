package cn.edu.szcu.connect

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class Stage(val label: String) {
    IDLE("准备连接"), WAITING_WIFI("等待 Wi-Fi"), READING("读取认证信息"),
    LOGIN("正在登录"), VERIFYING("检查网络"), CONNECTED("连接成功"),
    CAMPUS("内网认证成功"), LIMITED("认证成功，外网未确认"), ALREADY("当前 Wi-Fi 已联网"),
    FAILED("连接未完成"), CANCELLED("已取消")
}
data class ConnectionStatus(val stage: Stage = Stage.IDLE, val detail: String = "选择一个配置，连接你的校园网络")

interface PortalSession {
    suspend fun online(): Boolean
    suspend fun readContext(): PortalContext
    suspend fun authenticate(profile: Profile, ctx: PortalContext): PortalReply
    fun checkNetwork()
}

class ConnectionEngine {
    suspend fun run(profile: Profile, session: PortalSession, report: (ConnectionStatus) -> Unit) {
        fun status(s: Stage, detail: String) = report(ConnectionStatus(s, detail))
        currentCoroutineContext().ensureActive()
        session.checkNetwork()
        status(Stage.VERIFYING, "通过目标 Wi-Fi 检查已有连接")
        if (session.online()) {
            currentCoroutineContext().ensureActive()
            session.checkNetwork()
            status(Stage.ALREADY, "当前网络已可上网；未提交所选账号。如需换账号，请先在校园网页退出当前会话。")
            return
        }
        currentCoroutineContext().ensureActive()
        status(Stage.READING, "读取本次 Wi-Fi 的认证参数")
        val ctx = try { session.readContext() } catch (_: ExistingPortalSession) {
            currentCoroutineContext().ensureActive()
            session.checkNetwork()
            status(Stage.ALREADY, "校园网已有认证会话，未提交所选账号；外网访问尚未确认。如需换账号，请先在校园网页退出。")
            return
        }
        currentCoroutineContext().ensureActive()
        session.checkNetwork()
        status(Stage.LOGIN, "正在向校园网提交认证，请稍候")
        val reply = session.authenticate(profile, ctx)
        currentCoroutineContext().ensureActive()
        session.checkNetwork()
        if (!reply.success) throw PortalException(reply.message)
        if (profile.carrier == Carrier.CAMPUS) {
            status(Stage.CAMPUS, "校园内网认证成功；此配置不要求外网访问")
            return
        }
        status(Stage.VERIFYING, "认证已通过，正在验证 Wi-Fi 外网访问")
        val online = session.online()
        currentCoroutineContext().ensureActive()
        session.checkNetwork()
        if (online) status(Stage.CONNECTED, "校园 Wi-Fi 已可上网，可以离开应用")
        else status(Stage.LIMITED, "账号认证已通过，但外网检测暂未通过。请检查网络，不必重复提交密码。")
    }
}
