package cn.edu.szcu.connect

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class Stage(val label: String) {
    IDLE("准备连接"), WAITING_WIFI("等待 Wi-Fi"), READING("读取认证信息"),
    SESSION("检查当前账号"), LOGOUT("正在注销"), LOGIN("正在登录"), VERIFYING("检查网络"), CONNECTED("连接成功"),
    CAMPUS("内网认证成功"), LIMITED("认证成功，外网未确认"), ALREADY("当前 Wi-Fi 已联网"),
    FAILED("连接未完成"), CANCELLED("已取消")
}
data class ConnectionStatus(val stage: Stage = Stage.IDLE, val detail: String = "选择一个配置，连接你的校园网络")

interface PortalSession {
    suspend fun online(): Boolean
    suspend fun inspect(): CampusSession
    suspend fun logout()
    suspend fun verify(): Boolean
    suspend fun readContext(): PortalContext
    suspend fun authenticate(profile: Profile, ctx: PortalContext): PortalReply
    fun checkNetwork()
}

data class CampusSession(val authenticated: Boolean, val account: String? = null) {
    fun matches(profile: Profile): Boolean = authenticated && account != null &&
        account.removePrefix(",1,") == profile.account + profile.carrier.suffix
}

class ConnectionEngine {
    suspend fun run(profile: Profile, session: PortalSession, report: (ConnectionStatus) -> Unit) {
        fun status(s: Stage, detail: String) = report(ConnectionStatus(s, detail))
        currentCoroutineContext().ensureActive()
        session.checkNetwork()
        status(Stage.SESSION, "通过目标 Wi-Fi 检查校园登录会话")
        val existing = session.inspect()
        currentCoroutineContext().ensureActive()
        session.checkNetwork()
        val sameAccount = existing.matches(profile)
        if (existing.authenticated && !sameAccount) {
            status(Stage.LOGOUT, "正在退出原账号，完成后登录所选配置")
            session.logout()
            currentCoroutineContext().ensureActive()
            session.checkNetwork()
            if (session.inspect().authenticated) throw PortalException("未能确认原账号已注销，已停止登录，请检查校园网页")
        }
        if (!sameAccount) {
            currentCoroutineContext().ensureActive()
            status(Stage.READING, "读取本次 Wi-Fi 的认证参数")
            val ctx = session.readContext()
            currentCoroutineContext().ensureActive()
            session.checkNetwork()
            status(Stage.LOGIN, "正在向校园网提交认证，请稍候")
            val reply = session.authenticate(profile, ctx)
            currentCoroutineContext().ensureActive()
            session.checkNetwork()
            if (!reply.success) throw PortalException(reply.message)
        }
        if (profile.carrier == Carrier.CAMPUS) {
            status(Stage.CAMPUS, "校园内网认证成功；此配置不要求外网访问")
            return
        }
        status(Stage.VERIFYING, "认证已通过，正在验证 Wi-Fi 外网访问")
        val online = session.verify()
        currentCoroutineContext().ensureActive()
        session.checkNetwork()
        if (online) status(if (sameAccount) Stage.ALREADY else Stage.CONNECTED, "所选账号已认证，Wi-Fi 外网与系统联网检查均已通过")
        else status(Stage.LIMITED, "认证已通过，网络状态待确认；Wi-Fi 外网探测或系统检查尚未通过，请稍后再检查")
    }
}
